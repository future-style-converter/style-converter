package com.styleconverter.runtime.typography.font

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.styleconverter.runtime.R
import com.styleconverter.runtime.typography.font.ScriptRunSegmenter.TextScript

/**
 * The bundled per-script fallback faces, and the one function that installs
 * them (wave 34, lane F1 — the Compose half of the Rule-43 closing move).
 *
 * ## The faces
 * Noto Sans <Script> Regular, OFL 1.1, from the notofonts.github.io mirror
 * the Rule-43 banner records, sha256-verified at download time against the
 * banner's recorded digests:
 *
 * | script   | res/font asset                    | upstream sha256 | bundled sha256 |
 * |----------|-----------------------------------|-----------------|----------------|
 * | Arabic   | `noto_sans_arabic_regular.ttf`    | bdff3e56…aaef48 | 2ed68535…87770a |
 * | Armenian | `noto_sans_armenian_regular.ttf`  | 720df88c…bfce1e | fa4e8909…eb02c4 |
 * | Bengali  | `noto_sans_bengali_regular.ttf`   | b55c62ee…7a3193 | cb34cf04…c6e796 |
 * | Hebrew   | `noto_sans_hebrew_regular.ttf`    | cdefaf8e…5e02ee | 02f7a83d…42094d |
 * | Khmer    | `noto_sans_khmer_regular.ttf`     | e66675f2…b5e605 | fb2b756d…eb8302 |
 *
 * ~537 KB total. The iOS twin bundles the BYTE-IDENTICAL five files as
 * SwiftPM resources (`runtimes/swiftui/…/Resources/Fonts/`) — verified with
 * `cmp` at bundling time — which is what makes the two natives resolve the
 * SAME outlines, and therefore the same advances, for the same string.
 *
 * ## Why the bundled bytes differ from upstream: METRIC-COMPATIBLE FALLBACK
 * The bundled files are the upstream ones with their VERTICAL metrics
 * rewritten to Inter's em-normalised values. `hmtx` and `glyf` are untouched,
 * so every advance width and every outline is bit-identical to upstream; only
 * the line-box metrics move. Reproducible in one fontTools pass per file:
 *
 *     f['hhea'].ascender, f['hhea'].descender, f['hhea'].lineGap = 969, -241, 0
 *     os2.sTypoAscender, os2.sTypoDescender, os2.sTypoLineGap    = 969, -241, 0
 *     os2.usWinAscent, os2.usWinDescent                          = 969, 241
 *     os2.fsSelection |= 128                      # USE_TYPO_METRICS
 *
 * (969/-241 = Inter-Regular's 1984/-494 at unitsPerEm 2048, scaled to these
 * faces' unitsPerEm 1000.)
 *
 * MEASURED, and this is why it is not optional. Upstream Noto Sans Arabic
 * declares hhea ascender/descender 1374/-738 — a 2.112 em line against
 * Inter's 1.210. A `SpanStyle(fontFamily = …)` makes Compose take the MAX
 * ascent across the line's spans, so on the first Android capture the Arabic
 * face's ascent dominated and pushed every row DOWN: composed WPT capture of
 * css-counter-styles/arabic-indic/css3-counter-styles-102, first ink band
 * y=[101,115) before the lane → y=[105,122) with the raw upstream face,
 * against the browser-ref's y=[94,112). The horizontal half had CONVERGED at
 * the same time (row 9: ref 85 px wide, pre-lane 73, raw-upstream 85 —
 * exact), so the raw face bought the right advances and paid for them in
 * baseline placement, and SSIM went DOWN (0.7791 → 0.7546). Normalising the
 * vertical metrics keeps the advances and removes the shift.
 *
 * ## Regular only, deliberately
 * Each family declares ONE face at [FontWeight.Normal]. Every Rule-43 corpus
 * document paints its non-Latin text at the default weight, and shipping four
 * weights per script would have quadrupled the payload for coverage nothing
 * exercises. A bold non-Latin run therefore takes Compose's synthetic
 * emboldening — the same synthesis Chromium applies when a fallback family
 * has no bold member (css-fonts-4 §2.8.1, font-synthesis-weight), so it is the
 * matching behaviour rather than a silent gap. Stated, not hidden.
 *
 * ## Scope gate
 * Nothing here runs outside WPT capture: every call site passes the ambient
 * `LocalWptCaptureMode`, which is `false` on the whole product/baseline path
 * (`ScreenshotCaptureScreen` sets it only for the TITAN inbox/composed
 * capture), so the committed 327-pair dark-stage baselines cannot move. The
 * second, stronger guarantee is structural: [applyTo] returns its input
 * unchanged whenever the string has no target-script codepoint, so even
 * inside WPT capture a Latin-only document takes the identical path.
 */
object ScriptFallbackFonts {

    /**
     * Is bundled-face SUBSTITUTION a measured improvement on this platform?
     *
     * TRUE on Compose. The Android emulator resolves non-Latin text through
     * its own Noto subset, which is NOT the face the Chromium-on-macOS
     * browser-ref lands on, so pinning our own bundled faces moves the
     * capture TOWARDS the ref. Measured — composed WPT capture, private
     * emulator-5572, the 13 documents that carry non-Latin ink, scored with
     * `diffComposedVsRef` against the frozen
     * `white-black-ink-font-lh-imgpad-htmlpins` refs (wave33-final numbers
     * on the left, this lane on the right):
     *
     *   arabic-indic 101  0.9287 → 0.9608  (+0.032, CROSSES 0.95)
     *   arabic-indic 102  0.7791 → 0.8140  (+0.035)
     *   arabic-indic 103  0.9658 → 0.9760  (+0.010)
     *   armenian     006  0.9294 → 0.9446  (+0.015)
     *   armenian     007  0.7787 → 0.8165  (+0.038)
     *   armenian     008  0.9173 → 0.9214  (+0.004)
     *   armenian     009  0.9674 → 0.9710  (+0.004)
     *   bengali      116  0.9485 → 0.9441  (−0.004)
     *   bengali      117  0.8241 → 0.8052  (−0.019)
     *   bengali      118  0.9756 → 0.9741  (−0.002)
     *   cambodian    158  0.9305 → 0.9390  (+0.009)
     *   cambodian    159  0.7049 → 0.7218  (+0.017)
     *   css-text bidi-lines-001  0.8713 → 0.8728  (+0.002)
     *
     * Net +1 test over the 0.95 gate (3/13 → 4/13), 10 of 13 improved, sum
     * of deltas +0.140. Bengali is the one script that goes slightly the
     * wrong way — the emulator's own Bengali face was already metrically
     * equal to Noto (byte-identical row extents in the pre-normalisation
     * A/B), so it had nothing to gain and paid the vertical-metric
     * normalisation. Three documents is not enough to carve it out; it is
     * recorded rather than special-cased.
     *
     * FALSE on the iOS twin, for a measured reason spelled out there: the
     * simulator shares CoreText's fallback with the Chromium-on-macOS ref,
     * so substituting Noto there REPLACES a matching face with a
     * non-matching one. This constant is the ONE line where the two twins
     * deliberately differ, and it is a measurement, not a preference.
     */
    const val SUBSTITUTION_ENABLED: Boolean = true

    /** Noto Sans Arabic Regular — Arabic-Indic + Extended-Arabic-Indic
     *  digits (css-counter-styles-3 §6.1 `arabic-indic` / `persian`) and the
     *  Arabic prose of the css-text bidi documents. */
    val ArabicFontFamily: FontFamily = FontFamily(
        Font(R.font.noto_sans_arabic_regular, FontWeight.Normal, FontStyle.Normal)
    )

    /** Noto Sans Armenian Regular — the §6.2 additive `armenian` /
     *  `upper-armenian` / `lower-armenian` ordinal letters. */
    val ArmenianFontFamily: FontFamily = FontFamily(
        Font(R.font.noto_sans_armenian_regular, FontWeight.Normal, FontStyle.Normal)
    )

    /** Noto Sans Bengali Regular — the §6.2 simple-numeric `bengali`
     *  digits U+09E6–U+09EF. */
    val BengaliFontFamily: FontFamily = FontFamily(
        Font(R.font.noto_sans_bengali_regular, FontWeight.Normal, FontStyle.Normal)
    )

    /** Noto Sans Hebrew Regular — the §6.2 additive `hebrew` system and the
     *  literal-ink Hebrew documents (css-text-decor, selectors). */
    val HebrewFontFamily: FontFamily = FontFamily(
        Font(R.font.noto_sans_hebrew_regular, FontWeight.Normal, FontStyle.Normal)
    )

    /** Noto Sans Khmer Regular — the §6.2 `khmer` / `cambodian` digits
     *  U+17E0–U+17E9. */
    val KhmerFontFamily: FontFamily = FontFamily(
        Font(R.font.noto_sans_khmer_regular, FontWeight.Normal, FontStyle.Normal)
    )

    /**
     * The family that supplies [script], or null for [TextScript.DEFAULT] —
     * null meaning "no span, keep whatever face the caller's TextStyle
     * already resolved", which is how a DEFAULT run stays byte-identical
     * instead of being re-stated as Inter (re-stating it would OVERRIDE a
     * document's own `font-family` declaration, a regression this lane has
     * no business causing).
     */
    fun familyFor(script: TextScript): FontFamily? = when (script) {
        TextScript.DEFAULT -> null
        TextScript.ARABIC -> ArabicFontFamily
        TextScript.ARMENIAN -> ArmenianFontFamily
        TextScript.BENGALI -> BengaliFontFamily
        TextScript.HEBREW -> HebrewFontFamily
        TextScript.KHMER -> KhmerFontFamily
    }

    /**
     * Install a per-script `fontFamily` span over every non-DEFAULT run of
     * [base] — the layout-level stand-in for per-character fallback.
     *
     * Additive by construction: the spans this adds set ONLY `fontFamily`,
     * and Compose merges overlapping `SpanStyle`s field-wise, so the
     * small-caps size spans (`synthesizeSmallCaps`) and the word-spacing
     * letter-spacing spans (`TextStyleApplier.applyWordSpacingSpans`) that
     * may already sit on [base] all survive untouched.
     *
     * @param base the string as the caller already built it (plain or
     *   already carrying spans).
     * @param enabled the ambient WPT-capture flag. False ⇒ identity, so the
     *   product renderer and the 327 baselines never see this code.
     * @return [base] itself when there is nothing to do (disabled, or no
     *   target-script codepoint) — reference-identical, not a copy, so the
     *   "cannot move" claim is structural rather than a comparison.
     */
    fun applyTo(base: AnnotatedString, enabled: Boolean): AnnotatedString {
        // Both gates, in cheapest-first order: the platform's measured
        // verdict, then the caller's ambient WPT-capture flag.
        if (!SUBSTITUTION_ENABLED) return base
        if (!enabled) return base
        if (!ScriptRunSegmenter.needsFallback(base.text)) return base
        val builder = AnnotatedString.Builder(base)
        for (run in ScriptRunSegmenter.segment(base.text)) {
            val family = familyFor(run.script) ?: continue   // DEFAULT → no span
            builder.addStyle(SpanStyle(fontFamily = family), run.start, run.end)
        }
        return builder.toAnnotatedString()
    }

    /**
     * String convenience for the three `::marker` call sites, which hand
     * `Text` a raw `String`. Same gate, same identity guarantee — a marker
     * with no target-script glyph comes back as a plain `AnnotatedString`
     * carrying no spans at all, which `Text` lays out identically to the
     * `String` overload.
     */
    fun annotate(text: String, enabled: Boolean): AnnotatedString =
        applyTo(AnnotatedString(text), enabled)
}
