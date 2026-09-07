package com.styleconverter.runtime.borders

// Applier campaign — pinning tests for the three skeptic-confirmed
// border-image fixes on Android:
//
//  1. PADDING COMPENSATION: BorderImageBox's drawBehind sits after the
//     component's full modifier chain, whose LayoutFacade padding shrinks
//     the DrawScope box just like StyleApplier.borderContentInset does.
//     The dest expansion must therefore re-add outset + computedBorder +
//     resolvedPadding per side (destExpansion), with the padding plumbed
//     into BorderImageConfig by the extractor mirroring PaddingApplier's
//     resolution. Repro: padding 20px + border 4px on a 200px box → the
//     dest must span the full 200px (24px expansion per side + outset).
//  2. NO CONTENT INSET for border-image-width (css-backgrounds-3 §5:
//     border-image properties do not affect layout — content is inset by
//     border-width ONLY; Chromium keeps a 10px inset for border 10px +
//     border-image-width 15px). extraContentInset was deleted — its old
//     pins in BorderImageOutsetTest / BorderFidelityWave3Test are gone.
//  3. §6.1 PROPORTIONAL SLICE REDUCTION: opposing slices summing past the
//     image extent scale by extent/sum (mirrors iOS BorderImageMath
//     .nineGrid), fixing the latent negative-srcSize crash at slice ≥ 50%.

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.borders.image.BorderImageApplier
import com.styleconverter.runtime.borders.image.BorderImageExtractor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BorderImageSpecFixesTest {

    // Helper: parse a JSON string into a JsonElement (same style as
    // BorderExtractorsTest so IR shapes stay copy-pasteable from fixtures).
    private fun parse(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    // ── 1. Dest expansion = outset + computed border + resolved padding ──

    @Test
    fun `dest expansion sums outset border and padding`() {
        // The skeptic repro's per-side number: padding 20px + border 4px +
        // outset 0 → 24px. Each component contributes linearly because the
        // modifier chain inset each of them linearly before the drawBehind.
        assertEquals(24f, BorderImageApplier.destExpansion(0f, 4f, 20f), 0f)
        // With a 5px outset the §6.4 growth stacks on top: 29px.
        assertEquals(29f, BorderImageApplier.destExpansion(5f, 4f, 20f), 0f)
        // No padding degenerates to the pre-fix outset+border behaviour.
        assertEquals(4f, BorderImageApplier.destExpansion(0f, 4f, 0f), 0f)
    }

    @Test
    fun `padded 200px box reconstructs the full border box`() {
        // End-to-end geometry of the repro: a 200px box with padding 20px
        // and border 4px leaves a 152px DrawScope content box (200 − 2·24).
        val contentBox = 200f - 2 * 24f
        // Expansion per side re-adds exactly what the chain inset.
        val e = BorderImageApplier.destExpansion(0f, 4f, 20f)
        // Feeding the expansion through the outset dest-rect math must
        // recover the ORIGINAL 200px border box: origin (−24,−24), extent
        // 200 on both axes — the frame hugs the component perimeter again
        // instead of floating 24px inside it.
        val dest = BorderImageApplier.outsetDestRect(contentBox, contentBox, e, e, e, e)
        assertEquals(Rect(-24f, -24f, 176f, 176f), dest)
        assertEquals(200f, dest.width, 0f)
        assertEquals(200f, dest.height, 0f)
    }

    // ── 1b. Extractor plumbs resolved padding into the config ────────────

    @Test
    fun `extractor resolves physical padding px into the config`() {
        // Four distinct px paddings (raw {"px": N} IR shape, the same one
        // PaddingExtractor sees) must land on their own config side so the
        // drawBehind can undo the per-side layout inset exactly.
        val config = BorderImageExtractor.extractBorderImageConfig(
            listOf(
                pair("PaddingTop", """{"px": 20}"""),
                pair("PaddingRight", """{"px": 10}"""),
                pair("PaddingBottom", """{"px": 5}"""),
                pair("PaddingLeft", """{"px": 2}""")
            )
        )
        assertEquals(20.dp, config.resolvedPaddingTop)
        assertEquals(10.dp, config.resolvedPaddingRight)
        assertEquals(5.dp, config.resolvedPaddingBottom)
        assertEquals(2.dp, config.resolvedPaddingLeft)
    }

    @Test
    fun `extractor collapses logical padding with the LTR default`() {
        // PaddingApplier resolves logical→physical with isRtl = false (the
        // renderer doesn't plumb LayoutDirection yet); the extractor must
        // use the SAME collapse or the compensation would miss logical
        // longhands: inline-start → left, inline-end → right in LTR.
        val config = BorderImageExtractor.extractBorderImageConfig(
            listOf(
                pair("PaddingInlineStart", """{"px": 7}"""),
                pair("PaddingInlineEnd", """{"px": 3}""")
            )
        )
        assertEquals(7.dp, config.resolvedPaddingLeft)
        assertEquals(3.dp, config.resolvedPaddingRight)
    }

    @Test
    fun `extractor resolves percent padding with the layout default context`() {
        // Bare-number IR shape = percent (see extractLength). The layout
        // chain resolves % with the DEFAULT SpacingContext (no parent width
        // → viewport 390px fallback), so the compensation must match:
        // 10% → 39px. Pins the "same context as PaddingApplier" contract.
        val config = BorderImageExtractor.extractBorderImageConfig(
            listOf(pair("PaddingTop", "10.0"))
        )
        assertEquals(39.dp, config.resolvedPaddingTop)
    }

    @Test
    fun `extractor clamps negative padding to zero like the applier`() {
        // CSS padding is never negative (spec clamp); PaddingApplier
        // coerces at 0 before insetting, so a negative value was never
        // applied by layout and must not be "compensated" either.
        val config = BorderImageExtractor.extractBorderImageConfig(
            listOf(pair("PaddingTop", """{"px": -5}"""))
        )
        assertEquals(0.dp, config.resolvedPaddingTop)
        // Absent padding stays the 0 default — nothing to undo.
        assertEquals(0.dp, config.resolvedPaddingBottom)
    }

    // ── 3. §6.1 proportional slice reduction ─────────────────────────────

    @Test
    fun `slice 60 percent on a 30px image reduces both sides to 15`() {
        // The skeptic degenerate case: 60% of 30 = 18 per side, 18+18 = 36
        // > 30 → §6.1 factor 30/36 → both sides land on exactly 15 (both
        // axes, square image). Matches iOS nineGrid's overlap branch.
        val s = BorderImageApplier.reduceSlices(18f, 18f, 18f, 18f, 30f, 30f)
        assertEquals(15f, s.top, 1e-4f)
        assertEquals(15f, s.right, 1e-4f)
        assertEquals(15f, s.bottom, 1e-4f)
        assertEquals(15f, s.left, 1e-4f)
    }

    @Test
    fun `slices at or below half the extent pass through unchanged`() {
        // 50% is the boundary: 15+15 = 30 = extent → NO reduction (§6.1
        // only fires when the sum is LARGER than the extent).
        val s = BorderImageApplier.reduceSlices(15f, 15f, 15f, 15f, 30f, 30f)
        assertEquals(15f, s.top, 0f)
        assertEquals(15f, s.left, 0f)
        // Asymmetric sub-half slices are untouched per side.
        val a = BorderImageApplier.reduceSlices(2f, 4f, 6f, 8f, 30f, 30f)
        assertEquals(2f, a.top, 0f)
        assertEquals(4f, a.right, 0f)
        assertEquals(6f, a.bottom, 0f)
        assertEquals(8f, a.left, 0f)
    }

    @Test
    fun `axes reduce independently`() {
        // Only the horizontal pair overlaps (20+20 > 30): left/right scale
        // by 30/40 while the compliant vertical pair keeps its values —
        // §6.1 treats the two axes as separate constraints.
        val s = BorderImageApplier.reduceSlices(5f, 20f, 5f, 20f, 30f, 30f)
        assertEquals(15f, s.right, 1e-4f)
        assertEquals(15f, s.left, 1e-4f)
        assertEquals(5f, s.top, 0f)
        assertEquals(5f, s.bottom, 0f)
    }

    @Test
    fun `no slice percentage from 0 to 100 yields negative source extents`() {
        // The crash the reduction exists to prevent: for EVERY slice in
        // 0..100% on a 30×30 image, all reduced offsets are ≥ 0 and the
        // center extents (extent − opposing sum) never go negative — i.e.
        // no srcSize passed to drawImage can be negative any more.
        for (pct in 0..100) {
            // Percentage slices resolve against the image extent (§6.1).
            val px = pct / 100f * 30f
            val s = BorderImageApplier.reduceSlices(px, px, px, px, 30f, 30f)
            // Offsets themselves stay non-negative.
            assertTrue("slice $pct%: negative offset", s.top >= 0f && s.right >= 0f && s.bottom >= 0f && s.left >= 0f)
            // Center column/row widths stay non-negative (tiny float slack).
            assertTrue("slice $pct%: negative center width", 30f - s.left - s.right >= -1e-3f)
            assertTrue("slice $pct%: negative center height", 30f - s.top - s.bottom >= -1e-3f)
        }
    }

    @Test
    fun `negative slice inputs clamp to zero before reduction`() {
        // Negative slices are invalid per the §6.1 grammar — clamp first
        // (mirrors iOS slicePx) so a bad IR value can't grow the opposite
        // side through the proportional factor.
        val s = BorderImageApplier.reduceSlices(-5f, 10f, -5f, 10f, 30f, 30f)
        assertEquals(0f, s.top, 0f)
        assertEquals(0f, s.bottom, 0f)
        assertEquals(10f, s.right, 0f)
        assertEquals(10f, s.left, 0f)
    }
}
