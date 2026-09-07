package com.styleconverter.runtime.content

// PseudoStyledDeclarations — wave-43 lane V7: the RAW-string → TYPED
// IRProperty conversion for the styling declarations a `pseudos` bucket
// carries. Wave-42's bridge rendered every baked pseudo run in the
// document-default face/ink (PseudoTextMetrics' three bottom-outs) and
// named the bucket's own styling declarations as unsupported; this file
// closes the top of that gap — MEASURED against wave42-final per-test-ir:
// `color: red` ×7 (css-display/display-contents-dynamic-before-after-001)
// and `font-size: 3em` ×1 (css-contain/contain-content-011 — the ref's
// 48px "17" vs Android's 16sp "25", android-ref 0.9275 FAIL).
//
// WHY A CONVERSION AND NOT A PARSER: the bucket's `properties` map is raw
// CSS strings (spec 01-envelope.md forwards the extractor payload
// verbatim), but the pseudo render site ALREADY consumes typed IRProperty
// lists (ContentApplier.PseudoElement → TextStyleApplier.extractTextStyle),
// so the honest bridge is raw string → the EXACT typed wire shapes those
// extractors document, letting the one existing text pipeline do all
// resolution (em against the threaded inherited base, family walk, …).
// font-weight is deliberately absent: the wave-43 census counts ZERO
// before/after buckets declaring it — wire it when the corpus carries one.

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object PseudoStyledDeclarations {

    /** Outcome of converting ONE raw styling declaration. */
    sealed interface Conversion {
        /** Converted — feed [property] to the typed text pipeline. */
        data class Typed(val property: IRProperty) : Conversion
        /**
         * Consumed as a spec-level no-op: the declaration asks for exactly
         * what the run already does (e.g. `font-size: inherit` — the pseudo
         * has no own-size channel, so it already renders the inherited
         * default). NOT a gap, so the caller must not track it.
         */
        data object Inert : Conversion
        /** Beyond this conversion — the caller names it via the tracker. */
        data object Unsupported : Conversion
    }

    /**
     * css-cascade-5 §7.3 CSS-wide keywords. For the three properties this
     * file owns they all collapse to "no own declaration": color and
     * font-size/font-family are INHERITED properties, so inherit / unset /
     * revert(-layer) resolve to the inherited value and initial to the UA
     * default — and an undeclared pseudo run already renders exactly those
     * (the bucket channel has no own-value otherwise). Hence [Conversion.Inert].
     */
    private val CSS_WIDE = setOf("inherit", "initial", "unset", "revert", "revert-layer")

    /**
     * Convert one raw declaration to its typed IR form, or null when
     * [prop] is not a styling declaration this file owns (the caller's
     * walk keeps its own handling for content/display/counter-*).
     *
     * @param prop the declaration name, already trimmed + lowercased by
     *   the caller's walk (css-syntax-3 §5: names are ASCII-case-insensitive).
     * @param value the raw author string, verbatim from the wire.
     */
    fun convert(prop: String, value: String): Conversion? = when (prop) {
        // css-color-4 §3.2: `color` sets the text ink of the run.
        "color" -> colorConversion(value)
        // css-fonts-4 §2.5: `font-size` sets the run's own glyph size.
        "font-size" -> fontSizeConversion(value)
        // css-fonts-4 §2.1: `font-family` sets the run's face list.
        "font-family" -> fontFamilyConversion(value)
        // Anything else is outside this file's ownership.
        else -> null
    }

    /**
     * `color: <raw>` → the typed `{"srgb":{r,g,b,a}}` wire shape
     * [ValueExtractors.extractColor] documents (0–1 floats, spec
     * 02-values.md color normalization).
     */
    private fun colorConversion(value: String): Conversion {
        // Author keywords are case-insensitive; classify on a trimmed copy.
        val t = value.trim().lowercase()
        // CSS-wide keywords collapse to the run's existing default ink.
        if (t in CSS_WIDE) return Conversion.Inert
        // The SHARED literal parser (#hex + the named table) the runtime
        // already uses for color-mix endpoints — one color vocabulary.
        val c = ValueExtractors.parseCssColorLiteral(value) ?: return Conversion.Unsupported
        // Emit the exact IRColor srgb block the typed extractor reads,
        // plus `original` so the payload stays debuggable like the wire's.
        return Conversion.Typed(IRProperty(
            type = "Color",
            data = buildJsonObject {
                // Pre-resolved sRGB — the static-color path of extractColor.
                put("srgb", buildJsonObject {
                    put("r", c.red.toDouble())
                    put("g", c.green.toDouble())
                    put("b", c.blue.toDouble())
                    put("a", c.alpha.toDouble())
                })
                // The author string, mirrored like converter output does.
                put("original", value.trim())
            }
        ))
    }

    /**
     * `font-size: <raw>` → the typed shapes TextStyleApplier's
     * extractFontSize documents: absolute units resolve to `{"px": N}`
     * here (spec 02: absolute lengths normalize to px at 96dpi), while
     * em / rem / % stay SYMBOLIC in the `original` wrapper so the render
     * site resolves them against the threaded INHERITED base
     * (css-values-4 §6.1.1: font-size's own em resolves against the
     * inherited size — a base only the render site knows).
     */
    private fun fontSizeConversion(value: String): Conversion {
        // Keywords are case-insensitive; numbers unaffected by lowercase.
        val t = value.trim().lowercase()
        // CSS-wide keywords collapse to the run's existing inherited size.
        if (t in CSS_WIDE) return Conversion.Inert
        // Split "3em" → number prefix + unit suffix (css-syntax-3 §4.3.11:
        // a dimension token is a number immediately followed by its unit).
        val num = t.takeWhile { it.isDigit() || it == '.' || it == '-' || it == '+' }
        val unit = t.substring(num.length).trim()
        // A non-numeric prefix (absolute keywords, calc(), var()) is
        // beyond this conversion — named by the caller, never guessed.
        val v = num.toDoubleOrNull() ?: return Conversion.Unsupported
        // Negative font sizes are invalid per css-fonts-4 §2.5 — refuse.
        if (v < 0) return Conversion.Unsupported
        // Absolute units → canonical px (spec 02-values.md table).
        val px: Double? = when (unit) {
            // A bare number is not valid CSS font-size; px is canonical.
            "px" -> v
            "pt" -> v * 4.0 / 3.0        // 1pt = 1/72in, 96dpi → 4/3 px.
            "pc" -> v * 16.0             // 1pc = 12pt = 16px.
            "in" -> v * 96.0             // 96 px per CSS inch.
            "cm" -> v * 96.0 / 2.54      // metric via the CSS inch.
            "mm" -> v * 96.0 / 25.4
            "q" -> v * 96.0 / 25.4 / 4.0 // 1Q = 1/4 mm.
            else -> null                 // relative/unknown → symbolic path.
        }
        return when {
            // Absolute: the plain `{"px": N}` blob extractDp unwraps.
            px != null -> Conversion.Typed(IRProperty(
                type = "FontSize",
                data = buildJsonObject { put("px", px) }
            ))
            // em/rem: the LIVE relative-length wire shape (extractFontSize's
            // "length" branch) — resolved at render time against the
            // inherited base (em) or the 16px root default (rem).
            unit == "em" || unit == "rem" -> Conversion.Typed(IRProperty(
                type = "FontSize",
                data = buildJsonObject {
                    put("original", buildJsonObject {
                        put("type", "length")
                        put("original", buildJsonObject {
                            put("v", v)
                            put("u", unit.uppercase())
                        })
                    })
                }
            ))
            // %: the LIVE percentage wire shape — same inherited base as em
            // (css-fonts-4 §2.5), resolved by the same render-site branch.
            unit == "%" -> Conversion.Typed(IRProperty(
                type = "FontSize",
                data = buildJsonObject {
                    put("original", buildJsonObject {
                        put("type", "percentage")
                        put("value", v)
                    })
                }
            ))
            // vw/ch/… have no static base at this seam — named, not forged.
            else -> Conversion.Unsupported
        }
    }

    /**
     * `font-family: <raw>` → the canonical JsonArray-of-names wire shape
     * [com.styleconverter.runtime.typography.CssFontFamilyResolver] walks
     * (css-fonts-4 §5.2 prioritised fallback list). Per-name quote
     * stripping is the resolver's job — names pass through verbatim.
     */
    private fun fontFamilyConversion(value: String): Conversion {
        // Keyword check on the whole value: `font-family: inherit` is a
        // CSS-wide keyword, not a family named "inherit" (css-fonts-4 §2.1).
        val t = value.trim().lowercase()
        // The wave-43 census's entire font-family population is
        // `inherit` (×27, all ::marker) — the run's default face already
        // IS the inherited/document face at this seam, so: no-op.
        if (t in CSS_WIDE) return Conversion.Inert
        // Top-level comma split — family names cannot contain unquoted
        // commas (css-fonts-4 §2.1 grammar); a quoted name containing a
        // comma would mis-split, accepted as a documented simplification
        // (no corpus payload carries one).
        val names = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        // An empty list is a malformed declaration — never emit an empty
        // typed payload the resolver would read as "declared but empty".
        if (names.isEmpty()) return Conversion.Unsupported
        // The canonical converter wire: a JSON array of name strings.
        return Conversion.Typed(IRProperty(
            type = "FontFamily",
            data = JsonArray(names.map { JsonPrimitive(it) })
        ))
    }
}
