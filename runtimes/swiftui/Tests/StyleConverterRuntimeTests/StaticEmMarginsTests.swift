//
//  StaticEmMarginsTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 22 (B-RC2) — the shared E1–E8 pin table for StaticEmMargin, the
//  em-resolving static block-margin classifier the composed WPT canvas feeds
//  into the CSS 2.1 §8.3.1 root-stack fold (UABlockMargin.staticDeclaredEdges
//  → rootStackMargin → stackedSpacing) — plus the wave-45 (H0) F3/F4 pins
//  for the UA-default em base (absent FontSize ⇒ 16px, or the 13px monospace
//  fixed default via MonospaceUAFontSize). Every expected value here is
//  IDENTICAL to the Kotlin twin's (apps/android-harness
//  StaticEmMarginsTest.kt) so the two implementations cannot drift.
//
//  The wire literals below were copied from the LIVE per-test IR at
//  tools/titan/runs/wave21-final/sections/css-text-decor/per-test-ir/
//  wpt__css-text-decor__text-decoration-dotted-001.json, and re-verified
//  against a probe `:converter:run` on 2026-09-05: `font-size: 16px` →
//  `{"px":16.0,"original":{"type":"length","px":16.0}}` and `margin-top:
//  2em` → `{"original":{"v":2.0,"u":"EM"}}` are both current.
//
//  They are FROZEN COPIES, not a live feed (retro R10, finding A8#5): the
//  header used to claim "so a converter shape change fails here first",
//  which is false in both directions — a hand-copied literal keeps passing
//  after the converter changes shape (it only stops matching reality), and
//  the pins that DO fail first on a shape change are the ones that read
//  real converter output (the vendored corpus pins under
//  tools/titan/fixtures/, and the converter's own suite). What this table
//  pins is the classifier's arithmetic on those byte shapes.
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
        // css-values-4 §6.1.1: `em` on a margin resolves against the
        // element's OWN computed font-size — 92, never the 16px root.
        XCTAssertEqual(StaticEmMargin.ownFontSizePx(try props(fontSize(92))), 92)
    }

    // MARK: - E4 (narrowed, wave 45 H0): declared-but-unresolvable ⇒ bail

    func testE4RelativeOwnFontSizeIsNotAnHonestBase() throws {
        // `font-size: 2em` decodes to `.relative` — no absolute px, so the
        // em margin has nothing to multiply against and the root bails.
        // This is the E4 bail KEPT by wave 45: a DECLARED size that needs
        // the inheritance channel (em/%/var()/calc()) is genuinely
        // unresolvable, unlike the ABSENT-size cases below.
        let p = try props("""
        {"type":"FontSize","data":{"original":{"v":2,"u":"EM"}}},
        \(emMargin("MarginTop", 0.5))
        """)
        XCTAssertNil(StaticEmMargin.ownFontSizePx(p))
        XCTAssertNil(StaticEmMargin.verticalEdges(p))
    }

    // MARK: - F3/F4 (wave 45, H0): ABSENT FontSize resolves the UA ladder

    func testF4FloatsClearMulticol002WireAbsentFontSizeResolvesEmAt16() throws {
        // F4 — the verbatim live wire of tools/titan/runs/wave44-final/
        // sections/CSS2/per-test-ir/wpt__CSS2__floats-clear__floats-clear-
        // multicol-002.json root __1: `margin: 1em` on a multicol root with
        // NO FontSize and NO FontFamily. The base is the UA `medium`
        // default 16px (a composed root is a body-level child), so both
        // vertical edges resolve to 1em × 16 = 16. Pre-fix E4 bailed here
        // and the stack double-spaced (measured +16px, X3/X4).
        let p = try props([
            emMargin("MarginTop", 1.0),
            emMargin("MarginRight", 1.0),
            emMargin("MarginBottom", 1.0),
            emMargin("MarginLeft", 1.0),
            "{\"type\":\"BorderTopStyle\",\"data\":\"SOLID\"}",
            "{\"type\":\"BorderRightStyle\",\"data\":\"SOLID\"}",
            "{\"type\":\"BorderBottomStyle\",\"data\":\"SOLID\"}",
            "{\"type\":\"BorderLeftStyle\",\"data\":\"SOLID\"}",
            "{\"type\":\"BorderTopColor\",\"data\":{\"srgb\":{\"r\":0.7529411764705882,\"g\":0.7529411764705882,\"b\":0.7529411764705882},\"original\":\"silver\"}}",
            "{\"type\":\"Width\",\"data\":{\"type\":\"length\",\"px\":300}}",
            "{\"type\":\"ColumnWidth\",\"data\":{\"px\":100}}",
            "{\"type\":\"ColumnGap\",\"data\":{\"type\":\"length\",\"px\":0}}",
            "{\"type\":\"ColumnFill\",\"data\":\"AUTO\"}",
            "{\"type\":\"Height\",\"data\":{\"type\":\"length\",\"px\":100}}",
        ].joined(separator: ","))
        XCTAssertEqual(StaticEmMargin.ownFontSizePx(p), 16)
        let e = StaticEmMargin.verticalEdges(p)
        XCTAssertEqual(e?.top, 16)
        XCTAssertEqual(e?.bottom, 16)
    }

    func testF3DiscardMulticol001WireMonospaceFirstFamilyResolvesEmAt13() throws {
        // F3 — the verbatim live wire of …/css-overflow/per-test-ir/
        // wpt__css-overflow__line-clamp__discard__discard-multicol-001.json
        // roots __1/__2: FontFamily ["monospace"] FIRST + `margin: 1em`,
        // no FontSize. MonospaceUAFontSize's 13px fixed default (the value
        // the frozen refs rasterised) is the base ⇒ 13/13 — consulted
        // through the single quirk owner, never re-derived here.
        let p = try props([
            "{\"type\":\"FontFamily\",\"data\":[\"monospace\"]}",
            "{\"type\":\"RowGap\",\"data\":{\"type\":\"length\",\"original\":{\"v\":1,\"u\":\"CH\"}}}",
            "{\"type\":\"ColumnGap\",\"data\":{\"type\":\"length\",\"original\":{\"v\":1,\"u\":\"CH\"}}}",
            "{\"type\":\"Width\",\"data\":{\"type\":\"length\",\"original\":{\"v\":27,\"u\":\"CH\"}}}",
            "{\"type\":\"ColumnCount\",\"data\":3}",
            "{\"type\":\"Height\",\"data\":{\"type\":\"length\",\"original\":{\"v\":2,\"u\":\"LH\"}}}",
            "{\"type\":\"BorderTopWidth\",\"data\":{\"px\":1}}",
            "{\"type\":\"BorderTopStyle\",\"data\":\"SOLID\"}",
            emMargin("MarginTop", 1.0),
            emMargin("MarginRight", 1.0),
            emMargin("MarginBottom", 1.0),
            emMargin("MarginLeft", 1.0),
            "{\"type\":\"Continue\",\"data\":\"DISCARD\"}",
        ].joined(separator: ","))
        XCTAssertEqual(StaticEmMargin.ownFontSizePx(p), 13)
        let e = StaticEmMargin.verticalEdges(p)
        XCTAssertEqual(e?.top, 13)
        XCTAssertEqual(e?.bottom, 13)
    }

    func testF4ConcreteFaceFirstFamilyKeepsTheStandard16Default() throws {
        // The quirk keys on the FIRST entry being the monospace GENERIC
        // (Blink's rule — see MonospaceUAFontSize's header): a concrete
        // face first keeps the standard 16px default even when the list
        // ends in the generic.
        let p = try props("""
        {"type":"FontFamily","data":["Courier New","monospace"]},
        \(emMargin("MarginTop", 1.0))
        """)
        XCTAssertEqual(StaticEmMargin.ownFontSizePx(p), 16)
        let e = StaticEmMargin.verticalEdges(p)
        XCTAssertEqual(e?.top, 16)
        XCTAssertEqual(e?.bottom, 0)
    }

    func testF4FloatsClearMulticol002StackFoldEmitsOneSingle16pxGap() throws {
        // The consequence pin — the measured defect this wave closes. The
        // fixture stacks a bare `<p>` (UA margins 16/16) above the 1em-
        // margin multicol root. Post-fix the root classifies (16,16), so
        // rootStackMargin takes R2 (stripDeclared) and the §8.3.1 fold
        // emits ONE max(16,16) = 16px gap between the two roots; the
        // pre-fix bail (R4/R5) emitted the 16px UA gap AND left
        // MarginApplier rendering the full 16px margin — 32px total, the
        // measured +16 shift (Android border-top y=124 vs the ref's 108).
        let multicol = try props([
            emMargin("MarginTop", 1.0), emMargin("MarginBottom", 1.0),
        ].joined(separator: ","))
        let edges = StaticEmMargin.verticalEdges(multicol)
        XCTAssertEqual(edges?.top, 16)
        XCTAssertEqual(edges?.bottom, 16)
        let plans = [
            // The fixture's root __0: `<p>`, zero properties ⇒ R1 pure UA.
            UABlockMargin.rootStackMargin(
                tag: "p", declaresTop: false, declaresBottom: false,
                staticDeclaredEdges: (0, 0)),
            // The multicol root ⇒ R2: declared px into the fold + strip.
            UABlockMargin.rootStackMargin(
                tag: nil, declaresTop: true, declaresBottom: true,
                staticDeclaredEdges: edges),
        ]
        XCTAssertTrue(plans[1].stripDeclared)
        let spacing = UABlockMargin.stackedSpacing(plans: plans)
        // Gap indices: [above p, BETWEEN p and multicol] + trailing.
        XCTAssertEqual(spacing.leading, [16, 16])
        XCTAssertEqual(spacing.trailing, 16)
    }

    func testF3DiscardMulticol001StackFoldCollapsesTheTwo13pxBoxes() throws {
        // discard-multicol-001 stacks TWO monospace 1em-margin boxes under
        // the intro `<p>`: p↔box1 collapses max(16,13) = 16 (box1 top at
        // the ref's 88, not the pre-fix 104) and box1↔box2 collapses
        // max(13,13) = 13 — both margins in the gaps, neither rendered.
        let box = try props([
            "{\"type\":\"FontFamily\",\"data\":[\"monospace\"]}",
            emMargin("MarginTop", 1.0), emMargin("MarginBottom", 1.0),
        ].joined(separator: ","))
        let edges = StaticEmMargin.verticalEdges(box)
        XCTAssertEqual(edges?.top, 13)
        XCTAssertEqual(edges?.bottom, 13)
        let plans = [
            UABlockMargin.rootStackMargin(
                tag: "p", declaresTop: false, declaresBottom: false,
                staticDeclaredEdges: (0, 0)),
            UABlockMargin.rootStackMargin(
                tag: nil, declaresTop: true, declaresBottom: true,
                staticDeclaredEdges: edges),
            UABlockMargin.rootStackMargin(
                tag: nil, declaresTop: true, declaresBottom: true,
                staticDeclaredEdges: edges),
        ]
        let spacing = UABlockMargin.stackedSpacing(plans: plans)
        XCTAssertEqual(spacing.leading, [16, 16, 13])
        XCTAssertEqual(spacing.trailing, 13)
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
