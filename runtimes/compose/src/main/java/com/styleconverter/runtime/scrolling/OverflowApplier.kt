package com.styleconverter.runtime.scrolling

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape

object OverflowApplier {

    /**
     * Apply overflow behavior to modifier.
     *
     * CSS overflow maps to (css-overflow-3 §3, on USED values after the
     * §3.1 coercion baked into OverflowConfig.clipsX/clipsY):
     * - visible on both axes: no modifier
     * - non-visible on both axes: Modifier.clip(RectangleShape)
     * - non-visible on ONE axis (only reachable as visible+clip, §3.1):
     *   axis-selective drawing clip — ink spills on the visible axis only
     *   (WPT css-overflow clip-003: overflow-x:clip must not clip Y)
     * - scroll/auto ALSO clip (a scroll container always clips); actual
     *   scrolling still needs composable-level wiring (see applyScrolling)
     */
    fun applyOverflow(modifier: Modifier, config: OverflowConfig): Modifier {
        // Per-axis clip decisions from USED values (§3.1 coercion inside).
        val cx = config.clipsX
        val cy = config.clipsY

        // The declared-overflow clip, exactly as before wave 41: this
        // branch is byte-identical for every clamp-less component (the
        // cap block below is a no-op when lineClampCapPx is null).
        val base = when {
            // No axis declared non-visible → identity (the pre-wave-41
            // fast path, now falling through to the cap check).
            !config.hasOverflow -> modifier
            // Both axes clip → plain rectangle clip. graphicsLayer clipping
            // is cheaper than a draw-lambda and pixel-identical here.
            cx && cy -> modifier.clip(RectangleShape)
            // Exactly one axis clips → drawing-level clipRect with the
            // visible axis extended to a large finite bound (see AxisClip).
            cx || cy -> modifier.axisSelectiveClip(clipX = cx, clipY = cy)
            // Neither clips (visible/visible after coercion) → identity.
            else -> modifier
        }

        // Wave 41 (lane T6) — the block-level line-clamp cap. A fixed-count
        // `line-clamp: <n>` implies `continue: discard` (css-overflow-4 §5):
        // the container keeps its first N line boxes and the rest is not
        // rendered — even when those line boxes live in CHILD components the
        // leaf Text(maxLines) path cannot cap (block-ellipsis-012/027/032,
        // line-clamp-005: the wave-40 native captures render every child
        // line unclamped below the box the ref closed after line N).
        val capPx = config.lineClampCapPx ?: return base

        // Discarded lines must be unpaintable, so the cap needs a BLOCK-axis
        // ink clip. If the declared overflow already clips Y it is already
        // in `base` (wrapping the cap node below — correct nesting); a bare
        // clamp adds a Y-only clip and NEVER an X clip: line-clamp creates
        // no inline-axis clip (block-ellipsis-013's preserved line paints
        // past the border box's right edge in the ref and must keep doing
        // so — see placeholderOverflow's unclippedLineWidths note).
        val clipped = if (config.hasOverflow && cy) base
                      else base.axisSelectiveClip(clipX = false, clipY = true)

        // The layout cap itself, INSIDE the clip so the clip rect tracks
        // the capped box size (LineClampCap has the geometry contract).
        return clipped.lineClampHeightCap(capPx)

        // Note: Scroll modifiers need to be applied at the composable level.
        // Scroll state should be handled by the container renderer.
    }

    /**
     * Apply scroll modifiers.
     * This should be called when building scrollable containers.
     */
    fun applyScrolling(modifier: Modifier, config: OverflowConfig): Modifier {
        val result = modifier

        // Note: In actual usage, you'd use rememberScrollState() in a @Composable
        // This is a simplified version showing the pattern

        return result
    }

    /**
     * Check if the overflow config requires a scrollable container.
     */
    fun requiresScrollableContainer(config: OverflowConfig): Boolean {
        return config.isScrollableX || config.isScrollableY
    }

    /**
     * Get scroll direction from config.
     */
    fun getScrollDirection(config: OverflowConfig): ScrollDirection {
        return when {
            config.isScrollableX && config.isScrollableY -> ScrollDirection.BOTH
            config.isScrollableX -> ScrollDirection.HORIZONTAL
            config.isScrollableY -> ScrollDirection.VERTICAL
            else -> ScrollDirection.NONE
        }
    }
}

enum class ScrollDirection {
    NONE,
    HORIZONTAL,
    VERTICAL,
    BOTH
}
