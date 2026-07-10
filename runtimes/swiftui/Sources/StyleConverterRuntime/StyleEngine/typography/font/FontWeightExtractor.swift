//
//  FontWeightExtractor.swift
//  StyleEngine/typography/font — Phase 6.
//
//  The parser hands us either a numeric weight (100..1000) or a keyword
//  that we bucket into a SwiftUI Font.Weight. The bucket boundaries
//  mirror the legacy StyleBuilder.parseFontWeight helper that this
//  extractor replaces.
//

import SwiftUI

enum FontWeightProperty { static let name = "FontWeight" }

enum FontWeightExtractor {
    static func extract(from properties: [IRProperty]) -> FontWeightConfig? {
        var cfg = FontWeightConfig()
        var touched = false
        for prop in properties where prop.type == FontWeightProperty.name {
            touched = true
            // Numeric path — bucket 100-step ranges to the closest SwiftUI
            // weight. The thresholds match the legacy StyleBuilder impl.
            // Three IR shapes have to flow through here:
            //   • bare numeric  : 700                          (font-weight: 700)
            //   • keyword form  : { weight: 700,               (font-weight: bold)
            //                       original: "bold" }
            //   • plain string  : "bold"                       (older fixtures)
            // `ValueExtractors.extractInt` covers the first; the keyword
            // form's `weight` key isn't in extractInt's lookup list so we
            // fish it out explicitly before falling through to keyword
            // resolution. Without this branch Typography_Weight,
            // Button_Primary, etc. silently rendered .regular on iOS.
            if let n = ValueExtractors.extractInt(prop.data) {
                cfg.weight = bucket(n)
                continue
            }
            if case .object(let o) = prop.data,
               let n = o["weight"]?.intValue {
                cfg.weight = bucket(n)
                continue
            }
            // Keyword path. Swift keywords match CSS exactly when lowercased.
            // Also peek at `original` for the keyword-form IR (the parser
            // stashes the source token there alongside the resolved int).
            let kw: String? = {
                if let k = ValueExtractors.extractKeyword(prop.data) { return k }
                if case .object(let o) = prop.data {
                    return o["original"]?.stringValue
                }
                return nil
            }()
            switch kw?.lowercased() {
            case "bold":    cfg.weight = .bold
            // Fidelity wave 2 — css-fonts-4 §2.2 relative-weight table:
            // against the inherited default of 400, `bolder` computes to
            // 700 (bold) and `lighter` to 100 (ultraLight). The converter
            // flattens no cascade so 400 is always the inherited base for
            // these fixtures; the old .heavy/.light picks over/under-shot
            // the web reference (Typography_C08).
            case "bolder":  cfg.weight = .bold
            case "lighter": cfg.weight = .ultraLight
            case "normal":  cfg.weight = .regular
            default:        cfg.weight = nil   // unknown → inherit
            }
        }
        return touched ? cfg : nil
    }

    // Bucket numeric CSS weights to SwiftUI's 9-step ladder. CSS allows
    // 1-1000 but quantises render-side to 100-step increments anyway.
    private static func bucket(_ n: Int) -> Font.Weight {
        switch n {
        case ..<200: return .ultraLight
        case ..<300: return .thin
        case ..<400: return .light
        case ..<500: return .regular
        case ..<600: return .medium
        case ..<700: return .semibold
        case ..<800: return .bold
        case ..<900: return .heavy
        default:     return .black
        }
    }
}
