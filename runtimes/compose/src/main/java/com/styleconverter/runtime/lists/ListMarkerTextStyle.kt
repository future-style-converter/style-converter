package com.styleconverter.runtime.lists

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
// TextUnit.Unspecified is how a resolved style spells "no font-size
// declaration"; `sp` builds the 16sp browser-default bottom-out below.
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
// The bundled face the marker falls back to when nothing declared one —
// the item's own bottom-out (see the fontFamily comment below).
import com.styleconverter.runtime.typography.InterFontFamily

/**
 * The typography a synthesized `::marker` box paints with — wave 27,
 * lane NMARK (B-RC8).
 *
 * ## The defect
 * `ComponentRenderer.RenderListItemMarker` built its marker `Text` with a
 * colour and nothing else, so every marker rendered at Compose's Material
 * default (~14sp) regardless of the item's own font. On the wave27-gate
 * arabic-indic capture
 * (tools/titan/runs/wave27-gate/sections/css-counter-styles/
 *  android-screenshots/wpt__css-counter-styles__arabic-indic__
 *  css3-counter-styles-101.png) the items inherit `font-size: 25px` and
 * the marker painted at roughly half that — visibly smaller than the
 * browser-ref's, and sitting on a different baseline from the item's own
 * glyph run because a 14sp box and a 25px box have different ascents.
 *
 * ## The rule
 * css-lists-3 §3.1.1: the marker box "inherits from the originating
 * element" — it is not an independently-styled box, so the list item's
 * resolved font IS the marker's font.
 *
 * ## Honest scope: this is the CONTAINER's font, not the item's
 * The caller resolves the style from the LIST CONTAINER's
 * (`ol`/`ul`/`menu`/`dir`) inheritance-merged property list, because that
 * is what is in scope where the marker rows are built. For every shape
 * the wave-27 corpus produces the two agree — `font-size` is declared on
 * an ancestor and reaches container and item alike through the
 * inheritance channel. They diverge only when an `<li>` declares its OWN
 * font, where the browser would size the marker from the `<li>`.
 * MEASURED: iOS has the identical limitation (its marker `Text` carries
 * no `.font()` and takes the container's environment font — a raster
 * probe varying `font-size` on the `<li>` alone moved the iOS marker not
 * one pixel), so the two natives stay consistent with each other and
 * deviate from the browser together. Closing it needs the per-item merged
 * list at the marker call site on BOTH runtimes.
 *
 * ## Why NARROWED and not passed through
 * The resolved style also carries PARAGRAPH-level fields — `textAlign`,
 * `textIndent`, `lineBreak` — which describe how the ITEM lays its lines
 * out, not how a one-token marker box paints. Passing them through would
 * apply them twice: once on the item's own content, once again on the
 * marker (a `text-indent: 40px` item would shove its marker 40px right of
 * where the browser puts it). Only the character-level fields — the font
 * quartet, the line box, letter spacing, and colour — are marker state.
 *
 * Pure and JVM-pinned (ListMarkerTextStyleTest); the `@Composable`
 * pathway itself needs androidTest, so this is the testable half.
 */
object ListMarkerTextStyle {

    /**
     * Narrow an item's resolved [inherited] text style to the fields a
     * marker box paints with, overriding the colour with [textColor] when
     * the caller resolved one explicitly.
     *
     * @param inherited the LIST CONTAINER's inheritance-merged style,
     *   from `TextStyleApplier.extractTextStyle` (see "Honest scope"
     *   above for why it is the container's and not the `<li>`'s).
     * @param textColor the marker colour the renderer already resolved
     *   for this subtree (the pre-wave-27 behaviour, kept verbatim);
     *   `null` falls back to whatever [inherited] carries.
     */
    fun forItem(inherited: TextStyle, textColor: Color? = null): TextStyle =
        TextStyle(
            // Explicit caller colour wins — it is the same value the
            // pre-wave-27 code passed, so a document that only ever set
            // colour renders byte-identically to before this lane.
            color = textColor ?: inherited.color,
            // The font quartet: the whole point of B-RC8. `font-size`,
            // `font-family`, `font-weight` and `font-style` are all
            // "Inherited: yes" (css-fonts-4 §1.1), so the marker's are
            // the item's.
            //
            // Wave 41 (lane T4) — the UNDECLARED case bottoms out at the
            // browser default 16, the SAME bottom-out the item's own text
            // run gets (ComponentRenderer.PlaceholderContent:
            // `if (fontSize != Unspecified) fontSize else 16.sp`) and the
            // same constant the marker's own composed-WPT snap already
            // reads (ListMarkerLineBox.DEFAULT_FONT_SIZE_SP). It was
            // `inherited.fontSize` alone, i.e. Unspecified for every
            // document that never declares `font-size` — and an
            // Unspecified size is not "the item's size": Compose's `Text`
            // resolves it to the Material default (~14sp), so one CSS
            // font-size produced a 14sp marker beside 16sp item text on
            // every marker row of such a document. css-lists-3 §3.5 makes
            // the marker inherit from its originating element, whose
            // default IS 16 (css-fonts-4 §2.5.1 `medium`, the corpus-pinned
            // browser default). MEASURED on wave40-final
            // css-counter-styles/cssom__cssom-negative-setter android:
            // marker cap-height 10px vs web's 12px vs the ref's glyph rows
            // — every text-marker row on Android under-painted its ink by
            // (14/16)². The two core call sites that DERIVE numbers from
            // this style (the snap's fontSizePx at ComponentRenderer:3311
            // and the half-leading delta at :3291) already bottomed out at
            // the same 16, so an explicit 16.sp here leaves them
            // byte-identical and only the painted glyph moves.
            fontSize = if (inherited.fontSize != TextUnit.Unspecified)
                inherited.fontSize else ListMarkerLineBox.DEFAULT_FONT_SIZE_SP.sp,
            // Wave 29 (lane MP) — the UNDECLARED case bottoms out at the
            // bundled Inter, the SAME bottom-out
            // `ComponentRenderer.PlaceholderContent` applies to the item's
            // own text (`textStyle.fontFamily ?: InterFontFamily`). It was
            // `inherited.fontFamily` alone, i.e. NULL for every document
            // that never declares `font-family` — and a null family is not
            // "the item's font", it is Compose's SYSTEM face. One CSS
            // `font-family`, two different primary faces on one line box.
            //
            // HONEST SCOPE, measured and NOT assumed: this is NOT what
            // closed the row-pitch drift. An on-device A/B (emulator-5558,
            // css3-counter-styles-007) with the snap forced off left the
            // marker measuring h=34 exactly as before —
            // [ListMarkerLineBox]'s snap is what closed the pitch.
            //
            // SKEPTIC CORRECTION (wave 29): an earlier revision of this
            // comment went on to claim the bottom-out was a LATIN-only
            // parity fix that "cannot matter" for Armenian/Bengali/Khmer,
            // on the reasoning that Inter covers none of them so the same
            // system fallback face resolves either way. Four staged A/B
            // builds refuted that reasoning — naming Inter as the PRIMARY
            // family changes which fallback face Compose resolves, and that
            // face's natural line box differs:
            //   • snap off, three mechanics fields only, NO bottom-out →
            //     every row height byte-identical to wave 28 (bengali-117
            //     pitch 33.00, capture 896). The mechanics fields alone
            //     really do move no row height.
            //   • snap off, bottom-out added → bengali-117 pitch 33.00 →
            //     33.76 and capture 896 → 920; bengali-116 32.50 → 33.40;
            //     armenian-007 unchanged at 33.80. Bengali resolves a
            //     DIFFERENT fallback under Inter; Armenian does not.
            //   • final config, bottom-out removed → armenian-008 −0.0130,
            //     bengali-117 −0.0209, armenian-007 −0.0077, armenian-006
            //     −0.0044, bengali-116 −0.0021 SSIM; arabic-indic and
            //     cambodian move ≤ +0.0009.
            //   • css-lists (the LATIN markers this line was supposed to be
            //     for) is a WASH either way: ±0.004 with no pass flip,
            //     10/12 both ways.
            // So the bottom-out earns its place, but on the NON-Latin
            // corpus — the opposite of the original rationale. It is kept
            // for the CSS reason (one `font-family` must not resolve two
            // primary faces on one line box) with the measured effect
            // stated honestly.
            fontFamily = inherited.fontFamily ?: InterFontFamily,
            fontWeight = inherited.fontWeight,
            fontStyle = inherited.fontStyle,
            // The marker's line box must match the item's, or a
            // baseline-aligned row would still stack the two boxes at
            // different heights (css-lists-3 §3.5 again — same line).
            lineHeight = inherited.lineHeight,
            // Inherited: yes (css-text-3 §8.2), and it changes the
            // marker's advance width, so it belongs to the glyph run.
            letterSpacing = inherited.letterSpacing,
            // DELIBERATELY ABSENT: textAlign, textIndent, lineBreak —
            // paragraph-level, already applied by the item itself. See
            // the "Why NARROWED" note above.
            // Wave 29 (lane MP) — the three LINE-BOX MECHANICS fields.
            // Not CSS properties: they are how this runtime makes Compose
            // behave like CSS, set by `TextStyleApplier.extractTextStyle`
            // on every text run and then dropped here, because a
            // positional `TextStyle(...)` ctor does not inherit them.
            //   • platformStyle — `includeFontPadding = false`, or the
            //     marker's first line carries the font's top/bottom
            //     padding while the item's does not.
            //   • lineHeightStyle — Center/None, the distribution rule
            //     that decides WHERE in the box the baseline sits; two
            //     boxes of equal height with different distributions put
            //     their baselines apart, and a baseline-aligned Row is
            //     `max(baseline) + max(height − baseline)`, i.e. it grows
            //     by exactly that disagreement.
            //   • textMotion — `TextMotion.Animated`, the linear/subpixel
            //     advances the ref (Chrome) uses; without it the marker
            //     alone reverted to hinted quantized advances, which the
            //     item's own snap-mirroring comment in ComponentRenderer
            //     calls out as cumulative first-line glyph drift.
            // MEASURED: on the Armenian corpus these three moved glyph ink
            // sub-pixel and did NOT change the row's height on their own
            // (the fallback face's win/hhea metrics happen to coincide) —
            // they are here so marker and item cannot resolve their shared
            // line box through two different sets of rules, which is the
            // class of bug this whole lane is.
            platformStyle = inherited.platformStyle,
            lineHeightStyle = inherited.lineHeightStyle,
            textMotion = inherited.textMotion,
        )
}
