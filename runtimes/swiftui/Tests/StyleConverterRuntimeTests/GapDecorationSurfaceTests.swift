//
//  GapDecorationSurfaceTests.swift
//  Wave 25, lane IGAPS — CAL-RC3 (the painter's paint SURFACE) and
//  CAL-RC8 (the dotted dot's measured geometry).
//
//  The wave-24 painter put its greedy Canvas in the container's CONTENT
//  box, so a segment outside that box survived only if it fell inside
//  SwiftUI's (undocumented) rasterization margin — 008's 30px overflow
//  did not, and two of its five rules were erased. These pins rasterize
//  the two WPT cases that produce out-of-box segments and assert the ink
//  is there; see each test for which one currently bites.
//
//  Coordinates are the WPT refs' own flexbox CONTENT-box space (ref page
//  coordinates minus the 2px border), the same space the painter works in.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class GapDecorationSurfaceTests: XCTestCase {

    // MARK: - Raster helpers

    /// Rasterize the painter the way GapDecorationsOverlay hosts it: the
    /// painter is offered exactly the container's CONTENT box (`content`)
    /// — that frame is what the overlay's GeometryReader supplies — while
    /// the white `stage` is larger, so ink that leaves the content box is
    /// visible instead of falling off the image.
    ///
    /// This is what makes the 008 pin bite: under the wave-24 painter the
    /// greedy Canvas took the whole `content` frame, and a rule 30px
    /// outside it never reached the raster.
    ///
    /// `origin` shifts the whole thing inside the stage so NEGATIVE
    /// segment coordinates (a negative `*-rule-inset`) land on pixels.
    @MainActor
    private func render(_ segs: [GapRuleSegment],
                        content: CGSize, stage: CGSize,
                        origin: CGPoint = .zero)
        throws -> (px: [UInt8], w: Int, h: Int) {
        let view = ZStack(alignment: .topLeading) {
            // Opaque stage first so "no ink" reads as white, not alpha.
            Color.white
            GapDecorationsPainter(segments: segs)
                .frame(width: content.width, height: content.height,
                       alignment: .topLeading)
                .offset(x: origin.x, y: origin.y)
        }
        .frame(width: stage.width, height: stage.height, alignment: .topLeading)
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

    /// Loose colour match — ±24 antialiasing tolerance, as in the other
    /// raster pins in this suite.
    private func matches(_ c: (r: UInt8, g: UInt8, b: UInt8),
                         _ t: (r: Int, g: Int, b: Int)) -> Bool {
        abs(Int(c.r) - t.r) <= 24 && abs(Int(c.g) - t.g) <= 24 && abs(Int(c.b) - t.b) <= 24
    }

    // SPEC sRGB primaries, not SwiftUI's system colours (which rasterize
    // as #FF383C / #0088FF) — same convention as GapDecorationRasterTests.
    private let pureRed = Color(.sRGB, red: 1, green: 0, blue: 0, opacity: 1)
    private let pureBlue = Color(.sRGB, red: 0, green: 0, blue: 1, opacity: 1)
    private let red = (r: 255, g: 0, b: 0)
    private let white = (r: 255, g: 255, b: 255)

    // MARK: - paintBounds (pure)

    /// The surface is the union of the ink-bearing segments, snapped
    /// outward to whole points. Degenerate (zero-area) segments carry no
    /// ink and must not drag the box out.
    func testPaintBoundsIsTheIntegralUnionOfInkedSegments() {
        let segs = [
            GapRuleSegment(rect: CGRect(x: 50, y: -2, width: 10, height: 54),
                           color: nil, style: .solid, isRowRule: false),
            GapRuleSegment(rect: CGRect(x: 290.5, y: 0, width: 10, height: 50),
                           color: nil, style: .solid, isRowRule: false),
            // Zero width — filtered by the painter, so it cannot widen
            // the stage even though its x sits far to the right.
            GapRuleSegment(rect: CGRect(x: 900, y: 0, width: 0, height: 50),
                           color: nil, style: .solid, isRowRule: false),
        ]
        let b = GapDecorationsPainter.paintBounds(segs)
        XCTAssertEqual(b, CGRect(x: 50, y: -2, width: 251, height: 54))
        // No segments at all → an empty stage, never a null rect (which
        // would make `.frame(width:height:)` NaN).
        XCTAssertEqual(GapDecorationsPainter.paintBounds([]), .zero)
    }

    // MARK: - ref 008 — rules beyond the container's content box

    /// flex-gap-decorations-008: a `width: 200px` NOWRAP container holds
    /// six 50px `flex-shrink: 0` items with a 10px column gap, so the
    /// line overflows to 350px and five column rules are painted — at
    /// content-x 50–60, 110–120, 170–180, 230–240 and 290–300 (the ref
    /// draws them at page x 52, 112, 172, 232 and 292, i.e. +2 for the
    /// border). The last TWO sit entirely outside x ≤ 200 and were
    /// clipped away by the content-box Canvas.
    @MainActor
    func testRulesBeyondTheContentBoxSurvive() throws {
        // The overflowing single line, exactly as the layout measures it.
        let frames = (0..<6).map {
            CGRect(x: CGFloat($0) * 60, y: 0, width: 50, height: 50)
        }
        var cfg = GapDecorationsConfig(touched: true)
        cfg.column = GapRuleSpec(color: pureRed, style: .solid, widthPx: 10)
        let segs = GapDecorationSegments.build(
            items: frames,
            // The CONTENT box is the declared 200px — the overflow is
            // exactly what makes this test interesting.
            contentSize: CGSize(width: 200, height: 50),
            mainHorizontal: true, config: cfg)
        XCTAssertEqual(segs.count, 5, "five item gaps ⇒ five column rules")
        // Stage wide enough to hold the whole overflowing line.
        let img = try render(segs,
                             content: CGSize(width: 200, height: 50),
                             stage: CGSize(width: 360, height: 50))
        // The three rules that were always inside the content box.
        for x in [55, 115, 175] {
            XCTAssertTrue(matches(rgb(img, x, 25), red), "rule at x=\(x) missing")
        }
        // THE REGRESSION: the two beyond x = 200, at both ends of their
        // bands and over the full cross extent of the line.
        for x in [231, 235, 239, 291, 295, 299] {
            XCTAssertTrue(matches(rgb(img, x, 25), red), "clipped rule at x=\(x)")
        }
        for y in [1, 25, 48] {
            XCTAssertTrue(matches(rgb(img, 235, y), red), "cross extent at y=\(y)")
        }
        // …and the item boxes between them stay bare.
        XCTAssertTrue(matches(rgb(img, 205, 25), white))
        XCTAssertTrue(matches(rgb(img, 265, 25), white))
    }

    // MARK: - ref 011 — negative-inset overhang at both outer ends

    /// flex-gap-decorations-011: `column-rule-inset: -2px` extends every
    /// 50px column run to 54px, so in the 170px-tall three-line box the
    /// topmost run starts at y = −2 and the bottom one ends at y = 172 —
    /// 4 outer ends × 2px × the 2px rule width = 16px² of ink OUTSIDE the
    /// content box.
    ///
    /// HONEST STATUS OF THIS PIN: unlike the 008 pin above, it does not
    /// bite today. The round's correction vector said these overhangs
    /// were trimmed; probing the pre-fix painter end-to-end through
    /// ComponentRenderer showed they were NOT (ink at stage y 18–71 /
    /// 78–131 for a content box at 20–130), because a 2px overhang falls
    /// inside SwiftUI's rasterization margin while 008's 30px one does
    /// not. The pin stays because that margin is undocumented: with the
    /// union surface the overhang is guaranteed rather than tolerated,
    /// and this asserts the guarantee.
    @MainActor
    func testNegativeInsetOverhangsSurviveAtBothOuterEnds() throws {
        // The ref's 3-line layout, content-box relative.
        let frames: [CGRect] = [
            CGRect(x: 0, y: 0, width: 50, height: 50),
            CGRect(x: 60, y: 0, width: 50, height: 50),
            CGRect(x: 120, y: 0, width: 50, height: 50),
            CGRect(x: 0, y: 60, width: 100, height: 50),
            CGRect(x: 110, y: 60, width: 50, height: 50),
            CGRect(x: 0, y: 120, width: 50, height: 50),
            CGRect(x: 60, y: 120, width: 50, height: 50),
            CGRect(x: 120, y: 120, width: 50, height: 50),
        ]
        var cfg = GapDecorationsConfig(touched: true)
        cfg.column = GapRuleSpec(color: pureRed, style: .solid, widthPx: 2,
                                 breakMode: .intersection, insetPx: -2)
        cfg.row = GapRuleSpec(color: pureBlue, style: .solid, widthPx: 2)
        let segs = GapDecorationSegments.build(
            items: frames, contentSize: CGSize(width: 170, height: 170),
            mainHorizontal: true, config: cfg)
        // Stage 4px taller with the painter pushed down 2px, so content
        // y = −2 lands on stage y = 0 and the y = 172 end is on-stage.
        let img = try render(segs,
                             content: CGSize(width: 170, height: 170),
                             stage: CGSize(width: 170, height: 174),
                             origin: CGPoint(x: 0, y: 2))
        // TOP overhang of the first line's rule at content x 54–56
        // (ref page x 56, per its `#columns1 { left: 56px }`): content
        // y −2…0 ⇒ stage y 0…2.
        XCTAssertTrue(matches(rgb(img, 55, 0), red), "top overhang row 0")
        XCTAssertTrue(matches(rgb(img, 55, 1), red), "top overhang row 1")
        // BOTTOM overhang of the last line's rule: content y 170…172 ⇒
        // stage y 172…174.
        XCTAssertTrue(matches(rgb(img, 55, 172), red), "bottom overhang row 172")
        XCTAssertTrue(matches(rgb(img, 55, 173), red), "bottom overhang row 173")
        // The same two ends on the SECOND column band (ref `left: 116px`
        // ⇒ content x 114–116), so the pin covers all four outer ends.
        XCTAssertTrue(matches(rgb(img, 115, 1), red), "band 2 top overhang")
        XCTAssertTrue(matches(rgb(img, 115, 173), red), "band 2 bottom overhang")
        // Off the bands the overhang rows are bare — the surface grew,
        // it did not start painting a slab.
        XCTAssertTrue(matches(rgb(img, 20, 1), white))
        XCTAssertTrue(matches(rgb(img, 20, 173), white))
    }

    // MARK: - CAL-RC8 — the dotted dot, as MEASURED in Chromium

    /// The wave-25 round asked for the dot to be shrunk to ~9.5px with a
    /// phase offset. Measurement says no: rendering the 005 ref's own
    /// `border-left: 10px dotted red` (50px run) and `border-bottom: 10px
    /// dotted blue` (170px run) in the capture-browser-ref recipe's
    /// Chromium at scale 1 gives a FULL diameter-10 circle (dot ink
    /// 77.0px², identical to a control 10×10 `border-radius: 50%` box
    /// measured with the same estimator), a first dot flush with the run
    /// start, and dot counts 3 and 9 — exactly what this painter draws.
    /// This pin locks that in so the claim cannot come back untested.
    @MainActor
    func testDottedRunMatchesTheMeasuredChromiumRhythm() throws {
        // The 005 band: one 50px vertical run, 10px wide, at x 50–60.
        let seg = GapRuleSegment(rect: CGRect(x: 50, y: 0, width: 10, height: 50),
                                 color: pureRed, style: .dotted, isRowRule: false)
        let img = try render([seg],
                             content: CGSize(width: 70, height: 50),
                             stage: CGSize(width: 70, height: 50))
        // Chromium's three dot centres on this run: y = 5, 25, 45.
        for y in [5, 25, 45] {
            XCTAssertTrue(matches(rgb(img, 55, y), red), "no dot centred at y=\(y)")
        }
        // The clear gaps between them.
        for y in [15, 35] {
            XCTAssertTrue(matches(rgb(img, 55, y), white), "gap at y=\(y) inked")
        }
        // FULL diameter: at the first dot's centre row the ink spans the
        // whole 10px band — a 9.5px dot would leave both edge columns
        // bare. Chromium's own raster has partial coverage at x = 50 and
        // x = 59 there, i.e. the circle reaches both.
        XCTAssertFalse(matches(rgb(img, 50, 5), white), "left edge column bare")
        XCTAssertFalse(matches(rgb(img, 59, 5), white), "right edge column bare")
        // FLUSH START: the first dot's leading edge is the run's start,
        // so y = 0 is inked at the band centre and there is no dot above.
        XCTAssertFalse(matches(rgb(img, 55, 0), white), "first dot not flush")
        // The count law: BorderSideApplier.dottedDotCount reproduces
        // Chromium's measured counts for the 005 band's two run lengths.
        XCTAssertEqual(BorderSideApplier.dottedDotCount(length: 50, width: 10), 3)
        XCTAssertEqual(BorderSideApplier.dottedDotCount(length: 170, width: 10), 9)
    }
}


