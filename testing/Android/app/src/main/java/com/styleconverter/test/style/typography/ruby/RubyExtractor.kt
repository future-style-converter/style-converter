package com.styleconverter.test.style.typography.ruby

import com.styleconverter.test.style.PropertyRegistry
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement

/**
 * Extracts ruby configuration from IR properties.
 */
object RubyExtractor {

    init {
        // Claim the CSS-Ruby-1 family. Compose has no native ruby rendering;
        // RubyConfig is currently parse-only (we keep the extracted values so
        // future ruby-aware layouts can read them), but registering here
        // blocks the legacy dispatcher from emitting warnings for these.
        // CSS spec: https://drafts.csswg.org/css-ruby-1/
        PropertyRegistry.migrated(
            "RubyAlign",
            "RubyMerge",
            "RubyOverhang",
            "RubyPosition",
            owner = "typography/ruby"
        )
    }

    /**
     * Extract ruby configuration from property pairs.
     */
    fun extractRubyConfig(properties: List<Pair<String, JsonElement?>>): RubyConfig {
        var rubyAlign = RubyAlignValue.SPACE_AROUND
        var rubyPosition = RubyPositionValue.OVER
        var rubyMerge = RubyMergeValue.SEPARATE
        var rubyOverhang = RubyOverhangValue.AUTO

        for ((type, data) in properties) {
            when (type) {
                "RubyAlign" -> rubyAlign = extractRubyAlign(data)
                "RubyPosition" -> rubyPosition = extractRubyPosition(data)
                "RubyMerge" -> rubyMerge = extractRubyMerge(data)
                "RubyOverhang" -> rubyOverhang = extractRubyOverhang(data)
            }
        }

        return RubyConfig(
            rubyAlign = rubyAlign,
            rubyPosition = rubyPosition,
            rubyMerge = rubyMerge,
            rubyOverhang = rubyOverhang
        )
    }

    private fun extractRubyAlign(data: JsonElement?): RubyAlignValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return RubyAlignValue.SPACE_AROUND

        return when (keyword) {
            "START" -> RubyAlignValue.START
            "CENTER" -> RubyAlignValue.CENTER
            "SPACE_BETWEEN" -> RubyAlignValue.SPACE_BETWEEN
            "SPACE_AROUND" -> RubyAlignValue.SPACE_AROUND
            else -> RubyAlignValue.SPACE_AROUND
        }
    }

    private fun extractRubyPosition(data: JsonElement?): RubyPositionValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return RubyPositionValue.OVER

        return when (keyword) {
            "OVER" -> RubyPositionValue.OVER
            "UNDER" -> RubyPositionValue.UNDER
            "INTER_CHARACTER" -> RubyPositionValue.INTER_CHARACTER
            "ALTERNATE" -> RubyPositionValue.ALTERNATE
            else -> RubyPositionValue.OVER
        }
    }

    private fun extractRubyMerge(data: JsonElement?): RubyMergeValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return RubyMergeValue.SEPARATE

        return when (keyword) {
            "SEPARATE" -> RubyMergeValue.SEPARATE
            "MERGE" -> RubyMergeValue.MERGE
            "AUTO" -> RubyMergeValue.AUTO
            else -> RubyMergeValue.SEPARATE
        }
    }

    private fun extractRubyOverhang(data: JsonElement?): RubyOverhangValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return RubyOverhangValue.AUTO

        return when (keyword) {
            "AUTO" -> RubyOverhangValue.AUTO
            "START" -> RubyOverhangValue.START
            "END" -> RubyOverhangValue.END
            "NONE" -> RubyOverhangValue.NONE
            else -> RubyOverhangValue.AUTO
        }
    }

    /**
     * Check if a property type is ruby-related.
     */
    fun isRubyProperty(type: String): Boolean {
        return type in RUBY_PROPERTIES
    }

    private val RUBY_PROPERTIES = setOf(
        "RubyAlign", "RubyPosition", "RubyMerge", "RubyOverhang"
    )
}
