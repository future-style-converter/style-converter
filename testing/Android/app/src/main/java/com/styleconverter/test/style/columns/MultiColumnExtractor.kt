package com.styleconverter.test.style.columns

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.core.types.ValueExtractors
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
            }
        }

        return MultiColumnConfig(
            columnCount = columnCount,
            columnWidth = columnWidth,
            columnGap = columnGap,
            ruleWidth = ruleWidth,
            ruleStyle = ruleStyle,
            ruleColor = ruleColor,
            span = span,
            fill = fill
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
