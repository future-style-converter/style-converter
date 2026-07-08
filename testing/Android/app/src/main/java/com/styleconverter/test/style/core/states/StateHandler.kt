package com.styleconverter.test.style.core.states

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.styleconverter.test.style.core.ir.IRProperty
import com.styleconverter.test.style.core.ir.IRSelector

/**
 * Handles CSS pseudo-class selectors by mapping them to Compose interaction states.
 *
 * ## Supported Selectors
 * - :hover -> isHovered (desktop/TV only)
 * - :active -> isPressed
 * - :focus -> isFocused
 * - :focus-visible -> isFocused (approximation)
 * - :focus-within -> isFocused (approximation)
 * - :disabled -> !isEnabled
 * - :enabled -> isEnabled
 * - :checked -> isChecked
 *
 * ## Usage
 * ```kotlin
 * val (interactionSource, state) = StateHandler.rememberInteractionState()
 *
 * // Find matching selector
 * val activeSelector = StateHandler.findActiveSelector(component.selectors, state)
 *
 * // Merge base properties with selector properties
 * val effectiveProperties = StateHandler.mergeProperties(
 *     component.properties,
 *     activeSelector?.properties
 * )
 * ```
 *
 * ## Limitations
 * - :hover is limited on touch devices
 * - :focus-within would need parent context
 * - :first-child, :nth-child etc. require structural info
 */
object StateHandler {

    /**
     * Selector condition type.
     */
    enum class SelectorCondition {
        HOVER,
        ACTIVE,
        FOCUS,
        FOCUS_VISIBLE,
        FOCUS_WITHIN,
        DISABLED,
        ENABLED,
        CHECKED,
        UNKNOWN
    }

    /**
     * Parse selector condition from string.
     */
    fun parseCondition(condition: String): SelectorCondition {
        return when (condition.lowercase().trim().removePrefix(":")) {
            "hover" -> SelectorCondition.HOVER
            "active" -> SelectorCondition.ACTIVE
            "focus" -> SelectorCondition.FOCUS
            "focus-visible" -> SelectorCondition.FOCUS_VISIBLE
            "focus-within" -> SelectorCondition.FOCUS_WITHIN
            "disabled" -> SelectorCondition.DISABLED
            "enabled" -> SelectorCondition.ENABLED
            "checked" -> SelectorCondition.CHECKED
            else -> SelectorCondition.UNKNOWN
        }
    }

    /**
     * State for a component with selectors.
     */
    data class InteractionState(
        val isHovered: Boolean,
        val isPressed: Boolean,
        val isFocused: Boolean,
        val isEnabled: Boolean = true,
        val isChecked: Boolean = false
    ) {
        /**
         * Check if a selector condition is active.
         */
        fun isActive(condition: SelectorCondition): Boolean {
            return when (condition) {
                SelectorCondition.HOVER -> isHovered
                SelectorCondition.ACTIVE -> isPressed
                SelectorCondition.FOCUS,
                SelectorCondition.FOCUS_VISIBLE,
                SelectorCondition.FOCUS_WITHIN -> isFocused
                SelectorCondition.DISABLED -> !isEnabled
                SelectorCondition.ENABLED -> isEnabled
                SelectorCondition.CHECKED -> isChecked
                SelectorCondition.UNKNOWN -> false
            }
        }

        /**
         * Check if any interactive state is active.
         */
        val hasActiveState: Boolean
            get() = isHovered || isPressed || isFocused
    }

    /**
     * Find the first matching selector based on current interaction state.
     * Priority: active > hover > focus
     */
    fun findActiveSelector(
        selectors: List<IRSelector>,
        state: InteractionState
    ): IRSelector? {
        val priorityOrder = listOf(
            SelectorCondition.ACTIVE,
            SelectorCondition.HOVER,
            SelectorCondition.FOCUS,
            SelectorCondition.FOCUS_VISIBLE,
            SelectorCondition.FOCUS_WITHIN,
            SelectorCondition.DISABLED,
            SelectorCondition.CHECKED
        )

        for (priority in priorityOrder) {
            val selector = selectors.find { selector ->
                val condition = parseCondition(selector.condition)
                condition == priority && state.isActive(condition)
            }
            if (selector != null) return selector
        }

        return null
    }

    /**
     * Find all matching selectors based on current interaction state.
     */
    fun findAllActiveSelectors(
        selectors: List<IRSelector>,
        state: InteractionState
    ): List<IRSelector> {
        return selectors.filter { selector ->
            val condition = parseCondition(selector.condition)
            state.isActive(condition)
        }
    }

    /**
     * Merge base properties with selector properties.
     * Selector properties override base properties.
     */
    fun mergeProperties(
        baseProperties: List<IRProperty>,
        selectorProperties: List<IRProperty>?
    ): List<IRProperty> {
        if (selectorProperties.isNullOrEmpty()) return baseProperties

        val mergedMap = mutableMapOf<String, IRProperty>()

        // Add base properties first
        baseProperties.forEach { prop ->
            mergedMap[prop.type] = prop
        }

        // Override with selector properties
        selectorProperties.forEach { prop ->
            mergedMap[prop.type] = prop
        }

        return mergedMap.values.toList()
    }

    /**
     * Merge base properties with multiple selector properties.
     * Later selectors take precedence.
     */
    fun mergePropertiesFromSelectors(
        baseProperties: List<IRProperty>,
        activeSelectors: List<IRSelector>
    ): List<IRProperty> {
        var result = baseProperties

        activeSelectors.forEach { selector ->
            result = mergeProperties(result, selector.properties)
        }

        return result
    }

    /**
     * Composable helper to collect interaction state.
     */
    @Composable
    fun rememberInteractionState(
        interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
        isEnabled: Boolean = true,
        isChecked: Boolean = false
    ): Pair<MutableInteractionSource, InteractionState> {
        val isHovered by interactionSource.collectIsHoveredAsState()
        val isPressed by interactionSource.collectIsPressedAsState()
        val isFocused by interactionSource.collectIsFocusedAsState()

        return Pair(
            interactionSource,
            InteractionState(
                isHovered = isHovered,
                isPressed = isPressed,
                isFocused = isFocused,
                isEnabled = isEnabled,
                isChecked = isChecked
            )
        )
    }

    /**
     * Get the CSS selector string for a condition.
     */
    fun conditionToCssSelector(condition: SelectorCondition): String {
        return when (condition) {
            SelectorCondition.HOVER -> ":hover"
            SelectorCondition.ACTIVE -> ":active"
            SelectorCondition.FOCUS -> ":focus"
            SelectorCondition.FOCUS_VISIBLE -> ":focus-visible"
            SelectorCondition.FOCUS_WITHIN -> ":focus-within"
            SelectorCondition.DISABLED -> ":disabled"
            SelectorCondition.ENABLED -> ":enabled"
            SelectorCondition.CHECKED -> ":checked"
            SelectorCondition.UNKNOWN -> ""
        }
    }
}
