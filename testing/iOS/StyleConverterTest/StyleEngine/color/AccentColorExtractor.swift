//
//  AccentColorExtractor.swift
//  StyleEngine/color — Phase 4.
//
//  Reads the `AccentColor` property and folds it into the tri-state
//  AccentColorConfig. IR contract (per the Phase 4 brief):
//    {"type":"auto"}  OR
//    {"type":"color","srgb":{r,g,b,a?},"original":"..."}
//

import Foundation

enum AccentColorProperty {
    static let names: [String] = ["AccentColor"]
}

enum AccentColorExtractor {

    // Returns nil when no AccentColor property was present so the applier
    // doesn't need to differentiate "not present" from "present + auto".
    // The tri-state enum itself carries that distinction elsewhere, but
    // the StyleBuilder wiring uses nil to short-circuit the modifier.
    static func extract(from properties: [IRProperty]) -> AccentColorConfig? {
        var result: AccentColorConfig? = nil

        // Last-wins iteration matches CSS cascade behaviour.
        for prop in properties where prop.type == "AccentColor" {
            result = parse(prop.data)
        }

        return result
    }

    // Parse a single IRValue into the tri-state enum. Unknown shapes
    // downgrade to `.inherit` so malformed IR never crashes the render.
    private static func parse(_ value: IRValue) -> AccentColorConfig {
        // Expect an object at the top level.
        guard case .object(let o) = value else { return .inherit }

        // The discriminator: "auto" vs "color".
        let type = o["type"]?.stringValue?.lowercased()

        switch type {
        case "auto":
            // IR says auto → use the platform default. Applier is still
            // identity but we preserve the signal for diagnostics.
            return .auto
        case "color":
            // Re-route through the Phase-1 colour extractor. The `srgb`
            // and `original` keys live at the same level as `type` so
            // we pass the whole object through.
            return .color(extractColor(value))
        default:
            // Unrecognised / missing type = safest fallback is "no-op".
            return .inherit
        }
    }
}
