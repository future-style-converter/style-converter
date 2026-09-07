package com.styleconverter.test.screenshot

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Wave 22 (B-RC2) — the shared E1–E8 pin table for [StaticEmMargin], the
 * em-resolving static block-margin classifier the composed WPT canvas feeds
 * into the CSS 2.1 §8.3.1 root-stack fold — plus the wave-45 (H0) F3/F4
 * pins for the UA-default em base (absent FontSize ⇒ 16px, or the 13px
 * monospace fixed default via MonospaceUAFontSize). Every expected value
 * here is IDENTICAL to the Swift twin's (runtimes/swiftui Tests/…/
 * StaticEmMarginsTests.swift) so the two implementations cannot drift.
 *
 * Plain junit:4.13.2, no Android runtime — the IR model + kotlinx JSON types
 * ride the `:runtime` project dependency (same pattern as OutOfFlowAnchorTest).
 *
 * The wire literals below are copied from the LIVE per-test IR at
 * tools/titan/runs/wave21-final/sections/css-text-decor/per-test-ir/
 * wpt__css-text-decor__text-decoration-dotted-001.json, so a converter shape
 * change fails here first.
 */
class StaticEmMarginsTest {

    /** One IR property from a JSON literal (the frozen wire byte shape). */
    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    /** The dotted-001 relative-margin wire: `{"original":{"v":N,"u":"EM"}}`. */
    private fun emMargin(type: String, v: Double) =
        prop(type, """{"original":{"v":$v,"u":"EM"}}""")

    /** The dotted-001 font-size wire: `{"px":N,"original":{…}}`. */
    private fun fontSize(px: Double) =
        prop("FontSize", """{"px":$px,"original":{"type":"length","px":$px}}""")

    // ── E1: the absolute-px lane is unchanged ──────────────────────────────

    @Test
    fun e1_absolutePxEdge_resolvesToItself() {
        // E1: the safe-001 wire `{"px":20}` — identical to the narrow
        // classifier's Exact branch (BlockMarginCollapse.plainPxOrNull).
        val p = listOf(prop("MarginTop", """{"px":20}"""), prop("MarginBottom", """{"px":20}"""))
        assertEquals(20f to 20f, StaticEmMargin.verticalEdges(p))
    }

    @Test
    fun e1_negativePxEdge_staysOutOfScope() {
        // E1 floor: §8.3.1's negative-margin rules are NOT emulated by this
        // lane, so a negative edge bails the whole root (unchanged).
        val p = listOf(prop("MarginTop", """{"px":-10}"""), prop("MarginBottom", """{"px":20}"""))
        assertNull(StaticEmMargin.verticalEdges(p))
    }

    // ── E2/E3: em resolution ───────────────────────────────────────────────

    @Test
    fun e2_emEdgeWithResolvedPxFallback_takesThePx() {
        // E2: a converter-pre-resolved em (`{"px":46,"original":{v,u:EM}}`).
        // Kotlin's extractLength collapses this to Exact upstream and Swift's
        // keeps the unit hint with a pxFallback — both rule tables answer 46.
        val p = listOf(
            prop("MarginTop", """{"px":46,"original":{"v":0.5,"u":"EM"}}"""),
            prop("MarginBottom", """{"px":46,"original":{"v":0.5,"u":"EM"}}"""),
        )
        assertEquals(46f to 46f, StaticEmMargin.verticalEdges(p))
    }

    @Test
    fun e3_dotted001Wire_halfEmOver92pxFont_resolvesTo46() {
        // E3 — THE defect case. `margin: .5em` + `font-size: 92px` on the
        // three dotted-001 divs: 0.5 × 92 = 46px per edge. The §8.3.1 fold
        // then collapses the abutting 46/46 pair to ONE 46px gap, where the
        // pre-fix bail painted both (measured Android inter-div 92px).
        val p = listOf(
            fontSize(92.0),
            emMargin("MarginTop", 0.5),
            emMargin("MarginRight", 0.5),
            emMargin("MarginBottom", 0.5),
            emMargin("MarginLeft", 0.5),
        )
        assertEquals(46f to 46f, StaticEmMargin.verticalEdges(p))
    }

    @Test
    fun e3_logicalEmEdges_resolveThroughTheSamePhysicalFold() {
        // E3 (logical): margin-block-start/end map to top/bottom in
        // horizontal-tb (css-logical-1 §4.1) via MarginConfig.resolve.
        val p = listOf(
            fontSize(20.0),
            emMargin("MarginBlockStart", 1.0),
            emMargin("MarginBlockEnd", 2.0),
        )
        assertEquals(20f to 40f, StaticEmMargin.verticalEdges(p))
    }

    @Test
    fun e3_ownFontSizeIsTheBase_notTheRootDefault() {
        // css-values-4 §6.1.1: `em` on a margin resolves against the
        // element's OWN computed font-size — 92, never the 16px root.
        assertEquals(92f, StaticEmMargin.ownFontSizePx(listOf(fontSize(92.0))))
    }

    // ── E4 (narrowed, wave 45 H0): declared-but-unresolvable base ⇒ bail ──

    @Test
    fun e4_relativeOwnFontSize_isNotAnHonestBase() {
        // `font-size: 2em` decodes to Relative — no absolute px, so the em
        // margin has nothing to multiply against and the root bails. This
        // is the E4 bail KEPT by wave 45: a DECLARED size that needs the
        // inheritance channel (em/%/var()/calc()) is genuinely
        // unresolvable, unlike the ABSENT-size case below.
        val p = listOf(
            prop("FontSize", """{"original":{"v":2,"u":"EM"}}"""),
            emMargin("MarginTop", 0.5),
        )
        assertNull(StaticEmMargin.ownFontSizePx(p))
        assertNull(StaticEmMargin.verticalEdges(p))
    }

    // ── F3/F4 (wave 45, H0): ABSENT FontSize resolves the UA ladder ───────

    @Test
    fun f4_floatsClearMulticol002Wire_absentFontSize_resolvesEmAt16() {
        // F4 — the verbatim live wire of tools/titan/runs/wave44-final/
        // sections/CSS2/per-test-ir/wpt__CSS2__floats-clear__floats-clear-
        // multicol-002.json root __1: `margin: 1em` on a multicol root with
        // NO FontSize and NO FontFamily. The base is the UA `medium`
        // default 16px (a composed root is a body-level child), so both
        // vertical edges resolve to 1em × 16 = 16. Pre-fix E4 bailed here
        // and the stack double-spaced (measured +16px, X3/X4).
        val p = listOf(
            emMargin("MarginTop", 1.0),
            emMargin("MarginRight", 1.0),
            emMargin("MarginBottom", 1.0),
            emMargin("MarginLeft", 1.0),
            prop("BorderTopStyle", "\"SOLID\""),
            prop("BorderRightStyle", "\"SOLID\""),
            prop("BorderBottomStyle", "\"SOLID\""),
            prop("BorderLeftStyle", "\"SOLID\""),
            prop("BorderTopColor", """{"srgb":{"r":0.7529411764705882,"g":0.7529411764705882,"b":0.7529411764705882},"original":"silver"}"""),
            prop("Width", """{"type":"length","px":300}"""),
            prop("ColumnWidth", """{"px":100}"""),
            prop("ColumnGap", """{"type":"length","px":0}"""),
            prop("ColumnFill", "\"AUTO\""),
            prop("Height", """{"type":"length","px":100}"""),
        )
        assertEquals(16f, StaticEmMargin.ownFontSizePx(p))
        assertEquals(16f to 16f, StaticEmMargin.verticalEdges(p))
    }

    @Test
    fun f3_discardMulticol001Wire_monospaceFirstFamily_resolvesEmAt13() {
        // F3 — the verbatim live wire of …/css-overflow/per-test-ir/
        // wpt__css-overflow__line-clamp__discard__discard-multicol-001.json
        // roots __1/__2: FontFamily ["monospace"] FIRST + `margin: 1em`,
        // no FontSize. MonospaceUAFontSize's 13px fixed default (the value
        // the frozen refs rasterised) is the base ⇒ 13/13 — consulted
        // through the single quirk owner, never re-derived here.
        val p = listOf(
            prop("FontFamily", """["monospace"]"""),
            prop("RowGap", """{"type":"length","original":{"v":1,"u":"CH"}}"""),
            prop("ColumnGap", """{"type":"length","original":{"v":1,"u":"CH"}}"""),
            prop("Width", """{"type":"length","original":{"v":27,"u":"CH"}}"""),
            prop("ColumnCount", "3"),
            prop("Height", """{"type":"length","original":{"v":2,"u":"LH"}}"""),
            prop("BorderTopWidth", """{"px":1}"""),
            prop("BorderTopStyle", "\"SOLID\""),
            emMargin("MarginTop", 1.0),
            emMargin("MarginRight", 1.0),
            emMargin("MarginBottom", 1.0),
            emMargin("MarginLeft", 1.0),
            prop("Continue", "\"DISCARD\""),
        )
        assertEquals(13f, StaticEmMargin.ownFontSizePx(p))
        assertEquals(13f to 13f, StaticEmMargin.verticalEdges(p))
    }

    @Test
    fun f4_concreteFaceFirstFamily_keepsTheStandard16Default() {
        // The quirk keys on the FIRST entry being the monospace GENERIC
        // (Blink's rule — see MonospaceUAFontSize's header): a concrete
        // face first keeps the standard 16px default even when the list
        // ends in the generic.
        val p = listOf(
            prop("FontFamily", """["Courier New","monospace"]"""),
            emMargin("MarginTop", 1.0),
        )
        assertEquals(16f, StaticEmMargin.ownFontSizePx(p))
        assertEquals(16f to 0f, StaticEmMargin.verticalEdges(p))
    }

    @Test
    fun f4_floatsClearMulticol002_stackFoldEmitsOneSingle16pxGap() {
        // The consequence pin — the measured defect this wave closes. The
        // fixture stacks a bare `<p>` (UA margins 16/16) above the 1em-
        // margin multicol root. Post-fix the root classifies (16,16), so
        // rootStackMargin takes R2 (stripDeclared) and the §8.3.1 fold
        // emits ONE max(16,16) = 16px gap between the two roots; the
        // pre-fix bail (R4/R5) emitted the 16px UA gap AND left
        // MarginApplier rendering the full 16px margin — 32px total, the
        // measured +16 shift (Android border-top y=124 vs the ref's 108).
        val multicol = listOf(emMargin("MarginTop", 1.0), emMargin("MarginBottom", 1.0))
        val edges = StaticEmMargin.verticalEdges(multicol)
        assertEquals(16f to 16f, edges)
        val plans = listOf(
            // The fixture's root __0: `<p>`, zero properties ⇒ R1 pure UA.
            rootStackMargin("p", declaresTop = false, declaresBottom = false,
                staticDeclaredPx = 0f to 0f),
            // The multicol root ⇒ R2: declared px into the fold + strip.
            rootStackMargin(null, declaresTop = true, declaresBottom = true,
                staticDeclaredPx = edges),
        )
        assertEquals(true, plans[1].stripDeclared)
        // Gap indices: [above p, BETWEEN p and multicol, after multicol].
        assertEquals(listOf(16f, 16f, 16f), collapsedRootStackGapsPx(plans))
    }

    @Test
    fun f3_discardMulticol001_stackFoldCollapsesTheTwo13pxBoxes() {
        // discard-multicol-001 stacks TWO monospace 1em-margin boxes under
        // the intro `<p>`: p↔box1 collapses max(16,13) = 16 (box1 top at
        // the ref's 88, not the pre-fix 104) and box1↔box2 collapses
        // max(13,13) = 13 — both margins in the gaps, neither rendered.
        val box = listOf(
            prop("FontFamily", """["monospace"]"""),
            emMargin("MarginTop", 1.0), emMargin("MarginBottom", 1.0),
        )
        val edges = StaticEmMargin.verticalEdges(box)
        assertEquals(13f to 13f, edges)
        val plans = listOf(
            rootStackMargin("p", declaresTop = false, declaresBottom = false,
                staticDeclaredPx = 0f to 0f),
            rootStackMargin(null, declaresTop = true, declaresBottom = true,
                staticDeclaredPx = edges),
            rootStackMargin(null, declaresTop = true, declaresBottom = true,
                staticDeclaredPx = edges),
        )
        assertEquals(listOf(16f, 16f, 13f, 13f), collapsedRootStackGapsPx(plans))
    }

    // ── E5: every other flavor still bails ─────────────────────────────────

    @Test
    fun e5_percentEdge_stillBails() {
        // `%` needs the containing block's inline size (css-box-4 §3), which
        // is not on the property list — the bail is deliberate, not an
        // oversight. The live converter emits a percent margin as a bare
        // number, which MarginExtractor reads as Relative(PERCENT).
        val p = listOf(fontSize(92.0), prop("MarginTop", "10.0"))
        assertNull(StaticEmMargin.verticalEdges(p))
    }

    @Test
    fun e5_viewportEdge_stillBails() {
        // vw/vh need the viewport — still out of the static scope.
        val p = listOf(fontSize(92.0), prop("MarginBottom", """{"original":{"v":5,"u":"VW"}}"""))
        assertNull(StaticEmMargin.verticalEdges(p))
    }

    @Test
    fun e5_autoEdge_stillBails() {
        // `margin-top: auto` is alignment, not px — MarginApplier's vertical
        // auto-centering path must stay untouched.
        val p = listOf(fontSize(92.0), prop("MarginTop", "\"auto\""))
        assertNull(StaticEmMargin.verticalEdges(p))
    }

    // ── E6/E7: mixed lists ────────────────────────────────────────────────

    @Test
    fun e6_emTopWithPxBottom_resolvesBothLanes() {
        // E6: the two lanes compose per edge — em top, absolute-px bottom.
        val p = listOf(fontSize(32.0), emMargin("MarginTop", 0.5), prop("MarginBottom", """{"px":8}"""))
        assertEquals(16f to 8f, StaticEmMargin.verticalEdges(p))
    }

    @Test
    fun e7_horizontalEmEdgesAreIgnored() {
        // E7: only VERTICAL margins collapse in horizontal writing mode
        // (§8.3.1), so an unresolvable inline edge must NOT bail the root —
        // dotted-001's `margin-left: .5em` keeps rendering via MarginApplier.
        val p = listOf(prop("MarginLeft", "10.0"), prop("MarginTop", """{"px":4}"""))
        assertEquals(4f to 0f, StaticEmMargin.verticalEdges(p))
    }

    // ── E8: drift guard against the narrow runtime classifier ─────────────

    @Test
    fun e8_nonEmWires_matchTheNarrowClassifierByteForByte() {
        // E8: on any list WITHOUT an em block margin the widened classifier
        // is byte-identical to BlockMarginCollapse.blockMarginsOrNull — this
        // is what keeps every wave-19 R/S/T pin and the composed-canvas
        // geometry of the other 14 sections unchanged.
        val cases = listOf(
            listOf(prop("MarginTop", """{"px":20}"""), prop("MarginBottom", """{"px":20}""")),
            listOf(prop("BackgroundColor", "\"#fff\"")),
            listOf(prop("MarginTop", "\"auto\""), prop("MarginBottom", """{"px":20}""")),
            listOf(prop("MarginTop", "10.0")),
            listOf(prop("MarginBlockStart", """{"px":12}""")),
        )
        for (p in cases) {
            val narrow = com.styleconverter.runtime.spacing.BlockMarginCollapse
                .blockMarginsOrNull(
                    com.styleconverter.runtime.spacing.MarginExtractor
                        .extract(p.map { it.type to it.data })
                )?.let { it.topPx to it.bottomPx }
            assertEquals(narrow, StaticEmMargin.verticalEdges(p))
        }
    }
}
