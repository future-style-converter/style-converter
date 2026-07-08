//
//  TimeValue.swift
//  StyleConverterTest — StyleEngine/core/types
//
//  CSS time values. Fixture: examples/primitives/times.json.
//  IR shape: { "ms": <normalized>, "original"?: { "v": <n>, "u": "S"|"MS" } }
//  `original` is omitted when the input was already milliseconds.
//
//  Quirk #7: transition-duration etc. often wrap this in an array. This
//  extractor handles a single IRValue — callers iterate lists.
//

import Foundation

// Single-field struct — always milliseconds.
struct TimeValue: Equatable {
    let milliseconds: Double
}

// Extract one time. Returns nil on malformed input so array callers can skip.
func extractTime(_ value: IRValue?) -> TimeValue? {
    guard let value = value else { return nil }

    if case .object(let o) = value {
        if let ms = o["ms"]?.doubleValue {
            return TimeValue(milliseconds: ms)
        }
        // Fallback to `original` in case the normaliser dropped `ms`.
        if let original = o["original"]?.objectValue,
           let v = original["v"]?.doubleValue,
           let u = original["u"]?.stringValue {
            return TimeValue(milliseconds: convertToMs(v, unit: u))
        }
        return nil
    }
    // Bare numbers treated as ms (unusual but possible).
    if let d = value.doubleValue { return TimeValue(milliseconds: d) }
    return nil
}

// Helper for callers that receive arrays (TransitionDuration et al).
// Keeps the list-handling logic close to the single-value extractor so we
// stay enum-exhaustive without leaking the shape into call sites.
func extractTimes(_ value: IRValue?) -> [TimeValue] {
    guard let value = value else { return [] }
    if case .array(let arr) = value {
        return arr.compactMap(extractTime)
    }
    // Allow a single time — some properties emit a bare object.
    if let one = extractTime(value) { return [one] }
    return []
}

private func convertToMs(_ v: Double, unit: String) -> Double {
    switch unit.uppercased() {
    case "S":  return v * 1000.0
    case "MS": return v
    default:   return v      // Unknown unit — assume already ms.
    }
}
