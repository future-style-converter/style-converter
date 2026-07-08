package com.styleconverter.test.style.print

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Extracts print configuration from IR properties.
 */
object PrintExtractor {

    /**
     * Extract print configuration from property pairs.
     */
    fun extractPrintConfig(properties: List<Pair<String, JsonElement?>>): PrintConfig {
        var orphans = 2
        var widows = 2
        var pageBreakBefore = PageBreakValue.AUTO
        var pageBreakAfter = PageBreakValue.AUTO
        var pageBreakInside = BreakInsideValue.AUTO
        var breakBefore = PageBreakValue.AUTO
        var breakAfter = PageBreakValue.AUTO
        var breakInside = BreakInsideValue.AUTO

        for ((type, data) in properties) {
            when (type) {
                "Orphans" -> orphans = extractInteger(data, 2)
                "Widows" -> widows = extractInteger(data, 2)
                "PageBreakBefore" -> pageBreakBefore = extractPageBreak(data)
                "PageBreakAfter" -> pageBreakAfter = extractPageBreak(data)
                "PageBreakInside" -> pageBreakInside = extractBreakInside(data)
                "BreakBefore" -> breakBefore = extractPageBreak(data)
                "BreakAfter" -> breakAfter = extractPageBreak(data)
                "BreakInside" -> breakInside = extractBreakInside(data)
            }
        }

        return PrintConfig(
            orphans = orphans,
            widows = widows,
            pageBreakBefore = pageBreakBefore,
            pageBreakAfter = pageBreakAfter,
            pageBreakInside = pageBreakInside,
            breakBefore = breakBefore,
            breakAfter = breakAfter,
            breakInside = breakInside
        )
    }

    private fun extractInteger(data: JsonElement?, default: Int): Int {
        if (data == null) return default
        return when (data) {
            is JsonPrimitive -> data.intOrNull ?: default
            else -> default
        }
    }

    private fun extractPageBreak(data: JsonElement?): PageBreakValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return PageBreakValue.AUTO

        return when (keyword) {
            "AUTO" -> PageBreakValue.AUTO
            "ALWAYS" -> PageBreakValue.ALWAYS
            "AVOID" -> PageBreakValue.AVOID
            "LEFT" -> PageBreakValue.LEFT
            "RIGHT" -> PageBreakValue.RIGHT
            "RECTO" -> PageBreakValue.RECTO
            "VERSO" -> PageBreakValue.VERSO
            "PAGE" -> PageBreakValue.ALWAYS
            else -> PageBreakValue.AUTO
        }
    }

    private fun extractBreakInside(data: JsonElement?): BreakInsideValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return BreakInsideValue.AUTO

        return when (keyword) {
            "AUTO" -> BreakInsideValue.AUTO
            "AVOID" -> BreakInsideValue.AVOID
            "AVOID_PAGE" -> BreakInsideValue.AVOID_PAGE
            "AVOID_COLUMN" -> BreakInsideValue.AVOID_COLUMN
            "AVOID_REGION" -> BreakInsideValue.AVOID_REGION
            else -> BreakInsideValue.AUTO
        }
    }

    /**
     * Check if a property type is print-related.
     */
    fun isPrintProperty(type: String): Boolean {
        return type in PRINT_PROPERTIES
    }

    private val PRINT_PROPERTIES = setOf(
        "Orphans", "Widows",
        "PageBreakBefore", "PageBreakAfter", "PageBreakInside",
        "BreakBefore", "BreakAfter", "BreakInside"
    )
}
