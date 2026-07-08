package app.parsing.css.properties.longhands.lists

import app.irmodels.IRProperty
import app.irmodels.properties.lists.ListStyleTypeProperty
import app.parsing.css.properties.longhands.PropertyParser

object ListStyleTypePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val type: ListStyleTypeProperty.ListStyleType = when {
            trimmed == "none" -> ListStyleTypeProperty.ListStyleType.None()
            trimmed == "disc" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.DISC)
            trimmed == "circle" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.CIRCLE)
            trimmed == "square" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.SQUARE)
            trimmed == "decimal" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.DECIMAL)
            trimmed == "decimal-leading-zero" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.DECIMAL_LEADING_ZERO)
            trimmed == "lower-roman" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.LOWER_ROMAN)
            trimmed == "upper-roman" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.UPPER_ROMAN)
            trimmed == "lower-greek" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.LOWER_GREEK)
            trimmed == "lower-latin" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.LOWER_LATIN)
            trimmed == "upper-latin" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.UPPER_LATIN)
            trimmed == "lower-alpha" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.LOWER_ALPHA)
            trimmed == "upper-alpha" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.UPPER_ALPHA)
            trimmed == "armenian" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.ARMENIAN)
            trimmed == "georgian" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.GEORGIAN)
            trimmed == "hebrew" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.HEBREW)
            trimmed == "hiragana" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.HIRAGANA)
            trimmed == "katakana" -> ListStyleTypeProperty.ListStyleType.Keyword(ListStyleTypeProperty.ListMarkerKeyword.KATAKANA)
            trimmed.startsWith("symbols(") -> parseSymbols(value) ?: return null
            trimmed.startsWith("\"") && trimmed.endsWith("\"") -> {
                ListStyleTypeProperty.ListStyleType.CustomString(trimmed.removeSurrounding("\""))
            }
            trimmed.startsWith("'") && trimmed.endsWith("'") -> {
                ListStyleTypeProperty.ListStyleType.CustomString(trimmed.removeSurrounding("'"))
            }
            // Additional standard keywords
            trimmed == "disclosure-open" -> ListStyleTypeProperty.ListStyleType.CustomString("disclosure-open")
            trimmed == "disclosure-closed" -> ListStyleTypeProperty.ListStyleType.CustomString("disclosure-closed")
            // International numbering systems (CSS Counter Styles Level 3)
            trimmed in setOf(
                "arabic-indic", "bengali", "cambodian", "cjk-decimal", "devanagari",
                "ethiopic-numeric", "gujarati", "gurmukhi", "kannada", "khmer",
                "lao", "malayalam", "mongolian", "myanmar", "oriya", "persian",
                "tamil", "telugu", "thai", "tibetan", "japanese-formal",
                "japanese-informal", "korean-hangul-formal", "korean-hanja-formal",
                "korean-hanja-informal", "simp-chinese-formal", "simp-chinese-informal",
                "trad-chinese-formal", "trad-chinese-informal"
            ) -> ListStyleTypeProperty.ListStyleType.CustomString(trimmed)
            // Any other identifier is a custom counter style
            trimmed.matches(Regex("^[a-z_-][a-z0-9_-]*$")) -> {
                ListStyleTypeProperty.ListStyleType.CustomString(trimmed)
            }
            else -> return null
        }

        return ListStyleTypeProperty(type)
    }

    /**
     * Parse symbols() function.
     * Syntax: symbols([cyclic|numeric|alphabetic|symbolic|fixed]? <string>+)
     */
    private fun parseSymbols(value: String): ListStyleTypeProperty.ListStyleType.Symbols? {
        val lower = value.trim().lowercase()
        if (!lower.startsWith("symbols(") || !lower.endsWith(")")) return null

        val content = value.substring(8, value.length - 1).trim()
        if (content.isEmpty()) return null

        val tokens = tokenize(content)
        if (tokens.isEmpty()) return null

        var symbolsType = ListStyleTypeProperty.SymbolsType.SYMBOLIC // default
        var symbolStart = 0

        // Check if first token is a type keyword
        val firstToken = tokens[0].lowercase()
        when (firstToken) {
            "cyclic" -> { symbolsType = ListStyleTypeProperty.SymbolsType.CYCLIC; symbolStart = 1 }
            "numeric" -> { symbolsType = ListStyleTypeProperty.SymbolsType.NUMERIC; symbolStart = 1 }
            "alphabetic" -> { symbolsType = ListStyleTypeProperty.SymbolsType.ALPHABETIC; symbolStart = 1 }
            "symbolic" -> { symbolsType = ListStyleTypeProperty.SymbolsType.SYMBOLIC; symbolStart = 1 }
            "fixed" -> { symbolsType = ListStyleTypeProperty.SymbolsType.FIXED; symbolStart = 1 }
        }

        // Extract symbols (quoted strings)
        val symbols = tokens.drop(symbolStart).map { token ->
            token.removeSurrounding("\"").removeSurrounding("'")
        }

        if (symbols.isEmpty()) return null

        return ListStyleTypeProperty.ListStyleType.Symbols(symbolsType, symbols)
    }

    /**
     * Tokenize by whitespace, keeping quoted strings together.
     */
    private fun tokenize(value: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var inQuote: Char? = null

        for (char in value) {
            when {
                char == '"' || char == '\'' -> {
                    if (inQuote == char) {
                        current.append(char)
                        tokens.add(current.toString())
                        current = StringBuilder()
                        inQuote = null
                    } else if (inQuote == null) {
                        inQuote = char
                        current.append(char)
                    } else {
                        current.append(char)
                    }
                }
                char.isWhitespace() && inQuote == null -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }
}
