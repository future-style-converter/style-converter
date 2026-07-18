//
//  FontStyleExtractor.swift
//  StyleEngine/typography/font — Phase 6; oblique-angle decode wave 6.
//
//  Live wire shapes (FontStylePropertyParser.kt, verified against the
//  running converter on fixtures/properties/typography/font-style.json):
//    "normal" | "italic" | "oblique"   — bare keyword strings
//    {"oblique":{"deg":N,…}}           — `oblique <angle>`; the converter
//                                        normalizes ALL angle units
//                                        (deg/rad/grad/turn — grad
//                                        suffix-order bug fixed) to a
//                                        numeric "deg", clamps to
//                                        css-fonts-4 §2.4's [-90, 90]
//                                        band, and rejects the whole
//                                        declaration on an unparseable
//                                        angle, so a well-formed wire
//                                        always carries a real "deg"
//                                        (non-deg sources add an
//                                        "original" key we ignore).
//
//  Mapping: css-fonts-4 §2.5 gives bare `oblique` a 14deg default, and
//  14deg is also where Chromium's SYNTHETIC oblique engages on the
//  roman-only harness Inter — the wave-6 web captures show one FIXED
//  skew (Skia textSkewX -0.25 ≈ 14deg): `oblique 14deg`/`18deg` render
//  pixel-identical to `italic`, while `0deg`/`-10deg`/`11.46deg` render
//  pixel-identical to `normal`. So the italic flag flips at deg ≥ 14 —
//  a per-angle skew would slant text the reference leaves upright.
//

import Foundation

enum FontStyleProperty { static let name = "FontStyle" }

enum FontStyleExtractor {
    /// Slant (degrees) at which an `oblique <angle>` request becomes the
    /// italic appearance — css-fonts-4 §2.5's default oblique angle,
    /// empirically Chromium's synthesis cutoff (see file header).
    static let obliqueSlantThresholdDeg: Double = 14.0

    static func extract(from properties: [IRProperty]) -> FontStyleConfig? {
        var cfg = FontStyleConfig()
        var touched = false
        for prop in properties where prop.type == FontStyleProperty.name {
            touched = true
            // Object shape first: `oblique <angle>` → threshold at 14deg.
            if case .object(let outer) = prop.data,
               case .object(let oblique)? = outer["oblique"] {
                // Missing/non-numeric "deg" is a malformed payload —
                // unreachable from the fixed wire (the converter rejects
                // unparseable angles outright), but pinned anyway: drop to
                // upright, matching Compose's conservative convention
                // (TextStyleApplier.extractFontStyle returns null), instead
                // of the old guess-14deg default that silently invented an
                // italic appearance for a slant nobody declared.
                guard let deg = oblique["deg"]?.doubleValue else { continue }
                // Valid degrees → threshold map (see file header).
                cfg.italic = deg >= obliqueSlantThresholdDeg
                continue
            }
            // Keyword path — strings or {keyword:…} blobs via extractKeyword.
            let kw = ValueExtractors.extractKeyword(prop.data)?.lowercased() ?? ""
            // Bare `oblique` = `oblique 14deg` (§2.5 default) → italic
            // appearance, matching web capture 039 == 038 pixel-identical.
            cfg.italic = (kw == "italic" || kw == "oblique")
        }
        return touched ? cfg : nil
    }
}
