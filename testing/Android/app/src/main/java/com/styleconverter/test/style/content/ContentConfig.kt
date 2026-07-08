package com.styleconverter.test.style.content

/**
 * Configuration for CSS counter-related properties.
 *
 * ## Supported Properties
 * - counter-reset: Initialize counters
 * - counter-increment: Increment/decrement counters
 * - counter-set: Set counter values
 *
 * ## Usage
 * Counters are typically used with ::before/::after pseudo-elements
 * and content property to generate numbered content.
 */
data class CounterConfig(
    /** Counters to reset with their initial values */
    val reset: Map<String, Int> = emptyMap(),
    /** Counters to increment with their increment values */
    val increment: Map<String, Int> = emptyMap(),
    /** Counters to set with their values */
    val set: Map<String, Int> = emptyMap()
) {
    val hasCounters: Boolean
        get() = reset.isNotEmpty() || increment.isNotEmpty() || set.isNotEmpty()

    companion object {
        val Default = CounterConfig()
    }
}

/**
 * Configuration for CSS quotes property.
 *
 * ## Supported Properties
 * - quotes: Defines quotation marks for q and blockquote elements
 *
 * ## Values
 * - auto: Use language-appropriate quotes
 * - none: No quotes
 * - Custom pairs: e.g., "«" "»" "‹" "›"
 */
data class QuotesConfig(
    /** Opening/closing quote pairs for nested quotes */
    val quotePairs: List<QuotePair> = listOf(QuotePair.DEFAULT),
    /** Use auto (language-dependent) quotes */
    val isAuto: Boolean = true,
    /** Use no quotes */
    val isNone: Boolean = false
) {
    val hasQuotes: Boolean
        get() = !isNone && (isAuto || quotePairs.isNotEmpty())

    /** Get quote pair for the given nesting level (0-indexed) */
    fun getQuotePair(level: Int): QuotePair {
        if (quotePairs.isEmpty()) return QuotePair.DEFAULT
        return quotePairs.getOrElse(level % quotePairs.size) { QuotePair.DEFAULT }
    }

    companion object {
        val Auto = QuotesConfig(isAuto = true)
        val None = QuotesConfig(isNone = true, isAuto = false)
        val Default = Auto
    }
}

/**
 * A pair of opening and closing quote marks.
 */
data class QuotePair(
    val open: String,
    val close: String
) {
    companion object {
        /** Standard English double quotes */
        val DEFAULT = QuotePair("\u201C", "\u201D")  // " "
        /** Standard English single quotes */
        val SINGLE = QuotePair("\u2018", "\u2019")  // ' '
        /** French guillemets */
        val FRENCH = QuotePair("\u00AB", "\u00BB")  // « »
        /** German quotes */
        val GERMAN = QuotePair("\u201E", "\u201C")  // „ "
        /** Japanese brackets */
        val JAPANESE = QuotePair("\u300C", "\u300D")  // 「 」
    }
}

/**
 * CSS content property values for generated content.
 */
sealed interface ContentValue {
    /** Normal content (default) */
    data object Normal : ContentValue
    /** No content generated */
    data object None : ContentValue
    /** Text string content */
    data class Text(val value: String) : ContentValue
    /** URL/image content */
    data class Url(val url: String) : ContentValue
    /** Counter reference */
    data class Counter(val name: String, val style: ListStyleType = ListStyleType.DECIMAL) : ContentValue
    /** Counters with separator */
    data class Counters(val name: String, val separator: String, val style: ListStyleType = ListStyleType.DECIMAL) : ContentValue
    /** Attribute value */
    data class Attr(val attributeName: String) : ContentValue
    /** Open quote */
    data object OpenQuote : ContentValue
    /** Close quote */
    data object CloseQuote : ContentValue
    /** No open quote but increases nesting */
    data object NoOpenQuote : ContentValue
    /** No close quote but decreases nesting */
    data object NoCloseQuote : ContentValue
}

/**
 * List style types for counters.
 */
enum class ListStyleType {
    NONE,
    DISC,
    CIRCLE,
    SQUARE,
    DECIMAL,
    DECIMAL_LEADING_ZERO,
    LOWER_ROMAN,
    UPPER_ROMAN,
    LOWER_GREEK,
    LOWER_LATIN,
    UPPER_LATIN,
    ARMENIAN,
    GEORGIAN,
    LOWER_ALPHA,
    UPPER_ALPHA
}
