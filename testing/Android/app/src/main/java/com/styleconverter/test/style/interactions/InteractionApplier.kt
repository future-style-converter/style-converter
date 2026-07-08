package com.styleconverter.test.style.interactions

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Applies interaction configuration to Compose Modifiers.
 *
 * ## Supported Features
 * - Visibility: visible, hidden, collapse → alpha and graphicsLayer
 * - PointerEvents: none → clickable(false) (must be applied at composable level)
 * - BackfaceVisibility: hidden → rotationY clipping
 *
 * ## Limitations
 * - ContentVisibility: Compose doesn't have direct equivalent, mapped to alpha
 * - UserSelect: Android handles text selection differently
 * - Cursor: Desktop only, Android uses touch
 * - TouchAction: Handled by gesture modifiers
 * - Appearance: Affects form control styling, not applicable
 *
 * ## Usage
 * ```kotlin
 * val config = InteractionExtractor.extractInteractionConfig(properties)
 * val modifier = InteractionApplier.applyInteraction(Modifier, config)
 * ```
 */
object InteractionApplier {

    /**
     * Apply interaction configuration to modifier.
     *
     * @param modifier The base modifier
     * @param config The interaction configuration
     * @return Modified modifier with applicable interaction properties
     */
    fun applyInteraction(modifier: Modifier, config: InteractionConfig): Modifier {
        if (!config.hasInteraction) return modifier

        var result = modifier

        // Apply visibility
        result = applyVisibility(result, config)

        // Apply backface visibility (for 3D transforms)
        result = applyBackfaceVisibility(result, config)

        return result
    }

    /**
     * Apply visibility mode to modifier.
     *
     * CSS visibility maps to:
     * - visible: default (no change)
     * - hidden: alpha = 0, but space preserved
     * - collapse: alpha = 0, space may be collapsed
     *
     * Note: Content visibility "hidden" also sets alpha to 0
     */
    private fun applyVisibility(modifier: Modifier, config: InteractionConfig): Modifier {
        return when {
            config.isHidden -> modifier.alpha(0f)
            config.contentVisibility == ContentVisibilityMode.AUTO -> {
                // AUTO could skip rendering off-screen content
                // In Compose, this is handled by LazyColumn/LazyRow
                modifier
            }
            else -> modifier
        }
    }

    /**
     * Apply backface visibility for 3D transforms.
     *
     * When backface-visibility is hidden and element is rotated > 90deg,
     * the back face should not be visible.
     */
    private fun applyBackfaceVisibility(modifier: Modifier, config: InteractionConfig): Modifier {
        if (config.backfaceVisibility != BackfaceVisibilityMode.HIDDEN) {
            return modifier
        }

        // Apply graphicsLayer with cameraDistance for proper 3D rendering
        return modifier.graphicsLayer {
            // Note: Actual backface culling would need to check rotation
            // This sets up the 3D context; the actual culling would be done
            // when transforms are applied
            cameraDistance = 8f * density
        }
    }

    /**
     * Check if pointer events should be disabled.
     *
     * When pointer-events is none, the element should not respond to touch/click.
     * This needs to be applied at the composable level using clickable(enabled = false)
     * or by not attaching click handlers.
     *
     * @param config The interaction configuration
     * @return True if pointer events should be disabled
     */
    fun shouldDisablePointerEvents(config: InteractionConfig): Boolean {
        return config.isPointerDisabled
    }

    /**
     * Get alpha value for visibility configuration.
     *
     * @param config The interaction configuration
     * @return Alpha value (0f for hidden, 1f for visible)
     */
    fun getAlpha(config: InteractionConfig): Float {
        return if (config.isHidden) 0f else 1f
    }

    /**
     * Check if the element should be laid out.
     *
     * For visibility: collapse on non-table elements, space should be removed.
     * However, Compose doesn't have a direct equivalent - you'd need to conditionally
     * not compose the element at all.
     *
     * @param config The interaction configuration
     * @return True if element should take up space, false if collapsed
     */
    fun shouldTakeUpSpace(config: InteractionConfig): Boolean {
        return config.visibility != VisibilityMode.COLLAPSE
    }

    /**
     * Get touch action hint for gesture handling.
     *
     * Returns information about which touch gestures should be enabled/disabled.
     * This is informational - actual implementation depends on gesture modifiers used.
     *
     * @param config The interaction configuration
     * @return TouchActionInfo describing allowed gestures
     */
    fun getTouchActionInfo(config: InteractionConfig): TouchActionInfo {
        return when (config.touchAction) {
            TouchActionMode.NONE -> TouchActionInfo(
                allowPanX = false,
                allowPanY = false,
                allowPinchZoom = false
            )
            TouchActionMode.MANIPULATION -> TouchActionInfo(
                allowPanX = true,
                allowPanY = true,
                allowPinchZoom = true
            )
            TouchActionMode.PAN_X -> TouchActionInfo(
                allowPanX = true,
                allowPanY = false,
                allowPinchZoom = false
            )
            TouchActionMode.PAN_Y -> TouchActionInfo(
                allowPanX = false,
                allowPanY = true,
                allowPinchZoom = false
            )
            TouchActionMode.PINCH_ZOOM -> TouchActionInfo(
                allowPanX = false,
                allowPanY = false,
                allowPinchZoom = true
            )
            else -> TouchActionInfo(
                allowPanX = true,
                allowPanY = true,
                allowPinchZoom = true
            )
        }
    }
}

/**
 * Information about allowed touch gestures.
 */
data class TouchActionInfo(
    val allowPanX: Boolean,
    val allowPanY: Boolean,
    val allowPinchZoom: Boolean
) {
    val allowsAnyGesture: Boolean
        get() = allowPanX || allowPanY || allowPinchZoom
}
