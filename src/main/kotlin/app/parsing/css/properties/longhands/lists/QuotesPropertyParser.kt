package app.parsing.css.properties.longhands.lists

import app.irmodels.IRProperty
import app.irmodels.properties.content.QuotesProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object QuotesPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return QuotesProperty(QuotesProperty.Quotes.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return QuotesProperty(QuotesProperty.Quotes.Raw(trimmed))
        }

        return when (lower) {
            "none" -> QuotesProperty(QuotesProperty.Quotes.None())
            "auto" -> QuotesProperty(QuotesProperty.Quotes.Auto())
            else -> {
                // Parse quoted strings handling escaped characters
                val strings = parseQuotedStrings(trimmed)
                if (strings.size >= 2 && strings.size % 2 == 0) {
                    val pairs = strings.chunked(2).map { pair ->
                        QuotesProperty.QuotePair(pair[0], pair[1])
                    }
                    QuotesProperty(QuotesProperty.Quotes.QuotePairs(pairs))
                } else {
                    QuotesProperty(QuotesProperty.Quotes.Raw(trimmed))
                }
            }
        }
    }

    /**
     * Parse quoted strings, handling escaped quotes inside strings.
     * Supports both double and single quoted strings.
     */
    private fun parseQuotedStrings(input: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < input.length) {
            when {
                input[i].isWhitespace() -> i++
                input[i] == '"' -> {
                    val (str, endIdx) = parseQuotedString(input, i, '"')
                    if (str != null) {
                        result.add(str)
                        i = endIdx
                    } else {
                        return emptyList() // Parse error
                    }
                }
                input[i] == '\'' -> {
                    val (str, endIdx) = parseQuotedString(input, i, '\'')
                    if (str != null) {
                        result.add(str)
                        i = endIdx
                    } else {
                        return emptyList() // Parse error
                    }
                }
                else -> return emptyList() // Unexpected character
            }
        }
        return result
    }

    private fun parseQuotedString(input: String, start: Int, quoteChar: Char): Pair<String?, Int> {
        if (start >= input.length || input[start] != quoteChar) return Pair(null, start)

        val sb = StringBuilder()
        var i = start + 1
        while (i < input.length) {
            when {
                input[i] == '\\' && i + 1 < input.length -> {
                    // Handle escape sequences
                    i++
                    when (input[i]) {
                        '"' -> sb.append('"')
                        '\'' -> sb.append('\'')
                        '\\' -> sb.append('\\')
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        else -> {
                            // For CSS Unicode escapes like \201C, just append as-is
                            sb.append(input[i])
                        }
                    }
                    i++
                }
                input[i] == quoteChar -> {
                    return Pair(sb.toString(), i + 1)
                }
                else -> {
                    sb.append(input[i])
                    i++
                }
            }
        }
        return Pair(null, i) // Unterminated string
    }
}
