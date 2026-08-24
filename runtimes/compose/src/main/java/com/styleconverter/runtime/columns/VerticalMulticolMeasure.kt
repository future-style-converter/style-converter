// Wave-47 lane Z2 — the Compose measure half of VERTICAL-writing-mode
// multicol fragmentation (css-multicol-1 §2 + css-break-3 §4 with the
// container's inline axis VERTICAL): the integration
// MultiColumnDistributionLayout delegates to BEFORE any horizontal-tb pass.
// Split out of MultiColumnApplier.kt (the MulticolCloneMeasure precedent) so
// the B-RC6 bridge pin — MultiColumnApplier.kt touches fragmentsState.value
// exactly 3 times — stays intact: THIS file's placement block owns the
// vertical branch's write.
package com.styleconverter.runtime.columns

// The measure→draw snapshot bridge type — written ONLY in the placement
// block below (B-RC6 contract, same as the clone/run/spanner twins).
import androidx.compose.runtime.MutableState
// The Layout-pass types this integration bridges between the pure vertical
// geometry and Compose: one measurable in, placement commands out.
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
// Constraints caps the child at the column's inline size (its height here).
import androidx.compose.ui.unit.Constraints

/**
 * Measures + places a vertical-writing-mode multicol container's sole flow
 * child and publishes [VerticalFragmentGeometry] fragments for the caller's
 * drawWithContent clip+translate replay (the same replay the horizontal
 * S-table feeds — Fragment is axis-agnostic clip+translate data).
 *
 * Geometry recap (details on [VerticalFragmentGeometry]): the column boxes
 * stack VERTICALLY (inline axis is vertical), each colH tall; the child lays
 * out ONCE as a continuous C-wide × colH-tall box and each fragment shows
 * one W-wide physical band of it — right-aligned bands walking leftward for
 * vertical-rl, left-aligned walking rightward for vertical-lr.
 *
 * Returns null whenever the pass does NOT engage — the caller then continues
 * into the frozen paths (whose own vertical bails keep logging) byte-
 * identically. Engagement gates, each named (no-silent-fallthrough rule):
 *  - specs present (null outside WPT capture — inherent dark-stage
 *    protection, the clone/run twins' convention) and exactly ONE spec: the
 *    sole flow child, no leading text;
 *  - NOT a post-load-extracted wire: a child carrying baked physical
 *    Width+Height (the post-load extractor's signature) already encodes the
 *    browser's used layout — including its fragmentation — so re-fragmenting
 *    would double-apply (the anchor-position-multicol family, which PASSES
 *    today on the baked geometry, must keep its frozen path);
 *  - `box-decoration-break: clone` declared → logged bail (the clone
 *    re-render trick is horizontal-only today);
 *  - a bounded inline extent (maxHeight) and a DEFINITE block extent
 *    (fixed width — the boundary-threaded signal, wave-11's lesson) with
 *    usedCount ≥ 2 (a 1-column container overflows, never clips — §8.2).
 */
internal object VerticalMulticolMeasure {

    fun MeasureScope.measureVerticalSoleChild(
        // The Layout's measurables (the pass engages only on exactly one).
        measurables: List<Measurable>,
        // The Layout's incoming constraints (post Box loosening).
        constraints: Constraints,
        // Capture-gated per-measurable specs (null = dark stage, decline).
        childSpecs: List<MulticolSpannerFlow.ChildSpec>?,
        // The used column count N (≥ 1, resolved by the caller from the
        // container's declared count — vertical basis).
        usedCount: Int,
        // Used column-gap in px.
        gapPx: Int,
        // Wave-11 twin for the BLOCK axis (= width here): true when the
        // MultiColumnLayout boundary saw TIGHT width constraints — the Box
        // between that boundary and this Layout loosens minWidth to 0, so
        // hasFixedWidth is unreadable here.
        containerBlockAxisDefinite: Boolean,
        // true for vertical-rl / sideways-rl (block axis right→left).
        blockRtl: Boolean,
        // The measure→draw bridge state ([] = plain drawContent). Written
        // ONLY inside the placement block below (B-RC6 contract).
        fragmentsBridge: MutableState<List<FragmentGeometry.Fragment>>,
        // One-shot fallback logger (MultiColumnApplier's shared dedupe).
        logFallback: (String) -> Unit
    ): MeasureResult? {
        // No specs → dark stage or a text-only container: decline silently
        // (the frozen paths' own vertical bail still logs downstream).
        if (childSpecs == null) return null
        // Sole-flow-child family only (leading text would be spec[0]):
        // multi-child vertical balancing is not built — the frozen paths'
        // "vertical writing-mode (blocked-platform)" log stays the record.
        if (childSpecs.size != 1 || measurables.size != 1) return null
        val spec = childSpecs[0]
        // Out-of-flow / spanning sole children are not column content
        // (css-position-3 §2.1, css-multicol-1 §6.2) — decline, keep the
        // frozen behaviour for their owning models.
        if (!with(MulticolSpannerFlow) { spec.role.isFlow }) return null
        // Post-load-extracted wires bake the browser's used physical layout
        // (see the class doc) — the frozen greedy render of those tests is
        // already correct, so fragmenting again would break passing cells.
        if (spec.bakedPhysicalSize) {
            logFallback("vertical multicol: post-load-extracted baked layout — fragmentation already encoded, frozen path kept")
            return null
        }
        // Clone is horizontal-only today (MulticolCloneMeasure) — honest bail.
        if (spec.cloneDeclared) {
            logFallback("vertical multicol: box-decoration-break clone not built for vertical writing modes — frozen path kept")
            return null
        }
        // Axis extents: the INLINE extent (height) sizes the column boxes,
        // the BLOCK extent (width) is the fragmentainer W.
        if (constraints.maxHeight == Constraints.Infinity) {
            logFallback("vertical multicol: unbounded inline extent (no column inline size to fit)")
            return null
        }
        if (!(containerBlockAxisDefinite || constraints.hasFixedWidth) ||
            constraints.maxWidth == Constraints.Infinity
        ) {
            // css-break-3 §2: no definite fragmentainer block-size → the
            // container grows instead of fragmenting.
            logFallback("vertical multicol: indefinite block extent (width) — no fragmentainer to break at")
            return null
        }
        // §8.2: with one column there is nothing to clip into — overflow
        // behaviour belongs to the frozen path (the anchor-position
        // N==1 shape).
        if (usedCount < 2) return null
        val w = constraints.maxWidth
        // colH — the used column inline size (§3.4 transposed; shared
        // helper so the V-table test pins the same number).
        val colH = VerticalFragmentGeometry.columnInlineSizePx(
            constraints.maxHeight, usedCount, gapPx)
        if (colH <= 0) {
            logFallback("vertical multicol: zero column inline size — nothing to lay into")
            return null
        }
        // Measure ONCE: block axis (width) unbounded so the child lays out
        // (and paints) its full continuous block extent C; inline axis
        // capped at colH so its stretch-fit inline size is the column's
        // (css-writing-modes-4 §7.3 via the renderer's fillMaxHeight fold).
        val placeable = measurables[0].measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = Constraints.Infinity,
                minHeight = 0,
                maxHeight = colH
            )
        )
        // Geometry from the MEASURED width (the true laid-out C).
        val fragments = VerticalFragmentGeometry.fragmentGeometry(
            childBlockSizePx = placeable.width,
            columnBlockSizePx = w,
            columnInlineSizePx = colH,
            columnGapPx = gapPx,
            columnCount = usedCount,
            blockRtl = blockRtl,
        )
        // The container keeps its own W × inline extent, like the frozen
        // paths keep theirs (both are tight under the gates above).
        return layout(w, constraints.maxHeight) {
            // Placement-phase publish (B-RC6 contract): placement is
            // skipped by intrinsic measurement and runs once per layout
            // pass; structuralEqualityPolicy swallows equal republishes.
            fragmentsBridge.value = fragments
            // Place the child ONCE at the origin — every visible band
            // comes from the caller's clip+translate replay.
            placeable.place(0, 0)
        }
    }
}
