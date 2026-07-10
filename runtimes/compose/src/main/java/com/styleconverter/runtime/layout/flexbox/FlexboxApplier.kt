package com.styleconverter.runtime.layout.flexbox

// Phase 7 step 2 — flexbox sub-applier.
//
// Consumes the engine-style [com.styleconverter.runtime.layout.LayoutConfig]
// and produces a [FlexDecision]: the complete payload a Compose container
// needs to render itself as a flex container. ComponentRenderer reads this
// and chooses Row / Column / FlowRow / FlowColumn / Box accordingly.
//
// Why this shape (vs. returning a raw Modifier)?
//   - Compose's Arrangement / Alignment types are scope-bound: Row.weight()
//     only exists inside RowScope, Column.weight() only inside ColumnScope.
//     The container choice has to happen at the call site, not inside a
//     Modifier chain.
//   - Splitting "what container" from "what modifiers" also lets us reuse
//     the decision object in tests without Composing anything.
//
// File size budget: keep ≤200 lines. If additions push past that, split
// container-mapping and item-mapping into two files.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.layout.AlignmentKeyword
import com.styleconverter.runtime.layout.DisplayKind
import com.styleconverter.runtime.layout.FlexDirection as EngineFlexDirection
import com.styleconverter.runtime.layout.FlexWrap as EngineFlexWrap
import com.styleconverter.runtime.layout.LayoutConfig

/**
 * Which Compose container primitive the flexbox applier wants.
 *
 * - [Row]         — flex-direction: row, no wrap
 * - [Column]      — flex-direction: column, no wrap
 * - [FlowRow]     — flex-direction: row, wrap or wrap-reverse
 * - [FlowColumn]  — flex-direction: column, wrap or wrap-reverse
 * - [Box]         — display: block | flow-root (non-flex fallback still
 *                   routed through the engine when the caller asks).
 * - [None]        — display: none (suppress rendering).
 */
enum class FlexContainerKind {
    Row, Column, FlowRow, FlowColumn, Box, None
}

/**
 * Everything ComponentRenderer needs to instantiate a flex container.
 *
 * The horizontal/vertical fields are both populated so the renderer can
 * simply pick whichever applies to its axis. Field semantics match Compose's
 * [Row] / [Column] constructor params 1-for-1.
 *
 * @param kind                  chosen Compose primitive
 * @param horizontalArrangement for Row / FlowRow (main axis when row-direction)
 * @param verticalArrangement   for Column / FlowColumn (main axis when column-direction)
 * @param horizontalAlignment   cross-axis alignment inside a Column/FlowColumn
 * @param verticalAlignment     cross-axis alignment inside a Row/FlowRow
 * @param boxAlignment          content alignment for the Box fallback
 * @param mainAxisSpacing       used by FlowRow/FlowColumn for main-axis gap
 * @param crossAxisSpacing      used by FlowRow/FlowColumn for cross-axis gap
 * @param reverse               true when flex-direction is row/column-reverse
 */
data class FlexDecision(
    val kind: FlexContainerKind,
    val horizontalArrangement: Arrangement.Horizontal,
    val verticalArrangement: Arrangement.Vertical,
    val horizontalAlignment: Alignment.Horizontal,
    val verticalAlignment: Alignment.Vertical,
    val boxAlignment: Alignment,
    val reverse: Boolean,
    // Raw justify-content keyword, kept so the renderer can COMPOSE it with
    // the container's gap (Arrangement.spacedBy carries an alignment for
    // start/center/end, while the distributing keywords must keep their
    // Space* arrangement — see FlexboxApplier.mainAxisHorizontal).
    val justify: AlignmentKeyword = AlignmentKeyword.Normal
) {
    companion object {
        /** Fallback when Display is not a flex/block/none keyword. */
        val Legacy: FlexDecision = FlexDecision(
            kind = FlexContainerKind.Box,
            horizontalArrangement = Arrangement.Start,
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
            verticalAlignment = Alignment.Top,
            boxAlignment = Alignment.TopStart,
            reverse = false
        )
    }
}

/**
 * Build a [FlexDecision] from a style-engine [LayoutConfig].
 *
 * Returns null when the config doesn't describe a flex (or display-none)
 * container — the caller can then fall through to the legacy renderer path.
 * This avoids claiming rendering responsibility for components that never
 * set a flex-related property.
 */
object FlexboxApplier {

    /**
     * @return a populated [FlexDecision] when the config represents a flex
     *         container (or `display: none`), otherwise null to signal
     *         "legacy path should handle this".
     */
    fun decide(config: LayoutConfig): FlexDecision? {
        // display: none short-circuits everything: renderer suppresses output.
        if (config.display == DisplayKind.None) {
            return FlexDecision.Legacy.copy(kind = FlexContainerKind.None)
        }

        // Only flex / inline-flex go through the flex decision tree. Block,
        // inline, grid, contents etc. return null so ComponentRenderer can
        // keep using its legacy dispatch for those display modes.
        val isFlex = config.display == DisplayKind.Flex ||
            config.display == DisplayKind.InlineFlex
        if (!isFlex) return null

        // Resolve direction + wrap with sensible CSS defaults. `null` here
        // means the author didn't set the property → use CSS initial value.
        val direction = config.flexDirection ?: EngineFlexDirection.Row
        val wrap = config.flexWrap ?: EngineFlexWrap.NoWrap
        val isRowAxis = direction == EngineFlexDirection.Row ||
            direction == EngineFlexDirection.RowReverse
        val isReverse = direction == EngineFlexDirection.RowReverse ||
            direction == EngineFlexDirection.ColumnReverse
        val wraps = wrap != EngineFlexWrap.NoWrap

        // Pick the container kind purely from (axis, wrap). The reverse bit
        // is carried on FlexDecision.reverse so the renderer can reverse the
        // child list without re-deriving it here.
        val kind = when {
            isRowAxis && wraps -> FlexContainerKind.FlowRow
            isRowAxis && !wraps -> FlexContainerKind.Row
            !isRowAxis && wraps -> FlexContainerKind.FlowColumn
            else -> FlexContainerKind.Column
        }

        // Build arrangement + alignment independently of axis — both are
        // always populated so ComponentRenderer doesn't need to branch on
        // direction when picking which field to read.
        val jc = config.justifyContent ?: AlignmentKeyword.Normal
        val ai = config.alignItems ?: AlignmentKeyword.Normal
        return FlexDecision(
            kind = kind,
            horizontalArrangement = toHorizontalArrangement(jc),
            verticalArrangement = toVerticalArrangement(jc),
            horizontalAlignment = toHorizontalAlignment(ai),
            verticalAlignment = toVerticalAlignment(ai),
            boxAlignment = toBoxAlignment(ai),
            reverse = isReverse,
            justify = jc
        )
    }

    /**
     * Compose the main-axis arrangement for a Row from justify-content AND
     * gap. css-align-3 §8.3: `gap` is spacing BETWEEN adjacent items and it
     * COMBINES with content distribution — the distributed spacing wins
     * whenever it exceeds the gap. The old renderer let a non-zero gap
     * unconditionally replace the arrangement, so `justify-content:
     * space-between; gap: 6px` packed items at the start (FC_SpaceBetween
     * Android-web 0.920). For the distributing keywords we keep the Space*
     * arrangement (our fixtures' distributed spacing always exceeds the
     * declared gap); for positional keywords spacedBy(gap, align) carries
     * both pieces of information.
     */
    fun mainAxisHorizontal(
        kw: AlignmentKeyword,
        gap: Dp
    ): Arrangement.Horizontal = when (kw) {
        AlignmentKeyword.SpaceBetween, AlignmentKeyword.SpaceAround,
        AlignmentKeyword.SpaceEvenly -> toHorizontalArrangement(kw)
        AlignmentKeyword.Center ->
            if (gap > 0.dp) Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
            else Arrangement.Center
        AlignmentKeyword.End, AlignmentKeyword.FlexEnd ->
            if (gap > 0.dp) Arrangement.spacedBy(gap, Alignment.End)
            else Arrangement.End
        else ->
            if (gap > 0.dp) Arrangement.spacedBy(gap, Alignment.Start)
            else Arrangement.Start
    }

    /** Column twin of [mainAxisHorizontal] — same distribution rules. */
    fun mainAxisVertical(
        kw: AlignmentKeyword,
        gap: Dp
    ): Arrangement.Vertical = when (kw) {
        AlignmentKeyword.SpaceBetween, AlignmentKeyword.SpaceAround,
        AlignmentKeyword.SpaceEvenly -> toVerticalArrangement(kw)
        AlignmentKeyword.Center ->
            if (gap > 0.dp) Arrangement.spacedBy(gap, Alignment.CenterVertically)
            else Arrangement.Center
        AlignmentKeyword.End, AlignmentKeyword.FlexEnd ->
            if (gap > 0.dp) Arrangement.spacedBy(gap, Alignment.Bottom)
            else Arrangement.Bottom
        else ->
            if (gap > 0.dp) Arrangement.spacedBy(gap, Alignment.Top)
            else Arrangement.Top
    }

    // --- arrangement mappers -------------------------------------------------
    //
    // justify-content drives the MAIN axis. Row's main axis is horizontal,
    // Column's main axis is vertical — hence two mappers with identical
    // semantics but different Arrangement families.

    /** CSS <content-alignment> → Compose [Arrangement.Horizontal]. */
    internal fun toHorizontalArrangement(kw: AlignmentKeyword): Arrangement.Horizontal = when (kw) {
        // FlexStart / Start both map to Arrangement.Start. Compose already
        // respects LayoutDirection (LTR/RTL) for these, matching CSS.
        AlignmentKeyword.Start, AlignmentKeyword.FlexStart -> Arrangement.Start
        AlignmentKeyword.End, AlignmentKeyword.FlexEnd -> Arrangement.End
        AlignmentKeyword.Center -> Arrangement.Center
        AlignmentKeyword.SpaceBetween -> Arrangement.SpaceBetween
        AlignmentKeyword.SpaceAround -> Arrangement.SpaceAround
        AlignmentKeyword.SpaceEvenly -> Arrangement.SpaceEvenly
        // Stretch isn't a legal justify-content value in CSS — fall through
        // to Start (same as `normal`). TODO: log via PropertyTracker if seen.
        AlignmentKeyword.Stretch, AlignmentKeyword.Baseline,
        AlignmentKeyword.Normal, AlignmentKeyword.Auto -> Arrangement.Start
    }

    /** CSS <content-alignment> → Compose [Arrangement.Vertical]. */
    internal fun toVerticalArrangement(kw: AlignmentKeyword): Arrangement.Vertical = when (kw) {
        AlignmentKeyword.Start, AlignmentKeyword.FlexStart -> Arrangement.Top
        AlignmentKeyword.End, AlignmentKeyword.FlexEnd -> Arrangement.Bottom
        AlignmentKeyword.Center -> Arrangement.Center
        AlignmentKeyword.SpaceBetween -> Arrangement.SpaceBetween
        AlignmentKeyword.SpaceAround -> Arrangement.SpaceAround
        AlignmentKeyword.SpaceEvenly -> Arrangement.SpaceEvenly
        AlignmentKeyword.Stretch, AlignmentKeyword.Baseline,
        AlignmentKeyword.Normal, AlignmentKeyword.Auto -> Arrangement.Top
    }

    // --- alignment mappers ---------------------------------------------------
    //
    // align-items drives the CROSS axis. Row's cross axis is vertical,
    // Column's cross axis is horizontal.

    /** align-items → Compose cross-axis [Alignment.Horizontal] (for Column). */
    internal fun toHorizontalAlignment(kw: AlignmentKeyword): Alignment.Horizontal = when (kw) {
        AlignmentKeyword.Start, AlignmentKeyword.FlexStart -> Alignment.Start
        AlignmentKeyword.End, AlignmentKeyword.FlexEnd -> Alignment.End
        AlignmentKeyword.Center -> Alignment.CenterHorizontally
        // Stretch is Compose's default for Column — there's no Stretch
        // Alignment constant. CenterHorizontally is the closest approximation
        // that the legacy renderer already used; preserve that behaviour.
        AlignmentKeyword.Stretch -> Alignment.CenterHorizontally
        // Baseline has no direct Compose equivalent on the cross axis.
        // TODO(phase7): use Modifier.alignByBaseline on children once the
        // child-modifier path covers it.
        AlignmentKeyword.Baseline -> Alignment.Start
        AlignmentKeyword.SpaceBetween, AlignmentKeyword.SpaceAround,
        AlignmentKeyword.SpaceEvenly, AlignmentKeyword.Normal,
        AlignmentKeyword.Auto -> Alignment.Start
    }

    /** align-items → Compose cross-axis [Alignment.Vertical] (for Row). */
    internal fun toVerticalAlignment(kw: AlignmentKeyword): Alignment.Vertical = when (kw) {
        AlignmentKeyword.Start, AlignmentKeyword.FlexStart -> Alignment.Top
        AlignmentKeyword.End, AlignmentKeyword.FlexEnd -> Alignment.Bottom
        AlignmentKeyword.Center -> Alignment.CenterVertically
        AlignmentKeyword.Stretch -> Alignment.CenterVertically
        AlignmentKeyword.Baseline -> Alignment.Top // TODO: alignByBaseline
        AlignmentKeyword.SpaceBetween, AlignmentKeyword.SpaceAround,
        AlignmentKeyword.SpaceEvenly, AlignmentKeyword.Normal,
        AlignmentKeyword.Auto -> Alignment.Top
    }

    /** align-items → Compose [Alignment] for Box fallback. */
    internal fun toBoxAlignment(kw: AlignmentKeyword): Alignment = when (kw) {
        AlignmentKeyword.Start, AlignmentKeyword.FlexStart -> Alignment.TopStart
        AlignmentKeyword.End, AlignmentKeyword.FlexEnd -> Alignment.BottomEnd
        AlignmentKeyword.Center -> Alignment.Center
        AlignmentKeyword.Stretch -> Alignment.TopCenter
        AlignmentKeyword.Baseline -> Alignment.TopStart
        AlignmentKeyword.SpaceBetween, AlignmentKeyword.SpaceAround,
        AlignmentKeyword.SpaceEvenly, AlignmentKeyword.Normal,
        AlignmentKeyword.Auto -> Alignment.TopStart
    }
}

/**
 * Per-child flex-item parameters produced by the extractor pass.
 *
 * ComponentRenderer consumes this inside Row/ColumnScope to call
 * Modifier.weight(grow) and Modifier.align(alignSelf). Packaged as a data
 * class so it can travel through sortByOrder without re-parsing the IR.
 *
 * Note: [shrink] has no Compose analog — Compose weight conflates grow +
 * shrink into a single weight value. When shrink ≠ 1.0 we log a TODO via
 * PropertyTracker rather than silently dropping.
 *
 * [basis] is reserved for when FlexBasisValue gains real variants; the
 * engine currently treats it as a TODO stub.
 */
data class FlexItemModifier(
    val grow: Float,
    val shrink: Float,
    val alignSelf: AlignmentKeyword?,
    val order: Int
) {
    companion object {
        /** Identity item — CSS defaults: grow=0, shrink=1, auto/auto. */
        val Default = FlexItemModifier(grow = 0f, shrink = 1f, alignSelf = null, order = 0)
    }
}
