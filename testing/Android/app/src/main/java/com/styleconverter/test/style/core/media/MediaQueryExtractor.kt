package com.styleconverter.test.style.core.media

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Parses CSS media query strings into [MediaQueryConfig] objects.
 *
 * Supports standard CSS media query syntax including:
 * - Feature queries: (min-width: 768px), (max-height: 500px)
 * - Orientation: (orientation: portrait)
 * - Color scheme: (prefers-color-scheme: dark)
 * - Aspect ratio: (min-aspect-ratio: 16/9)
 * - Logical operators: and, or, not
 *
 * ## Example
 * ```kotlin
 * val config = MediaQueryExtractor.parse("(min-width: 768px) and (orientation: landscape)")
 * ```
 */
object MediaQueryExtractor {

    // Regex patterns for media query parsing
    private val FEATURE_PATTERN = Regex("""\(\s*([a-z-]+)\s*:\s*([^)]+)\s*\)""", RegexOption.IGNORE_CASE)
    private val LENGTH_PATTERN = Regex("""(-?\d+(?:\.\d+)?)\s*(px|dp|em|rem|vh|vw|%)""", RegexOption.IGNORE_CASE)
    private val RATIO_PATTERN = Regex("""(\d+)\s*/\s*(\d+)""")

    /**
     * Parse a CSS media query string into a list of conditions.
     *
     * @param query The raw media query string (e.g., "(min-width: 768px) and (max-width: 1024px)")
     * @return List of parsed conditions. Empty list if parsing fails.
     */
    fun parse(query: String): List<MediaQueryConfig> {
        if (query.isBlank()) return emptyList()

        val normalizedQuery = query.trim().lowercase()
        val conditions = mutableListOf<MediaQueryConfig>()

        // Handle screen/all/print media types
        val cleanedQuery = normalizedQuery
            .replace(Regex("""^(screen|all|print)\s*(and\s*)?"""), "")
            .trim()

        // Check for NOT operator at the start
        val isNegated = cleanedQuery.startsWith("not ")
        val queryWithoutNot = if (isNegated) cleanedQuery.removePrefix("not ").trim() else cleanedQuery

        // Split by "and" and "or" operators
        val parts = splitByOperators(queryWithoutNot)

        for ((part, operator) in parts) {
            val config = parseCondition(part.trim())
            if (config != null) {
                conditions.add(config.copy(
                    negate = isNegated && conditions.isEmpty(),
                    operator = operator
                ))
            }
        }

        return conditions
    }

    /**
     * Split query string by logical operators while preserving operator info.
     */
    private fun splitByOperators(query: String): List<Pair<String, LogicalOperator>> {
        val result = mutableListOf<Pair<String, LogicalOperator>>()
        var remaining = query
        var nextOperator = LogicalOperator.AND

        while (remaining.isNotEmpty()) {
            // Find next operator
            val andIndex = remaining.indexOf(" and ")
            val orIndex = remaining.indexOf(" or ")
            val commaIndex = remaining.indexOf(",")

            val (splitIndex, splitLength, foundOperator) = when {
                andIndex >= 0 && (orIndex < 0 || andIndex < orIndex) && (commaIndex < 0 || andIndex < commaIndex) ->
                    Triple(andIndex, 5, LogicalOperator.AND)
                orIndex >= 0 && (commaIndex < 0 || orIndex < commaIndex) ->
                    Triple(orIndex, 4, LogicalOperator.OR)
                commaIndex >= 0 ->
                    Triple(commaIndex, 1, LogicalOperator.OR)
                else ->
                    Triple(-1, 0, LogicalOperator.AND)
            }

            if (splitIndex < 0) {
                result.add(remaining.trim() to nextOperator)
                break
            } else {
                val part = remaining.substring(0, splitIndex).trim()
                if (part.isNotEmpty()) {
                    result.add(part to nextOperator)
                }
                remaining = remaining.substring(splitIndex + splitLength).trim()
                nextOperator = foundOperator
            }
        }

        return result
    }

    /**
     * Parse a single media feature condition.
     */
    private fun parseCondition(condition: String): MediaQueryConfig? {
        val match = FEATURE_PATTERN.find(condition) ?: return null
        val feature = match.groupValues[1].lowercase()
        val value = match.groupValues[2].trim()

        return when (feature) {
            "min-width" -> MediaQueryConfig(minWidth = parseLength(value))
            "max-width" -> MediaQueryConfig(maxWidth = parseLength(value))
            "width" -> MediaQueryConfig(width = parseLength(value))
            "min-height" -> MediaQueryConfig(minHeight = parseLength(value))
            "max-height" -> MediaQueryConfig(maxHeight = parseLength(value))
            "height" -> MediaQueryConfig(height = parseLength(value))
            "orientation" -> MediaQueryConfig(orientation = parseOrientation(value))
            "prefers-color-scheme" -> MediaQueryConfig(colorScheme = parseColorScheme(value))
            "min-aspect-ratio" -> MediaQueryConfig(minAspectRatio = parseAspectRatio(value))
            "max-aspect-ratio" -> MediaQueryConfig(maxAspectRatio = parseAspectRatio(value))
            "display-mode" -> MediaQueryConfig(displayMode = parseDisplayMode(value))
            "prefers-reduced-motion" -> MediaQueryConfig(prefersReducedMotion = parseReducedMotion(value))
            "prefers-contrast" -> MediaQueryConfig(prefersContrast = parseContrast(value))
            "hover" -> MediaQueryConfig(hoverCapability = parseHover(value))
            "pointer" -> MediaQueryConfig(pointerType = parsePointer(value))
            else -> null
        }
    }

    /**
     * Parse prefers-reduced-motion value.
     */
    private fun parseReducedMotion(value: String): ReducedMotion? {
        return when (value.lowercase().trim()) {
            "no-preference" -> ReducedMotion.NO_PREFERENCE
            "reduce" -> ReducedMotion.REDUCE
            else -> null
        }
    }

    /**
     * Parse prefers-contrast value.
     */
    private fun parseContrast(value: String): ContrastPreference? {
        return when (value.lowercase().trim()) {
            "no-preference" -> ContrastPreference.NO_PREFERENCE
            "more" -> ContrastPreference.MORE
            "less" -> ContrastPreference.LESS
            "custom" -> ContrastPreference.CUSTOM
            else -> null
        }
    }

    /**
     * Parse hover capability value.
     */
    private fun parseHover(value: String): HoverCapability? {
        return when (value.lowercase().trim()) {
            "none" -> HoverCapability.NONE
            "hover" -> HoverCapability.HOVER
            else -> null
        }
    }

    /**
     * Parse pointer type value.
     */
    private fun parsePointer(value: String): PointerType? {
        return when (value.lowercase().trim()) {
            "none" -> PointerType.NONE
            "coarse" -> PointerType.COARSE
            "fine" -> PointerType.FINE
            else -> null
        }
    }

    /**
     * Parse a CSS length value to Dp.
     */
    private fun parseLength(value: String): Dp? {
        val match = LENGTH_PATTERN.find(value) ?: return null
        val number = match.groupValues[1].toFloatOrNull() ?: return null
        val unit = match.groupValues[2].lowercase()

        return when (unit) {
            "px", "dp" -> number.dp
            "em", "rem" -> (number * 16).dp // Approximate em/rem to 16px base
            "vh", "vw" -> null // Cannot resolve viewport units statically
            "%" -> null // Cannot resolve percentage statically
            else -> number.dp
        }
    }

    /**
     * Parse orientation value.
     */
    private fun parseOrientation(value: String): Orientation? {
        return when (value.lowercase().trim()) {
            "portrait" -> Orientation.PORTRAIT
            "landscape" -> Orientation.LANDSCAPE
            else -> null
        }
    }

    /**
     * Parse color scheme value.
     */
    private fun parseColorScheme(value: String): ColorScheme? {
        return when (value.lowercase().trim()) {
            "light" -> ColorScheme.LIGHT
            "dark" -> ColorScheme.DARK
            else -> null
        }
    }

    /**
     * Parse aspect ratio (e.g., "16/9" or "1.777").
     */
    private fun parseAspectRatio(value: String): Float? {
        // Try fraction format first
        val ratioMatch = RATIO_PATTERN.find(value)
        if (ratioMatch != null) {
            val numerator = ratioMatch.groupValues[1].toFloatOrNull() ?: return null
            val denominator = ratioMatch.groupValues[2].toFloatOrNull() ?: return null
            if (denominator == 0f) return null
            return numerator / denominator
        }

        // Try decimal format
        return value.trim().toFloatOrNull()
    }

    /**
     * Parse display mode value.
     */
    private fun parseDisplayMode(value: String): DisplayMode? {
        return when (value.lowercase().trim()) {
            "browser" -> DisplayMode.BROWSER
            "standalone" -> DisplayMode.STANDALONE
            "fullscreen" -> DisplayMode.FULLSCREEN
            "minimal-ui" -> DisplayMode.MINIMAL_UI
            else -> null
        }
    }
}
