package app.parsing.css.properties.shorthands

/**
 * Expands the `font-variant` shorthand property.
 *
 * This is a complex shorthand that can set multiple font-variant-* properties.
 * For simplicity, we handle the most common cases.
 *
 * Examples:
 * - "normal" → font-variant-caps: normal, etc.
 * - "small-caps" → font-variant-caps: small-caps
 * - "oldstyle-nums" → font-variant-numeric: oldstyle-nums
 */
object FontVariantExpander : ShorthandExpander {
    private val capsValues = setOf("normal", "small-caps", "all-small-caps",
                                   "petite-caps", "all-petite-caps", "unicase", "titling-caps")
    private val numericValues = setOf("lining-nums", "oldstyle-nums", "proportional-nums",
                                      "tabular-nums", "diagonal-fractions", "stacked-fractions",
                                      "ordinal", "slashed-zero")
    private val ligaturesValues = setOf("common-ligatures", "no-common-ligatures",
                                        "discretionary-ligatures", "no-discretionary-ligatures",
                                        "historical-ligatures", "no-historical-ligatures",
                                        "contextual", "no-contextual")

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim().lowercase()
        val result = mutableMapOf<String, String>()

        if (trimmed == "normal" || trimmed == "none") {
            result["font-variant-caps"] = "normal"
            result["font-variant-numeric"] = "normal"
            result["font-variant-ligatures"] = "normal"
            return result
        }

        val parts = trimmed.split("""\s+""".toRegex())
        val numericParts = mutableListOf<String>()
        val ligatureParts = mutableListOf<String>()

        for (part in parts) {
            when {
                part in capsValues -> result["font-variant-caps"] = part
                part in numericValues -> numericParts.add(part)
                part in ligaturesValues -> ligatureParts.add(part)
            }
        }

        if (numericParts.isNotEmpty()) {
            result["font-variant-numeric"] = numericParts.joinToString(" ")
        }
        if (ligatureParts.isNotEmpty()) {
            result["font-variant-ligatures"] = ligatureParts.joinToString(" ")
        }

        return result
    }
}
