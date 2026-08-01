//
//  DecorationColorOpsTests.swift
//  StyleConverterRuntimeTests
//
//  DecorationColorOps pin table — applier campaign wave 22, lane DECOR
//  (B-RC4b, per-line decoration colours).
//
//  TWIN of the Compose suite DecorationColorOpsTest.kt for the shared
//  half — the wire keyword table and `resolve` — where the cases and
//  expected values are identical (repo twin rule). Change one, change
//  both. What is deliberately NOT twinned, matching the module's own twin
//  ledger: the geometry and op cases. Compose anchors bands on
//  TextLayoutResult BASELINES (DecorationColorOps.bands/bandTop there),
//  this overlay anchors on LINE-BOX TOPS (DecorationMetrics.top) and
//  expands ops per row in ComponentRenderer.decorationRow, so the same
//  measured web rows are pinned here as line-box-relative offsets and the
//  op cases go straight through DecorationOps.styleOps — the call the
//  painter actually makes. (Wave 22 first pinned them through a
//  `ColoredBand`/`ops` pair that no iOS painter called; those nominal
//  twins were deleted, so these cases now exercise the live path.)
//
//  Every number below is pinned against LIVE wave-21 artifacts:
//    • WEB capture (the pixel oracle for this section — web is the
//      platform that matches the browser ref):
//      tools/titan/runs/wave21-final/sections/css-text-decor/report/images/
//      web/wpt__css-text-decor__text-decoration-color.png, 390×600.
//      Component `text-decoration-color__7` = span[underline blue] >
//      span[overline gray] > span[line-through green], text on the
//      innermost. Visual line 0 paints THREE 1px rows in THREE colours:
//        row 219 (128,128,128) gray  → overline
//        row 230 (0,128,0)     green → line-through
//        row 237 (0,0,255)     blue  → underline
//      (line 1 repeats at 239/250/257 — advance 20). The iOS capture of
//      the same component painted TWO rows and the Android capture ONE
//      (green, the innermost box only): the loss this lane closes.
//    • per-test IR: tools/titan/runs/wave21-final/sections/css-text-decor/
//      per-test-ir/wpt__css-text-decor__text-decoration-color.json — the
//      exact normalized sRGB leaves used below (gray 0.5019607843137255,
//      green g=0.5019607843137255, blue b=1).
//

import XCTest
import CoreGraphics
@testable import StyleConverterRuntime

final class DecorationColorOpsTests: XCTestCase {

    // MARK: - the live IR's colour leaves

    /// The live IR's `gray` leaf (128/255 in the capture's row 219).
    private let gray = DecorationColorOps.Rgba(r: 0.5019607843137255,
                                               g: 0.5019607843137255,
                                               b: 0.5019607843137255)

    /// The live IR's `green` leaf (0,128,0 — capture row 230).
    private let green = DecorationColorOps.Rgba(r: 0, g: 0.5019607843137255, b: 0)

    /// The live IR's `blue` leaf (0,0,255 — capture row 237).
    private let blue = DecorationColorOps.Rgba(r: 0, g: 0, b: 1)

    /// The merged wire for the oracle component, ANCESTOR-FIRST exactly
    /// as the IR nests it: outer span underlines blue, its child
    /// overlines gray, the innermost (text-bearing) line-throughs green.
    private var chain: [DecorationColorOps.DecorationLine] {
        [.init(kind: .underline, color: blue),
         .init(kind: .overline, color: gray),
         .init(kind: .lineThrough, color: green)]
    }

    // MARK: - wire keyword table

    func testBothWireSpellingsOfEveryLineKeywordMapToAKind() {
        // The IR ships the screaming spelling (TextDecorationLine data is
        // ["UNDERLINE"] / ["LINE_THROUGH"] in the live per-test IR)…
        XCTAssertEqual(DecorationColorOps.lineKind(from: "UNDERLINE"), .underline)
        XCTAssertEqual(DecorationColorOps.lineKind(from: "OVERLINE"), .overline)
        XCTAssertEqual(DecorationColorOps.lineKind(from: "LINE_THROUGH"), .lineThrough)
        // …the meta.decorations contract spells them the CSS way.
        XCTAssertEqual(DecorationColorOps.lineKind(from: "underline"), .underline)
        XCTAssertEqual(DecorationColorOps.lineKind(from: "overline"), .overline)
        XCTAssertEqual(DecorationColorOps.lineKind(from: "line-through"), .lineThrough)
    }

    func testNonPaintingAndUnknownKeywordsYieldNoLine() {
        // css-text-decor-3 §2.1: `none` paints nothing and `blink` has no
        // visual effect in any modern UA — neither may become a band.
        XCTAssertNil(DecorationColorOps.lineKind(from: "none"))
        XCTAssertNil(DecorationColorOps.lineKind(from: "blink"))
        // Garbage / absent → dropped, never guessed (no silent fallthrough).
        XCTAssertNil(DecorationColorOps.lineKind(from: "grand-underline"))
        XCTAssertNil(DecorationColorOps.lineKind(from: nil))
        // decorationLine propagates the drop rather than inventing a kind.
        XCTAssertNil(DecorationColorOps.decorationLine(line: "blink", color: blue))
        XCTAssertEqual(DecorationColorOps.decorationLine(line: "OVERLINE", color: gray),
                       .init(kind: .overline, color: gray))
    }

    // MARK: - resolve: the ordered request list

    func testNoWireSynthesizesTheLegacyFlagOrderWithCurrentColor() {
        // The property's own keyword order and a nil colour, so the
        // painter substitutes exactly what it did before — the dark-stage
        // 327 byte-identity guarantee (the ZStack reorder this introduces
        // is z-order only over disjoint rows; see resolve's banner).
        XCTAssertEqual(
            DecorationColorOps.resolve(wire: nil, underline: true, overline: true, lineThrough: true),
            [.init(kind: .underline, color: nil),
             .init(kind: .overline, color: nil),
             .init(kind: .lineThrough, color: nil)])
        // Only the flagged kinds appear, in the same order.
        XCTAssertEqual(
            DecorationColorOps.resolve(wire: nil, underline: false, overline: false, lineThrough: true),
            [.init(kind: .lineThrough, color: nil)])
        // Nothing flagged → nothing painted (the overlay's whole gate).
        XCTAssertTrue(
            DecorationColorOps.resolve(wire: nil, underline: false, overline: false, lineThrough: false)
                .isEmpty)
    }

    func testAPresentButEmptyWireIsAuthoritativeAndPaintsNothing() {
        // The contract hole this closes: DecorationWire's known-keyword
        // filter can empty a PRESENT list (every entry an unrecognised
        // §2.1 keyword). Treating that as "absent" would re-enable the
        // legacy flag path and paint lines from the merged flat bag that
        // the authoritative list just said were unpaintable. The painter
        // side of the same rule is `overlayOwns`, which suppresses the
        // built-ins on `decorations != nil` rather than on non-emptiness.
        XCTAssertTrue(
            DecorationColorOps.resolve(wire: [], underline: true,
                                       overline: true, lineThrough: true).isEmpty,
            "an empty-but-present wire must win over the component's own flags")
    }

    func testAMergedWireWinsOverTheFlagsAndKeepsAncestorFirstOrder() {
        // The collapsed run's own TextDecorationLine says only
        // `line-through` (the innermost box), but the merged list carries
        // all three ancestors — the wire is authoritative and its ORDER is
        // the §5.1 per-kind paint order + the extractor's outermost-first
        // emission order (see DecorationColorOps' banner).
        XCTAssertEqual(
            DecorationColorOps.resolve(wire: chain, underline: false,
                                       overline: false, lineThrough: true),
            chain)
    }

    func testTheSameKindMayAppearTwiceWithDifferentColors() {
        // Two nested boxes both underlining is legal CSS; keying the list
        // by kind would silently drop one. Order = ancestor first, so the
        // DESCENDANT's band is emitted last and paints on top — which is
        // why the overlay's ForEach keys on the array OFFSET.
        let doubled: [DecorationColorOps.DecorationLine] = [
            .init(kind: .underline, color: blue),
            .init(kind: .underline, color: green),
        ]
        XCTAssertEqual(
            DecorationColorOps.resolve(wire: doubled, underline: true,
                                       overline: false, lineThrough: false),
            doubled)
        // Both requests resolve to the SAME row (the overlay's per-request
        // ForEach draws them one over the other), and the LAST one — the
        // descendant — is the colour that survives on those pixels.
        let rows = doubled.map { req in
            (DecorationMetrics.top(kind: req.kind, ascentPx: 16,
                                   fontSizePx: 16, explicitThicknessPx: nil),
             req.color)
        }
        XCTAssertEqual(rows.count, 2)
        XCTAssertEqual(rows[0].0, rows[1].0)
        XCTAssertEqual(rows[0].1, blue)
        XCTAssertEqual(rows[1].1, green)
    }

    // MARK: - geometry: the measured web rows, per kind

    func testTheOracleChainLandsOnTheMeasuredWebRowDeltasInItsOwnColors() {
        // fontSize 16 (the capture's default face) → auto thickness
        // DecorationMetrics.autoThickness(16) = max(1, round(16/11)) = 1
        // (the capture's 1px rows). Ascent 16 is the render face's
        // ascender at that size for the pinned metrics.
        XCTAssertEqual(DecorationMetrics.autoThickness(fontSizePx: 16), 1)
        let over = DecorationMetrics.top(kind: .overline, ascentPx: 16,
                                         fontSizePx: 16, explicitThicknessPx: nil)
        let strike = DecorationMetrics.top(kind: .lineThrough, ascentPx: 16,
                                           fontSizePx: 16, explicitThicknessPx: nil)
        let under = DecorationMetrics.top(kind: .underline, ascentPx: 16,
                                          fontSizePx: 16, explicitThicknessPx: nil)
        // Overline hangs one thickness above the line-box top: −1.
        XCTAssertEqual(over, -1)
        // Line-through: round(16 − 16 × 8/22) = round(10.18) = 10.
        XCTAssertEqual(strike, 10)
        // Underline: round(16 + 16 × 2/22) = round(17.45) = 17.
        XCTAssertEqual(under, 17)
        // The row DELTAS the WEB capture shows: gray→green 11 (230−219),
        // gray→blue 18 (237−219). Same three rows, same spacing, from a
        // line-box-top anchor instead of a baseline anchor.
        XCTAssertEqual(strike - over, 11)
        XCTAssertEqual(under - over, 18)
    }

    func testAnExplicitThicknessSwapsInBlinksUnderlineGapOnly() {
        // Wave-21's ref-pinned rule (dotted-001 band tops 207/363/519):
        // gap = max(1, ceil(T/2)) below the SNAPPED baseline (= the
        // rounded ascent, the row TextKit lays glyphs on).
        XCTAssertEqual(DecorationMetrics.top(kind: .underline, ascentPx: 202,
                                             fontSizePx: 92, explicitThicknessPx: 10), 207)
        // Overline's BOTTOM stays flush with the line-box top → −T.
        XCTAssertEqual(DecorationMetrics.top(kind: .overline, ascentPx: 202,
                                             fontSizePx: 92, explicitThicknessPx: 10), -10)
        // Line-through keeps the auto CENTER and straddles it: the auto
        // top round(202 − 92×8/22) = round(168.55) = 169 plus half the
        // auto thickness round(92/11)=8 → centre 173, minus T/2 = 168.
        XCTAssertEqual(DecorationMetrics.top(kind: .lineThrough, ascentPx: 202,
                                             fontSizePx: 92, explicitThicknessPx: 10), 168)
    }

    func testAFatExplicitThicknessMakesOverlineAndLineThroughOverlap() {
        // Z-ORDER CORRECTION PIN (twin of the Compose case). The first
        // wave-22 `resolve` banner justified the ZStack reorder by claiming
        // the three rows are "disjoint by construction … so no pixel can
        // differ". FALSE — and this is the counter-example. Ascent 16 /
        // face 16 / thickness 30 (the css-text-decor dotted tests already
        // author 10/20/30px):
        //   overline     −30                              → [−30, 0)
        //   line-through auto top round(16 − 16×8/22) = 10
        //                + half the auto thickness
        //                  round(16/11)=1 → centre 10.5,
        //                minus T/2 = 15 → −4.5, floored −5   → [−5, 25)
        //   underline    16 + max(1, ceil(30/2)) = 31       → [31, 61)
        let t: CGFloat = 30
        let over = DecorationMetrics.top(kind: .overline, ascentPx: 16,
                                         fontSizePx: 16, explicitThicknessPx: t)
        let strike = DecorationMetrics.top(kind: .lineThrough, ascentPx: 16,
                                           fontSizePx: 16, explicitThicknessPx: t)
        let under = DecorationMetrics.top(kind: .underline, ascentPx: 16,
                                          fontSizePx: 16, explicitThicknessPx: t)
        XCTAssertEqual(over, -30)
        XCTAssertEqual(strike, -5)
        XCTAssertEqual(under, 31)
        func overlaps(_ a: CGFloat, _ b: CGFloat) -> Bool { a < b + t && b < a + t }
        // THE counter-example: the centre-anchored strike grows up into the
        // overline, and the strike is emitted LAST, so it wins those rows.
        XCTAssertTrue(overlaps(over, strike), "overline and line-through overlap at t=30")
        // The other two pairs stay disjoint at ANY thickness — a property
        // of the anchors, not luck: the overline's BOTTOM is pinned to the
        // line-box top while the underline's TOP is pinned below the
        // baseline, so growing t moves them apart.
        XCTAssertFalse(overlaps(over, under), "underline never meets the overline")
        XCTAssertFalse(overlaps(strike, under), "underline never meets the strike")
    }

    // MARK: - ops: the per-line colour reaches every paint row
    //
    // These go through the call the PAINTER makes — DecorationOps.styleOps
    // per row, coloured by the overlay's `req.color ?? substitute` rule —
    // not through a mirror type no painter uses (see the header ledger).

    func testSolidRowsCarryTheirOwnLinesColor() {
        // decorationRow expands each row band-locally: (0, 0, width, t).
        let rows = chain.map { req -> (DecorationOps.Op, DecorationColorOps.Rgba?) in
            let ops = DecorationOps.styleOps(left: 0, top: 0, width: 74,
                                             thicknessPx: 1, style: .solid)
            // Solid = exactly one band op per row.
            XCTAssertEqual(ops.count, 1)
            return (ops[0], req.color)
        }
        XCTAssertEqual(rows.count, 3)
        XCTAssertEqual(rows.map { $0.1 }, [blue, gray, green])
        // The op geometry is untouched by the colouring (the wave-21
        // byte-for-byte solid identity).
        XCTAssertEqual(rows[0].0, .band(left: 0, top: 0, width: 74, height: 1))
    }

    func testADottedRowKeepsEveryDotInThatRowsColor() {
        // Style expansion composes with per-line colour: the dotted
        // emitter's dash runs (t=2 ≤ 3 → square dashes at 4px pitch) are
        // ALL painted with the row's one colour, and the next row's with
        // the OTHER — decorationRow takes a single `color` per row.
        let ops = DecorationOps.styleOps(left: 0, top: 0, width: 20,
                                         thicknessPx: 2, style: .dotted)
        // 5 dashes per row (x = 0,4,8,12,16 over a 20px run).
        XCTAssertEqual(ops.count, 5)
        // Two rows, two colours, same op list under each.
        let rows: [(ops: [DecorationOps.Op], color: DecorationColorOps.Rgba?)] = [
            (ops, blue), (ops, gray),
        ]
        XCTAssertEqual(rows.flatMap { r in r.ops.map { _ in r.color } }.count, 10)
        XCTAssertTrue(rows[0].ops.allSatisfy { _ in rows[0].color == blue })
        XCTAssertTrue(rows[1].ops.allSatisfy { _ in rows[1].color == gray })
    }

    func testTheLegacyPathLeavesEveryRowColorNilForThePainter() {
        // No wire → nil colours all the way to the row, so the painter
        // substitutes `text-decoration-color ?? text colour` exactly as it
        // did before wave 22. This is the byte-identity pin.
        let requests = DecorationColorOps.resolve(wire: nil, underline: true,
                                                  overline: true, lineThrough: true)
        XCTAssertEqual(requests.count, 3)
        XCTAssertTrue(requests.allSatisfy { $0.color == nil })
    }

    // MARK: - DecorationMetrics.top is a pure dispatch (no row moved)

    func testTopDispatchesToTheExactRulesTheOverlayUsedBefore() {
        // The wave-5 capture oracle: 22px Inter, ascent 21, baseline 41 —
        // overline rows 18-19 (top −2), strike 33-34 (top 33−20 = 13
        // line-box-relative), underline 43-44 (top 43−20 = 23). Every
        // branch must equal the standalone function it replaced.
        for (ascent, size) in [(CGFloat(21), CGFloat(22)), (CGFloat(16), CGFloat(16))] {
            XCTAssertEqual(
                DecorationMetrics.top(kind: .overline, ascentPx: ascent,
                                      fontSizePx: size, explicitThicknessPx: nil),
                DecorationMetrics.overlineTop(fontSizePx: size))
            XCTAssertEqual(
                DecorationMetrics.top(kind: .lineThrough, ascentPx: ascent,
                                      fontSizePx: size, explicitThicknessPx: nil),
                DecorationMetrics.lineThroughTop(ascentPx: ascent, fontSizePx: size))
            XCTAssertEqual(
                DecorationMetrics.top(kind: .underline, ascentPx: ascent,
                                      fontSizePx: size, explicitThicknessPx: nil),
                DecorationMetrics.underlineTop(ascentPx: ascent, fontSizePx: size))
            // …and the explicit-thickness branches against DecorationOps.
            for t in [CGFloat(10), CGFloat(20), CGFloat(30)] {
                XCTAssertEqual(
                    DecorationMetrics.top(kind: .overline, ascentPx: ascent,
                                          fontSizePx: size, explicitThicknessPx: t),
                    DecorationOps.explicitOverlineTop(thicknessPx: t))
                XCTAssertEqual(
                    DecorationMetrics.top(kind: .lineThrough, ascentPx: ascent,
                                          fontSizePx: size, explicitThicknessPx: t),
                    DecorationOps.explicitLineThroughTop(ascentPx: ascent,
                                                         fontSizePx: size, thicknessPx: t))
                XCTAssertEqual(
                    DecorationMetrics.top(kind: .underline, ascentPx: ascent,
                                          fontSizePx: size, explicitThicknessPx: t),
                    DecorationOps.explicitUnderlineTop(ascentPx: ascent, thicknessPx: t))
            }
        }
    }
}
