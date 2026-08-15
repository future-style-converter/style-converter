//
//  FloatClearanceWireTests.swift
//  Wave-42 lane W5 — the REAL-WIRE half of the §9.5.2 clearance pins,
//  twin of Compose FloatClearanceWireTest.kt (same six documents, same
//  expected numbers).
//
//  FloatClearanceTests.swift pins the math on hand-built trees; this file
//  proves the same plan resolves from the BYTES the natives actually
//  consumed in the wave-41 gate: each literal below is
//  tools/titan/runs/wave41-final/sections/CSS2/per-test-ir/
//  wpt__CSS2__floats-clear__<test>.json verbatim, with only the component
//  ids/names shortened to c0…cN (opaque keys the projection never reads).
//
//  Why: FloatClearanceModel.project bails STRICTLY, so a plan that
//  resolves on a hand-built tree can still be dead on the wire — the
//  captured floats of clear-on-child-with-margins-2 carry
//  `Width:{type:percentage}` and every float carries `meta.role:ws-after`.
//  Decoding through the production reader (IRDocument → IRComposer) is
//  the only device-free proof that the emulation fires on device.
//

import XCTest
@testable import StyleConverterRuntime

final class FloatClearanceWireTests: XCTestCase {

    /// Decode a captured v2 document through the production reader (which
    /// slot-composes via IRComposer) and hand back the composed ROOT at
    /// `index`. Root 0 is always the WPT `<p>Test passes if…</p>`
    /// paragraph; the test document's own boxes start at root 1.
    private func root(_ doc: String, _ index: Int) throws -> IRComponent {
        try JSONDecoder().decode(IRDocument.self, from: Data(doc.utf8)).components[index]
    }

    func testAdjoiningFloatBeforeClearanceWireResolvesTo50() throws {
        // red(w100) > [ws-wrapper > float(L,100x50), cleared(mt400,
        // clear:left, h50)]. The 400 collapses through the wrapper AND the
        // red root's top edge, so with clear:none the float would be
        // pulled down WITH the cleared box (§8.3.1 adjoining) — clearance
        // is forced regardless of the margin, landing the top border edge
        // on the float's bottom outer edge: 0 + 50 = 50.
        let plan = FloatClearance.resolve(try root(Self.beforeClearance, 1))
        XCTAssertNotNil(plan, "the captured document must resolve a plan")
        XCTAssertEqual(plan?.adjustments["c3"]?.zeroFlowHeight, true)
        XCTAssertEqual(plan?.adjustments["c4"]?.appliedTopPx, 50)
    }

    func testAdjoiningFloatNewFcWireResolvesTo50() throws {
        // red(w100, overflow:hidden) > wrapper > [float(L,100x50),
        // cleared(mt300, clear:left, overflow:hidden, 100x50)]. The BFC
        // edge at the red root stops the ascent, but the wrapper IS
        // crossed — its float is pulled — so clearance is forced.
        let plan = FloatClearance.resolve(try root(Self.newFc, 1))
        XCTAssertNotNil(plan, "the captured document must resolve a plan")
        XCTAssertEqual(plan?.adjustments["c3"]?.zeroFlowHeight, true)
        XCTAssertEqual(plan?.adjustments["c4"]?.appliedTopPx, 50)
    }

    func testClearOnChildWithMargins2WirePercentageFloatsProject() throws {
        // red(w100, overflow:hidden) > [float(R,h20,w50%), mid >
        // [float(L,h100,w50%), wrap > cleared(clear:right,h80,mt16)]].
        // Percentage WIDTH never reaches the projection (only Height is
        // read), so the captured floats project. clear:right looks only at
        // the RIGHT float (bottom 20); the hypothetical top (16) is above
        // it → clearance moves the box to 20.
        let plan = FloatClearance.resolve(try root(Self.childMargins2, 1))
        XCTAssertNotNil(plan, "the captured document must resolve a plan")
        // BOTH floats leave the flow — the left one's 100px of stacked
        // height is the wave-41 Android failure signature (0.9499).
        XCTAssertEqual(plan?.adjustments["c2"]?.zeroFlowHeight, true)
        XCTAssertEqual(plan?.adjustments["c4"]?.zeroFlowHeight, true)
        XCTAssertEqual(plan?.adjustments["c6"]?.appliedTopPx, 20)
    }

    func testClearOnParentWithMarginsWireAbsorbsTheMinus1000Child() throws {
        // red(200x200) > [float(L,200x100), cleared(clear:left,mt100) >
        // child(h100,mt-1000)]. The §8.3.1 group {100,-1000} collapses to
        // -900 — far above the float — and the float is pulled by it, so
        // clearance pins the cleared box at the float bottom (100) and the
        // child renders flush at its content top.
        let plan = FloatClearance.resolve(try root(Self.parentMargins, 1))
        XCTAssertNotNil(plan, "the captured document must resolve a plan")
        XCTAssertEqual(plan?.adjustments["c2"]?.zeroFlowHeight, true)
        XCTAssertEqual(plan?.adjustments["c3"]?.appliedTopPx, 100)
        // The absorbed chain member's -1000 must not survive anywhere.
        XCTAssertEqual(plan?.adjustments["c4"]?.appliedTopPx, 0)
        XCTAssertEqual(plan?.adjustments["c4"]?.appliedBottomPx, 0)
    }

    func testNegativeClearanceAfterAdjoiningFloatWireCollapsesRedTo50() throws {
        // red(w100) > [float(L,100x50), cleared(clear:left, mt200, h0)],
        // then a SEPARATE green root (100x50). Forced clearance puts the
        // empty cleared box's border top at 50, so red's auto height is 50
        // and the following root stacks at 50..100 (the ref's lower half).
        let plan = FloatClearance.resolve(try root(Self.negativeClearance, 1))
        XCTAssertNotNil(plan, "the captured document must resolve a plan")
        XCTAssertEqual(plan?.adjustments["c2"]?.zeroFlowHeight, true)
        XCTAssertEqual(plan?.adjustments["c3"]?.appliedTopPx, 50)
    }

    func testClearAfterTopMarginIsOutOfScope() throws {
        // The float here is a ROOT-LEVEL SIBLING of the block holding the
        // clearing box (body > float, body > green-block > … >
        // clear:right), so no single composed root contains both actors:
        // resolve() sees a Clear with no Float in its subtree and returns
        // the identity. Fixing it needs the body-level root STACK to
        // become the clearance scope — that root loop lives in the capture
        // harnesses, outside this lane's ownership. Pinned as nil so the
        // boundary is explicit rather than accidental.
        XCTAssertNil(FloatClearance.resolve(try root(Self.afterTopMargin, 1)))
        XCTAssertNil(FloatClearance.resolve(try root(Self.afterTopMargin, 2)))
    }

    // ── The six captured documents (see the file banner for provenance) ──

    // css/CSS2/floats-clear/adjoining-float-before-clearance.html
    private static let beforeClearance = """
    {"irVersion":2,"minReaderVersion":2,"components":[
    {"id":"c0","name":"c0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
    {"id":"c1","name":"c1","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}]},
    {"id":"c2","name":"c2","properties":[],"slot":{"parent":"c1"},"meta":{"role":"ws-after"}},
    {"id":"c3","name":"c3","properties":[{"type":"Float","data":"LEFT"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c2"}},
    {"id":"c4","name":"c4","properties":[{"type":"MarginTop","data":{"px":400}},{"type":"Clear","data":"LEFT"},{"type":"Height","data":{"type":"length","px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c1"}}]}
    """

    // css/CSS2/floats-clear/adjoining-float-new-fc.html
    private static let newFc = """
    {"irVersion":2,"minReaderVersion":2,"components":[
    {"id":"c0","name":"c0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
    {"id":"c1","name":"c1","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}]},
    {"id":"c2","name":"c2","properties":[],"slot":{"parent":"c1"}},
    {"id":"c3","name":"c3","properties":[{"type":"Float","data":"LEFT"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c2"},"meta":{"role":"ws-after"}},
    {"id":"c4","name":"c4","properties":[{"type":"MarginTop","data":{"px":300}},{"type":"Clear","data":"LEFT"},{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c2"}}]}
    """

    // css/CSS2/floats-clear/clear-on-child-with-margins-2.html
    private static let childMargins2 = """
    {"irVersion":2,"minReaderVersion":2,"components":[
    {"id":"c0","name":"c0","properties":[],"text":"Test passes if there is a filled green square.","meta":{"sourceTag":"p","role":"ws-after"}},
    {"id":"c1","name":"c1","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"}]},
    {"id":"c2","name":"c2","properties":[{"type":"Float","data":"RIGHT"},{"type":"Height","data":{"type":"length","px":20}},{"type":"Width","data":{"type":"percentage","value":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c1"},"meta":{"role":"ws-after"}},
    {"id":"c3","name":"c3","properties":[],"slot":{"parent":"c1"}},
    {"id":"c4","name":"c4","properties":[{"type":"Float","data":"LEFT"},{"type":"Height","data":{"type":"length","px":100}},{"type":"Width","data":{"type":"percentage","value":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c3"},"meta":{"role":"ws-after"}},
    {"id":"c5","name":"c5","properties":[],"slot":{"parent":"c3"}},
    {"id":"c6","name":"c6","properties":[{"type":"Clear","data":"RIGHT"},{"type":"Height","data":{"type":"length","px":80}},{"type":"MarginTop","data":{"px":16}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c5"}}]}
    """

    // css/CSS2/floats-clear/clear-on-parent-with-margins.html
    private static let parentMargins = """
    {"irVersion":2,"minReaderVersion":2,"components":[
    {"id":"c0","name":"c0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
    {"id":"c1","name":"c1","properties":[{"type":"Width","data":{"type":"length","px":200}},{"type":"Height","data":{"type":"length","px":200}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}]},
    {"id":"c2","name":"c2","properties":[{"type":"Float","data":"LEFT"},{"type":"Width","data":{"type":"length","px":200}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c1"},"meta":{"role":"ws-after"}},
    {"id":"c3","name":"c3","properties":[{"type":"Clear","data":"LEFT"},{"type":"MarginTop","data":{"px":100}}],"slot":{"parent":"c1"}},
    {"id":"c4","name":"c4","properties":[{"type":"Height","data":{"type":"length","px":100}},{"type":"MarginTop","data":{"px":-1000}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c3"}}]}
    """

    // css/CSS2/floats-clear/negative-clearance-after-adjoining-float.html
    private static let negativeClearance = """
    {"irVersion":2,"minReaderVersion":2,"components":[
    {"id":"c0","name":"c0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
    {"id":"c1","name":"c1","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"meta":{"role":"ws-after"}},
    {"id":"c2","name":"c2","properties":[{"type":"Float","data":"LEFT"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"c1"},"meta":{"role":"ws-after"}},
    {"id":"c3","name":"c3","properties":[{"type":"Clear","data":"LEFT"},{"type":"MarginTop","data":{"px":200}}],"slot":{"parent":"c1"}},
    {"id":"c4","name":"c4","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}]}]}
    """

    // css/CSS2/floats-clear/clear-after-top-margin.html — the float
    // (root 1) and the clearing subtree (root 2) are SEPARATE roots.
    private static let afterTopMargin = """
    {"irVersion":2,"minReaderVersion":2,"components":[
    {"id":"c0","name":"c0","properties":[],"text":"Test passes if there is a filled green square.","meta":{"sourceTag":"p","role":"ws-after"}},
    {"id":"c1","name":"c1","properties":[{"type":"Float","data":"RIGHT"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}}],"meta":{"role":"ws-after"}},
    {"id":"c2","name":"c2","properties":[{"type":"PaddingTop","data":{"px":10}},{"type":"Width","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}]},
    {"id":"c3","name":"c3","properties":[{"type":"MarginTop","data":{"px":80}}],"slot":{"parent":"c2"}},
    {"id":"c4","name":"c4","properties":[{"type":"Clear","data":"RIGHT"}],"slot":{"parent":"c3"}}]}
    """
}
