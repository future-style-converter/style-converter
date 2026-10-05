//
//  MulticolSpannerContainingBlockTests.swift
//  Wave 52 (lane L3 · failure-ink, pin P3) — the containing-block chain
//  across a `column-span: all` box (css-multicol-1 §6.1 read through
//  CSS 2.1 §10.1), the Kotlin MulticolSpannerContainingBlockTest table
//  BYTE-FOR-BYTE (S1 predicates, S2 restart, S3 stamp) plus the S4
//  end-to-end rows over `FixedHoist.split` — the iOS walk that consumes
//  the rule (Compose: CanvasRootHoist.collectCanvasHoisted).
//
//  Every payload is VERBATIM wave51-fix per-test IR:
//    tools/titan/runs/wave51-fix/sections/css-multicol/per-test-ir/
//      wpt__css-multicol__abspos-containing-block-outside-spanner.json
//  (`…__8-032` the multicol container, `…__8__0-033` the relpos wrapper,
//  `…__8__0__0-034` the spanner, `…__8__0__0__0-035` / `…__1-036` the two
//  green abspos probes). The ONLY document of the 1435 with an out-of-flow
//  descendant of a spanner (census F3) — the rule's whole live surface.
//
//  MUTATION RECORD (executed 2026-09-25, lane L3): (a) the spanner branch
//  of childPositionedAncestor dropped → S2 row 1 and S4 row 1 FAIL;
//  (b) hoistsToInitialContainingBlock forced false → S4 row 1 FAILS
//  (hoisted empty, probes kept under the spanner); both restored byte-exact
//  (sha256 verified), every row green again.
//

import XCTest
@testable import StyleConverterRuntime

final class MulticolSpannerContainingBlockTests: XCTestCase {

    /// Decode wire-shaped JSON — the production decode path.
    private func component(_ json: String) throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    /// A declaration list from wire JSON (through the same decoder).
    private func props(_ json: String) throws -> [IRProperty] {
        try component(#"{"id":"x","name":"x","properties":\#(json)}"#).properties
    }

    // The multicol container: `columns:3; column-gap:1em; width:20em`.
    private let multicol = #"[{"type":"ColumnCount","data":3},{"type":"ColumnGap","data":{"type":"length","original":{"v":1,"u":"EM"}}},{"type":"Width","data":{"type":"length","original":{"v":20,"u":"EM"}}}]"#
    // The wrapper column item: `position:relative` and nothing else.
    private let relpos = #"[{"type":"Position","data":"RELATIVE"}]"#
    // The spanner: `column-span:all; height:50px` — NOT positioned.
    private let spanner = #"[{"type":"ColumnSpan","data":"ALL"},{"type":"Height","data":{"type":"length","px":50}}]"#
    // The two green probes: absolute, inset-anchored at opposite corners.
    private let greenTL = #"{"id":"green-tl","name":"div","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"Height","data":{"type":"length","px":100}},{"type":"Width","data":{"type":"length","px":100}}]}"#
    private let greenBR = #"{"id":"green-br","name":"div","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Bottom","data":{"px":0}},{"type":"Right","data":{"px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"Height","data":{"type":"length","px":100}},{"type":"Width","data":{"type":"length","px":100}}]}"#

    /// The test document's multicol subtree, verbatim in shape; the middle
    /// box and outer box are parameters for the S4 negative controls.
    private func document(outer: String, middle: String, middleId: String = "spanner",
                          probes: String? = nil) throws -> IRComponent {
        try component(#"""
        {"id":"multicol","name":"div","properties":\#(outer),"children":[
          {"id":"relpos-wrapper","name":"div","properties":\#(relpos),"children":[
            {"id":"\#(middleId)","name":"div","properties":\#(middle),
             "children":[\#(probes ?? "\(greenTL),\(greenBR)")]}]}]}
        """#)
    }

    // MARK: - S1: the two wire predicates

    func testS1SpannerRecognisedWrapperNot() throws {
        XCTAssertTrue(MulticolSpannerContainingBlock.isSpanner(try props(spanner)))
        XCTAssertFalse(MulticolSpannerContainingBlock.isSpanner(try props(relpos)))
        // `column-span: none` is the initial value and must never match.
        XCTAssertFalse(MulticolSpannerContainingBlock.isSpanner(
            try props(#"[{"type":"ColumnSpan","data":"NONE"}]"#)))
    }

    func testS1ColumnCountBoxIsAContainerPlainBoxNot() throws {
        XCTAssertTrue(MulticolSpannerContainingBlock.isMulticolContainer(try props(multicol)))
        XCTAssertFalse(MulticolSpannerContainingBlock.isMulticolContainer(try props(relpos)))
        // css-multicol-1 §3: column-width alone also makes a container.
        XCTAssertTrue(MulticolSpannerContainingBlock.isMulticolContainer(
            try props(#"[{"type":"ColumnWidth","data":{"type":"length","px":120}}]"#)))
    }

    // MARK: - S2: the chain restart itself

    /// The wrapper published TRUE (position:relative) but the container's
    /// own context was FALSE: §6.1 skips the wrapper, children see FALSE.
    func testS2SpannerRestartsTheChainAtItsMulticolContainer() throws {
        XCTAssertFalse(MulticolSpannerContainingBlock.childPositionedAncestor(
            ancestorPositioned: true, positionedAtMulticol: false, properties: try props(spanner)))
    }

    /// Skipping OVER the wrapper is not skipping the spanner itself.
    func testS2SpannerOwnPositionStillCounts() throws {
        XCTAssertTrue(MulticolSpannerContainingBlock.childPositionedAncestor(
            ancestorPositioned: true, positionedAtMulticol: false,
            properties: try props(spanner) + props(relpos)))
    }

    /// Only boxes BETWEEN container and spanner are skipped.
    func testS2PositionedAncestorOutsideTheMulticolSurvives() throws {
        XCTAssertTrue(MulticolSpannerContainingBlock.childPositionedAncestor(
            ancestorPositioned: true, positionedAtMulticol: true, properties: try props(spanner)))
    }

    /// `column-span` computes to none with no multicol ancestor → inert.
    func testS2OutsideAnyMulticolTheRuleIsInert() throws {
        XCTAssertTrue(MulticolSpannerContainingBlock.childPositionedAncestor(
            ancestorPositioned: true, positionedAtMulticol: nil, properties: try props(spanner)))
    }

    /// A non-spanner keeps the frozen OR-accumulation, whatever the stamp.
    func testS2NonSpannerKeepsTheFrozenOrAccumulation() throws {
        XCTAssertTrue(MulticolSpannerContainingBlock.childPositionedAncestor(
            ancestorPositioned: false, positionedAtMulticol: false, properties: try props(relpos)))
        // A static box passes the incoming value straight through.
        XCTAssertFalse(MulticolSpannerContainingBlock.childPositionedAncestor(
            ancestorPositioned: false, positionedAtMulticol: false,
            properties: Array(try props(spanner).dropFirst())))
    }

    // MARK: - S3: the stamp

    func testS3MulticolContainerStampsItsOwnChainState() throws {
        XCTAssertEqual(true, MulticolSpannerContainingBlock.childPositionedAtMulticol(
            positionedAtMulticol: nil, childPositionedAncestor: true, properties: try props(multicol)))
        XCTAssertEqual(false, MulticolSpannerContainingBlock.childPositionedAtMulticol(
            positionedAtMulticol: nil, childPositionedAncestor: false, properties: try props(multicol)))
    }

    func testS3NonContainerPassesTheStampThroughUnchanged() throws {
        XCTAssertNil(MulticolSpannerContainingBlock.childPositionedAtMulticol(
            positionedAtMulticol: nil, childPositionedAncestor: true, properties: try props(relpos)))
        XCTAssertEqual(false, MulticolSpannerContainingBlock.childPositionedAtMulticol(
            positionedAtMulticol: false, childPositionedAncestor: true, properties: try props(relpos)))
    }

    // MARK: - S4: end-to-end over the real document (FixedHoist.split)

    /// The measured defect: the relpos wrapper claimed both probes, so the
    /// green squares painted mid-document and the red canvas-corner probe
    /// stayed uncovered (ios f 0.9553, 20 000 red px). Both probes now hoist
    /// to the canvas-root overlay (the ICB), stripped out of the spanner.
    func testS4BothGreenProbesHoistToTheInitialContainingBlock() throws {
        let split = FixedHoist.split(roots: [try document(outer: multicol, middle: spanner)])
        XCTAssertEqual(split.hoisted.map(\.id), ["green-tl", "green-br"])
        // The multicol stays in flow with the spanner emptied (nil children,
        // the decode contract) — nothing double-renders.
        XCTAssertEqual(split.flow.map(\.id), ["multicol"])
        XCTAssertNil(split.flow[0].children?[0].children?[0].children)
    }

    /// Negative control: drop `column-span: all` and the pre-wave-52 answer
    /// (nothing hoists — the relpos wrapper is the containing block) returns.
    func testS4WithoutTheSpannerTheWrapperStillClaimsBothProbes() throws {
        let plain = #"[{"type":"Height","data":{"type":"length","px":50}}]"#
        let split = FixedHoist.split(roots: [try document(outer: multicol, middle: plain, middleId: "plain-block")])
        XCTAssertTrue(split.hoisted.isEmpty)
        XCTAssertEqual(split.flow[0].children?[0].children?[0].children?.map(\.id), ["green-tl", "green-br"])
    }

    /// Same subtree under a plain block: `column-span` computes to none.
    func testS4SpannerWithNoMulticolAncestorChangesNothing() throws {
        let plainOuter = #"[{"type":"Width","data":{"type":"length","px":320}}]"#
        let split = FixedHoist.split(roots: [try document(outer: plainOuter, middle: spanner)])
        XCTAssertTrue(split.hoisted.isEmpty)
    }

    /// A `position: relative` root around the whole document: the restart
    /// restores THAT box, which legitimately contains the probes → no hoist,
    /// and the un-modelled §10.1 containing block is NAMED (fix pass: the
    /// breadcrumb; mutation "logOnce call removed" fails the last assert).
    func testS4PositionedAncestorOutsideTheMulticolStillVetoes() throws {
        // Fresh dedupe set so THIS split observes the breadcrumb.
        PropertyTracker._resetForTests()
        let outer = try component(#"""
        {"id":"outer-relpos","name":"div","properties":\#(relpos),"children":[]}
        """#).withChildren([try document(outer: multicol, middle: spanner)])
        XCTAssertTrue(FixedHoist.split(roots: [outer]).hoisted.isEmpty)
        // A fresh logOnce on the same key reports NOT-first → it was logged.
        XCTAssertFalse(PropertyTracker.logOnce(key: "multicol-spanner-abspos-outer-cb",
                                               message: "probe"))
    }

    /// A no-inset abspos under the spanner sits at its STATIC position
    /// (css-position-3 §3.1) — kept in the tree, like an inset-less root.
    func testS4NoInsetAbsoluteUnderTheSpannerStaysAtItsStaticPosition() throws {
        let noInset = #"{"id":"static-pos","name":"div","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Width","data":{"type":"length","px":100}}]}"#
        let split = FixedHoist.split(roots: [try document(outer: multicol, middle: spanner, probes: noInset)])
        XCTAssertTrue(split.hoisted.isEmpty)
    }
}
