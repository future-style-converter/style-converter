package com.styleconverter.runtime.typography.font

/**
 * PER-SCRIPT RUN SEGMENTATION — the layout-level substitute for the
 * per-character font fallback neither native runtime has (wave 34, lane F1;
 * the machinery item (e) of the Rule-43 banner in
 * `tools/titan/wpt-not-applicable.mjs`).
 *
 * ## The problem this solves
 * css-fonts-4 §5.2 matches a font stack PER CHARACTER: the UA walks the
 * family list for every codepoint and uses the first family that can supply
 * a glyph for it. Chromium implements that by shaping the whole run with
 * family #1 and re-shaping the `.notdef` sub-runs with the next family.
 * Compose's `FontFamily(Font…)` and SwiftUI's `.custom(name:)` both select
 * ONE face for a whole `Text` and delegate the rest to an opaque platform
 * cascade (Compose's `Typeface.CustomFallbackBuilder` is API 29 against this
 * module's `minSdk = 24`; iOS has no cascade-list hook on `Font`). So a WPT
 * document painting Armenian / Arabic-Indic / Bengali / Khmer / Hebrew text
 * resolved a DIFFERENT face on every surface — different advances, different
 * wrap points, and a native SSIM bounded by typography rather than by
 * anything the runtimes compute.
 *
 * This file is the mechanism: split the string into SCRIPT RUNS, and let the
 * caller install a bundled face per run ([ScriptFallbackFonts]). No blocked
 * API is involved — a `SpanStyle(fontFamily = …)` over an `AnnotatedString`
 * is ordinary Compose text layout.
 *
 * ## The rule (and where it deviates from the wave-31 sketch, with the
 * measurement that forced the deviation)
 *
 *  1. A codepoint inside one of the five TARGET script blocks below resolves
 *     to that script. Inter — the face pinned end-to-end by the harness
 *     (`InterFontFamily`, the web `@font-face`, the iOS registered "Inter",
 *     capture-browser-ref.mjs REF_FONT_STACK) — covers Latin, Greek and
 *     Cyrillic and NOTHING of these five (cmap-verified against
 *     `apps/web-harness/public/fonts/Inter-Regular.ttf`: U+0531, U+0561,
 *     U+0660, U+060C, U+0640, U+09E6, U+05D0, U+17E0, U+17D4 all absent), so
 *     every one of them is a genuine §5.2 fallback position.
 *  2. EVERY other codepoint — Latin, Greek, Cyrillic, ASCII punctuation and
 *     digits, spaces, and every other Common/Inherited character — resolves
 *     to [TextScript.DEFAULT], i.e. keeps the primary face.
 *  3. Common/Inherited characters therefore attach to the preceding run ONLY
 *     when the primary face cannot supply them. Within these five scripts
 *     that set is exactly the script-neutral characters that LIVE INSIDE the
 *     target blocks — U+060C ARABIC COMMA, U+061B ARABIC SEMICOLON, U+061F
 *     ARABIC QUESTION MARK, U+0640 ARABIC TATWEEL, U+17D4…U+17DA KHMER
 *     punctuation, and every combining mark in the Arabic / Bengali / Hebrew
 *     / Khmer blocks — and the block ranges below already assign them. So
 *     rule 3 is realised BY rule 1 and needs no separate pass.
 *
 * The wave-31 sketch said "keep Common/Inherited with the PRECEDING run"
 * unconditionally. MEASURED (fontTools cmap dump of the five bundled faces,
 * `_diag34/F1/fonts`): NotoSansArmenian, NotoSansBengali and NotoSansHebrew
 * carry NO U+002E FULL STOP. The css-counter-styles markers this lane exists
 * to fix are spelled `Ժ.` / `ԺԱ.` / `০.` — marker glyphs plus a period. The
 * unconditional rule would have handed that period to a face with no glyph
 * for it, producing `.notdef` tofu or an unpredictable platform cascade,
 * where Chromium (shape-then-refallback) keeps it in Inter. Rule 2 is both
 * the ref-parity choice and the only one that cannot tofu.
 *
 * ## Honest scope
 * The classifier is a BLOCK table, not a UAX #24 `Script` property lookup.
 * That is a deliberate approximation: it is exact for the five scripts the
 * Rule-43 corpus actually exercises (the banner's item (a) re-derivation:
 * armenian, arabic-indic, bengali, cambodian/khmer, plus the literal-ink
 * Hebrew documents), and any codepoint it does not know answers DEFAULT —
 * i.e. the pre-lane behaviour, never worse. It is NOT a general
 * script-itemiser and must not be sold as one.
 *
 * Twin: `runtimes/swiftui/…/StyleEngine/typography/font/ScriptRunSegmenter.swift`
 * (same tables, same rule, same pin names).
 */
object ScriptRunSegmenter {

    /** The five scripts with a bundled fallback face, plus the primary. */
    enum class TextScript { DEFAULT, ARABIC, ARMENIAN, BENGALI, HEBREW, KHMER }

    /**
     * One maximal span of codepoints resolving to the same script.
     * [start]/[end] are UTF-16 offsets into the source string (half-open),
     * because that is the index space both `AnnotatedString.Builder.addStyle`
     * and `NSAttributedString` ranges speak.
     */
    data class Run(val start: Int, val end: Int, val script: TextScript)

    /**
     * The script of ONE codepoint under the block table above.
     *
     * Ranges are the Unicode blocks (Unicode 15.1 `Blocks.txt`) trimmed of
     * the two positions whose block membership would mis-assign them:
     *  * U+FEFF ZERO WIDTH NO-BREAK SPACE sits at the top of the Arabic
     *    Presentation Forms-B block but is Script=Common — a document-order
     *    BOM must NOT drag the following Latin text onto the Arabic face,
     *    so the range stops at U+FEFE.
     *  * U+0590 / U+05FF and friends are unassigned inside the Hebrew block;
     *    they answer HEBREW harmlessly (no glyph, no run, nothing paints).
     */
    fun scriptOf(cp: Int): TextScript = when (cp) {
        // ── Arabic (css-counter-styles-3 §6 `arabic-indic` / `persian`
        // digits U+0660–U+0669 and U+06F0–U+06F9 both live here), plus the
        // supplement / extended / presentation-form blocks a shaped Arabic
        // run can reach. The block-internal NEUTRALS (U+060C ARABIC COMMA,
        // U+061B, U+061F, U+0640 TATWEEL) are deliberately INSIDE this
        // range — see rule 3 in the file banner.
        in 0x0600..0x06FF, in 0x0750..0x077F, in 0x08A0..0x08FF,
        in 0xFB50..0xFDFF, in 0xFE70..0xFEFE -> TextScript.ARABIC
        // ── Armenian. U+0531–U+058A are the letters/ligatures the §6.2
        // additive `armenian` / `upper-armenian` / `lower-armenian` systems
        // spell their ordinals with; U+058D–U+058F are Armenian symbols.
        in 0x0530..0x058F, in 0xFB13..0xFB17 -> TextScript.ARMENIAN
        // ── Hebrew (the §6.2 additive `hebrew` system + the literal-ink
        // Hebrew documents the banner's item (a) enumerates), plus the
        // Alphabetic Presentation Forms Hebrew sub-range.
        in 0x0590..0x05FF, in 0xFB1D..0xFB4F -> TextScript.HEBREW
        // ── Bengali (§6.2 simple-numeric `bengali`, digits U+09E6–U+09EF).
        in 0x0980..0x09FF -> TextScript.BENGALI
        // ── Khmer (§6.2 `khmer`/`cambodian`, digits U+17E0–U+17E9). The
        // Khmer Symbols block carries the lunar-date signs; both are in the
        // bundled face.
        in 0x1780..0x17FF, in 0x19E0..0x19FF -> TextScript.KHMER
        // Everything else — Latin, Greek, Cyrillic, ASCII, general
        // punctuation, CJK, emoji — keeps the primary face. Rule 2.
        else -> TextScript.DEFAULT
    }

    /**
     * Split [text] into maximal same-script runs, iterating by CODEPOINT so
     * a surrogate pair is classified once and never severed between its two
     * UTF-16 units (a split there would hand half a character to a different
     * `SpanStyle` and render two replacement glyphs).
     *
     * Returns a single DEFAULT run for the empty string — callers treat
     * "one DEFAULT run" as "nothing to do" and skip the rebuild entirely,
     * which is what keeps every non-target-script capture byte-identical.
     */
    fun segment(text: String): List<Run> {
        if (text.isEmpty()) return listOf(Run(0, 0, TextScript.DEFAULT))
        val runs = ArrayList<Run>()
        var runStart = 0                       // UTF-16 offset of the open run
        var runScript: TextScript? = null      // null until the first codepoint
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val width = Character.charCount(cp)  // 2 for a surrogate pair
            val s = scriptOf(cp)
            if (runScript == null) {
                runScript = s
            } else if (s != runScript) {
                // Script changed → close the open run at THIS codepoint's
                // start and open a new one. No merging of adjacent equal
                // runs is needed: they cannot occur by construction.
                runs.add(Run(runStart, i, runScript))
                runStart = i
                runScript = s
            }
            i += width
        }
        runs.add(Run(runStart, text.length, runScript ?: TextScript.DEFAULT))
        return runs
    }

    /**
     * Does [text] contain any codepoint that needs a bundled fallback face?
     * The cheap pre-check every call site runs first: false ⇒ the caller
     * returns its input untouched, so a Latin-only document takes exactly
     * the pre-lane code path and cannot move a single pixel.
     */
    fun needsFallback(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (scriptOf(cp) != TextScript.DEFAULT) return true
            i += Character.charCount(cp)
        }
        return false
    }
}
