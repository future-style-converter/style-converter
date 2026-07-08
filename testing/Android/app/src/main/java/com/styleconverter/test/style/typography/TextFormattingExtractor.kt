package com.styleconverter.test.style.typography

import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.PropertyRegistry
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Extracts text formatting configuration from IR properties.
 */
object TextFormattingExtractor {

    init {
        // Claim the CSS-Text-4 formatting family: text-transform, white-space
        // (incl. the new split white-space-collapse), word-break/overflow-wrap,
        // hyphens, tab-size, text-indent. All feed TextFormattingConfig which
        // TextStyleApplier reads when composing the final TextStyle.
        // CSS spec: https://drafts.csswg.org/css-text-4/#transforming
        PropertyRegistry.migrated(
            "TextTransform",
            "WhiteSpace",
            "WhiteSpaceCollapse",
            "WordBreak",
            "OverflowWrap",
            "WordWrap",
            "Hyphens",
            "TabSize",
            "TextIndent",
            owner = "typography"
        )
    }

    fun extractTextFormattingConfig(properties: List<Pair<String, JsonElement?>>): TextFormattingConfig {
        var textTransform = TextTransformValue.NONE
        var whiteSpace = WhiteSpaceValue.NORMAL
        var wordBreak = WordBreakValue.NORMAL
        var overflowWrap = OverflowWrapValue.NORMAL
        var hyphens = HyphensValue.MANUAL
        var tabSize = 8
        var textIndent = 0.dp

        for ((type, data) in properties) {
            when (type) {
                "TextTransform" -> textTransform = extractTextTransform(data)
                "WhiteSpace" -> whiteSpace = extractWhiteSpace(data)
                "WordBreak" -> wordBreak = extractWordBreak(data)
                "OverflowWrap", "WordWrap" -> overflowWrap = extractOverflowWrap(data)
                "Hyphens" -> hyphens = extractHyphens(data)
                "TabSize" -> tabSize = extractTabSize(data)
                "TextIndent" -> textIndent = ValueExtractors.extractDp(data) ?: 0.dp
            }
        }

        return TextFormattingConfig(
            textTransform = textTransform,
            whiteSpace = whiteSpace,
            wordBreak = wordBreak,
            overflowWrap = overflowWrap,
            hyphens = hyphens,
            tabSize = tabSize,
            textIndent = textIndent
        )
    }

    private fun extractTextTransform(data: JsonElement?): TextTransformValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return TextTransformValue.NONE

        return when (keyword) {
            "NONE" -> TextTransformValue.NONE
            "CAPITALIZE" -> TextTransformValue.CAPITALIZE
            "UPPERCASE" -> TextTransformValue.UPPERCASE
            "LOWERCASE" -> TextTransformValue.LOWERCASE
            "FULL_WIDTH" -> TextTransformValue.FULL_WIDTH
            "FULL_SIZE_KANA" -> TextTransformValue.FULL_SIZE_KANA
            else -> TextTransformValue.NONE
        }
    }

    private fun extractWhiteSpace(data: JsonElement?): WhiteSpaceValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return WhiteSpaceValue.NORMAL

        return when (keyword) {
            "NORMAL" -> WhiteSpaceValue.NORMAL
            "NOWRAP" -> WhiteSpaceValue.NOWRAP
            "PRE" -> WhiteSpaceValue.PRE
            "PRE_WRAP" -> WhiteSpaceValue.PRE_WRAP
            "PRE_LINE" -> WhiteSpaceValue.PRE_LINE
            "BREAK_SPACES" -> WhiteSpaceValue.BREAK_SPACES
            else -> WhiteSpaceValue.NORMAL
        }
    }

    private fun extractWordBreak(data: JsonElement?): WordBreakValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return WordBreakValue.NORMAL

        return when (keyword) {
            "NORMAL" -> WordBreakValue.NORMAL
            "BREAK_ALL" -> WordBreakValue.BREAK_ALL
            "KEEP_ALL" -> WordBreakValue.KEEP_ALL
            "BREAK_WORD" -> WordBreakValue.BREAK_WORD
            else -> WordBreakValue.NORMAL
        }
    }

    private fun extractOverflowWrap(data: JsonElement?): OverflowWrapValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return OverflowWrapValue.NORMAL

        return when (keyword) {
            "NORMAL" -> OverflowWrapValue.NORMAL
            "BREAK_WORD" -> OverflowWrapValue.BREAK_WORD
            "ANYWHERE" -> OverflowWrapValue.ANYWHERE
            else -> OverflowWrapValue.NORMAL
        }
    }

    private fun extractHyphens(data: JsonElement?): HyphensValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return HyphensValue.MANUAL

        return when (keyword) {
            "NONE" -> HyphensValue.NONE
            "MANUAL" -> HyphensValue.MANUAL
            "AUTO" -> HyphensValue.AUTO
            else -> HyphensValue.MANUAL
        }
    }

    private fun extractTabSize(data: JsonElement?): Int {
        if (data == null) return 8
        return when (data) {
            is JsonPrimitive -> data.intOrNull ?: 8
            else -> 8
        }
    }

    fun isTextFormattingProperty(type: String): Boolean {
        return type in setOf(
            "TextTransform", "WhiteSpace", "WordBreak", "OverflowWrap",
            "WordWrap", "Hyphens", "TabSize", "TextIndent"
        )
    }
}
