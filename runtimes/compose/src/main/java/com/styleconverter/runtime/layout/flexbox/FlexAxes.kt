package com.styleconverter.runtime.layout.flexbox

// Wave 25 lane CFLEX — CAL-RC2: the SINGLE fold point where a flex
// container's gaps meet its Compose arrangements.
//
// THE DEFECT THIS CLOSES: ComponentRenderer had four flex container call
// sites (Row / Column / FlowRow / FlowColumn) and only TWO of them folded
// the gap into the main-axis arrangement. The wrapping pair passed
// `flexDecision.horizontalArrangement` / `.verticalArrangement` — the
// justify-content-ONLY mapping built by FlexboxApplier.decide, which knows
// nothing about gap — so `column-gap` was dropped in EVERY wrapping flex
// row and `row-gap` in every wrapping flex column (css-align-3 §8.1: the
// gap applies to flex containers regardless of flex-wrap). The cross-axis
// half was already spaced, which is exactly why the bug survived: a
// wrapping row still showed its row-gap, so the missing column-gap read as
// "items are just packed".
//
// WHY A SEPARATE OBJECT AND NOT FOUR MORE CALL SITES: the four branches
// can only stay in step if there is one place that computes all four
// arrangements. [FlexAxes] is that place — the renderer builds it ONCE per
// flex container and each branch reads the field for its own axis pair. A
// future fifth container kind cannot forget the gap, because there is no
// gap-free arrangement left to reach for.
//
// WHY NOT ON FlexDecision ITSELF: FlexboxApplier.decide() consumes a
// LayoutConfig, and LayoutConfig carries no gap fields (gap still lives on
// the legacy DisplayConfig extraction — see the TODO at the renderer's
// `val rowGap = displayConfig.rowGap`). Folding here keeps decide()'s
// contract (and its 23 pinned tests) untouched while still giving the
// renderer a single construction point.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Dp

/**
 * Every arrangement a flex container needs, with its gaps already folded.
 *
 * Axis naming is CSS's, not Compose's: [rowGap] is the BLOCK-axis gap
 * (`row-gap`, the space between flex LINES of a row container) and
 * [columnGap] is the INLINE-axis gap (`column-gap`, the space between
 * items of a row container). Which one is "main" therefore depends on the
 * container's direction, and that is precisely the mapping this type
 * pre-computes so the renderer never has to redo it.
 *
 * @param mainHorizontal  main axis of a Row / FlowRow: justify-content
 *        composed with `column-gap` (FlexboxApplier.mainAxisHorizontal).
 * @param mainVertical    main axis of a Column / FlowColumn: justify-content
 *        composed with `row-gap`.
 * @param crossHorizontal cross axis of a FlowColumn — line-to-line spacing
 *        along the inline axis, so `column-gap`. Pure spacing: CSS
 *        `align-content` is not modelled by Compose's Flow* containers.
 * @param crossVertical   cross axis of a FlowRow — line-to-line spacing
 *        along the block axis, so `row-gap`.
 * @param rowGap          the raw block-axis gap, kept for the wrapping
 *        layout's own line-breaking arithmetic (FlexWrapRow).
 * @param columnGap       the raw inline-axis gap, same reason.
 */
data class FlexAxes(
    val mainHorizontal: Arrangement.Horizontal,
    val mainVertical: Arrangement.Vertical,
    val crossHorizontal: Arrangement.Horizontal,
    val crossVertical: Arrangement.Vertical,
    val rowGap: Dp,
    val columnGap: Dp
) {
    companion object {
        /**
         * Fold a decision's justify-content keyword together with the
         * container's gaps into the four arrangements above.
         *
         * A companion factory rather than an extension function so the
         * renderer can call it fully-qualified — ComponentRenderer names
         * every flexbox symbol by full path, which keeps four concurrent
         * lanes from colliding on its import block.
         *
         * BYTE-STABILITY (dark stage): with both gaps at 0dp,
         * FlexboxApplier.mainAxisHorizontal/Vertical return the SAME
         * objects `toHorizontalArrangement`/`toVerticalArrangement`
         * return for every keyword — Start/End/Center/Space* — so a
         * gap-free container's arrangement is the identical instance it
         * used before this fold existed. Only a container that actually
         * declares a main-axis gap moves.
         */
        fun of(decision: FlexDecision, rowGap: Dp, columnGap: Dp): FlexAxes = FlexAxes(
            // Row / FlowRow main axis — gap between ITEMS is the inline gap.
            mainHorizontal = FlexboxApplier.mainAxisHorizontal(decision.justify, columnGap),
            // Column / FlowColumn main axis — item gap is the block gap.
            mainVertical = FlexboxApplier.mainAxisVertical(decision.justify, rowGap),
            // FlowColumn cross axis — line-to-line gap runs inline.
            crossHorizontal = Arrangement.spacedBy(columnGap),
            // FlowRow cross axis — line-to-line gap runs block-wise.
            crossVertical = Arrangement.spacedBy(rowGap),
            rowGap = rowGap,
            columnGap = columnGap
        )
    }
}
