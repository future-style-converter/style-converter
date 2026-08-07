package com.styleconverter.runtime.table

import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Extracts table-related configuration from IR properties.
 */
object TableExtractor {

    /**
     * @param properties the table box's own resolved declarations.
     * @param sourceTag its `meta.sourceTag` (`IRComponent._tag`), for the
     *   HTML UA `border-spacing` lane — wave 34, lane T.
     *
     *   Defaults to null, which reproduces the pre-wave-34 result
     *   BYTE-FOR-BYTE: `TableSeparatedTracks.usedSpacing` then answers the
     *   CSS initial 0 and `TableConfig.usedSpacing` changes nothing about
     *   what `effectiveSpacing*` returns. The default exists because the
     *   one call site that can supply the tag lives in
     *   `core/renderer/ComponentRenderer.kt`, outside lane T's owned
     *   regions; threading `component._tag` there is what turns the UA
     *   lane on for Android, and it is recorded as a deferred item rather
     *   than reached across an ownership boundary.
     */
    fun extractTableConfig(
        properties: List<Pair<String, JsonElement?>>,
        sourceTag: String? = null
    ): TableConfig {
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
            emptyCells = emptyCells,
            // Wave 34 (lane T) — the §17.6.1 USED border-spacing, which is
            // not always the declared one: an undeclared `<table>` takes
            // the HTML UA sheet's 2px (§15.3.3), and the collapsing model
            // takes none at all. Computed by the shared decision table so
            // the two natives cannot disagree; null passes through to the
            // legacy declared-only path below.
            usedSpacing = TableSeparatedTracks.usedSpacing(properties, sourceTag)
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
