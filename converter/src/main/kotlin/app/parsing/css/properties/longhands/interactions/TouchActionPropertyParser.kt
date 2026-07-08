package app.parsing.css.properties.longhands.interactions

import app.irmodels.IRProperty
import app.irmodels.properties.interactions.TouchActionProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for the `touch-action` property.
 *
 * Sets how an element's region can be manipulated by a touchscreen user.
 *
 * Valid values:
 * - auto: Enable browser default touch behavior
 * - none: Disable all touch behaviors
 * - pan-x: Enable horizontal panning
 * - pan-left: Enable panning to the left
 * - pan-right: Enable panning to the right
 * - pan-y: Enable vertical panning
 * - pan-up: Enable panning upward
 * - pan-down: Enable panning downward
 * - pinch-zoom: Enable pinch-to-zoom
 * - manipulation: Enable panning and pinch-zoom
 *
 * Multiple values can be combined (e.g., "pan-x pan-y")
 */
object TouchActionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Split by whitespace to handle multiple values
        val tokens = trimmed.split("""\s+""".toRegex()).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        val actions = tokens.mapNotNull { token ->
            when (token) {
                "auto" -> TouchActionProperty.TouchAction.AUTO
                "none" -> TouchActionProperty.TouchAction.NONE
                "pan-x" -> TouchActionProperty.TouchAction.PAN_X
                "pan-left" -> TouchActionProperty.TouchAction.PAN_LEFT
                "pan-right" -> TouchActionProperty.TouchAction.PAN_RIGHT
                "pan-y" -> TouchActionProperty.TouchAction.PAN_Y
                "pan-up" -> TouchActionProperty.TouchAction.PAN_UP
                "pan-down" -> TouchActionProperty.TouchAction.PAN_DOWN
                "pinch-zoom" -> TouchActionProperty.TouchAction.PINCH_ZOOM
                "manipulation" -> TouchActionProperty.TouchAction.MANIPULATION
                else -> null
            }
        }

        // Return null if any token was invalid
        if (actions.size != tokens.size) return null
        if (actions.isEmpty()) return null

        return TouchActionProperty(actions)
    }
}
