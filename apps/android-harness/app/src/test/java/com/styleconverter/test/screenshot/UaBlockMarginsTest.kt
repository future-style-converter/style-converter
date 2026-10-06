package com.styleconverter.test.screenshot

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.spacing.UaBlockMarginFontBasis
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure UA-default block-margin model (UaBlockMargins.kt) that
 * FIX 1 of TITAN Round 4b uses to reproduce the browser-ref's inter-`<p>` gaps
 * in composed WPT capture. No Compose/Android runtime — the decisions are pure.
 *
 * Covers: the per-tag UA margin table, the "IR-declared margin wins over UA"
 * zeroing, and the adjacent-margin COLLAPSING that turns two 16px-margin bars
 * into a single 16px gap (not 32).
 */
class UaBlockMarginsTest {

    // ── UA margin table ─────────────────────────────────────────────────────

    @Test
    fun paragraph_has1emBlockMargins() {
        // <p>: 16px top+bottom, no horizontal margin (the hsl/rgb-001 bars).
        assertEquals(UaMargins(16, 16, 0, 0), uaBlockMargins("p"))
    }

    @Test
    fun tagLookupIsCaseInsensitive() {
        assertEquals(uaBlockMargins("p"), uaBlockMargins("P"))
    }

    @Test
    fun headings_scaleWithHeadingFont() {
        assertEquals(UaMargins(21, 21, 0, 0), uaBlockMargins("h1"))
        assertEquals(UaMargins(19, 19, 0, 0), uaBlockMargins("h2"))
        assertEquals(UaMargins(16, 16, 0, 0), uaBlockMargins("h3"))
        assertEquals(UaMargins(21, 21, 0, 0), uaBlockMargins("h4"))
        assertEquals(UaMargins(27, 27, 0, 0), uaBlockMargins("h5"))
        assertEquals(UaMargins(37, 37, 0, 0), uaBlockMargins("h6"))
    }

    @Test
    fun listsAndPreHave1emBlockMargins() {
        assertEquals(UaMargins(16, 16, 0, 0), uaBlockMargins("ul"))
        assertEquals(UaMargins(16, 16, 0, 0), uaBlockMargins("ol"))
        assertEquals(UaMargins(16, 16, 0, 0), uaBlockMargins("pre"))
    }

    @Test
    fun blockquoteAndFigureHaveHorizontalInsets() {
        assertEquals(UaMargins(16, 16, 40, 40), uaBlockMargins("blockquote"))
        assertEquals(UaMargins(16, 16, 40, 40), uaBlockMargins("figure"))
    }

    @Test
    fun blockContainersAndUnknownTagsHaveZeroUaMargin() {
        // div/section/article/header/footer/main/nav/aside → 0 in the UA sheet.
        for (tag in listOf("div", "section", "article", "header", "footer",
                           "main", "nav", "aside", "span", null, "unknowntag")) {
            assertEquals("tag=$tag", UaMargins.ZERO, uaBlockMargins(tag))
        }
    }

    // ── IR-declared margin wins over UA ─────────────────────────────────────

    @Test
    fun declaredSides_derivedFromPropertyTypes() {
        assertEquals(setOf("top"), declaredMarginSides(listOf("MarginTop")))
        assertEquals(setOf("bottom"), declaredMarginSides(listOf("MarginBottom")))
        assertEquals(setOf("top", "bottom"), declaredMarginSides(listOf("MarginBlock")))
        assertEquals(setOf("top", "bottom", "left", "right"),
            declaredMarginSides(listOf("Margin")))
        assertEquals(emptySet<String>(), declaredMarginSides(listOf("BackgroundColor")))
    }

    @Test
    fun irDeclaredSideZeroesTheUaDefault() {
        // A <p> that declares its own margin-top keeps the UA bottom but the
        // top is 0 (the runtime renders the declared top — UA must not add).
        assertEquals(UaMargins(0, 16, 0, 0), effectiveUaMargins("p", setOf("top")))
        // The a98rgb-003 boxes: divs (UA 0) declaring MarginBottom / MarginTop.
        assertEquals(UaMargins.ZERO, effectiveUaMargins("div", setOf("bottom")))
    }

    @Test
    fun barWithNoDeclaredMargin_keepsFullUaDefault() {
        // The hsl/rgb-001 bars declare only BackgroundColor → full 16/16.
        assertEquals(UaMargins(16, 16, 0, 0), effectiveUaMargins("p", emptySet()))
    }

    // ── Adjacent-margin collapsing ──────────────────────────────────────────

    @Test
    fun tenParagraphBars_collapseTo16pxGaps_notFlush_notDouble() {
        // 10 <p> bars, each 16/16, stacked in a padded canvas. Expected gaps:
        //   before first = 16 (kept: padding blocks collapse to the canvas)
        //   between each = max(16,16) = 16 (collapsed, NOT 32)
        //   after last   = 16 (kept)
        val margins = List(10) { 16 to 16 }
        val gaps = collapsedVerticalGaps(margins)
        assertEquals(11, gaps.size)
        assertEquals(List(11) { 16 }, gaps)
    }

    @Test
    fun asymmetricAdjacentMargins_collapseToMax() {
        // A 16px-bottom above a 24px-top collapses to 24 (the larger).
        val gaps = collapsedVerticalGaps(listOf(16 to 16, 24 to 8))
        assertEquals(listOf(16, 24, 8), gaps)
    }

    @Test
    fun zeroMarginBlocks_produceNoGaps() {
        // Divs (UA 0) render flush — no injected spacing.
        val gaps = collapsedVerticalGaps(listOf(0 to 0, 0 to 0, 0 to 0))
        assertEquals(listOf(0, 0, 0, 0), gaps)
    }

    @Test
    fun a98rgb003Layout_bodyRootThenParagraphThenBoxes() {
        // roots: body-root(div 0/0), <p> 16/16, div w/ IR margin-bottom (0/0),
        // div w/ IR margin-top (0/0). Only the <p> injects 16px above+below.
        val margins = listOf(0 to 0, 16 to 16, 0 to 0, 0 to 0)
        val gaps = collapsedVerticalGaps(margins)
        assertEquals(listOf(0, 16, 16, 0, 0), gaps)
    }

    @Test
    fun emptyRoots_yieldSingleZeroGap() {
        assertEquals(listOf(0), collapsedVerticalGaps(emptyList()))
    }

    // ── RC-A4 (wave 19): declared-margin collapse between stacked roots ─────
    // Shared R1–R7 pin table — expected values are IDENTICAL to the Swift twin
    // (ComposedRootStackTests.swift) so the two implementations cannot drift.

    @Test
    fun r1_pureUaRoot_keepsFix1Contribution() {
        // R1: an undeclared <p> contributes its UA 16/16 and never strips.
        assertEquals(RootStackMargin(16f, 16f, false),
            rootStackMargin("p", declaresTop = false, declaresBottom = false,
                staticDeclaredPx = 0f to 0f))
    }

    @Test
    fun r2_safe001Root_declared20BothSides_foldsAndStrips() {
        // R2: the safe-001 flex containers (margin: 20px, tag-less divs) —
        // declared px feed the fold and the root's block margins strip.
        assertEquals(RootStackMargin(20f, 20f, true),
            rootStackMargin(null, declaresTop = true, declaresBottom = true,
                staticDeclaredPx = 20f to 20f))
    }

    @Test
    fun r3_declaredTopOnly_mixesDeclaredAndUa() {
        // R3: declared top (20px) wins over UA; undeclared bottom keeps UA 16.
        assertEquals(RootStackMargin(20f, 16f, true),
            rootStackMargin("p", declaresTop = true, declaresBottom = false,
                staticDeclaredPx = 20f to 0f))
    }

    @Test
    fun r4_nonStaticDeclaredMargins_bailToFix1() {
        // R4: auto/negative/relative/calc (classifier returned null) — both
        // declared sides render via MarginApplier (UA zeroed), no strip.
        assertEquals(RootStackMargin(0f, 0f, false),
            rootStackMargin("p", declaresTop = true, declaresBottom = true,
                staticDeclaredPx = null))
    }

    @Test
    fun r5_partialBail_keepsUaOnUndeclaredSide() {
        // R5: only bottom declared and unresolvable — UA top survives.
        assertEquals(RootStackMargin(16f, 0f, false),
            rootStackMargin("p", declaresTop = false, declaresBottom = true,
                staticDeclaredPx = null))
    }

    @Test
    fun r6_safe001Stack_fourRoots_collapseTo20pxGaps() {
        // R6: four stripped 20/20 roots → every gap 20 (max(20,20)), NOT 40 —
        // the 76px root pitch (56px box + 20px gap) the browser-ref shows.
        val plans = List(4) { rootStackMargin(null, true, true, 20f to 20f) }
        val gaps = collapsedVerticalGapsPx(plans.map { it.topPx to it.bottomPx })
        assertEquals(List(5) { 20f }, gaps)
    }

    @Test
    fun r7_declaredAboveUaParagraph_collapsesToMax() {
        // R7: a div with declared margin-bottom 20 above an undeclared <p>
        // (UA 16 top) → interior gap max(20, 16) = 20.
        val above = rootStackMargin("div", false, true, 0f to 20f)
        val below = rootStackMargin("p", false, false, 0f to 0f)
        val gaps = collapsedVerticalGapsPx(
            listOf(above.topPx to above.bottomPx, below.topPx to below.bottomPx))
        assertEquals(listOf(0f, 20f, 16f), gaps)
    }

    @Test
    fun floatFold_matchesIntFold_onWholePxInputs() {
        // The Int entry point now delegates to the float fold — pin the
        // lossless round-trip on the FIX 1 shapes above.
        assertEquals(listOf(16, 24, 8), collapsedVerticalGaps(listOf(16 to 16, 24 to 8)))
    }

    // ── Wave-19 follow-up: §8.3.1 collapse THROUGH margin-transparent roots ─
    // Shared T1–T6 pin table, byte-parallel with the Swift twin
    // (ComposedRootStackTests). A transparent root has zero flow footprint
    // (hoisted overlay root, or RC1 static-position root under an active
    // host): the adjoining-margin set stays OPEN across it, so its two
    // margined neighbors share ONE max() gap instead of one gap each.

    // Shorthand builders for the fold pins below.
    private fun opaque(top: Float, bottom: Float) =
        RootStackMargin(top, bottom, stripDeclared = true)
    private fun transparent(top: Float = 0f, bottom: Float = 0f, strip: Boolean = false) =
        RootStackMargin(top, bottom, stripDeclared = strip, marginTransparent = true)

    @Test
    fun t1_transparentRootBetweenMarginedRoots_collapsesToOneGap() {
        // T1 — the described composed shape: a margin-transparent abspos
        // static-position root between two stripped margin:20px containers.
        // The set {20, 0, 0, 20} resolves to ONE 20px gap: emitted ABOVE the
        // transparent slot (§8.3.1 collapse-through position — the slot sits
        // where the hypothetical in-flow box would), zero above the next
        // container. Total inter-container space 20, root pitch 56+20=76.
        val gaps = collapsedRootStackGapsPx(
            listOf(opaque(20f, 20f), transparent(), opaque(20f, 20f)))
        assertEquals(listOf(20f, 20f, 0f, 20f), gaps)
    }

    @Test
    fun t2_chainOfTwoTransparentRoots_stillOneCollapsedGap() {
        // T2 — TWO transparent roots between the margined pair: the set stays
        // open across both, still exactly one 20px gap between the containers.
        val gaps = collapsedRootStackGapsPx(
            listOf(opaque(20f, 20f), transparent(), transparent(), opaque(20f, 20f)))
        assertEquals(listOf(20f, 20f, 0f, 0f, 20f), gaps)
    }

    @Test
    fun t3_transparentRootWithOwnMargins_participatesInTheMax() {
        // T3 — a transparent root with its OWN declared (stripped) margins:
        // the whole adjoining set {20, 30, 10, 20} collapses to max = 30
        // (§8.3.1's empty-box model), emitted once. Its slot anchors at the
        // prefix max through its own top margin (30) — the hypothetical
        // static position — and the following root adds nothing.
        val gaps = collapsedRootStackGapsPx(
            listOf(opaque(0f, 20f), transparent(30f, 10f, strip = true), opaque(20f, 0f)))
        assertEquals(listOf(0f, 30f, 0f, 0f), gaps)
    }

    @Test
    fun t4_allOpaquePlans_matchTheLegacyPairFold_byteForByte() {
        // T4 — regression: with no transparent entry the new fold IS the old
        // one (the pair entry point delegates), pinned on the R6 safe-001
        // shape: four stripped 20/20 containers → every gap 20, pitch 76.
        val plans = List(4) { opaque(20f, 20f) }
        assertEquals(
            collapsedVerticalGapsPx(plans.map { it.topPx to it.bottomPx }),
            collapsedRootStackGapsPx(plans))
        assertEquals(List(5) { 20f }, collapsedRootStackGapsPx(plans))
    }

    @Test
    fun t5_leadingAndTrailingTransparentRoots_keepPaddingBoundedEdges() {
        // T5 — a transparent root FIRST: it contributes nothing above itself
        // (the canvas padding bounds the set) and the following container's
        // 20px top margin still emits in full. A transparent root LAST rides
        // the already-emitted set: the container's 20px bottom margin sits
        // above its slot, trailing gap 0 (total below the container = 20).
        assertEquals(listOf(0f, 20f, 20f),
            collapsedRootStackGapsPx(listOf(transparent(), opaque(20f, 20f))))
        assertEquals(listOf(20f, 20f, 0f),
            collapsedRootStackGapsPx(listOf(opaque(20f, 20f), transparent())))
    }

    @Test
    fun t6_dynamicAlignSelf001Shape_hoistedMarginsDoNotPushFlow() {
        // T6 — the real dynamic-align-self-001 wire shape: a canvas-HOISTED
        // abspos root (inset + its own margins) between two margined flow
        // roots. Hoisted roots contribute (0,0) — §8.3.1: "margins of
        // absolutely positioned boxes do not collapse" — so the flow pair
        // still collapses to one max() gap and the overlay ink never pushes
        // flow content.
        val gaps = collapsedRootStackGapsPx(
            listOf(opaque(8f, 8f), transparent(0f, 0f), opaque(8f, 8f)))
        assertEquals(listOf(8f, 8f, 0f, 8f), gaps)
    }

    // ── Wave 26 (lane RES residual 3a) — the ROOT-STACK DOUBLE-COUNT fold ──
    //
    // B1..B6 pin `withHoistBand`, the step that gives a composed root's outer
    // block spacing exactly ONE owner. Byte-parallel with the Swift twin
    // (ComposedRootStackTests, same B-names and numbers) and with the band
    // producer's own pins (runtimes/compose RootHoistBandTest).

    @Test
    fun b1_zeroBandLeavesThePlanUntouched() {
        // The already-correct shape (blockquote root + <p> child: band 0 on
        // both edges) must stay byte-identical — every pre-wave-26 capture
        // whose roots emit no band is unmoved by this residual.
        val plan = opaque(16f, 16f)
        assertEquals(plan, withHoistBand(plan, 0f to 0f))
    }

    @Test
    fun b2_bandJoinsTheStackEdgeAsTheCollapsedThroughValue() {
        // blockquote root (own 16) whose first/last child is an <h5> (27):
        // the renderer's band is max(16,27) − 16 = 11, so the folded edge is
        // 16 + 11 = 27 — precisely the collapsed-through margin the browser
        // takes into the adjoining chain.
        val folded = withHoistBand(opaque(16f, 16f), 11f to 11f)
        assertEquals(27f, folded.topPx, 0f)
        assertEquals(27f, folded.bottomPx, 0f)
        // The strip/transparency flags ride through unchanged.
        assertEquals(true, folded.stripDeclared)
        assertEquals(false, folded.marginTransparent)
    }

    @Test
    fun b3_theDoubleCountThatUsedToRender_isGone() {
        // THE DEFECT, end to end. Roots: [prev with a 40px bottom margin,
        // blockquote (own 16) whose first child is an <h5> (27)].
        //   BEFORE: gap = max(40,16) = 40, PLUS the renderer's 11px band
        //           painted outside the root → 51px of spacing.
        //   BROWSER: one adjoining set {40, 16, 27} → max = 40.
        //   NOW: the band is folded in (root edge 27) and suppressed on the
        //        render, so the fold alone emits max(40, 27) = 40.
        val prev = opaque(0f, 40f)
        val root = withHoistBand(opaque(16f, 16f), 11f to 11f)
        val gaps = collapsedRootStackGapsPx(listOf(prev, root))
        assertEquals(40f, gaps[1], 0f)
    }

    @Test
    fun b4_twoAdjacentBandCarryingRoots_collapseToOneGap() {
        // The case that PROVES subtraction cannot express this: root A (own
        // 0) whose last child has a 16px bottom, root B (own 0) whose first
        // child has a 16px top. Both bands are 16. Folded edges are 16/16, so
        // the §8.3.1 fold emits ONE 16px gap. Subtracting the two bands from
        // that gap would give −16 → floored 0 → 0+16+16 = 32 rendered.
        val a = withHoistBand(opaque(0f, 0f), 16f to 16f)
        val b = withHoistBand(opaque(0f, 0f), 16f to 16f)
        assertEquals(listOf(16f, 16f, 16f), collapsedRootStackGapsPx(listOf(a, b)))
    }

    @Test
    fun b5_transparentRootsNeverTakeABand() {
        // A zero-flow root (canvas-hoisted, or the RC1 static-position
        // anchor) displaces no flow sibling, so whatever its subtree emits
        // must not enter the gap math — returned verbatim, band ignored.
        val t = transparent(4f, 4f)
        assertEquals(t, withHoistBand(t, 16f to 16f))
    }

    @Test
    fun b6_asymmetricBandsFoldPerEdge() {
        // Edge gates resolve independently (§8.3.1 is per edge): a root with
        // a padded TOP and an open bottom folds only the bottom band.
        val folded = withHoistBand(opaque(0f, 0f), 0f to 16f)
        assertEquals(0f, folded.topPx, 0f)
        assertEquals(16f, folded.bottomPx, 0f)
    }

    // ── Y1-Y3 (wave 46, lane Y8): the UA margin's FONT BASIS ────────────
    // The composed ROOT path for CSS2/cascade/inherit-computed-001: a `<p>`
    // declaring `font-size: larger` (live wire below, verbatim from the
    // wave45-final per-test IR). The browser-ref's border-top is at row 35
    // = 16px image pad + 1em × 19.2px; the 16px-root table put it at 32.

    /** The inherit-computed-001 root: `<p>` + `font-size: larger`, no margins. */
    private fun largerParagraph() = IRComponent(
        id = "p", name = "wpt__CSS2__cascade__inherit-computed-001__0", _tag = "p",
        properties = listOf(IRProperty("FontSize",
            Json.parseToJsonElement("""{"original":{"type":"relative","keyword":"larger"}}"""))),
    )

    @Test
    fun y1_largerParagraphRoot_uaMarginIs19px() {
        // Y1: 1em × 19.2 = 19.2 → 19 whole px (the Spacer is placed at
        // integer px; 16 + 19 = row 35, the ref's row). Both edges, no inline.
        assertEquals(UaMargins(19, 19, 0, 0), uaBlockMargins("p", 19.2f))
        assertEquals(UaMargins(19, 19, 0, 0), effectiveUaMargins(largerParagraph()))
        // The root-stack plan the rootPlans fold builds for this root (R1
        // shape: no declared block margin, never strips).
        assertEquals(RootStackMargin(19f, 19f, false),
            rootStackMargin("p", declaresTop = false, declaresBottom = false,
                staticDeclaredPx = 0f to 0f,
                ownFontSizePx = UaBlockMarginFontBasis.ownFontSizePx(largerParagraph().properties)))
    }

    @Test
    fun y2_nullBasis_isTheTableVerbatim() {
        // Y2: every root WITHOUT an own font signal resolves a null basis
        // and keeps the Round-4 numbers — the identity that protects the
        // whole corpus (simulated root blast radius: exactly one test).
        val plain = IRComponent(id = "p", name = "t__0", _tag = "p")
        assertEquals(UaMargins(16, 16, 0, 0), effectiveUaMargins(plain))
        assertEquals(uaBlockMargins("h2"), uaBlockMargins("h2", null))
        assertEquals(RootStackMargin(16f, 16f, false),
            rootStackMargin("p", false, false, 0f to 0f, ownFontSizePx = null))
    }

    @Test
    fun y3_declaredSideStillWinsOverTheScaledUa() {
        // Y3: author > UA per edge (css-cascade-4 §6.1) survives the basis —
        // a declared top zeroes the UA top, the scaled UA bottom stays.
        assertEquals(UaMargins(0, 19, 0, 0), effectiveUaMargins("p", setOf("top"), 19.2f))
        // Inline insets are untouched by the block-axis basis.
        assertEquals(UaMargins(19, 19, 40, 40), uaBlockMargins("blockquote", 19.2f))
    }

    // ── wave-52 lane L2 — the composed-stack PLAN on the VERBATIM per-test IR ──
    //
    // U1–U6 pin `composedRootStackPlan` / `isComposedStaticPositionRoot` /
    // `isSelfCollapsingRoot` on documents copied byte-for-byte out of
    // tools/titan/runs/wave51-fix/sections/<section>/per-test-ir/, decoded and
    // slot-composed through the SAME path ComposedCaptureCanvas runs
    // (IRDocumentDecoder → SlotComposer). Byte-parallel with the Swift twin
    // (ComposedRootStackTests U1–U6).
    //
    // EXECUTED MUTATIONS (applied to UaBlockMargins.kt, this class run alone,
    // source restored byte-exact — sha256 checked; record in
    // tools/titan/results/wave52-composed-canvas/_note.md):
    //   M1 shape 2 restored to the wave-19 join (`rootStackMargin(...)
    //      .copy(marginTransparent = true)` for the RC1 root)  → u1 red (50, strip).
    //   M2 `isSelfCollapsingRoot` forced false                  → u2 red (32 px).
    //   M3 the `display != "BLOCK"` guard dropped                → u8 red
    //      (u5 does NOT catch it: its table row is also the body-root —
    //      executed GREEN, which is why u8 exists).
    //   M4 the generated-content (`pseudos`) guard dropped       → u7 red.
    //   M5 withUaBlockMarginOnHoistedRoot returns `root` first    → u9 red (T6).

    /** Decode + slot-compose a v2 document exactly as the harness does. */
    private fun rootsOf(componentsJson: String): List<IRComponent> =
        com.styleconverter.runtime.core.renderer.SlotComposer.compose(
            com.styleconverter.runtime.core.ir.IRDocumentDecoder.decode(
                """{"irVersion":2,"minReaderVersion":2,"components":[$componentsJson]}"""))

    /** css-masking/clip-path/clip-path-ellipse-006 — roots 0 and 1, verbatim. */
    private val ellipse006 = """
        {"id":"wpt__css-masking__clip-path__clip-path-ellipse-006__0-059","name":"wpt__css-masking__clip-path__clip-path-ellipse-006__0","properties":[],"text":"The test passes if there is a full green ellipse.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-masking__clip-path__clip-path-ellipse-006__1-060","name":"wpt__css-masking__clip-path__clip-path-ellipse-006__1","properties":[{"type":"Width","data":{"type":"length","px":150}},{"type":"Height","data":{"type":"length","px":100}},{"type":"Position","data":"ABSOLUTE"},{"type":"MarginTop","data":{"px":50}},{"type":"MarginRight","data":{"px":50}},{"type":"MarginBottom","data":{"px":50}},{"type":"MarginLeft","data":{"px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"ClipPath","data":{"type":"ellipse"}}]}
    """.trimIndent()

    /** css-text-decor/text-decoration-propagation-shadow — all three roots, verbatim (post-load wire). */
    private val propagationShadow = """
        {"id":"wpt__css-text-decor__text-decoration-propagation-shadow__0-230","name":"wpt__css-text-decor__text-decoration-propagation-shadow__0","properties":[{"type":"Width","data":{"type":"length","px":358}},{"type":"Height","data":{"type":"length","px":0}},{"type":"Position","data":"STATIC"},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":16}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":16}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":{"r":0,"g":0,"b":0,"a":0}}},{"type":"Display","data":"BLOCK"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"meta":{"sourceTag":"p"}},
        {"id":"wpt__css-text-decor__text-decoration-propagation-shadow__1-231","name":"wpt__css-text-decor__text-decoration-propagation-shadow__1","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"Position","data":"STATIC"},{"type":"Width","data":{"type":"length","px":358}},{"type":"Height","data":{"type":"length","px":20}},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":{"r":0,"g":0,"b":0,"a":0}}},{"type":"Display","data":"BLOCK"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"text":"This text should be underlined.","meta":{"role":"ws-after"}},
        {"id":"wpt__css-text-decor__text-decoration-propagation-shadow__2-232","name":"wpt__css-text-decor__text-decoration-propagation-shadow__2","properties":[{"type":"Width","data":{"type":"length","px":358}},{"type":"Height","data":{"type":"length","px":0}},{"type":"Position","data":"STATIC"},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":16}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":16}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":{"r":0,"g":0,"b":0,"a":0}}},{"type":"Display","data":"BLOCK"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"meta":{"sourceTag":"p"}}
    """.trimIndent()

    /** css-flexbox/align-items-007 — all four components, verbatim (root 1 = the RC1 green). */
    private val alignItems007 = """
        {"id":"wpt__css-flexbox__align-items-007__0-361","name":"wpt__css-flexbox__align-items-007__0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-flexbox__align-items-007__1-362","name":"wpt__css-flexbox__align-items-007__1","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css-flexbox__align-items-007__2-363","name":"wpt__css-flexbox__align-items-007__2","properties":[{"type":"Display","data":"FLEX"},{"type":"FlexDirection","data":"COLUMN"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"LineHeight","data":{"original":{"type":"length","px":20}}},{"type":"AlignItems","data":"CENTER"}]},
        {"id":"align-items-007__2__0-364","name":"align-items-007__2__0","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}}],"slot":{"parent":"wpt__css-flexbox__align-items-007__2-363"},"meta":{"sourceTag":"img","attrs":{"src":"css/support/red-rect.svg"}}}
    """.trimIndent()

    /** css-images/gradient/gradient-hue-direction — root 4, the baked `<hr>`, verbatim. */
    private val hueDirectionHr = """
        {"id":"wpt__css-images__gradient__gradient-hue-direction__4-070","name":"wpt__css-images__gradient__gradient-hue-direction__4","properties":[{"type":"Display","data":"BLOCK"},{"type":"Height","data":{"type":"length","px":0}},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"BorderTopWidth","data":{"px":1}},{"type":"BorderRightWidth","data":{"px":1}},{"type":"BorderBottomWidth","data":{"px":1}},{"type":"BorderLeftWidth","data":{"px":1}},{"type":"BorderTopStyle","data":"INSET"},{"type":"BorderRightStyle","data":"INSET"},{"type":"BorderBottomStyle","data":"INSET"},{"type":"BorderLeftStyle","data":"INSET"},{"type":"BorderTopColor","data":{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}},{"type":"BorderRightColor","data":{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}},{"type":"MarginTop","data":{"px":8}},{"type":"MarginBottom","data":{"px":8}},{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"}],"meta":{"sourceTag":"hr","role":"ws-after","lang":"en"}}
    """.trimIndent()

    /** selectors/has-style-sharing-002 — root 0 (em padding + em margins), verbatim. */
    private val hasStyleSharing002Root0 = """
        {"id":"wpt__selectors__has-style-sharing-002__0-162","name":"wpt__selectors__has-style-sharing-002__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}},{"type":"PaddingTop","data":{"original":{"v":1,"u":"EM"}}},{"type":"PaddingRight","data":{"original":{"v":1,"u":"EM"}}},{"type":"PaddingBottom","data":{"original":{"v":1,"u":"EM"}}},{"type":"PaddingLeft","data":{"original":{"v":1,"u":"EM"}}},{"type":"MarginTop","data":{"original":{"v":1,"u":"EM"}}},{"type":"MarginRight","data":{"original":{"v":1,"u":"EM"}}},{"type":"MarginBottom","data":{"original":{"v":1,"u":"EM"}}},{"type":"MarginLeft","data":{"original":{"v":1,"u":"EM"}}}],"meta":{"role":"ws-after"}}
    """.trimIndent()

    /** CSS2/css21-errata/s-11-1-1b-006 — root 0, the `display: table` body-root, verbatim. */
    private val s006BodyRoot = """
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__0-141","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__0","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Display","data":"TABLE"},{"type":"BorderSpacing","data":{"type":"single","px":0}},{"type":"MarginTop","data":{"px":40}},{"type":"MarginRight","data":{"px":8}},{"type":"MarginBottom","data":{"px":8}},{"type":"MarginLeft","data":{"px":8}}],"meta":{"role":"body-root"}}
    """.trimIndent()

    @Test
    fun u1_ellipse006_rc1RootContributesZero_andKeepsItsDeclaredMargins() {
        // U1 (T1). Chrome: <p> bottom margin 16 resolves in full ABOVE the abspos
        // slot, then the box's own margin-top 50 offsets its ink → ellipse top
        // at line box + 66 (ref y 118). wave 19–51 joined the 50 into the set:
        // max(16, 50) = 50, stripped → y 102, 16 px high on both natives.
        val roots = rootsOf(ellipse006)
        val hostActive = com.styleconverter.runtime.layout.position.CanvasRootHoist.hostActivates(roots)
        assertEquals("the no-inset abspos root activates the host", true, hostActive)
        val plans = roots.map { composedRootStackPlan(it, hostActive) }
        // The <p>: UA 16/16, opaque, never stripped (R1).
        assertEquals(RootStackMargin(16f, 16f, stripDeclared = false), plans[0])
        // The RC1 root: (0,0), transparent, and NOT stripped — its 50 renders on the box.
        assertEquals(RootStackMargin(0f, 0f, stripDeclared = false, marginTransparent = true), plans[1])
        // Gap above the slot is the <p>'s full 16 (nothing else in the set);
        // slot + the box's own 50 = 66 below the <p>'s border box.
        assertEquals(listOf(16f, 16f, 0f), collapsedRootStackGapsPx(plans))
        assertEquals(66f, collapsedRootStackGapsPx(plans)[1] + 50f, 0f)
    }

    @Test
    fun u2_propagationShadow_emptyRootsCollapseThrough() {
        // U2 (T2-roots). Three roots: EMPTY <p> (h0, 16/16), the text block
        // (0/0), EMPTY <p> (h0, 16/16). §8.3.1 collapses the empty box's
        // margins through it → ONE 16-px gap above the text (ref y 35 = 16
        // frame + 16 + 3 line-box). The opaque fold emitted 16 + 16 → y 51.
        val roots = rootsOf(propagationShadow)
        val hostActive = com.styleconverter.runtime.layout.position.CanvasRootHoist.hostActivates(roots)
        assertEquals("no out-of-flow box — host inactive", false, hostActive)
        val plans = roots.map { composedRootStackPlan(it, hostActive) }
        assertEquals(RootStackMargin(16f, 16f, stripDeclared = true, marginTransparent = true), plans[0])
        assertEquals(RootStackMargin(0f, 0f, stripDeclared = true, marginTransparent = false), plans[1])
        assertEquals(RootStackMargin(16f, 16f, stripDeclared = true, marginTransparent = true), plans[2])
        // [above p0, above text, above p2, trailing]: the text block sits 16
        // below the padded top (16 emitted above the collapse-through p0, 0
        // more above the text), the trailing 16 is emitted above p2's slot.
        assertEquals(listOf(16f, 0f, 16f, 0f), collapsedRootStackGapsPx(plans))
        // Mutation M2 (predicate forced false) gives [16, 16, 16, 16] — 32 px.
    }

    @Test
    fun u3_alignItems007_rc1RootIsTheStaticPositionSlot_underAnActiveHost() {
        // U3 (T3 consumer). Root 1 (abspos, no inset) is the RC1 slot when the
        // host activates — and, with no declared z, no content and a plain
        // later root, the one the Column wraps in zIndex(1f) (u10 pins the lift).
        // Root 2 (the flex box) and root 0 (the <p>) are not.
        val roots = rootsOf(alignItems007)
        val hostActive = com.styleconverter.runtime.layout.position.CanvasRootHoist.hostActivates(roots)
        assertEquals(listOf(false, true, false), roots.map { isComposedStaticPositionRoot(it, hostActive) })
        // Host inactive (dark stage / hostless paths) → never the RC1 slot.
        assertEquals(listOf(false, false, false), roots.map { isComposedStaticPositionRoot(it, false) })
        // And its plan is shape 2: zero contribution, margins kept.
        assertEquals(RootStackMargin(0f, 0f, stripDeclared = false, marginTransparent = true),
            composedRootStackPlan(roots[1], hostActive))
    }

    @Test
    fun u4_emptyPredicate_acceptsThePostLoadHeightZeroParagraph() {
        // U4. The propagation-shadow <p>: no text/children, Display BLOCK,
        // Height 0, all block paddings/borders 0, Position STATIC → self-collapsing.
        val roots = rootsOf(propagationShadow)
        assertEquals(true, isSelfCollapsingRoot(roots[0]))
        // The text block has text → not empty.
        assertEquals(false, isSelfCollapsingRoot(roots[1]))
    }

    @Test
    fun u5_emptyPredicate_refusesBorderedTableAndPaddedRoots() {
        // U5 — the census's at-risk rows stay OPAQUE:
        //  gradient-hue-direction's <hr>: Height 0 but a 1-px inset border.
        assertEquals(false, isSelfCollapsingRoot(rootsOf(hueDirectionHr)[0]))
        //  has-style-sharing-002 root 0: `padding: 1em` (non-zero, unresolvable) → not empty.
        assertEquals(false, isSelfCollapsingRoot(rootsOf(hasStyleSharing002Root0)[0]))
        //  s-11-1-1b-006's body-root: `display: table` AND the body-root role (M1's domain).
        assertEquals(false, isSelfCollapsingRoot(rootsOf(s006BodyRoot)[0]))
        //  The RC1 abspos root of ellipse-006: out of flow → its own rule, not this one.
        assertEquals(false, isSelfCollapsingRoot(rootsOf(ellipse006)[1]))
    }

    @Test
    fun u6_planBuilder_matchesTheLegacyLambda_onTheR1Paragraph() {
        // U6 — byte-compat: an ordinary prose <p> root (the corpus's most
        // common root) is R1 exactly as before — UA 16/16, opaque, no strip,
        // band 0 — so every prose-only capture is unmoved.
        val p = rootsOf(ellipse006)[0]
        assertEquals(RootStackMargin(16f, 16f, stripDeclared = false), composedRootStackPlan(p, hostActive = true))
        assertEquals(RootStackMargin(16f, 16f, stripDeclared = false), composedRootStackPlan(p, hostActive = false))
    }
    /** css-counter-styles/counter-name-case-sensitive — root 1, verbatim (a `::before`-only div). */
    private val counterNameRoot1 = """
        {"id":"wpt__css-counter-styles__counter-name-case-sensitive__1-514","name":"wpt__css-counter-styles__counter-name-case-sensitive__1","properties":[{"type":"CounterIncrement","data":[{"name":"foo"}]}],"pseudos":{"before":{"properties":{"content":"\"1\" \"-\" \"5\""},"_text":"1-5","_lossy":true,"_lossyReasons":["generated-content-baked"]}},"meta":{"role":"ws-after","lang":"en-US"}}
    """.trimIndent()

    /** selectors/invalidation/any-link-attribute-removal — root 0, verbatim (an empty inline `<a>`). */
    private val anyLinkRoot0 = """
        {"id":"wpt__selectors__invalidation__any-link-attribute-removal__0-220","name":"wpt__selectors__invalidation__any-link-attribute-removal__0","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"Position","data":"STATIC"},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":{"r":0,"g":0,"b":0,"a":0}}},{"type":"Display","data":"INLINE"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"meta":{"sourceTag":"a"}}
    """.trimIndent()

    @Test
    fun u7_emptyPredicate_refusesGeneratedContent() {
        // U7 — the corpus census found 35 roots matching every other clause
        // that carry a `::before`/`::after` (this one paints "1-5"): line
        // boxes ⇒ NOT self-collapsing (§8.3.1). Mutation M4 red.
        assertEquals(false, isSelfCollapsingRoot(rootsOf(counterNameRoot1)[0]))
    }

    @Test
    fun u8_emptyPredicate_refusesANonBlockDisplay() {
        // U8 — an empty `display: inline` <a>: not a §8.3.1 block box; only
        // the Display guard keeps it opaque. Mutation M3 red.
        assertEquals(false, isSelfCollapsingRoot(rootsOf(anyLinkRoot0)[0]))
    }
    /** CSS2/css21-errata/s-11-1-1b-006 — root 3, verbatim: the inset abspos `<p>` (top 0, left 8). */
    private val s006InsetP = """
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__3-144","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__3","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":8}}],"text":"Test passes if there is a black square below.","meta":{"sourceTag":"p"}}
    """.trimIndent()

    @Test
    fun u9_t6_insetAbsposP_getsItsUaBlockMargin() {
        // U9 (T6). CSS 2.1 §9.3.2: `top: 0` offsets the MARGIN edge, so the
        // <p>'s border box sits 16 below the ICB edge (ref prose y 35). The
        // overlay never sees the fold's UA margins, so the harness hands it
        // the UA 16/16 as declared longhands. Mutation M5 red.
        val p = rootsOf(s006InsetP)[0]
        val out = withUaBlockMarginOnHoistedRoot(p)
        assertEquals(listOf("Position", "Top", "Left", "MarginTop", "MarginBottom"), out.properties.map { it.type })
        assertEquals(16f, com.styleconverter.runtime.core.types.ValueExtractors.extractDp(out.properties[3].data)?.value)
        assertEquals(16f, com.styleconverter.runtime.core.types.ValueExtractors.extractDp(out.properties[4].data)?.value)
        // The RC1 root (no inset) is the fold's, never this rule's: same instance.
        val rc1 = rootsOf(ellipse006)[1]
        assertEquals(true, rc1 === withUaBlockMarginOnHoistedRoot(rc1))
        // An in-flow <p> root: same instance.
        val prose = rootsOf(ellipse006)[0]
        assertEquals(true, prose === withUaBlockMarginOnHoistedRoot(prose))
        // An author block margin wins (css-cascade-4 §6.1): same instance.
        val declared = p.copy(properties = p.properties + com.styleconverter.runtime.core.ir.IRProperty(
            "MarginTop", kotlinx.serialization.json.Json.parseToJsonElement("""{"px":0}""")))
        assertEquals(true, declared === withUaBlockMarginOnHoistedRoot(declared))
    }

    // ── wave-52 lane L2 — T3 skeptic fix: WHICH roots the Column lifts ──
    //
    // `composedRootsPaintingAboveFlow` on four more verbatim wave51-fix docs.
    // The first cut lifted every RC1 root; these pin the four rules.
    //
    // EXECUTED MUTATIONS (UaBlockMargins.kt, this class run alone, restored
    // byte-exact — sha256 checked; mutations.log carries the mutated text
    // and the failing assertion):
    //   M6 rule 2 dropped (`ItemPlacementExtractor.zIndex(…) == null` → `true`) → u10 red.
    //   M7 rule 4 dropped (`paintsAsLayer(roots[j])` → `false`)                 → u11 red.
    //   M8 rule 3 dropped (`root.children.isNullOrEmpty()` → `true`)            → u12 red.

    /** css-tables/height-distribution/extra-height-given-to-all-row-groups-001 — all 7 components, verbatim. */
    private val extraHeight001 = """
        {"id":"wpt__css-tables__height-distribution__extra-height-given-to-all-row-groups-001__0-254","name":"wpt__css-tables__height-distribution__extra-height-given-to-all-row-groups-001__0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-tables__height-distribution__extra-height-given-to-all-row-groups-001__1-255","name":"wpt__css-tables__height-distribution__extra-height-given-to-all-row-groups-001__1","properties":[{"type":"Height","data":{"type":"length","px":100}},{"type":"Width","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Position","data":"ABSOLUTE"},{"type":"ZIndex","data":{"value":-1,"original":{"type":"integer","value":-1}}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css-tables__height-distribution__extra-height-given-to-all-row-groups-001__2-256","name":"wpt__css-tables__height-distribution__extra-height-given-to-all-row-groups-001__2","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"BorderCollapse","data":"COLLAPSE"},{"type":"Height","data":{"type":"length","px":100}}],"meta":{"sourceTag":"table"}},
        {"id":"height-distribution__extra-height-given-to-all-row-groups-001__2__0-257","name":"height-distribution__extra-height-given-to-all-row-groups-001__2__0","properties":[],"slot":{"parent":"wpt__css-tables__height-distribution__extra-height-given-to-all-row-groups-001__2-256"},"meta":{"sourceTag":"thead"}},
        {"id":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0-258","name":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0","properties":[],"slot":{"parent":"height-distribution__extra-height-given-to-all-row-groups-001__2__0-257"},"meta":{"sourceTag":"tr"}},
        {"id":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0__0-259","name":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0__0","properties":[{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}}],"slot":{"parent":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0-258"},"meta":{"sourceTag":"td"}},
        {"id":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0__0__0-260","name":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0__0__0","properties":[{"type":"Display","data":"INLINE_BLOCK"},{"type":"Width","data":{"type":"length","px":100}}],"slot":{"parent":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0__0-259"}}
    """.trimIndent()

    /** CSS2/abspos/static-inside-inline-001 — all 5 components, verbatim (root 2 nests an abspos green). */
    private val staticInsideInline001 = """
        {"id":"wpt__css2__abspos__static-inside-inline-001__0-019","name":"wpt__CSS2__abspos__static-inside-inline-001__0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css2__abspos__static-inside-inline-001__1-020","name":"wpt__CSS2__abspos__static-inside-inline-001__1","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css2__abspos__static-inside-inline-001__2-021","name":"wpt__CSS2__abspos__static-inside-inline-001__2","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}}]},
        {"id":"abspos__static-inside-inline-001__2__0-022","name":"abspos__static-inside-inline-001__2__0","properties":[{"type":"LineHeight","data":{"original":{"type":"length","px":100}}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}}],"slot":{"parent":"wpt__css2__abspos__static-inside-inline-001__2-021"},"text":"X","meta":{"sourceTag":"span","runs":[{"child":"abspos__static-inside-inline-001__2__0__0"},{"text":" X"}]}},
        {"id":"abspos__static-inside-inline-001__2__0__0-023","name":"abspos__static-inside-inline-001__2__0__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}}],"slot":{"parent":"abspos__static-inside-inline-001__2__0-022"}}
    """.trimIndent()

    /** css-cascade/unset-val-002 — all 4 components, verbatim (root 1 holds a `display: unset` span). */
    private val unsetVal002 = """
        {"id":"wpt__css-cascade__unset-val-002__0-150","name":"wpt__css-cascade__unset-val-002__0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-cascade__unset-val-002__1-151","name":"wpt__css-cascade__unset-val-002__1","properties":[{"type":"Position","data":"ABSOLUTE"}],"meta":{"role":"ws-after"}},
        {"id":"unset-val-002__1__0-152","name":"unset-val-002__1__0","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Generic","data":{"propertyName":"display","rawValue":"unset","_unmapped":true}}],"slot":{"parent":"wpt__css-cascade__unset-val-002__1-151"},"meta":{"sourceTag":"span"}},
        {"id":"wpt__css-cascade__unset-val-002__2-153","name":"wpt__css-cascade__unset-val-002__2","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}]}
    """.trimIndent()

    /** css-masking/clip-path/clip-path-path-with-zoom — both roots, verbatim (the green is a clip-path layer). */
    private val clipPathWithZoom = """
        {"id":"wpt__css-masking__clip-path__clip-path-path-with-zoom__0-099","name":"wpt__css-masking__clip-path__clip-path-path-with-zoom__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css-masking__clip-path__clip-path-path-with-zoom__1-100","name":"wpt__css-masking__clip-path__clip-path-path-with-zoom__1","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"ClipPath","data":{"type":"path","d":"M0,0 L100,0  L0,100  L0,0","rule":"nonzero"}},{"type":"Zoom","data":{"type":"number","value":2}}]}
    """.trimIndent()

    /** The lift verdicts for one verbatim doc, under the doc's own host activation. */
    private fun liftsOf(json: String): List<Boolean> {
        // Decode + compose exactly as the harness does, then the Host's own gate.
        val roots = rootsOf(json)
        val hostActive = com.styleconverter.runtime.layout.position.CanvasRootHoist.hostActivates(roots)
        return composedRootsPaintingAboveFlow(roots, hostActive)
    }

    @Test
    fun u10_extraHeight001_negativeZRc1RootIsNotLifted() {
        // U10 (rule 2). Root 1: red `position: absolute; z-index: -1`, the
        // RC1 slot (its margins still fold as shape 2), followed by the
        // green table. Appendix E step 3 paints it BELOW in-flow blocks;
        // the first cut lifted it (red over green, 22 passing cells).
        val roots = rootsOf(extraHeight001)
        val hostActive = com.styleconverter.runtime.layout.position.CanvasRootHoist.hostActivates(roots)
        assertEquals("still the RC1 slot for the fold", listOf(false, true, false),
            roots.map { isComposedStaticPositionRoot(it, hostActive) })
        assertEquals("never lifted", listOf(false, false, false), liftsOf(extraHeight001))
        // The positive control: align-items-007's undeclared-z green IS lifted.
        assertEquals(listOf(false, true, false), liftsOf(alignItems007))
    }

    @Test
    fun u11_staticInsideInline001_laterNestedAbsposKeepsTheRedBelow() {
        // U11 (rule 4). Root 1: red z-auto RC1 box; root 2 nests an abspos
        // green that follows it in TREE order at step 8, so the green wins.
        // A Column zIndex on root 1 would lift it past root 2's whole subtree.
        assertEquals(listOf(false, false, false), liftsOf(staticInsideInline001))
        // clip-path-path-with-zoom: the later green is a clip-path layer (step 8, z 0).
        assertEquals(listOf(false, false), liftsOf(clipPathWithZoom))
    }

    @Test
    fun u12_unsetVal002_contentBearingRc1RootIsNotLifted() {
        // U12 (rule 3). Root 1 is an RC1 box whose only paint is a child span;
        // rule 3 keeps the wave-51 order for any content-bearing root.
        assertEquals(listOf(false, false, false), liftsOf(unsetVal002))
    }
}
