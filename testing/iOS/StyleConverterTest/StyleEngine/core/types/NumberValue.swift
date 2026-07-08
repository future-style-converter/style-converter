//
//  NumberValue.swift
//  StyleConverterTest — StyleEngine/core/types
//
//  Unitless numbers. Fixture: examples/primitives/numbers.json.
//  Quirk #8: every numeric property uses a different envelope. No uniform
//  IRNumber extractor exists — we provide one adapter per property instead.
//

import Foundation

// Transparent value wrapper. Property-adapter helpers below return nil when
// the IR shape doesn't match — callers can then fall back to defaults.
struct NumberValue: Equatable {
    let value: Double
}

// Namespace the per-property adapters so their intent is obvious at use-site.
enum NumberExtractors {

    // `Opacity` envelope: { "alpha": 0.5, "original": { "type": "number", "value": 0.5 } }.
    static func opacity(_ v: IRValue?) -> NumberValue? {
        guard let v = v, case .object(let o) = v else { return nil }
        if let a = o["alpha"]?.doubleValue { return NumberValue(value: a) }
        // Defensive fallback — some shorthand paths drop alpha and leave original only.
        if let orig = o["original"]?.objectValue, let val = orig["value"]?.doubleValue {
            return NumberValue(value: val)
        }
        return nil
    }

    // `LineHeight` unitless envelope: { "multiplier": 1.5, "original": ... }.
    // Length-based line-height (e.g. "1.5em", "24px") flows through extractLength instead.
    static func lineHeightMultiplier(_ v: IRValue?) -> NumberValue? {
        guard let v = v, case .object(let o) = v else { return nil }
        if let m = o["multiplier"]?.doubleValue { return NumberValue(value: m) }
        return nil
    }

    // `FlexGrow` envelope: { "value": { "type": "...", "value": 1.0 }, "normalizedValue": 1.0 }.
    // Prefer `normalizedValue` — it's the canonical numeric.
    static func flexGrow(_ v: IRValue?) -> NumberValue? {
        guard let v = v, case .object(let o) = v else { return nil }
        if let nv = o["normalizedValue"]?.doubleValue { return NumberValue(value: nv) }
        // Nested fallback: dig into `value.value` if normalization was skipped.
        if let inner = o["value"]?.objectValue, let iv = inner["value"]?.doubleValue {
            return NumberValue(value: iv)
        }
        return nil
    }

    // `ZIndex` envelope: { "value": 10, "original": { "type": "integer", "value": 10 } }.
    static func zIndex(_ v: IRValue?) -> NumberValue? {
        guard let v = v else { return nil }
        if case .object(let o) = v, let i = o["value"]?.doubleValue {
            return NumberValue(value: i)
        }
        // Bare integer fallback.
        if let d = v.doubleValue { return NumberValue(value: d) }
        return nil
    }

    // `FontWeight` is notoriously a bare integer in the IR (fixture quirk).
    // Accept also the CSS keyword mapping `{ keyword: "bold" }` for safety.
    static func fontWeight(_ v: IRValue?) -> NumberValue? {
        guard let v = v else { return nil }
        if let d = v.doubleValue { return NumberValue(value: d) }
        if case .object(let o) = v {
            if let n = o["value"]?.doubleValue { return NumberValue(value: n) }
            if let kw = o["keyword"]?.stringValue {
                switch kw.lowercased() {
                case "normal":  return NumberValue(value: 400)
                case "bold":    return NumberValue(value: 700)
                case "bolder":  return NumberValue(value: 700)
                case "lighter": return NumberValue(value: 300)
                default:        return nil
                }
            }
        }
        return nil
    }

    // `FontSize` is always length-shaped: { "px": 16, "original": { "type":"length", "px":16 } }.
    // Returning LengthValue (not NumberValue) preserves unit semantics.
    static func fontSize(_ v: IRValue?) -> LengthValue {
        return extractLength(v)
    }
}
