//
//  BackgroundPositionShorthandTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 9 — pins the `background` SHORTHAND's BackgroundPosition wire:
//  a tagged PositionValue LIST (converter irmodels/properties/background/
//  BackgroundPositionProperty.kt) that NO runtime consumed before this
//  wave — `background: red url(…) right bottom` silently lost its
//  position (the extractor only understood the longhand X/Y axis
//  objects). Every JSON fixture below is byte-pinned against LIVE
//  converter output (JDK 21, wave 9):
//    background: … right bottom → [{"type":"two-value",
//        "x":{"type":"right"},"y":{"type":"bottom"}}]
//    background: … center       → [{"type":"center"}]
//    background: … 10px 25%     → [{"type":"two-value",
//        "x":{"type":"length","px":10.0},
//        "y":{"type":"percentage","percentage":25.0}}]
//    background: … left         → [{"type":"two-value",
//        "x":{"type":"left"},"y":{"type":"center"}}]  (pre-normalized)
//

import XCTest
@testable import StyleConverterRuntime

final class BackgroundPositionShorthandTests: XCTestCase {

    // MARK: - Helpers

    /// Decode a property list from wire JSON — the decode path IS the
    /// production path, keeping fixtures on converter byte shapes.
    private func props(_ json: String) throws -> [IRProperty] {
        try JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    // MARK: - Shorthand list shapes (pinned wire)

    /// `background: red url(…) right bottom` — keyword-as-type edges map
    /// to the same normalized keyword axes the longhand lane produces.
    func testTwoValueKeywordEdges() throws {
        let cfg = BackgroundPositionExtractor.extract(from: try props("""
        [{"type":"BackgroundPosition",
          "data":[{"type":"two-value","x":{"type":"right"},"y":{"type":"bottom"}}]}]
        """))
        XCTAssertEqual(cfg?.x, .keyword("RIGHT"),
                       "shorthand `right` must land on the X axis as a keyword")
        XCTAssertEqual(cfg?.y, .keyword("BOTTOM"),
                       "shorthand `bottom` must land on the Y axis as a keyword")
    }

    /// `background: blue url(…) center` — the bare-center PositionValue
    /// means 50% on both axes (css-backgrounds-3 §2.6).
    func testBareCenterFillsBothAxes() throws {
        let cfg = BackgroundPositionExtractor.extract(from: try props("""
        [{"type":"BackgroundPosition","data":[{"type":"center"}]}]
        """))
        XCTAssertEqual(cfg?.x, .keyword("CENTER"))
        XCTAssertEqual(cfg?.y, .keyword("CENTER"))
    }

    /// `background: green url(…) 10px 25%` — length and percentage edge
    /// carriers ({px} / {percentage}) map to the numeric axis cases.
    func testLengthAndPercentageEdges() throws {
        let cfg = BackgroundPositionExtractor.extract(from: try props("""
        [{"type":"BackgroundPosition",
          "data":[{"type":"two-value",
                   "x":{"type":"length","px":10.0},
                   "y":{"type":"percentage","percentage":25.0}}]}]
        """))
        XCTAssertEqual(cfg?.x, .px(10))
        XCTAssertEqual(cfg?.y, .percent(25))
    }

    /// `background: red url(…) left` — the parser pre-normalizes single
    /// keywords to two-value with the other axis center; the extractor
    /// must carry both axes through.
    func testSingleKeywordPreNormalizedByParser() throws {
        let cfg = BackgroundPositionExtractor.extract(from: try props("""
        [{"type":"BackgroundPosition",
          "data":[{"type":"two-value","x":{"type":"left"},"y":{"type":"center"}}]}]
        """))
        XCTAssertEqual(cfg?.x, .keyword("LEFT"))
        XCTAssertEqual(cfg?.y, .keyword("CENTER"))
    }

    /// The model's lone-keyword PositionValue variant ({"keyword": …}) —
    /// declared by BackgroundPositionProperty.kt even though the live
    /// parser pre-normalizes: the named edge takes its axis, the other
    /// defaults to center (§3.6 one-value syntax).
    func testKeywordVariantMapsToItsAxis() {
        // Vertical keyword → Y axis, X centers.
        let top = BackgroundPositionExtractor.parseShorthandEntry(
            .object(["type": .string("keyword"), "keyword": .string("top")]))
        XCTAssertEqual(top?.x, .keyword("CENTER"))
        XCTAssertEqual(top?.y, .keyword("TOP"))
        // Horizontal keyword → X axis, Y centers.
        let right = BackgroundPositionExtractor.parseShorthandEntry(
            .object(["type": .string("keyword"), "keyword": .string("right")]))
        XCTAssertEqual(right?.x, .keyword("RIGHT"))
        XCTAssertEqual(right?.y, .keyword("CENTER"))
    }

    /// Only layer 0 is consumed for now (the geometry engine takes one
    /// position pair; per-layer lists ride the multi-layer follow-up).
    func testMultiLayerListTakesFirstEntry() throws {
        let cfg = BackgroundPositionExtractor.extract(from: try props("""
        [{"type":"BackgroundPosition",
          "data":[{"type":"two-value","x":{"type":"right"},"y":{"type":"top"}},
                  {"type":"center"}]}]
        """))
        XCTAssertEqual(cfg?.x, .keyword("RIGHT"), "entry 0 must win")
        XCTAssertEqual(cfg?.y, .keyword("TOP"), "entry 0 must win")
    }

    // MARK: - Refusal paths (no fake positions)

    /// The parser's "raw" passthrough (unparsed author text) must not
    /// invent a position — extract returns nil (untouched config).
    func testRawEntryIsRefused() throws {
        XCTAssertNil(BackgroundPositionExtractor.extract(from: try props("""
        [{"type":"BackgroundPosition",
          "data":[{"type":"raw","value":"var(--pos)"}]}]
        """)), "a raw shorthand entry must not fake a position")
    }

    /// An empty layer list positions nothing.
    func testEmptyListIsRefused() throws {
        XCTAssertNil(BackgroundPositionExtractor.extract(from: try props("""
        [{"type":"BackgroundPosition","data":[]}]
        """)), "an empty shorthand list must leave the config untouched")
    }

    // MARK: - Longhand regression (pre-wave-9 shapes unchanged)

    /// The longhand X/Y axis objects must keep parsing exactly as before
    /// — the shorthand branch only handles the ARRAY shape.
    func testLonghandAxisShapesUnchanged() throws {
        let cfg = BackgroundPositionExtractor.extract(from: try props("""
        [{"type":"BackgroundPositionX","data":{"type":"keyword","value":"RIGHT"}},
         {"type":"BackgroundPositionY","data":{"type":"length","px":12.0}}]
        """))
        XCTAssertEqual(cfg?.x, .keyword("RIGHT"))
        XCTAssertEqual(cfg?.y, .px(12))
    }
}
