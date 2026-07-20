package com.styleconverter.runtime.layout.position

// Wave 18 (RC2) — JVM pins for abspos inset-stretch sizing
// (css-position-3 §3.5 + the css-sizing-4 §5 aspect-ratio interaction).
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

    @Test fun `explicit height blocks the ratio derivation of that axis`() {
        // Inline stretch present but height explicit → height untouched,
        // width injected from the stretch alone.
        val r = resolve(cbW = 200.0, cbH = 300.0,
            left = 0.0, right = 0.0, explicitH = 80.0, ratio = 2.0)
        assertEquals(200.0, r.widthPx!!, 1e-9)
        assertEquals(null, r.heightPx)
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
}
