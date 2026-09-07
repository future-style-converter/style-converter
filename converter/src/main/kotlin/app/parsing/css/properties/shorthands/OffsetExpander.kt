package app.parsing.css.properties.shorthands

// A6#14 — clone consolidation: this expander used to carry private
//   `tokenize` (1 copy), byte-identical to TokenizationUtils.tokenizeBySpace
//   — its 1 call site now calls the shared utility instead.
// One tokenizer rule, one body: a per-expander copy is exactly how the
// wave-47 border-shorthand defect (fix one, miss six) became possible.
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

/**
 * Expands `offset` shorthand into offset-* longhands.
 * Syntax: [offset-position] [offset-path] [offset-distance] [offset-rotate] / [offset-anchor]
 */
object OffsetExpander : ShorthandExpander {
    private val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed.lowercase() in globalKeywords) {
            return mapOf(
                "offset-position" to trimmed,
                "offset-path" to trimmed,
                "offset-distance" to trimmed,
                "offset-rotate" to trimmed,
                "offset-anchor" to trimmed
            )
        }

        val result = mutableMapOf<String, String>()

        // Split by / for anchor
        val slashParts = trimmed.split("/", limit = 2)
        if (slashParts.size == 2) {
            result["offset-anchor"] = slashParts[1].trim()
        }

        val mainPart = slashParts[0].trim()
        val tokens = TokenizationUtils.tokenizeBySpace(mainPart)

        for (token in tokens) {
            val lower = token.lowercase()
            when {
                // offset-path: path(), url(), ray(), basic shapes
                lower.startsWith("path(") || lower.startsWith("url(") ||
                lower.startsWith("ray(") || lower.startsWith("circle(") ||
                lower.startsWith("ellipse(") || lower.startsWith("polygon(") ||
                lower.startsWith("inset(") -> {
                    result["offset-path"] = token
                }
                // offset-rotate: auto, reverse, angle
                lower == "auto" || lower == "reverse" || lower.startsWith("auto ") ||
                lower.endsWith("deg") || lower.endsWith("rad") || lower.endsWith("turn") || lower.endsWith("grad") -> {
                    result["offset-rotate"] = token
                }
                // offset-distance: length or percentage
                lower.matches(Regex("-?[\\d.]+[a-z%]+")) -> {
                    result["offset-distance"] = token
                }
                // offset-position: keywords or positions
                lower in setOf("auto", "top", "bottom", "left", "right", "center") -> {
                    result["offset-position"] = token
                }
                else -> {
                    // Default to offset-path for unrecognized
                    if (!result.containsKey("offset-path")) {
                        result["offset-path"] = token
                    }
                }
            }
        }

        return result
    }

}
