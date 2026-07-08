package app.parsing.css.properties.primitiveParsers

/**
 * CSS Global Keywords utility.
 *
 * Global keywords can be used with any CSS property and have special
 * inheritance/cascade behavior:
 *
 * - inherit: Use the computed value from the parent element
 * - initial: Use the property's initial (default) value
 * - unset: Acts as inherit for inherited properties, initial for others
 * - revert: Reverts to the value from the previous cascade origin
 * - revert-layer: Reverts to the value from the previous cascade layer
 *
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_Values_and_Units#css-wide_values
 */
object GlobalKeywords {

    /**
     * The complete set of CSS global keywords.
     */
    val ALL: Set<String> = setOf(
        "inherit",
        "initial",
        "unset",
        "revert",
        "revert-layer"
    )

    /**
     * Check if a value is a CSS global keyword.
     *
     * @param value The CSS value to check (case-insensitive)
     * @return true if value is a global keyword
     */
    fun isGlobalKeyword(value: String): Boolean {
        return value.lowercase().trim() in ALL
    }

    /**
     * Get the normalized global keyword if value matches.
     *
     * @param value The CSS value to check (case-insensitive)
     * @return The lowercase keyword or null if not a global keyword
     */
    fun normalize(value: String): String? {
        val lower = value.lowercase().trim()
        return if (lower in ALL) lower else null
    }

    // Individual keyword constants for direct comparison
    const val INHERIT = "inherit"
    const val INITIAL = "initial"
    const val UNSET = "unset"
    const val REVERT = "revert"
    const val REVERT_LAYER = "revert-layer"
}
