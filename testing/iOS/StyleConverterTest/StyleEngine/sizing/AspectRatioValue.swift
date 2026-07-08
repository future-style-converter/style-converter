//
//  AspectRatioValue.swift
//  StyleEngine/sizing — Phase 3.
//
//  Canonical representation of CSS `aspect-ratio`. The IR is *not* a
//  LengthValue — it has four distinct shapes per the fixture agent:
//    1. "16/9"   → { ratio: { w:16, h:9 }, normalizedRatio: 1.777 }
//    2. "1.5"    → { ratio: { value: 1.5 }, normalizedRatio: 1.5 }
//    3. "auto"   → bare string "auto"
//    4. "auto 16/9" → { ratio: { auto:true, w:16, h:9 }, normalizedRatio: 1.777 }
//
//  The Phase 1 primitives therefore can't represent aspect-ratio
//  directly; a dedicated value type plus extractor lives here.
//

// Foundation for Double arithmetic; nothing more.
import Foundation

// Single resolved aspect ratio. Negative/zero ratios are filtered out by
// the extractor because SwiftUI's `.aspectRatio` treats them as no-ops
// and we want nil-propagation instead.
struct AspectRatioValue: Equatable {
    // Resolved width / height multiplier. 0 when `isAuto && no fallback`.
    let ratio: Double
    // True when the CSS value was `auto` (optionally with a ratio fallback).
    // The SizeApplier uses this to decide whether to attach `.aspectRatio`
    // at all — with `auto` the renderer should defer to natural sizing.
    let isAuto: Bool
}

// Single dispatch routine. Returns nil when the IR is absent or a shape
// we can't interpret — the applier treats nil as "do not attach".
enum AspectRatioExtractor {

    // Entry point — called from SizeExtractor.
    static func extract(_ data: IRValue?) -> AspectRatioValue? {
        // Nil in → nil out (no property present).
        guard let data = data else { return nil }

        // Shape 3: bare string "auto". Collapses the whole property to
        // the natural ratio, so `isAuto=true` and `ratio=0`.
        if case .string(let s) = data, s.lowercased() == "auto" {
            return AspectRatioValue(ratio: 0, isAuto: true)
        }

        // Shapes 1/2/4: object form. All share `normalizedRatio` at the
        // top level when the converter can resolve the numeric.
        guard case .object(let o) = data else { return nil }

        // Did the CSS carry the `auto` token alongside an explicit ratio?
        let isAuto = o["ratio"]?.objectValue?["auto"]?.boolValue == true

        // Prefer the pre-normalised ratio — the Kotlin side already did
        // the division and handles edge cases (w/0 etc).
        if let n = o["normalizedRatio"]?.doubleValue, n > 0 {
            return AspectRatioValue(ratio: n, isAuto: isAuto)
        }

        // Fallback: compute from `ratio.w` / `ratio.h` ourselves. Matches
        // shape 1; shape 2 uses `ratio.value` which we also accept.
        if let inner = o["ratio"]?.objectValue {
            if let v = inner["value"]?.doubleValue, v > 0 {
                return AspectRatioValue(ratio: v, isAuto: isAuto)
            }
            if let w = inner["w"]?.doubleValue,
               let h = inner["h"]?.doubleValue,
               h > 0 {
                return AspectRatioValue(ratio: w / h, isAuto: isAuto)
            }
        }

        // Unrecognised shape — propagate nil so the applier is a no-op.
        return nil
    }
}
