//
//  IOSTextLaneTests.swift
//  StyleConverterRuntimeTests
//
//  Lane IOS-TEXT pins — the seven pixel-diagnosed iOS text-pipeline
//  bugs plus the currentColor color channel:
//    1. Greedy line breaking (GreedyLineBreaker: algorithm + the
//       ImageRenderer smoke on the multi-line pre-broken path).
//    2. word-spacing renders (aggregate → TextConfig bridge).
//    3. letter/word-spacing em/rem resolution (bogus px:0.0 wire).
//    4. font-size em/rem/%/smaller/larger (DynamicValueResolver pass 0).
//    5. css-fonts-4 §5.2 face pick at 600/800 (FontFaceMatcher).
//    6. text-transform: capitalize (string rewrite).
//    7. TextDecorationLine underscore-token normalization.
//    C. `color: currentColor` resolves through the inheritance channel.
//
//  Every wire-shape literal below is copied from LIVE converter output
//  (out/verify-fidelity/typography.combos.ir.json and the serializer
//  sources) — not hand-invented shapes.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class IOSTextLaneTests: XCTestCase {

    // MARK: - Helpers

    /// Decode an [IRProperty] from inline JSON — the production decode
    /// path (same convention as DynamicValuesTests).
    private func props(_ json: String) -> [IRProperty] {
        try! JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    // MARK: - 7. Decoration-line token normalization

    /// The v2 wire ships enum names (`["LINE_THROUGH"]`, live output);
    /// lowercasing alone made "line_through" miss the "line-through"
    /// case and silently drop the strikethrough.
    func testDecorationLineEnumTokensNormalize() {
        // Array-of-enum-names shape — the live wire form.
        let cfg = TextDecorationLineExtractor.extract(from: props(
            #"[{"type":"TextDecorationLine","data":["LINE_THROUGH"]}]"#))
        XCTAssertEqual(cfg?.lineThrough, true, "LINE_THROUGH must map to line-through")
        // Mixed string form with an underscore token.
        let cfg2 = TextDecorationLineExtractor.extract(from: props(
            #"[{"type":"TextDecorationLine","data":"underline LINE_THROUGH"}]"#))
        XCTAssertEqual(cfg2?.underline, true)
        XCTAssertEqual(cfg2?.lineThrough, true)
        // NONE keeps every flag off (no false positives from the fix).
        let cfg3 = TextDecorationLineExtractor.extract(from: props(
            #"[{"type":"TextDecorationLine","data":["NONE"]}]"#))
        XCTAssertEqual(cfg3?.lineThrough, false)
        XCTAssertEqual(cfg3?.underline, false)
    }

    // MARK: - 6. capitalize

    /// css-text-3 §2.1 word titlecase — mirrors Compose capitalizeWords.
    func testCapitalizeWordsTransform() {
        XCTAssertEqual(TextTransformApplier.capitalizeWords("grumpy wizards vex"),
                       "Grumpy Wizards Vex")
        // Non-first letters are untouched (NOT lowercased).
        XCTAssertEqual(TextTransformApplier.capitalizeWords("hello WORLD"),
                       "Hello WORLD")
        // Double spaces round-trip (empty fragments preserved).
        XCTAssertEqual(TextTransformApplier.capitalizeWords("a  b"), "A  B")
        // Empty string is stable.
        XCTAssertEqual(TextTransformApplier.capitalizeWords(""), "")
    }

    /// The wire enum "CAPITALIZE" flags the string rewrite instead of
    /// the old `.some(nil)`-only mapping (which rendered as `none`).
    func testCapitalizeExtractsAsFlag() {
        guard let cfg = TextTransformExtractor.extract(from: props(
            #"[{"type":"TextTransform","data":"CAPITALIZE"}]"#)) else {
            return XCTFail("capitalize must produce a config")
        }
        XCTAssertTrue(cfg.capitalize)
        // textCase stays "explicit none" (outer present, inner nil) so
        // no environment case transform runs on top of the rewrite.
        if case .some(let inner) = cfg.textCase {
            XCTAssertNil(inner, "capitalize must clear any inherited case transform")
        } else {
            XCTFail("textCase must be explicitly present for capitalize")
        }
        // …and the flag bridges through the aggregate to TextConfig.
        let style = StyleBuilder.build(from: props(
            #"[{"type":"TextTransform","data":"CAPITALIZE"}]"#))
        XCTAssertTrue(style.text.capitalizeWords)
    }

    // MARK: - 3. letter/word-spacing em/rem

    /// Live wire: `{"px":0.0,"original":{"type":"length","original":
    /// {"v":0.25,"u":"REM"}}}` — px carries a bogus 0. em × element
    /// font-size; rem × 16 (harness root).
    func testLetterSpacingEmRemResolution() {
        // em against a 20px element font-size → 0.25 × 20 = 5.
        let agg = TypographyExtractor.extract(from: props("""
            [{"type":"FontSize","data":{"px":20.0}},
             {"type":"LetterSpacing","data":{"px":0.0,"original":{"type":"length","original":{"v":0.25,"u":"EM"}}}}]
            """))
        XCTAssertEqual(agg?.letterSpacingPx, 5.0)
        // rem ignores the element size: 0.25 × 16 = 4.
        let agg2 = TypographyExtractor.extract(from: props("""
            [{"type":"FontSize","data":{"px":20.0}},
             {"type":"LetterSpacing","data":{"px":0.0,"original":{"type":"length","original":{"v":0.25,"u":"REM"}}}}]
            """))
        XCTAssertEqual(agg2?.letterSpacingPx, 4.0)
        // No FontSize → the 16px default basis for em too.
        let agg3 = TypographyExtractor.extract(from: props(
            #"[{"type":"LetterSpacing","data":{"px":0.0,"original":{"type":"length","original":{"v":0.5,"u":"EM"}}}}]"#))
        XCTAssertEqual(agg3?.letterSpacingPx, 8.0)
        // A REAL px value is untouched by the relative lane.
        let agg4 = TypographyExtractor.extract(from: props(
            #"[{"type":"LetterSpacing","data":{"px":1.0,"original":{"type":"length","px":1.0}}}]"#))
        XCTAssertEqual(agg4?.letterSpacingPx, 1.0)
        // Live `normal` shape keeps its keyword in `original` — the
        // bogus px 0.0 must NOT be adopted as declared zero tracking.
        let agg5 = TypographyExtractor.extract(from: props(
            #"[{"type":"LetterSpacing","data":{"px":0.0,"original":"normal"}}]"#))
        XCTAssertNil(agg5?.letterSpacingPx)
    }

    /// Same lane for word-spacing + fix 2: the value must reach
    /// TextConfig (the render bridge that used to be missing).
    func testWordSpacingResolvesAndBridges() {
        // Absolute px: aggregate + TextConfig both see it.
        let style = StyleBuilder.build(from: props(
            #"[{"type":"WordSpacing","data":{"px":8.0,"original":{"type":"length","px":8.0}}}]"#))
        XCTAssertEqual(style.typography?.wordSpacingPx, 8.0)
        XCTAssertEqual(style.text.wordSpacingPx, 8.0, "word-spacing must bridge to the render path")
        // em resolves against the element font-size before bridging.
        let style2 = StyleBuilder.build(from: props("""
            [{"type":"FontSize","data":{"px":20.0}},
             {"type":"WordSpacing","data":{"px":0.0,"original":{"type":"length","original":{"v":0.5,"u":"EM"}}}}]
            """))
        XCTAssertEqual(style2.text.wordSpacingPx, 10.0)
        // `normal` keyword → no override (guard against px-0 adoption).
        let style3 = StyleBuilder.build(from: props(
            #"[{"type":"WordSpacing","data":{"px":0.0,"original":"normal"}}]"#))
        XCTAssertNil(style3.text.wordSpacingPx)
    }

    // MARK: - 4. font-size relative wire shapes

    /// The px-less FontSize shapes (FontSizeSerializer.kt through the
    /// deep-flatten): em/rem lengths, percentages, smaller/larger.
    func testFontSizeWireResolution() {
        // em × inherited basis.
        XCTAssertEqual(DynamicValueResolver.resolveFontSizeWire(
            .object(["original": .object(["type": .string("length"),
                                          "original": .object(["v": .double(1.5), "u": .string("EM")])])]),
            inheritedFontSizePx: 20), 30)
        // rem × 16 regardless of the inherited basis.
        XCTAssertEqual(DynamicValueResolver.resolveFontSizeWire(
            .object(["original": .object(["type": .string("length"),
                                          "original": .object(["v": .double(1.5), "u": .string("REM")])])]),
            inheritedFontSizePx: 20), 24)
        // % of the inherited size (css-fonts-4 §3.2).
        XCTAssertEqual(DynamicValueResolver.resolveFontSizeWire(
            .object(["original": .object(["type": .string("percentage"), "value": .double(120)])]),
            inheritedFontSizePx: 20), 24)
        // smaller/larger: ±1 step ≈ ÷1.2 / ×1.2 (CSS 2.1 §15.7).
        XCTAssertEqual(DynamicValueResolver.resolveFontSizeWire(
            .object(["original": .object(["type": .string("relative"), "keyword": .string("larger")])]),
            inheritedFontSizePx: 20), 24)
        let smaller = try? XCTUnwrap(DynamicValueResolver.resolveFontSizeWire(
            .object(["original": .object(["type": .string("relative"), "keyword": .string("smaller")])]),
            inheritedFontSizePx: 24))
        XCTAssertEqual(smaller ?? -1, 20, accuracy: 1e-9)
        // Static px shapes are authoritative — never re-resolved.
        XCTAssertNil(DynamicValueResolver.resolveFontSizeWire(
            .object(["px": .double(28), "original": .object(["type": .string("length"), "px": .double(28)])]),
            inheritedFontSizePx: 20))
    }

    /// End-to-end through the resolver: the relative FontSize is
    /// REWRITTEN to `{px:N}` so FontSizeExtractor stops reading nil.
    func testResolverRewritesRelativeFontSize() {
        let list = props(
            #"[{"type":"FontSize","data":{"original":{"type":"length","original":{"v":1.5,"u":"EM"}}}}]"#)
        let out = DynamicValueResolver.resolve(properties: list,
                                               variables: VariableStore(definitions: [:]),
                                               calc: CalcEvaluator.EvalContext(),
                                               inheritedFontSizePx: 20)
        XCTAssertEqual(out.count, 1)
        XCTAssertEqual(FontSizeExtractor.extract(from: out)?.px, 30,
                       "em font-size must reach the extractor as resolved pixels")
    }

    // MARK: - 5. §5.2 face pick

    /// The Inter 4-face ladder (400/500/700/900): 600 → 700 and
    /// 800 → 900 — the two picks CoreText's nearest-face rounds DOWN on
    /// (the pixel-verified FontWeight_800 Bold-vs-Black divergence).
    func testFaceMatcherPickHeavySide() {
        let inter = [400, 500, 700, 900]
        XCTAssertEqual(FontFaceMatcher.pick(desired: 600, available: inter), 700)
        XCTAssertEqual(FontFaceMatcher.pick(desired: 800, available: inter), 900)
        // Exact weights stay exact.
        XCTAssertEqual(FontFaceMatcher.pick(desired: 700, available: inter), 700)
        // 400–500 window: ≥ desired up to 500 first (450 → 500).
        XCTAssertEqual(FontFaceMatcher.pick(desired: 450, available: inter), 500)
        // Light side prefers lighter faces first (300 → nothing below →
        // nearest above).
        XCTAssertEqual(FontFaceMatcher.pick(desired: 300, available: inter), 400)
        // Heavy target with nothing above falls back downward.
        XCTAssertEqual(FontFaceMatcher.pick(desired: 950, available: [400, 700]), 700)
        // Empty ladder → honest nil.
        XCTAssertNil(FontFaceMatcher.pick(desired: 400, available: []))
    }

    /// PostScript face-name → weight parsing over the harness faces.
    func testFaceNameWeightParsing() {
        XCTAssertEqual(FontFaceMatcher.weight(ofFaceName: "Inter-Regular", family: "Inter"), 400)
        XCTAssertEqual(FontFaceMatcher.weight(ofFaceName: "Inter-Medium", family: "Inter"), 500)
        XCTAssertEqual(FontFaceMatcher.weight(ofFaceName: "Inter-Bold", family: "Inter"), 700)
        XCTAssertEqual(FontFaceMatcher.weight(ofFaceName: "Inter-Black", family: "Inter"), 900)
        // Bare family name = the Regular face.
        XCTAssertEqual(FontFaceMatcher.weight(ofFaceName: "Inter", family: "Inter"), 400)
        // SemiBold must NOT false-match the "bold" token.
        XCTAssertEqual(FontFaceMatcher.weight(ofFaceName: "Inter-SemiBold", family: "Inter"), 600)
        // Italic faces are a different style axis — never matched.
        XCTAssertNil(FontFaceMatcher.weight(ofFaceName: "Inter-BoldItalic", family: "Inter"))
        // Foreign families never match.
        XCTAssertNil(FontFaceMatcher.weight(ofFaceName: "Helvetica-Bold", family: "Inter"))
    }

    /// The raw numeric weight must survive extraction (bucketing alone
    /// loses the 800-vs-900 distinction the face pick needs).
    func testFontWeightNumericCarry() {
        let cfg = FontWeightExtractor.extract(from: props(
            #"[{"type":"FontWeight","data":800}]"#))
        XCTAssertEqual(cfg?.numeric, 800)
        XCTAssertEqual(cfg?.weight, .heavy)
        // Keyword forms map to their §2.2 numeric equivalents.
        let cfg2 = FontWeightExtractor.extract(from: props(
            #"[{"type":"FontWeight","data":"bold"}]"#))
        XCTAssertEqual(cfg2?.numeric, 700)
        // …and the number bridges through to TextConfig for the label.
        let style = StyleBuilder.build(from: props(
            #"[{"type":"FontWeight","data":800}]"#))
        XCTAssertEqual(style.text.fontWeightNumeric, 800)
    }

    // MARK: - C. currentColor on `color`

    /// css-color-4 §7.3: `color: currentColor` == `color: inherit` — the
    /// own declaration must yield to the inherited Color instead of
    /// blocking it (which sent the leaf to the contrast pick).
    func testCurrentColorOnColorResolvesThroughInheritance() {
        // Live wire shape for the dynamic marker: a bare-string original.
        let own = props(
            #"[{"type":"Color","data":{"original":"currentColor"}},{"type":"FontSize","data":{"px":16.0}}]"#)
        let inherited = props(
            #"[{"type":"Color","data":{"srgb":{"r":1.0,"g":0.0,"b":0.0,"a":1.0}}}]"#)
        // The filter drops ONLY the currentColor declaration.
        let filtered = InheritedText.resolvingCurrentColorOnColor(own)
        XCTAssertEqual(filtered.map(\.type), ["FontSize"])
        // Merged, the ancestor's red flows in and extracts as sRGB.
        let merged = InheritedText.merge(own: filtered, inherited: inherited)
        guard case .srgb(let r, _, _, _)? = ColorExtractor.extract(from: merged)?.foreground else {
            return XCTFail("inherited Color must reach the merged list as paintable sRGB")
        }
        XCTAssertEqual(r, 1.0)
        // A static own color is never touched by the filter.
        let staticOwn = props(
            #"[{"type":"Color","data":{"srgb":{"r":0.0,"g":1.0,"b":0.0,"a":1.0}}}]"#)
        XCTAssertEqual(InheritedText.resolvingCurrentColorOnColor(staticOwn).count, 1)
    }

    // MARK: - 1. Greedy line breaking

    /// The algorithm core with an injected measurer (10pt per char):
    /// the exact push-out divergence from the lane diagnosis — greedy
    /// keeps "vex" on line 1, TextKit pushed it out.
    func testGreedyBreakMatchesChromiumExample() {
        // 10 pt/char fake metric — "Grumpy wizards vex" = 180 fits 190,
        // "+ 0123" = 230 does not.
        let measure: (String) -> CGFloat = { CGFloat($0.count) * 10 }
        XCTAssertEqual(
            GreedyLineBreaker.lines(text: "Grumpy wizards vex 0123",
                                    maxWidth: 190, measure: measure),
            ["Grumpy wizards vex", "0123"],
            "greedy must break AFTER 'vex' (push-out would break before it)")
    }

    /// Overflow + paragraph edge cases: an overlong single word stays
    /// alone on its line (CSS 2.1 §9.5 overflow), hard newlines are
    /// paragraph boundaries, and a fitting run stays on one line.
    func testGreedyBreakEdgeCases() {
        let measure: (String) -> CGFloat = { CGFloat($0.count) * 10 }
        // Overlong word: no break opportunity inside it.
        XCTAssertEqual(
            GreedyLineBreaker.lines(text: "hippopotamus ox", maxWidth: 50, measure: measure),
            ["hippopotamus", "ox"])
        // Hard newline preserved; each paragraph wraps independently.
        XCTAssertEqual(
            GreedyLineBreaker.lines(text: "a b\nc d", maxWidth: 30, measure: measure),
            ["a b", "c d"])
        // Everything fits → single identical line (identity render).
        XCTAssertEqual(
            GreedyLineBreaker.lines(text: "a b", maxWidth: 100, measure: measure),
            ["a b"])
    }

    /// ImageRenderer smoke for the pre-broken multi-line path (the lane's
    /// gating check, adapted from the wave-3 Canvas lesson): a leaf with
    /// real text in a narrow box must rasterize GLYPH pixels spanning
    /// MORE than one line band — proving (a) SwiftUI Text carrying our
    /// hard breaks rasterizes under ImageRenderer and (b) the greedy
    /// path actually produced multiple lines. (The prescribed UILabel/
    /// UIViewRepresentable vehicle is documented by Apple as NOT
    /// rendered by ImageRenderer — that is exactly why the greedy break
    /// is implemented as a pre-broken SwiftUI Text instead.)
    @MainActor
    func testMultiLineRealTextRasterizesUnderImageRenderer() throws {
        // A 100px-wide leaf with a four-word run — far wider than 100px
        // at 16pt in any face, so the greedy breaker must emit ≥ 2 lines.
        let component = IRComponent(
            id: "t1", name: "GreedySmoke",
            properties: props("""
                [{"type":"Width","data":{"type":"length","px":100.0}},
                 {"type":"FontSize","data":{"px":16.0}}]
                """),
            selectors: nil, media: nil, children: nil, slot: nil,
            text: "Grumpy wizards vex 0123", pseudos: nil, meta: nil)
        let view = ComponentRenderer(component: component)
            // Fixed canvas so the buffer geometry is deterministic.
            .frame(width: 140, height: 120, alignment: .topLeading)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1   // one buffer pixel per point
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        // Collect the y-rows containing any non-transparent pixel.
        guard let data = cg.dataProvider?.data as Data? else {
            return XCTFail("no bitmap data")
        }
        let bpr = cg.bytesPerRow, bpp = cg.bitsPerPixel / 8
        let alphaFirst = cg.alphaInfo == .premultipliedFirst || cg.alphaInfo == .first
        var glyphRows: [Int] = []
        for y in 0..<cg.height {
            for x in 0..<cg.width {
                let idx = y * bpr + x * bpp + (alphaFirst ? 0 : 3)
                if idx < data.count, data[idx] > 60 {   // alpha > ~0.24
                    glyphRows.append(y); break
                }
            }
        }
        // (a) The path rasterized at all — the smoke's core assertion.
        XCTAssertFalse(glyphRows.isEmpty, "multi-line text path rendered an empty layer")
        // (b) Painted rows span more than one 16pt line box → at least
        // two lines actually laid out (single-line glyphs span < 20px).
        let span = (glyphRows.max() ?? 0) - (glyphRows.min() ?? 0)
        XCTAssertGreaterThan(span, 20,
                             "expected ≥ 2 rendered lines, got a glyph band of \(span)px")
    }
}
