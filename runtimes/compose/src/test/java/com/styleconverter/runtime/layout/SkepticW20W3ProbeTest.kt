package com.styleconverter.runtime.layout

// Wave-20 W3 skeptic pins (adversarial pass) — edge cases the lane
// suites left unpinned: layoutEnd row-mirroring with UNEQUAL row
// extents, wrong-side clear markers between mixed-side runs, the rtl
// mapping of the LOGICAL clear keywords, the inline-flow exact-fit
// wrap boundary, the zero-width-atom cursor quirk, and kebab-case wire
// keyword folding. Same inputs executed verbatim on iOS in
// SkepticW20W3Probes.swift; values must match exactly (byte-parallel
// twin-suite discipline, P10-P18).

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkepticW20W3ProbeTest {

    private fun kw(type: String, keyword: String) =
        IRProperty(type, Json.parseToJsonElement("\"$keyword\""))

    private fun cfg(vararg props: IRProperty): FloatConfig =
        FloatExtractor.extractFloatConfig(props.map { it.type to it.data })

    // Probe A — layoutEnd with UNEQUAL row extents: rows must right-align
    // at the RUN extent (60), not their own row extent.
    @Test
    fun probeA_layoutEnd_unequal_rows() {
        val p = FloatRowPacking.layoutEnd(
            widths = listOf(30.0, 30.0, 50.0),
            heights = listOf(10.0, 20.0, 5.0),
            availableWidth = 70.0,
            strutPx = 0.0,
        )
        assertEquals(listOf(30.0, 0.0, 10.0), p.x)
        assertEquals(listOf(0.0, 0.0, 20.0), p.y)
        assertEquals(60.0, p.width, 0.0)
        assertEquals(25.0, p.height, 0.0)
    }

    // Probe B — mixed sides with WRONG-side clear markers between: neither
    // run struts (same-side rule), segment shape [run,single,run,single,single].
    @Test
    fun probeB_mixed_sides_wrong_side_clears() {
        val F = FloatRowPacking.ChildFacts(floatsLeft = true, clearBreaksLeft = false)
        val R = FloatRowPacking.ChildFacts(floatsLeft = false, clearBreaksLeft = false, floatsRight = true)
        val BR = FloatRowPacking.ChildFacts(floatsLeft = false, clearBreaksLeft = true)
        val BRR = FloatRowPacking.ChildFacts(floatsLeft = false, clearBreaksLeft = false, clearBreaksRight = true)
        val segs = FloatRowPacking.segment(listOf(F, F, BRR, R, R, BR, F))
        assertEquals(5, segs.size)
        assertTrue(segs[0].isRun); assertFalse(segs[0].rightRun); assertFalse(segs[0].strutted)
        assertFalse(segs[1].isRun)
        assertTrue(segs[2].isRun); assertTrue(segs[2].rightRun); assertFalse(segs[2].strutted)
        assertFalse(segs[3].isRun)
        assertFalse(segs[4].isRun)
    }

    // Probe C — rtl CLEAR mapping (untested by the lane): under rtl,
    // clear inline-start breaks RIGHT runs, clear inline-end breaks LEFT.
    @Test
    fun probeC_rtl_clear_logical_mapping() {
        val cs = FloatRowPacking.facts(cfg(kw("Clear", "INLINE_START")), false, rtl = true)
        assertTrue(cs.clearBreaksRight); assertFalse(cs.clearBreaksLeft)
        val ce = FloatRowPacking.facts(cfg(kw("Clear", "INLINE_END")), false, rtl = true)
        assertTrue(ce.clearBreaksLeft); assertFalse(ce.clearBreaksRight)
    }

    // Probe D — inline flow exact-fit boundary: an atom ending exactly at
    // availableWidth does NOT wrap (strict >).
    @Test
    fun probeD_inline_exact_fit() {
        val p = InlineAtomFlow.layout(
            widths = listOf(100.0, 100.0),
            heights = listOf(10.0, 10.0),
            descents = listOf(0.0, 0.0),
            marginStarts = listOf(0.0, 0.0),
            marginEnds = listOf(0.0, 0.0),
            availableWidth = 204.0,
            gapPx = 4.0,
        )
        assertEquals(listOf(0.0, 104.0), p.x)
        assertEquals(listOf(0.0, 0.0), p.y)
        assertEquals(204.0, p.width, 0.0)
        // One px less wraps.
        val q = InlineAtomFlow.layout(
            widths = listOf(100.0, 100.0),
            heights = listOf(10.0, 10.0),
            descents = listOf(0.0, 0.0),
            marginStarts = listOf(0.0, 0.0),
            marginEnds = listOf(0.0, 0.0),
            availableWidth = 203.0,
            gapPx = 4.0,
        )
        assertEquals(listOf(0.0, 0.0), q.x)
        assertEquals(listOf(0.0, 10.0), q.y)
    }

    // Probe E — zero-width leading atom: cursor stays 0 so the next atom
    // still counts as row-first (no gap). Quirk pinned for parity.
    @Test
    fun probeE_zero_width_atom() {
        val p = InlineAtomFlow.layout(
            widths = listOf(0.0, 50.0),
            heights = listOf(10.0, 10.0),
            descents = listOf(0.0, 0.0),
            marginStarts = listOf(0.0, 0.0),
            marginEnds = listOf(0.0, 0.0),
            availableWidth = 500.0,
            gapPx = 4.0,
        )
        assertEquals(listOf(0.0, 0.0), p.x)
    }

    // Probe F — renderer: text-run atom next to a widget (a text-only <a>
    // beside an input) forms a run; rtl container + Float RIGHT keeps
    // rightRun (physical keyword direction-independent through the seam).
    @Test
    fun probeF_renderer_seams() {
        val plan = ComponentRenderer.blockInlineAtomSegments(
            listOf(
                IRComponent("a", "a", emptyList(), _text = "link", _tag = "a"),
                IRComponent("i", "i", emptyList(), _tag = "input"),
            )
        )
        assertTrue(plan!![0].isRun)
        val rtlPlan = ComponentRenderer.blockFloatSegments(
            listOf(
                IRComponent("x", "x", listOf(kw("Float", "RIGHT"))),
                IRComponent("y", "y", listOf(kw("Float", "RIGHT"))),
            ),
            containerProperties = listOf(kw("Direction", "RTL")),
        )
        assertTrue(rtlPlan!![0].isRun && rtlPlan[0].rightRun)
    }

    // Probe G — kebab-case wire keywords (the converter emits SHOUTY, but
    // the seam claims kebab folds identically on both natives).
    @Test
    fun probeG_kebab_wire() {
        val f = FloatRowPacking.facts(cfg(kw("Float", "inline-end")), false)
        assertTrue(f.floatsRight)
        val c = FloatRowPacking.facts(cfg(kw("Clear", "inline-end")), false)
        assertTrue(c.clearBreaksRight)
    }
}
