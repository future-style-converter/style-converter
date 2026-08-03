package com.styleconverter.test.screenshot

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
}
