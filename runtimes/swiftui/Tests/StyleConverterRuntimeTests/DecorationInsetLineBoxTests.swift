//
//  DecorationInsetLineBoxTests.swift
//  Applier campaign wave 23, lane DECOR-INSET (iOS).
//
//  WHAT THIS PINS — the line box a WPT-captured run with NO declared
//  `line-height` gets, and therefore where its glyph band AND the owned
//  decoration overlay riding on top of it paint.
//
//  ## The measurement that opened the lane
//  css-text-decor text-decoration-inset-001/002 scored iOS-web 0.94 while
//  the Android-web pair scored 0.98 on the same captures
//  (tools/titan/runs/wave22-final/sections/css-text-decor/compare.log).
//  Reading the three PNGs row by row shows the divergence is NOT in the
//  decoration geometry: the underline band sits exactly 27 rows below the
//  glyph ink top on ALL THREE platforms, and is 3px thick on iOS and web
//  alike. The whole `<h1>` — glyphs and band together — is translated 7px UP
//  on iOS (iOS ink rows 108-134 / band 135-137 vs web ink 115-141 / band
//  142-144, Android ink 116-141 / band 142-143).
//
//  ## The iOS-specific term
//  The `<h1>` declares `font-size: 32px` and no `line-height`, so all three
//  runtimes fall back to the browser-ref's injected default line box. Web and
//  Compose pin it as the ref's UNITLESS ratio, which recomputes against each
//  element's own font-size (css-inline-3 §2.2), giving the h1 a 40px box:
//    • web     `lineHeight: irLineHeight ?? '1.25'` (apps/web-harness/src/
//              sdui/ComponentRenderer.tsx) + `:where(body){line-height:1.25}`
//    • Compose `composedDefaultLineHeightPx(composedWpt, fontSizePx) =
//              fontSizePx * REF_DEFAULT_FONT_LINE_HEIGHT_RATIO`
//  iOS alone pinned the ABSOLUTE `wptRefLineBoxPx` = 20 — correct only at the
//  16px root it was calibrated at. At 32px that is a SUB-natural box (Inter's
//  content area is 1.20996em ≈ 38.72px), and PlaceholderLabel's CSS 2.1
//  §10.8.1 signed-half-leading model (LineBoxMetrics.subNaturalOffset) then
//  translates the glyph run — with the decoration overlay attached to it —
//  UP by (20 − 38.72)/2 = −9.36pt. That translation, not the band rows, is
//  the drift.
//
//  Blast radius, swept over all 180 wave22-final per-test-ir documents: four
//  tests carry text with a declared font-size ≠ 16 and no line-height —
//  text-decoration-inset-001/002 (32px), text-decoration-color-recalc and
//  line-through-vertical (50px). They are exactly the four css-text-decor
//  rows where iOS trailed a passing Android-web pair. At 16px the ratio
//  reproduces the old constant bit-for-bit, so nothing else can move.
//
//  Companion pins live in WPTCaptureModeTests (the 16px identity + the
//  declared/normal rows) and LineHeightNormalTests (the three-state table).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class DecorationInsetLineBoxTests: XCTestCase {

    // MARK: - The calibrated line box scales with the run's font size

    /// The CALIBRATED row (nothing declared, WPT capture) is the ref's
    /// unitless 1.25 resolved against THIS run's font-size — the numeric twin
    /// of Compose's `composedDefaultLineHeightPx(true, fontSizePx)`.
    func testCalibratedLineBoxIsTheRatioTimesTheRunsFontSize() throws {
        // The three sizes the wave22-final corpus actually exercises: the
        // 16px root, the inset tests' `<h1>`, and the 50px color-recalc /
        // line-through-vertical runs.
        for (fontSize, expected) in [(16.0, 20.0), (32.0, 40.0), (50.0, 62.5)] {
            // Non-nil is itself part of the pin: the calibrated row never
            // falls through to SwiftUI's natural metrics under WPT capture.
            let box = try XCTUnwrap(
                ComponentRenderer.effectiveLineHeight(declared: nil,
                                                      fontSizePx: CGFloat(fontSize),
                                                      wptCaptureMode: true))
            XCTAssertEqual(
                box, CGFloat(expected), accuracy: 0.0001,
                "calibrated line box at \(fontSize)px must be \(fontSize) × 1.25")
        }
    }

    /// The ratio IS the constant at the 16px root — the property that keeps
    /// every other capture in the corpus byte-stable.
    func testRatioReproducesTheLegacyConstantAtTheSixteenPixelRoot() {
        XCTAssertEqual(ComponentRenderer.wptRefLineHeightRatio, 1.25)
        XCTAssertEqual(16 * ComponentRenderer.wptRefLineHeightRatio,
                       ComponentRenderer.wptRefLineBoxPx,
                       "the 16px root must still land on the pinned 20pt box")
        // The default-argument call (every legacy call site) is unchanged.
        XCTAssertEqual(
            ComponentRenderer.effectiveLineHeight(declared: nil, wptCaptureMode: true),
            ComponentRenderer.wptRefLineBoxPx)
    }

    /// The scaling row must not leak outside WPT capture, and must still lose
    /// to an author-declared value / the `normal` keyword — the other two
    /// states of LineHeightNormal.lineBoxSource, unchanged by this lane.
    func testFontSizeScalingIsConfinedToTheCalibratedWptRow() {
        // Product path: still natural metrics regardless of font size.
        XCTAssertNil(ComponentRenderer.effectiveLineHeight(
            declared: nil, fontSizePx: 32, wptCaptureMode: false))
        // A declared value wins over the calibration at any font size.
        XCTAssertEqual(ComponentRenderer.effectiveLineHeight(
            declared: 30, fontSizePx: 32, wptCaptureMode: true), 30)
        // Declared `normal` under WPT capture keeps falling through to the
        // face's own metrics (nil), not to 1.25 × size.
        XCTAssertNil(ComponentRenderer.effectiveLineHeight(
            declared: nil, declaredNormal: true, fontSizePx: 92, wptCaptureMode: true))
    }

    // MARK: - The mechanism: no sub-natural up-shift at 32px any more

    /// The drift was mechanical: a 20pt box under a 38.72pt content area is
    /// SUB-natural, and CSS 2.1 §10.8.1's signed half-leading then paints the
    /// line (glyphs + owned decoration) above the line-box top. With the box
    /// at 1.25 × 32 = 40 the leading turns POSITIVE, so the compensating
    /// translation is exactly zero and the band sits inside its own line box.
    func testCalibratedThirtyTwoPixelRunHasNoSubNaturalUpshift() {
        let content = LineBoxMetrics.contentHeight(fontSizePx: 32, design: .default)
        // Inter's 1.20996em content area — the number that made 20 sub-natural.
        XCTAssertEqual(content, 32 * LineBoxMetrics.interContentRatio, accuracy: 0.0001)
        // The OLD constant: sub-natural, and the up-shift is the measured drift.
        XCTAssertEqual(
            LineBoxMetrics.subNaturalOffset(lineHeightPx: ComponentRenderer.wptRefLineBoxPx,
                                            fontSizePx: 32, design: .default),
            (20 - content) / 2, accuracy: 0.0001,
            "the legacy 20pt pin translates a 32px run up by half the deficit")
        // The FIXED calibration: L > content area → no translation at all.
        let calibrated = ComponentRenderer.effectiveLineHeight(
            declared: nil, fontSizePx: 32, wptCaptureMode: true)
        XCTAssertEqual(
            LineBoxMetrics.subNaturalOffset(lineHeightPx: calibrated,
                                            fontSizePx: 32, design: .default),
            0, accuracy: 0.0001)
    }

    // MARK: - Raster pin (ImageRenderer)

    /// The inset-001 `<h1>`, verbatim from the live per-test IR
    /// (tools/titan/runs/wave22-final/sections/css-text-decor/per-test-ir/
    /// wpt__css-text-decor__text-decoration-inset-001.json, component
    /// `text-decoration-inset-001__1__0`) minus the `Position: ABSOLUTE` and
    /// the margins — this test measures the label's OWN box, so the abspos
    /// placement is deliberately out of frame.
    private func insetH1() throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"h1-053","name":"h1",
         "properties":[
           {"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},
           {"type":"FontSize","data":{"px":32,"original":{"type":"length","px":32}}},
           {"type":"FontWeight","data":{"weight":700,"original":"bold"}}],
         "text":"the quick brown fox",
         "meta":{"sourceTag":"h1","decorations":[{"line":"underline","color":"black"}]}}
        """.utf8))
    }

    /// Rasterize the label on a white 390-wide stage in composed-WPT mode,
    /// anchored [labelBoxTop] points DOWN so an UPWARD overflow is visible
    /// instead of being clipped by the stage edge (the pre-fix render paints
    /// above its own box, which a top-anchored stage would silently swallow).
    private static let labelBoxTop = 20

    @MainActor
    private func render(_ comp: IRComponent) throws -> (px: [UInt8], w: Int, h: Int) {
        let view = VStack(spacing: 0) {
            // The transparent lead-in whose height IS the label's box top.
            Color.clear.frame(height: CGFloat(Self.labelBoxTop))
            ComponentRenderer(component: comp)
            Spacer(minLength: 0)
        }
            // 390 = the harness canvas width; 120 leaves room for the band.
            .frame(width: 390, height: 120, alignment: .topLeading)
            .background(Color.white)
            // The composed-WPT capture environment (mirrors the harness's
            // captureComposedDocument + ComposedCaptureCanvas).
            .environment(\.wptCaptureMode, true)
            .environment(\.wptBlockFlowFillWidth, 374)
        let renderer = ImageRenderer(content: view)
        // Scale 1 = one buffer pixel per logical point, like the harness.
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

    /// Per-row dark-pixel counts — the same reduction the SSIM report's band
    /// reading uses on the capture PNGs, so failures are stated in the same
    /// units as the diagnosis above.
    private func darkRows(_ img: (px: [UInt8], w: Int, h: Int)) -> [Int] {
        (0..<img.h).map { y in
            var dark = 0
            for x in 0..<img.w {
                let i = (y * img.w + x) * 4
                // Rec.601 luma; the stage is white and the ink is black.
                let lum = 0.299 * Double(img.px[i]) + 0.587 * Double(img.px[i + 1])
                    + 0.114 * Double(img.px[i + 2])
                if lum < 128 { dark += 1 }
            }
            return dark
        }
    }

    /// THE PIN: neither the glyph run nor the owned underline may paint ABOVE
    /// the label's own line-box top.
    ///
    /// This is the raster form of the wave-22 drift and it is deliberately
    /// FONT-METRIC-ROBUST: the XCTest bundle has no registered Inter face, so
    /// absolute ink rows depend on the system fallback, but "ink starts at or
    /// below the line-box top" holds for any face whose ascent is inside its
    /// own content area — which is every face, once the line box is at least
    /// as tall as that content area (the property this lane restores).
    /// Pre-fix numbers on this stage: first ink row 12 and band row 45 with a
    /// box top of 20 — the run hung 8 rows ABOVE its own box.
    @MainActor
    func testDecoratedH1PaintsInsideItsOwnLineBox() throws {
        let rows = darkRows(try render(try insetH1()))
        let top = Self.labelBoxTop
        // First inked row anywhere on the stage.
        let firstInk = try XCTUnwrap(rows.firstIndex { $0 > 0 },
                                     "the h1 painted nothing at all")
        XCTAssertGreaterThanOrEqual(
            firstInk, top,
            "glyph run paints \(top - firstInk)pt ABOVE its line-box top "
                + "(row \(firstInk) vs box top \(top)) — the sub-natural "
                + "line-box translation is back")
        // The owned underline is the only near-full-width run of dark pixels
        // (the measured advance spans ~290 of the 390 columns).
        let band = try XCTUnwrap(rows.firstIndex { $0 > 250 },
                                 "no full-width underline band was painted")
        XCTAssertGreaterThanOrEqual(
            band, top,
            "the owned decoration band paints above the line-box top (row \(band))")
        // …and it sits BELOW the glyph ink it decorates (css-text-decor-3
        // §2.1 underline), which is the part the wave-22 captures already had
        // right on all three platforms — pinned here so a line-box change can
        // never silently re-order the two.
        XCTAssertGreaterThan(band, firstInk,
                             "underline band must sit below the glyph ink top")
    }
}
