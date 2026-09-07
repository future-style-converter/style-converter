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
    // css-fonts-4 §6.11: "The font-variant property is a shorthand for all
    // font-variant subproperties: font-variant-ligatures, font-variant-position,
    // font-variant-caps, font-variant-numeric, font-variant-alternates,
    // font-variant-east-asian, font-variant-emoji." Listed in that spec order
    // so the CSS-wide keyword branch below emits them deterministically.
    private val subProperties = listOf(
        "font-variant-ligatures", "font-variant-position", "font-variant-caps",
        "font-variant-numeric", "font-variant-alternates", "font-variant-east-asian",
        "font-variant-emoji"
    )
    // The CSS-wide keywords, as the other expanders in this package spell the
    // set: `initial | inherit | unset` are css-values-4 §4.1.1, `revert` is
    // css-cascade-4 §7.3.4 and `revert-layer` css-cascade-5 §7.3.5 (both
    // "Explicit Defaulting" subsections, i.e. keywords other specs add to the
    // §4.1.1 three, exactly as §4.1.1 anticipates).
    private val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")
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

        // css-cascade-4 §3: a shorthand set to a CSS-wide keyword "sets all
        // of its sub-properties to that keyword". Before the retrospective
        // (finding A8#0) a keyword fell through the token loop below, matched
        // nothing and returned an EMPTY map — `font-variant: inherit` was
        // dropped silently, and `font: inherit` (which delegates here) had to
        // forward the shorthand name instead. The keyword rides as authored
        // (trimmed, original case) like FontExpander's forwarding does.
        if (trimmed in globalKeywords) {
            val asAuthored = value.trim()
            return subProperties.associateWithTo(LinkedHashMap()) { asAuthored }
        }

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
