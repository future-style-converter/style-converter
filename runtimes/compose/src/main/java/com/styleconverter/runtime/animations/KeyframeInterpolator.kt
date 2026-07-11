package com.styleconverter.runtime.animations

// KeyframeInterpolator — the PURE value math of animation runtime v1
// (schema/spec/07-animations.md §2 "Animatable tier v1").
//
// Input: one wire keyframe set (offset-sorted IRKeyframeStop list, spec 07
// §1.2) + an eased progress from KeyframeTimeline + the component's base
// property list. Output: typed IRProperty OVERRIDES carrying the SAME wire
// byte shapes the static appliers already consume ({type:'length',px},
// {srgb:{r,g,b}}, {type:'functions',list:[…]}, {alpha}) — so an animated
// frame renders through the exact extractor/applier path a static fixture
// does, and the cross-platform SSIM story stays one pipeline, not two.
//
// Tier rules (§2): numbers linear, lengths in px, colors in sRGB component
// space on the wire's 0–1 floats. Non-tier properties are STEP-APPLIED at
// stop boundaries and logged once via PropertyTracker (the documented
// honesty valve — never a silent skip).

import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRKeyframeStop
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonElement

object KeyframeInterpolator {

    /** The §2 tier: property types this runtime INTERPOLATES. Everything
     *  else in a keyframe payload is carried + step-applied (logged). */
    internal val TIER_TYPES = setOf(
        "Opacity", "Transform", "Translate", "Scale", "Rotate",
        "BackgroundColor", "Color", "Width", "Height"
    )

    /**
     * Compute the override property list for one keyframe set at DIRECTED
     * iteration progress [progress] (0..1, un-eased — KeyframeTimeline's
     * output). [base] supplies the implicit 0%/100% endpoints
     * css-animations-1 §4.1 constructs from computed values when a
     * property's first/last stop doesn't sit at the edges. [timing] is the
     * property-level timing function, applied to EACH SEGMENT's local
     * fraction per §4.4 ("between keyframes") — never to the whole
     * iteration, which is what keyframe offsets divide.
     */
    fun overridesAt(
        stops: List<IRKeyframeStop>,
        progress: Double,
        base: List<IRProperty>,
        timing: TimingFunctionConfig = TimingFunctionConfig.LINEAR
    ): List<IRProperty> {
        if (stops.isEmpty()) return emptyList()
        val p = progress.coerceIn(0.0, 1.0)
        val overrides = ArrayList<IRProperty>()
        for ((type, track) in buildTracks(stops)) {
            val value = valueOnTrack(type, track, p, base, timing)
            if (value != null) overrides.add(IRProperty(type, value))
        }
        return overrides
    }

    /**
     * Group stop declarations into per-property tracks: type → ordered
     * (offset, data) pairs. Stops arrive sorted (readers MAY rely on it,
     * §1.2); equal offsets keep authoring order, so a same-type duplicate
     * at the same offset resolves LAST-WINS here — the css-animations-1
     * §4.2 cascade the wire deliberately left to the runtime.
     */
    internal fun buildTracks(
        stops: List<IRKeyframeStop>
    ): Map<String, List<Pair<Double, JsonElement>>> {
        val tracks = LinkedHashMap<String, MutableList<Pair<Double, JsonElement>>>()
        for (stop in stops) {
            for (prop in stop.properties) {
                val track = tracks.getOrPut(prop.type) { mutableListOf() }
                val last = track.lastOrNull()
                if (last != null && last.first == stop.offset) {
                    // Same property declared twice at one offset → the later
                    // authoring order wins (equal-offset stable sort rule).
                    track[track.size - 1] = stop.offset to prop.data
                } else {
                    track.add(stop.offset to prop.data)
                }
            }
        }
        return tracks
    }

    /**
     * Resolve one property's value at progress [p] along its [track].
     * Returns null when NO override applies (p sits before the first
     * declaration and neither the base list nor §4.1 synthesis supplies an
     * endpoint — the component's own base styling then shows through).
     */
    private fun valueOnTrack(
        type: String,
        track: List<Pair<Double, JsonElement>>,
        p: Double,
        base: List<IRProperty>,
        timing: TimingFunctionConfig
    ): JsonElement? {
        // §4.1 implicit endpoints: when the edge stops are missing, the
        // base COMPUTED value fills them — that's what makes the golden's
        // slide-shift Width (declared only at 50%) animate 80→140→80.
        val baseValue = base.firstOrNull { it.type == type }?.data
        val first = track.first()
        val last = track.last()
        val effTrack = buildList {
            if (first.first > 0.0) {
                when {
                    baseValue != null -> add(0.0 to baseValue)
                    // No base declaration to synthesize from: clamp to the
                    // first stop (constant extension) — logged as a step so
                    // fidelity reports can see the §4.1 gap.
                    else -> {
                        reportStepped(type, "missing 0% endpoint and no base value")
                        add(0.0 to first.second)
                    }
                }
            }
            addAll(track)
            if (last.first < 1.0) {
                when {
                    baseValue != null -> add(1.0 to baseValue)
                    else -> {
                        reportStepped(type, "missing 100% endpoint and no base value")
                        add(1.0 to last.second)
                    }
                }
            }
        }

        // Locate the surrounding segment. Offsets are strictly increasing
        // after buildTracks' equal-offset dedupe + the endpoint synthesis.
        var prevIdx = 0
        for (i in effTrack.indices) {
            if (effTrack[i].first <= p) prevIdx = i else break
        }
        val prev = effTrack[prevIdx]
        val next = effTrack.getOrNull(prevIdx + 1) ?: return prev.second
        if (next.first <= prev.first) return next.second // defensive: degenerate segment

        val local = ((p - prev.first) / (next.first - prev.first)).coerceIn(0.0, 1.0)
        // §4.4: the timing function eases THIS SEGMENT's local fraction —
        // keyframe offsets are hit exactly at their linear-time positions
        // (web's ease-pulse sits precisely ON the 50% stop at t = D/2).
        val eased = KeyframeTimeline.ease(timing, local)

        // Non-tier → step-apply: the value switches at the stop boundary
        // (§2 honesty valve), i.e. the PREVIOUS stop's value holds until p
        // reaches the next offset exactly.
        if (type !in TIER_TYPES) {
            reportStepped(type, "non-tier keyframe property")
            return if (local >= 1.0) next.second else prev.second
        }

        // Tier interpolation; a null result means the shapes don't
        // interpolate (mismatched transform lists, runtime-dependent px…)
        // → the same boundary-step rule applies, logged.
        val interpolated = KeyframeValueMath.interpolateTyped(type, prev.second, next.second, eased)
        if (interpolated != null) return interpolated
        reportStepped(type, "non-interpolable payload shapes")
        return if (local >= 1.0) next.second else prev.second
    }

    // Typed tier interpolation + payload readers live in KeyframeValueMath
    // (shared with TransitionDriver — one §2 tier, two triggers).

    // ── §2 step logging (once per type per process) ─────────────────────

    private val steppedLogged = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Log a step-applied property ONCE via PropertyTracker + logcat so
     *  fidelity reports can distinguish "interpolated" from "stepped"
     *  (§2 MUST — the no-silent-fallthrough rule). */
    private fun reportStepped(type: String, why: String) {
        // Namespaced key (Selector[…] precedent) keeps keyframe-step
        // records separate from ordinary unhandled-property records.
        PropertyTracker.markUnhandled("KeyframeStep[$type]")
        if (steppedLogged.add(type)) {
            try {
                android.util.Log.w("KeyframeInterpolator", "keyframe property '$type' step-applied ($why)")
            } catch (_: RuntimeException) {
                // Plain-JVM unit tests: android.util.Log is a stub — the
                // PropertyTracker mark above already recorded the miss.
            }
        }
    }
}
