//
//  GapDecorationRasterTests.swift
//  Wave 24, lane GAPS-I — ImageRenderer raster pins for the gap-
//  decorations painter. The segment tables are pinned arithmetically in
//  GapDecorationSegmentsTests; this file proves the INK lands where
//  those rects say, in the paint order rule-overlap demands.
//
//  Every probe coordinate is quoted in the WPT ref's own flexbox
//  CONTENT-box space (fresh renders of
//  tools/wpt/css/css-gaps/flex/flex-gap-decorations-0NN-ref.html through
//  the capture-browser-ref.mjs recipe), which is exactly the painter's
//  coordinate space, so probes read as ref coordinates minus the 2px
//  border.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class GapDecorationRasterTests: XCTestCase {

    // MARK: - Raster helpers

    /// Rasterize the painter over a white stage of `size`.
    @MainActor
    private func render(_ segs: [GapRuleSegment], size: CGSize)
        throws -> (px: [UInt8], w: Int, h: Int) {
        let view = GapDecorationsPainter(segments: segs)
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .background(Color.white)
        // Scale 1 = one buffer pixel per logical point, like the harness.
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        // Redraw into a known RGBA8 layout for format-stable byte reads.
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        return (buf, w, h)
    }

    /// RGB triple at a raster point.
    private func rgb(_ img: (px: [UInt8], w: Int, h: Int),
                     _ x: Int, _ y: Int) -> (r: UInt8, g: UInt8, b: UInt8) {
        let i = (y * img.w + x) * 4
        return (img.px[i], img.px[i + 1], img.px[i + 2])
    }

    /// Loose colour match — antialiasing tolerance of ±24, same as the
    /// other raster pins in this suite.
    private func matches(_ c: (r: UInt8, g: UInt8, b: UInt8),
                         _ t: (r: Int, g: Int, b: Int)) -> Bool {
        abs(Int(c.r) - t.r) <= 24 && abs(Int(c.g) - t.g) <= 24 && abs(Int(c.b) - t.b) <= 24
    }

    // SPEC sRGB primaries, NOT SwiftUI's `Color.red`/`Color.blue` — the
    // system colours rasterize as #FF383C / #0088FF, so the configs below
    // build their colours the way ValueExtractors.extractColor does
    // (Color(.sRGB, …) straight off the IR's srgb floats).
    private let pureRed = Color(.sRGB, red: 1, green: 0, blue: 0, opacity: 1)
    private let pureBlue = Color(.sRGB, red: 0, green: 0, blue: 1, opacity: 1)
    private let red = (r: 255, g: 0, b: 0)
    private let blue = (r: 0, g: 0, b: 255)
    private let white = (r: 255, g: 255, b: 255)

    /// The ref-003/009/010/011/012 layout, content-box relative.
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
    private let threeLineSize = CGSize(width: 170, height: 170)

    /// Red column / blue row rules of `width`, both solid.
    private func redBlue(_ width: CGFloat, style: BorderStyleValue = .solid,
                         overlap: GapRuleOverlap = .rowOverColumn) -> GapDecorationsConfig {
        GapDecorationsConfig(
            column: GapRuleSpec(color: pureRed, style: style, widthPx: width),
            row: GapRuleSpec(color: pureBlue, style: style, widthPx: width),
            overlap: overlap, touched: true)
    }

    // MARK: - ref 003 — solid rules, row-over-column

    /// The ref's own column divs are 55px tall (they bleed 5px into the
    /// row band); ours are 50. The two are PIXEL-IDENTICAL because the
    /// full-width 10px blue row rule paints over that band last under the
    /// initial `rule-overlap: row-over-column` — which is exactly what
    /// this raster pin asserts.
    @MainActor
    func testSolidRulesPaintAndRowWinsTheCrossing() throws {
        let segs = GapDecorationSegments.build(
            items: threeLineFrames, contentSize: threeLineSize,
            mainHorizontal: true, config: redBlue(10))
        let img = try render(segs, size: threeLineSize)
        // Column rule inside line 1 (ref x52–62 / y2–52 ⇒ 50–60 / 0–50).
        XCTAssertTrue(matches(rgb(img, 55, 25), red))
        // Line 2's OFFSET column rule (ref x102–112 / y62–112).
        XCTAssertTrue(matches(rgb(img, 105, 85), red))
        // …and nothing at x55 on line 2 — item #four spans that band.
        XCTAssertTrue(matches(rgb(img, 55, 85), white))
        // Row rule across the whole content width (ref y52–62).
        XCTAssertTrue(matches(rgb(img, 5, 55), blue))
        XCTAssertTrue(matches(rgb(img, 165, 55), blue))
        // THE CROSSING: blue over red at x55/y55.
        XCTAssertTrue(matches(rgb(img, 55, 55), blue))
    }

    // MARK: - ref 012 — column-over-row inverts the crossing

    @MainActor
    func testColumnOverRowInvertsTheCrossing() throws {
        let segs = GapDecorationSegments.build(
            items: threeLineFrames, contentSize: threeLineSize,
            mainHorizontal: true, config: redBlue(2, overlap: .columnOverRow))
        let img = try render(segs, size: threeLineSize)
        // 2px rules: column at x54–56, row at y54–56 (ref 56/56 − border).
        XCTAssertTrue(matches(rgb(img, 55, 25), red))
        XCTAssertTrue(matches(rgb(img, 5, 55), blue))
        // In THIS layout the two families never actually overlap: every
        // column gap band is severed by a spanning item on the next line,
        // so the row band at y54–56 is pure blue even at x55 — exactly
        // what ref 012's flat full-width row div renders. (The paint
        // ORDER is pinned where an overlap really exists, in
        // testMergedColumnRuleWinsTheCrossingUnderColumnOverRow below.)
        XCTAssertTrue(matches(rgb(img, 55, 55), blue))
        // …and the column rule STOPS at the line edge — y52 is inside the
        // row gap but above the row rule, and must be bare white (this is
        // the pixel that proves spanning-item does not bleed).
        XCTAssertTrue(matches(rgb(img, 55, 52), white))
    }

    // MARK: - ref 009 — intersection cuts the row rule

    @MainActor
    func testIntersectionBreakLeavesTheCrossingsBare() throws {
        var cfg = redBlue(10)
        cfg.column.breakMode = .intersection
        cfg.row.breakMode = .intersection
        let segs = GapDecorationSegments.build(
            items: threeLineFrames, contentSize: threeLineSize,
            mainHorizontal: true, config: cfg)
        let img = try render(segs, size: threeLineSize)
        // Row rule survives between the cuts (ref run x62–102 ⇒ 60–100).
        XCTAssertTrue(matches(rgb(img, 80, 55), blue))
        XCTAssertTrue(matches(rgb(img, 25, 55), blue))
        XCTAssertTrue(matches(rgb(img, 145, 55), blue))
        // Cut over line 1's band (ref 52–62 ⇒ 50–60). The column rule
        // ALSO stops at the line edge under intersection, so this pixel
        // is bare stage — matching ref 009, whose row children leave
        // 52–62 (canvas) empty and whose column divs are only 50 tall.
        XCTAssertTrue(matches(rgb(img, 55, 55), white))
        // Cut over line 2's OFFSET band (ref 102–112 ⇒ 100–110) — the
        // cut comes from the line BELOW, which is the whole point of
        // taking the union of both neighbours' bands.
        XCTAssertTrue(matches(rgb(img, 105, 55), white))
        // …and 110–120, cut by line 1's second band. Together these pin
        // that the cut follows the GAP, not the (here equal) rule width.
        XCTAssertTrue(matches(rgb(img, 115, 55), white))
    }

    // MARK: - ref 011 — negative inset overshoots the line box

    @MainActor
    func testNegativeInsetPaintsAboveTheFirstLine() throws {
        var cfg = redBlue(2)
        cfg.column.breakMode = .intersection
        cfg.column.insetPx = -2
        let segs = GapDecorationSegments.build(
            items: threeLineFrames, contentSize: threeLineSize,
            mainHorizontal: true, config: cfg)
        // Stage 4px taller and shifted so the y = −2 overshoot is visible.
        let img = try render(segs.map {
            var s = $0; s.rect = s.rect.offsetBy(dx: 0, dy: 4); return s
        }, size: CGSize(width: 170, height: 178))
        // y = −2 + 4 = 2 on the stage: ink ABOVE the first line box.
        XCTAssertTrue(matches(rgb(img, 55, 2), red))
        // …and one point higher (y = −3) is past the run's end.
        XCTAssertTrue(matches(rgb(img, 55, 0), white))
    }

    // MARK: - ref 002 — the item-gap rule is CUT at every row gap

    /// Raster counterpart of the Chrome 150 scan
    /// (tools/visual/_skeptic/probe-scan2.mjs): with `column-over-row`
    /// so nothing can mask the column band, the row gap must be BARE
    /// between the two 50-tall column runs.
    @MainActor
    func testItemGapRuleIsBareInsideTheRowGap() throws {
        let frames = [CGRect(x: 0, y: 0, width: 50, height: 50),
                      CGRect(x: 60, y: 0, width: 50, height: 50),
                      CGRect(x: 0, y: 60, width: 50, height: 50),
                      CGRect(x: 60, y: 60, width: 50, height: 50)]
        // 2px rules so the row rule (y54–56) cannot cover the rest of
        // the row gap — exactly the browser probe's setup.
        var cfg = redBlue(2)
        cfg.overlap = .columnOverRow
        let segs = GapDecorationSegments.build(
            items: frames, contentSize: CGSize(width: 110, height: 110),
            mainHorizontal: true, config: cfg)
        let img = try render(segs, size: CGSize(width: 110, height: 110))
        // Inside each line box: the column rule paints (x54–56).
        XCTAssertTrue(matches(rgb(img, 55, 5), red))
        XCTAssertTrue(matches(rgb(img, 55, 105), red))
        // Inside the row gap, off the row rule's own 2px band: BARE.
        // Chrome's y50–53 / y56–59 white run is the pin.
        XCTAssertTrue(matches(rgb(img, 55, 51), white))
        XCTAssertTrue(matches(rgb(img, 55, 58), white))
        // …and the row rule's own band is blue there, not red — the
        // column rule contributes nothing inside the row gap, so
        // column-over-row has nothing to win with.
        XCTAssertTrue(matches(rgb(img, 55, 55), blue))
    }

    /// The paint-ORDER pin. Because an item-gap rule stops at the line
    /// edge, the two families do NOT touch at all in a plain 2×2 layout
    /// — so `rule-overlap` is a no-op there, and the ONLY way to make a
    /// real crossing in flex is a NEGATIVE inset that pushes the column
    /// runs into the row gap (ref 011's mechanism). Both halves are
    /// pinned: the no-op, then the flip.
    @MainActor
    func testRuleOverlapIsInertUntilANegativeInsetCreatesACrossing() throws {
        let frames = [CGRect(x: 0, y: 0, width: 50, height: 50),
                      CGRect(x: 60, y: 0, width: 50, height: 50),
                      CGRect(x: 0, y: 60, width: 50, height: 50),
                      CGRect(x: 60, y: 60, width: 50, height: 50)]
        let stage = CGSize(width: 110, height: 110)
        // (1) No inset ⇒ no crossing ⇒ the keyword changes NOTHING.
        var rowFirst = redBlue(10)
        rowFirst.overlap = .rowOverColumn
        var colFirst = redBlue(10)
        colFirst.overlap = .columnOverRow
        let a = try render(GapDecorationSegments.build(
            items: frames, contentSize: stage,
            mainHorizontal: true, config: rowFirst), size: stage)
        let b = try render(GapDecorationSegments.build(
            items: frames, contentSize: stage,
            mainHorizontal: true, config: colFirst), size: stage)
        XCTAssertEqual(a.px, b.px,
                       "with no crossing, rule-overlap must be a no-op")
        // The row gap belongs to the row rule under either keyword.
        XCTAssertTrue(matches(rgb(a, 55, 55), blue))
        XCTAssertTrue(matches(rgb(b, 55, 55), blue))
        // (2) column-rule-inset: -2px extends each 50-tall column run to
        // y −2…52 / 58…112, so y50…52 now carries BOTH families.
        var rowWins = redBlue(10)
        rowWins.column.insetPx = -2
        rowWins.overlap = .rowOverColumn
        var colWins = rowWins
        colWins.overlap = .columnOverRow
        let c = try render(GapDecorationSegments.build(
            items: frames, contentSize: stage,
            mainHorizontal: true, config: rowWins), size: stage)
        let d = try render(GapDecorationSegments.build(
            items: frames, contentSize: stage,
            mainHorizontal: true, config: colWins), size: stage)
        XCTAssertTrue(matches(rgb(c, 55, 51), blue), "row-over-column")
        XCTAssertTrue(matches(rgb(d, 55, 51), red), "column-over-row")
        // Outside the 2px overshoot the two agree again.
        XCTAssertTrue(matches(rgb(c, 55, 55), blue))
        XCTAssertTrue(matches(rgb(d, 55, 55), blue))
    }

    // MARK: - ref 004 / 005 — double and dotted strokes

    /// `double`: three equal bands across the 10px rule, outer two inked.
    /// The ref draws it as `border-left: 10px double red`, so parity with
    /// BorderSideApplier's split IS parity with the ref.
    @MainActor
    func testDoubleStrokeLeavesTheMiddleThirdBare() throws {
        var cfg = redBlue(10, style: .double)
        cfg.row = GapRuleSpec()   // isolate the column family
        let segs = GapDecorationSegments.build(
            items: threeLineFrames, contentSize: threeLineSize,
            mainHorizontal: true, config: cfg)
        let img = try render(segs, size: threeLineSize)
        // Band 50.0–53.3 inked, 53.3–56.6 bare, 56.6–60.0 inked.
        XCTAssertTrue(matches(rgb(img, 51, 25), red))
        XCTAssertTrue(matches(rgb(img, 55, 25), white))
        XCTAssertTrue(matches(rgb(img, 58, 25), red))
    }

    /// `dotted`: spaced circles down the rule, so the run alternates ink
    /// and bare stage along its length (a solid rule never would).
    @MainActor
    func testDottedStrokeAlternatesAlongTheRun() throws {
        var cfg = redBlue(10, style: .dotted)
        cfg.row = GapRuleSpec()
        let segs = GapDecorationSegments.build(
            items: threeLineFrames, contentSize: threeLineSize,
            mainHorizontal: true, config: cfg)
        let img = try render(segs, size: threeLineSize)
        // Scan the first 50px run at the rule's centre line and require
        // BOTH inked and bare samples — the dot rhythm itself is pinned
        // by BorderSideApplier.dottedDotCount's own tests.
        var inked = 0, bare = 0
        for y in 0..<50 {
            if matches(rgb(img, 55, y), red) { inked += 1 } else { bare += 1 }
        }
        XCTAssertGreaterThan(inked, 0, "dotted rule painted nothing")
        XCTAssertGreaterThan(bare, 0, "dotted rule painted a solid line")
    }
}
