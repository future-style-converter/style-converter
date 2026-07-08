package com.styleconverter.test.style.print

/**
 * Page break value options.
 */
enum class PageBreakValue {
    AUTO,
    ALWAYS,
    AVOID,
    LEFT,
    RIGHT,
    RECTO,
    VERSO
}

/**
 * Break inside value options.
 */
enum class BreakInsideValue {
    AUTO,
    AVOID,
    AVOID_PAGE,
    AVOID_COLUMN,
    AVOID_REGION
}

/**
 * Configuration for CSS print-related properties.
 * Includes orphans, widows, page-break, and break properties.
 */
data class PrintConfig(
    val orphans: Int = 2,
    val widows: Int = 2,
    val pageBreakBefore: PageBreakValue = PageBreakValue.AUTO,
    val pageBreakAfter: PageBreakValue = PageBreakValue.AUTO,
    val pageBreakInside: BreakInsideValue = BreakInsideValue.AUTO,
    val breakBefore: PageBreakValue = PageBreakValue.AUTO,
    val breakAfter: PageBreakValue = PageBreakValue.AUTO,
    val breakInside: BreakInsideValue = BreakInsideValue.AUTO
) {
    /**
     * Check if this config has any print properties set.
     */
    val hasPrintProperties: Boolean
        get() = orphans != 2 ||
                widows != 2 ||
                pageBreakBefore != PageBreakValue.AUTO ||
                pageBreakAfter != PageBreakValue.AUTO ||
                pageBreakInside != BreakInsideValue.AUTO ||
                breakBefore != PageBreakValue.AUTO ||
                breakAfter != PageBreakValue.AUTO ||
                breakInside != BreakInsideValue.AUTO
}
