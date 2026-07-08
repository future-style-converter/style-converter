package com.styleconverter.test.style.scrolling

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape

object OverflowApplier {

    /**
     * Apply overflow behavior to modifier.
     *
     * CSS overflow maps to:
     * - visible: default (no modifier)
     * - hidden/clip: Modifier.clip()
     * - scroll/auto: Modifier.verticalScroll() / horizontalScroll()
     */
    fun applyOverflow(modifier: Modifier, config: OverflowConfig): Modifier {
        if (!config.hasOverflow) return modifier

        var result = modifier

        // Apply clipping if needed
        if (config.shouldClip) {
            result = result.clip(RectangleShape)
        }

        // Note: Scroll modifiers need to be applied at the composable level
        // This returns the modifier with clip applied, scroll state should be
        // handled by the container renderer

        return result
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
