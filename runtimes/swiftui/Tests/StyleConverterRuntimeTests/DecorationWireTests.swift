//
//  DecorationWireTests.swift
//  StyleConverterRuntimeTests
//
//  DecorationWire pin table — applier campaign wave 22, lane DECOR.
//
//  TWIN of the Compose suite DecorationWireTest.kt — SAME cases, SAME
//  expected values. Change one, change both.
//
//  This suite pins the LAST hop of the meta.decorations seam:
//    extractor `_decorations` → converter `meta.decorations` →
//    IRWireV2Reader → IRMeta.decorations ([IRDecoration], raw wire
//    strings) → HERE → DecorationColorOps.resolve → DecorationMetrics.
//
//  The live artifact these expectations track is the converted
//  re-extracted fixture (`./gradlew :converter:run --args="convert --from
//  css --to ir -i fixtures/wpt/css-text-decor/text-decoration-color.json
//  -o out"`), component `text-decoration-color__7-008`:
//    meta.decorations = [ {line:"underline",   color:"blue"},
//                         {line:"overline",    color:"gray"},
//                         {line:"line-through",color:"green"} ]
//  Colour tokens are AUTHORED — this file is where they become sRGB.
//

import XCTest
import CoreGraphics
@testable import StyleConverterRuntime

final class DecorationWireTests: XCTestCase {

    /// css `gray` = 128/255 in all three channels (capture row 219).
    private let gray = DecorationColorOps.Rgba(r: 128 / 255.0, g: 128 / 255.0, b: 128 / 255.0)

    /// css `green` = #008000, NOT lime (capture row 230).
    private let green = DecorationColorOps.Rgba(r: 0, g: 128 / 255.0, b: 0)

    /// css `blue` = #0000ff (capture row 237).
    private let blue = DecorationColorOps.Rgba(r: 0, g: 0, b: 1)

    // MARK: - the live wire, end to end

    func testTheLiveThreeColourChainResolvesToTheMeasuredSRGBRows() {
        // Exactly the bytes the converter emits for
        // text-decoration-color__7-008 (order = outermost-first).
        let lines = DecorationWire.decorationLines(from: [
            IRDecoration(line: "underline", color: "blue"),
            IRDecoration(line: "overline", color: "gray"),
            IRDecoration(line: "line-through", color: "green"),
        ])
        XCTAssertEqual(lines, [
            .init(kind: .underline, color: blue),
            .init(kind: .overline, color: gray),
            .init(kind: .lineThrough, color: green),
        ])
        // …and those requests land on the capture's measured row DELTAS in
        // the capture's measured colours (the full painter contract; iOS
        // anchors line-box-relative, so deltas are the twin-stable part).
        let requests = DecorationColorOps.resolve(wire: lines, underline: false,
                                                  overline: false, lineThrough: true)
        let rows = requests.map {
            DecorationMetrics.top(kind: $0.kind, ascentPx: 16,
                                  fontSizePx: 16, explicitThicknessPx: nil)
        }
        // underline 17, overline −1, line-through 10 → gray→green 11,
        // gray→blue 18, the WEB capture's spacing.
        XCTAssertEqual(rows, [17, -1, 10])
        XCTAssertEqual(requests.map { $0.color }, [blue, gray, green])
    }

    // MARK: - absence vs emptiness

    func testAbsentStaysAbsentAndEmptyStaysEmpty() {
        // nil → nil: not a collapsed run, the painter synthesizes the
        // component's own flags (the legacy byte-identity path).
        XCTAssertNil(DecorationWire.decorationLines(from: nil))
        // An empty list is a DIFFERENT state and must not be upgraded to
        // "absent" — it is authoritative and paints nothing.
        XCTAssertEqual(DecorationWire.decorationLines(from: []), [])
    }

    func testAWireOfOnlyUnknownKeywordsEmptiesWithoutBecomingAbsent() {
        // The contract hole this seam closes: every entry drops, the list
        // survives as EMPTY, and resolve() then paints nothing instead of
        // falling back to the merged flat bag's flags.
        let lines = DecorationWire.decorationLines(from: [
            IRDecoration(line: "blink"),
            IRDecoration(line: "grand-underline", color: "blue"),
        ])
        XCTAssertEqual(lines, [])
        XCTAssertTrue(
            DecorationColorOps.resolve(wire: lines, underline: true,
                                       overline: true, lineThrough: true).isEmpty,
            "a fully-filtered wire is still authoritative")
    }

    // MARK: - the two drop rules

    func testAnUnknownKeywordDropsItsEntryAndKeepsTheRestInOrder() {
        XCTAssertEqual(
            DecorationWire.decorationLines(from: [
                IRDecoration(line: "underline", color: "blue"),
                IRDecoration(line: "blink", color: "gray"),   // §2.1 grammar, paints nothing
                IRDecoration(line: "line-through"),           // no colour = currentColor
            ]),
            [.init(kind: .underline, color: blue),
             .init(kind: .lineThrough, color: nil)])
    }

    func testAnUnresolvableColourTokenDegradesToCurrentColorNeverToADroppedLine() {
        // oklch() is outside the runtime token parser's families (the
        // converter would normally pre-resolve it as a DECLARATION, but a
        // meta hint is forwarded verbatim). The LINE must survive.
        XCTAssertEqual(
            DecorationWire.decorationLines(from: [
                IRDecoration(line: "underline", color: "oklch(0.7 0.1 200)"),
            ]),
            [.init(kind: .underline, color: nil)])
    }

    func testHexAndKeywordTokensBothResolveThroughTheRuntimeParser() {
        XCTAssertEqual(
            DecorationWire.decorationLines(from: [
                IRDecoration(line: "underline", color: "#00f"),
                IRDecoration(line: "overline", color: "#008000"),
                IRDecoration(line: "line-through", color: "transparent"),
            ]),
            [.init(kind: .underline, color: blue),
             .init(kind: .overline, color: green),
             .init(kind: .lineThrough, color: .init(r: 0, g: 0, b: 0, a: 0))])
    }

    func testKeywordCaseIsNormalizedOnBothChannels() {
        // CSS keywords are ASCII case-insensitive (css-values-4 §3.2), and
        // the IR's screaming line spelling must keep working too.
        XCTAssertEqual(
            DecorationWire.decorationLines(from: [IRDecoration(line: "LINE_THROUGH", color: "BLUE")]),
            [.init(kind: .lineThrough, color: blue)])
    }

    // MARK: - the KNOWN twin asymmetry, stated as a test

    func testTheFunctionalRgbFormResolvesHereButNotOnCompose() {
        // CSSTokenParser parses rgb()/rgba(); Compose's
        // ValueExtractors.parseCssColorLiteral does not (documented in
        // both DecorationWire banners). Pinning it here means the day
        // Compose gains the form, this asymmetry note is the thing that
        // has to change — it cannot rot silently.
        XCTAssertEqual(
            DecorationWire.decorationLines(from: [IRDecoration(line: "underline", color: "rgb(0, 0, 255)")]),
            [.init(kind: .underline, color: blue)])
    }
}
