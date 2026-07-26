//
//  ComposedRootStackTests.swift
//  StyleConverterRuntimeTests
//
//  Pins the RC-A4 (wave 19) declared-margin root-stack plan
//  (UABlockMargin.rootStackMargin + staticDeclaredEdges) that the composed
//  WPT canvas uses to COLLAPSE adjacent stacked roots' declared block
//  margins per CSS 2.1 §8.3.1 instead of stacking them (the safe-001
//  96px-vs-76px root-pitch drift). The R1–R7 expected values are IDENTICAL
//  to the Kotlin twin's pins (apps/android-harness UaBlockMarginsTest) so
//  the two implementations cannot drift apart silently.
//

import XCTest
import CoreGraphics
@testable import StyleConverterRuntime

final class ComposedRootStackTests: XCTestCase {

    // MARK: - IR helper (same wire-decoding pattern as UABlockMarginTests)

    /// Decode a childless component's `properties` from wire JSON —
    /// IRProperty is Decodable-only, so tests stay on live byte shapes.
    private func props(_ propsJSON: String) throws -> [IRProperty] {
        let json = "{\"id\":\"t\",\"name\":\"t\",\"properties\":[\(propsJSON)]}"
        return try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8)).properties
    }

    // MARK: - R1–R5: the per-root plan (shared pin table)

    func testR1PureUaRootKeepsRound4Contribution() {
        // R1: an undeclared <p> contributes its UA 16/16 and never strips.
        let plan = UABlockMargin.rootStackMargin(
            tag: "p", declaresTop: false, declaresBottom: false,
            staticDeclaredEdges: (top: 0, bottom: 0))
        XCTAssertEqual(plan, .init(top: 16, bottom: 16, stripDeclared: false))
    }

    func testR2Safe001RootDeclared20BothSidesFoldsAndStrips() {
        // R2: the safe-001 flex containers (margin: 20px, tag-less divs) —
        // declared px feed the fold and the root's block margins strip.
        let plan = UABlockMargin.rootStackMargin(
            tag: nil, declaresTop: true, declaresBottom: true,
            staticDeclaredEdges: (top: 20, bottom: 20))
        XCTAssertEqual(plan, .init(top: 20, bottom: 20, stripDeclared: true))
    }

    func testR3DeclaredTopOnlyMixesDeclaredAndUa() {
        // R3: declared top (20px) wins over UA; undeclared bottom keeps UA 16.
        let plan = UABlockMargin.rootStackMargin(
            tag: "p", declaresTop: true, declaresBottom: false,
            staticDeclaredEdges: (top: 20, bottom: 0))
        XCTAssertEqual(plan, .init(top: 20, bottom: 16, stripDeclared: true))
    }

    func testR4NonStaticDeclaredMarginsBailToRound4() {
        // R4: auto/negative/relative/calc (classifier nil) — both declared
        // sides render via MarginApplier (UA zeroed), no strip.
        let plan = UABlockMargin.rootStackMargin(
            tag: "p", declaresTop: true, declaresBottom: true,
            staticDeclaredEdges: nil)
        XCTAssertEqual(plan, .init(top: 0, bottom: 0, stripDeclared: false))
    }

    func testR5PartialBailKeepsUaOnUndeclaredSide() {
        // R5: only bottom declared and unresolvable — UA top survives.
        let plan = UABlockMargin.rootStackMargin(
            tag: "p", declaresTop: false, declaresBottom: true,
            staticDeclaredEdges: nil)
        XCTAssertEqual(plan, .init(top: 16, bottom: 0, stripDeclared: false))
    }

    // MARK: - R6/R7: the fold over the plans (stackedSpacing reuse)

    func testR6Safe001StackFourRootsCollapseTo20pxGaps() {
        // R6: four stripped 20/20 roots → every interior gap max(20,20)=20,
        // NOT 40 — the ref's 76px root pitch (56px box + 20px gap).
        let plans = (0..<4).map { _ in UABlockMargin.rootStackMargin(
            tag: nil, declaresTop: true, declaresBottom: true,
            staticDeclaredEdges: (top: 20, bottom: 20)) }
        let spacing = UABlockMargin.stackedSpacing(
            plans.map { (top: $0.top, bottom: $0.bottom) })
        XCTAssertEqual(spacing.leading, [20, 20, 20, 20])
        XCTAssertEqual(spacing.trailing, 20)
    }

    func testR7DeclaredAboveUaParagraphCollapsesToMax() {
        // R7: a div with declared margin-bottom 20 above an undeclared <p>
        // (UA 16 top) → interior gap max(20, 16) = 20.
        let above = UABlockMargin.rootStackMargin(
            tag: "div", declaresTop: false, declaresBottom: true,
            staticDeclaredEdges: (top: 0, bottom: 20))
        let below = UABlockMargin.rootStackMargin(
            tag: "p", declaresTop: false, declaresBottom: false,
            staticDeclaredEdges: (top: 0, bottom: 0))
        let spacing = UABlockMargin.stackedSpacing([
            (top: above.top, bottom: above.bottom),
            (top: below.top, bottom: below.bottom),
        ])
        XCTAssertEqual(spacing.leading, [0, 20])
        XCTAssertEqual(spacing.trailing, 16)
    }

    // MARK: - staticDeclaredEdges rides the runtime's §8.3.1 classifier

    func testStaticEdgesReadTheLiveSafe001WireShape() throws {
        // The exact per-side wire the safe-001 IR carries: {"px": 20}.
        let p = try props("""
        {"type":"MarginTop","data":{"px":20}},
        {"type":"MarginBottom","data":{"px":20}},
        {"type":"MarginLeft","data":{"px":20}},
        {"type":"MarginRight","data":{"px":20}}
        """)
        let edges = UABlockMargin.staticDeclaredEdges(p)
        XCTAssertEqual(edges?.top, 20)
        XCTAssertEqual(edges?.bottom, 20)
    }

    func testStaticEdgesBailOnAutoVerticalMargin() throws {
        // margin-top: auto is centering, not px — the classifier must bail
        // (nil) so the canvas keeps the Round 4 behavior for this root.
        let p = try props("""
        {"type":"MarginTop","data":"auto"},
        {"type":"MarginBottom","data":{"px":20}}
        """)
        XCTAssertNil(UABlockMargin.staticDeclaredEdges(p))
    }

    func testStaticEdgesTreatMarginlessRootAsZeroZero() throws {
        // No margin longhand at all → the CSS initial 0/0 (eligible), which
        // rootStackMargin then overrides per-side with the UA defaults.
        let p = try props("{\"type\":\"BackgroundColor\",\"data\":\"#fff\"}")
        let edges = UABlockMargin.staticDeclaredEdges(p)
        XCTAssertEqual(edges?.top, 0)
        XCTAssertEqual(edges?.bottom, 0)
    }

    // MARK: - Wave-19 follow-up: §8.3.1 collapse THROUGH transparent roots
    // Shared T1–T6 pin table, byte-parallel with the Kotlin twin
    // (UaBlockMarginsTest). A margin-transparent root has zero flow
    // footprint (on iOS: the RC1 static-position root behind
    // StaticPositionAnchor): the adjoining-margin set stays OPEN across it,
    // so its two margined neighbors share ONE max() gap instead of one each.

    /// Shorthand: an opaque stripped root plan.
    private func opaque(_ top: CGFloat, _ bottom: CGFloat) -> UABlockMargin.RootStackMargin {
        .init(top: top, bottom: bottom, stripDeclared: true)
    }
    /// Shorthand: a margin-transparent (zero-flow) root plan.
    private func transparent(_ top: CGFloat = 0, _ bottom: CGFloat = 0,
                             strip: Bool = false) -> UABlockMargin.RootStackMargin {
        .init(top: top, bottom: bottom, stripDeclared: strip, marginTransparent: true)
    }

    func testT1TransparentRootBetweenMarginedRootsCollapsesToOneGap() {
        // T1 — the composed shape: a transparent abspos static-position root
        // between two stripped margin:20px containers. The set {20,0,0,20}
        // resolves to ONE 20px gap, emitted ABOVE the transparent slot
        // (§8.3.1 collapse-through position — the slot sits where the
        // hypothetical in-flow box would); zero above the next container.
        // Inter-container space 20 → the ref's 76px root pitch (56 + 20).
        let s = UABlockMargin.stackedSpacing(plans: [opaque(20, 20), transparent(), opaque(20, 20)])
        XCTAssertEqual(s.leading, [20, 20, 0])
        XCTAssertEqual(s.trailing, 20)
    }

    func testT2ChainOfTwoTransparentRootsStillOneCollapsedGap() {
        // T2 — TWO transparent roots between the margined pair: the set stays
        // open across both — still exactly one 20px inter-container gap.
        let s = UABlockMargin.stackedSpacing(
            plans: [opaque(20, 20), transparent(), transparent(), opaque(20, 20)])
        XCTAssertEqual(s.leading, [20, 20, 0, 0])
        XCTAssertEqual(s.trailing, 20)
    }

    func testT3TransparentRootWithOwnMarginsParticipatesInTheMax() {
        // T3 — a transparent root with its OWN declared (stripped) margins:
        // the whole adjoining set {20, 30, 10, 20} collapses to max = 30
        // (§8.3.1's empty-box model), emitted once; its slot anchors at the
        // prefix max through its own top margin (30) — the hypothetical
        // static position — and the following root adds nothing.
        let s = UABlockMargin.stackedSpacing(
            plans: [opaque(0, 20), transparent(30, 10, strip: true), opaque(20, 0)])
        XCTAssertEqual(s.leading, [0, 30, 0])
        XCTAssertEqual(s.trailing, 0)
    }

    func testT4AllOpaquePlansMatchTheLegacyTupleFoldByteForByte() {
        // T4 — regression: with no transparent entry the plan fold IS the
        // Round 4 tuple fold (which now delegates), pinned on the R6
        // safe-001 shape: four stripped 20/20 containers → every gap 20.
        let plans = (0..<4).map { _ in opaque(20, 20) }
        let viaPlans = UABlockMargin.stackedSpacing(plans: plans)
        let viaTuples = UABlockMargin.stackedSpacing(
            plans.map { (top: $0.top, bottom: $0.bottom) })
        XCTAssertEqual(viaPlans.leading, viaTuples.leading)
        XCTAssertEqual(viaPlans.trailing, viaTuples.trailing)
        XCTAssertEqual(viaPlans.leading, [20, 20, 20, 20])
        XCTAssertEqual(viaPlans.trailing, 20)
    }

    func testT5LeadingAndTrailingTransparentRootsKeepPaddingBoundedEdges() {
        // T5 — transparent FIRST: nothing above its slot (the canvas padding
        // bounds the set) and the container's 20px top margin still emits in
        // full. Transparent LAST: the container's 20px bottom margin sits
        // above its slot; the trailing pad is 0 (total below stays 20).
        let lead = UABlockMargin.stackedSpacing(plans: [transparent(), opaque(20, 20)])
        XCTAssertEqual(lead.leading, [0, 20])
        XCTAssertEqual(lead.trailing, 20)
        let trail = UABlockMargin.stackedSpacing(plans: [opaque(20, 20), transparent()])
        XCTAssertEqual(trail.leading, [20, 20])
        XCTAssertEqual(trail.trailing, 0)
    }

    func testT6ZeroMarginTransparentRootBetweenSmallMargins() {
        // T6 — the dynamic-align-self-001 flow shape (8px UA-ish margins
        // around a zero-margin transparent slot): one 8px collapsed gap,
        // mirroring the Kotlin twin's hoisted-root pin (on iOS the hoisted
        // flavor never reaches the flow list, so the transparent entry here
        // stands for the static-position family).
        let s = UABlockMargin.stackedSpacing(plans: [opaque(8, 8), transparent(), opaque(8, 8)])
        XCTAssertEqual(s.leading, [8, 8, 0])
        XCTAssertEqual(s.trailing, 8)
    }
}
