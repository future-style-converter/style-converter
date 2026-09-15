//
//  GapDecorationBandsTests.swift
//  Wave 50, lane B10 — pins the css-flexbox-1 §9.4 step 8 LINE BOX
//  against the frozen Chromium refs, and pins the fallbacks that keep
//  every other container on the pre-wave-50 item-union geometry.
//  Twin of Compose's GapDecorationBandsTest.kt.
//
//  EVERY number below is measured, not hand-derived:
//   * item frames come from the wave49-final captures, read back with a
//     connected-component scan of the `#007bff` item fill —
//     tools/titan/runs/wave49-final/sections/css-gaps/ios-screenshots/
//     wpt__css-gaps__flex__flex-gap-decorations-045.png (and -046). The
//     ref, iOS and Android captures agree to the pixel on both fixtures,
//     so the ITEM geometry is not in dispute; only the RULE geometry is.
//   * expected rule bands come from the frozen ref under
//     tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
//     white-black-ink-font-lh-imgpad-htmlpins/css-gaps/.
//   * the "what the union model paints today" expectations come from the
//     same iOS captures, so the defect is pinned by the pixels that
//     exhibit it rather than by an argument.
//
//  Image → content space: each capture carries a 16pt image pad
//  (capture-browser-ref.mjs padPngBuffer) plus the fixture's own border,
//  so content x = image x − 18 on 045 (16 pad + 2pt border) and − 16 on
//  046 (16 pad, no border).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class GapDecorationBandsTests: XCTestCase {

    /// Points of slack allowed when comparing geometry; SwiftUI works in
    /// fractional points and 1/1000 pt is far below a paintable rule.
    private let eps: CGFloat = 0.001

    private func solid(_ w: CGFloat) -> GapRuleSpec {
        GapRuleSpec(style: .solid, widthPx: w)
    }

    private func assertRects(_ got: [CGRect], _ want: [CGRect],
                             _ msg: String, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(got.count, want.count, msg, file: file, line: line)
        for (g, w) in zip(got, want) {
            XCTAssertEqual(g.minX, w.minX, accuracy: eps, msg, file: file, line: line)
            XCTAssertEqual(g.minY, w.minY, accuracy: eps, msg, file: file, line: line)
            XCTAssertEqual(g.maxX, w.maxX, accuracy: eps, msg, file: file, line: line)
            XCTAssertEqual(g.maxY, w.maxY, accuracy: eps, msg, file: file, line: line)
        }
    }

    // MARK: - WPT flex-gap-decorations-045

    // `flex-direction: column; flex-wrap: wrap; align-content: stretch;`
    // width 120 content, column-gap 5, nine 50×70 items, height 400.
    // Captured item boxes (content space, ref == iOS == Android):
    //   line 1  x [0,50]   y [0,70] [83,153] [165,235] [248,318] [330,400]
    //   line 2  x [63,113] y [0,70] [110,180] [220,290] [330,400]
    // Main axis is VERTICAL, so the item-to-item gaps carry the ROW rules
    // (gold) and the inter-line gap carries the COLUMN rule (red).
    private let frames045: [CGRect] = [
        CGRect(x: 0, y: 0, width: 50, height: 70),
        CGRect(x: 0, y: 83, width: 50, height: 70),
        CGRect(x: 0, y: 165, width: 50, height: 70),
        CGRect(x: 0, y: 248, width: 50, height: 70),
        CGRect(x: 0, y: 330, width: 50, height: 70),
        CGRect(x: 63, y: 0, width: 50, height: 70),
        CGRect(x: 63, y: 110, width: 50, height: 70),
        CGRect(x: 63, y: 220, width: 50, height: 70),
        CGRect(x: 63, y: 330, width: 50, height: 70),
    ]
    private let size045 = CGSize(width: 120, height: 400)

    private func config045(columnGap: CGFloat? = 5) -> GapDecorationsConfig {
        var c = GapDecorationsConfig()
        c.column = solid(5)
        c.row = solid(5)
        c.columnGapPx = columnGap
        c.rowGapPx = 0
        return c
    }

    func testLineBoxesTileTheContentBox045() {
        let unions = GapDecorationLines.lines(from: frames045, mainHorizontal: false)
        // The union model's answer: the two item columns.
        XCTAssertEqual(unions.map(\.crossStart), [0, 63])
        XCTAssertEqual(unions.map(\.crossEnd), [50, 113])
        let bands = GapDecorationBands.resolve(lines: unions, contentSize: size045,
                                               mainHorizontal: false,
                                               crossGapPx: 5, alignContentStretches: true)
        // §9.4 step 8: leftover = 120 − 50 − 50 − 5 = 15, split evenly
        // (FlexWrapPlan.stretchLines is the exact fractional division the
        // FlowLayout that placed these items used), then §9.6 packs the
        // lines from the cross-start edge with the gap between.
        XCTAssertEqual(bands[0].crossStart, 0, accuracy: eps)
        XCTAssertEqual(bands[0].crossEnd, 57.5, accuracy: eps)
        XCTAssertEqual(bands[1].crossStart, 62.5, accuracy: eps)
        XCTAssertEqual(bands[1].crossEnd, 120, accuracy: eps)
    }

    func testRedColumnRuleLandsOnTheRefBand045() {
        let segs = GapDecorationSegments.build(items: frames045, contentSize: size045,
                                               mainHorizontal: false, config: config045())
        // One between-line rule, spanning the content box along the main
        // (vertical) axis. Ref ink runs image x [76,81]; the band is
        // [57.5,62.5] in content space, i.e. image [75.5,80.5] with the
        // two half-covered edge columns reading as white.
        assertRects(segs.filter { !$0.isRowRule }.map(\.rect),
                    [CGRect(x: 57.5, y: 0, width: 5, height: 400)],
                    "045 column rule must sit in the LINE-BOX gap")
        // The defect this replaces: with the item-union extent the same
        // rule is centred in [50,63] → [54,59] → image x [72,77], which
        // is exactly the red run in the wave49-final iOS capture.
        let union = GapDecorationSegments.build(items: frames045, contentSize: size045,
                                                mainHorizontal: false,
                                                config: config045(columnGap: nil))
        assertRects(union.filter { !$0.isRowRule }.map(\.rect),
                    [CGRect(x: 54, y: 0, width: 5, height: 400)],
                    "the union model must still reproduce the shipped ink")
    }

    func testGoldRowRulesSpanTheStretchedLine045() {
        let segs = GapDecorationSegments.build(items: frames045, contentSize: size045,
                                               mainHorizontal: false, config: config045())
        let rows = segs.filter(\.isRowRule).map(\.rect)
        // Four gaps on line 1, three on line 2 → seven within-line rules.
        XCTAssertEqual(rows.count, 7)
        // Ref gold ink runs image x [18,76] on line 1 and [81,138] on
        // line 2 → content [0,57.5] and [62.5,120].
        for r in rows.prefix(4) {
            XCTAssertEqual(r.minX, 0, accuracy: eps)
            XCTAssertEqual(r.maxX, 57.5, accuracy: eps)
        }
        for r in rows.suffix(3) {
            XCTAssertEqual(r.minX, 62.5, accuracy: eps)
            XCTAssertEqual(r.maxX, 120, accuracy: eps)
        }
        // The first item gap is [70,83]; a 5pt rule centred there is
        // [74,79] → image y [92,97], the first gold run in the ref.
        XCTAssertEqual(rows[0].minY, 74, accuracy: eps)
        XCTAssertEqual(rows[0].maxY, 79, accuracy: eps)
    }

    // MARK: - WPT flex-gap-decorations-046

    // Row flex, 140×180 content box, row-gap 5, six 70×50 items → three
    // lines. Chromium and FlowLayout both place the lines at cross
    // 0 / 61.667 / 123.333 (the capture's pixel runs, image y [16,66]
    // [78,128] [139,189], are those frames rounded). The main axis is
    // HORIZONTAL, so the inter-line gaps carry the ROW rules (gold).
    private let size046 = CGSize(width: 140, height: 180)

    /// The captured line origins, written as the exact thirds SwiftUI and
    /// Chromium both produce: leftover 20 / 3 lines = 6.667 per line, so
    /// the lines start at 0, 56.667 + 5, 2·(56.667 + 5).
    private var frames046Exact: [CGRect] {
        let lineSize = (180.0 - 2.0 * 5.0) / 3.0          // 56.666…
        let starts = [0.0, lineSize + 5.0, 2.0 * (lineSize + 5.0)]
        return starts.flatMap { y in
            [CGRect(x: 0, y: y, width: 70, height: 50),
             CGRect(x: 70, y: y, width: 70, height: 50)]
        }
    }

    private func config046(rowGap: CGFloat? = 5) -> GapDecorationsConfig {
        var c = GapDecorationsConfig()
        c.row = solid(5)
        c.rowGapPx = rowGap
        c.columnGapPx = 0
        return c
    }

    func testGoldRowRulesMoveOntoTheLineBoxGaps046() {
        let segs = GapDecorationSegments.build(items: frames046Exact, contentSize: size046,
                                               mainHorizontal: true, config: config046())
        let lineSize = (180.0 - 2.0 * 5.0) / 3.0
        // Bands [0,56.667] [61.667,118.333] [123.333,180]; the two line
        // gaps are therefore [56.667,61.667] and [118.333,123.333], which
        // is image y [72.667,77.667] and [134.333,139.333] — the ref ink
        // runs are [73,78] and [134,139].
        assertRects(segs.map(\.rect),
                    [CGRect(x: 0, y: lineSize, width: 140, height: 5),
                     CGRect(x: 0, y: 2 * lineSize + 5, width: 140, height: 5)],
                    "046 row rules must sit in the LINE-BOX gaps")
    }

    func testUnionModelReproducesTheShippedInk046() {
        // Same inputs, cross gap withheld → the pre-wave-50 answer, which
        // must equal the wave49-final iOS capture: gold at image y
        // [70,74] and [131,136] → content [53.333,58.333] and [115,120].
        let segs = GapDecorationSegments.build(items: frames046Exact, contentSize: size046,
                                               mainHorizontal: true,
                                               config: config046(rowGap: nil))
        assertRects(segs.map(\.rect),
                    [CGRect(x: 0, y: 160.0 / 3.0, width: 140, height: 5),
                     CGRect(x: 0, y: 115, width: 140, height: 5)],
                    "the union model must still reproduce the shipped ink")
        // …and it must differ from the corrected answer, or this file
        // would be pinning a no-op.
        let fixed = GapDecorationSegments.build(items: frames046Exact, contentSize: size046,
                                                mainHorizontal: true, config: config046())
        XCTAssertNotEqual(segs.map(\.rect), fixed.map(\.rect))
    }

    // MARK: - the fallbacks

    func testUnresolvedCrossGapRefusesTheReconstruction() {
        let unions = GapDecorationLines.lines(from: frames046Exact, mainHorizontal: true)
        XCTAssertEqual(GapDecorationBands.resolve(lines: unions, contentSize: size046,
                                                  mainHorizontal: true, crossGapPx: nil,
                                                  alignContentStretches: true), unions)
    }

    func testPositioningAlignContentRefusesTheReconstruction() {
        // css-align-3 §5.1: `center`, `space-between`, … leave the lines
        // content-sized and place the line BLOCK, so the item union
        // already IS the line box.
        let unions = GapDecorationLines.lines(from: frames046Exact, mainHorizontal: true)
        XCTAssertEqual(GapDecorationBands.resolve(lines: unions, contentSize: size046,
                                                  mainHorizontal: true, crossGapPx: 5,
                                                  alignContentStretches: false), unions)
    }

    func testLinesThatAlreadyTileAreReturnedUntouched() {
        // The 003-family layout: 170pt content box, three 50pt lines,
        // row-gap 10 → leftover 0. This is the shape of every css-gaps
        // fixture that PASSES today, and it must not move.
        let frames = [
            CGRect(x: 0, y: 0, width: 50, height: 50),
            CGRect(x: 60, y: 0, width: 50, height: 50),
            CGRect(x: 0, y: 60, width: 100, height: 50),
            CGRect(x: 0, y: 120, width: 50, height: 50),
            CGRect(x: 60, y: 120, width: 50, height: 50),
        ]
        let unions = GapDecorationLines.lines(from: frames, mainHorizontal: true)
        XCTAssertEqual(GapDecorationBands.resolve(lines: unions,
                                                  contentSize: CGSize(width: 170, height: 170),
                                                  mainHorizontal: true, crossGapPx: 10,
                                                  alignContentStretches: true), unions)
    }

    func testBandsThatDisagreeWithPlacedItemsAreRejected() {
        // Lines packed at the cross-START edge of a definite 180pt box
        // with no stretch — the shape a `wrap-reverse` container still
        // takes on the frozen path. The reconstruction would claim
        // [0,56.667] [61.667,118.333] [123.333,180]; the third line's
        // items sit at [110,160], outside the band claimed for it.
        let frames = [0.0, 55.0, 110.0].flatMap { y in
            [CGRect(x: 0, y: y, width: 70, height: 50),
             CGRect(x: 70, y: y, width: 70, height: 50)]
        }
        let unions = GapDecorationLines.lines(from: frames, mainHorizontal: true)
        XCTAssertEqual(GapDecorationBands.resolve(lines: unions, contentSize: size046,
                                                  mainHorizontal: true, crossGapPx: 5,
                                                  alignContentStretches: true), unions)
    }

    func testSingleLineIsNeverRebuilt() {
        let frames = [CGRect(x: 0, y: 0, width: 70, height: 50),
                      CGRect(x: 75, y: 0, width: 70, height: 50)]
        let unions = GapDecorationLines.lines(from: frames, mainHorizontal: true)
        XCTAssertEqual(GapDecorationBands.resolve(lines: unions,
                                                  contentSize: CGSize(width: 145, height: 180),
                                                  mainHorizontal: true, crossGapPx: 5,
                                                  alignContentStretches: true), unions)
    }

    // MARK: - the wire

    func testExtractorReadsTheContainerGapsAndAlignContent() {
        // The live 046 container wire (wave49-final per-test IR):
        // RowGap {"type":"length","px":5}, no AlignContent → `normal`,
        // which stretches.
        let cfg = GapDecorationsExtractor.extract(from: [
            IRProperty(type: "RowRuleStyle", data: .string("SOLID")),
            IRProperty(type: "RowRuleWidth", data: .object(["type": .string("length"), "px": .double(5)])),
            IRProperty(type: "RowGap", data: .object(["type": .string("length"), "px": .double(5)])),
        ])
        XCTAssertEqual(cfg?.rowGapPx, 5)
        // Absent `column-gap` is the CSS initial `normal`, i.e. 0 on a
        // flex container — KNOWN, not unknown (css-align-3 §8).
        XCTAssertEqual(cfg?.columnGapPx, 0)
        XCTAssertEqual(cfg?.alignContentStretches, true)
        // A positioning keyword turns the reconstruction off.
        let centred = GapDecorationsExtractor.extract(from: [
            IRProperty(type: "RowRuleStyle", data: .string("SOLID")),
            IRProperty(type: "AlignContent", data: .string("CENTER")),
        ])
        XCTAssertEqual(centred?.alignContentStretches, false)
        // A container with NO gap-decoration property still returns nil —
        // reading RowGap must not make the family look declared.
        XCTAssertNil(GapDecorationsExtractor.extract(from: [
            IRProperty(type: "RowGap", data: .object(["type": .string("length"), "px": .double(5)])),
        ]))
    }
}
