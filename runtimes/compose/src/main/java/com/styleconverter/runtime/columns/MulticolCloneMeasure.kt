// The Compose half of css-break-3 §5.4 `box-decoration-break: clone` for a
// multicol container's SOLE over-tall child (wave-46 lane Y3): the
// measure-pass integration MultiColumnDistributionLayout delegates to right
// before its slice branch. Split out of MultiColumnApplier.kt (the
// MulticolRunFragmentMeasure precedent) so the B-RC6 bridge pin —
// MultiColumnApplier.kt touches fragmentsState.value exactly 3 times —
// stays intact: THIS file's placement block owns the clone branch's write.
package com.styleconverter.runtime.columns

// The measure→draw snapshot bridge type — written ONLY in the placement
// block below (B-RC6 contract, same as the run/spanner twins).
import androidx.compose.runtime.MutableState
// The Layout-pass types this integration bridges between the pure clone
// geometry and Compose: one measurable in, placement commands out.
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
// Constraints.copy is how the child is capped at the fragmentainer's H.
import androidx.compose.ui.unit.Constraints

/**
 * Measures + places a definite-height multicol container's sole
 * `box-decoration-break: clone` child and publishes the
 * [MulticolCloneGeometry] fragments for the caller's drawWithContent
 * clip+translate replay.
 *
 * THE MEASURING TRICK (why one measure suffices): css-break-3 §5.4 wants
 * every fragment "independently wrapped" with its border, padding and
 * background, radius applied per fragment, a no-repeat image painted once
 * per fragment. A child with no content of its own (the repo's
 * `monolithicContent` = no IR children, no text, no generated content) is
 * NOTHING BUT decoration — so laying it out ONCE at the fragmentainer's
 * block-size H (Modifier.height coerces the declared size into the
 * incoming max, so the declared 240px box becomes the 100px box the
 * fragment needs) yields exactly one complete clone fragment: borders at
 * its edges, corners rounded for THAT box, the background positioned in
 * THAT box. Replaying that paint in every column with no block translate
 * IS the clone render (WPT borders-008: three 100px circles; background-
 * image-007: one cat per column).
 *
 * Returns null whenever the clone pass does NOT engage — the caller then
 * continues into its slice branch byte-identically. Engagement requires
 * (each bail below names its reason — repo no-silent-fallthrough rule;
 * capture gating is inherent because the spec is null outside WPT capture):
 *  - a spec for the sole child, declaring clone, in flow;
 *  - NO content inside the child — a clone child with its own children or
 *    text needs real per-fragment RE-FLOW (its content must advance across
 *    fragments while its decoration repeats), which one measure cannot
 *    express; such children keep the slice replay and log (the corpus'
 *    box-decoration-break-clone-001/002 shape — green on both natives
 *    today under slice, so the honest bail also protects them);
 *  - statically-resolved bands ([MulticolCloneDecoration.bandsFor]);
 *  - a fragmentainer tall enough for some content past the bands.
 *
 * KNOWN APPROXIMATION (documented, pinned): a SHORT last fragment is drawn
 * as two bands of the single H-tall paint (top rows + the block-end
 * decoration band from the paint's bottom — MulticolCloneGeometry). Exact
 * for straight borders and for corner radii no taller than that band;
 * a bottom-anchored background image taller than the band would be cut.
 * The iOS twin re-renders the child at the short size and is exact.
 */
internal object MulticolCloneMeasure {

    fun MeasureScope.measureCloneFragments(
        // The sole child (the caller proved measurables.size == 1).
        measurable: Measurable,
        // The Layout's incoming constraints (post Box loosening).
        constraints: Constraints,
        // The sole child's spec (null outside capture — inherent dark-stage
        // protection, same convention as the run/spanner twins).
        spec: MulticolSpannerFlow.ChildSpec?,
        // C — the child's UNFRAGMENTED border-box block-size from the
        // caller's intrinsic gate probe (minIntrinsicHeight at column
        // width): the same number that proved C > H.
        naturalBlockSizePx: Int,
        // §3.4 used column count (resolveUsedColumns — always ≥ 1).
        usedCount: Int,
        // §3.4 used per-column width in px (always ≥ 0).
        columnWidthPx: Int,
        // Used column-gap in px.
        gapPx: Int,
        // H — the definite column block-size (the caller's gate proved it).
        columnBlockSizePx: Int,
        // The measure→draw bridge state ([] = plain drawContent). Written
        // ONLY inside the placement block below (B-RC6 contract).
        fragmentsBridge: MutableState<List<FragmentGeometry.Fragment>>,
        // One-shot fallback logger (MultiColumnApplier's shared dedupe).
        logFallback: (String) -> Unit
    ): MeasureResult? {
        // No spec → no IR child view (dark stage by construction), and a
        // slice child (the css-break-3 §5.4 default) is the S-table's —
        // neither is a fallthrough, no log.
        if (spec == null || !spec.cloneDeclared) return null
        // Out-of-flow / spanning sole children are not column content to
        // clone (css-position-3 §2.1, css-multicol-1 §6.1) — their owning
        // models already decided; the slice branch's existing behaviour
        // for them stays untouched.
        if (!with(MulticolSpannerFlow) { spec.role.isFlow }) return null
        // The content gate: clone needs per-fragment RE-FLOW for a child
        // with content of its own — not built (see the class doc).
        if (!spec.monolithicContent) {
            logFallback("box-decoration-break: clone on a content-bearing child — per-fragment re-flow not built, slice replay kept")
            return null
        }
        // Bands that did not resolve statically (em/%/calc padding, a
        // percent corner radius) — honest bail, slice kept.
        val bands = spec.cloneBands
        if (bands == null) {
            logFallback("box-decoration-break: clone with a non-px decoration band — slice replay kept")
            return null
        }
        // The pure K-table: null when the bands alone fill the
        // fragmentainer (no content could ever advance).
        val fragments = MulticolCloneGeometry.cloneFragments(
            childBlockSizePx = naturalBlockSizePx,
            columnBlockSizePx = columnBlockSizePx,
            bands = bands,
            columnWidthPx = columnWidthPx,
            columnGapPx = gapPx,
            columnCount = usedCount
        )
        if (fragments == null) {
            logFallback("box-decoration-break: clone bands fill the whole column block-size — slice replay kept")
            return null
        }
        // Measure ONCE at column width, CAPPED at the fragmentainer's H:
        // the child's Modifier.height coerces into this max, so it lays
        // out (and paints) as exactly one full clone fragment — the replay
        // source for every column (the trick in the class doc).
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = columnWidthPx,
                minHeight = 0,
                maxHeight = columnBlockSizePx
            )
        )
        // The container stays exactly H tall (min==max==H under the
        // definite gate) and full width — like the slice branch.
        return layout(constraints.maxWidth, columnBlockSizePx) {
            // Placement-phase publish (B-RC6 contract): placement is
            // skipped by intrinsic measurement and runs once per layout
            // pass; structuralEqualityPolicy swallows equal republishes.
            fragmentsBridge.value = fragments
            // Place the child ONCE at the origin — every visible clone
            // comes from the caller's replay.
            placeable.place(0, 0)
        }
    }
}
