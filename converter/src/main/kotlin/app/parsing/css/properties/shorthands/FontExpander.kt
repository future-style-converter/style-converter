package app.parsing.css.properties.shorthands

/**
 * Expands `font` shorthand into font-* longhands.
 * Syntax: [font-style] [font-variant] [font-weight] [font-stretch] font-size[/line-height] font-family
 * Also handles system fonts: caption, icon, menu, message-box, small-caption, status-bar
 */
object FontExpander : ShorthandExpander {
    private val systemFonts = setOf("caption", "icon", "menu", "message-box", "small-caption", "status-bar")
    private val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")
    private val styleKeywords = setOf("normal", "italic", "oblique")
    private val variantKeywords = setOf("normal", "small-caps")
    private val weightKeywords = setOf("normal", "bold", "lighter", "bolder", "100", "200", "300", "400", "500", "600", "700", "800", "900")
    private val stretchKeywords = setOf("normal", "ultra-condensed", "extra-condensed", "condensed", "semi-condensed", "semi-expanded", "expanded", "extra-expanded", "ultra-expanded")
    private val angleUnits = setOf("deg", "grad", "rad", "turn")

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        if (lower in globalKeywords) {
            return mapOf(
                "font-style" to trimmed,
                "font-variant" to trimmed,
                "font-weight" to trimmed,
                "font-stretch" to trimmed,
                "font-size" to trimmed,
                "line-height" to trimmed,
                "font-family" to trimmed
            )
        }

        // System fonts - pass through as raw
        if (lower in systemFonts) {
            return mapOf("font-family" to trimmed)
        }

        val result = mutableMapOf<String, String>()
        val tokens = tokenize(trimmed)

        var i = 0
        var foundSize = false

        // Parse optional values before size
        while (i < tokens.size && !foundSize) {
            val token = tokens[i]
            val tokenLower = token.lowercase()

            when {
                tokenLower == "oblique" -> {
                    // Check if next token is an angle (oblique <angle> syntax)
                    if (i + 1 < tokens.size && isAngleValue(tokens[i + 1])) {
                        result["font-style"] = "$token ${tokens[i + 1]}"
                        i += 2
                    } else {
                        result["font-style"] = token
                        i++
                    }
                }
                tokenLower in styleKeywords && !result.containsKey("font-style") -> {
                    result["font-style"] = token
                    i++
                }
                tokenLower in variantKeywords && !result.containsKey("font-variant") && tokenLower != "normal" -> {
                    result["font-variant"] = token
                    i++
                }
                tokenLower in weightKeywords && !result.containsKey("font-weight") -> {
                    result["font-weight"] = token
                    i++
                }
                tokenLower in stretchKeywords && !result.containsKey("font-stretch") && tokenLower != "normal" -> {
                    result["font-stretch"] = token
                    i++
                }
                isSizeValue(tokenLower) -> {
                    foundSize = true
                }
                else -> i++
            }
        }

        // Parse font-size and optional line-height
        if (i < tokens.size) {
            val sizeToken = tokens[i]
            if (sizeToken.contains("/")) {
                val parts = sizeToken.split("/", limit = 2)
                result["font-size"] = parts[0]
                result["line-height"] = parts[1]
            } else {
                result["font-size"] = sizeToken
            }
            i++
        }

        // Rest is font-family
        if (i < tokens.size) {
            result["font-family"] = tokens.subList(i, tokens.size).joinToString(" ")
        }

        return result
    }

    private fun isSizeValue(value: String): Boolean {
        val sizeKeywords = setOf("xx-small", "x-small", "small", "medium", "large", "x-large", "xx-large", "xxx-large", "smaller", "larger")
        val base = value.split("/")[0]
        // Exclude angle values (deg, grad, rad, turn) from being treated as size
        if (isAngleValue(base)) return false
        return base in sizeKeywords || base.matches(Regex("-?[\\d.]+[a-z%]*"))
    }

    private fun isAngleValue(value: String): Boolean {
        val lower = value.lowercase()
        return angleUnits.any { lower.endsWith(it) && lower.dropLast(it.length).toDoubleOrNull() != null }
    }

    private fun tokenize(value: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var quoteChar = ' '
        var depth = 0

        for (char in value) {
            when {
                (char == '"' || char == '\'') && depth == 0 -> {
                    if (!inQuotes) {
                        inQuotes = true
                        quoteChar = char
                    } else if (char == quoteChar) {
                        inQuotes = false
                    }
                    current.append(char)
                }
                char == '(' -> { depth++; current.append(char) }
                char == ')' -> { depth--; current.append(char) }
                char == ' ' && depth == 0 && !inQuotes -> {
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
