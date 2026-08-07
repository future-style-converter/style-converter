//
//  AbsposCbUsedHeightTests.swift
//  Wave 33 (lane C) — pins for the abspos CB-HEIGHT channel
//  (CSS 2.2 §10.6.3 read through css-position-3 §3.1 / §10.6.4).
//
//  The rule is pure decisions + arithmetic with an identical signature on
//  Compose (AbsposCbUsedHeightTest.kt pins the same table entry-for-entry
//  so cross-native probes can diff the two rule tables directly), and the
//  final pin runs against the LIVE wave-32 IR shape
//  (tools/titan/runs/wave32-final/sections/css-tables/per-test-ir/
//  wpt__css-tables__absolute-tables-007.json).
//

import XCTest
@testable import StyleConverterRuntime

final class AbsposCbUsedHeightTests: XCTestCase {

    // MARK: - H1: only a positioned box establishes an abspos cb

    /// CSS 2.2 §10.1 item 4 — an abspos descendant skips past a static
    /// ancestor entirely, so this box's height is not anyone's basis.
    func testH1StaticAncestorPublishesNothing() throws {
        XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"STATIC"}"#),
            ancestorTag: "div", ancestorHasOwnContent: false,
            children: [try props(#"{"type":"Height","data":{"type":"length","px":100}}"#)]))
    }

    func testH1NoPositionDeclarationPublishesNothing() throws {
        XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
            ancestor: [], ancestorTag: "div", ancestorHasOwnContent: false,
            children: [try props(#"{"type":"Height","data":{"type":"length","px":100}}"#)]))
    }

    func testH1RelativeAbsoluteFixedAndStickyAllQualify() throws {
        for kw in ["RELATIVE", "ABSOLUTE", "FIXED", "STICKY"] {
            try assertPx(AbsposCbUsedHeight.contentHeightPx(
                ancestor: try props(#"{"type":"Position","data":"\#(kw)"}"#),
                ancestorTag: "div", ancestorHasOwnContent: false,
                children: [try props(#"{"type":"Height","data":{"type":"length","px":40}}"#)]),
                40, "position: \(kw)")
        }
    }

    // MARK: - H2: the declared-size channel always wins

    /// §10.6.3 IS the height:auto branch — a declared size is already
    /// answered, with the author's own number, upstream.
    func testH2DeclaredPxHeightDefersToTheDeclaredChannel() throws {
        XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#,
                                #"{"type":"Height","data":{"type":"length","px":250}}"#),
            ancestorTag: "div", ancestorHasOwnContent: false,
            children: [try props(#"{"type":"Height","data":{"type":"length","px":100}}"#)]))
    }

    func testH2DeclaredPercentageHeightAlsoDefers() throws {
        XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#,
                                #"{"type":"Height","data":{"type":"percentage","value":50}}"#),
            ancestorTag: "div", ancestorHasOwnContent: false,
            children: [try props(#"{"type":"Height","data":{"type":"length","px":100}}"#)]))
    }

    func testH2ExplicitAutoHeightIsStillTheAutoBranch() throws {
        try assertPx(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#,
                                #"{"type":"Height","data":"auto"}"#),
            ancestorTag: "div", ancestorHasOwnContent: false,
            children: [try props(#"{"type":"Height","data":{"type":"length","px":100}}"#)]),
            100)
    }

    /// §10.7 clamps the §10.6.3 result; this file does not model it, so
    /// answering would be a guess.
    func testH2MinOrMaxHeightClampRefuses() throws {
        for t in ["MinHeight", "MaxHeight", "MinBlockSize", "MaxBlockSize"] {
            XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
                ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#,
                                    #"{"type":"\#(t)","data":{"px":50}}"#),
                ancestorTag: "div", ancestorHasOwnContent: false,
                children: [try props(#"{"type":"Height","data":{"type":"length","px":100}}"#)]), t)
        }
    }

    // MARK: - H3: block container, overflow visible, not a UA table

    func testH3DeclaredFlexGridOrTableDisplayRefuses() throws {
        for kw in ["FLEX", "GRID", "TABLE", "INLINE_FLEX", "LIST_ITEM", "TABLE_CELL"] {
            XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
                ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#,
                                    #"{"type":"Display","data":"\#(kw)"}"#),
                ancestorTag: "div", ancestorHasOwnContent: false,
                children: [try props(#"{"type":"Height","data":{"type":"length","px":100}}"#)]), kw)
        }
    }

    func testH3BlockFlowRootAndInlineBlockAreBlockContainers() throws {
        for kw in ["BLOCK", "FLOW_ROOT", "INLINE_BLOCK"] {
            try assertPx(AbsposCbUsedHeight.contentHeightPx(
                ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#,
                                    #"{"type":"Display","data":"\#(kw)"}"#),
                ancestorTag: "div", ancestorHasOwnContent: false,
                children: [try props(#"{"type":"Height","data":{"type":"length","px":100}}"#)]),
                100, kw)
        }
    }

    /// The converter never serializes UA defaults, so meta.sourceTag is
    /// the only sighting of `<table>` (the AbsposInsetStretch S5 lane).
    func testH3BareTableTagRefusesButADeclaredDisplayWins() throws {
        XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#),
            ancestorTag: "table", ancestorHasOwnContent: false,
            children: [try props(#"{"type":"Height","data":{"type":"length","px":100}}"#)]))
        // css-display-3 §2 — a DECLARED display always wins over the tag.
        try assertPx(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#,
                                #"{"type":"Display","data":"BLOCK"}"#),
            ancestorTag: "table", ancestorHasOwnContent: false,
            children: [try props(#"{"type":"Height","data":{"type":"length","px":100}}"#)]),
            100)
    }

    func testH3NonVisibleOverflowIsTheUnmodelled1067Branch() throws {
        for t in ["OverflowX", "OverflowY", "Overflow", "OverflowBlock"] {
            XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
                ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#,
                                    #"{"type":"\#(t)","data":"HIDDEN"}"#),
                ancestorTag: "div", ancestorHasOwnContent: false,
                children: [try props(#"{"type":"Height","data":{"type":"length","px":100}}"#)]), t)
        }
        // The post-load-extracted corpus stamps VISIBLE explicitly — that
        // must NOT refuse, or the rule would never fire on a live IR.
        try assertPx(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#,
                                #"{"type":"OverflowX","data":"VISIBLE"}"#,
                                #"{"type":"OverflowY","data":"VISIBLE"}"#),
            ancestorTag: "div", ancestorHasOwnContent: false,
            children: [try props(#"{"type":"Height","data":{"type":"length","px":100}}"#)]),
            100)
    }

    // MARK: - H4: own line boxes are not statically measurable

    func testH4AncestorCarryingItsOwnTextRefuses() throws {
        XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#),
            ancestorTag: "div", ancestorHasOwnContent: true,
            children: [try props(#"{"type":"Height","data":{"type":"length","px":100}}"#)]))
    }

    // MARK: - H5: the per-child gate

    /// The asking abspos box is itself one of these (§9.3.1).
    func testH5OutOfFlowChildrenAreSkippedNotSummed() throws {
        try assertPx(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#),
            ancestorTag: "div", ancestorHasOwnContent: false,
            children: [
                try props(#"{"type":"Position","data":"ABSOLUTE"}"#,
                          #"{"type":"Height","data":{"type":"percentage","value":100}}"#),
                try props(#"{"type":"Position","data":"FIXED"}"#,
                          #"{"type":"Height","data":{"type":"length","px":999}}"#),
                try props(#"{"type":"Height","data":{"type":"length","px":100}}"#),
            ]), 100)
    }

    func testH5RelativeAndStickyChildrenStayInFlow() throws {
        try assertPx(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#),
            ancestorTag: "div", ancestorHasOwnContent: false,
            children: [
                try props(#"{"type":"Position","data":"RELATIVE"}"#,
                          #"{"type":"Height","data":{"type":"length","px":10}}"#),
                try props(#"{"type":"Position","data":"STICKY"}"#,
                          #"{"type":"Height","data":{"type":"length","px":20}}"#),
            ]), 30)
    }

    func testH5FloatLeavesTheOverflowVisibleSumSoTheRuleRefuses() throws {
        XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#),
            ancestorTag: "div", ancestorHasOwnContent: false,
            children: [try props(#"{"type":"Float","data":"LEFT"}"#,
                                 #"{"type":"Height","data":{"type":"length","px":100}}"#)]))
        // `float: none` is the initial value and never disqualifies.
        try assertPx(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#),
            ancestorTag: "div", ancestorHasOwnContent: false,
            children: [try props(#"{"type":"Float","data":"NONE"}"#,
                                 #"{"type":"Height","data":{"type":"length","px":100}}"#)]),
            100)
    }

    func testH5DisplayNoneChildRefuses() throws {
        XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#),
            ancestorTag: "div", ancestorHasOwnContent: false,
            children: [try props(#"{"type":"Display","data":"NONE"}"#,
                                 #"{"type":"Height","data":{"type":"length","px":100}}"#)]))
    }

    func testH5NonZeroVerticalBandRefusesBecauseMarginsCollapse() throws {
        for t in ["MarginTop", "MarginBottom", "PaddingTop", "BorderTopWidth"] {
            XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
                ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#),
                ancestorTag: "div", ancestorHasOwnContent: false,
                children: [try props(#"{"type":"\#(t)","data":{"px":8}}"#,
                                     #"{"type":"Height","data":{"type":"length","px":100}}"#)]), t)
        }
    }

    /// post-load-extract stamps every band as {"px":0}; refusing those
    /// would make the rule dead on every live IR.
    func testH5ExplicitZeroBandsAreFine() throws {
        try assertPx(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#),
            ancestorTag: "div", ancestorHasOwnContent: false,
            children: [try props(
                #"{"type":"MarginTop","data":{"px":0}}"#,
                #"{"type":"MarginBottom","data":{"px":0}}"#,
                #"{"type":"PaddingTop","data":{"px":0}}"#,
                #"{"type":"PaddingBottom","data":{"px":0}}"#,
                #"{"type":"BorderTopWidth","data":{"px":0}}"#,
                #"{"type":"BorderBottomWidth","data":{"px":0}}"#,
                #"{"type":"Height","data":{"type":"length","px":100}}"#)]),
            100)
    }

    /// The converter emits a percentage spacing value as a BARE number;
    /// reading it as px would bake the wrong band into the sum.
    func testH5PercentageBandIsNotPxSoTheRuleRefuses() throws {
        XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#),
            ancestorTag: "div", ancestorHasOwnContent: false,
            children: [try props(#"{"type":"MarginTop","data":10}"#,
                                 #"{"type":"Height","data":{"type":"length","px":100}}"#)]))
    }

    func testH5InFlowChildWithNoDefiniteHeightRefuses() throws {
        let heights = [
            nil,
            #"{"type":"Height","data":"auto"}"#,
            #"{"type":"Height","data":{"type":"percentage","value":50}}"#,
            #"{"type":"Height","data":{"expr":"calc(1px + 2px)"}}"#,
        ]
        for h in heights {
            let child = try h.map { try props($0) } ?? []
            XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
                ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#),
                ancestorTag: "div", ancestorHasOwnContent: false,
                children: [child]), h ?? "absent")
        }
    }

    func testH5LogicalBlockSizeSpellingResolvesLikeHeight() throws {
        try assertPx(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#),
            ancestorTag: "div", ancestorHasOwnContent: false,
            children: [try props(#"{"type":"BlockSize","data":{"type":"length","px":60}}"#)]),
            60)
    }

    // MARK: - H6/H7: the sum and the empty-box refusal

    func testH6SeveralInFlowChildrenSumInDocumentOrder() throws {
        try assertPx(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#),
            ancestorTag: "div", ancestorHasOwnContent: false,
            children: [
                try props(#"{"type":"Height","data":{"type":"length","px":25}}"#),
                try props(#"{"type":"Height","data":{"type":"length","px":50}}"#),
            ]), 75)
    }

    func testH7NoInFlowChildrenMeansNoHonestAnswer() throws {
        XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#),
            ancestorTag: "div", ancestorHasOwnContent: false, children: []))
        // Only out-of-flow children counts as "none" too.
        XCTAssertNil(AbsposCbUsedHeight.contentHeightPx(
            ancestor: try props(#"{"type":"Position","data":"RELATIVE"}"#),
            ancestorTag: "div", ancestorHasOwnContent: false,
            children: [try props(#"{"type":"Position","data":"ABSOLUTE"}"#,
                                 #"{"type":"Height","data":{"type":"length","px":100}}"#)]))
    }

    // MARK: - The live absolute-tables-007 wire

    /// Verbatim from tools/titan/runs/wave32-final/sections/css-tables/
    /// per-test-ir/wpt__css-tables__absolute-tables-007.json: a
    /// `position:relative; width:100px` wrapper holding the abspos green
    /// table and a 100px red block. The used content height is 100, so the
    /// table's `height:100%` covers the red exactly like the ref.
    func testTheLiveAbsoluteTables007WrapperResolvesTo100() throws {
        let wrapper = try props(#"{"type":"Position","data":"RELATIVE"}"#,
                                #"{"type":"Width","data":{"type":"length","px":100}}"#)
        let greenTable = try props(#"{"type":"Position","data":"ABSOLUTE"}"#,
                                   #"{"type":"Display","data":"TABLE"}"#,
                                   #"{"type":"Width","data":{"type":"percentage","value":100}}"#,
                                   #"{"type":"Height","data":{"type":"percentage","value":100}}"#)
        let redBlock = try props(#"{"type":"Height","data":{"type":"length","px":100}}"#)
        try assertPx(AbsposCbUsedHeight.contentHeightPx(
            ancestor: wrapper, ancestorTag: nil, ancestorHasOwnContent: false,
            children: [greenTable, redBlock]), 100)
    }

    // MARK: - Helpers

    /// Unwrap-then-compare: a nil answer is a DIFFERENT failure from a
    /// wrong number (the rule refusing vs. the rule mis-summing), so the
    /// unwrap must fail on its own rather than being folded into an
    /// optional comparison.
    private func assertPx(_ actual: Double?, _ expected: Double,
                          _ message: String = "",
                          file: StaticString = #filePath, line: UInt = #line) throws {
        let v = try XCTUnwrap(actual, message, file: file, line: line)
        XCTAssertEqual(v, expected, accuracy: 0.0001, message, file: file, line: line)
    }

    /// Build a property list by decoding a real IRComponent, so every pin
    /// runs against the SAME decode path the renderer uses (no hand-built
    /// IRValue trees that could drift from the wire).
    private func props(_ entries: String...) throws -> [IRProperty] {
        let json = #"{"id":"c","name":"c","properties":[\#(entries.joined(separator: ","))]}"#
        return try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8)).properties
    }
}
