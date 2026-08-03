package com.styleconverter.runtime.lists

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

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
 * css-lists-3 §3.2: the marker box "inherits from the originating
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
            fontSize = inherited.fontSize,
            fontFamily = inherited.fontFamily,
            fontWeight = inherited.fontWeight,
            fontStyle = inherited.fontStyle,
            // The marker's line box must match the item's, or a
            // baseline-aligned row would still stack the two boxes at
            // different heights (css-lists-3 §3.2 again — same line).
            lineHeight = inherited.lineHeight,
            // Inherited: yes (css-text-3 §8.2), and it changes the
            // marker's advance width, so it belongs to the glyph run.
            letterSpacing = inherited.letterSpacing,
            // DELIBERATELY ABSENT: textAlign, textIndent, lineBreak —
            // paragraph-level, already applied by the item itself. See
            // the "Why NARROWED" note above.
        )
}
