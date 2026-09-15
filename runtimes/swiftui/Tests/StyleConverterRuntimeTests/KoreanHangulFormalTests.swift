//
//  KoreanHangulFormalTests.swift
//  Wave 50, lane B4 — the `korean-hangul-formal` counter style.
//
//  The mechanism, the measured cell and the corpus census live in
//  StyleEngine/lists/KoreanHangulFormal.swift. These pins cover three
//  layers so a regression cannot hide in the seam between them:
//    1. the additive expansion itself (css-counter-styles-3 §7.1);
//    2. the keyword table (`ListMarkerResolver.markerType`), which is the
//       exact place the gap was — an unmodelled keyword answers nil and
//       the resolver silently keeps the `<ol>` UA `decimal`;
//    3. the end-to-end marker string, driven by the VERBATIM per-test IR
//       of the one corpus carrier (tools/titan/runs/wave49-final/sections/
//       css-counter-styles/per-test-ir/wpt__css-counter-styles__
//       counter-suffix.json, components `counter-suffix__0__3` and its two
//       `<li>` children, copied byte-for-byte below).
//
//  MUTATION PROOF (run at authoring time, lane B4): deleting the
//  `case "korean-hangul-formal"` row from ListMarkerResolver.markerType
//  turns tests 2 and 3 red (the resolver falls back to `.decimal`, so the
//  markers read "1." / "2." again) while test 1 stays green — and deleting
//  the `.koreanHangulFormal` arm of ListMarkerText.marker fails to compile,
//  which is the strongest form of "cannot regress silently" available for
//  an exhaustive Swift switch.
//

import XCTest
@testable import StyleConverterRuntime

final class KoreanHangulFormalTests: XCTestCase {

    // MARK: - 1. The additive expansion (css-counter-styles-3 §7.1)

    /// The `additive-symbols` table, exercised at every structural shape:
    /// bare units, a place marker with its FORMAL leading digit, a skipped
    /// (zero) digit, the range ends, and both bottom-outs.
    func testExpandCoversTheAdditiveTable() {
        // Units — the two the corpus carrier actually asks for.
        XCTAssertEqual(KoreanHangulFormal.expand(1), "일")
        XCTAssertEqual(KoreanHangulFormal.expand(2), "이")
        XCTAssertEqual(KoreanHangulFormal.expand(9), "구")
        // The FORMAL rule: the digit is written before the place marker,
        // so 10 is 일십 (`10 일십` in the table), never a bare 십.
        XCTAssertEqual(KoreanHangulFormal.expand(10), "일십")
        XCTAssertEqual(KoreanHangulFormal.expand(11), "일십일")
        XCTAssertEqual(KoreanHangulFormal.expand(20), "이십")
        XCTAssertEqual(KoreanHangulFormal.expand(100), "일백")
        XCTAssertEqual(KoreanHangulFormal.expand(1000), "일천")
        // A ZERO digit contributes nothing — 1001 is 일천일, not
        // 일천영백영십일. This is the assertion that fails if the
        // `guard digit > 0` skip is ever removed.
        XCTAssertEqual(KoreanHangulFormal.expand(1001), "일천일")
        // 2026 = 2·1000 + 0·100 + 2·10 + 6 — the hundreds digit is skipped
        // and the tens digit keeps its formal leading symbol.
        XCTAssertEqual(KoreanHangulFormal.expand(2026), "이천이십육")
        // Top of the declared `range: -9999 9999`.
        XCTAssertEqual(KoreanHangulFormal.expand(9999), "구천구백구십구")
        // The `0 영` row is in the table even though the marker path
        // (1-based) never asks for it.
        XCTAssertEqual(KoreanHangulFormal.expand(0), "영")
        // ABOVE the declared range → the decimal spelling
        // (css-counter-styles-3 §3.2 `range`), the same bottom-out
        // armenian/georgian/hebrew take. This one really is §3.2.
        XCTAssertEqual(KoreanHangulFormal.expand(10_000), "10000")
        // -1 is NOT out of range: §7.1 declares `range: -9999 9999`, so
        // the negative half is inside it and a spec-faithful reader would
        // answer `negative: "마이너스 "` + 일. The decimal spelling is
        // this renderer's deliberate UNMODELLED-NEGATIVE fallback (the
        // marker path only ever asks for 1…n — see `expand`'s
        // `- Returns:`), pinned here so the choice cannot change silently.
        XCTAssertEqual(KoreanHangulFormal.expand(-1), "-1")
    }

    // MARK: - 2. The keyword table — where the gap actually was

    func testKeywordTableClaimsKoreanHangulFormal() {
        // The live wire spelling (the converter lowercases and hyphenates).
        XCTAssertEqual(ListMarkerResolver.markerType(fromKeyword: "korean-hangul-formal"),
                       .koreanHangulFormal)
        // The table normalises case and underscores for every keyword; the
        // new row must not be the one exception.
        XCTAssertEqual(ListMarkerResolver.markerType(fromKeyword: "KOREAN_HANGUL_FORMAL"),
                       .koreanHangulFormal)
        // NEGATIVE CONTROL — the documented scope boundary. The two hanja
        // siblings have zero corpus carriers and stay unmodelled, so they
        // keep answering nil (which the resolver reads as "keep the running
        // value"). If a later wave models them, THIS assertion is the one
        // to update, deliberately.
        XCTAssertNil(ListMarkerResolver.markerType(fromKeyword: "korean-hanja-formal"))
        XCTAssertNil(ListMarkerResolver.markerType(fromKeyword: "korean-hanja-informal"))
    }

    // MARK: - 3. End to end on the VERBATIM corpus payload

    /// `counter-suffix`'s fourth `<ol class="kor">` and its two `<li>`s,
    /// byte-shapes copied from the wave49-final per-test IR. The `<ol>`
    /// carries `ListStyleType: "korean-hangul-formal"`; neither `<li>`
    /// carries a baked `meta.markerText` (the counter bake declined this
    /// style), which is exactly why the runtime table has to answer.
    func testCounterSuffixKoreanListResolvesHangulMarkers() throws {
        let ol = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"counter-suffix__0__3-620","name":"counter-suffix__0__3",
         "properties":[
           {"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},
           {"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},
           {"type":"PaddingTop","data":{"px":0}},
           {"type":"PaddingRight","data":{"original":{"v":3,"u":"EM"}}},
           {"type":"PaddingBottom","data":{"px":0}},
           {"type":"PaddingLeft","data":{"original":{"v":3,"u":"EM"}}},
           {"type":"LineHeight","data":{"multiplier":1.5,
             "original":{"type":"percentage","value":150}}},
           {"type":"ListStyleType","data":"korean-hangul-formal"}],
         "meta":{"sourceTag":"ol","role":"ws-after"}}
        """.utf8))
        // The two items declare nothing of their own — the container's
        // `list-style-type` reaches them through the cascade (css-lists-3
        // §3.1, "Inherited: yes"), which is slot 2 of the resolver.
        let item = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"counter-suffix__0__3__0-621","name":"counter-suffix__0__3__0",
         "properties":[{"type":"LineHeight","data":{"multiplier":1.5,
           "original":{"type":"percentage","value":150}}}],
         "text":"foo","meta":{"sourceTag":"li"}}
        """.utf8))
        let cfg = try XCTUnwrap(ListMarkerResolver.resolve(
            parentTag: ol.meta?.sourceTag,
            parentProperties: ol.properties,
            childProperties: item.properties))
        XCTAssertEqual(cfg.type, .koreanHangulFormal)
        // The two marker strings the frozen browser reference paints on
        // rows 7 and 8 of this document ("일, foo" / "이, bar" — ink bands
        // 165–180 and 189–204 of tools/wpt/refs/9b5435e55e0b54a6cd09c1c563
        // 861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/
        // css-counter-styles/counter-suffix.png). The suffix space the
        // descriptor spells `", "` is supplied once by ListMarkerRow.gapPt
        // — see KoreanHangulFormal.suffix for the measured argument.
        XCTAssertEqual(ListMarkerText.marker(index: 0, config: cfg), "일,")
        XCTAssertEqual(ListMarkerText.marker(index: 1, config: cfg), "이,")
        // REGRESSION GUARD: before this lane both of these read "1." / "2."
        // because the unmodelled keyword left the `<ol>` UA default in
        // place. Stating the old answer makes the pin's purpose explicit.
        XCTAssertNotEqual(ListMarkerText.marker(index: 0, config: cfg), "1.")
    }

    /// The painted-symbol table must NOT claim a korean marker: `disc` /
    /// `circle` / `square` are the only shapes ListMarkerSymbol draws, and
    /// a text counter style has to keep its glyphs (css-lists-3 §3.5 — the
    /// marker box paints the counter style's string).
    func testKoreanMarkerKeepsItsGlyphsAndTakesNoPaintedSymbol() {
        XCTAssertNil(ListMarkerSymbol.shape(for: .koreanHangulFormal))
        XCTAssertNil(ListMarkerSymbol.shape(for: .koreanHangulFormal, markerText: "일,"))
    }
}
