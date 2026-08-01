//
//  LineHeightExtractor.swift
//  StyleEngine/typography/line — Phase 6.
//

import Foundation

enum LineHeightProperty { static let name = "LineHeight" }

enum LineHeightExtractor {
    static func extract(from properties: [IRProperty]) -> LineHeightConfig? {
        var cfg = LineHeightConfig()
        var touched = false
        for prop in properties where prop.type == LineHeightProperty.name {
            touched = true
            // Two shapes reach this extractor: `{ "px": N }` for
            // resolved lengths and `{ "multiplier": N, "original": … }`
            // for unitless multipliers (CSS `line-height: 2`) and
            // percentages. NOTE the converter actually ships lengths on
            // a NESTED wire with no top-level px
            // (`{"original":{"type":"length", …}}`, pinned live) —
            // DynamicValueResolver.resolveLineHeightWire unwraps that
            // to `{px:N}` BEFORE extraction, because em needs the
            // element's own font size (css-values-4 §6.1), a channel
            // only the resolver has. extractPx reads the px form; the
            // multiplier path needs an explicit lookup so it isn't
            // silently dropped.
            // Wave 22 (lane FONT) — flag the DECLARED-`normal` state
            // (css-fonts-4 §4.3: what `font: 92px Arial` resets line-height to,
            // now emitted by the converter's FontExpander). Last-write-wins,
            // like px/multiplier below, so the flag and the number can never
            // describe different declarations.
            //
            // The wire's legacy 1.2 compatibility multiplier is DELIBERATELY
            // still read below for this case: the CSS-correct answer is the
            // face's own metrics, but every committed 327-pair dark-stage
            // capture of `line-height: normal`
            // (fixtures/properties/typography/line-height.json →
            // tools/visual/baseline/iOS__063_Typography_LineHeight.png) was made
            // with the 1.2 box, and this lane cannot re-capture. The natural
            // reading is therefore applied WPT-GATED in
            // ComponentRenderer.effectiveLineHeight, which prefers the flag over
            // the number only under WPT capture.
            cfg.isNormalKeyword = LineHeightNormal.isDeclaredNormal(prop.data)
            cfg.px = ValueExtractors.extractPx(prop.data)
            if cfg.px == nil, case .object(let o) = prop.data,
               let mult = o["multiplier"]?.doubleValue {
                cfg.multiplier = CGFloat(mult)
            }
        }
        return touched ? cfg : nil
    }
}
