package app.parsing.css.properties.longhands.typography

import app.irmodels.*
import app.irmodels.properties.typography.LineClampProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * `line-clamp` — css-overflow-4 §5.1:
 *
 *     line-clamp: none | <integer [1,∞]> | auto
 *
 * wave-37 lane W2: the `auto` keyword was missing, so every
 * `line-clamp: auto` declaration in the corpus fell through this parser's
 * `return null` into GenericProperty (`_unmapped:true`) and reached no
 * runtime at all — measured on 45 bucket-A `line-clamp-auto-*` tests, whose
 * captures rendered the FULL unclamped text next to a ref clamped at the
 * height constraint. `auto` means "clamp at the element's own block-size
 * constraint" rather than at a fixed line count, so it needs its own IR
 * variant: the line count is not known until a runtime resolves the
 * constraint (see the web LineClampExtractor's lh/px resolution).
 */
object LineClampPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? = parseClamp(value, allowAuto = true)
}

/**
 * `-webkit-line-clamp` — the legacy vendor alias, whose grammar is the
 * PRE-`auto` one (css-overflow-4 Appendix A: `none | <integer [1,∞]>`).
 * Kept as a separate object rather than aliasing LineClampPropertyParser so
 * `-webkit-line-clamp: auto` stays invalid — exactly as browsers treat it —
 * instead of silently gaining a keyword the vendor property never had.
 */
object WebkitLineClampPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? = parseClamp(value, allowAuto = false)
}

/** Shared grammar body; `allowAuto` is the only difference between the two. */
private fun parseClamp(value: String, allowAuto: Boolean): IRProperty? {
    val trimmed = value.trim().lowercase()                                   // CSS keywords are ASCII case-insensitive

    if (trimmed == "none") {
        return LineClampProperty(LineClampProperty.LineClamp.None())         // clamp off
    }

    // `auto` — standard property only. Emitted as its own sealed variant so a
    // runtime can tell "clamp at my height constraint" apart from "clamp at N".
    if (allowAuto && trimmed == "auto") {
        return LineClampProperty(LineClampProperty.LineClamp.Auto())
    }

    val lines = trimmed.toIntOrNull() ?: return null                          // anything else is invalid → drop
    return LineClampProperty(LineClampProperty.LineClamp.Lines(IRNumber(lines.toDouble())))
}
