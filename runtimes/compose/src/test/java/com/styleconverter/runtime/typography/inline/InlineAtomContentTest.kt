package com.styleconverter.runtime.typography.inline

// Wave 45 (lane X2) — JVM pins for the ATOM RING's Compose-facing half:
// the marker→InlineTextContent annotation rebuild and the Placeholder
// geometry contract. Both are plain object construction (AnnotatedString
// building and foundation's appendInlineContent are android-free), so the
// suite pins them without a device; only the actual rasterisation
// (RingBox's drawBehind) is left to the composed capture gate.

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineAtomContentTest {

    /** The inherit-computed-001 ring: four inherited 3px solid sides. */
    private fun emRingSpec(): InlineAtomRing.Spec {
        val side = InlineAtomRing.Side(3f, "SOLID", InlineAtomRing.Paint.CurrentColor)
        return InlineAtomRing.Spec(side, side, side, side)
    }

    @Test
    fun `annotate - each marker becomes exactly one inline-content slot, text preserved`() {
        // The verbatim fold output for inherit-computed-001 (see
        // InlineRunFoldTest): one marker between "size " and "and".
        val source = AnnotatedString("This line is all in one font size \uFFFCand there is no red.")
        val annotated = InlineAtomContent.annotate(source, listOf("x2atom:0"))
        // The visible string is unchanged — the slot's alternate text is
        // the marker char itself, so downstream offsets keep their meaning.
        assertEquals(source.text, annotated.text)
        // Exactly one annotation covers the marker: foundation's
        // inline-content tag, carrying the atom id. The tag-agnostic
        // range read keeps this pin independent of the INTERNAL tag
        // constant appendInlineContent plants.
        val at = source.text.indexOf(InlineAtomRing.MARKER)
        val overMarker = annotated.getStringAnnotations(at, at + 1)
        assertEquals(1, overMarker.size)
        assertEquals("x2atom:0", overMarker[0].item)
        assertEquals(at, overMarker[0].start)
        assertEquals(at + 1, overMarker[0].end)
        // And nothing bleeds outside the marker range.
        assertTrue(annotated.getStringAnnotations(0, at).isEmpty())
        assertTrue(annotated.getStringAnnotations(at + 1, source.text.length).isEmpty())
    }

    @Test
    fun `annotate - two markers bind ids in marker order`() {
        // Two atoms: the k-th marker must take ids[k] — the fold emits
        // atoms in marker order and contentMap preserves it (LinkedHashMap).
        val source = AnnotatedString("a\uFFFCb\uFFFCc")
        val annotated = InlineAtomContent.annotate(source, listOf("x2atom:0", "x2atom:1"))
        assertEquals("x2atom:0", annotated.getStringAnnotations(1, 2)[0].item)
        assertEquals("x2atom:1", annotated.getStringAnnotations(3, 4)[0].item)
    }

    @Test
    fun `annotate - a count mismatch returns the source untouched (defensive)`() {
        // Cannot happen through the fold (it bails on source U+FFFC), but
        // a defensive caller must never mis-bind: identity instead.
        val source = AnnotatedString("a\uFFFCb")
        assertEquals(source, InlineAtomContent.annotate(source, listOf("x", "y")))
        assertEquals(source, InlineAtomContent.annotate(source, emptyList()))
    }

    @Test
    fun `contentMap - placeholder reserves the ring advance inside the text band`() {
        // inherit-computed-001 at the host's resolved 19.2px: the entry
        // reserves the ring's 6px ADVANCE (px == sp, density-1 harness)
        // but only a font-size-tall box — the ring's 29.23px height is
        // DRAWN (RingBox), never reserved, because a taller placeholder
        // would grow the 24px line box the way a CSS inline border must
        // not (CSS 2.1 §10.8).
        val atoms = listOf(InlineRunFold.Atom(memberIndex = 1, spec = emRingSpec()))
        val map = InlineAtomContent.contentMap(atoms, fontSizePx = 19.2f, currentColor = androidx.compose.ui.graphics.Color.Black)
        assertEquals(setOf("x2atom:0"), map.keys)
        val placeholder = map.getValue("x2atom:0").placeholder
        assertEquals(6f.sp, placeholder.width)
        assertEquals(19.2f.sp, placeholder.height)
        // Centered on the surrounding text's font band — the empty inline's
        // border-box is that band ± symmetric borders, so centers coincide.
        assertEquals(PlaceholderVerticalAlign.TextCenter, placeholder.placeholderVerticalAlign)
    }

    @Test
    fun `ring bands - the em ring is four solid bands forming a closed 6x29 ring`() {
        // The pure band math RingBox draws — pinned at the measured ref
        // geometry (wave44-final: ring x=289..294, y=36..64 → 6×29.23).
        val bands = InlineAtomRing.bands(emRingSpec(), fontSizePx = 19.2f)
        assertEquals(4, bands.size)
        val (ringW, ringH) = InlineAtomRing.ringSizePx(emRingSpec(), 19.2f)
        // Top band spans the full ring width at the top edge.
        assertEquals(InlineAtomRing.Band(0f, 0f, ringW, 3f, InlineAtomRing.Paint.CurrentColor), bands[0])
        // Bottom band anchors at the bottom edge.
        assertEquals(0f, bands[1].xPx)
        assertEquals(ringH - 3f, bands[1].yPx)
        // Left/right bands fill the rows between the horizontal bands.
        assertEquals(InlineAtomRing.Band(0f, 3f, 3f, ringH - 6f, InlineAtomRing.Paint.CurrentColor), bands[2])
        assertEquals(ringW - 3f, bands[3].xPx)
        // With 3px left + 3px right and zero content, the bands tile the
        // full 6px width — the ref's solid black column.
        assertEquals(3f, bands[2].widthPx)
        assertEquals(3f, bands[3].widthPx)
    }
}
