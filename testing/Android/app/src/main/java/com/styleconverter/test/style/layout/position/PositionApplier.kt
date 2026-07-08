package com.styleconverter.test.style.layout.position

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Applies position configuration to a Compose Modifier.
 *
 * ## CSS Position Mapping
 *
 * | CSS Position | Compose Equivalent |
 * |--------------|-------------------|
 * | static       | No modifier (default) |
 * | relative     | `Modifier.offset()` |
 * | absolute     | `Box` + `Modifier.offset()` (container handling required) |
 * | fixed        | [FixedPositionWrapper] using `Popup` |
 * | sticky       | [StickyPositionWrapper] with scroll-aware behavior |
 *
 * ## Position Wrappers
 *
 * For `position: fixed` and `position: sticky`, use the dedicated wrapper composables:
 *
 * ### Fixed Positioning
 * ```kotlin
 * if (config.isFixed()) {
 *     FixedPositionWrapper(config = config, modifier = modifier) {
 *         // Content renders fixed to viewport
 *     }
 * }
 * ```
 *
 * ### Sticky Positioning
 * ```kotlin
 * if (config.isSticky()) {
 *     StickyPositionWrapper(
 *         scrollState = scrollState,
 *         config = config,
 *         modifier = modifier
 *     ) {
 *         // Content sticks when scrolled past threshold
 *     }
 * }
 * ```
 *
 * ## Implementation Notes
 *
 * 1. **Absolute positioning**: In CSS, `position: absolute` removes the element from
 *    the normal flow and positions it relative to the nearest positioned ancestor.
 *    In Compose, this requires the parent to be a `Box` and uses `Modifier.offset()`.
 *    The [needsAbsoluteContainer] method indicates when this is needed.
 *
 * 2. **Fixed positioning**: CSS `position: fixed` positions relative to the viewport.
 *    Implemented via [FixedPositionWrapper] which uses `Popup` to render content
 *    in a separate window above the app's content.
 *
 * 3. **Sticky positioning**: CSS `position: sticky` is a hybrid that acts like
 *    relative until a scroll threshold, then acts like fixed. Implemented via
 *    [StickyPositionWrapper] which tracks scroll state and applies dynamic offset.
 *
 * ## z-index
 *
 * CSS `z-index` maps directly to `Modifier.zIndex()` for controlling
 * draw order within the same parent.
 */
object PositionApplier {

    /**
     * Apply position configuration to a modifier.
     *
     * @param modifier The base modifier to extend
     * @param config The position configuration to apply
     * @return Modified modifier with positioning applied
     */
    fun applyPosition(modifier: Modifier, config: PositionConfig): Modifier {
        if (!config.hasPosition) return modifier

        var result = modifier

        // Apply z-index for stacking order
        if (config.hasZIndex) {
            result = result.zIndex(config.zIndex)
        }

        // Apply positioning based on type
        result = when (config.type) {
            PositionType.STATIC -> {
                // Static elements have no offset
                result
            }

            PositionType.RELATIVE -> {
                // Relative: offset from normal position
                applyOffset(result, config)
            }

            PositionType.ABSOLUTE -> {
                // Absolute: positioned relative to container
                // Note: This requires the parent to be a Box with proper alignment
                applyOffset(result, config)
            }

            PositionType.FIXED -> {
                // Fixed: positioned relative to viewport
                // Limited support - apply offset but warn about viewport behavior
                applyOffset(result, config)
            }

            PositionType.STICKY -> {
                // Sticky: hybrid behavior
                // Limited support - treat as relative for basic offset
                applyOffset(result, config)
            }
        }

        return result
    }

    /**
     * Apply offset based on resolved top/right/bottom/left values.
     *
     * This applies the calculated offsetX and offsetY from the config,
     * which already handles precedence rules (left over right, top over bottom).
     */
    private fun applyOffset(modifier: Modifier, config: PositionConfig): Modifier {
        val x = config.offsetX
        val y = config.offsetY

        // Only apply offset if there's actual movement
        return if (x.value != 0f || y.value != 0f) {
            modifier.offset(x = x, y = y)
        } else {
            modifier
        }
    }

    /**
     * Apply only z-index without any offset.
     * Useful when offset is handled separately (e.g., in container logic).
     *
     * @param modifier The base modifier
     * @param config The position configuration
     * @return Modifier with only z-index applied
     */
    fun applyZIndexOnly(modifier: Modifier, config: PositionConfig): Modifier {
        return if (config.hasZIndex) {
            modifier.zIndex(config.zIndex)
        } else {
            modifier
        }
    }

    /**
     * Check if this component needs special container handling for absolute/fixed positioning.
     *
     * When true, the parent container should:
     * 1. Use `Box` as the container type
     * 2. Position this child using alignment and offset
     * 3. Handle the element being "out of flow"
     *
     * @param config The position configuration to check
     * @return True if absolute container handling is needed
     */
    fun needsAbsoluteContainer(config: PositionConfig): Boolean {
        return config.isAbsolutelyPositioned
    }

    /**
     * Check if a property type is handled by this applier.
     */
    fun isPositionProperty(propertyType: String): Boolean {
        return PositionExtractor.isPositionProperty(propertyType)
    }

    /**
     * Check if this config requires a FixedPositionWrapper.
     *
     * When true, the component should be wrapped with [FixedPositionWrapper]
     * instead of using normal modifier-based positioning.
     */
    fun needsFixedWrapper(config: PositionConfig): Boolean {
        return config.type == PositionType.FIXED
    }

    /**
     * Check if this config requires a StickyPositionWrapper.
     *
     * When true, the component should be wrapped with [StickyPositionWrapper]
     * instead of using normal modifier-based positioning.
     */
    fun needsStickyWrapper(config: PositionConfig): Boolean {
        return config.type == PositionType.STICKY
    }

    /**
     * Check if this config requires special wrapper handling.
     *
     * Returns true for both fixed and sticky positioning, which need
     * dedicated wrapper composables rather than simple modifier application.
     */
    fun needsPositionWrapper(config: PositionConfig): Boolean {
        return needsFixedWrapper(config) || needsStickyWrapper(config)
    }
}
