package app.parsing.css.properties.longhands.content

import app.irmodels.IRProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.irmodels.properties.content.*
import app.parsing.css.properties.primitiveParsers.UrlParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for `content` property.
 *
 * Supports single values and multiple values like:
 * - content: "Hello"
 * - content: counter(section)
 * - content: "Chapter " counter(chapter) ": " attr(data-title)
 * - content: var(--custom-content)
 */
object ContentPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lowered = trimmed.lowercase()

        // Handle global keywords first
        if (GlobalKeywords.isGlobalKeyword(lowered)) {
            return ContentProperty(ContentValue.Keyword(lowered))
        }

        // Simple keywords
        when (lowered) {
            "normal" -> return ContentProperty(ContentValue.Normal)
            "none" -> return ContentProperty(ContentValue.None)
            "open-quote" -> return ContentProperty(ContentValue.OpenQuote)
            "close-quote" -> return ContentProperty(ContentValue.CloseQuote)
            "no-open-quote" -> return ContentProperty(ContentValue.NoOpenQuote)
            "no-close-quote" -> return ContentProperty(ContentValue.NoCloseQuote)
        }

        // Check for var() or other complex expressions that we can't fully parse
        if (ExpressionDetector.containsExpression(lowered)) {
            return ContentProperty(ContentValue.Raw(trimmed))
        }

        // Check for url() / "alt-text" syntax
        if (lowered.contains("url(") && trimmed.contains(" / ")) {
            val urlAltParsed = parseUrlWithAlt(trimmed)
            if (urlAltParsed != null) {
                return ContentProperty(urlAltParsed)
            }
        }

        // Try to parse as multiple parts
        val parts = splitContentParts(trimmed)
        if (parts.size == 1) {
            val parsed = parseSinglePart(parts[0])
            return ContentProperty(parsed ?: ContentValue.Raw(trimmed))
        } else if (parts.size > 1) {
            val values = parts.mapNotNull { parseSinglePart(it) }
            if (values.size != parts.size) {
                // Fall back to Raw for complex unparseable values
                return ContentProperty(ContentValue.Raw(trimmed))
            }
            return ContentProperty(ContentValue.Multiple(values))
        }

        // Fall back to Raw for any unparseable values
        return ContentProperty(ContentValue.Raw(trimmed))
    }

    private fun parseSinglePart(part: String): ContentValue? {
        val trimmed = part.trim()
        val lowered = trimmed.lowercase()

        // Check for keywords
        return when (lowered) {
            "open-quote" -> ContentValue.OpenQuote
            "close-quote" -> ContentValue.CloseQuote
            "no-open-quote" -> ContentValue.NoOpenQuote
            "no-close-quote" -> ContentValue.NoCloseQuote
            else -> {
                // Try parsing as quoted string with escape handling
                if (trimmed.length >= 2 &&
                    ((trimmed.startsWith("\"") && endsWithUnescapedQuote(trimmed, '"')) ||
                    (trimmed.startsWith("'") && endsWithUnescapedQuote(trimmed, '\'')))) {
                    val unescaped = unescapeString(trimmed.substring(1, trimmed.length - 1))
                    return ContentValue.StringValue(unescaped)
                }
                // Try parsing as function
                if (lowered.startsWith("url(")) {
                    val url = UrlParser.parse(trimmed) ?: return null
                    return ContentValue.UrlValue(url)
                }
                if (lowered.startsWith("counter(")) {
                    val params = extractFunctionParams(trimmed) ?: return null
                    val funcParts = params.split(",").map { it.trim() }
                    return ContentValue.Counter(funcParts[0], funcParts.getOrNull(1))
                }
                if (lowered.startsWith("counters(")) {
                    val params = extractFunctionParams(trimmed) ?: return null
                    val funcParts = params.split(",").map { it.trim() }
                    if (funcParts.size < 2) return null
                    return ContentValue.Counters(funcParts[0], funcParts[1], funcParts.getOrNull(2))
                }
                if (lowered.startsWith("attr(")) {
                    val params = extractFunctionParams(trimmed) ?: return null
                    return ContentValue.Attr(params)
                }
                // Advanced content functions
                if (lowered.startsWith("leader(")) {
                    val params = extractFunctionParams(trimmed) ?: return null
                    return ContentValue.Function("leader", params)
                }
                if (lowered.startsWith("target-counter(")) {
                    val params = extractFunctionParams(trimmed) ?: return null
                    return ContentValue.Function("target-counter", params)
                }
                if (lowered.startsWith("target-counters(")) {
                    val params = extractFunctionParams(trimmed) ?: return null
                    return ContentValue.Function("target-counters", params)
                }
                if (lowered.startsWith("target-text(")) {
                    val params = extractFunctionParams(trimmed) ?: return null
                    return ContentValue.Function("target-text", params)
                }
                if (lowered.startsWith("element(")) {
                    val params = extractFunctionParams(trimmed) ?: return null
                    return ContentValue.Function("element", params)
                }
                // Image functions in content
                if (lowered.startsWith("linear-gradient(") ||
                    lowered.startsWith("radial-gradient(") ||
                    lowered.startsWith("conic-gradient(") ||
                    lowered.startsWith("repeating-linear-gradient(") ||
                    lowered.startsWith("repeating-radial-gradient(") ||
                    lowered.startsWith("repeating-conic-gradient(")) {
                    return ContentValue.Image(trimmed)
                }
                if (lowered.startsWith("image-set(")) {
                    return ContentValue.Image(trimmed)
                }
                if (lowered.startsWith("cross-fade(")) {
                    return ContentValue.Image(trimmed)
                }
                if (lowered.startsWith("image(")) {
                    return ContentValue.Image(trimmed)
                }
                null
            }
        }
    }

    /**
     * Split content value into parts, respecting quotes, escape sequences, and function parentheses.
     * Example: "Chapter " counter(chapter) ": " attr(data-title)
     * -> ["\"Chapter \"", "counter(chapter)", "\": \"", "attr(data-title)"]
     */
    private fun splitContentParts(value: String): List<String> {
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        var i = 0
        var inQuotes = false
        var quoteChar: Char? = null
        var parenDepth = 0

        while (i < value.length) {
            val c = value[i]

            when {
                // Handle escape sequences inside quotes
                c == '\\' && inQuotes && i + 1 < value.length -> {
                    current.append(c)
                    i++
                    current.append(value[i])
                }
                // Handle quotes
                (c == '"' || c == '\'') && parenDepth == 0 -> {
                    if (!inQuotes) {
                        // Start of quoted string - first save any accumulated content
                        if (current.isNotBlank()) {
                            parts.add(current.toString().trim())
                            current = StringBuilder()
                        }
                        inQuotes = true
                        quoteChar = c
                        current.append(c)
                    } else if (c == quoteChar) {
                        // End of quoted string
                        current.append(c)
                        parts.add(current.toString())
                        current = StringBuilder()
                        inQuotes = false
                        quoteChar = null
                    } else {
                        // Different quote inside string
                        current.append(c)
                    }
                }
                // Handle parentheses (for functions)
                c == '(' && !inQuotes -> {
                    parenDepth++
                    current.append(c)
                }
                c == ')' && !inQuotes -> {
                    parenDepth--
                    current.append(c)
                    if (parenDepth == 0) {
                        parts.add(current.toString().trim())
                        current = StringBuilder()
                    }
                }
                // Handle whitespace outside of quotes/functions
                c.isWhitespace() && !inQuotes && parenDepth == 0 -> {
                    if (current.isNotBlank()) {
                        parts.add(current.toString().trim())
                        current = StringBuilder()
                    }
                }
                else -> {
                    current.append(c)
                }
            }
            i++
        }

        // Don't forget any remaining content
        if (current.isNotBlank()) {
            parts.add(current.toString().trim())
        }

        return parts
    }

    private fun extractFunctionParams(value: String): String? {
        val start = value.indexOf("(")
        val end = value.lastIndexOf(")")
        if (start == -1 || end == -1 || end <= start) return null
        return value.substring(start + 1, end).trim()
    }

    /**
     * Parse url() / "alt-text" syntax.
     * Example: url(icon.svg) / "[icon]"
     */
    private fun parseUrlWithAlt(value: String): ContentValue? {
        // Split on " / " (the alt separator)
        val separatorIdx = value.indexOf(" / ")
        if (separatorIdx == -1) return null

        val urlPart = value.substring(0, separatorIdx).trim()
        val altPart = value.substring(separatorIdx + 3).trim()

        // Parse the URL
        val url = UrlParser.parse(urlPart) ?: return null

        // Parse the alt text (should be a quoted string)
        if (altPart.length >= 2 &&
            ((altPart.startsWith("\"") && endsWithUnescapedQuote(altPart, '"')) ||
             (altPart.startsWith("'") && endsWithUnescapedQuote(altPart, '\'')))) {
            val altText = unescapeString(altPart.substring(1, altPart.length - 1))
            return ContentValue.UrlWithAlt(url, altText)
        }

        return null
    }

    /**
     * Check if the string ends with an unescaped quote character.
     */
    private fun endsWithUnescapedQuote(s: String, quoteChar: Char): Boolean {
        if (s.isEmpty() || s.last() != quoteChar) return false
        // Count backslashes before the final quote
        var backslashCount = 0
        var i = s.length - 2
        while (i >= 0 && s[i] == '\\') {
            backslashCount++
            i--
        }
        // If even number of backslashes, the quote is unescaped
        return backslashCount % 2 == 0
    }

    /**
     * Unescape a string, handling common escape sequences.
     */
    private fun unescapeString(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                i++
                when (s[i]) {
                    '"' -> sb.append('"')
                    '\'' -> sb.append('\'')
                    '\\' -> sb.append('\\')
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    else -> {
                        // For unknown escapes, keep the character
                        sb.append(s[i])
                    }
                }
            } else {
                sb.append(s[i])
            }
            i++
        }
        return sb.toString()
    }
}
