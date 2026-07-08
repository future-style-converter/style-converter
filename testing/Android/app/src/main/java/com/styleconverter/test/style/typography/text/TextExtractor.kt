package com.styleconverter.test.style.typography.text

import com.styleconverter.test.style.PropertyRegistry
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement

/**
 * Extracts text-related configuration from IR properties.
 */
object TextExtractor {

    init {
        // Claim writing-mode + text-wrap + hyphenation-limit families. The
        // writing-mode quartet feeds WritingModeApplier (vertical text,
        // direction reversal). text-wrap sub-properties feed TextWrapConfig.
        // HyphenateLimit* are parse-only today — Compose's LineBreak API has
        // no knob for these, but registering them here keeps the legacy
        // dispatcher from re-entering them.
        // CSS spec: https://drafts.csswg.org/css-writing-modes-4/
        //           https://drafts.csswg.org/css-text-4/#wrapping
        PropertyRegistry.migrated(
            // Writing mode family
            "WritingMode",
            "TextOrientation",
            "Direction",
            "UnicodeBidi",
            // Text wrap family (modern CSS Text 4)
            "TextWrap",
            "TextWrapMode",
            "TextWrapStyle",
            // Hyphenation tuning (parse-only on Compose)
            "HyphenateCharacter",
            "HyphenateLimitChars",
            "HyphenateLimitLast",
            "HyphenateLimitLines",
            "HyphenateLimitZone",
            owner = "typography/text"
        )
    }

    fun extractWritingModeConfig(properties: List<Pair<String, JsonElement?>>): WritingModeConfig {
        var writingMode = WritingModeValue.HORIZONTAL_TB
        var textOrientation = TextOrientationValue.MIXED
        var direction = DirectionValue.LTR
        var unicodeBidi = UnicodeBidiValue.NORMAL

        for ((type, data) in properties) {
            when (type) {
                "WritingMode" -> writingMode = extractWritingMode(data)
                "TextOrientation" -> textOrientation = extractTextOrientation(data)
                "Direction" -> direction = extractDirection(data)
                "UnicodeBidi" -> unicodeBidi = extractUnicodeBidi(data)
            }
        }

        return WritingModeConfig(
            writingMode = writingMode,
            textOrientation = textOrientation,
            direction = direction,
            unicodeBidi = unicodeBidi
        )
    }

    fun extractTextWrapConfig(properties: List<Pair<String, JsonElement?>>): TextWrapConfig {
        var textWrap = TextWrapValue.WRAP
        var whiteSpace = WhiteSpaceValue.NORMAL
        var wordBreak = WordBreakValue.NORMAL
        var overflowWrap = OverflowWrapValue.NORMAL
        var hyphens = HyphensValue.MANUAL
        var hyphenateCharacter = "auto"
        var textIndent: androidx.compose.ui.unit.Dp? = null

        for ((type, data) in properties) {
            when (type) {
                "TextWrap" -> textWrap = extractTextWrap(data)
                "WhiteSpace" -> whiteSpace = extractWhiteSpace(data)
                "WordBreak" -> wordBreak = extractWordBreak(data)
                "OverflowWrap", "WordWrap" -> overflowWrap = extractOverflowWrap(data)
                "Hyphens" -> hyphens = extractHyphens(data)
                "HyphenateCharacter" -> hyphenateCharacter = ValueExtractors.extractKeyword(data) ?: "auto"
                "TextIndent" -> textIndent = ValueExtractors.extractDp(data)
            }
        }

        return TextWrapConfig(
            textWrap = textWrap,
            whiteSpace = whiteSpace,
            wordBreak = wordBreak,
            overflowWrap = overflowWrap,
            hyphens = hyphens,
            hyphenateCharacter = hyphenateCharacter,
            textIndent = textIndent
        )
    }

    private fun extractWritingMode(data: JsonElement?): WritingModeValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return WritingModeValue.HORIZONTAL_TB
        return try {
            WritingModeValue.valueOf(keyword)
        } catch (e: Exception) {
            WritingModeValue.HORIZONTAL_TB
        }
    }

    private fun extractTextOrientation(data: JsonElement?): TextOrientationValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return TextOrientationValue.MIXED
        return try {
            TextOrientationValue.valueOf(keyword)
        } catch (e: Exception) {
            TextOrientationValue.MIXED
        }
    }

    private fun extractDirection(data: JsonElement?): DirectionValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return DirectionValue.LTR
        return when (keyword) {
            "RTL" -> DirectionValue.RTL
            else -> DirectionValue.LTR
        }
    }

    private fun extractUnicodeBidi(data: JsonElement?): UnicodeBidiValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return UnicodeBidiValue.NORMAL
        return try {
            UnicodeBidiValue.valueOf(keyword)
        } catch (e: Exception) {
            UnicodeBidiValue.NORMAL
        }
    }

    private fun extractTextWrap(data: JsonElement?): TextWrapValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return TextWrapValue.WRAP
        return try {
            TextWrapValue.valueOf(keyword)
        } catch (e: Exception) {
            TextWrapValue.WRAP
        }
    }

    private fun extractWhiteSpace(data: JsonElement?): WhiteSpaceValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return WhiteSpaceValue.NORMAL
        return try {
            WhiteSpaceValue.valueOf(keyword)
        } catch (e: Exception) {
            WhiteSpaceValue.NORMAL
        }
    }

    private fun extractWordBreak(data: JsonElement?): WordBreakValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return WordBreakValue.NORMAL
        return try {
            WordBreakValue.valueOf(keyword)
        } catch (e: Exception) {
            WordBreakValue.NORMAL
        }
    }

    private fun extractOverflowWrap(data: JsonElement?): OverflowWrapValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return OverflowWrapValue.NORMAL
        return try {
            OverflowWrapValue.valueOf(keyword)
        } catch (e: Exception) {
            OverflowWrapValue.NORMAL
        }
    }

    private fun extractHyphens(data: JsonElement?): HyphensValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return HyphensValue.MANUAL
        return try {
            HyphensValue.valueOf(keyword)
        } catch (e: Exception) {
            HyphensValue.MANUAL
        }
    }

    fun isTextProperty(type: String): Boolean {
        return type in TEXT_PROPERTIES
    }

    private val TEXT_PROPERTIES = setOf(
        "WritingMode", "TextOrientation", "Direction", "UnicodeBidi",
        "TextWrap", "WhiteSpace", "WordBreak", "OverflowWrap", "WordWrap",
        "Hyphens", "HyphenateCharacter", "HyphenateLimitChars",
        "TextIndent"
    )
}
