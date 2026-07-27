package com.styleconverter.runtime.columns

// Plain-JVM JUnit4 — same suite style as MultiColumnDistributionTest.
import com.styleconverter.runtime.columns.MulticolSpannerFlow.Child
import com.styleconverter.runtime.columns.MulticolSpannerFlow.Role
import com.styleconverter.runtime.columns.MulticolSpannerFlow.Slot
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The SHARED spanner-flow pin table (wave-21 lane MULTICOL) — pins
 * [MulticolSpannerFlow] exactly; the iOS twin
 * (runtimes/swiftui .../MulticolSpannerFlowTests.swift) carries the SAME
 * SP/E/F/X rows byte-for-byte, so any change must land on both platforms
 * in the same commit (native-pair parity gate).
 *
 * SP1–SP3 and SP6 are pinned against the LIVE wave21-gate IRs
 * (tools/titan/runs/wave21-gate/sections/css-multicol/per-test-ir/):
 * always-balancing-before-column-span, abspos-after-spanner,
 * as-column-flex-item, abspos-after-spanner-static-pos.
 */
class MulticolSpannerFlowTest {

    /** SP1 — always-balancing-before-column-span: 200px child balances into 2×100 before the 0-height spanner. */
    @Test
    fun `SP1 balance before spanner - live IR always-balancing-before-column-span`() {
        // css-multicol §6.3: pre-spanner content balances (H = ceil(200/2)).
        val plan = MulticolSpannerFlow.plan(
            listOf(Child(200, Role.FLOW), Child(0, Role.SPANNER)), 2
        )
        // Flow child starts at column 0 / y 0; spanner sits below the
        // balanced 100px row; container auto height = 100.
        assertEquals(
            listOf(Slot(Role.FLOW, 0, 0), Slot(Role.SPANNER, 0, 100)),
            plan.slots
        )
        assertEquals(100, plan.containerBlockSizePx)
        // The sole flow child's replay fragmentainer is the balanced H.
        assertEquals(100, plan.soleFlowColumnBlockSizePx)
    }

    /** SP2 — abspos-after-spanner: the abspos anchors in the post-spanner flow of column 1. */
    @Test
    fun `SP2 abspos static position after spanner - live IR abspos-after-spanner`() {
        // Children in IR order: spanner(h10), abspos, red(30), spacer(70).
        val plan = MulticolSpannerFlow.plan(
            listOf(
                Child(10, Role.SPANNER), Child(0, Role.STATIC),
                Child(30, Role.FLOW), Child(70, Role.FLOW)
            ),
            2
        )
        // Post-spanner segment: T=100, H=50; the abspos anchors at the
        // segment start of column 0 (y = spanner bottom = 10), exactly
        // where the red flow sibling lands — green then paints over red.
        assertEquals(
            listOf(
                Slot(Role.SPANNER, 0, 0),
                Slot(Role.STATIC, 0, 10),
                Slot(Role.FLOW, 0, 10),
                Slot(Role.FLOW, 0, 40)
            ),
            plan.slots
        )
        // Container: 10px spanner + 50px balanced segment.
        assertEquals(60, plan.containerBlockSizePx)
        // Two flow children → no sole-flow fragmentainer.
        assertNull(plan.soleFlowColumnBlockSizePx)
    }

    /** SP3 — as-column-flex-item: auto-height sole child balances into 4×40. */
    @Test
    fun `SP3 auto-height sole-flow balance - live IR as-column-flex-item`() {
        // §7.1: unconstrained multicol always balances — H = ceil(160/4).
        val plan = MulticolSpannerFlow.plan(listOf(Child(160, Role.FLOW)), 4)
        assertEquals(listOf(Slot(Role.FLOW, 0, 0)), plan.slots)
        // Container auto block-size = the balanced 40, not the child's 160.
        assertEquals(40, plan.containerBlockSizePx)
        assertEquals(40, plan.soleFlowColumnBlockSizePx)
    }

    /** SP4 — multi-flow sequential fill: R maps to column floor(R/H), offset R mod H. */
    @Test
    fun `SP4 three 30px children in two columns fill sequentially at H 45`() {
        // T=90, H=ceil(90/2)=45: R = 0, 30, 60 → cols 0, 0, 1.
        val plan = MulticolSpannerFlow.plan(
            listOf(Child(30, Role.FLOW), Child(30, Role.FLOW), Child(30, Role.FLOW)), 2
        )
        assertEquals(
            listOf(Slot(Role.FLOW, 0, 0), Slot(Role.FLOW, 0, 30), Slot(Role.FLOW, 1, 15)),
            plan.slots
        )
        assertEquals(45, plan.containerBlockSizePx)
        assertNull(plan.soleFlowColumnBlockSizePx)
    }

    /** SP5 — a painted (non-zero) spanner splits two balanced segments. */
    @Test
    fun `SP5 mid-list 20px spanner - segments balance independently`() {
        // seg0 T=40 H=20; spanner 20 at y=20; seg1 T=60 H=30 from y=40.
        val plan = MulticolSpannerFlow.plan(
            listOf(Child(40, Role.FLOW), Child(20, Role.SPANNER), Child(60, Role.FLOW)), 2
        )
        assertEquals(
            listOf(Slot(Role.FLOW, 0, 0), Slot(Role.SPANNER, 0, 20), Slot(Role.FLOW, 0, 40)),
            plan.slots
        )
        // 20 (seg0) + 20 (spanner) + 30 (seg1).
        assertEquals(70, plan.containerBlockSizePx)
        assertNull(plan.soleFlowColumnBlockSizePx)
    }

    /** SP6 — abspos-after-spanner-static-pos: the abspos anchors mid-segment in column 2. */
    @Test
    fun `SP6 static anchor mid-segment - live IR abspos-after-spanner-static-pos`() {
        // IR order: flow70, spanner10, flow35, flow15, abspos, red-flow20.
        val plan = MulticolSpannerFlow.plan(
            listOf(
                Child(70, Role.FLOW), Child(10, Role.SPANNER),
                Child(35, Role.FLOW), Child(15, Role.FLOW),
                Child(0, Role.STATIC), Child(20, Role.FLOW)
            ),
            2
        )
        // seg0 T=70 H=35 → y 0..35; spanner 35..45; seg1 T=70 H=35:
        // R runs 0(35) 35(15) — the abspos sees R=50 → column 1, y 45+15.
        assertEquals(
            listOf(
                Slot(Role.FLOW, 0, 0),
                Slot(Role.SPANNER, 0, 35),
                Slot(Role.FLOW, 0, 45),
                Slot(Role.FLOW, 1, 45),
                Slot(Role.STATIC, 1, 60),
                Slot(Role.FLOW, 1, 60)
            ),
            plan.slots
        )
        assertEquals(80, plan.containerBlockSizePx)
        assertNull(plan.soleFlowColumnBlockSizePx)
    }

    /** SP7 — degenerates: empty input, and sub-1 column counts coerce to 1. */
    @Test
    fun `SP7 empty container plans nothing and N floors at 1`() {
        // No children → no slots, zero height.
        val empty = MulticolSpannerFlow.plan(emptyList(), 2)
        assertEquals(emptyList<Slot>(), empty.slots)
        assertEquals(0, empty.containerBlockSizePx)
        // N=0 coerces to 1 → the single column holds the whole child.
        val one = MulticolSpannerFlow.plan(listOf(Child(10, Role.FLOW)), 0)
        assertEquals(listOf(Slot(Role.FLOW, 0, 0)), one.slots)
        assertEquals(10, one.containerBlockSizePx)
    }

    /** SP8 — a static anchor at the segment's end clamps to the last column. */
    @Test
    fun `SP8 trailing static clamps to column N-1`() {
        // T=100, H=50; the static sees R=100 → floor(100/50)=2, clamped
        // to column 1 at offset 100−1·50=50 (the column's bottom edge).
        val plan = MulticolSpannerFlow.plan(
            listOf(Child(50, Role.FLOW), Child(50, Role.FLOW), Child(0, Role.STATIC)), 2
        )
        assertEquals(
            listOf(Slot(Role.FLOW, 0, 0), Slot(Role.FLOW, 1, 0), Slot(Role.STATIC, 1, 50)),
            plan.slots
        )
        assertEquals(50, plan.containerBlockSizePx)
    }

    /** E1–E6 — the narrow engagement gate (dark-stage protection). */
    @Test
    fun `E engagement - spanner always, sole-flow balance only when it shortens`() {
        // E1: any spanner engages (SP2's children).
        assertTrue(
            MulticolSpannerFlow.engages(
                listOf(Child(10, Role.SPANNER), Child(0, Role.STATIC), Child(30, Role.FLOW), Child(70, Role.FLOW)), 2
            )
        )
        // E2: sole flow child, 4 columns — balancing shortens (40 < 160).
        assertTrue(MulticolSpannerFlow.engages(listOf(Child(160, Role.FLOW)), 4))
        // E3: one column — balancing is the identity.
        assertFalse(MulticolSpannerFlow.engages(listOf(Child(160, Role.FLOW)), 1))
        // E4: multi-flow without a spanner keeps the greedy heuristic.
        assertFalse(MulticolSpannerFlow.engages(listOf(Child(40, Role.FLOW), Child(40, Role.FLOW)), 2))
        // E5: a 1px child cannot balance shorter (ceil(1/2)=1).
        assertFalse(MulticolSpannerFlow.engages(listOf(Child(1, Role.FLOW)), 2))
        // E6: a lone static is not a flow child.
        assertFalse(MulticolSpannerFlow.engages(listOf(Child(0, Role.STATIC)), 2))
        // E7: a CONTENT-sized sole flow child (no declared Height) keeps
        // the legacy render — e.g. abspos-containing-block-outside-spanner's
        // relative wrapper hiding a nested spanner must not be sliced.
        assertFalse(
            MulticolSpannerFlow.engages(
                listOf(Child(50, Role.FLOW, explicitBlockSize = false)), 3
            )
        )
    }

    /** F1–F4 — the sole-flow clip+translate replay gate. */
    @Test
    fun `F replay gate - sole flow with only inkless spanners`() {
        // F1: SP1's shape (0-height spanner) may replay.
        assertTrue(
            MulticolSpannerFlow.soleFlowFragmentReplay(
                listOf(Child(200, Role.FLOW), Child(0, Role.SPANNER))
            )
        )
        // F2: statics forbid the replay (their ink would ghost per column).
        assertFalse(
            MulticolSpannerFlow.soleFlowFragmentReplay(
                listOf(Child(10, Role.SPANNER), Child(0, Role.STATIC), Child(30, Role.FLOW), Child(70, Role.FLOW))
            )
        )
        // F3: a lone flow child replays (SP3's shape).
        assertTrue(MulticolSpannerFlow.soleFlowFragmentReplay(listOf(Child(160, Role.FLOW))))
        // F4: a painted (10px) spanner forbids the replay.
        assertFalse(
            MulticolSpannerFlow.soleFlowFragmentReplay(
                listOf(Child(200, Role.FLOW), Child(10, Role.SPANNER))
            )
        )
    }

    /** X1–X3 — boundary-crossing detection (the whole-child-placement log gate). */
    @Test
    fun `X boundary crossing - straddling children are detected`() {
        // X1: SP4's second child straddles the 45px line (30+30 > 45).
        assertTrue(
            MulticolSpannerFlow.anyFlowChildCrossesBoundary(
                listOf(Child(30, Role.FLOW), Child(30, Role.FLOW), Child(30, Role.FLOW)), 2
            )
        )
        // X2: SP2's 70px spacer straddles the post-spanner 50px line.
        assertTrue(
            MulticolSpannerFlow.anyFlowChildCrossesBoundary(
                listOf(Child(10, Role.SPANNER), Child(0, Role.STATIC), Child(30, Role.FLOW), Child(70, Role.FLOW)), 2
            )
        )
        // X3: two 40s at H=40 land exactly on the boundary — no straddle.
        assertFalse(
            MulticolSpannerFlow.anyFlowChildCrossesBoundary(
                listOf(Child(40, Role.FLOW), Child(40, Role.FLOW)), 2
            )
        )
    }

    /** R1 — role classification from the live abspos-after-spanner IR shapes. */
    @Test
    fun `R rolesFor - span all is SPANNER, abspos is STATIC, out-of-flow beats span`() {
        // Minimal IR children mirroring the wire: keyword primitives.
        val spanner = IRComponent(id = "s", name = "s", properties = listOf(IRProperty("ColumnSpan", JsonPrimitive("ALL"))))
        val abspos = IRComponent(id = "a", name = "a", properties = listOf(IRProperty("Position", JsonPrimitive("ABSOLUTE"))))
        val flow = IRComponent(id = "f", name = "f", properties = listOf(IRProperty("Height", JsonPrimitive(30))))
        // An abspos spanner is out of flow — STATIC wins (css-position §2.1).
        val absposSpanner = IRComponent(
            id = "as", name = "as",
            properties = listOf(IRProperty("ColumnSpan", JsonPrimitive("ALL")), IRProperty("Position", JsonPrimitive("ABSOLUTE")))
        )
        assertEquals(
            listOf(Role.SPANNER, Role.STATIC, Role.FLOW, Role.STATIC),
            MulticolSpannerFlow.rolesFor(listOf(spanner, abspos, flow, absposSpanner))
        )
        // Leading _text prepends an anonymous FLOW box (RenderContent order).
        assertEquals(
            listOf(Role.FLOW, Role.SPANNER),
            MulticolSpannerFlow.rolesFor(listOf(spanner), leadingText = true)
        )
        // specsFor pairs roles with the declared-block-size flag: the flow
        // child declares Height (explicit), the spanner does not; leading
        // text is content-sized by definition.
        assertEquals(
            listOf(
                MulticolSpannerFlow.ChildSpec(Role.FLOW, false),
                MulticolSpannerFlow.ChildSpec(Role.SPANNER, false),
                MulticolSpannerFlow.ChildSpec(Role.FLOW, true)
            ),
            MulticolSpannerFlow.specsFor(listOf(spanner, flow), leadingText = true)
        )
    }
}
