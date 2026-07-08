package com.styleconverter.test.style.typography.ruby

/**
 * Ruby align value options.
 */
enum class RubyAlignValue {
    START,
    CENTER,
    SPACE_BETWEEN,
    SPACE_AROUND
}

/**
 * Ruby position value options.
 */
enum class RubyPositionValue {
    OVER,
    UNDER,
    INTER_CHARACTER,
    ALTERNATE
}

/**
 * Ruby merge value options.
 */
enum class RubyMergeValue {
    SEPARATE,
    MERGE,
    AUTO
}

/**
 * Ruby overhang value options.
 */
enum class RubyOverhangValue {
    AUTO,
    START,
    END,
    NONE
}

/**
 * Configuration for CSS ruby properties.
 * Used for East Asian typography annotations.
 */
data class RubyConfig(
    val rubyAlign: RubyAlignValue = RubyAlignValue.SPACE_AROUND,
    val rubyPosition: RubyPositionValue = RubyPositionValue.OVER,
    val rubyMerge: RubyMergeValue = RubyMergeValue.SEPARATE,
    val rubyOverhang: RubyOverhangValue = RubyOverhangValue.AUTO
) {
    /**
     * Check if this config has any ruby properties set.
     */
    val hasRubyProperties: Boolean
        get() = rubyAlign != RubyAlignValue.SPACE_AROUND ||
                rubyPosition != RubyPositionValue.OVER ||
                rubyMerge != RubyMergeValue.SEPARATE ||
                rubyOverhang != RubyOverhangValue.AUTO
}
