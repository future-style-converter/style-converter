//
//  StaticEmMarginsTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 22 (B-RC2) — the shared E1–E8 pin table for StaticEmMargin, the
//  em-resolving static block-margin classifier the composed WPT canvas feeds
//  into the CSS 2.1 §8.3.1 root-stack fold (UABlockMargin.staticDeclaredEdges
//  → rootStackMargin → stackedSpacing). Every expected value here is
//  IDENTICAL to the Kotlin twin's (apps/android-harness
//  StaticEmMarginsTest.kt) so the two implementations cannot drift.
//
//  The wire literals below are copied from the LIVE per-test IR at
//  tools/titan/runs/wave21-final/sections/css-text-decor/per-test-ir/
//  wpt__css-text-decor__text-decoration-dotted-001.json, so a converter
//  shape change fails here first.
//

import XCTest
import CoreGraphics
@testable import StyleConverterRuntime

final class StaticEmMarginsTests: XCTestCase {

    /// Decode a childless component's `properties` from wire JSON —
    /// IRProperty is Decodable-only, so tests stay on live byte shapes
    /// (same helper as ComposedRootStackTests).
    private func props(_ propsJSON: String) throws -> [IRProperty] {
        let json = "{\"id\":\"t\",\"name\":\"t\",\"properties\":[\(propsJSON)]}"
        return try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8)).properties
    }

    /// The dotted-001 font-size wire fragment: `{"px":N,"original":{…}}`.
    private func fontSize(_ px: Double) -> String {
        "{\"type\":\"FontSize\",\"data\":{\"px\":\(px),\"original\":{\"type\":\"length\",\"px\":\(px)}}}"
    }

    /// The dotted-001 relative-margin wire: `{"original":{"v":N,"u":"EM"}}`.
    private func emMargin(_ type: String, _ v: Double) -> String {
        "{\"type\":\"\(type)\",\"data\":{\"original\":{\"v\":\(v),\"u\":\"EM\"}}}"
    }

    // MARK: - E1: the absolute-px lane is unchanged

    func testE1AbsolutePxEdgeResolvesToItself() throws {
        // E1: the safe-001 wire `{"px":20}` — delegated verbatim to
        // MarginCollapse.staticEdge, so the px lane has ONE definition.
        let p = try props("""
        {"type":"MarginTop","data":{"px":20}},
        {"type":"MarginBottom","data":{"px":20}}
        """)
        let e = StaticEmMargin.verticalEdges(p)
        XCTAssertEqual(e?.top, 20)
        XCTAssertEqual(e?.bottom, 20)
    }

    func testE1NegativePxEdgeStaysOutOfScope() throws {
        // E1 floor: §8.3.1's negative-margin rules are NOT emulated by this
        // lane, so a negative edge bails the whole root (unchanged).
        let p = try props("""
        {"type":"MarginTop","data":{"px":-10}},
        {"type":"MarginBottom","data":{"px":20}}
        """)
        XCTAssertNil(StaticEmMargin.verticalEdges(p))
    }

    // MARK: - E2/E3: em resolution

    func testE2EmEdgeWithResolvedPxFallbackTakesThePx() throws {
        // E2: a converter-pre-resolved em. Swift keeps the unit hint with a
        // pxFallback and Kotlin collapses the shape to Exact upstream —
        // both rule tables answer 46.
        let p = try props("""
        {"type":"MarginTop","data":{"px":46,"original":{"v":0.5,"u":"EM"}}},
        {"type":"MarginBottom","data":{"px":46,"original":{"v":0.5,"u":"EM"}}}
        """)
        let e = StaticEmMargin.verticalEdges(p)
        XCTAssertEqual(e?.top, 46)
        XCTAssertEqual(e?.bottom, 46)
    }

    func testE3Dotted001WireHalfEmOver92pxFontResolvesTo46() throws {
        // E3 — THE defect case. `margin: .5em` + `font-size: 92px` on the
        // three dotted-001 divs: 0.5 × 92 = 46px per edge. The §8.3.1 fold
        // then collapses the abutting 46/46 pair to ONE 46px gap, where the
        // pre-fix bail painted both (measured Android inter-div 92px).
        let p = try props([
            fontSize(92),
            emMargin("MarginTop", 0.5),
            emMargin("MarginRight", 0.5),
            emMargin("MarginBottom", 0.5),
            emMargin("MarginLeft", 0.5),
        ].joined(separator: ","))
        let e = StaticEmMargin.verticalEdges(p)
        XCTAssertEqual(e?.top, 46)
        XCTAssertEqual(e?.bottom, 46)
    }

    func testE3LogicalEmEdgesResolveThroughTheSamePhysicalFold() throws {
        // E3 (logical): margin-block-start/end map to top/bottom in
        // horizontal-tb (css-logical-1 §4.1) via MarginExtractor's second pass.
        let p = try props([
            fontSize(20),
            emMargin("MarginBlockStart", 1.0),
            emMargin("MarginBlockEnd", 2.0),
        ].joined(separator: ","))
        let e = StaticEmMargin.verticalEdges(p)
        XCTAssertEqual(e?.top, 20)
        XCTAssertEqual(e?.bottom, 40)
    }

    func testE3OwnFontSizeIsTheBaseNotTheRootDefault() throws {
        // css-values-4 §5.1.1: `em` on a margin resolves against the
        // element's OWN computed font-size — 92, never the 16px root.
        XCTAssertEqual(StaticEmMargin.ownFontSizePx(try props(fontSize(92))), 92)
    }

    // MARK: - E4: no honest base ⇒ bail

    func testE4EmEdgeWithoutOwnFontSizeBails() throws {
        // E4: the base would be the INHERITED font-size, which the flattened
        // composed-root IR does not carry — guessing 16px would be a silent
        // fallthrough, so the root keeps the pre-fix R4/R5 behavior.
        let p = try props([
            emMargin("MarginTop", 0.5),
            emMargin("MarginBottom", 0.5),
        ].joined(separator: ","))
        XCTAssertNil(StaticEmMargin.verticalEdges(p))
    }

    func testE4RelativeOwnFontSizeIsNotAnHonestBase() throws {
        // `font-size: 2em` decodes to `.relative` — no absolute px, so the
        // em margin has nothing to multiply against and the root bails.
        let p = try props("""
        {"type":"FontSize","data":{"original":{"v":2,"u":"EM"}}},
        \(emMargin("MarginTop", 0.5))
        """)
        XCTAssertNil(StaticEmMargin.ownFontSizePx(p))
        XCTAssertNil(StaticEmMargin.verticalEdges(p))
    }

    // MARK: - E5: every other flavor still bails

    func testE5PercentEdgeStillBails() throws {
        // `%` needs the containing block's inline size (css-box-4 §3), which
        // is not on the property list — the bail is deliberate. The live
        // converter emits a percent margin as a bare number, which
        // MarginExtractor's extractLengthPercentDefault reads as `.percent`.
        let p = try props("\(fontSize(92)),{\"type\":\"MarginTop\",\"data\":10.0}")
        XCTAssertNil(StaticEmMargin.verticalEdges(p))
    }

    func testE5ViewportEdgeStillBails() throws {
        // vw/vh need the viewport — still out of the static scope.
        let p = try props("""
        \(fontSize(92)),
        {"type":"MarginBottom","data":{"original":{"v":5,"u":"VW"}}}
        """)
        XCTAssertNil(StaticEmMargin.verticalEdges(p))
    }

    func testE5AutoEdgeStillBails() throws {
        // `margin-top: auto` is alignment, not px — MarginApplier's vertical
        // auto-centering path must stay untouched.
        let p = try props("\(fontSize(92)),{\"type\":\"MarginTop\",\"data\":\"auto\"}")
        XCTAssertNil(StaticEmMargin.verticalEdges(p))
    }

    // MARK: - E6/E7: mixed lists

    func testE6EmTopWithPxBottomResolvesBothLanes() throws {
        // E6: the two lanes compose per edge — em top, absolute-px bottom.
        let p = try props("""
        \(fontSize(32)),
        \(emMargin("MarginTop", 0.5)),
        {"type":"MarginBottom","data":{"px":8}}
        """)
        let e = StaticEmMargin.verticalEdges(p)
        XCTAssertEqual(e?.top, 16)
        XCTAssertEqual(e?.bottom, 8)
    }

    func testE7HorizontalEmEdgesAreIgnored() throws {
        // E7: only VERTICAL margins collapse in horizontal writing mode
        // (§8.3.1), so an unresolvable inline edge must NOT bail the root —
        // dotted-001's `margin-left: .5em` keeps rendering via MarginApplier.
        let p = try props("""
        {"type":"MarginLeft","data":10.0},
        {"type":"MarginTop","data":{"px":4}}
        """)
        let e = StaticEmMargin.verticalEdges(p)
        XCTAssertEqual(e?.top, 4)
        XCTAssertEqual(e?.bottom, 0)
    }

    // MARK: - E8: drift guard against the narrow runtime classifier

    func testE8NonEmWiresMatchTheNarrowClassifierByteForByte() throws {
        // E8: on any list WITHOUT an em block margin the widened classifier
        // is byte-identical to MarginCollapse.staticVerticalEdges — this is
        // what keeps every wave-19 R/S/T pin and the composed-canvas
        // geometry of the other 14 sections unchanged.
        let cases = [
            "{\"type\":\"MarginTop\",\"data\":{\"px\":20}},{\"type\":\"MarginBottom\",\"data\":{\"px\":20}}",
            "{\"type\":\"BackgroundColor\",\"data\":\"#fff\"}",
            "{\"type\":\"MarginTop\",\"data\":\"auto\"},{\"type\":\"MarginBottom\",\"data\":{\"px\":20}}",
            "{\"type\":\"MarginTop\",\"data\":10.0}",
            "{\"type\":\"MarginBlockStart\",\"data\":{\"px\":12}}",
        ]
        for c in cases {
            let p = try props(c)
            let narrow = MarginCollapse.staticVerticalEdges(p)
            let wide = StaticEmMargin.verticalEdges(p)
            XCTAssertEqual(narrow?.top, wide?.top, "drift on: \(c)")
            XCTAssertEqual(narrow?.bottom, wide?.bottom, "drift on: \(c)")
            XCTAssertEqual(narrow == nil, wide == nil, "nil-ness drift on: \(c)")
        }
    }
}
