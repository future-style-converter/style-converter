package app.parsing.css.properties.primitiveParsers

/**
 * Parses and validates CSS keyword values.
 *
 * Used for properties that accept specific keyword values like:
 * - "auto", "none", "normal"
 * - "bold", "italic", "underline"
 * - "center", "left", "right"
 *
 * Examples:
 * - parse("auto", setOf("auto", "none")) → "auto"
 * - parse("bold", setOf("normal", "bold")) → "bold"
 * - parse("invalid", setOf("auto", "none")) → null
 */
object KeywordParser {

    /**
     * Parse a keyword value, validating against allowed keywords.
     *
     * @param value The keyword string to validate
     * @param validKeywords Set of allowed keywords (case-insensitive)
     * @return The validated keyword in lowercase, or null if invalid
     */
    fun parse(value: String, validKeywords: Set<String>): String? {
        val trimmed = value.trim().lowercase()
        return if (trimmed in validKeywords) trimmed else null
    }

    /**
     * Parse a keyword without validation.
     * Useful when you just need to extract a keyword value.
     *
     * @param value The keyword string
     * @return The keyword in lowercase
     */
    fun parseAny(value: String): String {
        return value.trim().lowercase()
    }

    /**
     * Check if a value matches one of the valid keywords.
     *
     * @param value The value to check
     * @param validKeywords Set of allowed keywords
     * @return true if the value is a valid keyword
     */
    fun isValid(value: String, validKeywords: Set<String>): Boolean {
        return value.trim().lowercase() in validKeywords
    }
}
