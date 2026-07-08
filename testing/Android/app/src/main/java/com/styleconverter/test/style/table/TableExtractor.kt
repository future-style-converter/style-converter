package com.styleconverter.test.style.table

import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Extracts table-related configuration from IR properties.
 */
object TableExtractor {

    fun extractTableConfig(properties: List<Pair<String, JsonElement?>>): TableConfig {
        var layout = TableLayout.AUTO
        var borderCollapse = BorderCollapse.SEPARATE
        var spacingHorizontal: androidx.compose.ui.unit.Dp? = null
        var spacingVertical: androidx.compose.ui.unit.Dp? = null
        var captionSide = CaptionSide.TOP
        var emptyCells = EmptyCells.SHOW

        for ((type, data) in properties) {
            when (type) {
                "TableLayout" -> layout = extractTableLayout(data)
                "BorderCollapse" -> borderCollapse = extractBorderCollapse(data)
                "BorderSpacing" -> {
                    val (h, v) = extractBorderSpacing(data)
                    spacingHorizontal = h
                    spacingVertical = v
                }
                "CaptionSide" -> captionSide = extractCaptionSide(data)
                "EmptyCells" -> emptyCells = extractEmptyCells(data)
            }
        }

        return TableConfig(
            layout = layout,
            borderCollapse = borderCollapse,
            borderSpacingHorizontal = spacingHorizontal,
            borderSpacingVertical = spacingVertical,
            captionSide = captionSide,
            emptyCells = emptyCells
        )
    }

    private fun extractTableLayout(data: JsonElement?): TableLayout {
        val keyword = ValueExtractors.extractKeyword(data)?.lowercase() ?: return TableLayout.AUTO
        return when (keyword) {
            "fixed" -> TableLayout.FIXED
            else -> TableLayout.AUTO
        }
    }

    private fun extractBorderCollapse(data: JsonElement?): BorderCollapse {
        val keyword = ValueExtractors.extractKeyword(data)?.lowercase() ?: return BorderCollapse.SEPARATE
        return when (keyword) {
            "collapse" -> BorderCollapse.COLLAPSE
            else -> BorderCollapse.SEPARATE
        }
    }

    private fun extractBorderSpacing(data: JsonElement?): Pair<androidx.compose.ui.unit.Dp?, androidx.compose.ui.unit.Dp?> {
        if (data == null) return Pair(null, null)

        val obj = data as? JsonObject
        if (obj != null) {
            val horizontal = obj["horizontal"]?.let { ValueExtractors.extractDp(it) }
                ?: obj["x"]?.let { ValueExtractors.extractDp(it) }
            val vertical = obj["vertical"]?.let { ValueExtractors.extractDp(it) }
                ?: obj["y"]?.let { ValueExtractors.extractDp(it) }
                ?: horizontal
            return Pair(horizontal, vertical)
        }

        val single = ValueExtractors.extractDp(data)
        return Pair(single, single)
    }

    private fun extractCaptionSide(data: JsonElement?): CaptionSide {
        val keyword = ValueExtractors.extractKeyword(data)?.lowercase() ?: return CaptionSide.TOP
        return when (keyword) {
            "bottom" -> CaptionSide.BOTTOM
            else -> CaptionSide.TOP
        }
    }

    private fun extractEmptyCells(data: JsonElement?): EmptyCells {
        val keyword = ValueExtractors.extractKeyword(data)?.lowercase() ?: return EmptyCells.SHOW
        return when (keyword) {
            "hide" -> EmptyCells.HIDE
            else -> EmptyCells.SHOW
        }
    }

    fun isTableProperty(type: String): Boolean {
        return type in TABLE_PROPERTIES
    }

    private val TABLE_PROPERTIES = setOf(
        "TableLayout", "BorderCollapse", "BorderSpacing",
        "CaptionSide", "EmptyCells"
    )
}
