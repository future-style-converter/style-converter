package com.styleconverter.runtime.columns

// WAVE-44 LANE U8 — the FLOAT-STRIP pin table (FS rows). The iOS twin
// (runtimes/swiftui .../MulticolFloatStripTests.swift) carries the SAME
// FS rows byte-for-byte (native-pair parity gate).
//
// Every geometry row is hand-derived from the frozen Chromium refs
// (tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
//  white-black-ink-font-lh-imgpad-htmlpins/CSS2/), content-box y:
//  · fill refs (floats-clear-multicol-000/002/003): aqua strips rows
//    111-210 in columns 1-2, rows 111-160 in column 3, 3px orange at rows
//    161-163 → strip [0,250) floats + cleared box at 250, H=100, C=253;
//  · balancing refs (-balancing-000/002/003): aqua rows 91-175 / col-3
//    91-170, 5px orange rows 171-175 → H = ceil(255/3) = 85, C=255.
// The IR shapes mirror tools/titan/runs/wave43-final/sections/CSS2/
// per-test-ir/wpt__CSS2__floats-clear__floats-clear-multicol-*.json.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MulticolFloatStripTest {

    /** IR wire helper — keywords are bare JSON strings, lengths objects. */
    private fun prop(t: String, j: String) = IRProperty(t, Json.parseToJsonElement(j))

    /** A px length wire: `{"type":"length","px":N}` (converter shape). */
    private fun px(n: Int) = "{\"type\":\"length\",\"px\":$n}"

    /** One aqua float box exactly as the wave43-final IRs carry it. */
    private fun floatBox(id: String, side: String, h: Int) = IRComponent(
        id = id, name = id,
        properties = listOf(
            prop("Float", "\"$side\""), prop("Width", px(15)), prop("Height", px(h))
        )
    )

    /** The `.container` (width:100%; two floats) of -000/002/balancing. */
    private fun container(h: Int = 250) = IRComponent(
        id = "container", name = "container",
        properties = listOf(prop("Width", "{\"type\":\"percentage\",\"value\":100}")),
        children = listOf(floatBox("fL", "LEFT", h), floatBox("fR", "RIGHT", h))
    )

    /** The -002 `.clear` (clear:left; medium orange border-bottom). */
    private val clear002 = IRComponent(
        id = "clear", name = "clear",
        properties = listOf(prop("Clear", "\"LEFT\""), prop("BorderBottomStyle", "\"SOLID\""))
    )

    /** The -003 `.step` (height:10; 15px aqua side borders, none top/bottom). */
    private val step003 = IRComponent(
        id = "step", name = "step",
        properties = listOf(
            prop("Height", px(10)),
            prop("BorderLeftStyle", "\"SOLID\""), prop("BorderRightStyle", "\"SOLID\""),
            prop("BorderTopStyle", "\"NONE\""), prop("BorderBottomStyle", "\"NONE\""),
            prop("BorderLeftWidth", "{\"px\":15}"), prop("BorderRightWidth", "{\"px\":15}")
        )
    )

    /** The -003 `.clear` (clear:left; height:0) holding the orange `.bar`. */
    private fun clear003(barWidthWire: String?) = IRComponent(
        id = "clear", name = "clear",
        properties = listOf(prop("Clear", "\"LEFT\""), prop("Height", px(0))),
        children = listOf(
            IRComponent(
                id = "bar", name = "bar",
                properties = listOfNotNull(
                    prop("BorderBottomStyle", "\"SOLID\""),
                    barWidthWire?.let { prop("BorderBottomWidth", it) }
                )
            )
        )
    )

    // ── FS-A: the facts projection (factsFor) ─────────────────────────────

    @Test
    fun `FS-A1 container facts - two 250px floats anchored at content top`() {
        val f = MulticolFloatStrip.factsFor(container())
        assertNotNull(f)
        // One float per side, 250px each, ids preserved for the zero-flow keys.
        assertEquals(
            listOf(
                MulticolFloatStrip.FloatFact("fL", rightSide = false, heightPx = 250.0),
                MulticolFloatStrip.FloatFact("fR", rightSide = true, heightPx = 250.0)
            ),
            f!!.floats
        )
        // The container clears nothing and paints no trailing ink of its own.
        assertFalse(f.clearsLeft); assertFalse(f.clearsRight)
        assertEquals(0.0, f.trailingInkPx, 0.0)
    }

    @Test
    fun `FS-A2 cleared box facts - clear left plus the medium 3px border ink`() {
        val f = MulticolFloatStrip.factsFor(clear002)
        assertNotNull(f)
        assertTrue(f!!.clearsLeft); assertFalse(f.clearsRight)
        // css-backgrounds-3 §4.3: no declared width + SOLID ⇒ medium = 3px
        // (the exact BorderSideExtractor default, so ink matches paint).
        assertEquals(3.0, f.trailingInkPx, 0.0)
        assertTrue(f.floats.isEmpty())
    }

    @Test
    fun `FS-A3 height-0 cleared box - the inner bar's border is the trailing ink`() {
        // -003: `.clear{height:0}` + `.bar{border-bottom:orange solid}` —
        // the bar paints BELOW the measured 0 (css-overflow-3 §2), and
        // Chromium's balanced column height includes the band.
        assertEquals(3.0, MulticolFloatStrip.factsFor(clear003(null))!!.trailingInkPx, 0.0)
        // -balancing-003 declares the width: 5px.
        assertEquals(5.0, MulticolFloatStrip.factsFor(clear003("{\"px\":5}"))!!.trailingInkPx, 0.0)
    }

    @Test
    fun `FS-A4 step facts - declared height is the ink floor, side borders add none`() {
        // Block borders: top/bottom styles are NONE ⇒ 0 used width (§4.3);
        // the 15px side borders have no block-axis extent.
        assertEquals(10.0, MulticolFloatStrip.factsFor(step003)!!.trailingInkPx, 0.0)
    }

    @Test
    fun `FS-A5 strict bails - unproven shapes project to null`() {
        // Two same-side floats stack per §9.5.1 rules 2/3 — not modeled.
        assertNull(
            MulticolFloatStrip.factsFor(
                IRComponent(
                    id = "c", name = "c",
                    children = listOf(floatBox("a", "LEFT", 10), floatBox("b", "LEFT", 10))
                )
            )
        )
        // A margin breaks the flush cursor stacking.
        assertNull(
            MulticolFloatStrip.factsFor(
                IRComponent(id = "c", name = "c", properties = listOf(prop("MarginTop", px(4))))
            )
        )
        // A padded float's outer edge is taller than its height (the
        // FloatClearance padded-float bail, same reasoning).
        assertNull(
            MulticolFloatStrip.factsFor(
                IRComponent(
                    id = "c", name = "c",
                    children = listOf(
                        IRComponent(
                            id = "f", name = "f",
                            properties = listOf(
                                prop("Float", "\"LEFT\""), prop("Height", px(10)),
                                prop("PaddingTop", px(2))
                            )
                        )
                    )
                )
            )
        )
        // A float hiding PAST the leading run anchors somewhere this model
        // cannot pin.
        assertNull(
            MulticolFloatStrip.factsFor(
                IRComponent(
                    id = "c", name = "c",
                    children = listOf(
                        IRComponent(id = "plain", name = "plain"),
                        floatBox("late", "LEFT", 10)
                    )
                )
            )
        )
        // A float without an explicit px height has no §9.5.2 ledger entry.
        assertNull(
            MulticolFloatStrip.factsFor(
                IRComponent(
                    id = "c", name = "c",
                    children = listOf(
                        IRComponent(id = "f", name = "f", properties = listOf(prop("Float", "\"LEFT\"")))
                    )
                )
            )
        )
        // Relative positioning paints away from the flow slot (§9.4.3).
        assertNull(
            MulticolFloatStrip.factsFor(
                IRComponent(id = "c", name = "c", properties = listOf(prop("Position", "\"RELATIVE\"")))
            )
        )
    }

    // ── FS-B: engagement + the zero-flow plan ─────────────────────────────

    /** specsFor over the -002 IR shape — the real integration entry. */
    private fun specs002() = MulticolSpannerFlow.specsFor(listOf(container(), clear002))

    @Test
    fun `FS-B1 the 002 shape engages and zero-flows exactly the two floats`() {
        val specs = specs002()
        assertTrue(MulticolFloatStrip.engages(specs))
        // Both fill modes reach the plan for this shape (250px floats are a
        // provable ink floor — the balance branch's pre-measure condition).
        assertNotNull(MulticolFloatStrip.zeroFlowPlan(specs, columnFillAuto = false))
        val plan = MulticolFloatStrip.zeroFlowPlan(specs, columnFillAuto = true)
        assertNotNull(plan)
        // Scope: the multicol children (so their block loops ADOPT the
        // inherited plan) plus the floats themselves.
        assertEquals(setOf("container", "clear", "fL", "fR"), plan!!.scopeIds)
        // Adjustments: zero flow height for the floats, nothing else.
        assertEquals(setOf("fL", "fR"), plan.adjustments.keys)
        assertTrue(plan.adjustments.values.all { it.zeroFlowHeight && it.appliedTopPx == null })
    }

    @Test
    fun `FS-B2 float-less containers and unproven children never engage`() {
        // No floats anywhere: the run/greedy paths already render this.
        assertFalse(
            MulticolFloatStrip.engages(
                MulticolSpannerFlow.specsFor(listOf(clear002, step003))
            )
        )
        // One unproven child (margin wire) disables the WHOLE strip.
        assertFalse(
            MulticolFloatStrip.engages(
                MulticolSpannerFlow.specsFor(
                    listOf(
                        container(),
                        IRComponent(id = "m", name = "m", properties = listOf(prop("MarginTop", px(1))))
                    )
                )
            )
        )
        // Null specs (dark stage) and the leading-text head both bail.
        assertFalse(MulticolFloatStrip.engages(null))
        assertFalse(
            MulticolFloatStrip.engages(
                MulticolSpannerFlow.specsFor(listOf(container()), leadingText = true)
            )
        )
        assertNull(MulticolFloatStrip.zeroFlowPlan(null, columnFillAuto = true))
    }

    // ── FS-C: the strip geometry (plan) — ref-derived rows ────────────────

    /** Shared plan call with the family's used geometry (N=3, W=100, G=0). */
    private fun plan(
        specs: List<MulticolSpannerFlow.ChildSpec>,
        measured: List<Int>,
        fillAuto: Boolean,
        h: Int = 100,
        n: Int = 3
    ) = MulticolFloatStripPlan.plan(specs, measured, h, fillAuto, 100, 0, n)

    @Test
    fun `FS-C1 fill 002 - cleared box at 250, C 253, three 100px slices`() {
        // Measured under zero-flow: container 0 (floats take no space),
        // cleared box 3 (its own border-bottom band).
        val p = plan(specs002(), listOf(0, 3), fillAuto = true)!!
        // §9.5.2: clear:left jumps the cleared box to the left float's
        // bottom outer edge 250 (ref: orange rows 161-163 = strip 250-253).
        assertEquals(listOf(0, 250), p.yOffsetsPx)
        assertEquals(100, p.columnBlockSizePx)
        assertEquals(253, p.stripInkPx)
        // Three slices — the wave-10 S-geometry with C=253, H=100.
        assertEquals(
            FragmentGeometry.fragmentGeometry(253, 100, 100, 0, 3),
            p.fragments
        )
        assertEquals(3, p.fragments.size)
        // Slice 2 shows strip [200,300): aqua 200-250 + orange 250-253.
        assertEquals(-200, p.fragments[2].translateY)
        assertEquals(200, p.fragments[2].clipLeft)
    }

    @Test
    fun `FS-C2 fill 003 - step then floats then clearance, same three slices`() {
        val specs = MulticolSpannerFlow.specsFor(
            listOf(step003, container(h = 240), clear003(null))
        )
        assertTrue(MulticolFloatStrip.engages(specs))
        // Measured: step 10, container 0, cleared box 0 (height:0 —
        // Compose clamps the bar; trailingInkPx 3 carries the band).
        val p = plan(specs, listOf(10, 0, 0), fillAuto = true)!!
        // Floats anchor at the container's top = strip 10; 240px extent
        // ends at 250 — the same clear line as -002 (ref-identical).
        assertEquals(listOf(0, 10, 250), p.yOffsetsPx)
        assertEquals(253, p.stripInkPx)
        assertEquals(3, p.fragments.size)
    }

    @Test
    fun `FS-C3 balance 002 - the 5px border joins C and the columns balance to 85`() {
        val specs = MulticolSpannerFlow.specsFor(
            listOf(
                container(),
                IRComponent(
                    id = "clear", name = "clear",
                    properties = listOf(
                        prop("Clear", "\"LEFT\""), prop("BorderBottomStyle", "\"SOLID\""),
                        prop("BorderBottomWidth", "{\"px\":5}")
                    )
                )
            )
        )
        val p = plan(specs, listOf(0, 5), fillAuto = false)!!
        // §7.1 reduced: H = min(100, ceil(255/3)) = 85 — the balancing
        // refs' exact column height (aqua rows 91-175, orange 171-175).
        assertEquals(85, p.columnBlockSizePx)
        assertEquals(255, p.stripInkPx)
        assertEquals(listOf(0, 250), p.yOffsetsPx)
        // Slice 2 shows strip [170,255): aqua 170-250 + orange 250-255.
        assertEquals(3, p.fragments.size)
        assertEquals(-170, p.fragments[2].translateY)
        assertEquals(85, p.fragments[2].clipHeight)
    }

    @Test
    fun `FS-C4 balance 003 - height-0 cleared box still carries the 5px band into C`() {
        val specs = MulticolSpannerFlow.specsFor(
            listOf(step003, container(h = 240), clear003("{\"px\":5}"))
        )
        val p = plan(specs, listOf(10, 0, 0), fillAuto = false)!!
        // Without trailingInkPx C would be 250 → H 84, one px off every
        // slice; the IR ink floor restores Chromium's 255 → 85.
        assertEquals(85, p.columnBlockSizePx)
        assertEquals(255, p.stripInkPx)
        assertEquals(listOf(0, 10, 250), p.yOffsetsPx)
    }

    @Test
    fun `FS-C5 no clear wire - floats still strip but the sibling stays at the cursor`() {
        // The -000/-001 IR shape: `<br clear=all>` reaches the IR with NO
        // Clear wire (tools/titan/extract-fixture.mjs:9789 reads only
        // rule-matched declarations — the HTML clear attribute is lost),
        // so the sibling stacks at the flush cursor: the aqua geometry
        // still fixes, the orange line stays misplaced until the feeder
        // emits the wire (deferred, evidence in the lane report).
        val noClear = IRComponent(
            id = "clear", name = "clear",
            properties = listOf(prop("BorderBottomStyle", "\"SOLID\"")),
            children = listOf(
                IRComponent(
                    id = "br", name = "br",
                    properties = listOf(prop("Width", px(0)), prop("Height", px(20)))
                )
            )
        )
        val specs = MulticolSpannerFlow.specsFor(listOf(container(), noClear))
        val p = plan(specs, listOf(0, 23), fillAuto = true)!!
        // No clearance jump — but the float ledger still spans C to 250.
        assertEquals(listOf(0, 0), p.yOffsetsPx)
        assertEquals(250, p.stripInkPx)
        assertEquals(3, p.fragments.size)
    }

    @Test
    fun `FS-C6 balance cannot exceed the definite height - the fill cap`() {
        // A 400px strip in 3 columns of definite 100: ceil(400/3)=134 must
        // cap at 100 (content that cannot balance falls back to the fill
        // geometry and clips like the wave-10 N cap).
        val specs = MulticolSpannerFlow.specsFor(listOf(container(h = 400), clear002))
        val p = plan(specs, listOf(0, 3), fillAuto = false)!!
        assertEquals(100, p.columnBlockSizePx)
        // ceil(403/100)=5 fragments capped at N=3.
        assertEquals(3, p.fragments.size)
    }

    @Test
    fun `FS-C7 geometry bails - one column, misaligned inputs, degenerate H`() {
        // N == 1 keeps overflow-not-clip semantics (the run twin's rule).
        assertNull(plan(specs002(), listOf(0, 3), fillAuto = true, n = 1))
        // Misaligned measured list — bail rather than zip wrong.
        assertNull(plan(specs002(), listOf(0), fillAuto = true))
        // Non-positive definite H has no fragmentainer.
        assertNull(plan(specs002(), listOf(0, 3), fillAuto = true, h = 0))
    }

    // ── FS-E: the pre-measure invariant (wave-44 skeptic S5, D1) ─────────
    //
    // The strip's decline path returns the container to MultiColumnApplier's
    // legacy loop, which measures the SAME measurables again — Compose
    // allows one measure per Measurable per pass and throws on the second.
    // So every decline must be answerable BEFORE the measure loop, and the
    // only plan decline that needed measured heights (§7.1 balance with
    // C == 0) is now pre-empted by MulticolFloatStripPlan.engagesPreMeasure.

    /** The S5 shape: engaged strip, balance mode, EVERY float height 0. */
    private fun specsS5() = MulticolSpannerFlow.specsFor(
        listOf(container(h = 0), IRComponent(id = "plain", name = "plain"))
    )

    @Test
    fun `FS-E1 the S5 shape declines PRE-measure under balance, never after`() {
        val specs = specsS5()
        // It is a fully ENGAGED strip by shape — this is why the crash was
        // reachable: the gate said yes, then the geometry said no.
        assertTrue(MulticolFloatStrip.engages(specs))
        // Nothing in the IR proves a single px of ink (0-height floats, no
        // trailing band), so C can only come from measured heights.
        assertEquals(0, MulticolFloatStripPlan.provableStripInkPx(specs))
        // The crash shape itself: measured all-zero + balance ⇒ h = 0 ⇒ the
        // pure plan declines. THIS is the answer that used to arrive after
        // the measure loop and re-entered the caller's re-measuring path.
        assertNull(plan(specs, listOf(0, 0), fillAuto = false))
        // The shared predicate now answers it from the IR alone, before any
        // measure — balance declines, fill:auto still engages (h = H).
        assertFalse(MulticolFloatStripPlan.engagesPreMeasure(specs, columnFillAuto = false))
        assertTrue(MulticolFloatStripPlan.engagesPreMeasure(specs, columnFillAuto = true))
        assertNotNull(plan(specs, listOf(0, 0), fillAuto = true))
        // And the composition half declines with it, so the floats never
        // zero-flow under a layout that still stacks them.
        assertNull(MulticolFloatStrip.zeroFlowPlan(specs, columnFillAuto = false))
        assertNotNull(MulticolFloatStrip.zeroFlowPlan(specs, columnFillAuto = true))
    }

    @Test
    fun `FS-E2 the conservative side of the gate, stated honestly`() {
        val specs = specsS5()
        // Had the children MEASURED tall, the balance plan would have been
        // fine (C = 120 ⇒ h = 40) — but proving that needs the measure the
        // gate must precede, so this shape stays on the legacy path. The
        // cost is fragmentation only: a 0-height float carrying no borders,
        // padding, children or text (factsFor rejects all of those) paints
        // nothing, so no float geometry is lost.
        assertNotNull(plan(specs, listOf(0, 120), fillAuto = false))
        assertFalse(MulticolFloatStripPlan.engagesPreMeasure(specs, columnFillAuto = false))
    }

    @Test
    fun `FS-E3 a provable ink floor keeps the balance strip engaged`() {
        // Either IR term is enough: the 250px float ledger of -002…
        assertEquals(250, MulticolFloatStripPlan.provableStripInkPx(specs002()))
        assertTrue(MulticolFloatStripPlan.engagesPreMeasure(specs002(), columnFillAuto = false))
        // …or a trailing border band alone (0-height floats + the -002
        // cleared box's medium 3px bottom border).
        val banded = MulticolSpannerFlow.specsFor(listOf(container(h = 0), clear002))
        assertEquals(3, MulticolFloatStripPlan.provableStripInkPx(banded))
        assertTrue(MulticolFloatStripPlan.engagesPreMeasure(banded, columnFillAuto = false))
        // Sub-half-pixel declarations round to 0 exactly like the plan's own
        // Math.round terms — the gate can never disagree with the geometry.
        val hairline = MulticolSpannerFlow.specsFor(
            listOf(
                IRComponent(
                    id = "c", name = "c",
                    children = listOf(
                        IRComponent(
                            id = "f", name = "f",
                            properties = listOf(
                                prop("Float", "\"LEFT\""),
                                prop("Height", "{\"type\":\"length\",\"px\":0.4}")
                            )
                        )
                    )
                )
            )
        )
        assertEquals(0, MulticolFloatStripPlan.provableStripInkPx(hairline))
        assertNull(plan(hairline, listOf(0), fillAuto = false))
        assertFalse(MulticolFloatStripPlan.engagesPreMeasure(hairline, columnFillAuto = false))
        // Unengaged shapes are false whatever the fill mode.
        assertFalse(MulticolFloatStripPlan.engagesPreMeasure(null, columnFillAuto = true))
        assertEquals(0, MulticolFloatStripPlan.provableStripInkPx(null))
    }

    @Test
    fun `FS-E4 every measure-pass decline is decided before the measure loop`() {
        // The structural pin behind FS-E1: source-scan the measure half —
        // no bail may sit after the loop that consumes the measurables.
        val src = source(
            "runtimes/compose/src/main/java/com/styleconverter/runtime/columns/" +
                "MulticolFloatStripMeasure.kt")
        val measureLoop = src.indexOf("val placeables = measurables.map {")
        assertTrue("the measure loop must exist", measureLoop >= 0)
        assertTrue(
            "every `return null` must precede the measure loop — a later one " +
                "hands the SAME measurables back to MultiColumnApplier's legacy " +
                "loop, which measures them again (Compose: once per pass)",
            src.lastIndexOf("return null") in 0 until measureLoop)
        // And the wall itself: the caller's legacy path really does
        // re-measure everything after the strip call returns.
        val stripCall = applierSource.indexOf("measureFloatStrip(")
        val legacyMeasure = applierSource.indexOf("val placeables = measurables.map { measurable ->")
        assertTrue("the legacy loop must still follow the strip call",
            stripCall in 0 until legacyMeasure)
    }

    // ── FS-D: wiring source scans (the same style as the gate tests) ──────

    /** A repo file's text, located by walking up from the test working dir. */
    private fun source(rel: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        return File(requireNotNull(dir) { "repo root ($rel) not found" }, rel).readText()
    }

    private val applierSource: String by lazy {
        source("runtimes/compose/src/main/java/com/styleconverter/runtime/columns/MultiColumnApplier.kt")
    }

    @Test
    fun `the applier consults the float strip BEFORE the run pass`() {
        val strip = applierSource.indexOf("measureFloatStrip(")
        val run = applierSource.indexOf("measureRunFragment(")
        // Both delegations exist…
        assertTrue("measureFloatStrip must be wired", strip >= 0)
        assertTrue("measureRunFragment must stay wired", run >= 0)
        // …and the strip runs first (the run pass bails on floats, so the
        // order is what lets proven float shapes fragment at all).
        assertTrue("float strip must precede the run pass", strip < run)
    }

    @Test
    fun `the zero-flow paint half is provided around the multicol content`() {
        // Stage-1: the synthetic §9.5.2 plan riding LocalFloatClearancePlan.
        assertTrue(
            "zeroFlowPlan must gate the content wrap",
            applierSource.contains("MulticolFloatStrip.zeroFlowPlan(\n"))
        assertTrue(
            "the plan must ride the clearance CompositionLocal",
            applierSource.contains(".LocalFloatClearancePlan provides floatStripZeroFlow"))
        // Both stages share the SAME gates (a POSITIVE definite height, ≥2
        // columns, horizontal-tb) so paint and layout can never half-engage.
        assertTrue(
            applierSource.contains(
                "if (containerBlockSizeDefinite && this.constraints.maxHeight > 0 &&\n" +
                    "                    fragmentationAllowed && columnCount > 1)"))
        // …and the fill mode rides in, so the balance branch's degenerate
        // case is decided identically on both sides (skeptic S5, D1).
        assertTrue(
            "the composition gate must pass the fill mode into the shared predicate",
            applierSource.contains("spannerSpecs, columnFillAuto = config.fill == ColumnFill.AUTO"))
    }

    @Test
    fun `specsFor feeds the strip facts through the shared classifier`() {
        // The renderer call site is untouched — the facts ride ChildSpec.
        assertTrue(
            source("runtimes/compose/src/main/java/com/styleconverter/runtime/columns/MulticolSpannerFlow.kt")
                .contains("MulticolFloatStrip.factsFor(child)"))
    }
}
