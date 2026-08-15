package com.styleconverter.runtime.layout

// Wave-42 lane W5 — the REAL-WIRE half of the §9.5.2 clearance pins.
// FloatClearanceTest.kt pins the math on hand-built trees; this file
// proves the same plan resolves from the BYTES the natives actually
// consumed in the wave-41 gate: each literal below is
// tools/titan/runs/wave41-final/sections/CSS2/per-test-ir/
// wpt__CSS2__floats-clear__<test>.json verbatim, with only the component
// ids/names shortened to c0…cN (they are opaque keys — the projection
// never reads them) and the harness's line breaks removed.
//
// Why this file exists: FloatClearanceModel.project bails STRICTLY (text,
// pseudos, unmodeled property types, non-div tags…), so a plan that
// resolves on a hand-built tree can still be dead on the wire — e.g. the
// captured floats of clear-on-child-with-margins-2 carry
// `Width:{type:percentage}` and every float carries `meta.role:ws-after`.
// Feeding the captured document through the production decoder
// (IRDocumentDecoder) and composer (SlotComposer) is the only device-free
// way to prove the emulation fires on device.
//
// Twin: FloatClearanceWireTests.swift (same six documents, same pins).

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import com.styleconverter.runtime.core.renderer.SlotComposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FloatClearanceWireTest {

    /**
     * Decode + compose a captured v2 document exactly like the harness
     * screens do (ScreenshotCaptureScreen → SlotComposer.compose), then
     * hand back the composed ROOT at [index]. Root 0 is always the WPT
     * `<p>Test passes if…</p>` instruction paragraph; the test document's
     * own boxes start at root 1.
     */
    private fun root(doc: String, index: Int): IRComponent =
        SlotComposer.compose(IRDocumentDecoder.decode(doc))[index]

    @Test
    fun `adjoining-float-before-clearance - the captured wire resolves to a 50px clearance`() {
        // Captured shape: red(w100) > [ws-wrapper > float(L,100x50),
        // cleared(mt400, clear:left, h50)]. The 400px margin collapses
        // through the wrapper AND the red root's top edge, so with
        // clear:none the float would be pulled down WITH the cleared box
        // (§8.3.1 adjoining) — clearance is forced regardless of the
        // margin's size, landing the top border edge on the float's
        // bottom outer edge: 0 + 50 = 50.
        val plan = FloatClearance.resolve(root(BEFORE_CLEARANCE, 1))
        assertNotNull("the captured document must resolve a plan", plan)
        // The float leaves the flow (its 50px no longer pushes the stack).
        assertEquals(true, plan!!.adjustments["c3"]?.zeroFlowHeight)
        // …and the cleared box's applied margin IS the clearance.
        assertEquals(50.0, plan.adjustments["c4"]?.appliedTopPx)
    }

    @Test
    fun `adjoining-float-new-fc - captured BFC root, clearance still 50px`() {
        // red(w100, overflow:hidden) > wrapper > [float(L,100x50),
        // cleared(mt300, clear:left, overflow:hidden, 100x50)]. The BFC
        // edge at the red root stops the margin's ascent, but the wrapper
        // IS crossed — its float is pulled — so clearance is forced and
        // the cleared box's border top lands at the float bottom, 50.
        val plan = FloatClearance.resolve(root(NEW_FC, 1))
        assertNotNull("the captured document must resolve a plan", plan)
        assertEquals(true, plan!!.adjustments["c3"]?.zeroFlowHeight)
        assertEquals(50.0, plan.adjustments["c4"]?.appliedTopPx)
    }

    @Test
    fun `clear-on-child-with-margins-2 - percentage-width floats still project`() {
        // red(w100, overflow:hidden) > [float(R,h20,w50%), mid >
        // [float(L,h100,w50%), wrap > cleared(clear:right,h80,mt16)]].
        // `Width:{type:percentage}` is NOT read by the projection (only
        // Height is), so the captured floats project fine. clear:right
        // looks only at the RIGHT float (bottom 20); the hypothetical top
        // (16) is above it → clearance moves the box to 20.
        val plan = FloatClearance.resolve(root(CHILD_MARGINS_2, 1))
        assertNotNull("the captured document must resolve a plan", plan)
        // BOTH floats leave the flow — the left one's 100px of stacked
        // height is the wave-41 Android failure signature (0.9499).
        assertEquals(true, plan!!.adjustments["c2"]?.zeroFlowHeight)
        assertEquals(true, plan.adjustments["c4"]?.zeroFlowHeight)
        assertEquals(20.0, plan.adjustments["c6"]?.appliedTopPx)
    }

    @Test
    fun `clear-on-parent-with-margins - the captured -1000px child is absorbed`() {
        // red(200x200) > [float(L,200x100), cleared(clear:left,mt100) >
        // child(h100,mt-1000)]. The §8.3.1 group {100,-1000} collapses to
        // -900 — far above the float — and the float is pulled by it, so
        // clearance pins the cleared box at the float bottom (100) and
        // the child renders flush at its content top. Wave 41 rendered
        // green-over-RED here (the child was pushed out of the box).
        val plan = FloatClearance.resolve(root(PARENT_MARGINS, 1))
        assertNotNull("the captured document must resolve a plan", plan)
        assertEquals(true, plan!!.adjustments["c2"]?.zeroFlowHeight)
        assertEquals(100.0, plan.adjustments["c3"]?.appliedTopPx)
        // The absorbed chain member's -1000 must not survive anywhere.
        assertEquals(0.0, plan.adjustments["c4"]?.appliedTopPx)
        assertEquals(0.0, plan.adjustments["c4"]?.appliedBottomPx)
    }

    @Test
    fun `negative-clearance-after-adjoining-float - the red root collapses to 50px`() {
        // red(w100) > [float(L,100x50), cleared(clear:left, mt200, h0)],
        // then a SEPARATE green root (100x50) follows in the document.
        // Forced clearance puts the empty cleared box's border top at 50,
        // so red's auto height is 50 and the following root stacks at
        // 50..100 — the ref's lower green half.
        val plan = FloatClearance.resolve(root(NEGATIVE_CLEARANCE, 1))
        assertNotNull("the captured document must resolve a plan", plan)
        assertEquals(true, plan!!.adjustments["c2"]?.zeroFlowHeight)
        assertEquals(50.0, plan.adjustments["c3"]?.appliedTopPx)
    }

    @Test
    fun `clear-after-top-margin - out of scope, resolves to null (deferred)`() {
        // The float here is a ROOT-LEVEL SIBLING of the block that holds
        // the clearing box (body > float, body > green-block > … >
        // clear:right), so no single composed root contains both actors:
        // resolve() sees a Clear with no Float in its subtree and returns
        // the identity. Fixing this needs the body-level root STACK to
        // become the clearance scope — the root loop lives in the capture
        // harnesses (apps/android-harness ScreenshotCaptureScreen.kt /
        // apps/ios-harness), outside this lane's ownership. Pinned as a
        // null so the boundary is explicit rather than accidental.
        assertNull(FloatClearance.resolve(root(AFTER_TOP_MARGIN, 1)))
        assertNull(FloatClearance.resolve(root(AFTER_TOP_MARGIN, 2)))
    }

    // ── The six captured documents (see the file banner for provenance) ──

    companion object {
        // css/CSS2/floats-clear/adjoining-float-before-clearance.html
        private const val BEFORE_CLEARANCE = """{"irVersion":2,"minReaderVersion":2,"components":[
{"id":"c0","name":"c0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
{"id":"c1","name":"c1","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}]},
{"id":"c2","name":"c2","properties":[],"slot":{"parent":"c1"},"meta":{"role":"ws-after"}},
{"id":"c3","name":"c3","properties":[{"type":"Float","data":"LEFT"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c2"}},
{"id":"c4","name":"c4","properties":[{"type":"MarginTop","data":{"px":400}},{"type":"Clear","data":"LEFT"},{"type":"Height","data":{"type":"length","px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c1"}}]}"""

        // css/CSS2/floats-clear/adjoining-float-new-fc.html
        private const val NEW_FC = """{"irVersion":2,"minReaderVersion":2,"components":[
{"id":"c0","name":"c0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
{"id":"c1","name":"c1","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}]},
{"id":"c2","name":"c2","properties":[],"slot":{"parent":"c1"}},
{"id":"c3","name":"c3","properties":[{"type":"Float","data":"LEFT"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c2"},"meta":{"role":"ws-after"}},
{"id":"c4","name":"c4","properties":[{"type":"MarginTop","data":{"px":300}},{"type":"Clear","data":"LEFT"},{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c2"}}]}"""

        // css/CSS2/floats-clear/clear-on-child-with-margins-2.html
        private const val CHILD_MARGINS_2 = """{"irVersion":2,"minReaderVersion":2,"components":[
{"id":"c0","name":"c0","properties":[],"text":"Test passes if there is a filled green square.","meta":{"sourceTag":"p","role":"ws-after"}},
{"id":"c1","name":"c1","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"}]},
{"id":"c2","name":"c2","properties":[{"type":"Float","data":"RIGHT"},{"type":"Height","data":{"type":"length","px":20}},{"type":"Width","data":{"type":"percentage","value":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c1"},"meta":{"role":"ws-after"}},
{"id":"c3","name":"c3","properties":[],"slot":{"parent":"c1"}},
{"id":"c4","name":"c4","properties":[{"type":"Float","data":"LEFT"},{"type":"Height","data":{"type":"length","px":100}},{"type":"Width","data":{"type":"percentage","value":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c3"},"meta":{"role":"ws-after"}},
{"id":"c5","name":"c5","properties":[],"slot":{"parent":"c3"}},
{"id":"c6","name":"c6","properties":[{"type":"Clear","data":"RIGHT"},{"type":"Height","data":{"type":"length","px":80}},{"type":"MarginTop","data":{"px":16}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c5"}}]}"""

        // css/CSS2/floats-clear/clear-on-parent-with-margins.html
        private const val PARENT_MARGINS = """{"irVersion":2,"minReaderVersion":2,"components":[
{"id":"c0","name":"c0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
{"id":"c1","name":"c1","properties":[{"type":"Width","data":{"type":"length","px":200}},{"type":"Height","data":{"type":"length","px":200}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}]},
{"id":"c2","name":"c2","properties":[{"type":"Float","data":"LEFT"},{"type":"Width","data":{"type":"length","px":200}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c1"},"meta":{"role":"ws-after"}},
{"id":"c3","name":"c3","properties":[{"type":"Clear","data":"LEFT"},{"type":"MarginTop","data":{"px":100}}],"slot":{"parent":"c1"}},
{"id":"c4","name":"c4","properties":[{"type":"Height","data":{"type":"length","px":100}},{"type":"MarginTop","data":{"px":-1000}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c3"}}]}"""

        // css/CSS2/floats-clear/negative-clearance-after-adjoining-float.html
        private const val NEGATIVE_CLEARANCE = """{"irVersion":2,"minReaderVersion":2,"components":[
{"id":"c0","name":"c0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
{"id":"c1","name":"c1","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"meta":{"role":"ws-after"}},
{"id":"c2","name":"c2","properties":[{"type":"Float","data":"LEFT"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c1"},"meta":{"role":"ws-after"}},
{"id":"c3","name":"c3","properties":[{"type":"Clear","data":"LEFT"},{"type":"MarginTop","data":{"px":200}}],"slot":{"parent":"c1"}},
{"id":"c4","name":"c4","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}]}]}"""

        // css/CSS2/floats-clear/clear-after-top-margin.html — the float
        // (root 1) and the clearing subtree (root 2) are SEPARATE roots.
        private const val AFTER_TOP_MARGIN = """{"irVersion":2,"minReaderVersion":2,"components":[
{"id":"c0","name":"c0","properties":[],"text":"Test passes if there is a filled green square.","meta":{"sourceTag":"p","role":"ws-after"}},
{"id":"c1","name":"c1","properties":[{"type":"Float","data":"RIGHT"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}}],"meta":{"role":"ws-after"}},
{"id":"c2","name":"c2","properties":[{"type":"PaddingTop","data":{"px":10}},{"type":"Width","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}]},
{"id":"c3","name":"c3","properties":[{"type":"MarginTop","data":{"px":80}}],"slot":{"parent":"c2"}},
{"id":"c4","name":"c4","properties":[{"type":"Clear","data":"RIGHT"}],"slot":{"parent":"c3"}}]}"""
    }
}
