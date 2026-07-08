package app.parsing.css.properties.primitiveParsers

/**
 * Shared tokenization utilities for CSS value parsing.
 *
 * These functions handle splitting CSS values while respecting
 * parentheses depth (for functions like calc(), rgb(), etc.)
 */
object TokenizationUtils {

    /**
     * Split a CSS value by commas, respecting parentheses depth.
     *
     * Example: "rgb(255, 0, 0), blue" → ["rgb(255, 0, 0)", "blue"]
     *
     * @param value The CSS value to split
     * @return List of comma-separated parts
     */
    fun splitByComma(value: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0

        for (char in value) {
            when (char) {
                '(' -> {
                    depth++
                    current.append(char)
                }
                ')' -> {
                    depth--
                    current.append(char)
                }
                ',' -> {
                    if (depth == 0) {
                        val trimmed = current.toString().trim()
                        if (trimmed.isNotEmpty()) {
                            result.add(trimmed)
                        }
                        current = StringBuilder()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }

        val remaining = current.toString().trim()
        if (remaining.isNotEmpty()) {
            result.add(remaining)
        }

        return result
    }

    /**
     * Tokenize a CSS value by whitespace, respecting parentheses depth.
     *
     * Example: "5px rgba(0, 0, 0, 0.5) inset" → ["5px", "rgba(0, 0, 0, 0.5)", "inset"]
     *
     * @param value The CSS value to tokenize
     * @return List of whitespace-separated tokens
     */
    fun tokenizeByWhitespace(value: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var parenDepth = 0

        for (char in value) {
            when {
                char == '(' -> {
                    parenDepth++
                    current.append(char)
                }
                char == ')' -> {
                    parenDepth--
                    current.append(char)
                }
                char.isWhitespace() && parenDepth == 0 -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }

        return tokens
    }

    /**
     * Extract content from inside a CSS function.
     *
     * Example: extractFunctionContent("linear-gradient(to right, red, blue)", "linear-gradient")
     *          → "to right, red, blue"
     *
     * @param value The full function call string
     * @param funcName The function name (without parentheses)
     * @return The content inside the parentheses, or null if not matching
     */
    fun extractFunctionContent(value: String, funcName: String): String? {
        val prefix = "$funcName("
        if (!value.startsWith(prefix, ignoreCase = true) || !value.endsWith(")")) {
            return null
        }
        return value.substring(prefix.length, value.length - 1)
    }

    /**
     * Find the index of the matching closing parenthesis.
     *
     * @param value The string to search
     * @param openIndex The index of the opening parenthesis
     * @return The index of the matching closing parenthesis, or -1 if not found
     */
    fun findMatchingCloseParen(value: String, openIndex: Int): Int {
        if (openIndex < 0 || openIndex >= value.length || value[openIndex] != '(') {
            return -1
        }

        var depth = 1
        var i = openIndex + 1

        while (i < value.length && depth > 0) {
            when (value[i]) {
                '(' -> depth++
                ')' -> depth--
            }
            i++
        }

        return if (depth == 0) i - 1 else -1
    }
}
