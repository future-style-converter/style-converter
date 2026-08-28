package com.styleconverter.runtime.animations

// TransitionDriver — state-change motion (schema/spec/07-animations.md §4).
//
// Transitions reuse the keyframe tier's interpolation math but are
// triggered by SELECTOR/MEDIA BUCKET FLIPS (spec 06 §5): when the wave-7
// resolution pass changes the effective value of a property named in
// `transition-property` (or `all`), the old value animates to the new one
// over transition-duration + delay + timing-function.
//
// Capture posture (docs/DYNAMIC_CAPTURE.md §4): the harness now performs a
// real post-paint flip — ScreenshotCaptureScreen mounts in BASE state and
// applies the forced set one painted frame later, but ONLY when a clock is
// pinned. So under `CAPTURE_ANIMATION_TIME` this driver presents the blend
// at the pinned t (the flip is timeline zero, §4), and under a forced run
// with no clock the forced set still applies at mount, so settled-appearance
// captures are unchanged. Measured on transitions.json at t=0.4s:
// MT_BgFade renders (153,107,102) on all three platforms — the spec value —
// where every t previously rendered the endpoint (192,57,43).

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonElement

object TransitionDriver {

    /** IR type → CSS property name, for matching `transition-property`
     *  entries (the wire carries CSS names: "background-color", "width").
     *  Only tier types interpolate (§4); other covered types are discrete. */
    internal val TYPE_TO_CSS = mapOf(
        "Opacity" to "opacity",
        "BackgroundColor" to "background-color",
        "Color" to "color",
        "Width" to "width",
        "Height" to "height",
        "Transform" to "transform",
        "Translate" to "translate",
        "Scale" to "scale",
        "Rotate" to "rotate"
    )

    /**
     * One in-flight transition: [from] → [to] on property [type],
     * starting at clock time [startMs] (flight clocks share the driver's
     * frame-clock timeline).
     */
    data class Flight(
        val type: String,
        val from: JsonElement,
        val to: JsonElement,
        val startMs: Double,
        val durationMs: Double,
        val delayMs: Double,
        val timing: TimingFunctionConfig
    )

    // ── pure core (JVM-testable) ────────────────────────────────────────

    /** Does [config] cover CSS property [cssName]? `transition-property`
     *  list semantics: explicit name match, or the `all` keyword. An empty
     *  extracted list with hasTransitions means shorthand `all` too, and
     *  the `none` keyword disables every transition (css-transitions-1
     *  §2.1 — the single-value-only keyword the parser carries verbatim). */
    internal fun covers(config: TransitionConfig, cssName: String): Boolean {
        if (config.properties.any { it.equals("none", true) }) return false
        if (config.properties.isEmpty()) return true // bare transition-duration ⇒ all
        return config.properties.any { it.equals("all", true) || it.equals(cssName, true) }
    }

    /** Index of [cssName] in the transition-property list, for the §3
     *  list-matching pairing with durations/delays/timings ("all" and
     *  missing entries pair with index 0). */
    internal fun timingIndex(config: TransitionConfig, cssName: String): Int {
        val i = config.properties.indexOfFirst { it.equals(cssName, true) }
        return if (i >= 0) i else 0
    }

    /**
     * Diff [previous] → [target] and start flights for covered changed
     * properties, at shared clock time [nowMs]. Properties must exist on
     * BOTH sides to transition (an appearing/disappearing declaration has
     * no interpolation endpoints — CSS treats those as discrete too).
     */
    internal fun startFlights(
        previous: List<IRProperty>,
        target: List<IRProperty>,
        config: TransitionConfig,
        nowMs: Double
    ): List<Flight> {
        val prevByType = previous.associateBy { it.type }
        val flights = ArrayList<Flight>()
        for (prop in target) {
            val old = prevByType[prop.type] ?: continue
            if (old.data == prop.data) continue // unchanged — nothing to animate
            val cssName = TYPE_TO_CSS[prop.type]
            if (cssName == null) {
                // Non-tier changed property: §4 lets it change discretely
                // (this driver switches at the flip itself — the endpoint
                // choice), logged so reports can see the discrete jump.
                reportDiscrete(prop.type)
                continue
            }
            if (!covers(config, cssName)) continue // not named → immediate switch
            val ti = timingIndex(config, cssName)
            val duration = config.getDuration(ti).toDouble()
            if (duration <= 0.0) continue // zero duration = instant (no flight)
            flights.add(Flight(
                type = prop.type,
                from = old.data,
                to = prop.data,
                startMs = nowMs,
                durationMs = duration,
                delayMs = config.getDelay(ti).toDouble(),
                timing = config.getTimingFunction(ti)
            ))
        }
        return flights
    }

    /** The presented value of one flight at clock time [nowMs]: old value
     *  during the delay, eased interpolation in flight, target after. */
    internal fun presentedValue(flight: Flight, nowMs: Double): JsonElement {
        val local = nowMs - flight.startMs - flight.delayMs
        if (local <= 0.0) return flight.from // still in the delay window
        if (local >= flight.durationMs) return flight.to // completed
        val eased = KeyframeTimeline.ease(flight.timing, local / flight.durationMs)
        // Reuse the keyframe tier math — §4: transitions use the SAME
        // interpolation tier. Non-interpolable shapes fall to the target
        // at completion (discrete endpoint rule), old value meanwhile.
        return KeyframeValueMath.interpolateTyped(flight.type, flight.from, flight.to, eased)
            ?: run { reportDiscrete(flight.type); flight.from }
    }

    /** Overlay all active flight values onto [target] at [nowMs]. */
    internal fun presented(
        target: List<IRProperty>,
        flights: List<Flight>,
        nowMs: Double
    ): List<IRProperty> {
        if (flights.isEmpty()) return target // identity fast path
        val byType = flights.associateBy { it.type }
        return target.map { prop ->
            val flight = byType[prop.type] ?: return@map prop
            IRProperty(prop.type, presentedValue(flight, nowMs))
        }
    }

    // ── composable glue ─────────────────────────────────────────────────

    /**
     * Wrap a component's RESOLVED property list (post bucket fold): when a
     * flip changes covered values, animate old → new per transition-*.
     * Identity-preserving when the component declares no transition-*
     * properties (the overwhelmingly common case).
     */
    @Composable
    fun apply(resolved: List<IRProperty>, componentId: String = ""): List<IRProperty> {
        // Cheap pre-gate: no transition-* declarations → no driver at all.
        val hasTransitionProps = resolved.any { it.type.startsWith("Transition") }
        if (!hasTransitionProps) return resolved

        val config = remember(resolved) {
            AnimationExtractor.extractTransitionConfig(resolved.map { it.type to it.data })
        }
        if (!config.hasTransitions) return resolved

        // Shared frame clock for this component's flights.
        //
        // KEYED ON componentId, and that is load-bearing rather than
        // tidiness. The Android capture loop renders every component
        // through ONE composition slot (ScreenshotCaptureScreen's
        // `CaptureView(component = flat[currentIndex], …)`, with no
        // `key(...)` anywhere in the file), so an unkeyed `remember` here
        // SURVIVES the switch from component N-1 to component N. That was
        // harmless only while the forced-clock branch below bailed out;
        // the moment transitions present under capture, component N would
        // start flights from component N-1's committed values — e.g.
        // MT_WidthGrow animating from MT_BgFade's background colour.
        //
        // Keyed here rather than by wrapping the call site in `key()`:
        // that would change composition identity for the whole committed
        // 327-baseline corpus, which is not inert by construction.
        var nowMs by remember(componentId) { mutableDoubleStateOf(0.0) }
        var flights by remember(componentId) { mutableStateOf(listOf<Flight>()) }
        // The last target list we committed — flips are detected as
        // value inequality against it. Initialized to the FIRST resolved
        // list, so first paint never transitions (matches web: the
        // element mounts already in its initial state).
        var committed by remember(componentId) { mutableStateOf(resolved) }

        // Deterministic capture (§5): present the blend at the PINNED t.
        //
        // This used to bail — "forced runs have no post-paint flip, so
        // transitions resolve to their ENDPOINT state" — which was an
        // accurate description of the harness at the time and is what
        // docs/DYNAMIC_CAPTURE.md §4 recorded as deferred platform-lane
        // work. The harness now performs that flip (ScreenshotCaptureScreen
        // applies the forced set one painted frame after mount), so the
        // bail would discard the very flight the flip exists to create.
        //
        // The flip IS timeline zero (spec 07 §4), so flights start at 0.0
        // and present at `forcedT`; no frame clock runs at all, which is
        // the same construction that makes the keyframe lane byte-stable
        // under capture. `transition-delay` counts inside t, exactly as
        // §5 requires ("as if wall-clock time t had elapsed").
        val forcedT = KeyframeAnimationDriver.LocalForcedAnimationTime.current
        if (forcedT != null) {
            if (committed != resolved) {
                flights = startFlights(committed, resolved, config, 0.0)
                committed = resolved
            }
            // Unforced seized runs (the whole static corpus, and
            // keyframes-basic) never flip, so `flights` stays empty and
            // `presented` returns the SAME list instance the old bail
            // returned — inert by construction, not by argument.
            return presented(resolved, flights, forcedT * 1000.0)
        }

        if (committed != resolved) {
            // A bucket flip landed: launch flights FROM the currently
            // presented values (mid-flight retarget keeps continuity —
            // css-transitions-1 §3 reversing-adjusted start values are a
            // v2 refinement; v1 restarts from the presented value).
            val current = presented(committed, flights, nowMs)
            val fresh = startFlights(current, resolved, config, nowMs)
            // New flights replace same-type older ones; unaffected old
            // flights keep running.
            val freshTypes = fresh.mapTo(HashSet()) { it.type }
            flights = flights.filter { it.type !in freshTypes } + fresh
            committed = resolved
        }

        // Drive the clock while any flight is still active. LaunchedEffect
        // keyed on the flight list restarts the loop on retargets.
        LaunchedEffect(flights) {
            if (flights.isEmpty()) return@LaunchedEffect
            val endMs = flights.maxOf { it.startMs + it.delayMs + it.durationMs }
            var baseNanos = -1L
            var baseOffsetMs = 0.0
            while (true) {
                withFrameNanos { frame ->
                    if (baseNanos < 0) {
                        // Re-anchor the frame clock to the current logical
                        // time so retargets don't jump the timeline.
                        baseNanos = frame
                        baseOffsetMs = nowMs
                    }
                    nowMs = baseOffsetMs + (frame - baseNanos) / 1e6
                }
                if (nowMs >= endMs) break
            }
            // Settle: drop completed flights so the identity fast path
            // resumes and the presented list equals the resolved target.
            flights = flights.filter { it.startMs + it.delayMs + it.durationMs > nowMs }
        }

        return remember(resolved, flights, nowMs) { presented(resolved, flights, nowMs) }
    }

    // Discrete-change reports, once per type per process (§4 logging rule).
    private val discreteLogged = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun reportDiscrete(type: String) {
        PropertyTracker.markUnhandled("TransitionDiscrete[$type]")
        if (discreteLogged.add(type)) {
            try {
                android.util.Log.w("TransitionDriver", "transition on '$type' is discrete (non-tier or non-interpolable) — switching at the flip/endpoint")
            } catch (_: RuntimeException) {
                // Plain-JVM unit tests: android.util.Log is a stub.
            }
        }
    }
}
