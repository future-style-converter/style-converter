package com.styleconverter.runtime.typography

import androidx.compose.ui.text.font.FontFamily
import com.styleconverter.runtime.typography.font.DocumentFontRegistry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The ONE `font-family` resolver for the Compose runtime (wave 24, lane
 * LF / B-RC7).
 *
 * ## Why this file exists
 *
 * There used to be two resolvers that disagreed:
 *
 *  * `TextStyleApplier.extractFontFamily` — read ONLY `data[0]` of the
 *    list form and matched it with substring heuristics. Its primitive
 *    (bare-string) twin bottomed out at [InterFontFamily].
 *  * `TypographyExtractor.extractFontFamily` — went through
 *    `ValueExtractors.extractKeyword`, which returns null for a
 *    `JsonArray`, so EVERY list-form declaration extracted as null.
 *
 * The live wire is ALWAYS a JSON array of family names — verified against
 * the converter (`fixtures/properties/typography/font-family.json` →
 * `["Helvetica Neue","Helvetica","Arial","sans-serif"]`) and against the
 * WPT corpus IR (`tools/titan/runs/wave23-final/sections/css-text/
 * per-test-ir/wpt__css-text__bidi__bidi-lines-001.json` →
 * `["Inter","-apple-system","system-ui","Segoe UI","Roboto","Oxygen",
 * "Ubuntu","sans-serif"]`). So the primitive branch is dead for converter
 * output and the array branch carried every real declaration.
 *
 * That made the harness font stack silently unresolvable on Android: the
 * ref/web/iOS harnesses all render the BUNDLED Inter face (see
 * `tools/titan/capture-browser-ref.mjs` REF_FONT_STACK, which injects the
 * same Inter faces into the reference page), while Compose matched
 * `"Inter"` against nothing and fell through to `FontFamily.Default`
 * (Roboto) — different faces ⇒ different advances ⇒ different wrap
 * points on every text-driven WPT capture.
 *
 * ## Semantics (css-fonts-4 §5.2 "Matching font styles")
 *
 * The family list is a PRIORITISED fallback list: the UA walks it and
 * uses the first family that is available. Only when NO listed family is
 * available does it fall back to its default font. Both rules are
 * implemented here — [resolveEntry] answers "is this one available on
 * Compose?" (null = no, keep walking) and [resolve] owns the walk plus
 * the terminal fallback.
 */
object CssFontFamilyResolver {

    /**
     * Resolve an IR `FontFamily` payload to the Compose family to render.
     *
     * @param data the property's `data` node. Accepted shapes:
     *   * `JsonArray` of strings — the canonical converter wire.
     *   * `JsonObject` with a `families` array — the shape
     *     TypographyExtractor's KDoc documented; kept so hand-authored IR
     *     and the schema-conformance goldens keep decoding.
     *   * `JsonPrimitive` string — legacy hand-authored single name.
     * @return the family to install on the TextStyle, or null when the
     *   payload carries no family at all (no declaration ⇒ the caller's
     *   own default applies; ComponentRenderer's placeholder path bottoms
     *   out at [InterFontFamily] there, which is the harness-parity rule
     *   for the UNDECLARED case).
     */
    fun resolve(data: JsonElement?): FontFamily? {
        val names = families(data) ?: return null
        // css-fonts-4 §5.2: first AVAILABLE family in the list wins.
        for (name in names) resolveEntry(name)?.let { return it }
        // Every listed family is unavailable on this device. css-fonts-4
        // §5.2 then hands the run to the UA's default font — which on
        // Compose is exactly `FontFamily.Default`. Deliberately NOT
        // InterFontFamily: substituting the bundled face for a named-but-
        // missing family (`font-family: Arial`) would repaint two verified
        // dark-stage variants (FontFamily_QuotedSingle,
        // FontFamily_Unquoted_Named) that the committed baselines captured
        // as the platform default, and CSS does not ask for it.
        return if (names.isEmpty()) null else FontFamily.Default
    }

    /**
     * The family-name list carried by [data], in declaration order.
     * Returns null when the payload is not a family list at all (so
     * "absent" stays distinguishable from "declared but empty").
     */
    internal fun families(data: JsonElement?): List<String>? = when (data) {
        // Canonical wire: ["Inter", "-apple-system", …]. Non-string
        // members are skipped rather than crashing the whole extraction.
        is JsonArray -> data.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        // Documented object shape { "families": [...] }.
        is JsonObject -> (data["families"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        // Legacy single name — treated as a one-entry list so it goes
        // through the SAME per-name mapping and the same §5.2 fallback.
        // `isString` gates it: a bare number/boolean is a malformed
        // payload, and answering the §5.2 UA default for it would silently
        // claim a family the wire never named. Null keeps the drop visible.
        is JsonPrimitive -> if (data.isString) data.contentOrNull?.let { listOf(it) } else null
        else -> null
    }

    /**
     * Map ONE family name to the Compose family that provides it, or null
     * when this device has no such family and the walk must continue.
     *
     * Order matters: exact matches first (so `sans-serif` never trips the
     * `contains("serif")` heuristic), substring heuristics last.
     */
    internal fun resolveEntry(rawName: String): FontFamily? {
        // CSS family names may be quoted (`"Helvetica Neue"`); the
        // converter already strips quotes, but hand-authored IR may not.
        val name = rawName.trim().trim('"', '\'').lowercase()
        if (name.isEmpty()) return null

        // ── wave-35 lane B2: the DOCUMENT font database, consulted FIRST.
        // css-fonts-4 §5 resolves a family name against the document's own
        // @font-face database before any system or generic face, so a document
        // that declared `@font-face { font-family: test; … }` must get THAT
        // file even if the name collides with a generic or with `Inter`. The
        // registry is empty for every document that declared no face (the
        // overwhelming majority), so this is a single map lookup on an empty
        // map and every pre-wave-35 capture keeps its exact resolution.
        if (!DocumentFontRegistry.isEmpty()) {
            DocumentFontRegistry.resolve(name)?.let { return it }
        }

        // ── The BUNDLED family. This is the B-RC7 fix: `Inter` is the head
        // of the harness font stack on all three platforms (web
        // index.html html/body rule, iOS's registered "Inter" face,
        // Compose typography/InterFont.kt loading res/font/inter_*.ttf),
        // so a stack that names it MUST land on InterFontFamily instead of
        // walking past it into the platform default.
        if (name == "inter") return InterFontFamily

        // ── css-fonts-4 §12.1 generic families, exact matches.
        when (name) {
            "serif" -> return FontFamily.Serif
            "sans-serif" -> return FontFamily.SansSerif
            "monospace" -> return FontFamily.Monospace
            "cursive" -> return FontFamily.Cursive
            // §12.2 system-ui / ui-* : the platform's UI faces. Compose
            // exposes only Default (= the system UI font, Roboto on
            // Android) plus the three shaped generics. `ui-sans-serif` maps
            // to SansSerif, NOT Default — that is what the legacy
            // substring rule (`contains("sans")`) produced, and the
            // dark-stage FontFamily_UiSansSerif baseline captured it.
            // (Identical pixels on Android either way, since Default IS
            // the sans-serif face; the pin table keeps it provable.)
            "system-ui", "ui-rounded" -> return FontFamily.Default
            "ui-sans-serif" -> return FontFamily.SansSerif
            "ui-serif" -> return FontFamily.Serif
            "ui-monospace" -> return FontFamily.Monospace
            // §12.1 fantasy / math / emoji / fangsong have no Compose
            // analogue; Android resolves them to the system default. These
            // are dark-stage-verified variants (FontFamily_Fantasy,
            // _Math, _Emoji, _Fangsong all baseline to the default face),
            // so answering Default here is both CSS-honest and
            // capture-preserving.
            "fantasy", "math", "emoji", "fangsong" -> return FontFamily.Default
        }

        // ── Legacy substring heuristics, preserved byte-for-byte from
        // TextStyleApplier's pre-wave-24 mapping so families like
        // "Courier Mono" / "Times New Roman" / "Comic Sans MS" keep
        // landing on the same generic they did before the unification.
        // The `!contains("sans")` guard keeps "…sans-serif…" out of the
        // serif branch, exactly as before.
        return when {
            name.contains("mono") -> FontFamily.Monospace
            name.contains("serif") && !name.contains("sans") -> FontFamily.Serif
            name.contains("sans") -> FontFamily.SansSerif
            name.contains("cursive") -> FontFamily.Cursive
            // Not a family this runtime can provide. Null (not Default) is
            // the point of the walk: `["Helvetica Neue", …, "sans-serif"]`
            // must reach the sans-serif tail instead of stopping at the
            // first unavailable name. No silent fallthrough — the caller's
            // §5.2 terminal owns the give-up decision.
            else -> null
        }
    }
}
