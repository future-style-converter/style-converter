//
//  OpacityExtractor.swift
//  StyleEngine/color — Phase 4.
//
//  Reads the `Opacity` property. IR shape (Phase-4 findings):
//    Opacity.data = { "alpha": <0..1 double>, "original": {...} }
//  We only care about `alpha` — the IR has already folded percentage
//  and clamp behaviour for us.
//

// Foundation — no SwiftUI at extract time.
import Foundation

// Registered types this extractor owns.
enum OpacityProperty {
    static let names: [String] = ["Opacity"]
}

enum OpacityExtractor {

    // Single-pass extractor. Returns nil when no `Opacity` property exists
    // so the applier is a no-op (vs painting `.opacity(1.0)` which still
    // forces a compositing group).
    static func extract(from properties: [IRProperty]) -> OpacityConfig? {
        // Find the last `Opacity` in the list — last-wins like CSS.
        var alpha: Double? = nil
        for prop in properties where prop.type == "Opacity" {
            alpha = readAlpha(prop.data)
        }
        // No alpha parsed → no config, applier is identity.
        guard let a = alpha else { return nil }
        return OpacityConfig(alpha: a)
    }

    // Parse the `alpha` field from the IR blob. Defensive against both
    // the documented object shape and stray bare-number IR (older outputs
    // used a raw float). Returns nil when the shape is malformed so the
    // applier can skip rather than paint zero.
    private static func readAlpha(_ value: IRValue) -> Double? {
        switch value {
        case .object(let o):
            // Preferred shape: {alpha: 0..1}.
            if let a = o["alpha"]?.doubleValue { return clamp(a) }
            // Occasional fallback: {value: 0..1} — some callers still use
            // the ValueExtractors numeric branch.
            if let v = o["value"]?.doubleValue { return clamp(v) }
            return nil
        case .double(let d): return clamp(d)
        case .int(let i):    return clamp(Double(i))
        default:             return nil
        }
    }

    // Mirror CSS's spec clamp just in case the upstream pipeline leaks a
    // value outside [0, 1]. Cheap and defensive.
    private static func clamp(_ d: Double) -> Double {
        if d < 0 { return 0 }
        if d > 1 { return 1 }
        return d
    }
}
