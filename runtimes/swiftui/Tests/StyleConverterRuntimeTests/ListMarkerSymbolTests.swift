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
}
