package com.styleconverter.runtime.layout

// Wave-19 lane FLOAT — JVM pins for the pure float-row-packing twins
// (FloatRowPacking.kt ↔ iOS FloatRowPacking.swift). Every pinned value
// here is mirrored VERBATIM in FloatRowPackingTests.swift so a skeptic
// probe can diff the two suites; the shared table is the lane pin table
// (P1-P6). Corpus geometry comes from the two failing WPT fixtures:
//   • css-flexbox flex-abspos-staticpos-justify-self-001 — 14 left
//     floats (margin box 27×19) split 3/2/5/4 by `<br clear:both>`
//     siblings; the ref rows advance 20px (br line-box strut).
//   • css-grid descendant-static-position-001 — a green+grey 20×40 pair
//     inside a shrink-to-fit abspos box (un-strutted, unbounded).

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatRowPackingTest {

    // IR keyword wire shape — bare JSON string, exactly what the live
    // per-test IR carries ({"type":"Float","data":"LEFT"}).
    private fun kw(type: String, keyword: String) =
        IRProperty(type, Json.parseToJsonElement("\"$keyword\""))

    // FloatConfig via the production extractor — facts() consumes the
    // typed config, so tests go through the same single parsing owner.
    private fun cfg(vararg props: IRProperty): FloatConfig =
        FloatExtractor.extractFloatConfig(props.map { it.type to it.data })

    // ── P1/P2 — facts derivation ──

    @Test
    fun `left and inline-start floats pack, right floats do not`() {
        // P1: the LTR-normalized engine folds inline-start onto left.
        assertTrue(FloatRowPacking.facts(cfg(kw("Float", "LEFT")), false).floatsLeft)
        assertTrue(FloatRowPacking.facts(cfg(kw("Float", "INLINE_START")), false).floatsLeft)
        // Right-family floats keep the wave-5 end-alignment path (F4).
        assertFalse(FloatRowPacking.facts(cfg(kw("Float", "RIGHT")), false).floatsLeft)
        assertFalse(FloatRowPacking.facts(cfg(kw("Float", "INLINE_END")), false).floatsLeft)
        // No Float wire at all → plain in-flow content.
        assertFalse(FloatRowPacking.facts(cfg(), false).floatsLeft)
    }

    @Test
    fun `childless clear both is a break, right clear and content clears are not`() {
        // P2: the `<br clear:both>` wire shape — childless, Clear only.
        assertTrue(FloatRowPacking.facts(cfg(kw("Clear", "BOTH")), false).clearBreaksLeft)
        // clear:left / inline-start also clear a LEFT run (§9.5.2).
        assertTrue(FloatRowPacking.facts(cfg(kw("Clear", "LEFT")), false).clearBreaksLeft)
        assertTrue(FloatRowPacking.facts(cfg(kw("Clear", "INLINE_START")), false).clearBreaksLeft)
        // clear:right does NOT clear a left run (§9.5.2 same-side rule).
        assertFalse(FloatRowPacking.facts(cfg(kw("Clear", "RIGHT")), false).clearBreaksLeft)
        // A clear on a CONTENT box is real layout, not a break marker.
        assertFalse(FloatRowPacking.facts(cfg(kw("Clear", "BOTH")), true).clearBreaksLeft)
        // A floating box never doubles as a break marker.
        assertFalse(
            FloatRowPacking.facts(cfg(kw("Float", "LEFT"), kw("Clear", "BOTH")), false)
                .clearBreaksLeft
        )
    }

    // Shorthand fact constructors for the segmentation pins.
    private val F = FloatRowPacking.ChildFacts(floatsLeft = true, clearBreaksLeft = false)
    private val BR = FloatRowPacking.ChildFacts(floatsLeft = false, clearBreaksLeft = true)
    private val X = FloatRowPacking.ChildFacts(floatsLeft = false, clearBreaksLeft = false)

    // ── P1/P2 — segmentation ──

    @Test
    fun `justify-self-001 sibling pattern segments into 3-2-5-4 strutted runs`() {
        // The live wire order: 14 floats + 4 br markers (18 siblings).
        val segs = FloatRowPacking.segment(
            listOf(F, F, F, BR, F, F, BR, F, F, F, F, F, BR, F, F, F, F, BR)
        )
        // 4 runs + 4 br singles = 8 segments, sibling order preserved.
        assertEquals(8, segs.size)
        // Run 1: indices 0-2, terminated by the br at 3 → strutted.
        assertEquals(listOf(0, 1, 2), segs[0].indices)
        assertTrue(segs[0].isRun); assertTrue(segs[0].strutted)
        // The br itself is a single (renders ~0-size in WPT mode).
        assertEquals(listOf(3), segs[1].indices); assertFalse(segs[1].isRun)
        // Runs 2-4 mirror the 2/5/4 ref rows, all strutted.
        assertEquals(listOf(4, 5), segs[2].indices); assertTrue(segs[2].strutted)
        assertEquals(listOf(7, 8, 9, 10, 11), segs[4].indices); assertTrue(segs[4].strutted)
        assertEquals(listOf(13, 14, 15, 16), segs[6].indices); assertTrue(segs[6].strutted)
    }

    @Test
    fun `grid float pair is one un-strutted run`() {
        // descendant-static-position-001: green+grey, no br after.
        val segs = FloatRowPacking.segment(listOf(F, F))
        assertEquals(1, segs.size)
        assertTrue(segs[0].isRun)
        // No trailing clear sibling → the run reports its bare height.
        assertFalse(segs[0].strutted)
        assertEquals(listOf(0, 1), segs[0].indices)
    }

    @Test
    fun `lone floats and non-floats stay singles`() {
        // A lone float keeps today's block path (F4 conservatism).
        val segs = FloatRowPacking.segment(listOf(F, X, F))
        assertEquals(3, segs.size)
        assertTrue(segs.none { it.isRun })
    }

    // ── P3/P5/P6 — packing geometry ──

    @Test
    fun `corpus big row packs side by side with the br strut`() {
        // Three 27×19 margin boxes (16w+4pad+2border+5margin / 10h+2+2+5),
        // unbounded width, strutted by the trailing br (pin P6).
        val plan = FloatRowPacking.layout(
            widths = listOf(27.0, 27.0, 27.0),
            heights = listOf(19.0, 19.0, 19.0),
            availableWidth = Double.POSITIVE_INFINITY,
            strutPx = FloatRowPacking.STRUT_PX,
        )
        // §9.5.1 rule 2 — each float packs against the previous margin edge.
        assertEquals(listOf(0.0, 27.0, 54.0), plan.x)
        // Single row — every float sits at the row top (rule 6).
        assertEquals(listOf(0.0, 0.0, 0.0), plan.y)
        // Run extent = Σ margin-box widths.
        assertEquals(81.0, plan.width, 0.0)
        // P6: 19px of float < the 20px br line box → the strut wins,
        // reproducing the ref's 20px row pitch.
        assertEquals(20.0, plan.height, 0.0)
    }

    @Test
    fun `grid pair reports bare height without strut`() {
        // Green+grey 20×40 inside the shrink-to-fit abspos (strut 0).
        val plan = FloatRowPacking.layout(
            widths = listOf(20.0, 20.0),
            heights = listOf(40.0, 40.0),
            availableWidth = Double.POSITIVE_INFINITY,
            strutPx = 0.0,
        )
        // Side-by-side pair: grey's left edge = green's right edge.
        assertEquals(listOf(0.0, 20.0), plan.x)
        // The run is exactly the 40×40 the ref paints (red fully covered).
        assertEquals(40.0, plan.width, 0.0)
        assertEquals(40.0, plan.height, 0.0)
    }

    @Test
    fun `finite width wraps at exhaustion per rule 7`() {
        // Five 27-wide floats against a 100px containing block: three fit
        // (54+27=81 ≤ 100), the fourth would end at 108 > 100 → new row.
        val plan = FloatRowPacking.layout(
            widths = List(5) { 27.0 },
            heights = List(5) { 19.0 },
            availableWidth = 100.0,
            strutPx = 0.0,
        )
        assertEquals(listOf(0.0, 27.0, 54.0, 0.0, 27.0), plan.x)
        // Row 2 starts below row 1's tallest float (P5).
        assertEquals(listOf(0.0, 0.0, 0.0, 19.0, 19.0), plan.y)
        assertEquals(81.0, plan.width, 0.0)
        assertEquals(38.0, plan.height, 0.0)
    }

    @Test
    fun `a row's first float may overflow - rule 7 loose case`() {
        // A 150-wide float in a 100-wide block stays in row 1 (§9.5.1
        // rule 7: only a float with another float to its LEFT wraps).
        val plan = FloatRowPacking.layout(
            widths = listOf(150.0, 30.0),
            heights = listOf(10.0, 8.0),
            availableWidth = 100.0,
            strutPx = 0.0,
        )
        // The second float cannot fit beside it → row 2 at x=0.
        assertEquals(listOf(0.0, 0.0), plan.x)
        assertEquals(listOf(0.0, 10.0), plan.y)
        // Extent = the widest row (the overflowing first float).
        assertEquals(150.0, plan.width, 0.0)
        assertEquals(18.0, plan.height, 0.0)
    }

    // ── renderer plan gate (blockFloatSegments) ──

    // Minimal IRComponent shells matching the live wire shapes.
    private fun floatChild(id: String) = IRComponent(
        id = id, name = id,
        properties = listOf(kw("Float", "LEFT"), kw("Width", "IGNORED")),
    )
    private fun brChild(id: String) = IRComponent(
        id = id, name = id, properties = listOf(kw("Clear", "BOTH")),
    )
    private fun plainChild(id: String) = IRComponent(id = id, name = id)

    @Test
    fun `renderer plan exists only when a run exists`() {
        // The grid pair produces a plan with one run…
        val plan = ComponentRenderer.blockFloatSegments(
            listOf(floatChild("green"), floatChild("grey"))
        )
        assertEquals(1, plan!!.size)
        assertTrue(plan[0].isRun)
        // …while run-free children keep the frozen loop (null plan).
        assertNull(
            ComponentRenderer.blockFloatSegments(
                listOf(plainChild("a"), floatChild("lone"), brChild("br"))
            )
        )
    }

    // ── Wave-20 W3 — right/inline-end float runs (P10-P14) ──
    // Corpus geometry: css-grid descendant-static-position-002/004 —
    // a green+grey Float:RIGHT 20×40 pair inside a shrink-to-fit abspos
    // box under a direction:rtl grid; ref paints grey LEFT of green
    // (grey x40-59, green x60-79).

    @Test
    fun `right and inline-end floats derive floatsRight under ltr`() {
        // P10: the physical keyword and the ltr logical alias.
        assertTrue(FloatRowPacking.facts(cfg(kw("Float", "RIGHT")), false).floatsRight)
        assertTrue(FloatRowPacking.facts(cfg(kw("Float", "INLINE_END")), false).floatsRight)
        // Left-family floats never derive the right fact.
        assertFalse(FloatRowPacking.facts(cfg(kw("Float", "LEFT")), false).floatsRight)
        assertFalse(FloatRowPacking.facts(cfg(kw("Float", "INLINE_START")), false).floatsRight)
        // No Float wire at all → plain in-flow content.
        assertFalse(FloatRowPacking.facts(cfg(), false).floatsRight)
    }

    @Test
    fun `rtl swaps the logical float members per css-logical`() {
        // css-logical-1 §2.1: rtl maps inline-start→right, inline-end→left.
        assertTrue(FloatRowPacking.facts(cfg(kw("Float", "INLINE_START")), false, rtl = true).floatsRight)
        assertTrue(FloatRowPacking.facts(cfg(kw("Float", "INLINE_END")), false, rtl = true).floatsLeft)
        // The physical keywords are direction-independent.
        assertTrue(FloatRowPacking.facts(cfg(kw("Float", "RIGHT")), false, rtl = true).floatsRight)
        assertTrue(FloatRowPacking.facts(cfg(kw("Float", "LEFT")), false, rtl = true).floatsLeft)
    }

    @Test
    fun `childless right clears break right runs only`() {
        // P14: BOTH clears either side; RIGHT/INLINE_END clear right (ltr).
        assertTrue(FloatRowPacking.facts(cfg(kw("Clear", "BOTH")), false).clearBreaksRight)
        assertTrue(FloatRowPacking.facts(cfg(kw("Clear", "RIGHT")), false).clearBreaksRight)
        assertTrue(FloatRowPacking.facts(cfg(kw("Clear", "INLINE_END")), false).clearBreaksRight)
        // §9.5.2 same-side rule: clear:left never breaks a RIGHT run.
        assertFalse(FloatRowPacking.facts(cfg(kw("Clear", "LEFT")), false).clearBreaksRight)
        // Content boxes and floating boxes are never break markers.
        assertFalse(FloatRowPacking.facts(cfg(kw("Clear", "BOTH")), true).clearBreaksRight)
        assertFalse(
            FloatRowPacking.facts(cfg(kw("Float", "RIGHT"), kw("Clear", "BOTH")), false)
                .clearBreaksRight
        )
    }

    // Right-float / right-clear fact shorthands for the segmentation pins.
    private val R = FloatRowPacking.ChildFacts(
        floatsLeft = false, clearBreaksLeft = false, floatsRight = true
    )
    private val BRR = FloatRowPacking.ChildFacts(
        floatsLeft = false, clearBreaksLeft = false, clearBreaksRight = true
    )

    @Test
    fun `right pair segments into one right run`() {
        // P11: descendant-static-position-002/004's green+grey pair.
        val segs = FloatRowPacking.segment(listOf(R, R))
        assertEquals(1, segs.size)
        assertTrue(segs[0].isRun)
        assertTrue(segs[0].rightRun)
        // No trailing clear sibling → un-strutted.
        assertFalse(segs[0].strutted)
        assertEquals(listOf(0, 1), segs[0].indices)
    }

    @Test
    fun `opposite sides never merge and lone rights stay single`() {
        // A side flip splits the streak into two independent runs (P11).
        val segs = FloatRowPacking.segment(listOf(F, F, R, R))
        assertEquals(2, segs.size)
        assertTrue(segs[0].isRun); assertFalse(segs[0].rightRun)
        assertTrue(segs[1].isRun); assertTrue(segs[1].rightRun)
        // Lone right floats keep the frozen wave-5 end-alignment path.
        assertTrue(FloatRowPacking.segment(listOf(R, X, R)).none { it.isRun })
    }

    @Test
    fun `right run struts only on a same-side clear marker`() {
        // P14: a right-clear br struts the right run…
        val strutted = FloatRowPacking.segment(listOf(R, R, BRR))
        assertTrue(strutted[0].isRun); assertTrue(strutted[0].strutted)
        // …while a LEFT-only clear marker does not (§9.5.2 same-side).
        val unstrutted = FloatRowPacking.segment(listOf(R, R, BR))
        assertTrue(unstrutted[0].isRun); assertFalse(unstrutted[0].strutted)
    }

    @Test
    fun `layoutEnd mirrors the grid pair right-to-left`() {
        // P12: the 002/004 pin — first float (green) flush right at the
        // run's right edge, grey packs to its LEFT: x = [20, 0], the
        // exact grey|green order the ref paints (grey x40-59, green
        // x60-79 once the abspos static position lands at x40).
        val plan = FloatRowPacking.layoutEnd(
            widths = listOf(20.0, 20.0),
            heights = listOf(40.0, 40.0),
            availableWidth = Double.POSITIVE_INFINITY,
            strutPx = 0.0,
        )
        assertEquals(listOf(20.0, 0.0), plan.x)
        // Extent + height identical to the left plan (shared rows).
        assertEquals(40.0, plan.width, 0.0)
        assertEquals(40.0, plan.height, 0.0)
    }

    @Test
    fun `layoutEnd keeps the left plan's rows and mirrors per extent`() {
        // P12: five 27-wide floats over a 100px block — same 3+2 row
        // split as the left pin, each x reflected against the 81 extent.
        val plan = FloatRowPacking.layoutEnd(
            widths = List(5) { 27.0 },
            heights = List(5) { 19.0 },
            availableWidth = 100.0,
            strutPx = 0.0,
        )
        // Mirror of [0,27,54,0,27] against width 81 minus each 27.
        assertEquals(listOf(54.0, 27.0, 0.0, 54.0, 27.0), plan.x)
        assertEquals(listOf(0.0, 0.0, 0.0, 19.0, 19.0), plan.y)
        assertEquals(81.0, plan.width, 0.0)
        assertEquals(38.0, plan.height, 0.0)
    }

    // A Float:RIGHT child shell matching the 002/004 wire shape.
    private fun rightFloatChild(id: String) = IRComponent(
        id = id, name = id,
        properties = listOf(kw("Float", "RIGHT"), kw("Width", "IGNORED")),
    )

    @Test
    fun `renderer plan packs the right pair and maps rtl logicals`() {
        // P10/P11 — the 002/004 pair yields one RIGHT run.
        val plan = ComponentRenderer.blockFloatSegments(
            listOf(rightFloatChild("green"), rightFloatChild("grey"))
        )
        assertEquals(1, plan!!.size)
        assertTrue(plan[0].isRun)
        assertTrue(plan[0].rightRun)
        // css-logical §2.1 — under a Direction:RTL container, a pair of
        // inline-start floats is a RIGHT run.
        val rtlPlan = ComponentRenderer.blockFloatSegments(
            listOf(
                IRComponent("a", "a", listOf(kw("Float", "INLINE_START"))),
                IRComponent("b", "b", listOf(kw("Float", "INLINE_START"))),
            ),
            containerProperties = listOf(kw("Direction", "RTL")),
        )
        assertTrue(rtlPlan!![0].isRun)
        assertTrue(rtlPlan[0].rightRun)
    }
}
