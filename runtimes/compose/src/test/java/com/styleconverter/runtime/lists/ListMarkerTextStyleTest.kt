package com.styleconverter.runtime.lists

// Wave 27, lane NMARK (B-RC8) — pin table for ListMarkerTextStyle.forItem,
// the narrowing that gives a synthesized `::marker` box the item's own font.
//
// ## The bug
// ComponentRenderer.RenderListItemMarker built its marker Text with a colour
// and NOTHING else, so every marker painted at Compose's Material default
// (~14sp) no matter what the item inherited. On the live capture
// tools/titan/runs/wave27-gate/sections/css-counter-styles/
// android-screenshots/wpt__css-counter-styles__arabic-indic__
// css3-counter-styles-101.png the items inherit `font-size: 25px` and the
// marker rendered at roughly half the browser-ref's size.
//
// The @Composable pathway itself needs androidTest to execute (same reason
// ListMarkerSelectionTest pins the resolver rather than the Row), so these
// pins cover the pure half: what forItem keeps, what it overrides, and —
// the part a future refactor is most likely to get wrong — what it drops.

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ListMarkerTextStyleTest {

    /// An item's resolved style carrying BOTH character-level fields (the
    /// marker's) and paragraph-level ones (the item's own) — the 25px
    /// arabic-indic shape, plus the paragraph fields that must not leak.
    private val itemStyle = TextStyle(
        color = Color.Red,
        fontSize = 25.sp,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        lineHeight = 30.sp,
        letterSpacing = 2.sp,
        textAlign = TextAlign.Center,
        textIndent = TextIndent(firstLine = 40.sp)
    )

    @Test
    fun `marker inherits the item's font quartet`() {
        // css-lists-3 §3.2 — the marker box inherits from its originating
        // element; css-fonts-4 §1.1 makes all four "Inherited: yes". This
        // is the whole of B-RC8: before the fix these were all unset and
        // the marker fell back to Material's ~14sp default.
        val marker = ListMarkerTextStyle.forItem(itemStyle)
        assertEquals(25.sp, marker.fontSize)
        assertEquals(FontFamily.Serif, marker.fontFamily)
        assertEquals(FontWeight.Bold, marker.fontWeight)
        assertEquals(FontStyle.Italic, marker.fontStyle)
    }

    @Test
    fun `marker inherits the item's line box and letter spacing`() {
        // Same line, so the same line box (a baseline-aligned Row still
        // stacks two differently-sized boxes wrong); letter-spacing is
        // "Inherited: yes" (css-text-3 §8.2) and changes the advance.
        val marker = ListMarkerTextStyle.forItem(itemStyle)
        assertEquals(30.sp, marker.lineHeight)
        assertEquals(2.sp, marker.letterSpacing)
    }

    @Test
    fun `paragraph-level fields are dropped, not inherited`() {
        // textAlign / textIndent describe how the ITEM lays out its lines
        // and are already applied by the item itself. Passing them through
        // would apply them twice — a `text-indent: 40px` item would shove
        // its marker 40px right of where the browser puts it.
        //
        // Compared against an UNSET TextStyle rather than a literal
        // sentinel: Compose has changed how it spells "no value" for these
        // fields across versions (nullable → `Unspecified`), and the claim
        // under test is "the marker carries no more than an unset style
        // does", which survives that.
        val marker = ListMarkerTextStyle.forItem(itemStyle)
        val unset = TextStyle()
        assertEquals(unset.textAlign, marker.textAlign)
        assertEquals(unset.textIndent, marker.textIndent)
        assertNotEquals(TextAlign.Center, marker.textAlign)
    }

    @Test
    fun `an explicit caller colour wins over the inherited one`() {
        // The renderer already resolved a marker colour for this subtree
        // (the pre-wave-27 behaviour). Keeping it authoritative is what
        // makes a colour-only document render byte-identically to before.
        val marker = ListMarkerTextStyle.forItem(itemStyle, textColor = Color.Blue)
        assertEquals(Color.Blue, marker.color)
    }

    @Test
    fun `without a caller colour the inherited one stands`() {
        // Null is the "renderer resolved nothing" case — the marker then
        // takes whatever the item's own cascade produced.
        val marker = ListMarkerTextStyle.forItem(itemStyle, textColor = null)
        assertEquals(Color.Red, marker.color)
    }

    @Test
    fun `an empty inherited style narrows to an empty marker style plus Inter`() {
        // The renderer's failure fallback: a malformed typography payload
        // yields TextStyle(), and forItem must not invent anything from it
        // — the marker then paints at the platform default exactly as it
        // did before wave 27, rather than vanishing.
        //
        // WAVE 29 (lane MP) — with ONE deliberate exception, the font
        // FAMILY. It used to be `TextStyle()` verbatim; the family is now
        // the bundled Inter, which is what the ITEM's own text run has
        // always bottomed out at (ComponentRenderer's placeholder branches:
        // `textStyle.fontFamily ?: InterFontFamily`). A null family is not
        // "the item's font" — it is Compose's SYSTEM face, so the same CSS
        // `font-family` produced two different primary faces for marker and
        // item, and hence two different natural line boxes on the row.
        // css-lists-3 §3.2 makes the marker inherit from its originating
        // element, so it must land on the item's face, not the platform's.
        val marker = ListMarkerTextStyle.forItem(TextStyle())
        assertEquals(
            TextStyle(fontFamily = com.styleconverter.runtime.typography.InterFontFamily),
            marker)
    }

    @Test
    fun `a declared family beats the Inter bottom-out`() {
        // The bottom-out is only for the UNDECLARED case. An author
        // `font-family` is "Inherited: yes" (css-fonts-4 §1.1) and reaches
        // the marker through the container's merged style — substituting
        // Inter for it would be a silent override, not a fallback.
        val marker = ListMarkerTextStyle.forItem(itemStyle)
        assertEquals(FontFamily.Serif, marker.fontFamily)
    }

    @Test
    fun `a relative font-size needs the inherited base threaded through`() {
        // SKEPTIC REGRESSION PIN (wave 27). The marker's style is resolved
        // by ComponentRenderer through TextStyleApplier.extractTextStyle,
        // whose `inheritedFontSizeSp` parameter is what every RELATIVE
        // font-size resolves against (css-values-4 §5.1.1 — `em` is a
        // multiple of the INHERITED size; the wire leaves it unresolved on
        // purpose, CLAUDE.md's "null means runtime-dependent").
        //
        // The marker call site originally omitted that argument while the
        // container's own text passed it, so on a `font-size: 2em`
        // container the marker painted at 2 × the extractor's hard-coded
        // 16sp default and the text at 2 × the real inherited size. This
        // asserts the two bases genuinely produce different fonts, so a
        // future edit that drops the argument again cannot be silent.
        // `font-size: 120%` — deliberately a PERCENTAGE and not `em`:
        // DynamicValueResolver pre-resolves em lengths to px earlier in
        // the renderer pipeline, but percentages (and the `larger` /
        // `smaller` keywords) reach the extractor unresolved, so this is
        // the shape that actually diverged in the product path.
        fun props(json: String) = listOf(
            com.styleconverter.runtime.core.ir.IRProperty(
                type = "FontSize",
                data = kotlinx.serialization.json.Json.parseToJsonElement(json)))
        val pct120 = props("""{"original":{"type":"percentage","value":120}}""")
        val atDefaultBase = com.styleconverter.runtime.typography.TextStyleApplier
            .extractTextStyle(pct120)
        val atInheritedBase = com.styleconverter.runtime.typography.TextStyleApplier
            .extractTextStyle(pct120, inheritedFontSizeSp = 25f)
        assertEquals(19.2f, atDefaultBase.fontSize.value, 0.01f)  // 1.2 × 16sp
        assertEquals(30.0f, atInheritedBase.fontSize.value, 0.01f) // 1.2 × 25sp
        assertNotEquals(atDefaultBase.fontSize, atInheritedBase.fontSize)
    }
}
