package com.styleconverter.test.style.interactions

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts interaction-related configuration from IR properties.
 *
 * ## Supported Properties
 * - Visibility: visible, hidden, collapse
 * - ContentVisibility: auto, visible, hidden
 * - PointerEvents: auto, none, etc.
 * - UserSelect: auto, none, text, all
 * - Cursor: pointer, default, etc.
 * - TouchAction: auto, none, manipulation, etc.
 * - Appearance: auto, none
 * - BackfaceVisibility: visible, hidden
 */
object InteractionExtractor {

    /**
     * Extract a complete InteractionConfig from a list of property type/data pairs.
     */
    fun extractInteractionConfig(properties: List<Pair<String, JsonElement?>>): InteractionConfig {
        var config = InteractionConfig()

        for ((type, data) in properties) {
            config = when (type) {
                "Visibility" -> config.copy(visibility = extractVisibility(data))
                "ContentVisibility" -> config.copy(contentVisibility = extractContentVisibility(data))
                "PointerEvents" -> config.copy(pointerEvents = extractPointerEvents(data))
                "UserSelect" -> config.copy(userSelect = extractUserSelect(data))
                "Cursor" -> config.copy(cursor = extractCursor(data))
                "TouchAction" -> config.copy(touchAction = extractTouchAction(data))
                "Appearance" -> config.copy(appearance = extractAppearance(data))
                "BackfaceVisibility" -> config.copy(backfaceVisibility = extractBackfaceVisibility(data))
                else -> config
            }
        }

        return config
    }

    /**
     * Extract visibility mode from IR data.
     */
    private fun extractVisibility(json: JsonElement?): VisibilityMode {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase() ?: return VisibilityMode.VISIBLE
        return when (keyword) {
            "visible" -> VisibilityMode.VISIBLE
            "hidden" -> VisibilityMode.HIDDEN
            "collapse" -> VisibilityMode.COLLAPSE
            else -> VisibilityMode.VISIBLE
        }
    }

    /**
     * Extract content visibility mode from IR data.
     */
    private fun extractContentVisibility(json: JsonElement?): ContentVisibilityMode {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase() ?: return ContentVisibilityMode.VISIBLE
        return when (keyword) {
            "visible" -> ContentVisibilityMode.VISIBLE
            "auto" -> ContentVisibilityMode.AUTO
            "hidden" -> ContentVisibilityMode.HIDDEN
            else -> ContentVisibilityMode.VISIBLE
        }
    }

    /**
     * Extract pointer events mode from IR data.
     */
    private fun extractPointerEvents(json: JsonElement?): PointerEventsMode {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()?.replace("-", "_") ?: return PointerEventsMode.AUTO
        return try {
            PointerEventsMode.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            PointerEventsMode.AUTO
        }
    }

    /**
     * Extract user select mode from IR data.
     */
    private fun extractUserSelect(json: JsonElement?): UserSelectMode {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase() ?: return UserSelectMode.AUTO
        return try {
            UserSelectMode.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            UserSelectMode.AUTO
        }
    }

    /**
     * Extract cursor type from IR data.
     */
    private fun extractCursor(json: JsonElement?): CursorType {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()?.replace("-", "_") ?: return CursorType.AUTO
        return try {
            CursorType.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            CursorType.AUTO
        }
    }

    /**
     * Extract touch action mode from IR data.
     */
    private fun extractTouchAction(json: JsonElement?): TouchActionMode {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase()?.replace("-", "_") ?: return TouchActionMode.AUTO
        return try {
            TouchActionMode.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            TouchActionMode.AUTO
        }
    }

    /**
     * Extract appearance mode from IR data.
     */
    private fun extractAppearance(json: JsonElement?): AppearanceMode {
        val keyword = ValueExtractors.extractKeyword(json)?.uppercase() ?: return AppearanceMode.AUTO
        return try {
            AppearanceMode.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            AppearanceMode.AUTO
        }
    }

    /**
     * Extract backface visibility mode from IR data.
     */
    private fun extractBackfaceVisibility(json: JsonElement?): BackfaceVisibilityMode {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase() ?: return BackfaceVisibilityMode.VISIBLE
        return when (keyword) {
            "visible" -> BackfaceVisibilityMode.VISIBLE
            "hidden" -> BackfaceVisibilityMode.HIDDEN
            else -> BackfaceVisibilityMode.VISIBLE
        }
    }

    /**
     * Extract detailed touch action configuration from properties.
     *
     * Returns a TouchActionConfig with boolean flags for each allowed action,
     * useful for container-level scroll and gesture handling.
     */
    fun extractTouchActionConfig(properties: List<Pair<String, JsonElement?>>): TouchActionConfig {
        val touchActionData = properties.find { it.first == "TouchAction" }?.second
            ?: return TouchActionConfig()

        val values = extractTouchActionValues(touchActionData)
        if (values.isEmpty()) return TouchActionConfig()

        val hasNone = values.any { it.uppercase() == "NONE" }
        val hasAuto = values.any { it.uppercase() == "AUTO" }
        val hasPanX = values.any { it.uppercase() in listOf("PAN_X", "PAN-X", "PAN_LEFT", "PAN_RIGHT") }
        val hasPanY = values.any { it.uppercase() in listOf("PAN_Y", "PAN-Y", "PAN_UP", "PAN_DOWN") }
        val hasPinchZoom = values.any { it.uppercase() in listOf("PINCH_ZOOM", "PINCH-ZOOM") }
        val hasManipulation = values.any { it.uppercase() == "MANIPULATION" }

        return when {
            hasNone -> TouchActionConfig.None
            hasAuto -> TouchActionConfig.Default
            hasManipulation -> TouchActionConfig.Manipulation
            else -> TouchActionConfig(
                allowPanX = hasPanX,
                allowPanY = hasPanY,
                allowPinchZoom = hasPinchZoom,
                allowAll = false
            )
        }
    }

    /**
     * Extract touch action values from JSON data.
     * Handles: {"values": ["NONE"]}, "NONE", {"keyword": "MANIPULATION"}
     */
    private fun extractTouchActionValues(data: JsonElement): List<String> {
        return when (data) {
            is JsonPrimitive -> listOfNotNull(data.contentOrNull)
            is JsonObject -> {
                // Try "values" array first
                (data["values"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?: listOfNotNull(
                        data["keyword"]?.jsonPrimitive?.contentOrNull
                            ?: data["value"]?.jsonPrimitive?.contentOrNull
                    )
            }
            is JsonArray -> data.mapNotNull { it.jsonPrimitive.contentOrNull }
            else -> emptyList()
        }
    }

    /**
     * Check if a property type is an interaction-related property.
     */
    fun isInteractionProperty(type: String): Boolean {
        return type in INTERACTION_PROPERTIES
    }

    private val INTERACTION_PROPERTIES = setOf(
        "Visibility",
        "ContentVisibility",
        "PointerEvents",
        "UserSelect",
        "Cursor",
        "TouchAction",
        "Appearance",
        "BackfaceVisibility"
    )
}
