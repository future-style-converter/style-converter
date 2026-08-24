package com.styleconverter.runtime.columns

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts multi-column configuration from IR properties.
 */
object MultiColumnExtractor {

    fun extractMultiColumnConfig(properties: List<Pair<String, JsonElement?>>): MultiColumnConfig {
        var columnCount: Int? = null
        var columnWidth: androidx.compose.ui.unit.Dp? = null
        var columnGap: androidx.compose.ui.unit.Dp? = null
        var ruleWidth: androidx.compose.ui.unit.Dp? = null
        var ruleStyle = ColumnRuleStyle.NONE
        var ruleColor: Color? = null
        var span = ColumnSpan.NONE
        var fill = ColumnFill.BALANCE
        var continueDiscard = false

        for ((type, data) in properties) {
            when (type) {
                "ColumnCount" -> columnCount = extractColumnCount(data)
                "ColumnWidth" -> columnWidth = extractColumnWidth(data)
                "ColumnGap" -> columnGap = ValueExtractors.extractDp(data)
                "ColumnRuleWidth" -> ruleWidth = extractRuleWidth(data)
                "ColumnRuleStyle" -> ruleStyle = extractRuleStyle(data)
                "ColumnRuleColor" -> ruleColor = ValueExtractors.extractColor(data)
                "ColumnSpan" -> span = extractColumnSpan(data)
                "ColumnFill" -> fill = extractColumnFill(data)
                // css-overflow-4 §3 `continue: discard` (wave-42 lane W4) —
                // only the DISCARD keyword flips the flag; `auto` (and any
                // future keyword) keeps the normal overflow rendering.
                "Continue" -> continueDiscard =
                    ValueExtractors.extractKeyword(data)?.uppercase() == "DISCARD"
            }
        }

        // Wave-47 lane Z2: ONE writing-mode read feeds both vertical flags
        // below — the shared typography decoder, so the mode and its block
        // direction can never disagree (and the scan runs once, not twice).
        val wm = com.styleconverter.runtime.typography.text.TextExtractor
            .extractWritingModeConfig(properties)

        return MultiColumnConfig(
            columnCount = columnCount,
            columnWidth = columnWidth,
            columnGap = columnGap,
            ruleWidth = ruleWidth,
            ruleStyle = ruleStyle,
            ruleColor = ruleColor,
            span = span,
            fill = fill,
            // Wave-42: `continue: discard` rides the config into the
            // spanner-flow plan (see MultiColumnConfig.continueDiscard).
            continueDiscard = continueDiscard,
            // Wave-47 lane Z2: vertical writing modes now have their OWN
            // sole-child fragmentation pass (VerticalMulticolMeasure); this
            // flag routes into it, and every shape that pass declines still
            // bails (+log once) exactly as before. The shared `wm` read
            // above is the typography decoder that owns the WritingMode IR
            // property (same properties list, so no extra plumbing upstream).
            verticalWritingMode = wm.isVertical,
            // Wave-47 lane Z2: which way the vertical block axis runs
            // (css-writing-modes-4 §6.4 — vertical-rl/sideways-rl walk
            // right→left). Same shared read, so the flags cannot disagree.
            verticalBlockRtl =
                wm.writingMode == com.styleconverter.runtime.typography.text.WritingModeValue.VERTICAL_RL ||
                    wm.writingMode == com.styleconverter.runtime.typography.text.WritingModeValue.SIDEWAYS_RL
        )
    }

    private fun extractColumnCount(data: JsonElement?): Int? {
        if (data == null) return null

        val keyword = ValueExtractors.extractKeyword(data)?.lowercase()
        if (keyword == "auto") return null

        val obj = data as? JsonObject
        if (obj != null) {
            obj["count"]?.jsonPrimitive?.let { prim ->
                return prim.content.toIntOrNull()
            }
            obj["value"]?.jsonPrimitive?.let { prim ->
                return prim.content.toIntOrNull()
            }
        }

        return data.jsonPrimitive?.content?.toIntOrNull()
    }

    private fun extractColumnWidth(data: JsonElement?): androidx.compose.ui.unit.Dp? {
        val keyword = ValueExtractors.extractKeyword(data)?.lowercase()
        if (keyword == "auto") return null

        return ValueExtractors.extractDp(data)
    }

    private fun extractRuleWidth(data: JsonElement?): androidx.compose.ui.unit.Dp? {
        val keyword = ValueExtractors.extractKeyword(data)?.lowercase()
        return when (keyword) {
            "thin" -> 1.dp
            "medium" -> 3.dp
            "thick" -> 5.dp
            else -> ValueExtractors.extractDp(data)
        }
    }

    private fun extractRuleStyle(data: JsonElement?): ColumnRuleStyle {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase() ?: return ColumnRuleStyle.NONE
        return try {
            ColumnRuleStyle.valueOf(keyword)
        } catch (e: Exception) {
            ColumnRuleStyle.NONE
        }
    }

    private fun extractColumnSpan(data: JsonElement?): ColumnSpan {
        val keyword = ValueExtractors.extractKeyword(data)?.lowercase() ?: return ColumnSpan.NONE
        return when (keyword) {
            "all" -> ColumnSpan.ALL
            else -> ColumnSpan.NONE
        }
    }

    private fun extractColumnFill(data: JsonElement?): ColumnFill {
        val keyword = ValueExtractors.extractKeyword(data)?.lowercase() ?: return ColumnFill.BALANCE
        return when (keyword) {
            "auto" -> ColumnFill.AUTO
            "balance-all" -> ColumnFill.BALANCE_ALL
            else -> ColumnFill.BALANCE
        }
    }

    fun isMultiColumnProperty(type: String): Boolean {
        return type in MULTI_COLUMN_PROPERTIES
    }

    private val MULTI_COLUMN_PROPERTIES = setOf(
        "ColumnCount", "ColumnWidth", "ColumnGap",
        "ColumnRuleWidth", "ColumnRuleStyle", "ColumnRuleColor",
        "ColumnSpan", "ColumnFill"
    )
}
