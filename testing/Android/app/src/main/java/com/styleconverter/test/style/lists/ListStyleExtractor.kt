package com.styleconverter.test.style.lists

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement

object ListStyleExtractor {

    fun extractListStyleConfig(properties: List<Pair<String, JsonElement?>>): ListStyleConfig {
        var config = ListStyleConfig()

        for ((type, data) in properties) {
            config = when (type) {
                "ListStyleType" -> config.copy(listStyleType = extractListStyleType(data))
                "ListStylePosition" -> config.copy(listStylePosition = extractListStylePosition(data))
                "ListStyleImage" -> config.copy(listStyleImage = extractListStyleImage(data))
                else -> config
            }
        }

        return config
    }

    private fun extractListStyleType(json: JsonElement?): ListStyleType {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase()?.replace("-", "_")
            ?: return ListStyleType.DISC

        return when (keyword) {
            "disc" -> ListStyleType.DISC
            "circle" -> ListStyleType.CIRCLE
            "square" -> ListStyleType.SQUARE
            "none" -> ListStyleType.NONE
            "decimal" -> ListStyleType.DECIMAL
            "decimal_leading_zero" -> ListStyleType.DECIMAL_LEADING_ZERO
            "lower_alpha", "lower_latin" -> ListStyleType.LOWER_ALPHA
            "upper_alpha", "upper_latin" -> ListStyleType.UPPER_ALPHA
            "lower_roman" -> ListStyleType.LOWER_ROMAN
            "upper_roman" -> ListStyleType.UPPER_ROMAN
            "lower_greek" -> ListStyleType.LOWER_GREEK
            "upper_greek" -> ListStyleType.UPPER_GREEK
            "armenian" -> ListStyleType.ARMENIAN
            "georgian" -> ListStyleType.GEORGIAN
            "hebrew" -> ListStyleType.HEBREW
            "cjk_decimal" -> ListStyleType.CJK_DECIMAL
            "hiragana" -> ListStyleType.HIRAGANA
            "katakana" -> ListStyleType.KATAKANA
            "hiragana_iroha" -> ListStyleType.HIRAGANA_IROHA
            "katakana_iroha" -> ListStyleType.KATAKANA_IROHA
            else -> ListStyleType.DISC
        }
    }

    private fun extractListStylePosition(json: JsonElement?): ListStylePosition {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase() ?: return ListStylePosition.OUTSIDE
        return when (keyword) {
            "inside" -> ListStylePosition.INSIDE
            "outside" -> ListStylePosition.OUTSIDE
            else -> ListStylePosition.OUTSIDE
        }
    }

    private fun extractListStyleImage(json: JsonElement?): String? {
        // URL for list marker image
        val keyword = ValueExtractors.extractKeyword(json) ?: return null
        if (keyword.startsWith("url(") && keyword.endsWith(")")) {
            return keyword.removeSurrounding("url(", ")").trim().removeSurrounding("\"").removeSurrounding("'")
        }
        return null
    }

    fun isListStyleProperty(type: String): Boolean {
        return type in setOf("ListStyleType", "ListStylePosition", "ListStyleImage", "ListStyle")
    }
}
