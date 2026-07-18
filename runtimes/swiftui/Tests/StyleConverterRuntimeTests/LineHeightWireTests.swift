//
//  LineHeightWireTests.swift
//  StyleConverterRuntimeTests
//
//  Applier campaign — the line-height lane, two pixel-diagnosed fixes:
//    1. WIRE UNWRAP: LineHeight lengths ride a nested wire with NO
//       top-level px, so px/em/rem line-heights all extracted nil and
//       fell back to natural metrics (iOS inkTop 8 vs web/Android 13
//       on Rem_2). DynamicValueResolver.resolveLineHeightWire now
//       unwraps them in the resolver (em needs the element's OWN
//       font-size channel, css-values-4 §6.1).
//    2. SUB-NATURAL PLACEMENT: line-height BELOW the natural content
//       height (Unitless_1: L=18 < Inter's 21.78 at 18px) paints the
//       glyph band (L − natural)/2 higher in a browser; SwiftUI's
//       lineSpacing clamp kept iOS at natural placement. The
//       compensating `.offset(y:)` translation is pinned here both as
//       math (LineBoxMetrics.subNaturalOffset) and as a raster delta.
//
//  Every wire-shape literal below is copied from LIVE converter output
//  (./gradlew :converter:run on
//  fixtures/properties/typography/line-height.json, re-run for this
//  change) — not hand-invented shapes:
//    Px_24  → {"original":{"type":"length","px":24.0}}
//    Em_1_2 → {"original":{"type":"length","original":{"v":1.2,"u":"EM"}}}
//    Rem_2  → {"original":{"type":"length","original":{"v":2.0,"u":"REM"}}}
//    Unitless_1 → {"multiplier":1.0,"original":{"type":"number","value":1.0}}
//    Normal → {"multiplier":1.2,"original":"normal"}
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class LineHeightWireTests: XCTestCase {

    // MARK: - Helpers

    /// Decode an [IRProperty] from inline JSON — the production decode
    /// path (same convention as IOSTextLaneTests/DynamicValuesTests).
    private func props(_ json: String) -> [IRProperty] {
        try! JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// Run the production resolution pass with no variables and the
    /// default calc bases — exactly what ComponentRenderer feeds
    /// StyleBuilder for a static leaf.
    private func resolve(_ list: [IRProperty],
                         inherited: Double = 16) -> [IRProperty] {
        DynamicValueResolver.resolve(properties: list,
                                     variables: VariableStore(definitions: [:]),
                                     calc: CalcEvaluator.EvalContext(),
                                     inheritedFontSizePx: inherited)
    }

    // MARK: - 1. resolveLineHeightWire shapes (pinned to live output)

    /// Absolute length: pixels sit one level down (`Px_24` live shape).
    func testWireAbsoluteNestedPx() {
        XCTAssertEqual(DynamicValueResolver.resolveLineHeightWire(
            .object(["original": .object(["type": .string("length"),
                                          "px": .double(24.0)])]),
            ownFontSizePx: 18), 24.0)
    }

    /// rem × 16 (harness root) regardless of the element size (`Rem_2`).
    func testWireRem() {
        XCTAssertEqual(DynamicValueResolver.resolveLineHeightWire(
            .object(["original": .object(["type": .string("length"),
                                          "original": .object(["v": .double(2.0),
                                                               "u": .string("REM")])])]),
            ownFontSizePx: 18), 32.0)
    }

    /// em × the element's OWN font size (css-values-4 §6.1 — unlike
    /// font-size's em, which uses the INHERITED size) (`Em_1_2`).
    func testWireEmUsesOwnFontSize() {
        let em = DynamicValueResolver.resolveLineHeightWire(
            .object(["original": .object(["type": .string("length"),
                                          "original": .object(["v": .double(1.2),
                                                               "u": .string("EM")])])]),
            ownFontSizePx: 18)
        XCTAssertEqual(em ?? -1, 21.6, accuracy: 1e-9)
    }

    /// Every non-length shape stays on its existing lane: unitless
    /// multipliers, percentages (pre-folded to multiplier), `normal`,
    /// calc expressions, and a hypothetical authoritative top-level px.
    func testWireLeavesOtherShapesAlone() {
        // Unitless_1 live shape → multiplier lane.
        XCTAssertNil(DynamicValueResolver.resolveLineHeightWire(
            .object(["multiplier": .double(1.0),
                     "original": .object(["type": .string("number"),
                                          "value": .double(1.0)])]),
            ownFontSizePx: 18))
        // Percent_150 live shape → multiplier lane.
        XCTAssertNil(DynamicValueResolver.resolveLineHeightWire(
            .object(["multiplier": .double(1.5),
                     "original": .object(["type": .string("percentage"),
                                          "value": .double(150.0)])]),
            ownFontSizePx: 18))
        // Normal live shape (original is a STRING) → multiplier lane.
        XCTAssertNil(DynamicValueResolver.resolveLineHeightWire(
            .object(["multiplier": .double(1.2), "original": .string("normal")]),
            ownFontSizePx: 18))
        // Calc live shape → the resolver's expression lane, not ours.
        XCTAssertNil(DynamicValueResolver.resolveLineHeightWire(
            .object(["original": .object(["type": .string("expression"),
                                          "expr": .string("calc(1em + 4px)")])]),
            ownFontSizePx: 18))
        // Top-level px is authoritative — never second-guessed.
        XCTAssertNil(DynamicValueResolver.resolveLineHeightWire(
            .object(["px": .double(24.0),
                     "original": .object(["type": .string("length"),
                                          "px": .double(24.0)])]),
            ownFontSizePx: 18))
    }

    // MARK: - 2. End-to-end through the resolver + extractor

    /// The three length wires are REWRITTEN to `{px:N}` so
    /// LineHeightExtractor stops reading nil (the pixel-proven bug).
    func testResolverRewritesLengthWires() {
        // Rem_2 (the pixel-diagnosed capture): 2rem → 32px.
        let rem = resolve(props(
            #"[{"type":"FontSize","data":{"px":18.0}},"# +
            #" {"type":"LineHeight","data":{"original":{"type":"length","original":{"v":2.0,"u":"REM"}}}}]"#))
        XCTAssertEqual(LineHeightExtractor.extract(from: rem)?.px, 32,
                       "rem line-height must reach the extractor as resolved pixels")
        // Px_24: nested absolute px → 24px.
        let px = resolve(props(
            #"[{"type":"FontSize","data":{"px":18.0}},"# +
            #" {"type":"LineHeight","data":{"original":{"type":"length","px":24.0}}}]"#))
        XCTAssertEqual(LineHeightExtractor.extract(from: px)?.px, 24)
        // Em_1_2 against the OWN size from pass 0 — the inherited basis
        // is deliberately bogus (99) to prove §6.1's own-size rule.
        let em = resolve(props(
            #"[{"type":"FontSize","data":{"px":18.0}},"# +
            #" {"type":"LineHeight","data":{"original":{"type":"length","original":{"v":1.2,"u":"EM"}}}}]"#),
            inherited: 99)
        XCTAssertEqual(Double(LineHeightExtractor.extract(from: em)?.px ?? -1),
                       21.6, accuracy: 1e-9)
    }

    /// Multiplier shapes pass through the resolver byte-untouched and
    /// keep the extractor's multiplier lane (Unitless_* / Percent_*).
    func testResolverLeavesMultiplierShapes() {
        let out = resolve(props(
            #"[{"type":"LineHeight","data":{"multiplier":2.0,"original":{"type":"number","value":2.0}}}]"#))
        let cfg = LineHeightExtractor.extract(from: out)
        XCTAssertNil(cfg?.px, "multiplier shape must not grow a px")
        XCTAssertEqual(cfg?.multiplier, 2.0)
    }

    // MARK: - 3. Sub-natural placement math

    /// The signed compensation at the shared-contract anchor point
    /// (Unitless_1): L=18, natural = 18 × 2478/2048 = 21.779 (test
    /// bundle has no registered Inter → the deterministic hhea-table
    /// fallback) → (L − natural)/2 = −1.8896 — the exact amount the
    /// web reference paints the glyph band higher.
    func testSubNaturalOffsetMath() {
        let natural = 18.0 * 2478.0 / 2048.0
        let off = LineBoxMetrics.subNaturalOffset(lineHeightPx: 18,
                                                  fontSizePx: 18,
                                                  design: .default)
        XCTAssertEqual(Double(off), (18 - natural) / 2, accuracy: 1e-9)
        XCTAssertEqual(Double(off), -1.8896, accuracy: 0.001)
        // L ≥ natural → exactly 0 (the positive case rides the existing
        // leading split; every such render stays byte-stable).
        XCTAssertEqual(LineBoxMetrics.subNaturalOffset(lineHeightPx: 32,
                                                       fontSizePx: 18,
                                                       design: .default), 0)
        // No declared line-height → natural metrics, no shift.
        XCTAssertEqual(LineBoxMetrics.subNaturalOffset(lineHeightPx: nil,
                                                       fontSizePx: 18,
                                                       design: .default), 0)
    }

    // MARK: - 4. Raster pin — the glyph band actually moves up

    /// Find the topmost ink row (any pixel with alpha above the glyph
    /// threshold) in a scale-1 ImageRenderer capture of `view`. Same
    /// probe as IOSTextLaneTests' multi-line smoke.
    @MainActor
    private func topInkRow<V: View>(of view: V) throws -> Int {
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1   // one buffer pixel per point
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        guard let data = cg.dataProvider?.data as Data? else {
            throw XCTSkip("no bitmap data")
        }
        let bpr = cg.bytesPerRow, bpp = cg.bitsPerPixel / 8
        let alphaFirst = cg.alphaInfo == .premultipliedFirst || cg.alphaInfo == .first
        for y in 0..<cg.height {
            for x in 0..<cg.width {
                let idx = y * bpr + x * bpp + (alphaFirst ? 0 : 3)
                if idx < data.count, data[idx] > 60 { return y }
            }
        }
        // No ink at all is its own failure — surfaced, never silent.
        XCTFail("no ink found in raster")
        return -1
    }

    /// A Unitless_1-shaped leaf (no background so the alpha probe sees
    /// glyphs only; single word so the run can never wrap): with the
    /// fix its glyph band must sit ~2px HIGHER than the unfixed
    /// control. The control is the SAME leaf with no line-height —
    /// pixel-identical to the unfixed sub-natural render, because the
    /// pre-fix clamp degraded sub-natural to natural placement.
    @MainActor
    func testSubNaturalPlacementShiftsGlyphBandUp() throws {
        // Builds the leaf with the LIVE Unitless_1 LineHeight shape
        // (or without any line-height for the control).
        func leaf(lineHeight: Bool) -> some View {
            let lh = lineHeight
                ? #",{"type":"LineHeight","data":{"multiplier":1.0,"original":{"type":"number","value":1.0}}}"#
                : ""
            let component = IRComponent(
                id: "lh1", name: "SubNatural",
                properties: props(
                    #"[{"type":"Width","data":{"type":"length","px":240.0}},"# +
                    #" {"type":"Height","data":{"type":"length","px":80.0}},"# +
                    #" {"type":"FontSize","data":{"px":18.0}}"# + lh + "]"),
                selectors: nil, media: nil, children: nil, slot: nil,
                text: "Grumpy", pseudos: nil, meta: nil)
            // Fixed canvas so both captures share buffer geometry.
            return ComponentRenderer(component: component)
                .frame(width: 260, height: 120, alignment: .topLeading)
        }
        let fixed   = try topInkRow(of: leaf(lineHeight: true))
        let control = try topInkRow(of: leaf(lineHeight: false))
        // Applied shift is (18 − natural)/2 ≈ −1.89 → the first ink row
        // lands 1–3 buffer rows higher (rounding + antialiasing slack).
        let delta = control - fixed
        XCTAssertTrue((1...3).contains(delta),
                      "expected the sub-natural glyph band ~2px higher " +
                      "than the natural-placement control, got Δ\(delta) " +
                      "(fixed top \(fixed), control top \(control))")
    }
}
