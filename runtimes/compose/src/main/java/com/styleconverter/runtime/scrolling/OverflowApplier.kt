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
        // Fast identity when neither axis was declared non-visible.
        if (!config.hasOverflow) return modifier

        // Per-axis clip decisions from USED values (§3.1 coercion inside).
        val cx = config.clipsX
        val cy = config.clipsY

        return when {
            // Both axes clip → plain rectangle clip. graphicsLayer clipping
            // is cheaper than a draw-lambda and pixel-identical here.
            cx && cy -> modifier.clip(RectangleShape)
            // Exactly one axis clips → drawing-level clipRect with the
            // visible axis extended to a large finite bound (see AxisClip).
            cx || cy -> modifier.axisSelectiveClip(clipX = cx, clipY = cy)
            // Neither clips (visible/visible after coercion) → identity.
            else -> modifier
        }

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
