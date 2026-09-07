package app.parsing.css.properties.primitiveParsers

/**
 * Shared tokenization utilities for CSS value parsing.
 *
 * These functions handle splitting CSS values while respecting
 * parentheses depth (for functions like calc(), rgb(), etc.)
 */
object TokenizationUtils {
    // All members are `internal` (A6#14): this is a converter-module utility,
    // and narrowing the surface makes "is anything outside the parser reaching
    // into the tokenizer?" a compiler question rather than a grep. NOTE, so the
    // next reader is not misled: `internal` does NOT stop a file from declaring
    // its OWN private tokenizer — object members cannot conflict with a class
    // member elsewhere. The mechanical guard against that is
    // TokenizationCloneGuardTest, which fails when any converter source
    // re-declares a body byte-identical to one of these.

    /**
     * Split a CSS value by commas, respecting parentheses depth.
     *
     * Example: "rgb(255, 0, 0), blue" → ["rgb(255, 0, 0)", "blue"]
     *
     * @param value The CSS value to split
     * @return List of comma-separated parts
     */
    internal fun splitByComma(value: String): List<String> {
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
     * Split by top-level commas WITHOUT trimming, keeping every segment.
     *
     * The third comma policy in this file, and the reason all three live here:
     * [splitByComma] trims and drops blanks, [splitByTopLevelComma] trims and
     * keeps interior blanks, and this one trims nothing at all — the caller
     * owns the whitespace. `rgb( 1 , 2 , 3 )` must keep its spaces for the
     * component parsers, and `linear-gradient(red, , blue)` must keep the
     * empty slot so the malformed stop is rejected rather than silently
     * re-indexed.
     *
     * A6#14: byte-identical private copies lived in ColorParser and
     * AnimationTimelinePropertyParser.
     *
     * @param value The CSS value to split
     * @return Untrimmed segments; a trailing empty segment is dropped
     */
    internal fun splitByCommaRaw(value: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()

        for (char in value) {
            when (char) {
                '(' -> { depth++; current.append(char) }
                ')' -> { depth--; current.append(char) }
                ',' -> {
                    if (depth == 0) {
                        result.add(current.toString())
                        current = StringBuilder()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
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
    internal fun tokenizeByWhitespace(value: String): List<String> {
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
     * Tokenize a CSS value by ASCII SPACE only, respecting parentheses depth.
     *
     * Deliberately NOT [tokenizeByWhitespace]: this breaks on `' '` alone, so a
     * tab/newline stays glued to its neighbours. Eleven expanders carried
     * byte-identical private copies of this loop (A6#14) — Animation,
     * Transition, ListStyle, BorderBlock, BorderInline, LogicalBorder (×4),
     * Offset, MaskBorder — so both whitespace policies are pinned here, as two
     * named functions, instead of drifting apart across fourteen files.
     *
     * @param value The CSS value to tokenize
     * @return List of space-separated tokens (empty runs collapse)
     */
    internal fun tokenizeBySpace(value: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0

        for (char in value) {
            when {
                char == '(' -> {
                    depth++
                    current.append(char)
                }
                char == ')' -> {
                    depth--
                    current.append(char)
                }
                // Space only — a tab/newline is appended like any other char,
                // which is the behaviour every copied original had.
                char == ' ' && depth == 0 -> {
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
     * Split a CSS value by top-level commas, keeping INTERIOR empty segments.
     *
     * Deliberately NOT [splitByComma]: that drops every blank segment, which
     * silently re-indexes a positional list. The comma-separated shorthands
     * (`animation`, `transition`, `background`) need each layer to keep its
     * index, so `"a,,b"` stays 3 segments; a TRAILING blank is still dropped
     * (`"a,"` → `["a"]`) because the tail is only emitted when non-empty.
     * That reproduces the three private copies (Animation/Transition/
     * BackgroundExpander, A6#14) exactly — the fixture differential is
     * byte-identical.
     *
     * @param value The CSS value to split
     * @return List of trimmed segments, interior blanks included
     */
    internal fun splitByTopLevelComma(value: String): List<String> {
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0

        for (char in value) {
            when {
                char == '(' -> {
                    depth++
                    current.append(char)
                }
                char == ')' -> {
                    depth--
                    current.append(char)
                }
                char == ',' && depth == 0 -> {
                    // Unconditional at a separator: an interior empty layer
                    // keeps its slot, so layer indices stay aligned.
                    parts.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }

        // Tail only when non-empty: a trailing comma adds no phantom layer.
        if (current.isNotEmpty()) {
            parts.add(current.toString().trim())
        }

        return parts
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
    internal fun extractFunctionContent(value: String, funcName: String): String? {
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
    internal fun findMatchingCloseParen(value: String, openIndex: Int): Int {
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
