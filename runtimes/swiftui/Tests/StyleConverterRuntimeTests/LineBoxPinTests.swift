//
//  LineBoxPinTests.swift
//  Applier campaign wave 30, lane LINEBOX (iOS).
//
//  WHAT THIS PINS — that a composed-WPT text box advances by its DECLARED
//  line box, never by the rendered face's natural metrics.
//
//  ## The measurement that opened the lane
//  On the composed WPT canvas a `<p>` root occupies `box + the UA 16px
//  collapsed block margin` (UABlockMargin). Chromium, the web harness and
//  Compose all advance EXACTLY 36.00px per default-font paragraph
//  (20px line box = 16 × the ref's unitless 1.25, + 16px margin). iOS
//  advanced 36.64px — measured on the live device capture of
//  selectors/child-indexed-no-parent, whose 9 stacked paragraphs land the
//  9th ink row 5px low (band tops 36, 73, 110, 146, 183, 220, 256, 293, 329
//  against the ref's 36, 72, 108, …, 324). That accumulation IS iOS's
//  0.8985 iOS-web residual on that test.
//
//  ## The arithmetic (Inter's own numbers, all exact binary fractions)
//      content area   = (hheaAsc 1984 + |hheaDesc| 494) / 2048 × 16
//                     = 19.359375pt                      (LineBoxMetrics
//                                                         .interContentRatio)
//      L (calibrated) = 16 × 1.25 = 20pt                 (wptRefLineHeightRatio)
//      leading        = 20 − 19.359375 = 0.640625pt      (CSS 2.1 §10.8.1)
//      half-leading   = 0.3203125pt   → `.padding(.vertical:)`
//  SwiftUI reports a `Text`'s height from the platform text engine, ROUNDED
//  UP (on iOS `UIFont.lineHeight` is the raw 19.359375 and the laid-out
//  block measures 20.0; on Mac Catalyst macOS has already rounded the face
//  metrics themselves, so `UIFont.lineHeight` IS 20.0). The half-leading
//  padding was then added ON TOP of that rounded number:
//      box(before) = 20.0 + 0.640625 = 20.640625   → advance 36.640625
//      box(after)  = 1 × L = 20.0                  → advance 36.000000
//  i.e. the excess per line box is exactly the CSS leading, and the repair
//  is to stop letting font metrics size the box at all: pin the frame to
//  `lineCount × line-height` (CSS 2.1 §10.8 — a block's height is the sum
//  of its line boxes) and let the glyphs sit inside it.
//
//  ## Why the raster half of this file uses a DECLARED line-height
//  Mac Catalyst is the only surface these tests run on, and there
//  `UIFont.lineHeight` is pre-rounded, so the CALIBRATED 16px case has zero
//  leading and cannot show the defect. A two-line run with a declared line
//  box does: before the pin its height was `2 × naturalLine + leading`, and
//  the second line box came up short of the browser's `2 × L`.
//
//  Companion pins: DecorationInsetLineBoxTests (which L the calibrated row
//  picks), WPTCaptureModeTests (the three-state table), FidelityWave3Tests
//  (the leading split itself, unchanged by this lane).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class LineBoxPinTests: XCTestCase {

    // MARK: - 1. The pure decision: when a box may be pinned at all

    /// The pin is composed-WPT only. The product renderer and the whole
    /// committed 327-pair baseline corpus keep their natural-height boxes —
    /// the same scoping Compose states for its own corrected arm
    /// (LineHeightNormal.lineBoxSource).
    func testNoPinOutsideWptCapture() {
        XCTAssertNil(LineBoxMetrics.pinnedBoxHeight(lineHeightPx: 20,
                                                    lineCount: 1,
                                                    lineCountIsExact: true,
                                                    wptCapture: false))
    }

    /// `line-height: normal` / nothing declared resolves to nil (natural
    /// metrics, ComponentRenderer.effectiveLineHeight's NATURAL row) — there
    /// is no authored box to pin to, and inventing one would re-break the
    /// wave-22 `font: 92px Arial` case.
    func testNoPinWithoutADeclaredLineBox() {
        XCTAssertNil(LineBoxMetrics.pinnedBoxHeight(lineHeightPx: nil,
                                                    lineCount: 1,
                                                    lineCountIsExact: true,
                                                    wptCapture: true))
        // A non-positive box is not a box either (defensive: a 0 would
        // collapse every following sibling onto this one).
        XCTAssertNil(LineBoxMetrics.pinnedBoxHeight(lineHeightPx: 0,
                                                    lineCount: 2,
                                                    lineCountIsExact: true,
                                                    wptCapture: true))
    }

    /// A GUESSED line count must not pin: TextKit may add lines we did not
    /// count (unknown wrap width, preserved-whitespace modes), and an
    /// under-counted pin would ride every following sibling up — worse than
    /// the drift it removes.
    func testNoPinWhenTheLineCountIsAGuess() {
        XCTAssertNil(LineBoxMetrics.pinnedBoxHeight(lineHeightPx: 20,
                                                    lineCount: 1,
                                                    lineCountIsExact: false,
                                                    wptCapture: true))
    }

    /// The pinned height is CSS 2.1 §10.8's sum of line boxes.
    func testPinnedHeightIsLineCountTimesTheLineBox() throws {
        for (n, l, expect) in [(1, 20.0, 20.0), (2, 20.0, 40.0), (3, 31.25, 93.75)] {
            let pinned = try XCTUnwrap(
                LineBoxMetrics.pinnedBoxHeight(lineHeightPx: CGFloat(l),
                                               lineCount: n,
                                               lineCountIsExact: true,
                                               wptCapture: true))
            XCTAssertEqual(pinned, CGFloat(expect), accuracy: 0.0001,
                           "\(n) line box(es) of \(l)pt must occupy \(expect)pt")
        }
    }

    /// The rendered line count is the DISPLAY string's hard breaks + 1 — the
    /// greedy pre-break has already rewritten TextKit's soft breaks as hard
    /// ones, so for a pre-broken run this is TextKit's own count.
    func testRenderedLineCountCountsHardBreaks() {
        XCTAssertEqual(LineBoxMetrics.renderedLineCount("Should be green"), 1)
        XCTAssertEqual(LineBoxMetrics.renderedLineCount("Should be\ngreen"), 2)
        // An empty last line still occupies a line box (CSS 2.1 §9.4.2).
        XCTAssertEqual(LineBoxMetrics.renderedLineCount("a\n"), 2)
        XCTAssertEqual(LineBoxMetrics.renderedLineCount(""), 1)
    }

    /// `line-clamp: 2` generates AT MOST two line boxes (css-overflow-4 §5),
    /// so a 4-line pre-broken string must pin a 2-line box — the exact miss
    /// that cost css-overflow/line-clamp/block-ellipsis-001 0.039 SSIM when
    /// the first cut of this lane counted the string instead of the clamp.
    func testRenderedLineCountObeysTheLineClamp() {
        XCTAssertEqual(LineBoxMetrics.renderedLineCount("a\nb\nc\nd", clampLimit: 2), 2)
        // A clamp never ADDS line boxes.
        XCTAssertEqual(LineBoxMetrics.renderedLineCount("a", clampLimit: 3), 1)
        // No clamp / a nonsense clamp is the unclamped count.
        XCTAssertEqual(LineBoxMetrics.renderedLineCount("a\nb", clampLimit: nil), 2)
        XCTAssertEqual(LineBoxMetrics.renderedLineCount("a\nb", clampLimit: 0), 2)
    }

    // MARK: - 2. The 36.00px paragraph advance, arithmetically

    /// The headline number: a default-font `<p>` on the composed canvas must
    /// advance EXACTLY 36.00px — the calibrated line box plus the UA block
    /// margin — and the pinned box is what makes the first term exact.
    func testDefaultParagraphAdvanceIsExactlyThirtySix() throws {
        // The line box the CALIBRATED row hands a 16px run (= 16 × 1.25).
        let box = try XCTUnwrap(
            ComponentRenderer.effectiveLineHeight(declared: nil,
                                                  fontSizePx: 16,
                                                  wptCaptureMode: true))
        XCTAssertEqual(box, 20, accuracy: 0.0001)
        // One line box is the whole height of a one-line paragraph.
        let pinned = try XCTUnwrap(
            LineBoxMetrics.pinnedBoxHeight(lineHeightPx: box,
                                           lineCount: 1,
                                           lineCountIsExact: true,
                                           wptCapture: true))
        // The UA sheet's `p { margin: 1em 0 }` at the 16px root, collapsed
        // between adjacent paragraphs (CSS 2.1 §8.3.1 → max(16, 16) = 16).
        let uaMargin = UABlockMargin.vertical(forTag: "p").bottom
        XCTAssertEqual(UABlockMargin.collapsed(uaMargin,
                                               UABlockMargin.vertical(forTag: "p").top),
                       16, accuracy: 0.0001)
        XCTAssertEqual(pinned + 16, 36, accuracy: 0.0001,
                       "a composed `<p>` must advance exactly 36.00px, the "
                       + "number Chromium/web/Compose all produce")
    }

    /// The DEFECT, spelled out in the same arithmetic so a regression that
    /// re-introduces it is legible: sizing the box from the face's rounded
    /// natural height and then ADDING the CSS leading overshoots by exactly
    /// the leading — 0.640625pt for Inter at 16px, i.e. the 36.640625px
    /// advance measured on device before this lane.
    func testTheNaturalMetricsBoxOvershootsByExactlyTheLeading() {
        // Inter's content area at the 16px root, from the face's own hhea
        // table (the constant LineBoxMetrics falls back to when the face is
        // not registered — e.g. in this test bundle).
        let content = 16 * LineBoxMetrics.interContentRatio
        XCTAssertEqual(content, 19.359375, accuracy: 0.000001)
        let box = ComponentRenderer.wptRefLineBoxPx            // 20
        let split = LineBoxMetrics.leading(lineHeightPx: box,
                                           fontSizePx: 16,
                                           design: .default)
        // The leading the label pads with, and the rounding the platform
        // text engine applies to the same content area, are the SAME number
        // here — which is why the box came out one full leading too tall.
        XCTAssertEqual(split.spacing, 0.640625, accuracy: 0.000001)
        XCTAssertEqual(box - content, split.spacing, accuracy: 0.000001)
        XCTAssertEqual(content.rounded(.up) + split.spacing, 20.640625,
                       accuracy: 0.000001)
        // …and the pin replaces that whole expression with the line box.
        XCTAssertEqual(
            LineBoxMetrics.pinnedBoxHeight(lineHeightPx: box, lineCount: 1,
                                           lineCountIsExact: true,
                                           wptCapture: true),
            20)
    }

    // MARK: - 3. Raster: the used box IS the declared line box

    /// Rasterize on the composed capture surface's geometry, at scale 1 so
    /// pixel rows are points (ListMarkerInFlowItemRasterTests pattern).
    @MainActor
    private func render(_ comp: IRComponent, width: CGFloat) throws -> (px: [UInt8], w: Int, h: Int) {
        let view = VStack(alignment: .leading, spacing: 0) {
            ComponentHost(component: comp)
        }
        .frame(width: width, alignment: .topLeading)
        .padding(16)
        .frame(width: 390, height: 300, alignment: .topLeading)
        .background(Color.white)
        .environment(\.wptCaptureMode, true)
        .environment(\.styleViewport, StyleViewport(width: 390, height: 844,
                                                    rootContainingBlock: width))
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        return (buf, w, h)
    }

    /// Height of the painted (dark) background band — the component's USED
    /// box height, read straight off the raster.
    private func paintedBandHeight(_ img: (px: [UInt8], w: Int, h: Int)) -> Int {
        var count = 0
        for y in 0..<img.h {
            var dark = 0
            for x in 20..<60 {
                let i = (y * img.w + x) * 4
                let lum = 0.299 * Double(img.px[i]) + 0.587 * Double(img.px[i + 1])
                    + 0.114 * Double(img.px[i + 2])
                if lum < 128 { dark += 1 }
            }
            if dark > 30 { count += 1 }
        }
        return count
    }

    /// A text component with a painted background so the BOX is measurable.
    private func para(_ text: String, lineHeightPx: Double?) throws -> IRComponent {
        let lh = lineHeightPx.map { ",{\"type\":\"LineHeight\",\"data\":{\"px\":\($0)}}" } ?? ""
        return try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"p0","name":"p0","text":"\(text)",
         "properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0}}}\(lh)],
         "meta":{"sourceTag":"p"}}
        """.utf8))
    }

    /// A run the greedy pre-break wraps to MORE than one line must occupy
    /// exactly `lines × line-height`. Before the pin the box was
    /// `lines × naturalLineHeight + leading`, which under-runs the browser's
    /// stack of line boxes and drags every following root up with it.
    @MainActor
    func testWrappedRunOccupiesAWholeNumberOfDeclaredLineBoxes() throws {
        let L = 30.0
        // Long enough to wrap at the 120pt content box, all spaces so the
        // greedy pre-break owns the breaks (line count therefore exact).
        let comp = try para("Should be green and wrapped over lines",
                            lineHeightPx: L)
        let h = paintedBandHeight(try render(comp, width: 120))
        XCTAssertGreaterThan(h, Int(L), "the fixture must actually wrap")
        XCTAssertEqual(Double(h).truncatingRemainder(dividingBy: L), 0,
                       accuracy: 1.0,
                       "a wrapped run's box must be a whole number of \(L)pt "
                       + "line boxes, got \(h)pt")
    }

    /// The Round-4 single-line case is UNCHANGED by the generalisation: one
    /// line box, exactly the declared height.
    @MainActor
    func testSingleLineRunStillOccupiesExactlyOneLineBox() throws {
        let L = 30.0
        let comp = try para("Green", lineHeightPx: L)
        XCTAssertEqual(paintedBandHeight(try render(comp, width: 300)), Int(L),
                       "a single-line WPT run keeps its exactly-one-line-box "
                       + "height (TITAN Round 4 cap)")
    }
}
