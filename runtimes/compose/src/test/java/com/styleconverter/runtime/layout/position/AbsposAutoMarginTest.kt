package com.styleconverter.runtime.layout.position

// Wave 31 (lane T) — JVM pins for abspos AUTO-MARGIN resolution
// (CSS 2.1 §10.3.7 inline / §10.6.4 block). The resolver is pure math
// with an identical signature on iOS (AbsposAutoMarginTests.swift pins
// the same table), and the wire wrapper is pinned against the LIVE
// wave-30 IR shapes (tools/titan/runs/wave30-final/sections/css-tables/
// per-test-ir/wpt__css-tables__absolute-tables-016.json).

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.variables.ContainingBlock
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AbsposAutoMarginTest {

    // ── M1–M6: the pure §10.3.7 / §10.6.4 rule table ──────────────────────

    @Test fun `M1 - no auto margin on the axis resolves nothing`() {
        // §10.3.7 solves only for margins that ARE auto; a declared margin
        // is used as declared, so there is nothing for this rule to do.
        assertNull(
            AbsposAutoMargin.split(
                cb = 160.0, startInset = 0.0, endInset = 0.0, sizePx = 100.0,
                startAuto = false, endAuto = false,
            )
        )
    }

    @Test fun `M2 - an auto end inset keeps the axis unresolved`() {
        // The over-constrained branch needs left, width AND right non-auto.
        // With `right: auto` the spec sets the auto margins to 0 and solves
        // for right instead — i.e. the margins contribute nothing, which is
        // the pre-wave-31 behaviour. This is the filter-effects
        // backdrop-filter-basic-blur shape (`margin: 0px auto`, no `right`).
        assertNull(
            AbsposAutoMargin.split(
                cb = 160.0, startInset = 100.0, endInset = null, sizePx = 150.0,
                startAuto = true, endAuto = true,
            )
        )
    }

    @Test fun `M2 - an indefinite used size keeps the axis unresolved`() {
        assertNull(
            AbsposAutoMargin.split(
                cb = 160.0, startInset = 0.0, endInset = 0.0, sizePx = null,
                startAuto = true, endAuto = true,
            )
        )
    }

    @Test fun `M2 - an unknown containing block keeps the axis unresolved`() {
        assertNull(
            AbsposAutoMargin.split(
                cb = null, startInset = 0.0, endInset = 0.0, sizePx = 100.0,
                startAuto = true, endAuto = true,
            )
        )
    }

    @Test fun `M4 - absolute-tables-016 both margins auto split the free space`() {
        // The live pin: a 100px box with `inset: 0` in a 160px containing
        // block → free 60 → 30 each. This is the exact 30px the wave30-final
        // Android capture was missing on the BLOCK axis.
        val r = AbsposAutoMargin.split(
            cb = 160.0, startInset = 0.0, endInset = 0.0, sizePx = 100.0,
            startAuto = true, endAuto = true,
        )!!
        assertEquals(30.0, r.startPx, 1e-9)
        assertEquals(30.0, r.endPx, 1e-9)
    }

    @Test fun `M4 - non-zero insets are subtracted before the split`() {
        // The whole reason this is arithmetic and not an alignment: an
        // alignment centring would answer 30 here regardless of the insets;
        // §10.3.7 answers (160 − 20 − 100 − 0)/2 = 20, used x = 20 + 20 = 40.
        val r = AbsposAutoMargin.split(
            cb = 160.0, startInset = 20.0, endInset = 0.0, sizePx = 100.0,
            startAuto = true, endAuto = true,
        )!!
        assertEquals(20.0, r.startPx, 1e-9)
        assertEquals(20.0, r.endPx, 1e-9)
    }

    @Test fun `M5 - negative free space pins the LTR start margin at zero`() {
        // §10.3.7: "unless this would make them negative, in which case
        // when the direction of the containing block is ltr, set
        // margin-left to 0 and solve for margin-right".
        val r = AbsposAutoMargin.split(
            cb = 100.0, startInset = 0.0, endInset = 0.0, sizePx = 160.0,
            startAuto = true, endAuto = true,
        )!!
        assertEquals(0.0, r.startPx, 1e-9)
        assertEquals(-60.0, r.endPx, 1e-9)
    }

    @Test fun `M6 - a single auto margin absorbs all the free space`() {
        val start = AbsposAutoMargin.split(
            cb = 160.0, startInset = 0.0, endInset = 0.0, sizePx = 100.0,
            startAuto = true, endAuto = false,
        )!!
        assertEquals(60.0, start.startPx, 1e-9)
        assertEquals(0.0, start.endPx, 1e-9)
        val end = AbsposAutoMargin.split(
            cb = 160.0, startInset = 0.0, endInset = 0.0, sizePx = 100.0,
            startAuto = false, endAuto = true,
        )!!
        assertEquals(0.0, end.startPx, 1e-9)
        assertEquals(60.0, end.endPx, 1e-9)
    }

    // ── M3/M6 with a DECLARED opposite margin (skeptic bugs 1 & 2) ────────
    // Chromium numbers from _diag31/skeptic/chromium-automargin-probe.mjs
    // (a 160px containing block, `left:0;right:0;width:100px`), which also
    // asserts native == Chromium on every case listed here.

    @Test fun `M3 - BUG 1 - a declared opposite margin is subtracted from the free space`() {
        // `margin-left:auto; margin-right:20px` — §10.3.7 solves
        // margin-left = 160 − 0 − 100 − 0 − 20 = 40. Chromium probe case C:
        // used x=40, computed margins `40px 20px`. The pre-fix table left
        // the 20px out of `free` and answered 60 (and the fold then deleted
        // the declared 20px, painting x=0).
        val r = AbsposAutoMargin.split(
            cb = 160.0, startInset = 0.0, endInset = 0.0, sizePx = 100.0,
            startAuto = true, endAuto = false, endMarginPx = 20.0,
        )!!
        assertEquals(40.0, r.startPx, 1e-9)
        // The non-auto side reports its DECLARED value, so the pair is
        // exactly Chromium's computed margin pair.
        assertEquals(20.0, r.endPx, 1e-9)
    }

    @Test fun `M3 - BUG 2 - a declared percent start margin is subtracted and reported`() {
        // `margin-left:10%; margin-right:auto`. §8.3: the percent resolves
        // against the containing block's inline size → 16px; §10.3.7 then
        // solves margin-right = 160 − 0 − 100 − 0 − 16 = 44. Chromium probe
        // case F: used x=16, computed margins `16px 44px`. The pre-fix table
        // answered 0/60 and the fold rewrote MarginLeft to 0 → x=0.
        val r = AbsposAutoMargin.split(
            cb = 160.0, startInset = 0.0, endInset = 0.0, sizePx = 100.0,
            startAuto = false, endAuto = true, startMarginPx = 16.0,
        )!!
        assertEquals(16.0, r.startPx, 1e-9)
        assertEquals(44.0, r.endPx, 1e-9)
    }

    @Test fun `M3 - a single auto margin may solve NEGATIVE, Chromium does not clamp`() {
        // `margin-left:auto; margin-right:120px` → 60 − 120 = −60. Chromium
        // probe case N: used x=−60, computed margins `-60px 120px`. Only the
        // BOTH-auto branch has the §10.3.7 negative special case (M5).
        val r = AbsposAutoMargin.split(
            cb = 160.0, startInset = 0.0, endInset = 0.0, sizePx = 100.0,
            startAuto = true, endAuto = false, endMarginPx = 120.0,
        )!!
        assertEquals(-60.0, r.startPx, 1e-9)
        assertEquals(120.0, r.endPx, 1e-9)
    }

    @Test fun `M3 - a declared margin on a BOTH-auto axis is ignored, not double-counted`() {
        // Both sides auto means both are unknowns; whatever the caller
        // passes for the declared values must not enter `free`. Keeps the
        // absolute-tables-016 answer at 30/30 no matter what the reader
        // hands over for an `auto` side.
        val r = AbsposAutoMargin.split(
            cb = 160.0, startInset = 0.0, endInset = 0.0, sizePx = 100.0,
            startAuto = true, endAuto = true,
            startMarginPx = 999.0, endMarginPx = 999.0,
        )!!
        assertEquals(30.0, r.startPx, 1e-9)
        assertEquals(30.0, r.endPx, 1e-9)
    }

    @Test fun `M2b - an unreadable declared margin keeps the axis unresolved`() {
        // An `em` / unresolved calc() opposite an auto margin leaves the
        // §10.3.7 equation with two unknowns. Guessing zero would silently
        // move the box, so the rule declines and the pre-wave-31 render
        // stands — the same honesty rule strictInsetPx enforces for insets.
        assertNull(
            AbsposAutoMargin.split(
                cb = 160.0, startInset = 0.0, endInset = 0.0, sizePx = 100.0,
                startAuto = true, endAuto = false, endMarginPx = null,
            )
        )
        assertNull(
            AbsposAutoMargin.split(
                cb = 160.0, startInset = 0.0, endInset = 0.0, sizePx = 100.0,
                startAuto = false, endAuto = true, startMarginPx = null,
            )
        )
    }

    @Test fun `M3 - percent margins use the cb INLINE size on the BLOCK axis too`() {
        // CSS 2.1 §8.3, pinned with a RECTANGULAR 160×120 containing block
        // so the two candidate bases give different answers. Chromium probe
        // case K (`margin-top:10%; margin-bottom:auto`, height 100): used
        // y=16, computed block margins `16px 4px` — i.e. the 10% is 16
        // (from the 160 WIDTH), not 12 (from the 120 height), and the auto
        // bottom margin takes 120 − 100 − 16 = 4.
        val r = AbsposAutoMargin.split(
            cb = 120.0, startInset = 0.0, endInset = 0.0, sizePx = 100.0,
            startAuto = false, endAuto = true, startMarginPx = 16.0,
        )!!
        assertEquals(16.0, r.startPx, 1e-9)
        assertEquals(4.0, r.endPx, 1e-9)
    }

    @Test fun `M4 - a zero-free-space stretch axis resolves both margins to zero`() {
        // The css-align abspos__*-stretch-auto-margins shape: `inset: 50px`
        // with the size STRETCHED to cb − 100 leaves no free space, so the
        // margins are 0 and the wire wrapper takes its M7 identity guard.
        val r = AbsposAutoMargin.split(
            cb = 400.0, startInset = 50.0, endInset = 50.0, sizePx = 300.0,
            startAuto = true, endAuto = true,
        )!!
        assertEquals(0.0, r.startPx, 1e-9)
        assertEquals(0.0, r.endPx, 1e-9)
    }

    // ── Chromium probe parity (every case, both axes) ─────────────────────
    // One row per AXIS of every case in
    // _diag31/skeptic/chromium-automargin-probe.mjs, carrying the numbers
    // Chromium actually painted. [used] composes [split] exactly the way
    // `inject` folds it, so this table and the probe's own JS mirror of the
    // rule table can only agree if the native rule table is right.

    /** One axis' inputs: cb extent, insets, used size, auto flags,
     *  declared margins in px (percents pre-resolved against the cb's
     *  INLINE size per §8.3 — noted per row where it matters). */
    private class Ax(
        val cb: Double?, val start: Double?, val end: Double?, val size: Double?,
        val sAuto: Boolean = false, val eAuto: Boolean = false,
        val sDecl: Double = 0.0, val eDecl: Double = 0.0,
    )

    /** (used start-edge offset, used start margin, used end margin) — the
     *  three quantities the probe reads out of Chromium. An unresolved
     *  axis (M1/M2/M2b) keeps its declared bands and an `auto` margin
     *  there is used as 0, which is the pre-wave-31 behaviour. */
    private fun used(a: Ax): Triple<Double, Double, Double> {
        val declStart = if (a.sAuto) 0.0 else a.sDecl
        val declEnd = if (a.eAuto) 0.0 else a.eDecl
        val axis = AbsposAutoMargin.split(
            cb = a.cb, startInset = a.start, endInset = a.end, sizePx = a.size,
            startAuto = a.sAuto, endAuto = a.eAuto,
            startMarginPx = declStart, endMarginPx = declEnd,
        ) ?: return Triple((a.start ?: 0.0) + declStart, declStart, declEnd)
        // Only a SOLVED auto start margin moves the inset; a declared one
        // paints its own band and must not be counted twice.
        return Triple(
            (a.start ?: 0.0) + (if (a.sAuto) axis.startPx else declStart),
            axis.startPx, axis.endPx,
        )
    }

    private fun assertAxis(case: String, a: Ax, offset: Double, sm: Double, em: Double) {
        val (o, s, e) = used(a)
        assertEquals("$case offset", offset, o, 1e-9)
        assertEquals("$case start margin", sm, s, 1e-9)
        assertEquals("$case end margin", em, e, 1e-9)
    }

    @Test fun `Chromium probe parity - the INLINE axis of every probe case`() {
        // A 160px cb, `left:0;right:0;width:100px` unless stated.
        val base = { s: Boolean, e: Boolean, sd: Double, ed: Double ->
            Ax(160.0, 0.0, 0.0, 100.0, s, e, sd, ed)
        }
        assertAxis("A margin:auto", base(true, true, 0.0, 0.0), 30.0, 30.0, 30.0)
        assertAxis("B ml:auto mr:0", base(true, false, 0.0, 0.0), 60.0, 60.0, 0.0)
        assertAxis("C ml:auto mr:20px", base(true, false, 0.0, 20.0), 40.0, 40.0, 20.0)
        assertAxis("D ml:0 mr:auto", base(false, true, 0.0, 0.0), 0.0, 0.0, 60.0)
        // E moves the start inset to 20 — the case an alignment-based
        // centring gets wrong (it would answer 30, not 20+20=40).
        assertAxis("E left:20 margin:auto",
            Ax(160.0, 20.0, 0.0, 100.0, sAuto = true, eAuto = true), 40.0, 20.0, 20.0)
        // F/G: `10%` of the 160px cb inline size = 16.
        assertAxis("F ml:10% mr:auto", base(false, true, 16.0, 0.0), 16.0, 16.0, 44.0)
        assertAxis("G ml:10% mr:10%", base(false, false, 16.0, 16.0), 16.0, 16.0, 16.0)
        assertAxis("H width:160 right:60 margin:auto",
            Ax(160.0, 0.0, 60.0, 160.0, sAuto = true, eAuto = true), 0.0, 0.0, -60.0)
        assertAxis("I ml:0 mr:0", base(false, false, 0.0, 0.0), 0.0, 0.0, 0.0)
        // J has `right: auto` → M2, so the auto margins stay 0 and the box
        // sits at its declared left.
        assertAxis("J left:100 right:auto margin-inline:auto",
            Ax(160.0, 100.0, null, 150.0, sAuto = true, eAuto = true), 100.0, 0.0, 0.0)
        assertAxis("K rect cb, inline margins 0", base(false, false, 0.0, 0.0), 0.0, 0.0, 0.0)
        assertAxis("L rect cb, ml:10% mr:10%", base(false, false, 16.0, 16.0), 16.0, 16.0, 16.0)
        assertAxis("M rect cb, inline margins 0", base(false, false, 0.0, 0.0), 0.0, 0.0, 0.0)
        assertAxis("N ml:auto mr:120px", base(true, false, 0.0, 120.0), -60.0, -60.0, 120.0)
    }

    @Test fun `Chromium probe parity - the BLOCK axis of every probe case`() {
        // A 160px-tall cb, `top:0;bottom:0;height:100px` unless stated;
        // K/L/M use the RECTANGULAR 160×120 cb, where a `10%` margin is
        // still 16 (the 160 WIDTH, §8.3) and not 12.
        val sq = { s: Boolean, e: Boolean, sd: Double, ed: Double ->
            Ax(160.0, 0.0, 0.0, 100.0, s, e, sd, ed)
        }
        val rect = { s: Boolean, e: Boolean, sd: Double, ed: Double ->
            Ax(120.0, 0.0, 0.0, 100.0, s, e, sd, ed)
        }
        assertAxis("A margin:auto", sq(true, true, 0.0, 0.0), 30.0, 30.0, 30.0)
        assertAxis("B block margins 0", sq(false, false, 0.0, 0.0), 0.0, 0.0, 0.0)
        assertAxis("C block margins 0", sq(false, false, 0.0, 0.0), 0.0, 0.0, 0.0)
        assertAxis("D block margins 0", sq(false, false, 0.0, 0.0), 0.0, 0.0, 0.0)
        assertAxis("E top:20 margin:auto",
            Ax(160.0, 20.0, 0.0, 100.0, sAuto = true, eAuto = true), 40.0, 20.0, 20.0)
        assertAxis("F block margins 0", sq(false, false, 0.0, 0.0), 0.0, 0.0, 0.0)
        assertAxis("G block margins 0", sq(false, false, 0.0, 0.0), 0.0, 0.0, 0.0)
        assertAxis("H margin:auto", sq(true, true, 0.0, 0.0), 30.0, 30.0, 30.0)
        assertAxis("I mt:auto mb:auto", sq(true, true, 0.0, 0.0), 30.0, 30.0, 30.0)
        assertAxis("J bottom:auto", Ax(160.0, -50.0, null, 100.0), -50.0, 0.0, 0.0)
        assertAxis("K mt:10% mb:auto", rect(false, true, 16.0, 0.0), 16.0, 16.0, 4.0)
        assertAxis("L mt:10% mb:10%", rect(false, false, 16.0, 16.0), 16.0, 16.0, 16.0)
        assertAxis("M mt:auto mb:10%", rect(true, false, 0.0, 16.0), 4.0, 4.0, 16.0)
        assertAxis("N block margins 0", sq(false, false, 0.0, 0.0), 0.0, 0.0, 0.0)
    }

    // ── The wire wrapper (live IR shapes) ─────────────────────────────────

    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    /** absolute-tables-016's abspos child, verbatim from the live IR. */
    private fun tables016(): List<IRProperty> = listOf(
        prop("Display", "\"TABLE\""),
        prop("Position", "\"ABSOLUTE\""),
        prop("Width", """{"type":"length","px":100}"""),
        prop("Height", """{"type":"length","px":100}"""),
        prop("Top", """{"px":0}"""),
        prop("Right", """{"px":0}"""),
        prop("Bottom", """{"px":0}"""),
        prop("Left", """{"px":0}"""),
        prop("MarginTop", "\"auto\""),
        prop("MarginRight", "\"auto\""),
        prop("MarginBottom", "\"auto\""),
        prop("MarginLeft", "\"auto\""),
    )

    private fun pxOf(properties: List<IRProperty>, type: String): Double? =
        properties.lastOrNull { it.type == type }
            ?.data?.let { Json.decodeFromJsonElement(PxProbe.serializer(), it) }?.px

    @kotlinx.serialization.Serializable
    private data class PxProbe(val px: Double)

    @Test fun `inject folds both solved start margins into the start insets`() {
        // The absolute-tables-016 end-to-end pin: 160×160 containing block,
        // 100×100 box, all insets 0, all margins auto → the box's used
        // position is (30, 30) from the containing block's origin, which is
        // where the Chromium ref paints the green square.
        val out = AbsposAutoMargin.inject(
            tables016(), ContainingBlock(widthPx = 160f, heightPx = 160f),
        )
        assertEquals(30.0, pxOf(out, "Left")!!, 1e-9)
        assertEquals(30.0, pxOf(out, "Top")!!, 1e-9)
    }

    @Test fun `inject replaces every auto margin so no aligner re-applies the rule`() {
        // ComponentRenderer.autoMarginAlignment matches with `any`, so the
        // auto entries must be REMOVED, not merely shadowed by an append.
        val out = AbsposAutoMargin.inject(
            tables016(), ContainingBlock(widthPx = 160f, heightPx = 160f),
        )
        for (side in listOf("MarginLeft", "MarginRight", "MarginTop", "MarginBottom")) {
            assertEquals(
                "exactly one $side survives the rewrite",
                1, out.count { it.type == side },
            )
            assertEquals("$side is solved to 0", 0.0, pxOf(out, side)!!, 1e-9)
        }
        assertNull(
            "no auto margin keyword may survive",
            out.firstOrNull { it.data.toString() == "\"auto\"" },
        )
    }

    /** A 100×100 abspos box with `inset: 0` and the given margin wires. */
    private fun box(vararg margins: Pair<String, String>): List<IRProperty> = listOf(
        prop("Position", "\"ABSOLUTE\""),
        prop("Width", """{"type":"length","px":100}"""),
        prop("Height", """{"type":"length","px":100}"""),
        prop("Top", """{"px":0}"""),
        prop("Right", """{"px":0}"""),
        prop("Bottom", """{"px":0}"""),
        prop("Left", """{"px":0}"""),
    ) + margins.map { (t, json) -> prop(t, json) }

    @Test fun `inject BUG 1 - a declared opposite margin survives and shifts the inset`() {
        // `margin-left:auto; margin-right:20px` in a 160px cb. Chromium
        // (probe case C) paints the border box at x=40 with computed
        // margins `40px 20px`. The fold folds the SOLVED 40 into `Left`,
        // drops the `auto` keyword, and leaves the declared 20px alone so
        // it still paints its own band — before this fix the box landed at
        // x=0 with the 20px deleted.
        val out = AbsposAutoMargin.inject(
            box(
                "MarginLeft" to "\"auto\"", "MarginRight" to """{"px":20}""",
                "MarginTop" to """{"px":0}""", "MarginBottom" to """{"px":0}""",
            ),
            ContainingBlock(widthPx = 160f, heightPx = 160f),
        )
        assertEquals(40.0, pxOf(out, "Left")!!, 1e-9)
        assertEquals("the solved auto margin is zeroed", 0.0, pxOf(out, "MarginLeft")!!, 1e-9)
        assertEquals("exactly one MarginRight survives", 1, out.count { it.type == "MarginRight" })
        assertEquals("the declared margin is untouched", 20.0, pxOf(out, "MarginRight")!!, 1e-9)
        // The block axis has no auto margin at all — M1 leaves it alone.
        assertEquals(0.0, pxOf(out, "Top")!!, 1e-9)
    }

    @Test fun `inject BUG 2 - a declared percent start margin is preserved verbatim`() {
        // `margin-left:10%; margin-right:auto` in a 160px cb. Chromium
        // (probe case F) paints x=16 with computed margins `16px 44px`.
        // The used position here is `Left(0) + the 16px band MarginApplier
        // paints from the SAME §8.3 basis`, so the inset must NOT move and
        // the percent wire must survive — before this fix MarginLeft was
        // rewritten to 0 and the box landed at x=0.
        val out = AbsposAutoMargin.inject(
            box(
                // Bare number == percent on a margin longhand (see
                // extractLength's kdoc), which is how the live converter
                // emits `margin-left: 10%`.
                "MarginLeft" to "10", "MarginRight" to "\"auto\"",
                "MarginTop" to """{"px":0}""", "MarginBottom" to """{"px":0}""",
            ),
            ContainingBlock(widthPx = 160f, heightPx = 160f),
        )
        assertEquals("the start inset must not move", 0.0, pxOf(out, "Left")!!, 1e-9)
        assertEquals("exactly one MarginLeft survives", 1, out.count { it.type == "MarginLeft" })
        assertEquals(
            "the declared percent margin is preserved verbatim",
            "10", out.last { it.type == "MarginLeft" }.data.toString(),
        )
        // The auto END margin still loses its keyword: MarginConfig's
        // horizontalAutoAlignment would otherwise push the box in an
        // expanded frame on top of the arithmetic.
        assertEquals(0.0, pxOf(out, "MarginRight")!!, 1e-9)
    }

    @Test fun `inject resolves BLOCK-axis percents against the cb INLINE size`() {
        // CSS 2.1 §8.3 with a RECTANGULAR 160×120 containing block, the
        // shape that discriminates the two candidate bases. Chromium probe
        // case M (`margin-top:auto; margin-bottom:10%`, height 100): used
        // y=4, computed block margins `4px 16px`.
        //   width basis  → bottom 16 → top = 120 − 100 − 16 = 4  ← Chromium
        //   height basis → bottom 12 → top = 8                   ← wrong
        // so the injected `Top` alone pins the basis.
        val out = AbsposAutoMargin.inject(
            box(
                "MarginLeft" to """{"px":0}""", "MarginRight" to """{"px":0}""",
                "MarginTop" to "\"auto\"", "MarginBottom" to "10",
            ),
            ContainingBlock(widthPx = 160f, heightPx = 120f),
        )
        assertEquals(4.0, pxOf(out, "Top")!!, 1e-9)
        assertEquals(0.0, pxOf(out, "MarginTop")!!, 1e-9)
        assertEquals("the declared percent margin survives", 1, out.count { it.type == "MarginBottom" })
        assertEquals("10", out.last { it.type == "MarginBottom" }.data.toString())
    }

    @Test fun `inject is identity when a declared margin cannot be read in px`() {
        // M2b end-to-end: `margin-right: 2em` opposite an auto margin. The
        // equation has two unknowns, so the rule declines rather than
        // guessing zero and silently moving the box.
        val props = box(
            "MarginLeft" to "\"auto\"",
            "MarginRight" to """{"original":{"v":2,"u":"EM"}}""",
            "MarginTop" to """{"px":0}""", "MarginBottom" to """{"px":0}""",
        )
        assertSame(
            props,
            AbsposAutoMargin.inject(props, ContainingBlock(widthPx = 160f, heightPx = 160f)),
        )
    }

    @Test fun `inject is identity when the split is zero on both axes`() {
        // The css-align stretch shape — same instance back, the
        // frozen-baseline byte-stability rule.
        val props = listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("Top", """{"px":50}"""),
            prop("Right", """{"px":50}"""),
            prop("Bottom", """{"px":50}"""),
            prop("Left", """{"px":50}"""),
            prop("Width", """{"type":"length","px":300}"""),
            prop("Height", """{"type":"length","px":300}"""),
            prop("MarginTop", "\"auto\""),
            prop("MarginBottom", "\"auto\""),
        )
        assertSame(
            props,
            AbsposAutoMargin.inject(props, ContainingBlock(widthPx = 400f, heightPx = 400f)),
        )
    }

    @Test fun `inject is identity when a margin is not auto`() {
        val props = listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("Top", """{"px":0}"""),
            prop("Bottom", """{"px":0}"""),
            prop("Height", """{"type":"length","px":100}"""),
            prop("MarginTop", """{"px":0}"""),
            prop("MarginBottom", """{"px":0}"""),
        )
        assertSame(
            props,
            AbsposAutoMargin.inject(props, ContainingBlock(widthPx = 160f, heightPx = 160f)),
        )
    }

    @Test fun `inject is identity for the backdrop-filter auto-end-inset shape`() {
        // `margin: 0px auto; top: -50px; left: 100px` with no right/bottom —
        // M2. This test's render is owned by another lane; the pin exists so
        // this rewrite provably cannot move it.
        val props = listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("Top", """{"px":-50}"""),
            prop("Left", """{"px":100}"""),
            prop("Width", """{"type":"length","px":150}"""),
            prop("Height", """{"type":"length","px":100}"""),
            prop("MarginLeft", "\"auto\""),
            prop("MarginRight", "\"auto\""),
            prop("MarginTop", """{"px":0}"""),
            prop("MarginBottom", """{"px":0}"""),
        )
        assertSame(
            props,
            AbsposAutoMargin.inject(props, ContainingBlock(widthPx = 390f, heightPx = 600f)),
        )
    }

    @Test fun `inject is identity when the containing block has no known extent`() {
        val props = tables016()
        assertSame(props, AbsposAutoMargin.inject(props, ContainingBlock()))
    }
}
