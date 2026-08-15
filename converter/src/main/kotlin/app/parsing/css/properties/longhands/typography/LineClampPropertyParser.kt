package app.parsing.css.properties.longhands.typography

import app.irmodels.*
import app.irmodels.properties.typography.LineClampProperty
import app.irmodels.properties.typography.LineClampProperty.BlockEllipsis
import app.irmodels.properties.typography.LineClampProperty.LineClamp
import app.parsing.css.properties.longhands.PropertyParser

/**
 * `line-clamp` — css-overflow-4 §5.1:
 *
 *     line-clamp: none | [ <integer [1,∞]> || <'block-ellipsis'> ] -webkit-legacy?
 *     <'block-ellipsis'> = no-ellipsis | auto | ellipsis | <string>
 *
 * The grammar (both orders of the `||`, each component at most once, and the
 * invalid combinations `none 2` / `3 none` / `none no-ellipsis` / `0` / `-5`)
 * is pinned verbatim by WPT css/css-overflow/parsing/line-clamp-valid.html
 * and line-clamp-invalid.html (mirrored under tools/wpt/).
 *
 * wave-37 lane W2 added the `auto` keyword: without it every `line-clamp:
 * auto` fell through to GenericProperty (`_unmapped:true`) and reached no
 * runtime — measured on 45 bucket-A tests rendering the FULL unclamped text.
 *
 * wave-42 lane W7 adds the two-value grammar. `line-clamp: 4 no-ellipsis`
 * (WPT block-ellipsis-023) and `line-clamp: 4 ""` (block-ellipsis-024) hit
 * the old single-token `toIntOrNull() ?: return null` and dropped the WHOLE
 * declaration: the wave-41 per-test IR carries NO LineClamp, so all three
 * runtimes rendered the 6-line text unclamped against a ref clamped at 4
 * lines (Android SSIM 0.9422, iOS 0.9199 — both failing). The marker
 * component now rides on the Lines variant as an ADDITIVE optional field;
 * single-value emissions are byte-identical to before.
 */
object LineClampPropertyParser : PropertyParser {
    // Standard property: the full css-overflow-4 grammar (header above).
    override fun parse(value: String): IRProperty? {
        // Tokenize on whitespace but keep quoted <string> tokens whole —
        // `4 " etc., etc. "` is TWO component values, not five tokens.
        val tokens = tokenize(value.trim()) ?: return null                // unterminated <string> → invalid declaration
        if (tokens.isEmpty()) return null                                 // empty value → invalid declaration
        // Optional trailing `-webkit-legacy` (css-overflow-4 §5.1) opts into
        // the legacy `-webkit-box` layout behavior; it does not change WHAT
        // is clamped, so it is accepted and dropped from the component fold.
        val body = if (tokens.last().equals("-webkit-legacy", ignoreCase = true)) tokens.dropLast(1) else tokens
        // `-webkit-legacy` alone is invalid (WPT line-clamp-invalid.html).
        if (body.isEmpty()) return null
        // `none` is its own single-value production — it cannot combine with
        // a count or marker (WPT invalid: `none 2`, `3 none`, `none no-ellipsis`).
        if (body.size == 1 && body[0].equals("none", ignoreCase = true)) {
            return LineClampProperty(LineClamp.None())                    // clamp off
        }
        // `[ <integer [1,∞]> || <'block-ellipsis'> ]`: one or both, in any
        // order, each slot filled at most once. Fold the components.
        var count: Int? = null                                            // the <integer> slot, if present
        var marker: BlockEllipsis? = null                                 // the marker slot's IR value (null = default marker)
        var sawMarker = false                                             // distinguishes explicit `ellipsis` (null IR) from an absent slot
        for (token in body) {
            val lower = token.lowercase()                                 // CSS keywords are ASCII case-insensitive
            when {
                // <string> — a custom marker: `""` (block-ellipsis-024) or
                // `" etc., etc. "`. Quotes stripped, inner text verbatim.
                token.length >= 2 && (token[0] == '"' || token[0] == '\'') -> {
                    if (sawMarker) return null                            // `||` fills the marker slot at most once
                    marker = BlockEllipsis.Text(token.substring(1, token.length - 1))
                    sawMarker = true                                      // slot now filled
                }
                // `no-ellipsis` — clamp but never draw a marker; content must
                // not be displaced for one (block-ellipsis-023's assert).
                lower == "no-ellipsis" -> {
                    if (sawMarker) return null                            // duplicate marker slot → invalid
                    marker = BlockEllipsis.NoEllipsis()                   // explicit no-marker keyword
                    sawMarker = true                                      // slot now filled
                }
                // `auto` — UA-chosen marker; ALSO the wave-37 bare spelling of
                // "clamp at the block-size constraint" when no count follows
                // (resolved after the fold, where both readings converge).
                lower == "auto" -> {
                    if (sawMarker) return null                            // duplicate marker slot → invalid
                    marker = BlockEllipsis.Auto()                         // UA marker choice
                    sawMarker = true                                      // slot now filled
                }
                // `ellipsis` — the marker longhand's INITIAL value: WPT pins
                // `8 ellipsis` ≡ `8` (line-clamp-valid.html serialization),
                // so it maps to a null field and the wire stays identical to
                // the bare-integer emission.
                lower == "ellipsis" -> {
                    if (sawMarker) return null                            // duplicate marker slot → invalid
                    marker = null                                         // initial value → field omitted
                    sawMarker = true                                      // slot still counts as filled
                }
                // <integer [1,∞]> — the fixed line count. Zero, negative and
                // fractional values are grammar violations that drop the whole
                // declaration (WPT invalid: `0`, `-5`; pinned here: `2.5`).
                else -> {
                    val n = lower.toIntOrNull() ?: return null            // non-component token → invalid
                    if (n < 1 || count != null) return null               // outside [1,∞], or integer slot already filled
                    count = n                                             // record the count
                }
            }
        }
        return when {
            // Count present → fixed-count clamp; the marker choice (or its
            // absence) rides along as the additive `ellipsis` field.
            count != null -> LineClampProperty(LineClamp.Lines(IRNumber(count.toDouble()), marker))
            // Marker alone → no fixed count, so the clamp point is the box's
            // own block-size constraint: exactly the Auto variant wave-37
            // introduced for bare `auto`. WPT pins the canonicalization
            // (`line-clamp: ellipsis` serializes as `auto`). The marker CHOICE
            // is dropped here knowingly: the Auto rendering path on all three
            // runtimes is geometry-only and never draws a marker anyway (see
            // the web LineClampApplier's measured no-trio note).
            sawMarker -> LineClampProperty(LineClamp.Auto())
            else -> null                                                  // unreachable: body was non-empty
        }
    }

    // Whitespace tokenizer that keeps CSS <string> tokens (either quote kind)
    // intact INCLUDING their delimiters, so the fold can tell `"auto"` (a
    // marker string) from `auto` (the keyword). Returns null on an
    // unterminated string — the declaration is invalid CSS. Escape sequences
    // inside strings pass through verbatim: no corpus marker uses one, and
    // guessing at un-escaping would corrupt the ones that might.
    private fun tokenize(value: String): List<String>? {
        val tokens = mutableListOf<String>()                              // accumulator
        var i = 0                                                         // scan cursor
        while (i < value.length) {
            val c = value[i]                                              // char under the cursor
            when {
                c.isWhitespace() -> i++                                   // separator → skip
                c == '"' || c == '\'' -> {                                // <string> opens
                    val end = value.indexOf(c, i + 1)                     // its matching close quote
                    if (end < 0) return null                              // unterminated → invalid
                    tokens += value.substring(i, end + 1)                 // keep quotes: marks token AS a string
                    i = end + 1                                           // resume after the close quote
                }
                else -> {                                                 // bare token (keyword / integer)
                    var j = i                                             // token-end scan
                    while (j < value.length && !value[j].isWhitespace()) j++  // run to the next separator
                    tokens += value.substring(i, j)                       // capture the token
                    i = j                                                 // resume after it
                }
            }
        }
        return tokens                                                     // may be empty — the caller rejects that
    }
}

/**
 * `-webkit-line-clamp` — the legacy vendor alias, whose grammar is frozen at
 * `none | <integer>` (css-overflow-4 Appendix A): no `auto`, no marker
 * component, no `-webkit-legacy` token — WPT webkit-line-clamp-invalid.html
 * pins `auto`, `1 no-ellipsis`, `1 "~"` and `1 -webkit-legacy` all invalid.
 * Kept as a separate object with its own single-token body (byte-identical
 * to the pre-wave-42 behavior) so the vendor property never silently gains
 * grammar the browsers' frozen alias does not have.
 */
object WebkitLineClampPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()                            // keywords are ASCII case-insensitive
        if (trimmed == "none") {
            return LineClampProperty(LineClamp.None())                    // clamp off
        }
        val lines = trimmed.toIntOrNull() ?: return null                  // single integer, else drop the declaration
        return LineClampProperty(LineClamp.Lines(IRNumber(lines.toDouble())))
    }
}
