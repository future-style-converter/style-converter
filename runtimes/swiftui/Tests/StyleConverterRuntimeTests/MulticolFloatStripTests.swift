//
//  MulticolFloatStripTests.swift
//  Wave-44 lane U8 — the FLOAT-STRIP pin table (FS rows).
//
//  Pins MulticolFloatStrip exactly; the Android twin
//  (runtimes/compose .../columns/MulticolFloatStripTest.kt) carries the
//  SAME FS rows byte-for-byte (native-pair parity gate). Every geometry
//  row is hand-derived from the frozen Chromium refs
//  (tools/wpt/refs/9b5435…/CSS2/floats-clear__floats-clear-multicol-*):
//   · fill refs: aqua strip rows 111-210 / col-3 111-160, 3px orange rows
//     161-163 → floats strip [0,250), cleared box at 250, H=100, C=253;
//   · balancing refs: aqua rows 91-175 / col-3 91-170, 5px orange rows
//     171-175 → H = ceil(255/3) = 85, C=255.
//  IR shapes mirror tools/titan/runs/wave43-final/sections/CSS2/
//  per-test-ir/wpt__CSS2__floats-clear__floats-clear-multicol-*.json.
//

import XCTest
@testable import StyleConverterRuntime

final class MulticolFloatStripTests: XCTestCase {

    // MARK: - IR builders (the live wire shapes)

    /// A leaf component with just properties (the spanner-test helper).
    private func component(_ id: String, _ properties: [IRProperty],
                           children: [IRComponent]? = nil) -> IRComponent {
        IRComponent(id: id, name: id, properties: properties,
                    selectors: nil, media: nil, children: children, slot: nil,
                    text: nil, pseudos: nil, meta: nil)
    }

    /// A px length wire: `{"type":"length","px":N}` (converter shape).
    private func px(_ n: Double) -> IRValue {
        .object(["type": .string("length"), "px": .double(n)])
    }

    /// One aqua float box exactly as the wave43-final IRs carry it.
    private func floatBox(_ id: String, _ side: String, _ h: Double) -> IRComponent {
        component(id, [IRProperty(type: "Float", data: .string(side)),
                       IRProperty(type: "Width", data: px(15)),
                       IRProperty(type: "Height", data: px(h))])
    }

    /// The `.container` (width:100%; two floats) of -000/002/balancing.
    private func container(_ h: Double = 250) -> IRComponent {
        component("container",
                  [IRProperty(type: "Width",
                              data: .object(["type": .string("percentage"), "value": .double(100)]))],
                  children: [floatBox("fL", "LEFT", h), floatBox("fR", "RIGHT", h)])
    }

    /// The -002 `.clear` (clear:left; medium orange border-bottom).
    private var clear002: IRComponent {
        component("clear", [IRProperty(type: "Clear", data: .string("LEFT")),
                            IRProperty(type: "BorderBottomStyle", data: .string("SOLID"))])
    }

    /// The -003 `.step` (height:10; 15px aqua side borders, none top/bottom).
    private var step003: IRComponent {
        component("step", [
            IRProperty(type: "Height", data: px(10)),
            IRProperty(type: "BorderLeftStyle", data: .string("SOLID")),
            IRProperty(type: "BorderRightStyle", data: .string("SOLID")),
            IRProperty(type: "BorderTopStyle", data: .string("NONE")),
            IRProperty(type: "BorderBottomStyle", data: .string("NONE")),
            IRProperty(type: "BorderLeftWidth", data: .object(["px": .double(15)])),
            IRProperty(type: "BorderRightWidth", data: .object(["px": .double(15)])),
        ])
    }

    /// The -003 `.clear` (clear:left; height:0) holding the orange `.bar`.
    private func clear003(_ barWidth: Double?) -> IRComponent {
        var barProps = [IRProperty(type: "BorderBottomStyle", data: .string("SOLID"))]
        if let w = barWidth {
            barProps.append(IRProperty(type: "BorderBottomWidth", data: .object(["px": .double(w)])))
        }
        return component("clear",
                         [IRProperty(type: "Clear", data: .string("LEFT")),
                          IRProperty(type: "Height", data: px(0))],
                         children: [component("bar", barProps)])
    }

    // MARK: - FS-A: the facts projection (factsFor)

    /// FS-A1 — container facts: two 250px floats anchored at content top.
    func testFSA1ContainerFacts() {
        let f = MulticolFloatStrip.factsFor(container())
        XCTAssertNotNil(f)
        // One float per side, 250px each, ids preserved (zero-flow keys).
        XCTAssertEqual(f?.floats, [
            .init(componentId: "fL", rightSide: false, heightPx: 250),
            .init(componentId: "fR", rightSide: true, heightPx: 250),
        ])
        // The container clears nothing, paints no trailing ink of its own.
        XCTAssertEqual(f?.clearsLeft, false)
        XCTAssertEqual(f?.clearsRight, false)
        XCTAssertEqual(f?.trailingInkPx, 0)
    }

    /// FS-A2 — cleared box facts: clear:left + the medium 3px border ink
    /// (css-backgrounds-3 §4.3 — no declared width + SOLID ⇒ medium 3px,
    /// the same default the border applier paints).
    func testFSA2ClearedBoxFacts() {
        let f = MulticolFloatStrip.factsFor(clear002)
        XCTAssertEqual(f?.clearsLeft, true)
        XCTAssertEqual(f?.clearsRight, false)
        XCTAssertEqual(f?.trailingInkPx, 3)
        XCTAssertEqual(f?.floats, [])
    }

    /// FS-A3 — height:0 cleared box: the inner bar's border is the ink
    /// (css-overflow-3 §2 — the bar paints below the measured 0, and
    /// Chromium's balanced column height includes the band).
    func testFSA3HeightZeroTrailingInk() {
        XCTAssertEqual(MulticolFloatStrip.factsFor(clear003(nil))?.trailingInkPx, 3)
        // -balancing-003 declares the width: 5px.
        XCTAssertEqual(MulticolFloatStrip.factsFor(clear003(5))?.trailingInkPx, 5)
    }

    /// FS-A4 — step facts: declared height is the ink floor; the 15px
    /// side borders add no block-axis extent (top/bottom styles NONE).
    func testFSA4StepFacts() {
        XCTAssertEqual(MulticolFloatStrip.factsFor(step003)?.trailingInkPx, 10)
    }

    /// FS-A5 — strict bails: unproven shapes project to nil.
    func testFSA5StrictBails() {
        // Two same-side floats stack per §9.5.1 rules 2/3 — not modeled.
        XCTAssertNil(MulticolFloatStrip.factsFor(
            component("c", [], children: [floatBox("a", "LEFT", 10), floatBox("b", "LEFT", 10)])))
        // A margin breaks the flush cursor stacking.
        XCTAssertNil(MulticolFloatStrip.factsFor(
            component("c", [IRProperty(type: "MarginTop", data: px(4))])))
        // A padded float's outer edge is taller than its height (the
        // FloatClearance padded-float bail, same reasoning).
        XCTAssertNil(MulticolFloatStrip.factsFor(
            component("c", [], children: [
                component("f", [IRProperty(type: "Float", data: .string("LEFT")),
                                IRProperty(type: "Height", data: px(10)),
                                IRProperty(type: "PaddingTop", data: px(2))]),
            ])))
        // A float hiding PAST the leading run anchors somewhere this
        // model cannot pin.
        XCTAssertNil(MulticolFloatStrip.factsFor(
            component("c", [], children: [component("plain", []), floatBox("late", "LEFT", 10)])))
        // A float without an explicit px height has no §9.5.2 ledger entry.
        XCTAssertNil(MulticolFloatStrip.factsFor(
            component("c", [], children: [component("f", [IRProperty(type: "Float", data: .string("LEFT"))])])))
        // Relative positioning paints away from the flow slot (§9.4.3).
        XCTAssertNil(MulticolFloatStrip.factsFor(
            component("c", [IRProperty(type: "Position", data: .string("RELATIVE"))])))
    }

    // MARK: - FS-B: engagement (through the shared specsFor classifier)

    /// specsFor over the -002 IR shape — the twin's integration entry.
    private func specs002() -> [MulticolSpannerFlow.ChildSpec] {
        MulticolSpannerFlow.specsFor(children: [container(), clear002])
    }

    /// FS-B1 — the 002 shape engages; the facts ride the specs.
    func testFSB1The002ShapeEngages() {
        let specs = specs002()
        XCTAssertTrue(MulticolFloatStrip.engages(specs))
        // The float ids the (Android-consumed) zero-flow plan would key.
        XCTAssertEqual(specs[0].floatStrip?.floats.map(\.componentId), ["fL", "fR"])
    }

    /// FS-B2 — float-less containers and unproven children never engage.
    func testFSB2NonEngagement() {
        // No floats anywhere: the run/greedy paths already render this.
        XCTAssertFalse(MulticolFloatStrip.engages(
            MulticolSpannerFlow.specsFor(children: [clear002, step003])))
        // One unproven child (margin wire) disables the WHOLE strip.
        XCTAssertFalse(MulticolFloatStrip.engages(
            MulticolSpannerFlow.specsFor(children: [
                container(), component("m", [IRProperty(type: "MarginTop", data: px(1))]),
            ])))
        // Nil specs (dark stage) and the leading-text head both bail.
        XCTAssertFalse(MulticolFloatStrip.engages(nil))
        XCTAssertFalse(MulticolFloatStrip.engages(
            MulticolSpannerFlow.specsFor(children: [container()], leadingText: true)))
    }

    // MARK: - FS-C: the strip geometry (plan) — ref-derived rows

    /// Shared plan call with the family's used geometry (N=3, W=100, G=0).
    private func plan(_ specs: [MulticolSpannerFlow.ChildSpec],
                      _ measured: [Double], fillAuto: Bool,
                      h: Double = 100, n: Int = 3) -> MulticolFloatStrip.StripPlan? {
        MulticolFloatStrip.plan(specs: specs, measuredHeightsPx: measured,
                                columnBlockSize: h, columnFillAuto: fillAuto,
                                columnWidth: 100, gapPx: 0, columnCount: n)
    }

    /// FS-C1 — fill 002: cleared box at 250, C 253, three 100px slices.
    func testFSC1Fill002() {
        // Measured under zero-flow: container 0, cleared box 3.
        let p = plan(specs002(), [0, 3], fillAuto: true)!
        // §9.5.2: clear:left jumps the cleared box to the left float's
        // bottom outer edge 250 (ref: orange rows 161-163 = strip 250-253).
        XCTAssertEqual(p.yOffsetsPx, [0, 250])
        XCTAssertEqual(p.columnBlockSizePx, 100)
        XCTAssertEqual(p.stripInkPx, 253)
        // Three slices — the wave-10 S-geometry with C=253, H=100.
        XCTAssertEqual(p.fragments,
                       FragmentGeometry.fragments(childBlockSize: 253, columnBlockSize: 100,
                                                  columnWidth: 100, gapPx: 0, columnCount: 3))
        XCTAssertEqual(p.fragments.count, 3)
        // Slice 2 shows strip [200,300): aqua 200-250 + orange 250-253.
        XCTAssertEqual(p.fragments[2].translate, CGSize(width: 200, height: -200))
    }

    /// FS-C2 — fill 003: step then floats then clearance, same slices.
    func testFSC2Fill003() {
        let specs = MulticolSpannerFlow.specsFor(
            children: [step003, container(240), clear003(nil)])
        XCTAssertTrue(MulticolFloatStrip.engages(specs))
        // Measured: step 10, container 0, cleared box 0 (height:0 —
        // trailingInkPx 3 carries the bar's band).
        let p = plan(specs, [10, 0, 0], fillAuto: true)!
        // Floats anchor at the container's top = strip 10; 240px extent
        // ends at 250 — the same clear line as -002 (ref-identical).
        XCTAssertEqual(p.yOffsetsPx, [0, 10, 250])
        XCTAssertEqual(p.stripInkPx, 253)
        XCTAssertEqual(p.fragments.count, 3)
    }

    /// FS-C3 — balance 002: the 5px border joins C; columns balance to 85.
    func testFSC3Balance002() {
        let specs = MulticolSpannerFlow.specsFor(children: [
            container(),
            component("clear", [IRProperty(type: "Clear", data: .string("LEFT")),
                                IRProperty(type: "BorderBottomStyle", data: .string("SOLID")),
                                IRProperty(type: "BorderBottomWidth", data: .object(["px": .double(5)]))]),
        ])
        let p = plan(specs, [0, 5], fillAuto: false)!
        // §7.1 reduced: H = min(100, ceil(255/3)) = 85 — the balancing
        // refs' exact column height (aqua rows 91-175, orange 171-175).
        XCTAssertEqual(p.columnBlockSizePx, 85)
        XCTAssertEqual(p.stripInkPx, 255)
        XCTAssertEqual(p.yOffsetsPx, [0, 250])
        // Slice 2 shows strip [170,255): aqua 170-250 + orange 250-255.
        XCTAssertEqual(p.fragments.count, 3)
        XCTAssertEqual(p.fragments[2].translate, CGSize(width: 200, height: -170))
        XCTAssertEqual(p.fragments[2].clipRect.height, 85)
    }

    /// FS-C4 — balance 003: the height:0 box still carries 5px into C
    /// (without trailingInkPx C would be 250 → H 84, one px off every
    /// slice; the IR ink floor restores Chromium's 255 → 85).
    func testFSC4Balance003() {
        let specs = MulticolSpannerFlow.specsFor(
            children: [step003, container(240), clear003(5)])
        let p = plan(specs, [10, 0, 0], fillAuto: false)!
        XCTAssertEqual(p.columnBlockSizePx, 85)
        XCTAssertEqual(p.stripInkPx, 255)
        XCTAssertEqual(p.yOffsetsPx, [0, 10, 250])
    }

    /// FS-C5 — no Clear wire: floats still strip, sibling at the cursor.
    /// The -000/-001 IR shape: `<br clear=all>` reaches the IR with NO
    /// Clear wire (tools/titan/extract-fixture.mjs:9789 reads only
    /// rule-matched declarations — the HTML clear attribute is lost), so
    /// the aqua geometry fixes while the orange line stays misplaced
    /// until the feeder emits the wire (deferred, lane report).
    func testFSC5NoClearWire() {
        let noClear = component("clear",
                                [IRProperty(type: "BorderBottomStyle", data: .string("SOLID"))],
                                children: [component("br", [
                                    IRProperty(type: "Width", data: px(0)),
                                    IRProperty(type: "Height", data: px(20)),
                                ])])
        let specs = MulticolSpannerFlow.specsFor(children: [container(), noClear])
        let p = plan(specs, [0, 23], fillAuto: true)!
        // No clearance jump — but the float ledger still spans C to 250.
        XCTAssertEqual(p.yOffsetsPx, [0, 0])
        XCTAssertEqual(p.stripInkPx, 250)
        XCTAssertEqual(p.fragments.count, 3)
    }

    /// FS-C6 — balance cannot exceed the definite height (the fill cap):
    /// a 403px strip in 3 columns of definite 100 caps at 100, and
    /// ceil(403/100)=5 fragments cap at N=3 (the wave-10 overflow cap).
    func testFSC6BalanceCap() {
        let specs = MulticolSpannerFlow.specsFor(children: [container(400), clear002])
        let p = plan(specs, [0, 3], fillAuto: false)!
        XCTAssertEqual(p.columnBlockSizePx, 100)
        XCTAssertEqual(p.fragments.count, 3)
    }

    /// FS-C7 — geometry bails: one column, misaligned inputs, no H.
    func testFSC7GeometryBails() {
        // N == 1 keeps overflow-not-clip semantics (the run twin's rule).
        XCTAssertNil(plan(specs002(), [0, 3], fillAuto: true, n: 1))
        // Misaligned measured list — bail rather than zip wrong.
        XCTAssertNil(plan(specs002(), [0], fillAuto: true))
        // Non-positive definite H has no fragmentainer.
        XCTAssertNil(plan(specs002(), [0, 3], fillAuto: true, h: 0))
    }
}
