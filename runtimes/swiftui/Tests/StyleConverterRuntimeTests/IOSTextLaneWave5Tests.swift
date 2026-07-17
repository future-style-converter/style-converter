//
//  IOSTextLaneWave5Tests.swift
//  StyleConverterRuntimeTests
//
//  Lane IOS wave 5 pins — the six skeptic-confirmed text findings:
//    1. text-transform folds into the RENDER string BEFORE the greedy
//       pre-break measures it (uppercase/lowercase no longer ride the
//       post-measurement `.textCase` environment) + executed render
//       proof that an uppercased run breaks where the measurer said.
//    2. word-spacing survives a co-declared letter-spacing (the box
//       `.tracking()` used to suppress the per-space `.kern`) —
//       kern-model unit pins + the skeptic's executed render probe.
//    3. preserved-whitespace modes (pre-wrap/break-spaces) gate the
//       greedy pre-break OFF (css-text-3 §4.1.2 space runs).
//    4. overline renders — segment-geometry unit pins (mirroring
//       Compose's overlineSegments) + an executed render proof that
//       decoration pixels paint ABOVE the glyph cap.
//    5. capitalize titlecases the first letter AFTER leading
//       punctuation (WPT capitalize-031).
//    6. NBSP (U+00A0) is a word separator for word-spacing
//       (css-text-3 §8.1), in the measurer and the render run.
//
//  Wire-shape literals mirror the live converter output pinned by
//  IOSTextLaneTests / FidelityWave2Tests ("PRE_WRAP" enum keywords,
//  ["OVERLINE"] enum arrays, {"px":N,"original":…} length envelopes).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class IOSTextLaneWave5Tests: XCTestCase {

    // MARK: - Helpers

    /// Decode an [IRProperty] from inline JSON — the production decode
    /// path (same convention as IOSTextLaneTests).
    private func props(_ json: String) -> [IRProperty] {
        try! JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// The label's measurement face in the TEST bundle: PlaceholderLabel
    /// probes registered Inter first and falls back to the system face —
    /// mirror that fallback so width predictions match the render.
    private var labelFont: UIFont {
        UIFont(name: "Inter", size: 16) ?? .systemFont(ofSize: 16, weight: .regular)
    }

    /// Rasterize a leaf component through the production renderer and
    /// return the painted-pixel bounding box (alpha > ~0.24, the same
    /// threshold as the wave-4 greedy smoke). Nil = nothing painted.
    @MainActor
    private func paintedBounds(_ component: IRComponent,
                               canvas: CGSize = CGSize(width: 300, height: 120))
        throws -> (minX: Int, maxX: Int, minY: Int, maxY: Int)? {
        // Fixed canvas so the buffer geometry is deterministic.
        let view = ComponentRenderer(component: component)
            .frame(width: canvas.width, height: canvas.height,
                   alignment: .topLeading)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1   // one buffer pixel per point
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        guard let data = cg.dataProvider?.data as Data? else {
            XCTFail("no bitmap data"); return nil
        }
        let bpr = cg.bytesPerRow, bpp = cg.bitsPerPixel / 8
        // Alpha channel position depends on the buffer's pixel order.
        let alphaFirst = cg.alphaInfo == .premultipliedFirst || cg.alphaInfo == .first
        var minX = Int.max, maxX = -1, minY = Int.max, maxY = -1
        for y in 0..<cg.height {
            for x in 0..<cg.width {
                let idx = y * bpr + x * bpp + (alphaFirst ? 0 : 3)
                if idx < data.count, data[idx] > 60 {   // alpha > ~0.24
                    minX = min(minX, x); maxX = max(maxX, x)
                    minY = min(minY, y); maxY = max(maxY, y)
                }
            }
        }
        return maxX >= 0 ? (minX, maxX, minY, maxY) : nil
    }

    /// Leaf component builder (no children — the PlaceholderLabel path).
    private func leaf(text: String, _ json: String) -> IRComponent {
        IRComponent(id: "w5", name: "Wave5Probe",
                    properties: props(json),
                    selectors: nil, media: nil, children: nil, slot: nil,
                    text: text, pseudos: nil, meta: nil)
    }

    // MARK: - 5. capitalize after leading punctuation

    /// WPT capitalize-031: the target is the first TYPOGRAPHIC LETTER
    /// UNIT — leading punctuation is skipped, not "capitalized".
    func testCapitalizeSkipsLeadingPunctuation() {
        XCTAssertEqual(TextTransformApplier.capitalizeWords("(hello world)"),
                       "(Hello World)")
        XCTAssertEqual(TextTransformApplier.capitalizeWords("'quoted' text"),
                       "'Quoted' Text")
        // A digit IS the word's first unit (Blink's segmentation) —
        // titlecasing it is the identity, so "123abc" must NOT become
        // "123Abc".
        XCTAssertEqual(TextTransformApplier.capitalizeWords("123abc"), "123abc")
        // All-punctuation words round-trip verbatim.
        XCTAssertEqual(TextTransformApplier.capitalizeWords("-- a"), "-- A")
        // Pre-wave behaviour unchanged for plain words.
        XCTAssertEqual(TextTransformApplier.capitalizeWords("grumpy wizards vex"),
                       "Grumpy Wizards Vex")
        XCTAssertEqual(TextTransformApplier.capitalizeWords("a  b"), "A  B")
    }

    // MARK: - 1. text-transform folds before measurement

    /// The render-string rewrite: one string for measure AND render.
    func testRenderStringFoldsCaseTransforms() {
        XCTAssertEqual(TextTransformApplier.renderString(
            "abc def", textCase: .uppercase, capitalize: false), "ABC DEF")
        XCTAssertEqual(TextTransformApplier.renderString(
            "ABC Def", textCase: .lowercase, capitalize: false), "abc def")
        XCTAssertEqual(TextTransformApplier.renderString(
            "abc def", textCase: nil, capitalize: true), "Abc Def")
        // No transform → identity.
        XCTAssertEqual(TextTransformApplier.renderString(
            "aBc", textCase: nil, capitalize: false), "aBc")
    }

    /// The wire's UPPERCASE/LOWERCASE keywords must reach TextConfig so
    /// the label rewrites the string itself (finding 1's bridge).
    func testTextCaseBridgesToTextConfig() {
        let upper = StyleBuilder.build(from: props(
            #"[{"type":"TextTransform","data":"UPPERCASE"}]"#))
        XCTAssertEqual(upper.text.textCase, .uppercase)
        let lower = StyleBuilder.build(from: props(
            #"[{"type":"TextTransform","data":"LOWERCASE"}]"#))
        XCTAssertEqual(lower.text.textCase, .lowercase)
        // Explicit `none` and absent both mean "no case rewrite".
        let none = StyleBuilder.build(from: props(
            #"[{"type":"TextTransform","data":"NONE"}]"#))
        XCTAssertNil(none.text.textCase)
        XCTAssertNil(StyleBuilder.build(from: []).text.textCase)
    }

    /// Executed render proof (the skeptic's failure scenario): a box
    /// sized so the LOWERCASE run fits one line while its UPPERCASE
    /// form does not. Pre-fix, the greedy break measured the lowercase
    /// string (one committed line), `.textCase` uppercased it after,
    /// and TextKit re-broke the overflow. Post-fix the measurer sees
    /// the uppercased glyphs and pre-breaks to two lines — the painted
    /// glyph band must span two line boxes while the lowercase control
    /// spans one.
    @MainActor
    func testUppercasePreBreaksWhereMeasured() throws {
        let text = "grumpy wizards"
        // Width the render actually wraps at: content box = declared
        // width; the label subtracts its 4px-per-side breathing inset.
        let lowerW = (text as NSString).size(
            withAttributes: [.font: labelFont]).width
        let upperW = (text.uppercased() as NSString).size(
            withAttributes: [.font: labelFont]).width
        // Sanity: caps really are wider (else the probe proves nothing).
        XCTAssertGreaterThan(upperW, lowerW + 2)
        // Declared width: lowercase fits (avail = w − 8 ≥ lowerW),
        // uppercase overflows (avail < upperW).
        let w = (ceil(lowerW) + 9).rounded()
        let json = """
            [{"type":"Width","data":{"type":"length","px":\(w)}},
             {"type":"FontSize","data":{"px":16.0}},
             {"type":"TextTransform","data":"UPPERCASE"}]
            """
        let upper = try XCTUnwrap(paintedBounds(leaf(text: text, json)))
        // ≥ 2 line boxes: band taller than one 16pt line (~20px).
        XCTAssertGreaterThan(upper.maxY - upper.minY, 20,
                             "uppercase run must pre-break to 2 lines")
        // Control: same box, no transform → single line band.
        let controlJson = """
            [{"type":"Width","data":{"type":"length","px":\(w)}},
             {"type":"FontSize","data":{"px":16.0}}]
            """
        let control = try XCTUnwrap(paintedBounds(leaf(text: text, controlJson)))
        XCTAssertLessThan(control.maxY - control.minY, 21,
                          "lowercase control must stay on one line")
    }

    // MARK: - 2. word-spacing + letter-spacing

    /// The kern-run model (render twin of GreedyLineBreaker.measurer):
    /// ws alone kerns separators only; ws+ls kerns EVERY character with
    /// ls, separators with ls+ws (css-text-3 §8.1/§8.2 both add).
    func testKernedRunModel() throws {
        // Per-character kern values of an AttributedString (nil = none).
        func kerns(_ a: AttributedString) -> [CGFloat?] {
            var out: [CGFloat?] = []
            var idx = a.startIndex
            while idx < a.endIndex {
                let next = a.index(afterCharacter: idx)
                out.append(a[idx..<next].kern)
                idx = next
            }
            return out
        }
        // ws only → space kerned, glyphs untouched (legacy behaviour).
        let wsOnly = try XCTUnwrap(WordSpacingApplier.kernedRun(
            text: "a b", letterSpacingPx: nil, wordSpacingPx: 8))
        XCTAssertEqual(kerns(wsOnly), [nil, 8, nil])
        // ws + ls → EVERY char kerned with ls; the space gets the sum
        // (the box tracking is skipped in this combination, so the kern
        // attribute is the only spacing carrier and must cover both).
        let both = try XCTUnwrap(WordSpacingApplier.kernedRun(
            text: "a b", letterSpacingPx: 2, wordSpacingPx: 8))
        XCTAssertEqual(kerns(both), [2, 10, 2])
        // No word-spacing → nil: letter-spacing stays on box tracking.
        XCTAssertNil(WordSpacingApplier.kernedRun(
            text: "a b", letterSpacingPx: 2, wordSpacingPx: nil))
        // Declared zero ws with no ls → identity, no attributed rebuild.
        XCTAssertNil(WordSpacingApplier.kernedRun(
            text: "a b", letterSpacingPx: nil, wordSpacingPx: 0))
    }

    /// Executed render probe (the skeptic's shape): with letter-spacing
    /// ALSO declared, word-spacing must still widen the painted run by
    /// ~its value. Pre-fix the box `.tracking()` suppressed the space
    /// kern (SwiftUI: tracking overrides kerning) and the delta was ~0.
    @MainActor
    func testWordSpacingRendersUnderLetterSpacing() throws {
        // Probe: ls 2px + ws 20px on a two-word run.
        let probeJson = """
            [{"type":"FontSize","data":{"px":16.0}},
             {"type":"LetterSpacing","data":{"px":2.0,"original":{"type":"length","px":2.0}}},
             {"type":"WordSpacing","data":{"px":20.0,"original":{"type":"length","px":20.0}}}]
            """
        // Control: identical but WITHOUT word-spacing.
        let controlJson = """
            [{"type":"FontSize","data":{"px":16.0}},
             {"type":"LetterSpacing","data":{"px":2.0,"original":{"type":"length","px":2.0}}}]
            """
        let probe = try XCTUnwrap(paintedBounds(leaf(text: "aa aa", probeJson)))
        let control = try XCTUnwrap(paintedBounds(leaf(text: "aa aa", controlJson)))
        // The single internal space must add ≈ 20px of advance. The
        // pre-fix suppression gave a delta of ~0; assert well above
        // antialiasing noise but below 20 + tracking drift.
        let delta = (probe.maxX - probe.minX) - (control.maxX - control.minX)
        XCTAssertGreaterThan(delta, 12,
            "word-spacing must widen the run even when letter-spacing is declared (got \(delta)px)")
    }

    // MARK: - 6. NBSP is a word separator

    /// css-text-3 §8.1: word-spacing applies to NBSP too — measurer lane.
    func testMeasurerAppliesWordSpacingToNBSP() {
        let f = labelFont
        let plain = GreedyLineBreaker.measurer(
            font: f, letterSpacingPx: nil, wordSpacingPx: nil)
        let spaced = GreedyLineBreaker.measurer(
            font: f, letterSpacingPx: nil, wordSpacingPx: 10)
        // One NBSP separator → exactly one 10px advance bump.
        let delta = spaced("a\u{00A0}b") - plain("a\u{00A0}b")
        XCTAssertEqual(delta, 10, accuracy: 0.5,
                       "NBSP must receive the word-spacing kern in measurement")
    }

    /// …and the render run kerns NBSP identically (model parity).
    func testKernedRunAppliesWordSpacingToNBSP() throws {
        let run = try XCTUnwrap(WordSpacingApplier.kernedRun(
            text: "a\u{00A0}b", letterSpacingPx: nil, wordSpacingPx: 10))
        var idx = run.startIndex
        idx = run.index(afterCharacter: idx)   // the NBSP is char #2
        let next = run.index(afterCharacter: idx)
        XCTAssertEqual(run[idx..<next].kern, 10,
                       "NBSP must carry the word-spacing kern in the render run")
    }

    // MARK: - 3. preserved whitespace gates the pre-break

    /// pre-wrap / break-spaces preserve space runs (css-text-3 §4.1.2);
    /// the applier must raise the flag WITHOUT touching noWrap. `pre`
    /// keeps its nowrap semantics and is also preserved-space.
    func testWhiteSpacePreservedModes() {
        // Live wire enum spelling ("PRE_WRAP" → extractor lowercases).
        let preWrap = StyleBuilder.build(from: props(
            #"[{"type":"WhiteSpace","data":"PRE_WRAP"}]"#))
        XCTAssertTrue(preWrap.text.preservesSpaces)
        XCTAssertFalse(preWrap.text.noWrap, "pre-wrap still wraps")
        let breakSpaces = StyleBuilder.build(from: props(
            #"[{"type":"WhiteSpace","data":"BREAK_SPACES"}]"#))
        XCTAssertTrue(breakSpaces.text.preservesSpaces)
        XCTAssertFalse(breakSpaces.text.noWrap)
        let pre = StyleBuilder.build(from: props(
            #"[{"type":"WhiteSpace","data":"PRE"}]"#))
        XCTAssertTrue(pre.text.preservesSpaces)
        XCTAssertTrue(pre.text.noWrap, "pre keeps its nowrap semantics")
        // normal / pre-line collapse spaces → the pre-break stays legal.
        let normal = StyleBuilder.build(from: props(
            #"[{"type":"WhiteSpace","data":"NORMAL"}]"#))
        XCTAssertFalse(normal.text.preservesSpaces)
        let preLine = StyleBuilder.build(from: props(
            #"[{"type":"WhiteSpace","data":"PRE_LINE"}]"#))
        XCTAssertFalse(preLine.text.preservesSpaces,
                       "pre-line collapses space runs (§4.1.1) — no gate")
    }

    // MARK: - 4. overline

    /// The overline flag + decoration color must bridge to TextConfig
    /// (the label overlay's inputs). Wire shapes are LIVE converter
    /// output (wave5-probe run, 2026-07-17): the enum-name array and an
    /// srgb envelope WITHOUT an "a" key (the serializer omits opaque
    /// alpha) + the keyword original.
    func testOverlineBridgesToTextConfig() {
        let style = StyleBuilder.build(from: props("""
            [{"type":"TextDecorationLine","data":["OVERLINE"]},
             {"type":"TextDecorationColor","data":{"srgb":{"r":1.0,"g":0.0,"b":0.0},"original":"red"}}]
            """))
        XCTAssertTrue(style.text.overline, "overline must reach the render config")
        XCTAssertNotNil(style.text.decorationColor,
                        "decoration color must reach the overlay paint")
        // Combined declarations keep the existing flags too.
        let triple = StyleBuilder.build(from: props(
            #"[{"type":"TextDecorationLine","data":["UNDERLINE","OVERLINE","LINE_THROUGH"]}]"#))
        XCTAssertTrue(triple.text.overline)
        XCTAssertTrue(triple.text.underline)
        XCTAssertTrue(triple.text.strikethrough)
        // No decoration → no overlay.
        XCTAssertFalse(StyleBuilder.build(from: []).text.overline)
    }

    /// Segment geometry (wave-5 gate follow-up rename: DecorationMetrics,
    /// formerly OverlineMetrics): thickness max(1, round(fontSize/11)) —
    /// the Chromium-capture rule — one segment per inked line, empties
    /// skipped.
    func testOverlineSegmentGeometry() {
        // 10pt-per-char fake metric, the GreedyLineBreaker test pattern.
        let measure: (String) -> CGFloat = { CGFloat($0.count) * 10 }
        let segs = DecorationMetrics.segments(
            lines: ["abc", "", "de"], fontSizePx: 32, measure: measure)
        // The empty middle line paints nothing (Compose: right ≤ left →
        // null) but the LINE INDEX is preserved so y stays correct.
        XCTAssertEqual(segs.map(\.index), [0, 2])
        XCTAssertEqual(segs.map(\.width), [30, 20])
        // 32px font → 3px thickness (round(32/11) = 3, the empirical
        // Chromium auto rule pinned by the wave-5 gate captures).
        XCTAssertEqual(segs.map(\.thickness), [3, 3])
        // Floor: small sizes still paint a visible 1px line.
        XCTAssertEqual(DecorationMetrics.autoThickness(fontSizePx: 8), 1)
        XCTAssertEqual(DecorationMetrics.autoThickness(fontSizePx: 16), 1)
    }

    /// Executed render proof: with overline declared, decoration pixels
    /// paint ABOVE the glyph cap (the line-box top sits several px above
    /// the tallest glyph row for any text face), so the painted band's
    /// top row must be strictly higher than the no-decoration control.
    @MainActor
    func testOverlinePaintsAboveGlyphs() throws {
        let json = """
            [{"type":"FontSize","data":{"px":16.0}},
             {"type":"TextDecorationLine","data":["OVERLINE"]}]
            """
        let controlJson = #"[{"type":"FontSize","data":{"px":16.0}}]"#
        let over = try XCTUnwrap(paintedBounds(leaf(text: "over", json)))
        let control = try XCTUnwrap(paintedBounds(leaf(text: "over", controlJson)))
        XCTAssertLessThan(over.minY, control.minY,
            "overline must add painted rows above the glyph cap (over \(over.minY) vs control \(control.minY))")
    }
}
