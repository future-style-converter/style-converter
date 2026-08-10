package com.styleconverter.runtime.layout.flexbox

// Wave 39 (lane A6) — css-flexbox-1 §4.5 AUTOMATIC MINIMUM SIZE for the
// LEGACY flex loop.
//
// ## The measured defect (frozen wave38-final Android captures)
// `ComponentRenderer.flexLineSpec` opens with an `anyFlex` gate: the line is
// only handed to the §9.7 resolver (static) or to FlexIntrinsicRow/Column
// (intrinsic) when SOME child declares `FlexBasis` / `FlexGrow` /
// `FlexShrink`. A flex container whose children declare NONE of those — the
// CSS-initial `flex: 0 1 auto` item, which is still a flex item — falls
// through to the plain Compose `Row`/`Column`, and Compose measures a
// non-weighted child with `maxWidth = the space left over`. When the
// container's own main size is ZERO, that leftover is zero and the item
// collapses to nothing.
//
// CSS never lets that happen: §4.5 gives an item whose `min-width` /
// `min-height` computes to `auto` an automatic minimum size — the
// content-based minimum — so a flex item overflows a too-small container
// instead of vanishing.
//
// MEASURED on the six `css-values/calc-size/calc-size-flex-00{1..6}` tests
// (a `display: flex` container with a ZERO main size and one item holding a
// fixed 60/80px grandchild). The reference paints a 100×100 green square;
// iOS paints the 80×100 content-based minimum and PASSES (ssim 0.9751 …
// 0.9819); Android painted NO GREEN AT ALL and scored 0.9441 on all six.
// The row/column split of the family is even (001-003 row, 004-006 column),
// which is what identifies the mechanism as axis-agnostic rather than a
// width-specific bug.
//
// ## Why a floor and not a re-plumb of the `anyFlex` gate
// Relaxing `anyFlex` would route EVERY plain flex container in the frozen
// corpus through the resolver — a whole-corpus behaviour change for a
// six-test defect. This modifier changes an item's used main size ONLY when
// the container squeezes it BELOW its own min-content size, which is exactly
// the state §4.5 forbids; every item that already fits is measured with
// byte-identical constraints.
//
// COMPOSED-WPT CAPTURE ONLY (the caller gates on `LocalWptComposedMode`), so
// the 327-pair baseline stage and the per-component inbox path never see it.

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.styleconverter.runtime.layout.IntrinsicChannel

object FlexAutoMinSize {

    // ── Wave 39 hotfix: the intrinsic channel is OPTIONAL on this platform ──
    //
    // §4.5's "content size suggestion" is read through Compose's intrinsic
    // channel, and Compose does NOT guarantee that channel exists: the
    // SubcomposeLayout family refuses it by THROWING. The mechanism — which
    // layouts refuse, the ui-android 1.11.4 bytecode proof, the narrow-by-
    // type rule — now lives in [IntrinsicChannel], hoisted there when the
    // table's §17.5.2 / §17.5.3 intrinsic reads (TableApplier) picked up
    // the same hazard. What stays HERE is this lane's measured history:
    //
    // MEASURED (wave39-final vs wave38-final, css-multicol Android): the
    // section captured 45 tests at wave38-final and only 36 at wave39-final.
    // All NINE lost tests — `as-column-flex-item` and `baseline-000` …
    // `baseline-007` — are the same shape: a `display: flex` container whose
    // item IS (or contains) a multicol box, and NO child of which declares a
    // flex property, i.e. exactly the `lineSpec == null` line this modifier
    // wraps. `MultiColumnApplier.MultiColumnLayout` renders through
    // `BoxWithConstraints`, so the §4.5 probe threw, the capture composition
    // died, and the feeder recorded `TIMEOUT (0/1 PNGs)` on both the first
    // pass and the retry. `baseline-008` — the one baseline test that is
    // `display: inline-block`, not flex — captured fine in both waves, which
    // is what identifies the flex wrapper rather than multicol itself as the
    // trigger.
    //
    // The guard is a SUPPORTS-CHECK, not a multicol special case: any
    // subtree that refuses intrinsics (grid, table, scroll, sticky,
    // container-query and line-clamp all reach a SubcomposeLayout in this
    // runtime too) falls back to the pre-wave-39 measure instead of taking
    // the whole capture down with it. It is deliberately NOT a list of
    // known-bad IR types — such a list drifts the moment a renderer swaps in
    // a subcomposed layout, and the platform already tells us the answer.

    // ── MECHANISM GATE — measured OFF ──────────────────────────────────
    //
    // The §4.5 floor below is CORRECT css-flexbox-1 and its arithmetic is
    // pinned by this module's JVM suite, but on the frozen depth-48 corpus it
    // is measured to win NOTHING and cost real cells, so it ships disarmed.
    //
    // ISOLATED MEASUREMENT (private emulator, composed capture, two APKs of
    // the SAME base commit 46abf94a — `arm1-pristine` and `arm2-a6` = arm1
    // plus lane A6's edits only, so every other wave-39 lane is absent from
    // both and any movement between them is A6's alone):
    //
    //   css-contain (48 tests): ONE cell moves —
    //     contain-inline-size-flexitem  0.9851 PASS → 0.9340 FAIL
    //   css-values  (48 tests): ONE cell moves —
    //     calc-height-table-1  0.8119 → 0.8990 (still fail, and that one is
    //     lane A6's *table* mechanism, not this file)
    //   The six tests this lane was BUILT for —
    //     css-values/calc-size/calc-size-flex-001…006 — do not move at all.
    //     (They read 0.9441 → 0.9542 across wave38→wave39, which is lane A1's
    //     corpus-wide half-leading uplift, not this floor.)
    //
    // Plus, in the integrated wave-39 tree, NINE css-multicol captures
    // (as-column-flex-item, baseline-000…007) died outright — see the
    // supports-check banner below. That failure is now guarded, but the
    // guard only stops the mechanism from wedging captures; it cannot make a
    // mechanism that wins zero cells worth its one measured loss.
    //
    // So: 0 wins, 1 pass lost, 9 captures lost. Disarmed, not deleted — the
    // diagnosis (Compose's Row gives a non-weighted item only the leftover
    // main space, which is nothing when the container's main size is zero) is
    // real, and the next lane that can make §4.5 pay for itself re-arms it by
    // flipping this one constant and re-running the two isolation sections.
    const val MECHANISM_ENABLED: Boolean = false

    /**
     * Run an intrinsic query that the platform is allowed to refuse.
     *
     * `internal`, not private, so the JVM suite can pin the refusal contract
     * without Robolectric — the standing shape of this module's tests.
     * A thin delegate: the guard itself (type-narrowed catch, log-once) is
     * [IntrinsicChannel.probe]; only this lane's log wording lives here.
     *
     * @return the queried intrinsic, or `null` when this subtree has no
     *   intrinsic channel at all. `null` is NOT a silent fallthrough: the
     *   caller drops to the frozen measure and the refusal is logged once.
     */
    internal fun probeIntrinsic(query: () -> Int): Int? =
        IntrinsicChannel.probe(
            logTag = "FlexAutoMinSize",
            refusalContext = "css-flexbox-1 §4.5 automatic minimum size skipped — " +
                "this flex item's subtree has no intrinsic channel; the item " +
                "keeps the pre-wave-39 measure.",
            query = query
        )

    /**
     * The main-axis constraint band a flex item must be measured with once
     * §4.5's automatic minimum size is honoured.
     *
     * Pure (no Compose layout types beyond the two Ints) so the whole rule is
     * pinnable on the JVM without Robolectric — the standing constraint of
     * this suite.
     *
     * @param availableMax the main-axis `maxWidth`/`maxHeight` the flex
     *   container handed down (what Compose's Row/Column leaves over).
     * @param contentMinMain the item's MIN-CONTENT main size, read through
     *   Compose's intrinsic channel (`minIntrinsicWidth`/`minIntrinsicHeight`
     *   — the same channel §4.5 calls the "content size suggestion"). Note
     *   the item's own `Modifier.width/height` already folds the "specified
     *   size suggestion" into this number, because a fixed-size modifier
     *   reports that fixed size as its intrinsic.
     * @return `(min, max)` for the main axis: the floor is the content
     *   minimum, and the ceiling opens up to it when the container offered
     *   less — an item overflows rather than collapsing (§4.5).
     */
    fun mainBand(availableMax: Int, contentMinMain: Int): Pair<Int, Int> {
        // A negative or absurd intrinsic (a mis-reporting child) must never
        // become a constraint — clamp at zero, which is the no-op floor.
        val floor = contentMinMain.coerceAtLeast(0)
        // Unbounded available space cannot squeeze anything, so the ceiling
        // stays Infinity; the floor is still applied because an item at its
        // min-content is what an unbounded measure would produce anyway.
        if (availableMax == Constraints.Infinity) return floor to Constraints.Infinity
        // Otherwise: never shrink below the floor, and raise the ceiling to
        // the floor so the constraint band stays well-formed (min <= max).
        return floor to maxOf(availableMax, floor)
    }

    /**
     * Does this item need the §4.5 floor at all?
     *
     * `false` when the container already offers at least the item's
     * min-content size — the overwhelmingly common case, and the one where
     * skipping the wrapper keeps the measure pass byte-identical.
     */
    fun squeezed(availableMax: Int, contentMinMain: Int): Boolean =
        availableMax != Constraints.Infinity && availableMax < contentMinMain.coerceAtLeast(0)

    /**
     * Wrap a flex item so its used MAIN size never falls below its
     * content-based minimum (§4.5).
     *
     * @param enabled the caller's gate — composed-WPT capture only. `false`
     *   returns the receiver UNTOUCHED (not a no-op wrapper), so no extra
     *   layout node exists on the frozen paths.
     * @param rowAxis true for a row container (main axis = width), false for
     *   a column container (main axis = height).
     */
    fun Modifier.flexAutoMinMain(enabled: Boolean, rowAxis: Boolean): Modifier {
        if (!enabled) return this
        return this.layout { measurable, constraints ->
            // The §4.5 "content size suggestion", or null when this subtree
            // has no intrinsic channel (see probeIntrinsic's banner). The
            // cross-axis hint is the container's own bound so a wrapping text
            // item reports the min-content it will actually lay out at.
            val contentMin = probeIntrinsic {
                if (rowAxis) measurable.minIntrinsicWidth(
                    if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
                ) else measurable.minIntrinsicHeight(
                    if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
                )
            }
            // REFUSED ⇒ no §4.5 floor is knowable here, so measure with the
            // constraints the container handed down — byte-for-byte the
            // measure this item got before this lane existed. (The wrapper
            // layout node itself remains, but a pass-through `measure` +
            // own-size `layout` is the identity measure policy.)
            val band = if (contentMin == null) null else if (rowAxis)
                mainBand(constraints.maxWidth, contentMin)
            else
                mainBand(constraints.maxHeight, contentMin)
            val placeable = measurable.measure(
                when {
                    band == null -> constraints
                    rowAxis -> constraints.copy(minWidth = band.first, maxWidth = band.second)
                    else -> constraints.copy(minHeight = band.first, maxHeight = band.second)
                }
            )
            // Report the item's OWN size: an overflowing item must not shrink
            // its reported box, or the container would place the next item on
            // top of it.
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    }
}
