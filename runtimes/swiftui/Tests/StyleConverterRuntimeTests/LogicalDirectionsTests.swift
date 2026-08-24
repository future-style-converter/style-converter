//
//  LogicalDirectionsTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 47 (lane Z2) — the css-writing-modes-4 §6.4 abstract-to-physical
//  side table (LogicalDirections) and its integration into the margin /
//  padding extractors plus the SizeExtractor axis swap. The identical
//  table is pinned on Android (LogicalDirectionsTest.kt).
//

import XCTest
@testable import StyleConverterRuntime

final class LogicalDirectionsTests: XCTestCase {

    /// Wire-shaped property decode (FragmentGeometryTests' helper style).
    private func props(_ json: String) throws -> [IRProperty] {
        try JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    // MARK: - The §6.4 table

    func testTableLtrRows() {
        XCTAssertEqual(LogicalSides.of(.horizontalTb, rtl: false),
            LogicalSides(blockStart: .top, blockEnd: .bottom,
                         inlineStart: .left, inlineEnd: .right))
        XCTAssertEqual(LogicalSides.of(.verticalRl, rtl: false),
            LogicalSides(blockStart: .right, blockEnd: .left,
                         inlineStart: .top, inlineEnd: .bottom))
        XCTAssertEqual(LogicalSides.of(.verticalLr, rtl: false),
            LogicalSides(blockStart: .left, blockEnd: .right,
                         inlineStart: .top, inlineEnd: .bottom))
        // sideways-rl boxes lay out exactly like vertical-rl (§4).
        XCTAssertEqual(LogicalSides.of(.sidewaysRl, rtl: false),
                       LogicalSides.of(.verticalRl, rtl: false))
        // sideways-lr: the one ltr mode whose inline-start is the BOTTOM.
        XCTAssertEqual(LogicalSides.of(.sidewaysLr, rtl: false),
            LogicalSides(blockStart: .left, blockEnd: .right,
                         inlineStart: .bottom, inlineEnd: .top))
    }

    func testTableRtlFlipsInlineSidesOnly() {
        XCTAssertEqual(LogicalSides.of(.verticalRl, rtl: true),
            LogicalSides(blockStart: .right, blockEnd: .left,
                         inlineStart: .bottom, inlineEnd: .top))
        XCTAssertEqual(LogicalSides.of(.horizontalTb, rtl: true),
            LogicalSides(blockStart: .top, blockEnd: .bottom,
                         inlineStart: .right, inlineEnd: .left))
    }

    /// The blast-radius contract: horizontal modes yield nil — the legacy
    /// two-pass fold stays the authority for every existing capture.
    func testVerticalOrNilIsNilForHorizontalModes() throws {
        XCTAssertNil(LogicalSides.verticalOrNil([]))
        XCTAssertNil(LogicalSides.verticalOrNil(try props(
            #"[{"type":"WritingMode","data":"HORIZONTAL_TB"},{"type":"Direction","data":"RTL"}]"#)))
    }

    // MARK: - Margin / padding extractor integration

    /// The css-break wall's exact shape: `margin-block-end: 20px` under
    /// vertical-rl is a LEFT margin; under vertical-lr a RIGHT one.
    func testMarginBlockEndMapsPerMode() throws {
        let rl = try XCTUnwrap(MarginExtractor.extract(from: try props(#"""
            [{"type":"WritingMode","data":"VERTICAL_RL"},
             {"type":"MarginBlockEnd","data":{"px":20.0}}]
            """#)))
        XCTAssertEqual(rl.left, .exact(px: 20), "vertical-rl: block-end = left")
        XCTAssertEqual(rl.bottom, .exact(px: 0), "bottom stays untouched")
        let lr = try XCTUnwrap(MarginExtractor.extract(from: try props(#"""
            [{"type":"WritingMode","data":"VERTICAL_LR"},
             {"type":"MarginBlockEnd","data":{"px":20.0}}]
            """#)))
        XCTAssertEqual(lr.right, .exact(px: 20), "vertical-lr: block-end = right")
    }

    /// Inline sides under vertical-rl+ltr land on TOP/BOTTOM, and the
    /// horizontal fold stays byte-identical without a WritingMode entry.
    func testPaddingMappingAndHorizontalLegacy() throws {
        let vertical = try XCTUnwrap(PaddingExtractor.extract(from: try props(#"""
            [{"type":"WritingMode","data":"VERTICAL_RL"},
             {"type":"PaddingInlineStart","data":{"px":5.0}},
             {"type":"PaddingBlockStart","data":{"px":8.0}}]
            """#)))
        XCTAssertEqual(vertical.top, .exact(px: 5), "vertical-rl: inline-start = top")
        XCTAssertEqual(vertical.right, .exact(px: 8), "vertical-rl: block-start = right")
        let legacy = try XCTUnwrap(PaddingExtractor.extract(from: try props(
            #"[{"type":"PaddingBlockStart","data":{"px":8.0}}]"#)))
        XCTAssertEqual(legacy.top, .exact(px: 8), "no writing mode: legacy fold")
    }

    // MARK: - SizeExtractor axis swap (css-logical-1 §4.1)

    /// The .container / .mc shapes: inline-size → height, block-size →
    /// width under vertical modes; horizontal mapping untouched; baked
    /// physical entries after the logical ones still win (the
    /// anchor-position-multicol wire order).
    func testSizeExtractorVerticalSwap() throws {
        let vertical = SizeExtractor.extract(from: try props(#"""
            [{"type":"WritingMode","data":"VERTICAL_RL"},
             {"type":"InlineSize","data":{"px":472.0}},
             {"type":"BlockSize","data":{"px":120.0}}]
            """#))
        XCTAssertEqual(vertical.height, .exact(px: 472), "inline-size → height")
        XCTAssertEqual(vertical.width, .exact(px: 120), "block-size → width")
        let horizontal = SizeExtractor.extract(from: try props(
            #"[{"type":"InlineSize","data":{"px":50.0}},{"type":"BlockSize","data":{"px":10.0}}]"#))
        XCTAssertEqual(horizontal.width, .exact(px: 50))
        XCTAssertEqual(horizontal.height, .exact(px: 10))
        let baked = SizeExtractor.extract(from: try props(#"""
            [{"type":"WritingMode","data":"VERTICAL_RL"},
             {"type":"InlineSize","data":{"px":100.0}},
             {"type":"BlockSize","data":{"px":70.0}},
             {"type":"Width","data":{"type":"length","px":450.0}},
             {"type":"Height","data":{"type":"length","px":20.0}}]
            """#))
        XCTAssertEqual(baked.width, .exact(px: 450), "baked physical wins by order")
        XCTAssertEqual(baked.height, .exact(px: 20), "baked physical wins by order")
    }
}
