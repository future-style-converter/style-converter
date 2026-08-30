//
//  StaticColorMix.swift
//  StyleEngine/color — wave-49 lane A5.
//
//  Static `color-mix()` evaluation (css-color-5 §3). Byte-parallel twin of
//  the Compose `color/StaticColorMix.kt`.
//
//  WHY THIS FILE EXISTS
//  The converter ships a `color-mix()` it will not commit to verbatim:
//  `{"original":{"type":"color-mix","colorSpace":"lch","color1":"currentColor",
//  "percent1":50,"color2":"blue"}}` (verbatim wire in tools/titan/runs/
//  wave48-final/sections/css-color/per-test-ir/
//  wpt__css-color__color-mix-currentcolor-002.json). `extractColor` classifies
//  that `.dynamic(.colorMix, raw:)`, `toSwiftUIColor()` returns nil for it and
//  ColorApplier skips the paint — so on the wave-48 gate iOS rendered SIX of
//  the seven stripes of css-color/color-mix-percents-01 as blank white
//  (ssim 0.9074) and color-mix-currentcolor-001/002 as an empty canvas.
//
//  WHOSE MATH
//  css-color-5 §3.3 defines the result as the css-color-4 §13 INTERPOLATION of
//  the two colours at the normalised weight of the second — the same
//  operation a gradient performs between two stops. So no colour math is
//  written here: the endpoints are handed to `GradientRamp.interpolate`,
//  which already implements §13.4 premultiplication, §13.5 hue-arc selection
//  and the §13.3 powerless-hue carry over every space `GradientColorMath`
//  converts. Gradients and color-mix() therefore cannot disagree about what
//  "in lch" means, on either platform.
//
//  VERIFIED against Chrome 151 and the frozen reference PNGs:
//    color-mix(in lch, green 50%, blue) → lch(37.9230 99.5929 217.8741),
//      sRGB (0, 117, 189) = the exact pixel at (50,50) of
//      tools/wpt/refs/…/css-color/color-mix-currentcolor-002.png;
//    color-mix(in lch, purple 50%, plum 50%) → sRGB (174.71, 91.81, 174.15)
//      against the value color-mix-percents-01 asserts,
//      rgb(68.4898% 36.015% 68.3102%) = (174.65, 91.84, 174.19).
//

import Foundation

enum StaticColorMix {

    /// The `color-mix()` payload inside an IRColor wire value, or nil.
    ///
    /// `extractColor` hands the INNER `original` object over on the
    /// `.dynamic(.colorMix, raw:)` case, so both shapes are accepted: the
    /// full `{original: {...}}` envelope and the unwrapped call object.
    static func payload(_ value: IRValue?) -> [String: IRValue]? {
        guard let obj = value?.objectValue else { return nil }
        // Unwrap the envelope when present; otherwise treat obj as the call.
        let call = obj["original"]?.objectValue ?? obj
        guard call["type"]?.stringValue == "color-mix" else { return nil }
        return call
    }

    /// Evaluate one `color-mix()` payload.
    ///
    /// - Parameter currentColor: the element's own computed `color`,
    ///   substituted for a `currentColor` endpoint per css-color-4 §6.4 —
    ///   the keyword resolves against the element the mix is USED on, which
    ///   is precisely why the converter cannot do this. nil when the chain
    ///   bottomed out; a mix needing it then stays unresolved rather than
    ///   guessing an ink.
    /// - Returns: the mixed colour, or nil when some part is beyond this
    ///   runtime — every such exit is named through PropertyTracker, never
    ///   silent.
    static func resolve(
        _ payload: [String: IRValue],
        currentColor: (r: Double, g: Double, b: Double, a: Double)?
    ) -> ColorValue? {
        // 1. Interpolation space + hue method (css-color-4 §13.2/§13.5).
        // Rebuilding the clause text lets GradientInterpolation.parse stay
        // the single space table shared with the gradient path.
        guard let spaceToken = payload["colorSpace"]?.stringValue else {
            return unsupported("no-space")
        }
        let hueToken = payload["hueMethod"]?.stringValue
        let clause = hueToken.map { "in \(spaceToken) \($0) hue" } ?? "in \(spaceToken)"
        let interp = GradientInterpolation.parse(clause)
        // `.legacy` means the clause was not understood. Mixing in sRGB
        // anyway would paint a plausible-but-wrong colour — the exact failure
        // the corpus's colour veto is too coarse to catch — so bail loudly.
        guard interp.space != .legacy else { return unsupported("space:\(spaceToken)") }

        // 2. Endpoints: `currentColor` is the §6.4 substitution, anything
        // else a CSS literal the converter copied through verbatim.
        guard let c1 = endpoint(payload["color1"], currentColor) else { return unsupported("endpoint1") }
        guard let c2 = endpoint(payload["color2"], currentColor) else { return unsupported("endpoint2") }

        // 3. Percentage normalisation (css-color-5 §3.2).
        let p1raw = payload["percent1"]?.doubleValue
        let p2raw = payload["percent2"]?.doubleValue
        // The grammar is <percentage [0,100]>; out of range invalidates the
        // whole declaration — what WPT color-mix-percents-02's 125% / 9999%
        // stripes assert — so it is NOT clamped into range.
        if let p = p1raw, p < 0 || p > 100 { return unsupported("percent-range") }
        if let p = p2raw, p < 0 || p > 100 { return unsupported("percent-range") }
        // "Both omitted → 50%; one omitted → 100% minus the other."
        let p1 = p1raw ?? (p2raw.map { 100 - $0 } ?? 50)
        let p2 = p2raw ?? (100 - (p1raw ?? 50))
        let sum = p1 + p2
        // "If the sum is zero the function is invalid."
        guard sum > 0 else { return unsupported("percent-zero") }
        // "Sum below 100% → the result's alpha is multiplied by that sum";
        // the mix itself always runs on weights normalised to 1.
        let alphaScale = sum < 100 ? sum / 100 : 1

        // 4. The mix IS a §13 interpolation at t = the second colour's
        // normalised weight (css-color-5 §3.3).
        let mixed = GradientRamp.interpolate(
            GradientApplier.RGBAStop(r: c1.r, g: c1.g, b: c1.b, a: c1.a, loc: 0),
            GradientApplier.RGBAStop(r: c2.r, g: c2.g, b: c2.b, a: c2.a, loc: 0),
            t: p2 / sum, interp: interp)
        return .srgb(
            r: min(max(mixed.r, 0), 1), g: min(max(mixed.g, 0), 1),
            b: min(max(mixed.b, 0), 1), a: min(max(mixed.a * alphaScale, 0), 1))
    }

    /// One `color-mix()` endpoint → sRGB components.
    ///
    /// The wire carries endpoints as the AUTHOR's CSS text (the converter's
    /// `ColorMix` model types both as `String`), so literals go through
    /// `CSSTokenParser.color` — the runtime's existing hex / rgb() / full
    /// named-keyword parser, which is where `plum` (color-mix-percents-01)
    /// and every other CSS colour name already live.
    private static func endpoint(
        _ node: IRValue?,
        _ currentColor: (r: Double, g: Double, b: Double, a: Double)?
    ) -> (r: Double, g: Double, b: Double, a: Double)? {
        guard let raw = node?.stringValue else { return nil }
        // css-color-4 §6.4 — ASCII case-insensitive like every CSS keyword
        // (css-values-4 §4.1).
        if raw.trimmingCharacters(in: .whitespaces).lowercased() == "currentcolor" {
            return currentColor
        }
        guard let c = CSSTokenParser.color(raw) else { return nil }
        return (r: c.r, g: c.g, b: c.b, a: c.a)
    }

    /// Record a refusal and return nil, so an unpainted background is always
    /// traceable to WHICH part of the value this runtime declined.
    private static func unsupported(_ reason: String) -> ColorValue? {
        _ = PropertyTracker.logOnce(
            key: "color-mix:\(reason)",
            message: "color-mix() left unresolved (\(reason)) — background not painted")
        return nil
    }
}
