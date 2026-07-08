//
//  ColorValue.swift
//  StyleConverterTest — StyleEngine/core/types
//
//  Represents every colour shape documented in:
//    examples/primitives/colors-named.json   — named + transparent + currentColor
//    examples/primitives/colors-legacy.json  — #hex3/4/6/8, rgb/rgba, hsl/hsla
//    examples/primitives/colors-modern.json  — hwb/lab/lch/oklab/oklch/color()/color-mix/light-dark/relative
//
//  Static colours carry a pre-resolved `srgb` block. Dynamic colours
//  (color-mix / light-dark / relative / currentColor / var()) deliberately
//  omit `srgb` — Kotlin ColorConversion cannot resolve them without live
//  context. We surface them as `.dynamic(kind, raw)` so platform code can
//  decide its own fallback strategy.
//

import SwiftUI

// Broad bucket of dynamic-color origins. Kept coarse on purpose — callers
// rarely need more than “is this statically paintable?”.
enum DynamicKind {
    case currentColor   // Inherits from text colour — needs a resolve pass.
    case colorMix       // `color-mix(in <space>, <c1>, <c2>)`.
    case lightDark      // `light-dark(<light>, <dark>)`.
    case relative       // `rgb(from base ...)` etc.
    case varFn          // `var(--name)` — caller must consult custom properties.
}

// Sealed enum — `.unknown` covers garbage input, never nil.
enum ColorValue: Equatable {
    case srgb(r: Double, g: Double, b: Double, a: Double)
    case dynamic(kind: DynamicKind, raw: IRValue)
    case unknown

    // Equatable: IRValue isn't trivially comparable, so we only compare the
    // discriminator for dynamic values (identity of content is rarely
    // interesting and always parseable from `raw` when it matters).
    static func == (lhs: ColorValue, rhs: ColorValue) -> Bool {
        switch (lhs, rhs) {
        case (.unknown, .unknown): return true
        case let (.srgb(ar, ag, ab, aa), .srgb(br, bg, bb, ba)):
            return ar == br && ag == bg && ab == bb && aa == ba
        case let (.dynamic(ak, _), .dynamic(bk, _)):
            return ak == bk
        default:
            return false
        }
    }
}

// Entry point — mirrors `extractLength`. Never throws, never returns nil.
// Quirk #5: IRColor has no `srgb` key for dynamic variants.
// Quirk #6: alpha key is `a` on legacy/srgb, `alpha` on modern spaces.
func extractColor(_ value: IRValue?) -> ColorValue {
    guard let value = value, case .object(let o) = value else {
        // Pure `.string(...)` colours (e.g. a bare "red") can also reach us
        // when callers pass the raw CSS. Treat as dynamic currentColor-ish.
        if case .string(let s) = value ?? .null {
            // `transparent` resolves to fully-transparent sRGB; named colours
            // normally already come through with `srgb` filled.
            if s.lowercased() == "transparent" {
                return .srgb(r: 0, g: 0, b: 0, a: 0)
            }
            if s.lowercased() == "currentcolor" {
                return .dynamic(kind: .currentColor, raw: value ?? .null)
            }
        }
        return .unknown
    }

    // Static path: `srgb: { r, g, b, a? }` is present. Alpha uses `a` here.
    if let srgb = o["srgb"]?.objectValue,
       let r = srgb["r"]?.doubleValue,
       let g = srgb["g"]?.doubleValue,
       let b = srgb["b"]?.doubleValue {
        let a = srgb["a"]?.doubleValue ?? 1.0
        return .srgb(r: r, g: g, b: b, a: a)
    }

    // Dynamic path: inspect `original` to classify.
    guard let original = o["original"] else { return .unknown }

    // currentColor arrives as a bare string original.
    if case .string(let s) = original {
        let lc = s.lowercased()
        if lc == "currentcolor" {
            return .dynamic(kind: .currentColor, raw: original)
        }
        // Any other bare-string original without srgb is unresolvable here.
        return .unknown
    }

    // Modern spaces expose a `type` discriminator (quirk from colors-modern.json).
    if case .object(let oo) = original, let t = oo["type"]?.stringValue {
        switch t.lowercased() {
        case "color-mix":  return .dynamic(kind: .colorMix,   raw: original)
        case "light-dark": return .dynamic(kind: .lightDark,  raw: original)
        case "relative":   return .dynamic(kind: .relative,   raw: original)
        case "var":        return .dynamic(kind: .varFn,      raw: original)
        default:           return .unknown // Static modern space without srgb = malformed.
        }
    }

    return .unknown
}

extension ColorValue {
    // SwiftUI bridge — returns nil for dynamic variants so callers can fall
    // back to environment-derived colour (e.g. foreground for currentColor).
    func toSwiftUIColor() -> Color? {
        switch self {
        case .srgb(let r, let g, let b, let a):
            return Color(.sRGB, red: r, green: g, blue: b, opacity: a)
        case .dynamic, .unknown:
            return nil
        }
    }
}
