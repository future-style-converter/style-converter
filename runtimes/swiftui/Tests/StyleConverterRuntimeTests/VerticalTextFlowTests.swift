//
//  VerticalTextFlowTests.swift
//  Wave 35 lane B5 — the vertical-flow decision module.
//
//  Every case here has a Kotlin twin of the same name in
//  runtimes/compose/src/test/java/com/styleconverter/runtime/typography/text/
//  VerticalTextFlowTest.kt; the two files are the contract that keeps the
//  natives from disagreeing about which runs are upright or where a vertical
//  line breaks.
//

import XCTest
@testable import StyleConverterRuntime

final class VerticalTextFlowTests: XCTestCase {

    // MARK: - §5.1 Vertical_Orientation classification

    func testFullwidthLatinIsUpright() {
        // The available-size-011 run: Ｐ Ａ Ｓ (U+FF30 / U+FF21 / U+FF33).
        XCTAssertTrue(VerticalTextFlow.isUprightOrientation(0xFF30))
        XCTAssertTrue(VerticalTextFlow.isUprightOrientation(0xFF21))
        XCTAssertTrue(VerticalTextFlow.isUprightOrientation(0xFF33))
    }

    func testCjkAndKanaAreUpright() {
        XCTAssertTrue(VerticalTextFlow.isUprightOrientation(0x4E00))   // 一
        XCTAssertTrue(VerticalTextFlow.isUprightOrientation(0x3042))   // あ
        XCTAssertTrue(VerticalTextFlow.isUprightOrientation(0xAC00))   // 가
        XCTAssertTrue(VerticalTextFlow.isUprightOrientation(0x20000))  // CJK Ext B
    }

    func testAsciiAndHalfwidthKanaRotate() {
        XCTAssertFalse(VerticalTextFlow.isUprightOrientation(0x41))    // A
        XCTAssertFalse(VerticalTextFlow.isUprightOrientation(0x30))    // 0
        // U+FF71 HALFWIDTH KATAKANA A — Vertical_Orientation R.
        XCTAssertFalse(VerticalTextFlow.isUprightOrientation(0xFF71))
    }

    // MARK: - §5.1 run orientation

    func testHorizontalModeHasNoOpinion() {
        XCTAssertNil(VerticalTextFlow.runOrientation(writingMode: .horizontalTb,
                                                     textOrientation: .mixed,
                                                     text: "ＰＡＳＳ"))
    }

    func testMixedClassifiesTheFullwidthRunUprightSpacesIgnored() {
        XCTAssertEqual(VerticalTextFlow.runOrientation(writingMode: .verticalRl,
                                                       textOrientation: .mixed,
                                                       text: "Ｓ Ｓ Ａ Ｐ"),
                       .upright)
    }

    func testMixedClassifiesAsciiRunsRotated() {
        // Every other vertical run in the frozen corpus: the available-size
        // asides ("0"), their interleaved digit strings, and
        // css-text-decor/line-through-vertical ("ABC ABC").
        for run in ["0", "0 0 0 0 0 0 0", "ABC ABC"] {
            XCTAssertEqual(VerticalTextFlow.runOrientation(writingMode: .verticalRl,
                                                           textOrientation: .mixed,
                                                           text: run),
                           .rotated, run)
        }
    }

    func testARunWithBothClassesIsMixedAndDeclines() {
        XCTAssertEqual(VerticalTextFlow.runOrientation(writingMode: .verticalRl,
                                                       textOrientation: .mixed,
                                                       text: "A漢"),
                       .mixed)
    }

    func testWhitespaceOnlyRunAnswersRotatedNotUpright() {
        XCTAssertEqual(VerticalTextFlow.runOrientation(writingMode: .verticalRl,
                                                       textOrientation: .mixed,
                                                       text: "   "),
                       .rotated)
    }

    func testSidewaysModesIgnoreTextOrientation() {
        // §3: sideways-rl / sideways-lr ARE "text-orientation: sideways", so
        // even an all-CJK run is typeset rotated there.
        XCTAssertEqual(VerticalTextFlow.runOrientation(writingMode: .sidewaysRl,
                                                       textOrientation: .upright,
                                                       text: "漢字"),
                       .rotated)
        XCTAssertEqual(VerticalTextFlow.runOrientation(writingMode: .sidewaysLr,
                                                       textOrientation: .mixed,
                                                       text: "漢字"),
                       .rotated)
    }

    func testExplicitTextOrientationKeywordsAreUnconditional() {
        XCTAssertEqual(VerticalTextFlow.runOrientation(writingMode: .verticalRl,
                                                       textOrientation: .upright,
                                                       text: "ABC"),
                       .upright)
        XCTAssertEqual(VerticalTextFlow.runOrientation(writingMode: .verticalLr,
                                                       textOrientation: .sideways,
                                                       text: "漢字"),
                       .rotated)
    }

    // MARK: - §3 line stacking

    func testLineStackingSideFollowsTheModeNotDirection() {
        XCTAssertEqual(VerticalTextFlow.lineStack(.verticalRl), .rightToLeft)
        XCTAssertEqual(VerticalTextFlow.lineStack(.sidewaysRl), .rightToLeft)
        XCTAssertEqual(VerticalTextFlow.lineStack(.verticalLr), .leftToRight)
        XCTAssertEqual(VerticalTextFlow.lineStack(.sidewaysLr), .leftToRight)
        XCTAssertNil(VerticalTextFlow.lineStack(.horizontalTb))
    }

    // MARK: - the column plan

    func testTheAvailableSize011RunBreaksOneGlyphPerLine() {
        // main { max-height: 1em; line-height: 1em } at the 16px body default
        // ⇒ a 16px budget against a 16px upright advance ⇒ capacity 1, and
        // the three separating spaces collapse at the breaks.
        XCTAssertEqual(VerticalTextFlow.uprightColumns(text: "Ｓ Ｓ Ａ Ｐ",
                                                       charAdvancePx: 16,
                                                       budgetPx: 16),
                       ["Ｓ", "Ｓ", "Ａ", "Ｐ"])
    }

    func testATallerBudgetPacksMoreGlyphsPerLine() {
        XCTAssertEqual(VerticalTextFlow.uprightColumns(text: "あいうえお",
                                                       charAdvancePx: 20,
                                                       budgetPx: 60),
                       ["あいう", "えお"])
    }

    func testSubPixelAdvanceStillFitsOneGlyphInItsOwnBudget() {
        // The measured advance carries float noise; a 16.0000001pt glyph in a
        // 16pt budget must not round to capacity 0.
        XCTAssertEqual(VerticalTextFlow.uprightColumns(text: "あい",
                                                       charAdvancePx: 16.0000001,
                                                       budgetPx: 16),
                       ["あ", "い"])
    }

    func testAnUnboundedBudgetDeclines() {
        XCTAssertNil(VerticalTextFlow.uprightColumns(text: "あい",
                                                     charAdvancePx: 16,
                                                     budgetPx: nil))
    }

    func testDegenerateInputsDecline() {
        XCTAssertNil(VerticalTextFlow.uprightColumns(text: "あい", charAdvancePx: 0, budgetPx: 16))
        XCTAssertNil(VerticalTextFlow.uprightColumns(text: "あい", charAdvancePx: 16, budgetPx: 0))
        XCTAssertNil(VerticalTextFlow.uprightColumns(text: "あい", charAdvancePx: 16, budgetPx: -4))
        XCTAssertNil(VerticalTextFlow.uprightColumns(text: "   ", charAdvancePx: 16, budgetPx: 16))
    }

    func testAPlanWiderThanTheCeilingDeclines() {
        let long = String(repeating: "あ", count: VerticalTextFlow.maxColumns + 2)
        XCTAssertNil(VerticalTextFlow.uprightColumns(text: long, charAdvancePx: 16, budgetPx: 16))
    }

    func testColumnIndicesAddressTheCodePointSlotsTheRendererComposed() {
        let glyphs = VerticalTextFlow.codePointsOf("Ｓ Ｓ Ａ Ｐ")
        XCTAssertEqual(glyphs.count, 7)  // 4 letters + 3 spaces
        let plan = VerticalTextFlow.uprightColumnIndices(glyphs: glyphs,
                                                         charAdvancePx: 16,
                                                         budgetPx: 16)
        // The dropped separators leave GAPS in the index sequence — which is
        // exactly why the renderer needs indices and not substrings.
        XCTAssertEqual(plan, [[0], [2], [4], [6]])
    }

    func testSurrogatePairsStayWhole() {
        // U+20000 is a surrogate pair in UTF-16; one scalar = one glyph.
        let run = String(UnicodeScalar(0x20000)!) + String(UnicodeScalar(0x20001)!)
        XCTAssertEqual(VerticalTextFlow.codePointsOf(run).count, 2)
        XCTAssertEqual(VerticalTextFlow.uprightColumns(text: run,
                                                       charAdvancePx: 16,
                                                       budgetPx: 16)?.count, 2)
    }

    // MARK: - the renderer gate

    func testGateAdmitsOnlyTheUprightVerticalRun() {
        let vertical = [IRProperty(type: "WritingMode", data: .string("VERTICAL_RL"))]
        XCTAssertEqual(VerticalUprightGate.stack(properties: vertical, text: "Ｓ Ｓ Ａ Ｐ"),
                       .rightToLeft)
        // ASCII in the same mode ⇒ rotated ⇒ the frozen horizontal chain.
        XCTAssertNil(VerticalUprightGate.stack(properties: vertical, text: "ABC ABC"))
        // No writing-mode anywhere ⇒ not ours.
        XCTAssertNil(VerticalUprightGate.stack(properties: [], text: "Ｓ Ｓ Ａ Ｐ"))
        // No text ⇒ nothing to typeset.
        XCTAssertNil(VerticalUprightGate.stack(properties: vertical, text: nil))
    }

    func testGateHonoursAnExplicitTextOrientation() {
        let sideways = [IRProperty(type: "WritingMode", data: .string("VERTICAL_LR")),
                        IRProperty(type: "TextOrientation", data: .string("sideways"))]
        XCTAssertNil(VerticalUprightGate.stack(properties: sideways, text: "漢字"))
        let upright = [IRProperty(type: "WritingMode", data: .string("VERTICAL_LR")),
                       IRProperty(type: "TextOrientation", data: .string("upright"))]
        XCTAssertEqual(VerticalUprightGate.stack(properties: upright, text: "ABC"), .leftToRight)
    }
}
