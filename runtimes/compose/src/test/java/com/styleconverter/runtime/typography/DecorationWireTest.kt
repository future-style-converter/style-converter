package com.styleconverter.runtime.typography

// DecorationWire pin table — applier campaign wave 22, lane DECOR.
//
// TWIN of the iOS suite DecorationWireTests.swift — SAME cases, SAME
// expected values. Change one, change both.
//
// This suite pins the LAST hop of the meta.decorations seam:
//   extractor `_decorations` → converter `meta.decorations` →
//   IRDocumentDecoder → IRComponent.decorations ([IRDecoration], raw
//   wire strings) → HERE → DecorationColorOps.resolve → bands → ops.
//
// The live artifact these expectations track is the converted
// re-extracted fixture (`./gradlew :converter:run --args="convert --from
// css --to ir -i fixtures/wpt/css-text-decor/text-decoration-color.json
// -o out"`), component `text-decoration-color__7-008`:
//   meta.decorations = [ {line:"underline",   color:"blue"},
//                        {line:"overline",    color:"gray"},
//                        {line:"line-through",color:"green"} ]
// Colour tokens are AUTHORED — this file is where they become sRGB.

import com.styleconverter.runtime.core.ir.IRDecoration
import com.styleconverter.runtime.typography.DecorationColorOps.DecorationLine
import com.styleconverter.runtime.typography.DecorationColorOps.LineKind
import com.styleconverter.runtime.typography.DecorationColorOps.Rgba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecorationWireTest {

    /** css `gray` = 128/255 in all three channels (capture row 219). */
    private val gray = Rgba(128 / 255f, 128 / 255f, 128 / 255f, 1f)

    /** css `green` = #008000, NOT lime (capture row 230). */
    private val green = Rgba(0f, 128 / 255f, 0f, 1f)

    /** css `blue` = #0000ff (capture row 237). */
    private val blue = Rgba(0f, 0f, 1f, 1f)

    // ---- the live wire, end to end ----

    @Test
    fun `the live three-colour chain resolves to the measured sRGB rows`() {
        // Exactly the bytes the converter emits for
        // text-decoration-color__7-008 (order = outermost-first).
        val lines = DecorationWire.toDecorationLines(
            listOf(
                IRDecoration("underline", "blue"),
                IRDecoration("overline", "gray"),
                IRDecoration("line-through", "green")
            )
        )
        assertEquals(
            listOf(
                DecorationLine(LineKind.UNDERLINE, blue),
                DecorationLine(LineKind.OVERLINE, gray),
                DecorationLine(LineKind.LINE_THROUGH, green)
            ),
            lines
        )
        // …and those requests land on the capture's measured rows in the
        // capture's measured colours (the full painter contract).
        val bands = DecorationColorOps.bands(
            lineCount = 1, fontSizePx = 16f, autoThicknessPx = 1f, explicitThicknessPx = null,
            lines = DecorationColorOps.resolve(lines, false, false, true),
            lineBaseline = { 236f }, lineLeft = { 0f }, lineRight = { 74f }
        )
        assertEquals(listOf(237f, 219f, 230f), bands.map { it.top })
        assertEquals(listOf(blue, gray, green), bands.map { it.color })
    }

    // ---- absence vs emptiness ----

    @Test
    fun `absent stays absent and empty stays empty`() {
        // null → null: not a collapsed run, the painter synthesizes the
        // component's own flags (the legacy byte-identity path).
        assertNull(DecorationWire.toDecorationLines(null))
        // An empty list is a DIFFERENT state and must not be upgraded to
        // "absent" — it is authoritative and paints nothing.
        assertEquals(emptyList<DecorationLine>(), DecorationWire.toDecorationLines(emptyList()))
    }

    @Test
    fun `a wire of only unknown keywords empties WITHOUT becoming absent`() {
        // The contract hole this seam closes: every entry drops, the list
        // survives as EMPTY, and resolve() then paints nothing instead of
        // falling back to the merged flat bag's flags.
        val lines = DecorationWire.toDecorationLines(
            listOf(IRDecoration("blink"), IRDecoration("grand-underline", "blue"))
        )
        assertEquals(emptyList<DecorationLine>(), lines)
        assertTrue(
            "a fully-filtered wire is still authoritative",
            DecorationColorOps.resolve(lines, underline = true, overline = true, lineThrough = true)
                .isEmpty()
        )
    }

    // ---- the two drop rules ----

    @Test
    fun `an unknown keyword drops its entry and keeps the rest in order`() {
        assertEquals(
            listOf(
                DecorationLine(LineKind.UNDERLINE, blue),
                DecorationLine(LineKind.LINE_THROUGH, null)
            ),
            DecorationWire.toDecorationLines(
                listOf(
                    IRDecoration("underline", "blue"),
                    IRDecoration("blink", "gray"),      // §2.1 grammar, paints nothing
                    IRDecoration("line-through")         // no colour = currentColor
                )
            )
        )
    }

    @Test
    fun `an unresolvable colour token degrades to currentColor, never to a dropped line`() {
        // oklch() is outside the runtime token parser's families (the
        // converter would normally pre-resolve it as a DECLARATION, but a
        // meta hint is forwarded verbatim). The LINE must survive.
        val lines = DecorationWire.toDecorationLines(
            listOf(IRDecoration("underline", "oklch(0.7 0.1 200)"))
        )
        assertEquals(listOf(DecorationLine(LineKind.UNDERLINE, null)), lines)
    }

    @Test
    fun `hex and keyword tokens both resolve through the runtime parser`() {
        assertEquals(
            listOf(
                DecorationLine(LineKind.UNDERLINE, blue),        // #00f short form
                DecorationLine(LineKind.OVERLINE, green),        // #008000 long form
                DecorationLine(LineKind.LINE_THROUGH, Rgba(0f, 0f, 0f, 0f)) // transparent
            ),
            DecorationWire.toDecorationLines(
                listOf(
                    IRDecoration("underline", "#00f"),
                    IRDecoration("overline", "#008000"),
                    IRDecoration("line-through", "transparent")
                )
            )
        )
    }

    @Test
    fun `keyword case is normalized on both channels`() {
        // CSS keywords are ASCII case-insensitive (css-values-4 §4.1), and
        // the IR's screaming line spelling must keep working too.
        assertEquals(
            listOf(DecorationLine(LineKind.LINE_THROUGH, blue)),
            DecorationWire.toDecorationLines(listOf(IRDecoration("LINE_THROUGH", "BLUE")))
        )
    }
}
