package com.styleconverter.runtime.layout

// Wave 33 (lane C, C2) — JVM pins for the declared-`display:inline-block`
// atom predicate (InlineBlockAtom.spec) and for the §9.4.2 row it feeds
// through the wave-20 packer. Every assertion here is mirrored VERBATIM in
// InlineBlockAtomTests.swift so the two natives cannot drift.
//
// Measured defect (see InlineBlockAtom's header): declared inline-block
// siblings that belong on one line box were block-STACKED by both natives'
// block child loop. On device the gate admits exactly ONE corpus site —
// filter-effects/backdrop-filter-boundary, Android 0.6130 → 0.8962 — and
// CSS2/abspos/static-inside-inline-block stays UNFIXED because its three
// boxes are document ROOTS, stacked by the harness, not by this loop.

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InlineBlockAtomTest {

    // ───────────────────────── B1 — the admission ticket ─────────────────

    @Test fun `B1 - a declared inline-block with a definite size is an atom`() {
        val spec = InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0), false, false, false)
        assertNotNull(spec)
        assertEquals(100.0, spec!!.fixedWpx!!, 0.0)
        assertEquals(100.0, spec.fixedHpx!!, 0.0)
    }

    @Test fun `B1 - block inline flex grid and table displays are not atoms`() {
        for (kw in listOf("BLOCK", "INLINE", "FLEX", "GRID", "TABLE", "LIST_ITEM", "NONE")) {
            assertNull(kw, InlineBlockAtom.spec(box(kw, 100.0, 100.0), false, false, false))
        }
    }

    @Test fun `B1 - an UNDECLARED display is the wave-20 widget lane not this one`() {
        // A bare <input> is inline-block by UA default; UAWidgetIntrinsics
        // owns its geometry, and racing that table with a wire size the
        // component does not carry is exactly what B1 prevents.
        assertNull(InlineBlockAtom.spec(sized(100.0, 100.0), false, false, false))
    }

    // ───────────────────────── B2 — definite border box ──────────────────

    @Test fun `B2 - an auto or percentage or missing axis refuses`() {
        // Shrink-to-fit (§10.3.9) and content height (§10.6.6) are measure
        // questions this pure rule cannot answer.
        assertNull("no size", InlineBlockAtom.spec(prop("Display", "\"INLINE_BLOCK\""), false, false, false))
        assertNull(
            "percent width",
            InlineBlockAtom.spec(
                prop("Display", "\"INLINE_BLOCK\"") +
                    prop("Width", """{"type":"percentage","value":50}""") +
                    len("Height", 100.0),
                false, false, false,
            )
        )
        assertNull(
            "auto height",
            InlineBlockAtom.spec(
                prop("Display", "\"INLINE_BLOCK\"") +
                    len("Width", 100.0) +
                    prop("Height", "\"auto\""),
                false, false, false,
            )
        )
    }

    @Test fun `B2 - the logical InlineSize and BlockSize spellings resolve too`() {
        val spec = InlineBlockAtom.spec(
            prop("Display", "\"INLINE_BLOCK\"") +
                len("InlineSize", 60.0) +
                len("BlockSize", 30.0),
            false, false, false,
        )
        assertNotNull(spec)
        assertEquals(60.0, spec!!.fixedWpx!!, 0.0)
        assertEquals(30.0, spec.fixedHpx!!, 0.0)
    }

    // ───────────────────────── B3/B4 — flow membership ───────────────────

    @Test fun `B3 - an absolutely positioned or fixed box is not on the line`() {
        for (kw in listOf("ABSOLUTE", "FIXED")) {
            assertNull(
                kw,
                InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0) + prop("Position", "\"$kw\""), false, false, false)
            )
        }
    }

    @Test fun `B3 - relative and sticky stay in flow and remain atoms`() {
        for (kw in listOf("RELATIVE", "STICKY", "STATIC")) {
            assertNotNull(
                kw,
                InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0) + prop("Position", "\"$kw\""), false, false, false)
            )
        }
    }

    @Test fun `B4 - a float belongs to the wave-19 packer`() {
        assertNull(
            InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0) + prop("Float", "\"LEFT\""), false, false, false)
        )
        // An explicit `float: none` is the initial value — still an atom.
        assertNotNull(
            InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0) + prop("Float", "\"NONE\""), false, false, false)
        )
    }

    // ───────────────────────── B5 — bands must be zero ───────────────────

    @Test fun `B5 - a non-zero margin padding or border refuses`() {
        for (t in listOf("MarginLeft", "PaddingTop", "BorderRightWidth", "MarginInlineStart")) {
            assertNull(
                t,
                InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0) + len(t, 4.0), false, false, false)
            )
        }
    }

    @Test fun `B5 - explicit zero bands are fine - the extracted corpus shape`() {
        // backdrop-filter-clip-rect-2's boxes carry an explicit `padding:
        // 0` from the UA reset the extractor bakes in.
        val props = box("INLINE_BLOCK", 100.0, 100.0) +
            len("PaddingTop", 0.0) + len("PaddingLeft", 0.0) + len("MarginTop", 0.0)
        assertNotNull(InlineBlockAtom.spec(props, false, false, false))
    }

    // ───────────────────────── B6 — the baseline ─────────────────────────

    @Test fun `B6 - own text or runs move the baseline so the box refuses`() {
        assertNull("text", InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0), true, false, false))
        assertNull("runs", InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0), false, true, false))
    }

    @Test fun `B6 - a childless or abspos-child box baselines at its bottom edge`() {
        // §10.8.1: no in-flow line boxes ⇒ baseline == bottom margin edge
        // ⇒ descent 0, which is what leaves the ref's 4px strut descent
        // visible below a row of tall inline-blocks.
        assertEquals(0.0, InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0), false, false, false)!!.descentPx, 0.0)
    }

    // ───────────────────────── B7 — the container's line box ────────────

    @Test fun `B7 - a container that declares its own line-height refuses`() {
        // MEASURED on device: absolute-tables-013/-014/-015 put two 100x50
        // spans in a `line-height: 0` <td>, where the browser's line boxes
        // are 50 tall. Feeding them the harness's 16/4 strut moved the
        // second span 4px down and cost 0.0115/0.0163/0.0165 SSIM against
        // the frozen wave32-final Android baseline; refusing restores all
        // three byte-for-byte.
        assertNull(
            InlineBlockAtom.spec(
                box("INLINE_BLOCK", 100.0, 50.0), false, false,
                containerDeclaresLineHeight = true,
            )
        )
    }

    // ───────────────────────── the two corpus geometries ─────────────────

    @Test fun `three same-size boxes pack on one line inside the composed width`() {
        // The static-inside-inline-block geometry: three 100×100
        // inline-blocks, one collapsed space between each, in the 358px
        // composed content width. This pins the PACKER's answer; that test
        // itself is root-level and never reaches this lane (see the file
        // header's measured-outcome section).
        val spec = InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 100.0), false, false, false)!!
        val plan = InlineAtomFlow.layout(
            widths = List(3) { spec.fixedWpx!! },
            heights = List(3) { spec.fixedHpx!! },
            descents = List(3) { spec.descentPx },
            marginStarts = List(3) { 0.0 },
            marginEnds = List(3) { 0.0 },
            availableWidth = 358.0,
            gapPx = UAWidgetIntrinsics.ATOM_GAP_PX,
            strutAscentPx = UAWidgetIntrinsics.STRUT_ASCENT_PX,
            strutDescentPx = UAWidgetIntrinsics.STRUT_DESCENT_PX,
        )
        // One row: 3×100 + 2×4.5 = 309 ≤ 358.
        assertEquals(listOf(0.0, 104.5, 209.0), plan.x)
        assertEquals(listOf(0.0, 0.0, 0.0), plan.y)
        assertEquals(309.0, plan.width, 0.0)
        // Row height = ascent 100 (the boxes) + descent 4 (the §10.8.1
        // strut) — the browser's classic below-inline-block gap.
        assertEquals(104.0, plan.height, 0.0)
    }

    @Test fun `the absolute-tables-013 pair WRAPS inside its 100px cell`() {
        // Two 100×50 spans in a 100px-wide <td>: 100 + 4.5 + 100 > 100,
        // so the second wraps — the same visual stacking the frozen
        // baseline already had, one strut descent lower per line box.
        val spec = InlineBlockAtom.spec(box("INLINE_BLOCK", 100.0, 50.0), false, false, false)!!
        val plan = InlineAtomFlow.layout(
            widths = List(2) { spec.fixedWpx!! },
            heights = List(2) { spec.fixedHpx!! },
            descents = List(2) { spec.descentPx },
            marginStarts = List(2) { 0.0 },
            marginEnds = List(2) { 0.0 },
            availableWidth = 100.0,
            gapPx = UAWidgetIntrinsics.ATOM_GAP_PX,
            strutAscentPx = UAWidgetIntrinsics.STRUT_ASCENT_PX,
            strutDescentPx = UAWidgetIntrinsics.STRUT_DESCENT_PX,
        )
        assertEquals(listOf(0.0, 0.0), plan.x)
        // Line box 1 = ascent 50 + strut descent 4 ⇒ row 2 top at 54.
        assertEquals(listOf(0.0, 54.0), plan.y)
        assertEquals(108.0, plan.height, 0.0)
    }

    // ───────────────────────── wire helpers ──────────────────────────────

    private fun prop(type: String, json: String) = listOf(IRProperty(type, Json.parseToJsonElement(json)))

    private fun len(type: String, px: Double) =
        listOf(IRProperty(type, Json.parseToJsonElement("""{"type":"length","px":$px}""")))

    /** A declared-display box with an exact px width/height. */
    private fun box(display: String, w: Double, h: Double): List<IRProperty> =
        prop("Display", "\"$display\"") + len("Width", w) + len("Height", h)

    /** The same box WITHOUT any Display declaration (B1's UA-default case). */
    private fun sized(w: Double, h: Double): List<IRProperty> = len("Width", w) + len("Height", h)
}
