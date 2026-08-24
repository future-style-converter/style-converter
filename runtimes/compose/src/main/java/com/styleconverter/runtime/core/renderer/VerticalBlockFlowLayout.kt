// Wave-47 lane Z2 — the vertical-writing-mode BLOCK-FLOW seam
// (css-writing-modes-4 §6: under `vertical-rl`/`vertical-lr` the block-flow
// direction is HORIZONTAL, so a block container's in-flow children stack
// side-by-side — right→left for vertical-rl, left→right for vertical-lr —
// instead of the Column stack that is correct for horizontal-tb).
// Companion of VerticalTextFlowLayout.kt (the inline-axis half of vertical
// writing modes); consumed only by ComponentRenderer's block branch, behind
// the Z2 capture gate documented there.
package com.styleconverter.runtime.core.renderer

// Composable + Layout are the minimal Compose surface: one custom measure
// policy, no state, no drawing; the compositionLocal marks the scope the
// vertical fill folds are allowed to act in.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/**
 * Wave-47 lane Z2 — true only INSIDE a flow this lane actually models:
 * the [VerticalBlockFlowLayout] seam's content and a vertical multicol
 * container's content (both provide it in ComponentRenderer). The
 * renderer's vertical fill decisions — suppressing the §10.3.3 WIDTH fill
 * and applying the inline-axis (height) stretch — are gated on it, so
 * every vertical box still rendered by the FROZEN horizontal-tb paths
 * (legacy Columns, flex/grid items — the css-writing-modes
 * available-size family, which PASSES on those paths today) keeps its
 * pre-Z2 fills byte-identically.
 */
internal val LocalVerticalFlowScope = compositionLocalOf { false }

/**
 * The pure placement half, split from the composable so the JVM suite can
 * pin the position table without a Compose host (same split as the multicol
 * S/V-tables).
 */
internal object VerticalBlockFlowMath {

    /**
     * X origin of each child box, in child order.
     *
     * @param widths      each child's measured width (its margin box — the
     *        margin rides inside the measurable as `absolutePadding`, the
     *        repo's margin emulation, so plain summing reproduces block-axis
     *        margin placement; adjoining-margin COLLAPSE along this axis is
     *        NOT modeled — the caller logs that honestly).
     * @param anchorWidth the container's used block extent: the sum of child
     *        widths for an auto-sized container (css-writing-modes-4 §7.3 —
     *        the block size of the orthogonal box hugs its content, even
     *        past the ICB), or the constrained width when the author pinned
     *        it (overflow then escapes past block-end, i.e. LEFT for
     *        vertical-rl — exactly what anchoring at the fixed right edge
     *        produces).
     * @param blockRtl    true for vertical-rl / sideways-rl: the first child
     *        sits at the container's RIGHT edge (block-start, §6.4) and flow
     *        walks leftward; false (vertical-lr) walks left→right from 0.
     */
    fun positions(widths: List<Int>, anchorWidth: Int, blockRtl: Boolean): List<Int> {
        // Running block-axis cursor: distance consumed before each child.
        var consumed = 0
        return widths.map { w ->
            val x = if (blockRtl) anchorWidth - consumed - w else consumed
            consumed += w
            x
        }
    }
}

/**
 * Block-flow container for a VERTICAL writing mode: children stack along
 * the horizontal axis in [VerticalBlockFlowMath.positions] order. Children
 * measure with a LOOSE, unbounded width (their own block size — width here
 * — is theirs to decide, css-writing-modes-4 §7.3) and the container's
 * inline extent as max height (the stretch-fit basis the renderer's
 * vertical fill fold targets).
 */
@Composable
internal fun VerticalBlockFlowLayout(
    // true = vertical-rl / sideways-rl (block axis right→left).
    blockRtl: Boolean,
    // The container's own style chain (size, background, margins…).
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        // Loose width: a child's width is its BLOCK size (own declaration or
        // content), never a stretch — the §10.3.3 stretch fit belongs to the
        // INLINE axis, which is the height cap we keep from the incoming
        // constraints (min loosened so non-filling children may be shorter).
        val childConstraints = constraints.copy(
            minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        // The container's used block extent = the sum of child extents
        // (content-hugging §7.3); its inline extent = the tallest child
        // unless the style chain pinned it (tight constraints win below).
        val totalW = placeables.sumOf { it.width }
        val maxH = placeables.maxOfOrNull { it.height } ?: 0
        // Compose requires the reported size inside the constraints — an
        // over-wide orthogonal box reports the cap and lets children draw
        // past it (block boxes do not clip by default, matching CSS
        // overflow: visible).
        val layoutW = totalW.coerceIn(constraints.minWidth, constraints.maxWidth)
        val layoutH =
            if (constraints.hasFixedHeight) constraints.maxHeight
            else maxH.coerceIn(constraints.minHeight, constraints.maxHeight)
        // Anchor (see VerticalBlockFlowMath.positions): the TRUE content
        // extent for an auto-width container — placement must not collapse
        // to the clamped report or vertical-rl would show the wrong end of
        // an over-wide flow — and the fixed edge when the width is pinned.
        val anchor = if (constraints.hasFixedWidth) layoutW else maxOf(totalW, layoutW)
        val xs = VerticalBlockFlowMath.positions(
            placeables.map { it.width }, anchor, blockRtl)
        layout(layoutW, layoutH) {
            // Every child at its block-axis slot, inline origin 0 (in-flow
            // block boxes share the container's inline start edge).
            placeables.forEachIndexed { i, p -> p.place(xs[i], 0) }
        }
    }
}
