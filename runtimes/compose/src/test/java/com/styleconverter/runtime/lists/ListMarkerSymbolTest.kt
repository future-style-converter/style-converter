package com.styleconverter.runtime.lists

// Wave 30, lane 3 — pin table for ListMarkerSymbol (fix B6): the painted
// geometry of a disc / circle / square marker.
//
// ## The defect these pin against (measured on the LIVE wave29-final run)
// Ink extents of the `square` marker of
// wpt__css-lists__change-list-style-type-001 (`font-size: 16px`):
//
//   platform | marker ink                  | ratio vs web
//   web      | x 56–60, y 41–45  (5 × 5)   | 1.00
//   Android  | x 17–27, y 36–46  (11 × 11) | 2.20
//   iOS      | x 17–29, y 36–48  (13 × 13) | 2.60
//
// The natives paint the UA symbols as TEXT glyphs, so their size is a
// property of whichever fallback face resolved them — 2.2–2.6× the
// browser's painted shape, and not even equal to each other.
//
// TWIN of the iOS suite ListMarkerSymbolTests.swift.
//
// The Modifier itself needs a draw surface (androidTest); these cover the
// pure geometry and the baked-string guard.

import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ListMarkerSymbolTest {

    // ── shapeFor: which counter styles are PAINTED ──────────────────────

    @Test
    fun `the three UA symbols paint a shape`() {
        assertEquals(ListMarkerSymbol.Shape.FILLED_CIRCLE,
            ListMarkerSymbol.shapeFor(ListStyleType.DISC))
        assertEquals(ListMarkerSymbol.Shape.HOLLOW_CIRCLE,
            ListMarkerSymbol.shapeFor(ListStyleType.CIRCLE))
        assertEquals(ListMarkerSymbol.Shape.FILLED_SQUARE,
            ListMarkerSymbol.shapeFor(ListStyleType.SQUARE))
    }

    @Test
    fun `every numeric and alphabetic style keeps its text`() {
        for (type in listOf(
            ListStyleType.DECIMAL, ListStyleType.DECIMAL_LEADING_ZERO,
            ListStyleType.UPPER_ROMAN, ListStyleType.LOWER_ALPHA,
            ListStyleType.ARMENIAN, ListStyleType.HEBREW, ListStyleType.NONE
        )) {
            assertNull(type.name, ListMarkerSymbol.shapeFor(type))
        }
    }

    @Test
    fun `an unresolved custom counter style is never a shape`() {
        // The live `marker-text-matches-disc` / `-circle` fixtures declare
        // `list-style-type: my-disc` / `my-circle`, which the IR wire
        // cannot carry (no @counter-style rule), so
        // ListStyleExtractor.typeFromKeyword returns null and the `<ol>`
        // UA `decimal` stands. All three runtimes therefore paint "1." and
        // score 0.99 — this table must not reach them.
        assertNull(ListStyleExtractor.typeFromKeyword("my-disc"))
        assertNull(ListStyleExtractor.typeFromKeyword("my-circle"))
    }

    // ── the baked-marker guard ──────────────────────────────────────────

    @Test
    fun `a shape only replaces the ink of its OWN glyph`() {
        // A `meta.markerText` bake wins over the local table (wave 27), so
        // a producer may bake any string onto a disc item. Blanking that
        // string and drawing a circle would delete content the wire asked
        // for.
        assertEquals(ListMarkerSymbol.Shape.FILLED_CIRCLE,
            ListMarkerSymbol.shapeFor(ListStyleType.DISC, "•"))
        assertNull(ListMarkerSymbol.shapeFor(ListStyleType.DISC, "Ա."))
        assertNull(ListMarkerSymbol.shapeFor(ListStyleType.DISC, ""))
        // The other two symbols pair with their own glyph only.
        assertEquals(ListMarkerSymbol.Shape.FILLED_SQUARE,
            ListMarkerSymbol.shapeFor(ListStyleType.SQUARE, "■"))
        assertNull(ListMarkerSymbol.shapeFor(ListStyleType.SQUARE, "•"))
    }

    // ── the geometry ────────────────────────────────────────────────────

    @Test
    fun `the shape is 0_35em of the resolved font size`() {
        // 5.6px at the ref's 16px root, against Chromium's
        // `(ascent * 2 / 3 + 1) / 2` = 5.5 at ascent 15 — and against the
        // 5px of solid ink the reference actually rasterized.
        assertEquals(5.6f, ListMarkerSymbol.sizePx(16f), 0.001f)
        // Scales with the item, exactly as the glyph it replaces did: the
        // counter-styles corpus inherits `font-size: 25px`.
        assertEquals(8.75f, ListMarkerSymbol.sizePx(25f), 0.001f)
    }

    @Test
    fun `an unresolved font size paints nothing rather than an inverted rect`() {
        assertEquals(0f, ListMarkerSymbol.sizePx(0f), 0f)
        assertEquals(0f, ListMarkerSymbol.sizePx(-4f), 0f)
        assertEquals(0f, ListMarkerSymbol.strokePx(0f), 0f)
    }

    @Test
    fun `the hollow circle strokes at one device pixel of the ref root`() {
        assertEquals(1f, ListMarkerSymbol.strokePx(16f), 0.001f)
    }

    @Test
    fun `the shape is vertically centred in the marker box`() {
        // The reference `square` occupies rows 41–45 of a line box
        // spanning 33–53: centre 43, shape centre 43. At a 20px box and a
        // 5.6px shape that is a 7.2px top inset.
        assertEquals(7.2f, ListMarkerSymbol.topPx(20f, 16f), 0.001f)
        // A box SHORTER than the shape clamps at the top instead of
        // painting above its own origin.
        assertEquals(0f, ListMarkerSymbol.topPx(4f, 16f), 0f)
    }

    // ── paint(): when the ink is replaced at all ────────────────────────

    @Test
    fun `paint is a no-op for everything but the three symbols`() {
        assertSame(Modifier,
            ListMarkerSymbol.paint(ListStyleType.DECIMAL, "1.", 16f,
                androidx.compose.ui.graphics.Color.Black))
        // …including a symbol style whose string was baked to something
        // else, and a symbol with no resolvable size.
        assertSame(Modifier,
            ListMarkerSymbol.paint(ListStyleType.DISC, "Ա.", 16f,
                androidx.compose.ui.graphics.Color.Black))
        assertSame(Modifier,
            ListMarkerSymbol.paint(ListStyleType.DISC, "•", 0f,
                androidx.compose.ui.graphics.Color.Black))
        // …and IS a real modifier for the case it exists for.
        assertNotSame(Modifier,
            ListMarkerSymbol.paint(ListStyleType.DISC, "•", 16f,
                androidx.compose.ui.graphics.Color.Black))
    }
}
