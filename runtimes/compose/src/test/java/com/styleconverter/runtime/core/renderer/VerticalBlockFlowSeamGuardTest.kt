package com.styleconverter.runtime.core.renderer

// Wave 52 (lane L3 · failure-ink, pin P1) — JVM pins for the POST-LOAD-
// EXTRACTION SIGNATURE (BakedLayoutSignature.bakedPhysicalBox) and the
// MulticolSpannerFlow.ChildSpec.bakedPhysicalSize flag that rides it: the
// guard both vertical seams (block flow in ComponentRenderer, multicol
// fragmentation in VerticalMulticolMeasure) consult before declining a
// container. Beside VerticalBlockFlowMathTest, which pins the seam's math.
//
// Every payload is VERBATIM wave51-fix per-test IR:
//   tools/titan/runs/wave51-fix/sections/css-writing-modes/per-test-ir/
//     wpt__css-writing-modes__flexbox_align-items-stretch-writing-modes.json
//     (`…__1__1__1__0-777`: an AUTHORED `.square { width:50px; height:50px }`
//     — the child that tripped the wave-47 two-property heuristic, ios f 0.999)
//   tools/titan/runs/wave51-fix/sections/css-writing-modes/per-test-ir/
//     wpt__css-writing-modes__abs-pos-border-offset-003.json (`…__0__0__0-321`,
//     the authored 10×20 in-flow sibling — the census's other authored carrier)
//   tools/titan/runs/wave51-fix/sections/css-anchor-position/per-test-ir/
//     wpt__css-anchor-position__anchor-position-multicol-013.json
//     (`anchor-position-multicol-013__1__0-707`: a post-load-extracted box,
//     29 longhands — the family the guard exists to protect, 8 carriers)
//
// MUTATION RECORD (executed 2026-09-25, lane L3): BakedLayoutSignature
// .bakedPhysicalBox reduced to the wave-47 heuristic (`WIDTH in types &&
// HEIGHT in types`) → the three `authored…`/`BoxSizing alone…` rows FAIL
// (expected false, was true) and the ChildSpec row fails on its authored
// half; the file was restored byte-exact (sha256 verified) and every row
// went green again. The record lives in the lane note.

import com.styleconverter.runtime.columns.MulticolSpannerFlow
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerticalBlockFlowSeamGuardTest {

    // Wire helper — the same idiom every JVM pin in this tree uses.
    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    // A bare component around a declaration list (specsFor takes components).
    private fun comp(id: String, properties: List<IRProperty>) =
        IRComponent(id = id, name = id, properties = properties)

    /** VERBATIM `…flexbox_align-items-stretch-writing-modes__1__1__1__0-777`. */
    private val authoredSquare = listOf(
        prop("Height", """{"type":"length","px":50}"""),
        prop("Width", """{"type":"length","px":50}"""),
    )

    /** VERBATIM `abs-pos-border-offset-003__0__0__0-321` (authored 10×20). */
    private val authoredSibling = listOf(
        prop("Width", """{"type":"length","px":10}"""),
        prop("Height", """{"type":"length","px":20}"""),
    )

    /** VERBATIM `anchor-position-multicol-013__1__0-707` — all 29 longhands. */
    private val bakedBox = listOf(
        prop("BlockSize", """{"px":110}"""),
        prop("Position", "\"STATIC\""),
        prop("Width", """{"type":"length","px":110}"""),
        prop("Height", """{"type":"length","px":20}"""),
        prop("BoxSizing", "\"CONTENT_BOX\""),
        prop("MarginTop", """{"px":0}"""), prop("MarginRight", """{"px":0}"""),
        prop("MarginBottom", """{"px":0}"""), prop("MarginLeft", """{"px":0}"""),
        prop("PaddingTop", """{"px":0}"""), prop("PaddingRight", """{"px":0}"""),
        prop("PaddingBottom", """{"px":0}"""), prop("PaddingLeft", """{"px":0}"""),
        prop("BorderTopWidth", """{"px":0}"""), prop("BorderRightWidth", """{"px":0}"""),
        prop("BorderBottomWidth", """{"px":0}"""), prop("BorderLeftWidth", """{"px":0}"""),
        prop("BorderTopStyle", "\"NONE\""), prop("BorderRightStyle", "\"NONE\""),
        prop("BorderBottomStyle", "\"NONE\""), prop("BorderLeftStyle", "\"NONE\""),
        prop("BorderTopColor", """{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}"""),
        prop("BorderRightColor", """{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}"""),
        prop("BorderBottomColor", """{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}"""),
        prop("BorderLeftColor", """{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}"""),
        prop("BackgroundColor", """{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":{"r":0,"g":128,"b":0}}"""),
        prop("Display", "\"BLOCK\""),
        prop("OverflowX", "\"VISIBLE\""), prop("OverflowY", "\"VISIBLE\""),
    )

    // ── P1: the predicate ─────────────────────────────────────────────────

    @Test fun `authored Width+Height alone is NOT a baked box - the 012 square`() {
        // The wave-47 heuristic said true here and froze the Column: the
        // two squares stacked vertically, the flex item came out 50 wide,
        // and the test's red probe showed through (ios f 0.999).
        assertFalse(BakedLayoutSignature.bakedPhysicalBox(authoredSquare))
    }

    @Test fun `authored 10x20 sibling of abs-pos-border-offset-003 is NOT baked either`() {
        // The census's other authored block-container carrier: unguarded
        // now, so its seam engages (the cell is f on both natives today).
        assertFalse(BakedLayoutSignature.bakedPhysicalBox(authoredSibling))
    }

    @Test fun `the post-load-extracted 013 box IS a baked box`() {
        // The anchor-position-multicol family must STAY guarded — 7 of its 8
        // baked carrier docs pass today on the frozen Column render (007 is
        // f), census F1.
        assertTrue(BakedLayoutSignature.bakedPhysicalBox(bakedBox))
    }

    @Test fun `BoxSizing alone or a band alone does not fake the signature`() {
        // An authored `box-sizing: border-box` on a sized box: NOT baked.
        assertFalse(BakedLayoutSignature.bakedPhysicalBox(
            authoredSquare + prop("BoxSizing", "\"BORDER_BOX\"")))
        // An authored padding on a sized box: NOT baked.
        assertFalse(BakedLayoutSignature.bakedPhysicalBox(
            authoredSquare + prop("PaddingTop", """{"px":4}""")))
        // Basis AND a band together is the extractor's shape — either band.
        assertTrue(BakedLayoutSignature.bakedPhysicalBox(
            authoredSquare + prop("BoxSizing", "\"CONTENT_BOX\"") + prop("PaddingTop", """{"px":0}""")))
        assertTrue(BakedLayoutSignature.bakedPhysicalBox(
            authoredSquare + prop("BoxSizing", "\"CONTENT_BOX\"") + prop("BorderTopStyle", "\"NONE\"")))
        // Missing either physical size is never baked, whatever else rides.
        assertFalse(BakedLayoutSignature.bakedPhysicalBox(bakedBox.filter { it.type != "Height" }))
    }

    // ── P1: the ChildSpec flag rides the SAME predicate ──────────────────

    @Test fun `ChildSpec bakedPhysicalSize follows the shared predicate`() {
        // The multicol fragmentation pass's decline flag — authored false…
        val authored = MulticolSpannerFlow.specsFor(listOf(comp("sq", authoredSquare)))
        assertFalse(authored.single().bakedPhysicalSize)
        // …baked true, so VerticalMulticolMeasure keeps its frozen path
        // for the anchor-position-multicol family exactly as before.
        val baked = MulticolSpannerFlow.specsFor(listOf(comp("baked", bakedBox)))
        assertTrue(baked.single().bakedPhysicalSize)
    }

    // ── Used-mode census row (resume pass 2026-10-05, census.json f1Used) ──
    // VERBATIM css-tables/height-distribution/td-different-subpixel-padding-
    // in-same-row-vertical-rl `__0__0__{0..4}-304..308`: five authored <td>s
    // (Width 20, Height 20, padding 2 / side) under a <tr> that INHERITS
    // vertical-rl from its <table> — wave51-fix ios P 0.9638 · android P
    // 0.9609. The old guard froze that row; the new one does not (padding but
    // no BoxSizing), so its protection is now the table path alone.
    // MUTATION RECORD (executed 2026-10-05): BOX_SIZING dropped from
    // bakedPhysicalBox → row (1) FAILS (the padded cells read baked); restored
    // byte-exact (sha256 verified). Row (2) is a DEPENDENCY pin on
    // TableBoxTree (not this lane's file — never mutated here); its control
    // row (the tag-less <tr>) proves the assertion can fail.
    private val subpixelCells = listOf("1", "1.2", "1.5", "1.7", "2").mapIndexed { i, side ->
        IRComponent(id = "td$i", name = "td$i", _tag = "td", properties = listOf(
            prop("Width", """{"type":"length","px":20}"""), prop("Height", """{"type":"length","px":20}"""),
            prop("PaddingTop", """{"px":2}"""), prop("PaddingRight", """{"px":$side}"""),
            prop("PaddingBottom", """{"px":2}"""), prop("PaddingLeft", """{"px":$side}""")))
    }

    @Test fun `td-different-subpixel row is protected by the table path, not the guard`() {
        // (1) No cell is a baked box — authored sizes + padding, no BoxSizing.
        subpixelCells.forEach { assertFalse(BakedLayoutSignature.bakedPhysicalBox(it.properties)) }
        // (2) The undeclared <table> folds to DisplayType.TABLE and consumes
        // its <tr> (RenderTableContent renders rows as TableRow, never via
        // RenderComponent), so the vertical block seam never sees this row.
        val row = IRComponent(id = "tr", name = "tr", _tag = "tr", children = subpixelCells)
        assertTrue(com.styleconverter.runtime.table.TableBoxTree.uaRoleOf("table") ==
            com.styleconverter.runtime.table.TableBoxTree.Role.TABLE)
        assertTrue(com.styleconverter.runtime.table.TableBoxTree.consumableRowList(listOf(row), useUaTagDefaults = true))
        // Control: the same row without its UA tag is NOT consumable.
        assertFalse(com.styleconverter.runtime.table.TableBoxTree.consumableRowList(listOf(row.copy(_tag = null)), useUaTagDefaults = true))
    }
}
