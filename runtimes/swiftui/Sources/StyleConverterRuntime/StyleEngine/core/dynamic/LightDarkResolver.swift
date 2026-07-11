//
//  LightDarkResolver.swift
//  StyleEngine/core/dynamic — wave 7 (dynamic styling, issue #34).
//
//  light-dark() color resolution (css-color-5 §4.1). The converter
//  ships the value as a dynamic color — no `srgb`, structured original:
//
//    { "original": { "type": "light-dark",
//                    "lightColor": "#ecf0f1", "darkColor": "#111827" } }
//
//  Before wave 7 the iOS engine classified this `.dynamic(.lightDark)`
//  and every applier dropped it as unpaintable. This pass runs at style
//  resolution (after StateResolver's bucket fold, before extraction)
//  and rewrites the shape into the static `{srgb, original}` form the
//  existing color extractors already read — picking the arm from the
//  SAME platform dark-mode signal `prefers-color-scheme` buckets use
//  (spec 06 §4: one surface, one scheme answer).
//
//  The CSS `color-scheme` property (css-color-adjust-1 §3) participates
//  as the per-component override: a component declaring exactly ONE of
//  light/dark pins light-dark() to that arm (the used color scheme
//  cannot be a scheme the component says it does not support); "light
//  dark" and "normal" follow the platform signal.
//

import Foundation

/// Pure property-list rewrite. Stateless; fully XCTest-pinned.
// enum: namespace only (mirrors StateResolver / DynamicValueResolver).
enum LightDarkResolver {

    /// Rewrite every light-dark() color in `properties` to its
    /// scheme-resolved static form. Lists without light-dark values
    /// return identically (single cheap scan, no allocation churn).
    static func resolve(_ properties: [IRProperty],
                        prefersDark: Bool) -> [IRProperty] {
        // Fast path: no light-dark shape anywhere → identity. The whole
        // pre-wave-7 corpus takes this branch.
        guard properties.contains(where: { lightDarkArms($0.data) != nil })
        else { return properties }
        // The scheme answer for THIS component: platform signal,
        // possibly pinned by the component's own color-scheme property.
        let dark = usedDark(properties: properties, prefersDark: prefersDark)
        // Rewrite pass — only light-dark shapes change.
        return properties.map { prop in
            // Not a light-dark color → byte-identical passthrough.
            guard let arms = lightDarkArms(prop.data) else { return prop }
            // Pick the arm the used scheme selects (css-color-5 §4.1).
            let chosen = dark ? arms.dark : arms.light
            // Parse the arm into sRGB via the wave-6 token parser (hex /
            // rgb() / named). Unparseable arm (e.g. oklch) → leave the
            // dynamic shape untouched so downstream stays honest about
            // the drop, and breadcrumb the miss once.
            guard let c = CSSTokenParser.color(chosen) else {
                PropertyTracker.logOnce(
                    key: "light-dark:\(chosen)",
                    message: "light-dark() arm '\(chosen)' is outside the "
                        + "runtime token grammar — value stays dynamic "
                        + "(dropped by appliers)")
                return prop
            }
            // Rebuild the converter's STATIC color shape so extractColor
            // takes its normal srgb path — same rebuild the wave-6
            // var()-color lane performs (DynamicValueResolver).
            return IRProperty(type: prop.type, data: .object([
                "srgb": .object(["r": .double(c.r), "g": .double(c.g),
                                 "b": .double(c.b), "a": .double(c.a)]),
                "original": .string(chosen),
            ]))
        }
    }

    // MARK: - Used scheme

    /// The dark/light answer for one component: the platform signal,
    /// overridden when the component's `color-scheme` declaration pins
    /// exactly one scheme (css-color-adjust-1 §3 — the used scheme must
    /// be one the component supports). Internal so XCTest pins the
    /// override table directly.
    static func usedDark(properties: [IRProperty],
                         prefersDark: Bool) -> Bool {
        // Find the component's ColorScheme declaration, if any (last
        // one wins, mirroring the extractors' last-wins scans).
        guard let data = properties.last(where: { $0.type == "ColorScheme" })?.data,
              // IR shape: { "schemes": ["LIGHT", "DARK", …] } — Kotlin
              // enum names serialize UPPERCASE; compare case-folded.
              let list = data["schemes"]?.arrayValue
        else { return prefersDark }
        // Collect the declared scheme keywords (ignore "only"/"normal"
        // for the membership test — only light/dark pin an arm).
        let schemes = Set(list.compactMap { $0.stringValue?.lowercased() })
        let light = schemes.contains("light")
        let dark = schemes.contains("dark")
        // Exactly one scheme declared → that arm, unconditionally.
        if light != dark { return dark }
        // Both or neither ("normal") → follow the platform signal.
        return prefersDark
    }

    // MARK: - Shape detection

    /// The (light, dark) arm strings when `data` is the converter's
    /// light-dark dynamic-color shape; nil for every other value.
    /// Static colors carry `srgb` and are never touched.
    static func lightDarkArms(_ data: IRValue) -> (light: String, dark: String)? {
        // Must be an object WITHOUT a resolved srgb block…
        guard case .object(let o) = data, o["srgb"] == nil,
              // …whose original is the structured light-dark payload.
              let original = o["original"]?.objectValue,
              original["type"]?.stringValue?.lowercased() == "light-dark",
              let light = original["lightColor"]?.stringValue,
              let dark = original["darkColor"]?.stringValue
        else { return nil }
        return (light, dark)
    }
}
