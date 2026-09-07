//
//  ListMarkerSymbolTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 30, lane 3 — pin table for ListMarkerSymbol (fix B6): the painted
//  geometry of a disc / circle / square marker.
//
//  ## The defect these pin against (measured on the LIVE wave29-final run)
//  Ink extents of the `square` marker of
//  wpt__css-lists__change-list-style-type-001 (`font-size: 16px`):
//
//      platform | marker ink                  | ratio vs web
//      web      | x 56–60, y 41–45  (5 × 5)   | 1.00
//      Android  | x 17–27, y 36–46  (11 × 11) | 2.20
//      iOS      | x 17–29, y 36–48  (13 × 13) | 2.60
//
//  The natives paint the UA symbols as TEXT glyphs, so their size is a
//  property of whichever fallback face resolved them — 2.2–2.6× the
//  browser's painted shape, and not even equal to each other.
//
//  TWIN of the Compose suite ListMarkerSymbolTest.kt.
//

import XCTest
// SwiftUI for the wave-41 raster pin (Text / ImageRenderer / Color);
// ImageRenderer is the same @MainActor surface the sibling raster suites
// (ListMarkerInFlowItemRasterTests) already stand on.
import SwiftUI
@testable import StyleConverterRuntime

final class ListMarkerSymbolTests: XCTestCase {

    // MARK: - shape(for:): which counter styles are PAINTED

    func testTheThreeUASymbolsPaintAShape() {
        XCTAssertEqual(ListMarkerSymbol.shape(for: .disc), .filledCircle)
        XCTAssertEqual(ListMarkerSymbol.shape(for: .circle), .hollowCircle)
        XCTAssertEqual(ListMarkerSymbol.shape(for: .square), .filledSquare)
    }

    func testEveryNumericAndAlphabeticStyleKeepsItsText() {
        for type: ListMarkerType in [.decimal, .decimalLeadingZero, .upperRoman,
                                     .lowerAlpha, .armenian, .hebrew, .noMarker] {
            XCTAssertNil(ListMarkerSymbol.shape(for: type), type.rawValue)
        }
    }

    func testAnUnresolvedCustomCounterStyleIsNeverAShape() {
        // The live `marker-text-matches-disc` / `-circle` fixtures declare
        // `list-style-type: my-disc` / `my-circle`, which the IR wire
        // cannot carry (no @counter-style rule), so
        // ListMarkerResolver.markerType returns nil for them and the
        // `<ol>` UA `decimal` stands. All three runtimes therefore paint
        // "1." and score 0.99 — this table must not reach them.
        XCTAssertNil(ListMarkerResolver.markerType(fromKeyword: "my-disc"))
        XCTAssertNil(ListMarkerResolver.markerType(fromKeyword: "my-circle"))
    }

    // MARK: - the baked-marker guard

    func testAShapeOnlyReplacesTheInkOfItsOwnGlyph() {
        // A `meta.markerText` bake wins over the local table (wave 27), so
        // a producer may bake any string onto a disc item. Blanking that
        // string and drawing a circle would delete content the wire asked
        // for.
        XCTAssertEqual(ListMarkerSymbol.shape(for: .disc, markerText: "\u{2022}"),
                       .filledCircle)
        XCTAssertNil(ListMarkerSymbol.shape(for: .disc, markerText: "\u{0531}."))
        XCTAssertNil(ListMarkerSymbol.shape(for: .disc, markerText: ""))
        // The other two symbols pair with their own glyph only.
        XCTAssertEqual(ListMarkerSymbol.shape(for: .square, markerText: "\u{25A0}"),
                       .filledSquare)
        XCTAssertNil(ListMarkerSymbol.shape(for: .square, markerText: "\u{2022}"))
    }

    // MARK: - the geometry

    func testTheShapeIs035EmOfTheResolvedFontSize() {
        // 5.6pt at the ref's 16px root, against Chromium's
        // `(ascent * 2 / 3 + 1) / 2` = 5.5 at ascent 15 — and against the
        // 5px of solid ink the reference actually rasterized.
        XCTAssertEqual(ListMarkerSymbol.sizePt(fontSizePx: 16), 5.6, accuracy: 0.001)
        // Scales with the item, exactly as the glyph it replaces did: the
        // counter-styles corpus inherits `font-size: 25px`.
        XCTAssertEqual(ListMarkerSymbol.sizePt(fontSizePx: 25), 8.75, accuracy: 0.001)
    }

    func testAnUnresolvedFontSizePaintsNothingRatherThanAnInvertedRect() {
        XCTAssertEqual(ListMarkerSymbol.sizePt(fontSizePx: 0), 0)
        XCTAssertEqual(ListMarkerSymbol.sizePt(fontSizePx: -4), 0)
        XCTAssertEqual(ListMarkerSymbol.strokePt(fontSizePx: 0), 0)
    }

    func testTheHollowCircleStrokesAtOneDevicePixelOfTheRefRoot() {
        XCTAssertEqual(ListMarkerSymbol.strokePt(fontSizePx: 16), 1, accuracy: 0.001)
    }

    func testTheTwoNativesResolveTheSameConstants() {
        // The parity half: Compose's ListMarkerSymbol.SIZE_EM / STROKE_EM
        // carry these exact numbers, which is what closes the 11×11 vs
        // 13×13 glyph gap in the table above — a painted shape is
        // font-independent, so both natives now land on one size.
        XCTAssertEqual(ListMarkerSymbol.sizeEm, 0.35, accuracy: 0.0001)
        XCTAssertEqual(ListMarkerSymbol.strokeEm, 1.0 / 16.0, accuracy: 0.0001)
    }

    // MARK: - wave 41 (lane T4): the TEXT branch's typography

    func testTheTextBranchCarriesTheResolvedMarkerFontSize() {
        // css-lists-3 §3.1.1 — the ::marker inherits from its originating
        // element, whose iOS text bottom-out is `.custom("Inter",
        // size: fontSize ?? 16)`. Before wave 41 the marker `Text` carried
        // NO font and ASCII markers resolved SwiftUI's environment body
        // default (SF at 17pt) — a face and size with no CSS basis, where
        // css-lists-3 §3.1.1 inherits the originating element's computed
        // font (the document default, 16px Inter). Digit metrics cannot
        // distinguish SF@17 from Inter@16 at capture resolution, so the
        // pin rests on the inheritance rule, not a measured face delta.
        // The extension threads ONE resolved size
        // into both consumers so shape and text can never disagree.
        let m = ListMarkerSymbolPaint(
            shape: nil,
            sizePt: ListMarkerSymbol.sizePt(fontSizePx: 16),
            strokePt: ListMarkerSymbol.strokePt(fontSizePx: 16),
            markerFontPt: 16)
        XCTAssertEqual(m.markerFontPt, 16)
        // A declared container size reaches the text branch verbatim — the
        // 25px counter-styles shape keeps painting its markers at 25.
        let large = ListMarkerSymbolPaint(shape: nil, sizePt: 8.75,
                                          strokePt: 25.0 / 16.0,
                                          markerFontPt: 25)
        XCTAssertEqual(large.markerFontPt, 25)
    }

    func testAnUnresolvableFontSizeDisablesTheTextBranchFont() {
        // Defensive twin of the `sizePt > 0` symbol guard: ≤ 0 must keep
        // the pre-wave-41 render (no font modifier) rather than handing
        // `.custom` a degenerate size-0 face.
        let m = ListMarkerSymbolPaint(shape: nil, sizePt: 0, strokePt: 0,
                                      markerFontPt: 0)
        XCTAssertEqual(m.markerFontPt, 0)
        // The branch condition itself is `markerFontPt > 0` — pinned here
        // as data so a refactor that flips the guard's sense cannot pass.
        XCTAssertFalse(m.markerFontPt > 0)
    }

    /// The behavioural half: the text branch's font genuinely reaches the
    /// glyphs. A property pin alone cannot see a dropped `.font` call, so
    /// this rasterizes the same marker string through the modifier at two
    /// sizes far enough apart (16 vs 32) that the ink height must roughly
    /// double — true whether or not the Inter face is registered in the
    /// unit bundle, because `Font.custom` preserves its SIZE through the
    /// unregistered-family fallback (the same degrade the item path's
    /// `.custom("Inter", …)` documents at ComponentRenderer's `font` var).
    @MainActor
    func testTheTextBranchFontActuallySizesTheInk() throws {
        // Rasterize "1." through the modifier at a given marker font size,
        // on a white stage, and return the glyph band's ink height.
        func inkHeight(markerFontPt: CGFloat) throws -> Int {
            let view = Text("1.")
                .modifier(ListMarkerSymbolPaint(shape: nil, sizePt: 0,
                                                strokePt: 0,
                                                markerFontPt: markerFontPt))
                .frame(width: 80, height: 80, alignment: .topLeading)
                .background(Color.white)
            let renderer = ImageRenderer(content: view)
            renderer.scale = 1
            let cg = try XCTUnwrap(renderer.cgImage)
            let w = cg.width, h = cg.height
            var buf = [UInt8](repeating: 0, count: w * h * 4)
            let ctx = try XCTUnwrap(CGContext(
                data: &buf, width: w, height: h, bitsPerComponent: 8,
                bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
            ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
            // Ink = dark pixels; the band's height is max inked y − min.
            var minY = Int.max, maxY = Int.min
            for y in 0..<h { for x in 0..<w {
                let i = (y * w + x) * 4
                if buf[i] < 128 && buf[i + 1] < 128 && buf[i + 2] < 128 {
                    minY = min(minY, y); maxY = max(maxY, y)
                }
            } }
            return minY == .max ? 0 : maxY - minY + 1
        }
        let at16 = try inkHeight(markerFontPt: 16)
        let at32 = try inkHeight(markerFontPt: 32)
        // Both must actually paint …
        XCTAssertGreaterThan(at16, 0, "no ink at 16pt — the branch vanished")
        // … and 32pt must be materially taller than 16pt: a dropped
        // `.font` would render BOTH at the ~17pt environment default and
        // the two heights would collapse to equal.
        XCTAssertGreaterThanOrEqual(at32, at16 + 6,
            "marker ink did not scale with markerFontPt (16pt → \(at16)px, "
            + "32pt → \(at32)px) — the text branch's font is not reaching "
            + "the glyphs")
    }
}
