//
//  GapDecorationSegmentsTests.swift
//  Wave 24, lane GAPS-I — the segment tables for css-gaps-1 gap
//  decorations, pinned against FRESH renders of the WPT css-gaps flex
//  refs (tools/wpt/css/css-gaps/flex/flex-gap-decorations-0NN-ref.html
//  put through the capture-browser-ref.mjs recipe: same puppeteer
//  BROWSER_LAUNCH_ARGS, same injected canvas CSS). The committed
//  css-gaps refs are stale; every number below was re-measured.
//
//  COORDINATE SPACE. The refs place their decoration divs with
//  `position:absolute` against the initial containing block, so their
//  rects read directly in the flexbox's BORDER-box space. This module
//  works in CONTENT-box space, so each ref number below is quoted as
//  `refX − border` (border = 2px for 003…012, 0 for 001/002).
//
//  Test 006 is deliberately absent: `writing-mode: vertical-lr` is the
//  section wall and out of this lane's scope.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class GapDecorationSegmentsTests: XCTestCase {

    // MARK: - Shared fixtures

    /// The 8-item / 3-line layout shared by refs 003, 004, 005, 009,
    /// 010, 011 and 012 (170×170 content box; item #4 is 100 wide so
    /// line 2 has a single, OFFSET gap band at 100–110).
    /// Content-box frames = ref canvas rect − (16,16) canvas pad − (2,2)
    /// border: (18,18)→(0,0), (78,18)→(60,0), … (138,138)→(120,120).
    private let threeLineFrames: [CGRect] = [
        CGRect(x: 0, y: 0, width: 50, height: 50),
        CGRect(x: 60, y: 0, width: 50, height: 50),
        CGRect(x: 120, y: 0, width: 50, height: 50),
        CGRect(x: 0, y: 60, width: 100, height: 50),
        CGRect(x: 110, y: 60, width: 50, height: 50),
        CGRect(x: 0, y: 120, width: 50, height: 50),
        CGRect(x: 60, y: 120, width: 50, height: 50),
        CGRect(x: 120, y: 120, width: 50, height: 50),
    ]
    /// That layout's content box.
    private let threeLineSize = CGSize(width: 170, height: 170)

    /// Red column rule / blue row rule, both `solid`, both `width` px.
    private func redBlue(width: CGFloat,
                         columnBreak: GapRuleBreak = .spanningItem,
                         rowBreak: GapRuleBreak = .spanningItem,
                         columnInset: CGFloat = 0,
                         rowInset: CGFloat = 0,
                         overlap: GapRuleOverlap = .rowOverColumn) -> GapDecorationsConfig {
        GapDecorationsConfig(
            column: GapRuleSpec(color: .red, style: .solid, widthPx: width,
                                breakMode: columnBreak, insetPx: columnInset),
            row: GapRuleSpec(color: .blue, style: .solid, widthPx: width,
                             breakMode: rowBreak, insetPx: rowInset),
            overlap: overlap, touched: true)
    }

    /// Rect list of the segments of one family, for table comparison.
    private func rects(_ segs: [GapRuleSegment], rowRule: Bool) -> [CGRect] {
        segs.filter { $0.isRowRule == rowRule }.map(\.rect)
    }

    // MARK: - the interval algebra (GapDecorationGeometry)

    /// union merges overlapping AND touching intervals — ref 009's two
    /// cuts at 102–112 and 112–122 must fuse into one 102–122 hole.
    func testIntervalUnionFusesTouchingCuts() {
        XCTAssertEqual(GapIntervals.union([GapSpan(start: 112, end: 122),
                                           GapSpan(start: 102, end: 112),
                                           GapSpan(start: 50, end: 60)]),
                       [GapSpan(start: 50, end: 60), GapSpan(start: 102, end: 122)])
        // Degenerate intervals are dropped, not fused.
        XCTAssertEqual(GapIntervals.union([GapSpan(start: 5, end: 5)]), [])
    }

    /// subtract carves every cut out, left to right, dropping the pieces
    /// that collapse.
    func testIntervalSubtractProducesTheSurvivingRuns() {
        XCTAssertEqual(
            GapIntervals.subtract(base: GapSpan(start: 0, end: 170),
                                  cuts: [GapSpan(start: 50, end: 60),
                                         GapSpan(start: 110, end: 120),
                                         GapSpan(start: 100, end: 110)]),
            [GapSpan(start: 0, end: 50), GapSpan(start: 60, end: 100),
             GapSpan(start: 120, end: 170)])
        // A cut covering everything leaves nothing.
        XCTAssertEqual(GapIntervals.subtract(base: GapSpan(start: 0, end: 10),
                                             cuts: [GapSpan(start: -5, end: 15)]), [])
    }

    /// band centres the rule; inset moves both ends and returns nil when
    /// a positive inset collapses the run.
    func testBandCentresAndInsetCanCollapse() {
        XCTAssertEqual(GapIntervals.band(gap: GapSpan(start: 50, end: 60), widthPx: 2),
                       GapSpan(start: 54, end: 56))
        XCTAssertEqual(GapIntervals.inset(GapSpan(start: 0, end: 50), insetPx: -2),
                       GapSpan(start: -2, end: 52))
        XCTAssertNil(GapIntervals.inset(GapSpan(start: 0, end: 10), insetPx: 5))
    }

    /// betweenLineGaps reports the cross bands the row rules live in.
    func testBetweenLineGapsReportsTheRowBands() {
        let lines = GapDecorationLines.lines(from: threeLineFrames, mainHorizontal: true)
        XCTAssertEqual(GapDecorationLines.betweenLineGaps(lines),
                       [GapSpan(start: 50, end: 60), GapSpan(start: 110, end: 120)])
    }

    // MARK: - (a) line grouping

    /// Items group into three lines; a line's cross extent is the UNION
    /// of its items (ref 007's align-items:flex-end line is 40 tall even
    /// though two of its three items are 18 tall).
    func testLineGroupingUnionsCrossExtents() {
        let lines = GapDecorationLines.lines(from: threeLineFrames, mainHorizontal: true)
        XCTAssertEqual(lines.count, 3)
        XCTAssertEqual(lines[1].crossStart, 60)
        XCTAssertEqual(lines[1].crossEnd, 110)
        // Line 2's single gap band sits at 100–110, NOT at 50–60 like the
        // other two lines — the whole point of ref 003's #four item.
        XCTAssertEqual(lines[1].gapBands, [GapSpan(start: 100, end: 110)])
        XCTAssertEqual(lines[0].gapBands,
                       [GapSpan(start: 50, end: 60), GapSpan(start: 110, end: 120)])
    }

    /// ref 007 (align-items: flex-end, one line, 170×40 content box):
    /// two column rules spanning the FULL line height.
    /// Ref canvas [52,2,10,40] / [112,2,10,40] − border ⇒ (50,0,10,40).
    func testAlignItemsFlexEndRuleSpansWholeLine() {
        let frames = [CGRect(x: 0, y: 22, width: 50, height: 18),
                      CGRect(x: 60, y: 22, width: 50, height: 18),
                      CGRect(x: 120, y: 0, width: 50, height: 40)]
        var cfg = redBlue(width: 10)
        cfg.row = GapRuleSpec()   // ref 007 declares no row rule at all
        let segs = GapDecorationSegments.build(
            items: frames, contentSize: CGSize(width: 170, height: 40),
            mainHorizontal: true, config: cfg)
        XCTAssertEqual(rects(segs, rowRule: false),
                       [CGRect(x: 50, y: 0, width: 10, height: 40),
                        CGRect(x: 110, y: 0, width: 10, height: 40)])
        XCTAssertTrue(rects(segs, rowRule: true).isEmpty)
    }

    /// ref 008 (flex-wrap: nowrap, six 50px items overflowing a 200px
    /// content box): ONE line, five column rules, the last three painted
    /// past the container's right edge.
    /// Ref canvas x 52/112/172/232/292 − border ⇒ 50/110/170/230/290.
    func testNowrapOverflowKeepsOneLineAndPaintsEveryGap() {
        let frames = (0..<6).map {
            CGRect(x: CGFloat($0) * 60, y: 0, width: 50, height: 50)
        }
        var cfg = redBlue(width: 10)
        cfg.row = GapRuleSpec()   // ref 008 declares no row rule
        let segs = GapDecorationSegments.build(
            items: frames, contentSize: CGSize(width: 200, height: 50),
            mainHorizontal: true, config: cfg)
        XCTAssertEqual(rects(segs, rowRule: false).map(\.minX),
                       [50, 110, 170, 230, 290])
        // Every run spans the single line's full 50px cross extent.
        XCTAssertEqual(rects(segs, rowRule: false).map(\.height),
                       Array(repeating: CGFloat(50), count: 5))
    }

    // MARK: - (d) breaks — an item-gap rule STOPS at every line edge

    /// ref 002 (2×2, 110×110). The agnostic ref this test matches draws
    /// its column-gap div `height: 110px`, i.e. spanning the whole
    /// container — but that markup is UNOBSERVABLE, because the
    /// full-width row rule repaints the crossing square on top of it
    /// (the same trap as ref 003's 55px bleed). Chrome 150 settles it:
    /// narrow both rules to 2px so neither masks the other and scan the
    /// column band down through the row gap —
    ///   y49 red · y50–53 WHITE · y54–55 blue · y56–59 WHITE · y60 red
    /// — so the column rule really is TWO 50-tall runs.
    /// (Probe: tools/visual/_skeptic/probe-scan2.mjs.)
    func testItemGapRuleStopsAtEveryLineEdge() {
        let frames = [CGRect(x: 0, y: 0, width: 50, height: 50),
                      CGRect(x: 60, y: 0, width: 50, height: 50),
                      CGRect(x: 0, y: 60, width: 50, height: 50),
                      CGRect(x: 60, y: 60, width: 50, height: 50)]
        let segs = GapDecorationSegments.build(
            items: frames, contentSize: CGSize(width: 110, height: 110),
            mainHorizontal: true, config: redBlue(width: 10))
        XCTAssertEqual(rects(segs, rowRule: false),
                       [CGRect(x: 50, y: 0, width: 10, height: 50),
                        CGRect(x: 50, y: 60, width: 10, height: 50)])
        // The LINE-gap family does span the whole content main extent —
        // the same probe's horizontal scan at y54 is blue from x46 to
        // x64, straight through the column band, uncut.
        XCTAssertEqual(rects(segs, rowRule: true),
                       [CGRect(x: 0, y: 50, width: 110, height: 10)])
    }

    /// ref 012 (rule-overlap: column-over-row makes the column rule's
    /// ends VISIBLE): a spanning item severs the rule AT THE LINE EDGE —
    /// no bleed into the row gap. Ref canvas [56,2,2,50] / [56,122,2,50]
    /// / [106,62,2,50] − border ⇒ (54,0,2,50) / (54,120,2,50) /
    /// (104,60,2,50). Row rules: [2,56,170,2] ⇒ (0,54,170,2).
    func testSpanningItemBreakStopsAtLineEdgeAndColumnOverRowPaintsLast() {
        let cfg = redBlue(width: 2, overlap: .columnOverRow)
        let segs = GapDecorationSegments.build(
            items: threeLineFrames, contentSize: threeLineSize,
            mainHorizontal: true, config: cfg)
        XCTAssertEqual(rects(segs, rowRule: false),
                       [CGRect(x: 54, y: 0, width: 2, height: 50),
                        CGRect(x: 114, y: 0, width: 2, height: 50),
                        CGRect(x: 104, y: 60, width: 2, height: 50),
                        CGRect(x: 54, y: 120, width: 2, height: 50),
                        CGRect(x: 114, y: 120, width: 2, height: 50)])
        XCTAssertEqual(rects(segs, rowRule: true),
                       [CGRect(x: 0, y: 54, width: 170, height: 2),
                        CGRect(x: 0, y: 114, width: 170, height: 2)])
        // column-over-row ⇒ the LAST segment painted is a column rule.
        XCTAssertEqual(segs.last?.isRowRule, false)
        // …and the default order (ref 003) is the other way round.
        let dflt = GapDecorationSegments.build(
            items: threeLineFrames, contentSize: threeLineSize,
            mainHorizontal: true, config: redBlue(width: 2))
        XCTAssertEqual(dflt.last?.isRowRule, true)
    }

    /// ref 009 (`row-rule-break: intersection` + `column-rule-break:
    /// intersection`, both insets 0): the row rule is cut over the union
    /// of BOTH neighbouring lines' gap bands. Ref row children
    /// [2,52,50,10] / [62,52,40,10] / [122,52,50,10] − border ⇒ x 0–50,
    /// 60–100, 120–170. Column rules keep their per-line 50px runs.
    func testIntersectionBreakCutsRowRuleOverBothNeighbourBands() {
        let cfg = redBlue(width: 10, columnBreak: .intersection,
                          rowBreak: .intersection)
        let segs = GapDecorationSegments.build(
            items: threeLineFrames, contentSize: threeLineSize,
            mainHorizontal: true, config: cfg)
        XCTAssertEqual(rects(segs, rowRule: true),
                       [CGRect(x: 0, y: 50, width: 50, height: 10),
                        CGRect(x: 60, y: 50, width: 40, height: 10),
                        CGRect(x: 120, y: 50, width: 50, height: 10),
                        CGRect(x: 0, y: 110, width: 50, height: 10),
                        CGRect(x: 60, y: 110, width: 40, height: 10),
                        CGRect(x: 120, y: 110, width: 50, height: 10)])
        XCTAssertEqual(rects(segs, rowRule: false),
                       [CGRect(x: 50, y: 0, width: 10, height: 50),
                        CGRect(x: 110, y: 0, width: 10, height: 50),
                        CGRect(x: 100, y: 60, width: 10, height: 50),
                        CGRect(x: 50, y: 120, width: 10, height: 50),
                        CGRect(x: 110, y: 120, width: 10, height: 50)])
    }

    /// ref 010 (column break intersection, row break DEFAULT): the row
    /// rule is a single uncut 170px band per gap — the only difference
    /// from 009, and the pin that proves `spanning-item` does not cut a
    /// row rule at crossings. Ref row-gap divs [2,52,170,10] ⇒ full width.
    func testDefaultRowBreakLeavesRowRuleUncut() {
        let cfg = redBlue(width: 10, columnBreak: .intersection)
        let segs = GapDecorationSegments.build(
            items: threeLineFrames, contentSize: threeLineSize,
            mainHorizontal: true, config: cfg)
        XCTAssertEqual(rects(segs, rowRule: true),
                       [CGRect(x: 0, y: 50, width: 170, height: 10),
                        CGRect(x: 0, y: 110, width: 170, height: 10)])
    }

    // MARK: - (e) insets

    /// ref 011 (`column-rule-inset: -2px`, 2px rules, column break
    /// intersection): every 50px column run grows to 54px, overshooting
    /// the line box by 2px at BOTH ends — the first one starts at −2,
    /// i.e. inside the container's border band. Ref canvas [56,0,2,54] /
    /// [106,60,2,54] / [56,120,2,54] − border ⇒ (54,−2,2,54) /
    /// (104,58,2,54) / (54,118,2,54). Row rules keep inset 0.
    func testNegativeInsetExtendsColumnRuleBeyondTheLineBox() {
        let cfg = redBlue(width: 2, columnBreak: .intersection, columnInset: -2)
        let segs = GapDecorationSegments.build(
            items: threeLineFrames, contentSize: threeLineSize,
            mainHorizontal: true, config: cfg)
        XCTAssertEqual(rects(segs, rowRule: false),
                       [CGRect(x: 54, y: -2, width: 2, height: 54),
                        CGRect(x: 114, y: -2, width: 2, height: 54),
                        CGRect(x: 104, y: 58, width: 2, height: 54),
                        CGRect(x: 54, y: 118, width: 2, height: 54),
                        CGRect(x: 114, y: 118, width: 2, height: 54)])
        XCTAssertEqual(rects(segs, rowRule: true),
                       [CGRect(x: 0, y: 54, width: 170, height: 2),
                        CGRect(x: 0, y: 114, width: 170, height: 2)])
    }

    /// A POSITIVE inset shortens both ends — the same arithmetic with the
    /// sign flipped (no WPT flex test uses one; pinned so the sign
    /// convention cannot drift).
    func testPositiveInsetShortensBothEnds() {
        let cfg = redBlue(width: 10, columnBreak: .intersection, columnInset: 5)
        let segs = GapDecorationSegments.build(
            items: threeLineFrames, contentSize: threeLineSize,
            mainHorizontal: true, config: cfg)
        XCTAssertEqual(rects(segs, rowRule: false).first,
                       CGRect(x: 50, y: 5, width: 10, height: 40))
    }

    // MARK: - axis swap + inert cases

    /// In a COLUMN-direction container the item gaps are the ROW gaps, so
    /// `row-rule-*` paints between items and `column-rule-*` between
    /// lines. Un-pinned by the corpus (all 12 flex tests are row
    /// direction) — this pins the swap itself.
    func testColumnDirectionSwapsWhichFamilyOwnsWhichGap() {
        // Two columns of two items: main = vertical.
        let frames = [CGRect(x: 0, y: 0, width: 50, height: 50),
                      CGRect(x: 0, y: 60, width: 50, height: 50),
                      CGRect(x: 60, y: 0, width: 50, height: 50),
                      CGRect(x: 60, y: 60, width: 50, height: 50)]
        let segs = GapDecorationSegments.build(
            items: frames, contentSize: CGSize(width: 110, height: 110),
            mainHorizontal: false, config: redBlue(width: 10))
        // The ROW rule is now the ITEM-gap family (main is vertical), so
        // it stops at each line edge: two 50-wide runs with a hole over
        // the column gap. This is EXACTLY the structure
        // flex-gap-decorations-015-ref draws for the column-direction
        // case — its `.row-1`/`.row-2` are flex rows of two 50px
        // `.row-gap` divs separated by a 20px column-gap — which is
        // independent corroboration of the per-line model above.
        XCTAssertEqual(rects(segs, rowRule: true),
                       [CGRect(x: 0, y: 50, width: 50, height: 10),
                        CGRect(x: 60, y: 50, width: 50, height: 10)])
        // The COLUMN rule is the vertical band between the two lines.
        XCTAssertEqual(rects(segs, rowRule: false),
                       [CGRect(x: 50, y: 0, width: 10, height: 110)])
    }

    /// Every inert shape produces zero segments — the property that makes
    /// wiring this family into the flex container ungated-safe.
    func testInertConfigurationsPaintNothing() {
        let frames = threeLineFrames
        // No style at all.
        XCTAssertTrue(GapDecorationSegments.build(
            items: frames, contentSize: threeLineSize, mainHorizontal: true,
            config: GapDecorationsConfig()).isEmpty)
        // `none` style with a width.
        var noneStyle = GapDecorationsConfig()
        noneStyle.column = GapRuleSpec(color: .red, style: BorderStyleValue.none, widthPx: 10)
        XCTAssertTrue(GapDecorationSegments.build(
            items: frames, contentSize: threeLineSize, mainHorizontal: true,
            config: noneStyle).isEmpty)
        // Zero width with a real style.
        var zeroWidth = GapDecorationsConfig()
        zeroWidth.column = GapRuleSpec(color: .red, style: .solid, widthPx: 0)
        XCTAssertTrue(GapDecorationSegments.build(
            items: frames, contentSize: threeLineSize, mainHorizontal: true,
            config: zeroWidth).isEmpty)
        // Active config but no measured items yet (first layout pass).
        XCTAssertTrue(GapDecorationSegments.build(
            items: [], contentSize: threeLineSize, mainHorizontal: true,
            config: redBlue(width: 10)).isEmpty)
    }
}
