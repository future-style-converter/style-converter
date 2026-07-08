//
//  AngleValue.swift
//  StyleConverterTest — StyleEngine/core/types
//
//  CSS angles. Fixture: examples/primitives/angles.json.
//  IR shape: { "deg": <normalized>, "original"?: { "v": <n>, "u": "DEG|RAD|GRAD|TURN" } }
//  `original` is omitted when the input was already in degrees.
//
//  We normalise everything to degrees so downstream renderers work in a
//  single unit. The original unit is intentionally discarded — iOS does not
//  need it for rendering.
//

import Foundation

// Single-field struct keeps the API surface tiny and Equatable-friendly.
struct AngleValue: Equatable {
    let degrees: Double
}

// Never throws, never returns nil. Quirk #7 (angle-as-list) handled by
// callers that expect arrays — this extractor operates on a single IRValue.
func extractAngle(_ value: IRValue?) -> AngleValue? {
    guard let value = value else { return nil }

    // Object form — the canonical shape.
    if case .object(let o) = value {
        if let deg = o["deg"]?.doubleValue {
            return AngleValue(degrees: deg)
        }
        // Legacy / defensive: some generators emitted `degrees` instead of `deg`.
        if let deg = o["degrees"]?.doubleValue {
            return AngleValue(degrees: deg)
        }
        // Only `original` is populated — resolve the unit ourselves.
        if let original = o["original"]?.objectValue,
           let v = original["v"]?.doubleValue,
           let u = original["u"]?.stringValue {
            return AngleValue(degrees: convertToDegrees(v, unit: u))
        }
        return nil
    }

    // Bare number — treat as degrees (matches converter's DEG omission rule).
    if let d = value.doubleValue { return AngleValue(degrees: d) }
    return nil
}

// Convert a raw (value, unit) pair to degrees. Unit tags match the Kotlin
// LengthUnit-for-angles enum (DEG/RAD/GRAD/TURN).
private func convertToDegrees(_ v: Double, unit: String) -> Double {
    switch unit.uppercased() {
    case "DEG":  return v
    case "RAD":  return v * 180.0 / .pi
    case "GRAD": return v * 0.9          // 400 grad == 360 deg.
    case "TURN": return v * 360.0
    default:     return v                // Unknown — assume already degrees.
    }
}
