package com.styleconverter.runtime.layout

// IntrinsicChannel — the ONE guard for Compose's optional intrinsic channel.
//
// Compose does NOT guarantee a subtree can answer intrinsic measurements.
// Every layout built on SubcomposeLayout — `BoxWithConstraints`, the lazy
// lists, `TabRow` — installs `LayoutNode.NoIntrinsicsMeasurePolicy`, whose
// four intrinsic entry points do nothing but throw. Read off the shipped
// bytecode of androidx.compose.ui:ui-android 1.11.4 (the BOM this module
// builds against):
//
//   LayoutNode$NoIntrinsicsMeasurePolicy.minIntrinsicWidth(…):
//     0: new  java/lang/IllegalStateException
//     …
//    14: athrow                      ← nothing else executes
//
//   LayoutNodeSubcompositionsState$createMeasurePolicy$1
//     extends LayoutNode$NoIntrinsicsMeasurePolicy(NoIntrinsicsMessage)
//   NoIntrinsicsMessage = "Asking for intrinsic measurements of
//     SubcomposeLayout layouts is not supported. This includes components
//     that are built on top of SubcomposeLayout, such as lazy lists,
//     BoxWithConstraints, TabRow, etc. …"
//
// In THIS runtime the multicol, grid, scroll, sticky, container-query and
// line-clamp renderers all reach a SubcomposeLayout, so ANY mechanism that
// reads a descendant's intrinsics can meet the throw — and an unguarded
// throw does not mis-size a box, it KILLS the capture composition (feeder
// `TIMEOUT`, no PNG). Measured precedent: wave 39's flex §4.5 probe cost
// css-multicol nine Android captures before it grew this guard — the full
// measured history lives in [FlexAutoMinSize]'s banner.
//
// Hoisted out of FlexAutoMinSize (wave 39 hotfix) when the table's
// §17.5.2 shrink-to-fit width and §17.5.3 row height picked up the same
// hazard (TableApplier's call sites). One implementation, because the
// subtlety (narrow the catch by TYPE, log the refusal ONCE, reproduce
// IntrinsicSizeModifier's math bound-for-bound) is exactly the kind of
// thing copies let drift apart.
//
// This is a SUPPORTS-CHECK, not a list of known-bad IR types: such a list
// drifts the moment a renderer swaps in a subcomposed layout, and the
// platform already tells us the answer.

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import java.util.Collections

object IntrinsicChannel {

    /** One log line per distinct (caller, refusal) pair — never per-frame. */
    private val refusalsLogged =
        Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Run an intrinsic query that the platform is allowed to refuse.
     *
     * @param logTag the caller's Log tag, so a refusal in logcat points at
     *   the mechanism that fell back rather than at this shared guard.
     * @param refusalContext one sentence, in the caller's own vocabulary,
     *   saying WHAT was skipped and WHAT measure the box keeps instead —
     *   the no-silent-fallthrough rule applied to logs: a table refusal
     *   must not read as a flexbox one.
     * @return the queried intrinsic, or `null` when this subtree has no
     *   intrinsic channel at all. `null` is NOT a silent fallthrough: the
     *   caller drops to its frozen measure and the refusal is logged once.
     */
    fun probe(logTag: String, refusalContext: String, query: () -> Int): Int? =
        try {
            query()
        } catch (refused: IllegalStateException) {
            // Narrow by TYPE, not by message text: IllegalStateException is
            // the exact and only thing NoIntrinsicsMeasurePolicy raises, and
            // matching the prose would silently stop working the next time
            // androidx reworks that string. Anything else (an
            // IllegalArgumentException out of Constraints packing, say) is a
            // real bug and keeps propagating.
            logRefusalOnce(logTag, refusalContext, refused.message ?: "no message")
            null
        }

    /** First caller per distinct (tag, message) wins the log (atomic `add`). */
    private fun logRefusalOnce(logTag: String, refusalContext: String, message: String) {
        // Key by tag + platform message so two DIFFERENT mechanisms hitting
        // the same subtree each get their one line — a table fallback must
        // not be silenced because a flex item logged first.
        if (refusalsLogged.add("$logTag|$message")) {
            // runCatching: android.util.Log is absent on the JVM test
            // classpath, and a logging failure must never fail a measure.
            runCatching {
                android.util.Log.i(
                    logTag,
                    "$refusalContext Platform said: $message"
                )
            }
        }
    }

    // ── Guarded twins of `Modifier.width/height(IntrinsicSize.*)` ─────────
    //
    // ## Exactness contract
    // Where the channel EXISTS these modifiers must be measure-for-measure
    // identical to foundation-layout's originals, or captures won on them
    // move. The original's measure is, verbatim from IntrinsicSizeModifier
    // (enforceIncoming = true for the plain `width()`/`height()` spellings;
    // width(IntrinsicSize.Max) shown, the height variant transposed):
    //
    //   w = measurable.maxIntrinsicWidth(constraints.maxHeight)
    //   placeable = measurable.measure(constraints.constrain(Constraints.fixedWidth(w)))
    //   layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    //
    // `constrain(fixedWidth(w))` resolves to: sized-axis min = max =
    // `w.coerceIn(incoming.min, incoming.max)`; the OTHER axis kept verbatim
    // (fixedWidth's 0..Infinity height collapses back onto the incoming band
    // under `coerceIn`). [fixedBand] pins that arithmetic on the JVM — the
    // same "extract the decision, pin it without Robolectric" shape as
    // TableBoxTree / FlexAutoMinSize.

    /**
     * The sized-axis constraint band once an intrinsic answer is in hand.
     *
     * Pure (three Ints in, two out) so the whole decision is pinnable on
     * the JVM without Robolectric — the standing constraint of this suite.
     *
     * @param intrinsic the probed intrinsic size, or `null` when the
     *   subtree has no intrinsic channel ([probe] refused).
     * @param min the incoming `Constraints` minimum on the sized axis.
     * @param max the incoming maximum on the sized axis (may be Infinity).
     * @return `(min, max)` for the sized axis. REFUSED ⇒ the incoming band
     *   unchanged — byte-for-byte the measure the box got before the
     *   intrinsic mechanism existed, which mis-sizes ONE box instead of
     *   killing the whole capture composition.
     */
    fun fixedBand(intrinsic: Int?, min: Int, max: Int): Pair<Int, Int> {
        // No channel ⇒ no intrinsic size is knowable here; keep the frozen
        // measure (deliberate, logged by [probe], not silent).
        if (intrinsic == null) return min to max
        // IntrinsicSizeModifier's `constrain(fixed*(w))`, sized axis: both
        // bounds become the intrinsic clamped into the incoming band. The
        // upper clamp gives `min(intrinsic, available)`; the lower keeps a
        // parent-imposed min (and a mis-reported negative intrinsic can
        // never produce an invalid Constraints).
        val fixed = intrinsic.coerceIn(min, max)
        return fixed to fixed
    }

    /**
     * `Modifier.width(IntrinsicSize.Max)` with the intrinsic read guarded.
     *
     * Member extension (call via `with(IntrinsicChannel) { … }`) — the
     * FlexAutoMinSize.flexAutoMinMain call-site shape.
     *
     * @param logTag / @param refusalContext see [probe] — the caller says,
     *   in its own CSS vocabulary, what was skipped and what measure the
     *   box keeps instead.
     */
    fun Modifier.widthAtMaxIntrinsic(logTag: String, refusalContext: String): Modifier =
        layout { measurable, constraints ->
            // The max-content width, read exactly the way
            // MaxIntrinsicWidthNode reads it (height hint = incoming
            // maxHeight, Infinity allowed) — or null on refusal.
            val intrinsic = probe(logTag, refusalContext) {
                measurable.maxIntrinsicWidth(constraints.maxHeight)
            }
            // The pinned decision: fixed-at-intrinsic when answered,
            // incoming band when refused. Height band untouched either way
            // (see the exactness contract above).
            val (minW, maxW) = fixedBand(intrinsic, constraints.minWidth, constraints.maxWidth)
            val placeable = measurable.measure(
                constraints.copy(minWidth = minW, maxWidth = maxW)
            )
            // Report the child's own size, place at the origin —
            // IntrinsicSizeModifier's exact epilogue (placeRelative, so RTL
            // mirroring stays whatever the original did).
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }

    /**
     * `Modifier.height(IntrinsicSize.Min)` with the intrinsic read guarded.
     *
     * The height transposition of [widthAtMaxIntrinsic], reproducing
     * MinIntrinsicHeightNode: width hint = incoming maxWidth, sized axis =
     * height, intrinsic = MIN (a row is as tall as its tallest cell needs,
     * not as tall as it could be).
     */
    fun Modifier.heightAtMinIntrinsic(logTag: String, refusalContext: String): Modifier =
        layout { measurable, constraints ->
            // The min-content height under the incoming width — or null on
            // refusal (same channel, same throw, same guard).
            val intrinsic = probe(logTag, refusalContext) {
                measurable.minIntrinsicHeight(constraints.maxWidth)
            }
            // Same pinned decision, height axis; width band untouched.
            val (minH, maxH) = fixedBand(intrinsic, constraints.minHeight, constraints.maxHeight)
            val placeable = measurable.measure(
                constraints.copy(minHeight = minH, maxHeight = maxH)
            )
            // Same epilogue as the width twin.
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }
}
