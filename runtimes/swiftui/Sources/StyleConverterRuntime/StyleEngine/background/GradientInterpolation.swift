//
//  GradientInterpolation.swift
//  StyleEngine/background — wave 46, lane Y2 (css-images natives).
//
//  The authored <color-interpolation-method> of one gradient
//  (css-images-4 §3.1 `||` clause, css-color-4 §13.2 grammar):
//    <color-interpolation-method> = in [ <rectangular-color-space>
//                                      | <polar-color-space> <hue-interpolation-method>? ]
//  The converter carries it verbatim on the layer as the optional
//  `interp` wire key ("in hsl longer hue", "in oklab", …) — see
//  BackgroundImageProperty.kt. Before this lane the iOS extractor never
//  read the key, so every polar-space gradient in the WPT css-images
//  corpus (increasing/decreasing/longer hue, powerless hue, oklab
//  analogous-components) rendered the legacy sRGB ramp: 10 of the 16
//  wave-45 native-fail cells carry an `interp` clause.
//
//  Parsed here into a closed enum pair so the stop resolver can dispatch
//  on it without re-validating wire text. Mirrors the web runtime's
//  `interpClause` validation table (BackgroundImageExtractor.ts) so the
//  two runtimes accept the same grammar; spaces this runtime cannot
//  convert yet (display-p3 family, a98, prophoto, rec2020) fall back to
//  `.legacy` with a PropertyTracker breadcrumb — never silently.
//

import Foundation

/// One gradient's interpolation recipe. `.legacy` (no clause) keeps the
/// pre-wave-46 non-premultiplied sRGB lerp BYTE-IDENTICAL — every
/// committed gradient baseline is clause-less, and skeptic S4 (wave 46)
/// re-rendered all 103 committed dark-stage fixtures to confirm it:
/// 103/103 identical. The claim is a measurement, not an assumption.
struct GradientInterpolation: Equatable {

    /// The interpolation colour space (css-color-4 §13.2).
    enum Space: Equatable {
        /// No clause authored → the historical sRGB component lerp.
        case legacy
        /// Rectangular spaces (no hue component).
        case srgb, srgbLinear, lab, oklab, xyzD65, xyzD50
        /// Polar spaces (hue arc applies, css-color-4 §13.5).
        case hsl, hwb, lch, oklch

        /// True for the polar spaces — the only ones a hue method binds to.
        var isPolar: Bool {
            switch self {
            case .hsl, .hwb, .lch, .oklch: return true
            default: return false
            }
        }
    }

    /// <hue-interpolation-method> (css-color-4 §13.5). `shorter` is the
    /// grammar default when a polar space carries no explicit method.
    enum HueMethod: Equatable { case shorter, longer, increasing, decreasing }

    var space: Space
    var hue: HueMethod

    /// The clause-less default.
    static let legacy = GradientInterpolation(space: .legacy, hue: .shorter)

    /// Parse the wire text. Returns `.legacy` for absent / unparseable
    /// input (the web twin drops an invalid clause the same way — an
    /// invalid method would invalidate the whole declaration in a
    /// browser, but the converter only emits grammar it validated, so
    /// the only realistic miss here is an UNSUPPORTED space).
    static func parse(_ raw: String?) -> GradientInterpolation {
        guard let raw = raw else { return .legacy }
        let t = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased().split(separator: " ").map(String.init)
        // Only the two legal shapes: `in <space>` / `in <polar> <method> hue`.
        guard t.count == 2 || t.count == 4, t[0] == "in" else { return .legacy }
        guard let space = spaceFor(t[1]) else {
            // Supported by the grammar, not by this runtime's converter
            // table yet — say so once and render the legacy ramp.
            PropertyTracker.logOnce(
                key: "gradient-interp-space:\(t[1])",
                message: "gradient interpolation space '\(t[1])' not implemented on iOS — rendering the legacy sRGB ramp")
            return .legacy
        }
        guard t.count == 4 else { return GradientInterpolation(space: space, hue: .shorter) }
        // A hue tail is only grammatical on a polar space (§13.2).
        guard space.isPolar, t[3] == "hue", let m = hueFor(t[2]) else { return .legacy }
        return GradientInterpolation(space: space, hue: m)
    }

    /// Wire token → space; nil for grammar-valid spaces this runtime
    /// cannot convert (they surface through the logOnce above).
    private static func spaceFor(_ s: String) -> Space? {
        switch s {
        case "srgb":          return .srgb
        case "srgb-linear":   return .srgbLinear
        case "lab":           return .lab
        case "oklab":         return .oklab
        // css-color-4 §10.9: bare `xyz` is an alias of `xyz-d65`.
        case "xyz", "xyz-d65": return .xyzD65
        case "xyz-d50":       return .xyzD50
        case "hsl":           return .hsl
        case "hwb":           return .hwb
        case "lch":           return .lch
        case "oklch":         return .oklch
        default:              return nil
        }
    }

    /// Wire token → hue method.
    private static func hueFor(_ s: String) -> HueMethod? {
        switch s {
        case "shorter":    return .shorter
        case "longer":     return .longer
        case "increasing": return .increasing
        case "decreasing": return .decreasing
        default:           return nil
        }
    }
}
