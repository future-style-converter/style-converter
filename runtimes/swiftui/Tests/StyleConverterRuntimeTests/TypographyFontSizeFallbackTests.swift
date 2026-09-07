//
//  TypographyFontSizeFallbackTests.swift
//  retro R5 (audit A7#5) — the FontFamily-only font-size fallback is CSS
//  `medium` = 16px, not UIKit's 17pt body size.
//
//  TypographyApplier's FontMod (`.custom(name, size:)`) and LineSpacingMod
//  bottomed out at 17 when no `font-size` reached the aggregate; every
//  other site in both runtimes uses 16 (css-fonts-4 §2.5 initial `medium`,
//  resolved by the UA stylesheet to 16px — Blink kDefaultFontSize; Compose
//  DynamicValueResolver.DEFAULT_FONT_SIZE_PX = 16f). The payloads are
//  VERBATIM wave49-final per-test IR components that carry FontFamily and
//  no FontSize: css-text-decor/text-decoration-inset-007 component 1
//  (`times new roman`) and css-writing-modes/bidi-plaintext-br-001
//  component 0 (`Arial`).
//

import XCTest
@testable import StyleConverterRuntime

final class TypographyFontSizeFallbackTests: XCTestCase {

    private func properties(_ component: String) throws -> [IRProperty] {
        try JSONDecoder().decode(IRDocument.self, from: Data("""
        {"irVersion": 2, "minReaderVersion": 2, "components": [\(component)]}
        """.utf8)).components[0].properties
    }

    func testTimesNewRomanOnlyAggregateResolvesToCssMedium16() throws {
        let props = try properties(#"""
        {"id": "wpt__css-text-decor__text-decoration-inset-007__1-081", "name": "wpt__css-text-decor__text-decoration-inset-007__1", "properties": [{"type": "FontFamily", "data": ["times new roman", "serif"]}, {"type": "Color", "data": {"srgb": {"r": 0, "g": 0, "b": 0}, "original": "black"}}], "text": "The underline for \"جلدي brown\" should be offset by 10px to the right, and not interrupted at the direction boundary:", "meta": {"sourceTag": "p", "role": "ws-after"}}
        """#)
        let agg = try XCTUnwrap(TypographyExtractor.extract(from: props))
        // No font-size declared → FontMod / LineSpacingMod compute with the
        // fallback. (On the renderer's text path the Text's own
        // `.font(labelFont)` — ComponentRenderer's `fontSize ?? 16` — wins by
        // the nearest-modifier rule; this pin is the two fallbacks AGREEING,
        // not a claim that FontMod's font is the painted one there.)
        XCTAssertNil(agg.fontSizePx)
        XCTAssertEqual(TypographyApplier.resolvedFontSizePx(agg), 16)
    }

    func testArialOnlyBidiCarrierResolvesToCssMedium16() throws {
        let props = try properties(#"""
        {"id": "wpt__css-writing-modes__bidi-plaintext-br-001__0-412", "name": "wpt__css-writing-modes__bidi-plaintext-br-001__0", "properties": [{"type": "FontFamily", "data": ["Arial"]}, {"type": "LineHeight", "data": {"multiplier": 1, "original": {"type": "number", "value": 1}}}, {"type": "Width", "data": {"type": "length", "px": 90.98}}, {"type": "BorderTopWidth", "data": {"px": 1}}, {"type": "BorderRightWidth", "data": {"px": 1}}, {"type": "BorderBottomWidth", "data": {"px": 1}}, {"type": "BorderLeftWidth", "data": {"px": 1}}, {"type": "BorderTopStyle", "data": "SOLID"}, {"type": "BorderRightStyle", "data": "SOLID"}, {"type": "BorderBottomStyle", "data": "SOLID"}, {"type": "BorderLeftStyle", "data": "SOLID"}, {"type": "BorderTopColor", "data": {"srgb": {"r": 0, "g": 0, "b": 1}, "original": "blue"}}, {"type": "BorderRightColor", "data": {"srgb": {"r": 0, "g": 0, "b": 1}, "original": "blue"}}, {"type": "BorderBottomColor", "data": {"srgb": {"r": 0, "g": 0, "b": 1}, "original": "blue"}}, {"type": "BorderLeftColor", "data": {"srgb": {"r": 0, "g": 0, "b": 1}, "original": "blue"}}, {"type": "UnicodeBidi", "data": "NORMAL"}, {"type": "Height", "data": {"type": "length", "px": 34}}, {"type": "BoxSizing", "data": "BORDER_BOX"}, {"type": "Direction", "data": "LTR"}, {"type": "TextAlign", "data": "LEFT"}, {"type": "Position", "data": "RELATIVE"}], "meta": {"role": "ws-after"}}
        """#)
        let agg = try XCTUnwrap(TypographyExtractor.extract(from: props))
        XCTAssertNil(agg.fontSizePx)
        XCTAssertEqual(TypographyApplier.resolvedFontSizePx(agg), 16)
    }

    func testTheSharedFallbackIsCssMediumAndADeclaredSizeWins() {
        // The one constant both modifiers read.
        XCTAssertEqual(TypographyApplier.fallbackFontSizePx, 16,
                       "CSS medium is 16px (css-fonts-4 §2.5 + UA default), never UIKit's 17")
        // A declared size is untouched by the fallback.
        var agg = TypographyAggregate()
        agg.fontSizePx = 20
        XCTAssertEqual(TypographyApplier.resolvedFontSizePx(agg), 20)
    }
}
