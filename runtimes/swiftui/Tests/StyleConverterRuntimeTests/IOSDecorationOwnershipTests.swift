//
//  IOSDecorationOwnershipTests.swift
//  StyleConverterRuntimeTests
//
//  Wave-5 gate follow-up pins — decoration-line ownership (Task A) and
//  the currentColor bottom-out (Task B):
//    A1. DecorationMetrics reproduces the MEASURED Chromium rows from
//        the archived wave-5 gate web captures (22px Inter, content top
//        y=20, baseline y=41): underline rows 43-44, line-through rows
//        33-34, overline rows 18-19, all 2px thick — expressed as the
//        baseline/line-top-relative offsets the overlay draws with.
//    A2. text-decoration-style bridges to TextConfig and gates the
//        owned pass to `solid` (patterns keep the built-ins).
//    A3. Executed render proofs: the owned underline / line-through
//        paint EXACTLY one thickness-tall continuous band at the
//        measured offset — a second band (or a fatter one) would mean
//        the platform built-in was NOT suppressed and double-drew.
//    B1. InheritedText.currentColorBottomsOut classifies the
//        "currentColor with no ancestor Color" case (and only it).
//    B2. defaultTextColor is the OPAQUE web-harness body color
//        (#eee ±1/255) — not the 70%-alpha contrast pick.
//    B3. Executed render proof: a bottomed-out currentColor leaf paints
//        opaque glyphs; the undeclared-color control stays at the
//        70%-alpha pick.
//
//  Wire-shape literals mirror the live converter output pinned by
//  IOSTextLaneTests / IOSTextLaneWave5Tests ({"original":"currentColor"},
//  ["UNDERLINE"] enum arrays, {"px":N} length envelopes, bare-string
//  "dashed" decoration styles).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class IOSDecorationOwnershipTests: XCTestCase {

    // MARK: - Helpers

    /// Decode an [IRProperty] from inline JSON — the production decode
    /// path (same convention as IOSTextLaneTests).
    private func props(_ json: String) -> [IRProperty] {
        try! JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// Leaf component builder (no children — the PlaceholderLabel path).
    private func leaf(text: String, _ json: String) -> IRComponent {
        IRComponent(id: "own", name: "OwnershipProbe",
                    properties: props(json),
                    selectors: nil, media: nil, children: nil, slot: nil,
                    text: text, pseudos: nil, meta: nil)
    }

    /// The label's measurement face in the TEST bundle: PlaceholderLabel
    /// probes registered Inter first and falls back to the system face —
    /// mirror that fallback so geometry predictions match the render
    /// (same pattern as IOSTextLaneWave5Tests.labelFont).
    private func labelFont(size: CGFloat) -> UIFont {
        UIFont(name: "Inter", size: size) ?? .systemFont(ofSize: size, weight: .regular)
    }

    /// Rasterize a leaf through the production renderer and return, per
    /// buffer row, the LONGEST CONTIGUOUS painted run (alpha > ~0.24)
    /// plus the global painted x-extent — the same "solid horizontal
    /// run" detector used to measure the Chromium captures, so the
    /// render proofs assert the identical signature.
    @MainActor
    private func rowRuns(_ component: IRComponent,
                         canvas: CGSize = CGSize(width: 300, height: 120))
        throws -> (runs: [Int], minX: Int, maxX: Int, maxAlpha: UInt8) {
        // Fixed canvas so the buffer geometry is deterministic.
        let view = ComponentRenderer(component: component)
            .frame(width: canvas.width, height: canvas.height,
                   alignment: .topLeading)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1   // one buffer pixel per point
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        let data = try XCTUnwrap(cg.dataProvider?.data as Data?, "no bitmap data")
        let bpr = cg.bytesPerRow, bpp = cg.bitsPerPixel / 8
        // Alpha channel position depends on the buffer's pixel order.
        let alphaFirst = cg.alphaInfo == .premultipliedFirst || cg.alphaInfo == .first
        var runs = [Int](repeating: 0, count: cg.height)
        var minX = Int.max, maxX = -1
        var maxAlpha: UInt8 = 0
        for y in 0..<cg.height {
            var run = 0
            for x in 0..<cg.width {
                let idx = y * bpr + x * bpp + (alphaFirst ? 0 : 3)
                // Painted = alpha above the wave-4 smoke threshold.
                if idx < data.count, data[idx] > 60 {
                    run += 1
                    runs[y] = max(runs[y], run)
                    minX = min(minX, x); maxX = max(maxX, x)
                    maxAlpha = max(maxAlpha, data[idx])
                } else {
                    run = 0
                }
            }
        }
        return (runs, minX, maxX, maxAlpha)
    }

    /// Rows whose longest contiguous run spans ≥90% of the painted
    /// extent — decoration bands are the ONLY full-width continuous
    /// rows when the probe text contains a space (glyph rows break at
    /// the word gap, exactly like the capture measurement).
    private func bandRows(_ p: (runs: [Int], minX: Int, maxX: Int, maxAlpha: UInt8)) -> [Int] {
        // Painted extent (the run's inked width, both glyphs + bands).
        let extent = p.maxX - p.minX + 1
        // ≥90% continuous = a decoration band, not a glyph row.
        return p.runs.enumerated().filter { $0.element >= Int(0.9 * Double(extent)) }
            .map(\.offset)
    }

    // MARK: - A1. The measured Chromium geometry oracle

    /// The capture-derived rows at 22px Inter (float ascent
    /// 0.96875 × 22 = 21.3125): content top y=20, baseline y=41;
    /// underline band rows 43-44, line-through 33-34, overline 18-19,
    /// all 2px thick. The pins below are those rows expressed in the
    /// overlay's line-top-relative space (subtract the content top).
    func testMeasuredGeometryReproducedAt22px() {
        // Inter's ascent fraction × 22 — the anchor the overlay reads
        // from the render face (measurementUIFont.ascender).
        let ascent: CGFloat = 21.3125
        // Thickness: the captures' 2px bands (round(22/11) = 2).
        XCTAssertEqual(DecorationMetrics.autoThickness(fontSizePx: 22), 2)
        // Underline top: capture row 43 − content top 20 = 23.
        XCTAssertEqual(DecorationMetrics.underlineTop(ascentPx: ascent, fontSizePx: 22), 23)
        // Line-through top: capture row 33 − content top 20 = 13.
        XCTAssertEqual(DecorationMetrics.lineThroughTop(ascentPx: ascent, fontSizePx: 22), 13)
        // Overline top: capture row 18 − content top 20 = −2 (the band
        // hangs ABOVE the line-box top; its bottom sits at the top).
        XCTAssertEqual(DecorationMetrics.overlineTop(fontSizePx: 22), -2)
    }

    /// The fractions scale with font size (not hardcoded to 22): at the
    /// 16px web-body default the thickness floors to Chrome's classic
    /// 1px and the offsets shrink proportionally.
    func testGeometryScalesWithFontSize() {
        // round(16/11) = 1 — Chrome's default underline at body size.
        XCTAssertEqual(DecorationMetrics.autoThickness(fontSizePx: 16), 1)
        // Underline gap 16·2/22 = 1.45 → ascent 15 + 1.45 rounds to 16.
        XCTAssertEqual(DecorationMetrics.underlineTop(ascentPx: 15, fontSizePx: 16), 16)
        // Strike raise 16·8/22 = 5.8 → 15 − 5.8 rounds to 9.
        XCTAssertEqual(DecorationMetrics.lineThroughTop(ascentPx: 15, fontSizePx: 16), 9)
        // Overline = −thickness at every size.
        XCTAssertEqual(DecorationMetrics.overlineTop(fontSizePx: 16), -1)
    }

    // MARK: - A2. Style gating bridges

    /// text-decoration-style rides TextConfig so the label can restrict
    /// the owned pass to `solid` (patterned styles keep the built-ins).
    func testDecorationStyleBridgesToTextConfig() {
        // Bare-string wire shape pinned by TypographyTests ("dashed").
        let dashed = StyleBuilder.build(from: props(
            #"[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"TextDecorationStyle","data":"dashed"}]"#))
        XCTAssertEqual(dashed.text.decorationStyle, .dashed,
                       "patterned style must reach the ownership gate")
        XCTAssertTrue(dashed.text.underline, "the line flag still bridges")
        // No declaration → the initial `solid` (css-text-decor-3 §2.3),
        // which is the owned path.
        let plain = StyleBuilder.build(from: props(
            #"[{"type":"TextDecorationLine","data":["UNDERLINE"]}]"#))
        XCTAssertEqual(plain.text.decorationStyle, .solid)
    }

    // MARK: - A3. Executed render proofs (owned bands, no double-draw)

    /// Owned underline: EXACTLY one 2px continuous band at the measured
    /// offset. "on on" has a word gap (glyph rows can't span ≥90%
    /// continuously) and no descenders (nothing else paints below the
    /// baseline), so the band rows are unambiguous. A count above the
    /// auto thickness would mean the platform built-in ALSO drew (at
    /// its own offset) — the double-draw the suppressor exists to stop.
    @MainActor
    func testOwnedUnderlineBandGeometry() throws {
        let json = """
            [{"type":"FontSize","data":{"px":22.0}},
             {"type":"TextDecorationLine","data":["UNDERLINE"]}]
            """
        let p = try rowRuns(leaf(text: "on on", json))
        let bands = bandRows(p)
        // The render face the label measured/rendered with (test bundle
        // fallback = system face, same probe as measurementUIFont).
        let font = labelFont(size: 22)
        // Expected band top: label padding (4) + the measured offset
        // from the text top. ±1 row tolerance for buffer rounding.
        let expectedTop = 4 + Int(DecorationMetrics.underlineTop(
            ascentPx: font.ascender, fontSizePx: 22))
        let thickness = Int(DecorationMetrics.autoThickness(fontSizePx: 22))
        XCTAssertEqual(bands.count, thickness,
            "exactly one owned band (rows \(bands)) — more rows = built-in double-draw")
        XCTAssertEqual(Double(bands.min() ?? -99), Double(expectedTop), accuracy: 1,
            "band must start at the measured underline offset")
    }

    /// Owned line-through: same signature mid-glyph — the strike fills
    /// the word gap, so its rows are the only ≥90%-continuous ones.
    @MainActor
    func testOwnedLineThroughBandGeometry() throws {
        let json = """
            [{"type":"FontSize","data":{"px":22.0}},
             {"type":"TextDecorationLine","data":["LINE_THROUGH"]}]
            """
        let p = try rowRuns(leaf(text: "on on", json))
        let bands = bandRows(p)
        // Same face + offset math as the overlay (see underline test).
        let font = labelFont(size: 22)
        let expectedTop = 4 + Int(DecorationMetrics.lineThroughTop(
            ascentPx: font.ascender, fontSizePx: 22))
        let thickness = Int(DecorationMetrics.autoThickness(fontSizePx: 22))
        XCTAssertEqual(bands.count, thickness,
            "exactly one owned band (rows \(bands)) — more rows = built-in double-draw")
        XCTAssertEqual(Double(bands.min() ?? -99), Double(expectedTop), accuracy: 1,
            "band must start at the measured line-through offset")
    }

    /// Patterned styles keep the platform built-in: a DASHED underline
    /// must NOT paint the owned solid band (the ≥90% continuous-run
    /// signature) — the built-in's dash pattern breaks the run.
    @MainActor
    func testDashedUnderlineKeepsBuiltIn() throws {
        let json = """
            [{"type":"FontSize","data":{"px":22.0}},
             {"type":"TextDecorationLine","data":["UNDERLINE"]},
             {"type":"TextDecorationStyle","data":"dashed"}]
            """
        let p = try rowRuns(leaf(text: "on on", json))
        // No solid full-width band anywhere: the owned pass stood down
        // and the dashed built-in (broken runs) rendered instead.
        XCTAssertTrue(bandRows(p).isEmpty,
            "dashed style must not draw the owned solid band")
    }

    // MARK: - B1. currentColor bottom-out classification

    /// The bottom-out fires ONLY for `color: currentColor` with no
    /// ancestor Color in the inherited channel (wire literal =
    /// {"original":"currentColor"}, the shape IOSTextLaneTests pins).
    func testCurrentColorBottomsOutClassification() {
        let current = props(#"[{"type":"Color","data":{"original":"currentColor"}}]"#)
        let ancestor = props(#"[{"type":"Color","data":{"srgb":{"r":1.0,"g":0.0,"b":0.0},"original":"red"}}]"#)
        // currentColor + empty chain → bottoms out.
        XCTAssertTrue(InheritedText.currentColorBottomsOut(own: current, inherited: []))
        // An ancestor Color resolves it — no bottom-out (the inherited
        // value flows into the merged list instead).
        XCTAssertFalse(InheritedText.currentColorBottomsOut(own: current, inherited: ancestor))
        // A static own color is not currentColor.
        XCTAssertFalse(InheritedText.currentColorBottomsOut(own: ancestor, inherited: []))
        // No color declaration at all → the legacy contrast-pick path.
        XCTAssertFalse(InheritedText.currentColorBottomsOut(own: [], inherited: []))
    }

    // MARK: - B2. The default text color is the opaque harness body color

    /// Stage contract: the web-harness body sets `color: #eee` on
    /// #1a1a2e, so an unresolvable currentColor computes to opaque
    /// 238/255 on web. The runtime default must match within 1/255 and
    /// be FULLY OPAQUE — the 70%-alpha contrast pick blends to ~171
    /// gray on the dark stage (the 0.856 divergence the gate measured).
    func testDefaultTextColorIsOpaqueBodyColor() {
        // Resolve the SwiftUI color to concrete sRGB components.
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(InheritedText.defaultTextColor).getRed(&r, green: &g, blue: &b, alpha: &a)
        // Opaque — the whole point of the fix.
        XCTAssertEqual(a, 1.0, "default text color must be opaque")
        // #eee = 238/255; Color(white: 0.93) = 237.15/255 → within 1.
        XCTAssertEqual(r * 255, 238, accuracy: 1)
        XCTAssertEqual(g * 255, 238, accuracy: 1)
        XCTAssertEqual(b * 255, 238, accuracy: 1)
    }

    // MARK: - B3. Executed render proof of the bottom-out

    /// A bottomed-out currentColor leaf paints OPAQUE glyphs (max alpha
    /// ~255); the undeclared-color control stays on the 70%-alpha
    /// contrast pick (max alpha ≈ 179). The gap between the two is the
    /// pixel difference the device gate measured against web's #eee.
    @MainActor
    func testCurrentColorLeafRendersOpaqueDefault() throws {
        let current = leaf(text: "Xx",
            #"[{"type":"Color","data":{"original":"currentColor"}},{"type":"FontSize","data":{"px":22.0}}]"#)
        let control = leaf(text: "Xx",
            #"[{"type":"FontSize","data":{"px":22.0}}]"#)
        // Bottomed-out glyphs reach full opacity in their interiors.
        let currentAlpha = try rowRuns(current).maxAlpha
        XCTAssertGreaterThanOrEqual(currentAlpha, 250,
            "currentColor bottom-out must paint the OPAQUE default text color")
        // The no-declaration control keeps the subtle 70% pick
        // (round(0.7 × 255) = 179 at full glyph coverage).
        let controlAlpha = try rowRuns(control).maxAlpha
        XCTAssertLessThanOrEqual(controlAlpha, 200,
            "undeclared color must keep the legacy contrast pick")
    }
}
