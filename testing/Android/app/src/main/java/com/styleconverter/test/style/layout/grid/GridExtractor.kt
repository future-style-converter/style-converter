package com.styleconverter.test.style.layout.grid

import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts grid configuration from IR property data.
 */
object GridExtractor {

    /**
     * Extract GridConfig from a list of IR properties.
     *
     * @param properties List of (propertyType, propertyData) pairs
     * @return GridConfig with extracted values
     */
    fun extractGridConfig(properties: List<Pair<String, JsonElement?>>): GridConfig {
        var config = GridConfig()

        for ((type, data) in properties) {
            config = when (type) {
                "GridTemplateColumns" -> config.copy(templateColumns = extractTrackList(data))
                "GridTemplateRows" -> config.copy(templateRows = extractTrackList(data))
                "GridTemplateAreas" -> config.copy(templateAreas = extractTemplateAreas(data))
                "GridAutoColumns" -> config.copy(autoColumns = extractTrackSize(data))
                "GridAutoRows" -> config.copy(autoRows = extractTrackSize(data))
                "GridAutoFlow" -> config.copy(autoFlow = extractAutoFlow(data))
                "RowGap" -> config.copy(rowGap = ValueExtractors.extractDp(data))
                "ColumnGap" -> config.copy(columnGap = ValueExtractors.extractDp(data))
                "JustifyItems" -> config.copy(justifyItems = extractJustify(data))
                "AlignItems" -> config.copy(alignItems = extractAlign(data))
                "JustifyContent" -> config.copy(justifyContent = extractJustifyContent(data))
                "AlignContent" -> config.copy(alignContent = extractAlignContent(data))
                else -> config
            }
        }

        return config
    }

    /**
     * Extract GridItemConfig from a list of IR properties.
     *
     * @param properties List of (propertyType, propertyData) pairs
     * @return GridItemConfig with extracted values
     */
    fun extractGridItemConfig(properties: List<Pair<String, JsonElement?>>): GridItemConfig {
        var config = GridItemConfig()

        for ((type, data) in properties) {
            config = when (type) {
                "GridColumnStart" -> config.copy(columnStart = extractGridLine(data))
                "GridColumnEnd" -> config.copy(columnEnd = extractGridLine(data))
                "GridRowStart" -> config.copy(rowStart = extractGridLine(data))
                "GridRowEnd" -> config.copy(rowEnd = extractGridLine(data))
                "GridArea" -> config.copy(areaName = extractAreaName(data))
                "JustifySelf" -> config.copy(justifySelf = extractJustify(data))
                "AlignSelf" -> config.copy(alignSelf = extractAlign(data))
                else -> config
            }
        }

        // Calculate spans from start/end
        if (config.columnStart != null && config.columnEnd != null) {
            config = config.copy(columnSpan = config.columnEnd!! - config.columnStart!!)
        }
        if (config.rowStart != null && config.rowEnd != null) {
            config = config.copy(rowSpan = config.rowEnd!! - config.rowStart!!)
        }

        return config
    }

    /**
     * Extract grid-template-areas from JSON.
     *
     * Handles formats:
     * - Array of strings: ["header header", "sidebar main", "footer footer"]
     * - Object with rows: {"rows": ["header header", ...]}
     * - Single string with newlines
     */
    private fun extractTemplateAreas(json: JsonElement?): GridTemplateAreas? {
        if (json == null) return null

        val rows = when (json) {
            is JsonArray -> {
                json.mapNotNull { element ->
                    when (element) {
                        is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                        else -> null
                    }
                }
            }
            is JsonObject -> {
                val rowsArray = json["rows"] as? JsonArray
                    ?: json["areas"] as? JsonArray
                rowsArray?.mapNotNull { element ->
                    (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                } ?: emptyList()
            }
            is JsonPrimitive -> {
                // Handle single string with newlines or quoted rows
                val content = json.contentOrNull ?: return null
                content.split("\n")
                    .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                    .filter { it.isNotEmpty() }
            }
            else -> emptyList()
        }

        return if (rows.isNotEmpty()) {
            GridTemplateAreas.parse(rows)
        } else null
    }

    /**
     * Extract area name from grid-area property.
     *
     * Handles formats:
     * - String: "header"
     * - Object with name: {"name": "header"}
     * - Object with named result: {"type": "named", "value": "header"}
     */
    private fun extractAreaName(json: JsonElement?): String? {
        if (json == null) return null

        return when (json) {
            is JsonPrimitive -> {
                // Simple named area
                json.contentOrNull?.trim()?.takeIf {
                    it.isNotEmpty() && !it.contains("/") && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' }
                }
            }
            is JsonObject -> {
                // Named area in object format
                json["name"]?.jsonPrimitive?.contentOrNull
                    ?: json["value"]?.jsonPrimitive?.contentOrNull
                    ?: json["area"]?.jsonPrimitive?.contentOrNull
            }
            else -> null
        }
    }

    /**
     * Extract a list of track sizes from JSON (for grid-template-columns/rows).
     */
    private fun extractTrackList(json: JsonElement?): List<GridTrackSize> {
        if (json == null) return emptyList()

        return when (json) {
            is JsonArray -> json.mapNotNull { extractTrackSize(it) }
            else -> listOfNotNull(extractTrackSize(json))
        }
    }

    /**
     * Extract a single track size from JSON.
     */
    private fun extractTrackSize(json: JsonElement?): GridTrackSize? {
        if (json == null) return null

        return when (json) {
            is JsonObject -> {
                // Check for fr unit
                json["fr"]?.jsonPrimitive?.floatOrNull?.let {
                    return GridTrackSize.Fraction(it)
                }

                // Check for px
                json["px"]?.jsonPrimitive?.floatOrNull?.let {
                    return GridTrackSize.Fixed(it.dp)
                }

                // Check for percentage
                json["percent"]?.jsonPrimitive?.floatOrNull?.let {
                    return GridTrackSize.Percent(it)
                }
                json["pct"]?.jsonPrimitive?.floatOrNull?.let {
                    return GridTrackSize.Percent(it)
                }

                // Check for keyword
                val keyword = json["keyword"]?.jsonPrimitive?.content?.lowercase()
                when (keyword) {
                    "min-content" -> GridTrackSize.MinContent
                    "max-content" -> GridTrackSize.MaxContent
                    "auto" -> GridTrackSize.Auto
                    else -> null
                }
            }
            is JsonPrimitive -> {
                val content = json.content.lowercase()
                when {
                    content == "min-content" -> GridTrackSize.MinContent
                    content == "max-content" -> GridTrackSize.MaxContent
                    content == "auto" -> GridTrackSize.Auto
                    content.endsWith("fr") -> {
                        content.removeSuffix("fr").toFloatOrNull()?.let {
                            GridTrackSize.Fraction(it)
                        }
                    }
                    else -> json.floatOrNull?.let { GridTrackSize.Fixed(it.dp) }
                }
            }
            else -> null
        }
    }

    /**
     * Extract grid-auto-flow value.
     */
    private fun extractAutoFlow(json: JsonElement?): GridAutoFlow {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase() ?: return GridAutoFlow.ROW
        return when {
            keyword.contains("column") && keyword.contains("dense") -> GridAutoFlow.COLUMN_DENSE
            keyword.contains("row") && keyword.contains("dense") -> GridAutoFlow.ROW_DENSE
            keyword.contains("column") -> GridAutoFlow.COLUMN
            else -> GridAutoFlow.ROW
        }
    }

    /**
     * Extract justify-items/justify-self value.
     */
    private fun extractJustify(json: JsonElement?): GridJustify {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase() ?: return GridJustify.STRETCH
        return when (keyword) {
            "start", "flex-start" -> GridJustify.START
            "end", "flex-end" -> GridJustify.END
            "center" -> GridJustify.CENTER
            "stretch" -> GridJustify.STRETCH
            else -> GridJustify.STRETCH
        }
    }

    /**
     * Extract align-items/align-self value.
     */
    private fun extractAlign(json: JsonElement?): GridAlign {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase() ?: return GridAlign.STRETCH
        return when (keyword) {
            "start", "flex-start" -> GridAlign.START
            "end", "flex-end" -> GridAlign.END
            "center" -> GridAlign.CENTER
            "stretch" -> GridAlign.STRETCH
            "baseline" -> GridAlign.BASELINE
            else -> GridAlign.STRETCH
        }
    }

    /**
     * Extract justify-content value.
     */
    private fun extractJustifyContent(json: JsonElement?): GridJustifyContent {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase() ?: return GridJustifyContent.START
        return when (keyword) {
            "start", "flex-start" -> GridJustifyContent.START
            "end", "flex-end" -> GridJustifyContent.END
            "center" -> GridJustifyContent.CENTER
            "stretch" -> GridJustifyContent.STRETCH
            "space-between" -> GridJustifyContent.SPACE_BETWEEN
            "space-around" -> GridJustifyContent.SPACE_AROUND
            "space-evenly" -> GridJustifyContent.SPACE_EVENLY
            else -> GridJustifyContent.START
        }
    }

    /**
     * Extract align-content value.
     */
    private fun extractAlignContent(json: JsonElement?): GridAlignContent {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase() ?: return GridAlignContent.START
        return when (keyword) {
            "start", "flex-start" -> GridAlignContent.START
            "end", "flex-end" -> GridAlignContent.END
            "center" -> GridAlignContent.CENTER
            "stretch" -> GridAlignContent.STRETCH
            "space-between" -> GridAlignContent.SPACE_BETWEEN
            "space-around" -> GridAlignContent.SPACE_AROUND
            "space-evenly" -> GridAlignContent.SPACE_EVENLY
            else -> GridAlignContent.START
        }
    }

    /**
     * Extract grid line number (for grid-column-start, etc.).
     */
    private fun extractGridLine(json: JsonElement?): Int? {
        return ValueExtractors.extractInt(json)
    }

    /**
     * Check if a property type is a grid-related property.
     */
    fun isGridProperty(type: String): Boolean {
        return type in gridPropertyTypes
    }

    private val gridPropertyTypes = setOf(
        "GridTemplateColumns", "GridTemplateRows", "GridTemplateAreas",
        "GridAutoColumns", "GridAutoRows", "GridAutoFlow",
        "GridColumnStart", "GridColumnEnd", "GridRowStart", "GridRowEnd",
        "GridColumn", "GridRow", "GridArea",
        "JustifyItems", "AlignItems", "JustifyContent", "AlignContent",
        "JustifySelf", "AlignSelf"
    )
}
