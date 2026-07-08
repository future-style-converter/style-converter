package com.styleconverter.test.style.layout.position

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for CSS positioning properties.
 *
 * Handles position type (static, relative, absolute, fixed, sticky) and
 * offset values (top, right, bottom, left, inset-*).
 *
 * ## Compose Mapping
 * - `position: relative` -> `Modifier.offset()`
 * - `position: absolute` -> `Box` with `Modifier.offset()` (needs container handling)
 * - `position: fixed` -> Limited support (would need `Popup` or similar)
 * - `position: sticky` -> Limited support (scroll-aware behavior)
 *
 * ## Logical Properties
 * Logical properties (inset-block-*, inset-inline-*) are mapped to physical
 * properties for LTR horizontal writing mode:
 * - inset-block-start -> top
 * - inset-block-end -> bottom
 * - inset-inline-start -> start (left in LTR)
 * - inset-inline-end -> end (right in LTR)
 */
data class PositionConfig(
    val type: PositionType = PositionType.STATIC,
    val top: Dp? = null,
    val end: Dp? = null,
    val bottom: Dp? = null,
    val start: Dp? = null,
    // Logical properties (writing mode aware)
    val insetBlockStart: Dp? = null,
    val insetBlockEnd: Dp? = null,
    val insetInlineStart: Dp? = null,
    val insetInlineEnd: Dp? = null,
    val zIndex: Float = 0f
) {
    /**
     * Returns true if any positioning property is defined.
     */
    val hasPosition: Boolean
        get() = type != PositionType.STATIC ||
            top != null || end != null || bottom != null || start != null ||
            insetBlockStart != null || insetBlockEnd != null ||
            insetInlineStart != null || insetInlineEnd != null ||
            zIndex != 0f

    /**
     * Returns true if z-index is explicitly set to a non-zero value.
     */
    val hasZIndex: Boolean
        get() = zIndex != 0f

    /**
     * Returns true if this element needs absolute positioning (removed from flow).
     */
    val isAbsolutelyPositioned: Boolean
        get() = type == PositionType.ABSOLUTE || type == PositionType.FIXED

    // Resolved offsets (physical property takes precedence over logical)
    val resolvedTop: Dp?
        get() = top ?: insetBlockStart

    val resolvedEnd: Dp?
        get() = end ?: insetInlineEnd

    val resolvedBottom: Dp?
        get() = bottom ?: insetBlockEnd

    val resolvedStart: Dp?
        get() = start ?: insetInlineStart

    /**
     * Calculate horizontal offset for positioning.
     * Positive values move right, negative values move left.
     *
     * For CSS `left: 10px`, the element moves right by 10px.
     * For CSS `right: 10px`, the element moves left by 10px (from the right edge).
     *
     * When both are specified, `left` takes precedence (CSS behavior).
     */
    val offsetX: Dp
        get() {
            val leftOffset = resolvedStart
            val rightOffset = resolvedEnd

            return when {
                // Left takes precedence when both are specified
                leftOffset != null -> leftOffset
                rightOffset != null -> -rightOffset
                else -> 0.dp
            }
        }

    /**
     * Calculate vertical offset for positioning.
     * Positive values move down, negative values move up.
     *
     * For CSS `top: 10px`, the element moves down by 10px.
     * For CSS `bottom: 10px`, the element moves up by 10px (from the bottom edge).
     *
     * When both are specified, `top` takes precedence (CSS behavior).
     */
    val offsetY: Dp
        get() {
            val topOffset = resolvedTop
            val bottomOffset = resolvedBottom

            return when {
                // Top takes precedence when both are specified
                topOffset != null -> topOffset
                bottomOffset != null -> -bottomOffset
                else -> 0.dp
            }
        }
}

/**
 * CSS position types and their Compose mappings.
 */
enum class PositionType {
    /**
     * Normal document flow. No offset applied.
     * Default behavior in both CSS and Compose.
     */
    STATIC,

    /**
     * Offset from normal position without affecting layout.
     * Maps to `Modifier.offset()` in Compose.
     */
    RELATIVE,

    /**
     * Removed from flow, positioned relative to nearest positioned ancestor.
     * In Compose, requires `Box` container handling.
     */
    ABSOLUTE,

    /**
     * Removed from flow, positioned relative to viewport.
     * Limited support in Compose - would need `Popup` or window-level handling.
     */
    FIXED,

    /**
     * Hybrid: static until scroll threshold, then fixed.
     * Requires scroll-aware behavior, limited support in basic Compose.
     */
    STICKY
}
