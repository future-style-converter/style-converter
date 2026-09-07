package app.parsing.css.properties.shorthands

// A6#14 — clone consolidation: this expander used to carry private
//   `splitPreservingFunctions` (1 copy), byte-identical to TokenizationUtils.tokenizeByWhitespace
//   — its 1 call site now calls the shared utility instead.
// One tokenizer rule, one body: a per-expander copy is exactly how the
// wave-47 border-shorthand defect (fix one, miss six) became possible.
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

/**
 * Expands the `flex` shorthand property.
 *
 * Syntax: flex: <grow> <shrink>? <basis>?
 *
 * Examples:
 * - "1" → flex-grow: 1
 * - "1 1" → flex-grow: 1, flex-shrink: 1
 * - "1 1 auto" → flex-grow: 1, flex-shrink: 1, flex-basis: auto
 * - "auto" → flex-grow: 1, flex-shrink: 1, flex-basis: auto
 * - "none" → flex-grow: 0, flex-shrink: 0, flex-basis: auto
 */
object FlexExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim().lowercase()

        return when (trimmed) {
            "auto" -> mapOf(
                "flex-grow" to "1",
                "flex-shrink" to "1",
                "flex-basis" to "auto"
            )
            "none" -> mapOf(
                "flex-grow" to "0",
                "flex-shrink" to "0",
                "flex-basis" to "auto"
            )
            "initial" -> mapOf(
                "flex-grow" to "0",
                "flex-shrink" to "1",
                "flex-basis" to "auto"
            )
            else -> {
                val parts = TokenizationUtils.tokenizeByWhitespace(value.trim())
                val result = mutableMapOf<String, String>()

                // css-flexbox-1 §7.1.1 is explicit about the omitted
                // components, and they are NOT the longhand initials:
                //   · flex-shrink, when omitted from the shorthand, is 1
                //   · flex-basis, when omitted from the shorthand, is 0%
                //     ("flex: <positive-number> — equivalent to
                //      flex: <positive-number> 1 0%")
                //   · flex-grow, when only a basis is given, is 1
                // The old code emitted ONLY the components that appeared, so
                // `flex: 1` left flex-basis at its cascade value (`auto`) —
                // content-sized distribution where the spec demands
                // zero-based distribution. That is a different layout, not a
                // rounding error.
                when (parts.size) {
                    1 -> {
                        val v = parts[0]
                        if (NUMBER.matches(v)) {
                            // flex: <number>  ≡  <number> 1 0%
                            result["flex-grow"] = v
                            result["flex-shrink"] = "1"
                            result["flex-basis"] = "0%"
                        } else {
                            // flex: <width>  ≡  1 1 <width>
                            result["flex-grow"] = "1"
                            result["flex-shrink"] = "1"
                            result["flex-basis"] = v
                        }
                    }
                    2 -> {
                        result["flex-grow"] = parts[0]
                        if (NUMBER.matches(parts[1])) {
                            // <number> <number>  ≡  grow shrink 0%
                            result["flex-shrink"] = parts[1]
                            result["flex-basis"] = "0%"
                        } else {
                            // <number> <width>  ≡  grow 1 basis
                            result["flex-shrink"] = "1"
                            result["flex-basis"] = parts[1]
                        }
                    }
                    3 -> {
                        result["flex-grow"] = parts[0]
                        result["flex-shrink"] = parts[1]
                        result["flex-basis"] = parts[2]
                    }
                }

                result
            }
        }
    }

    /**
     * A CSS <number>. The old pattern was `^\d+(\.\d+)?$`, which requires a
     * leading digit — so `flex: .5` failed the number test, was routed onto
     * flex-basis (where `.5` is not a valid width), and the declaration was
     * effectively dropped. `.5` is a valid <number> per css-values-4 §5.
     */
    private val NUMBER = """^(?:\d+\.?\d*|\.\d+)$""".toRegex()

}
