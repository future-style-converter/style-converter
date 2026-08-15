//
//  FloatClearanceTests.swift
//  Wave-42 lane W5 — XCTest pins for the pure §9.5.2 clearance twins
//  (FloatClearance.swift ↔ Compose FloatClearance.kt). Every pinned
//  value here mirrors FloatClearanceTest.kt VERBATIM so a skeptic probe
//  can diff the two suites. Geometry comes from the six failing CSS2/
//  floats-clear cells of the wave-41 gate (T7 evidence), each expected
//  position computed BY HAND from the fixture's CSS in the test comments.
//

import XCTest
@testable import StyleConverterRuntime

final class FloatClearanceTests: XCTestCase {

    // Bare keyword wire shape ({"type":"Float","data":"LEFT"}).
    private func kw(_ type: String, _ k: String) -> IRProperty {
        IRProperty(type: type, data: .string(k))
    }

    // Margin/padding wire shape ({"px":N}).
    private func px(_ type: String, _ v: Double) -> IRProperty {
        IRProperty(type: type, data: .object(["px": .double(v)]))
    }

    // Sized-length wire shape ({"type":"length","px":N} — Width/Height).
    private func len(_ type: String, _ v: Double) -> IRProperty {
        IRProperty(type: type, data: .object(["type": .string("length"), "px": .double(v)]))
    }

    // Component builder; roots carry no slot (the resolve attach gate).
    private func comp(_ id: String, _ props: [IRProperty],
                      _ children: [IRComponent]? = nil,
                      slotParent: String? = nil,
                      text: String? = nil,
                      tag: String? = nil) -> IRComponent {
        IRComponent(id: id, name: id, properties: props,
                    selectors: nil, media: nil, children: children,
                    slot: slotParent.map { IRSlot(parent: $0) },
                    text: text, pseudos: nil,
                    meta: tag.map { IRMeta(sourceTag: $0) })
    }

    // ── The six wave-41 cells, expected geometry hand-computed ──

    func testAdjoiningFloatBeforeClearanceForcedClearancePinsBoxAtFloatBottom() {
        // red > [wrapper > [float L 100x50], E(clear:left, mt:400, h:50)].
        // Hypothetical: the 400 collapses through wrapper AND red's top
        // edge, pulling the float down with it (§8.3.1 adjoining) — so
        // clearance is forced NO MATTER how large the margin is, and E's
        // top border edge lands at the float bottom: appliedTop = 50.
        let root = comp("red", [len("Width", 100)], [
            comp("w", [], [
                comp("f", [kw("Float", "LEFT"), len("Width", 100), len("Height", 50)])]),
            comp("e", [px("MarginTop", 400), kw("Clear", "LEFT"), len("Height", 50)]),
        ])
        let plan = FloatClearance.resolve(root)
        XCTAssertNotNil(plan)
        // The float leaves the flow; E's margin is replaced by the
        // clearance-adjusted 50 (400 absorbed by negative clearance).
        XCTAssertEqual(true, plan?.adjustments["f"]?.zeroFlowHeight)
        XCTAssertEqual(50.0, plan?.adjustments["e"]?.appliedTopPx)
        XCTAssertEqual(0.0, plan?.adjustments["e"]?.appliedBottomPx)
        // Scope covers every box so nested collapse plans suppress.
        XCTAssertEqual(Set(["red", "w", "f", "e"]), plan?.scopeIds)
    }

    func testAdjoiningFloatNewFcClearanceInsideABfcRoot() {
        // red(overflow:hidden) > inner > [float L 100x50,
        //   E(mt:300, clear:left, overflow:hidden, 100x50)].
        // The 300 collapses through inner's top edge (red's BFC edge
        // stops the chain) — inner would move, pulling its float: forced.
        let root = comp("red", [
            len("Width", 100), kw("OverflowX", "HIDDEN"), kw("OverflowY", "HIDDEN")], [
            comp("inner", [], [
                comp("f", [kw("Float", "LEFT"), len("Width", 100), len("Height", 50)]),
                comp("e", [px("MarginTop", 300), kw("Clear", "LEFT"),
                           kw("OverflowX", "HIDDEN"), kw("OverflowY", "HIDDEN"),
                           len("Width", 100), len("Height", 50)]),
            ]),
        ])
        let plan = FloatClearance.resolve(root)
        XCTAssertNotNil(plan)
        // E's border top = float bottom (50); the 300 is absorbed.
        XCTAssertEqual(true, plan?.adjustments["f"]?.zeroFlowHeight)
        XCTAssertEqual(50.0, plan?.adjustments["e"]?.appliedTopPx)
    }

    func testClearOnChildWithMargins2RightClearPastTheRightFloatOnly() {
        // red(oh) > [fR(right, h20), middle > [fL(left, h100),
        //   wrapper > [E(clear:right, h80, mt16)]]].
        // The 16 collapses up to red's BFC edge without displacing fR
        // (fR anchors in red, which is never crossed): hyp 16 < fR
        // bottom 20 → clearance → border top at 20 (browser: c=4+16).
        let root = comp("red", [
            len("Width", 100), kw("OverflowX", "HIDDEN"), kw("OverflowY", "HIDDEN")], [
            comp("fR", [kw("Float", "RIGHT"), len("Height", 20)]),
            comp("middle", [], [
                comp("fL", [kw("Float", "LEFT"), len("Height", 100)]),
                comp("wrapper", [], [
                    comp("e", [kw("Clear", "RIGHT"), len("Height", 80), px("MarginTop", 16)])]),
            ]),
        ])
        let plan = FloatClearance.resolve(root)
        XCTAssertNotNil(plan)
        // BOTH floats leave the flow (left one included — its 100px of
        // stacked height is the Android-only wave-41 failure signature).
        XCTAssertEqual(true, plan?.adjustments["fR"]?.zeroFlowHeight)
        XCTAssertEqual(true, plan?.adjustments["fL"]?.zeroFlowHeight)
        XCTAssertEqual(20.0, plan?.adjustments["e"]?.appliedTopPx)
    }

    func testClearOnParentWithMarginsTheMinus1000ChildMarginIsAbsorbed() {
        // red(200x200) > [f(left 200x100), E(clear:left, mt:100) >
        //   [child(h:100, mt:-1000)]].
        // E's top-margin group is {100, -1000} (parent/first-child
        // adjoining) — clearance absorbs the WHOLE group: E's border top
        // lands at the float bottom (100) and the child renders flush at
        // E's content top (its -1000 must not paint it offscreen, the
        // wave-41 capture's missing-green defect).
        let root = comp("red", [len("Width", 200), len("Height", 200)], [
            comp("f", [kw("Float", "LEFT"), len("Width", 200), len("Height", 100)]),
            comp("e", [kw("Clear", "LEFT"), px("MarginTop", 100)], [
                comp("child", [len("Height", 100), px("MarginTop", -1000)])]),
        ])
        let plan = FloatClearance.resolve(root)
        XCTAssertNotNil(plan)
        XCTAssertEqual(true, plan?.adjustments["f"]?.zeroFlowHeight)
        XCTAssertEqual(100.0, plan?.adjustments["e"]?.appliedTopPx)
        // The absorbed chain member's margins zero out entirely.
        XCTAssertEqual(0.0, plan?.adjustments["child"]?.appliedTopPx)
        XCTAssertEqual(0.0, plan?.adjustments["child"]?.appliedBottomPx)
    }

    func testNegativeClearanceAfterAdjoiningFloatRedBoxCollapsesTo50px() {
        // red > [f(left 100x50), E(clear:left, mt:200)] — then a sibling
        // ROOT follows in the document. Forced clearance (the 200 would
        // pull the float): E's border top = 50, so red's auto height is
        // 50 and the following green root lands at 50..100 (the ref's
        // lower green half).
        let root = comp("red", [len("Width", 100)], [
            comp("f", [kw("Float", "LEFT"), len("Width", 100), len("Height", 50)]),
            comp("e", [kw("Clear", "LEFT"), px("MarginTop", 200)]),
        ])
        let plan = FloatClearance.resolve(root)
        XCTAssertNotNil(plan)
        XCTAssertEqual(true, plan?.adjustments["f"]?.zeroFlowHeight)
        XCTAssertEqual(50.0, plan?.adjustments["e"]?.appliedTopPx)
    }

    func testClearOnParentIdenticalGeometryToTheFrozenAccidentalPass() {
        // red(200x200) > [f(left 200x100), E(clear:left) > [child(h100)]].
        // Wave-41 passed this by ACCIDENT (float stacked 100 + E at 100).
        // The honest model must produce the SAME pixels: float out of
        // flow at 0..100, E's border top = clear line 100.
        let root = comp("red", [len("Width", 200), len("Height", 200)], [
            comp("f", [kw("Float", "LEFT"), len("Width", 200), len("Height", 100)]),
            comp("e", [kw("Clear", "LEFT")], [
                comp("child", [len("Height", 100)])]),
        ])
        let plan = FloatClearance.resolve(root)
        XCTAssertNotNil(plan)
        XCTAssertEqual(100.0, plan?.adjustments["e"]?.appliedTopPx)
        XCTAssertEqual(0.0, plan?.adjustments["child"]?.appliedTopPx)
    }

    // ── No-clearance and bail pins (the identity guarantees) ──

    func testHypotheticalPastTheFloatNoClearanceIdentity() {
        // outer(padding-top:1) > [f(left 100x50), E(clear:left, mt:25) >
        //   [child(mt:150)]]. The padding band blocks the ascent (no
        //   pull); the collapsed group {25,150}=150 is past the float
        //   bottom 51 → §9.5.2 introduces NO clearance → nil (the
        //   clear-on-parent-with-margins-no-clearance discriminator).
        let root = comp("outer", [px("PaddingTop", 1)], [
            comp("f", [kw("Float", "LEFT"), len("Width", 100), len("Height", 50)]),
            comp("e", [kw("Clear", "LEFT"), px("MarginTop", 25)], [
                comp("child", [px("MarginTop", 150)])]),
        ])
        XCTAssertNil(FloatClearance.resolve(root))
    }

    func testBailsShapesOutsideTheProvenScopeResolveToNil() {
        let f = comp("f", [kw("Float", "LEFT"), len("Width", 50), len("Height", 50)])
        // Two clear boxes need sequential clearance state → nil.
        XCTAssertNil(FloatClearance.resolve(comp("r", [], [
            f, comp("e1", [kw("Clear", "LEFT")]), comp("e2", [kw("Clear", "LEFT")])])))
        // A float INSIDE the cleared subtree (adjoining-float-nested-
        // forced-clearance's inner 10px float) → nil.
        XCTAssertNil(FloatClearance.resolve(comp("r", [], [
            f, comp("e", [kw("Clear", "BOTH")], [
                comp("f2", [kw("Float", "LEFT"), len("Height", 10)])])])))
        // ≥2 consecutive same-side floats are the wave-19 RUN shape → nil.
        XCTAssertNil(FloatClearance.resolve(comp("r", [], [
            f, comp("f2", [kw("Float", "LEFT"), len("Height", 50)]),
            comp("e", [kw("Clear", "LEFT")])])))
        // A float with no provable height (auto) → nil.
        XCTAssertNil(FloatClearance.resolve(comp("r", [], [
            comp("fa", [kw("Float", "LEFT"), len("Width", 50)]),
            comp("e", [kw("Clear", "LEFT")])])))
        // Text anywhere in scope makes flow heights unknowable → nil.
        XCTAssertNil(FloatClearance.resolve(comp("r", [], [
            f, comp("e", [kw("Clear", "LEFT")])], text: "x")))
        // A positioned box in scope leaves the block-flow model → nil
        // (clear-on-parent-with-margins-no-clearance's relative outer).
        XCTAssertNil(FloatClearance.resolve(comp("r", [kw("Position", "RELATIVE")], [
            f, comp("e", [kw("Clear", "LEFT")])])))
        // Mid-tree non-BFC attach (slot parent, no overflow) → nil: the
        // scope could miss same-BFC floats above it.
        XCTAssertNil(FloatClearance.resolve(comp("r", [], [
            f, comp("e", [kw("Clear", "LEFT")])], slotParent: "above")))
        // Clear with no same-side float (clear:right, left float) → nil.
        XCTAssertNil(FloatClearance.resolve(comp("r", [], [
            f, comp("e", [kw("Clear", "RIGHT")])])))
        // A UA-margin tag in scope (only bare/div boxes are modeled).
        XCTAssertNil(FloatClearance.resolve(comp("r", [], [
            f, comp("p", [kw("Clear", "LEFT")], tag: "p")])))
        // An INTERMEDIATE wrapper with a block-start margin: the 30px is
        // adjoining to the root's top margin (§8.3.1) and may escape the
        // root, so the float's literal ledger position (30+50) is not
        // provable — bail rather than clear to a made-up line.
        XCTAssertNil(FloatClearance.resolve(comp("r", [], [
            comp("w", [px("MarginTop", 30)], [f]),
            comp("e", [kw("Clear", "LEFT"), px("MarginTop", 400)])])))
        // …and the mirror on the CLEARED box's own chain (its wrapper,
        // not the box itself — the box's own margin IS modeled).
        XCTAssertNil(FloatClearance.resolve(comp("r", [], [
            f, comp("w2", [px("MarginTop", 30)], [
                comp("e", [kw("Clear", "LEFT")])])])))
        // A float carrying its OWN block-axis padding: under the CSS
        // initial content-box its margin edge is mt+pt+h+pb+mb, but the
        // ledger adds only margins + height, so the clear line would be
        // SHORT by the padding band (here 10px) — bail, never guess.
        // Without this bail the same tree resolves appliedTop = 50.
        XCTAssertNil(FloatClearance.resolve(comp("r", [], [
            comp("fp", [kw("Float", "LEFT"), len("Width", 50), len("Height", 50),
                        px("PaddingBottom", 10)]),
            comp("e", [kw("Clear", "LEFT")])])))
        // …and a BoxSizing wire alone (zero padding) bails too: it decides
        // WHETHER `height` already contains the padding band, and the
        // projection cannot prove the used value either way.
        XCTAssertNil(FloatClearance.resolve(comp("r", [], [
            comp("fb", [kw("Float", "LEFT"), len("Width", 50), len("Height", 50),
                        kw("BoxSizing", "BORDER_BOX")]),
            comp("e", [kw("Clear", "LEFT")])])))
    }

    func testBfcRootReadsMatchTheTwinLogicalOverflowInNonKeywordOut() {
        // The two Kotlin/Swift divergence cases of the projection's BFC
        // read, pinned VERBATIM in FloatClearanceTest.kt. A BFC root
        // blocks the §8.3.1 ascent, so disagreeing here means the two
        // natives compute different clearance from identical bytes.
        // (a) `overflow-block: hidden` IS a BFC root: css-overflow-3 §3
        // maps the logical axis onto the block axis (horizontal-tb under
        // this lane's WritingMode bail) and a non-visible axis makes a
        // scroll container (CSS 2.1 §9.4.1). This file read only the three
        // physical keys and missed it.
        XCTAssertEqual(true, FloatClearanceModel.project(
            comp("r", [kw("OverflowBlock", "HIDDEN")]), parent: nil)?.bfcRoot)
        // (b) a NON-KEYWORD Overflow payload is undecodable, so the used
        // value stays the VISIBLE initial (OverflowExtractor's else-branch)
        // — never a BFC root. This file treated the failed decode as "not
        // visible" and turned every such box into one.
        XCTAssertEqual(false, FloatClearanceModel.project(
            comp("r", [px("Overflow", 5)]), parent: nil)?.bfcRoot)
        // The `{"type":"length"}` flavor too — the keyword extractor here
        // reads `type` as a keyword, so this one decoded to "LENGTH".
        XCTAssertEqual(false, FloatClearanceModel.project(
            comp("r", [len("Overflow", 5)]), parent: nil)?.bfcRoot)
        // The recognized non-visible keywords still make a BFC root.
        XCTAssertEqual(true, FloatClearanceModel.project(
            comp("r", [kw("Overflow", "HIDDEN")]), parent: nil)?.bfcRoot)
    }

    func testRunShapeStaysWithFloatRowPackingSegmentStillSeesIt() {
        // The run bail above must NOT orphan the wave-19 model: the same
        // two-float streak still segments as a run (pins P1/P2 hold).
        let facts = [
            FloatRowPacking.ChildFacts(floatsLeft: true, clearBreaksLeft: false),
            FloatRowPacking.ChildFacts(floatsLeft: true, clearBreaksLeft: false),
        ]
        let segs = FloatRowPacking.segment(facts)
        XCTAssertEqual(1, segs.count)
        XCTAssertEqual(true, segs.first?.isRun)
    }
}
