package com.styleconverter.runtime.layout.position

// Wave 18 (RC2) — JVM pins for abspos inset-stretch sizing
// (css-position-3 §3.5 + the css-sizing-4 §4.2 aspect-ratio interaction).
// The resolver is pure math with an identical signature on iOS
// (AbsposInsetStretchTests.swift pins the same table), and the wire
// wrapper is pinned against the LIVE wave-18 IR shapes
// (tools/titan/runs/wave18-gate/sections/css-sizing/per-test-ir).

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.variables.ContainingBlock
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AbsposInsetStretchTest {

    // Shorthand for the pure resolver with the common defaults.
    private fun resolve(
        cbW: Double? = null, cbH: Double? = null,
        left: Double? = null, right: Double? = null,
        top: Double? = null, bottom: Double? = null,
        explicitW: Double? = null, explicitH: Double? = null,
        hasExplicitW: Boolean = explicitW != null,
        hasExplicitH: Boolean = explicitH != null,
        ratio: Double? = null,
    ) = AbsposInsetStretch.resolve(
        cbW, cbH, left, right, top, bottom,
        explicitW, explicitH, hasExplicitW, hasExplicitH, ratio,
    )

    // ── The failing-test pins (numbers straight from the live IRs) ─────────

    @Test fun `abspos-003 all-zero insets in a 100x500 cb with ratio 1 resolve 100x100`() {
        // Inline stretch 100 wins; block DERIVES from the ratio, never the
        // 500px block stretch (the ref's 100×100, not 100×500).
        val r = resolve(cbW = 100.0, cbH = 500.0,
            left = 0.0, right = 0.0, top = 0.0, bottom = 0.0, ratio = 1.0)
        assertEquals(100.0, r.widthPx!!, 1e-9)
        assertEquals(100.0, r.heightPx!!, 1e-9)
    }

    @Test fun `abspos-004 first box - block stretch 50 with ratio 2 derives width 100`() {
        // left:0 top:0 bottom:0 in a 300×50 cb: block stretches to 50, the
        // fully-auto inline axis derives 50×2 = 100 (the stacked 100×50
        // half of the ref's composite square).
        val r = resolve(cbW = 300.0, cbH = 50.0,
            left = 0.0, top = 0.0, bottom = 0.0, ratio = 2.0)
        assertEquals(100.0, r.widthPx!!, 1e-9)
        assertEquals(50.0, r.heightPx!!, 1e-9)
    }

    @Test fun `abspos-004 second box - inline stretch 100 with ratio 2 derives height 50`() {
        // left:0 right:0 top:0 in a 100×300 cb: inline stretches to 100,
        // block derives 100/2 = 50. (The physical-axis rule coincides with
        // the test's vertical-lr expectation — documented approximation.)
        val r = resolve(cbW = 100.0, cbH = 300.0,
            left = 0.0, right = 0.0, top = 0.0, ratio = 2.0)
        assertEquals(100.0, r.widthPx!!, 1e-9)
        assertEquals(50.0, r.heightPx!!, 1e-9)
    }

    // ── Rule-table pins ────────────────────────────────────────────────────

    @Test fun `no ratio stretches each opposing-inset axis independently`() {
        val r = resolve(cbW = 200.0, cbH = 300.0,
            left = 10.0, right = 30.0, top = 20.0, bottom = 20.0)
        assertEquals(160.0, r.widthPx!!, 1e-9)
        assertEquals(260.0, r.heightPx!!, 1e-9)
    }

    @Test fun `single-inset axes never stretch without a ratio to derive them`() {
        // left only: no opposing pair on either axis → identity.
        val r = resolve(cbW = 200.0, cbH = 300.0, left = 10.0)
        assertEquals(null, r.widthPx)
        assertEquals(null, r.heightPx)
    }

    @Test fun `explicit sizes always win - S4 identity when nothing stretches`() {
        // abspos-001/002 shape: explicit size + ratio, NO insets — the
        // resolver must return the identity so the existing SizingApplier
        // aspect-ratio path (already correct) stays untouched.
        val r = resolve(cbW = 358.0, cbH = 600.0, explicitW = 100.0, ratio = 1.0)
        assertEquals(null, r.widthPx)
        assertEquals(null, r.heightPx)
    }

    @Test fun `explicit width plus block stretch - ratio overrides the stretch`() {
        // width:100 + top/bottom:0 + ratio 1 in a 100×500 cb: block would
        // stretch to 500, but the determined inline axis owns the ratio →
        // height 100. Width is NOT injected (author size, never overridden).
        val r = resolve(cbW = 100.0, cbH = 500.0,
            top = 0.0, bottom = 0.0, explicitW = 100.0, ratio = 1.0)
        assertEquals(null, r.widthPx)
        assertEquals(100.0, r.heightPx!!, 1e-9)
    }

    @Test fun `explicit height blocks its own axis and S6 derives the inline one`() {
        // Height explicit → block axis untouched. S6 (wave 38): the
        // definite block size + ratio DETERMINE the inline size
        // (css-sizing-4 §4.1 — 80 × 2 = 160), which wins over the 200px
        // inline stretch. Pre-S6 this pinned the raw 200 stretch, which
        // painted abspos-006 as a full-canvas green bar on both natives
        // against a 100×100 Chromium ref.
        val r = resolve(cbW = 200.0, cbH = 300.0,
            left = 0.0, right = 0.0, explicitH = 80.0, ratio = 2.0)
        assertEquals(160.0, r.widthPx!!, 1e-9)
        assertEquals(null, r.heightPx)
    }

    @Test fun `S6 abspos-006 - ratio beats the inline stretch`() {
        // The live shape: a 500×100 relative parent, all-zero insets,
        // `height: 100px; aspect-ratio: 1/1` → 100×100, not 500×100.
        val r = resolve(cbW = 500.0, cbH = 100.0,
            left = 0.0, right = 0.0, top = 0.0, bottom = 0.0,
            explicitH = 100.0, ratio = 1.0)
        assertEquals(100.0, r.widthPx!!, 1e-9)
        assertEquals(null, r.heightPx)
    }

    @Test fun `S6 does not fire for a non-px explicit height`() {
        // `height: 100%` (abspos-009) arrives as hasExplicitH=true with
        // explicitH=null — no honest basis, so the old stretch stands.
        val r = resolve(cbW = 500.0, cbH = 100.0,
            left = 0.0, right = 0.0, top = 0.0, bottom = 0.0,
            explicitH = null, hasExplicitH = true, ratio = 1.0)
        assertEquals(500.0, r.widthPx!!, 1e-9)
        assertEquals(null, r.heightPx)
    }

    @Test fun `S6 yields to an author width`() {
        // abspos-005 (`width: 100px`, height auto): the inline-first
        // branch still owns the resolution.
        val r = resolve(cbW = 100.0, cbH = 500.0,
            left = 0.0, right = 0.0, top = 0.0, bottom = 0.0,
            explicitW = 100.0, ratio = 1.0)
        assertEquals(null, r.widthPx)
        assertEquals(100.0, r.heightPx!!, 1e-9)
    }

    @Test fun `unknown cb axis disables the stretch on that axis only`() {
        // Height base unknown (auto-sized ancestor): the block pair cannot
        // resolve; the inline pair still can.
        val r = resolve(cbW = 200.0, cbH = null,
            left = 0.0, right = 0.0, top = 0.0, bottom = 0.0)
        assertEquals(200.0, r.widthPx!!, 1e-9)
        assertEquals(null, r.heightPx)
    }

    @Test fun `over-constrained insets clamp at zero, never negative`() {
        val r = resolve(cbW = 100.0, cbH = null, left = 80.0, right = 80.0)
        assertEquals(0.0, r.widthPx!!, 1e-9)
    }

    // ── Wire-wrapper pins (LIVE wave-18 IR shapes) ─────────────────────────

    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    @Test fun `inject rewrites the live abspos-003 child list to exact 100x100 px`() {
        // The EXACT property list from wpt__css-sizing__abspos-003.json's
        // abspos child (BackgroundColor elided — not consumed here).
        val props = listOf(
            prop("AspectRatio", """{"ratio":{"w":1,"h":1},"normalizedRatio":1}"""),
            prop("Position", "\"ABSOLUTE\""),
            prop("Left", """{"px":0}"""),
            prop("Right", """{"px":0}"""),
            prop("Top", """{"px":0}"""),
            prop("Bottom", """{"px":0}"""),
        )
        val out = AbsposInsetStretch.inject(props, ContainingBlock(widthPx = 100f, heightPx = 500f))
        // Injected as the frozen typed-length wire, appended (last wins).
        val width = out.last { it.type == "Width" }.data.toString()
        val height = out.last { it.type == "Height" }.data.toString()
        assertEquals("""{"type":"length","px":100.0}""", width)
        assertEquals("""{"type":"length","px":100.0}""", height)
    }

    @Test fun `inject is the SAME instance when nothing stretches - baseline byte-stability`() {
        // abspos-001 shape: explicit width + ratio, no insets.
        val props = listOf(
            prop("Width", """{"type":"length","px":100}"""),
            prop("AspectRatio", """{"ratio":{"w":1,"h":1},"normalizedRatio":1}"""),
            prop("Position", "\"ABSOLUTE\""),
        )
        assertSame(props, AbsposInsetStretch.inject(props, ContainingBlock(widthPx = 358f)))
    }

    // ── Wave-18 skeptic pins: the S1 honesty rule on the LIVE wire ─────────

    @Test fun `a percent inset - the live BARE-number wire - disables the stretch axis`() {
        // Pinned against the running converter (2026-07): `left: 10%`
        // emits {"type":"Left","data":10.0} — a bare number the offset
        // decoder reads as px. S1 promises percent insets conservatively
        // DISABLE the stretch; without the strict {"px"} side reader the
        // axis stretched to cb − 10 − 30 as if the 10 were px.
        val props = listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("Left", "10.0"),
            prop("Right", """{"px":30}"""),
        )
        assertSame(props, AbsposInsetStretch.inject(props, ContainingBlock(500f, 400f)))
    }

    @Test fun `logical px insets on both sides stretch the inline axis`() {
        // inset-inline-start/end 10px/30px (live typed wire) with no
        // author width: the logical fold reaches the stretch, cb 500 →
        // 500 − 10 − 30 = 460 (matches the iOS twin's strictInsets pin).
        val props = listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("InsetInlineStart", """{"px":10.0}"""),
            prop("InsetInlineEnd", """{"px":30.0}"""),
        )
        val out = AbsposInsetStretch.inject(props, ContainingBlock(500f, 400f))
        assertEquals("""{"type":"length","px":460.0}""", out.last { it.type == "Width" }.data.toString())
        // Exactly ONE injected property (Width) — no Height appended.
        assertEquals(props.size + 1, out.size)
    }

    @Test fun `a physical percent inset does NOT fall through to a logical px inset`() {
        // left:10% (bare 10.0) + inset-inline-start:5px: the applier
        // offsets by the PHYSICAL side (precedence), so the stretch must
        // not resolve against the logical 5 — the axis stays disabled.
        val props = listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("Left", "10.0"),
            prop("InsetInlineStart", """{"px":5.0}"""),
            prop("Right", """{"px":30}"""),
        )
        assertSame(props, AbsposInsetStretch.inject(props, ContainingBlock(500f, 400f)))
    }

    @Test fun `keyword auto on the physical side falls through to the logical longhand`() {
        // top:auto + inset-block-start:20px + bottom:30px — auto IS the
        // initial value, so the logical 20 anchors and the block axis
        // stretches to 400 − 20 − 30 = 350 (mirrors PositionConfig's
        // resolvedTop = top ?: insetBlockStart precedence).
        val props = listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("Top", "\"auto\""),
            prop("InsetBlockStart", """{"px":20.0}"""),
            prop("Bottom", """{"px":30}"""),
        )
        val out = AbsposInsetStretch.inject(props, ContainingBlock(500f, 400f))
        assertEquals("""{"type":"length","px":350.0}""", out.last { it.type == "Height" }.data.toString())
    }

    // ── Wave-31 lane T: S5, the css-tables-3 available-space ceiling ───────

    @Test fun `S5 - absolute-tables-009 clamps the table stretch to the containing block`() {
        // The live IR: cb 100×100, `left:-100; right:0`, no author width.
        // S1 alone hands 100 − (−100) − 0 = 200 and both natives painted a
        // 200×100 green band (wave30-final captures) where the ref paints
        // 100×100 — css-tables-3: an abspos table's available space can
        // never exceed the containing block's.
        val r = resolve(cbW = 100.0, cbH = 100.0, left = -100.0, right = 0.0)
        assertEquals(200.0, r.widthPx!!, 1e-9)
        val table = AbsposInsetStretch.resolve(
            100.0, 100.0, -100.0, 0.0, null, null,
            null, null, false, false, null, /* isTable = */ true,
        )
        assertEquals(100.0, table.widthPx!!, 1e-9)
    }

    @Test fun `S5 - a non-negative inset pair is unaffected by the table clamp`() {
        // cb − start − end is already ≤ cb whenever both insets are ≥ 0,
        // so the clamp is a no-op for the ordinary shape — the reason it
        // can be table-scoped without any per-test carve-out.
        val block = AbsposInsetStretch.resolve(
            500.0, 400.0, 10.0, 30.0, 20.0, 30.0,
            null, null, false, false, null, false,
        )
        val table = AbsposInsetStretch.resolve(
            500.0, 400.0, 10.0, 30.0, 20.0, 30.0,
            null, null, false, false, null, true,
        )
        assertEquals(block.widthPx!!, table.widthPx!!, 1e-9)
        assertEquals(block.heightPx!!, table.heightPx!!, 1e-9)
    }

    @Test fun `S5 - the clamp applies to the block axis too`() {
        val table = AbsposInsetStretch.resolve(
            100.0, 100.0, null, null, -50.0, 0.0,
            null, null, false, false, null, true,
        )
        assertEquals(100.0, table.heightPx!!, 1e-9)
    }

    @Test fun `isTableBox reads the live SCREAMING_SNAKE Display wire`() {
        assertEquals(true, AbsposInsetStretch.isTableBox(listOf(prop("Display", "\"TABLE\""))))
        assertEquals(true, AbsposInsetStretch.isTableBox(listOf(prop("Display", "\"INLINE_TABLE\""))))
        // A table-INTERNAL box is not the table box css-tables-3 §abspos
        // addresses, and an absent Display + absent tag is never a table.
        assertEquals(false, AbsposInsetStretch.isTableBox(listOf(prop("Display", "\"TABLE_CELL\""))))
        assertEquals(false, AbsposInsetStretch.isTableBox(listOf(prop("Display", "\"BLOCK\""))))
        assertEquals(false, AbsposInsetStretch.isTableBox(emptyList()))
    }

    @Test fun `isTableBox falls back to the tag's UA display`() {
        // The live absolute-tables-008…011 shape: a `<table>` with NO
        // Display property (the converter does not serialize UA
        // defaults). Without this channel S5 never fires on them.
        assertEquals(true, AbsposInsetStretch.isTableBox(emptyList(), "table"))
        assertEquals(false, AbsposInsetStretch.isTableBox(emptyList(), "div"))
        assertEquals(false, AbsposInsetStretch.isTableBox(emptyList(), "td"))
        // css-display-3 §2 — a DECLARED display always wins over the tag.
        assertEquals(
            false,
            AbsposInsetStretch.isTableBox(listOf(prop("Display", "\"BLOCK\"")), "table"),
        )
    }

    @Test fun `inject clamps the live absolute-tables-009 table wire`() {
        // End-to-end on the exact live shape — which carries NO Display
        // property, only the `<table>` tag — so the wire wrapper's isTable
        // plumbing (declared keyword AND tag fallback) is pinned too.
        val props = listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("Height", """{"type":"length","px":100}"""),
            prop("Left", """{"px":-100}"""),
            prop("Right", """{"px":0}"""),
        )
        val out = AbsposInsetStretch.inject(props, ContainingBlock(100f, 100f), "table")
        assertEquals("""{"type":"length","px":100.0}""", out.last { it.type == "Width" }.data.toString())
        // Without the tag the same wire is a plain block box → 200.
        val block = AbsposInsetStretch.inject(props, ContainingBlock(100f, 100f))
        assertEquals("""{"type":"length","px":200.0}""", block.last { it.type == "Width" }.data.toString())
    }
}
