//
//  CalcSizeValue.swift
//  StyleEngine/sizing — wave 42 (lane W3).
//
//  css-values-5 §11 `calc-size()` typed wire decode + pure arithmetic.
//  Wire shape (converter CalcSizeParser.kt, identical across the Width /
//  MinMax / Max value families):
//    {"type":"calc-size","basis":"auto","factor":1,"offsetPx":20,
//     "original":"calc-size(auto, size + 20px)"}
//
//  Before this decoder existed the shape fell through extractLength to
//  `.unknown` and the declaration was dropped — iOS then sized the box by
//  its intrinsic content alone. MEASURED on the wave41-final iOS captures:
//  calc-size-min-max-sizes-001..006 (`width: calc-size(auto|fit-content,
//  size + 60..80px)` inside a `width: fit-content` parent) painted a ~20px
//  green sliver against the ref's 100×100 green square (wptPass=false at
//  ssim 0.9619 on all six). The +offset arithmetic below is exactly what
//  was missing.
//
//  Only the WIDTH/HEIGHT (preferred size) slots consume this on iOS today.
//  The Min*/Max* slots keep their pre-existing behavior — extractLength
//  returns `.unknown`, SizeApplierResolve maps it to nil (no constraint) —
//  because the six calc-size-flex cells PASS on iOS with precisely that
//  fallback (0.9751..0.9819: the flex item already renders its content-based
//  minimum) and a floor lane would be an unmeasured change to passing cells.
//  That decision is recorded in SizeExtractor.swift's calc-size comment.
//

// Foundation only — keeps the value + math testable without SwiftUI.
import Foundation

// The css-values-5 keyword bases a typed calc-size() can carry. Pure-length
// bases never reach the runtime (the converter evaluates them at parse time).
enum CalcSizeBasis: String, Equatable {
    case auto = "auto"                    // the property's normal auto size
    case minContent = "min-content"       // css-sizing-3 §5.1
    case maxContent = "max-content"       // css-sizing-3 §5.1
    case fitContent = "fit-content"       // css-sizing-3 §5.1 clamp
    case stretch = "stretch"              // css-sizing-4 — treated like auto
    case content = "content"              // css-values-5 — treated like auto
}

// One typed calc-size() value: `factor * <resolved basis> + offsetPx`.
struct CalcSizeValue: Equatable {
    let basis: CalcSizeBasis      // what `size` resolves against
    let factor: CGFloat           // multiplier on the resolved basis
    let offsetPx: CGFloat         // absolute px addend (may be negative)

    // The resolved target for a basis measured as `basisPx`. Floored at
    // zero — css-values-4 §10 clamps negative results on the non-negative
    // sizing value space (`size - 50px` over a small basis must not go
    // negative; SwiftUI treats negative frame sizes as invalid anyway).
    func targetPx(_ basisPx: CGFloat) -> CGFloat {
        max(0, factor * basisPx + offsetPx)
    }

    // Decode the typed wire shape, or nil for anything else. Nil routes the
    // caller to its existing behavior (extractLength), so the decode is
    // purely additive — no value that decodes today can change meaning.
    static func decode(_ data: IRValue?) -> CalcSizeValue? {
        // Only object payloads can carry the discriminated shape.
        guard case .object(let o)? = data else { return nil }
        // Discriminator — the converter always stamps type:"calc-size".
        guard o["type"]?.stringValue == "calc-size" else { return nil }
        // Basis keyword → enum. Unknown keyword = wire drift from a newer
        // converter — refuse rather than guess (the caller's drop is the
        // pre-existing documented behavior).
        guard let basisRaw = o["basis"]?.stringValue,
              let basis = CalcSizeBasis(rawValue: basisRaw) else { return nil }
        // factor/offset default to the identity expression `size`, matching
        // the web (CalcSizeValue.ts) and Compose (CalcSizeValue.kt) twins.
        let factor = o["factor"]?.doubleValue ?? 1.0
        let offset = o["offsetPx"]?.doubleValue ?? 0.0
        return CalcSizeValue(basis: basis,
                             factor: CGFloat(factor),
                             offsetPx: CGFloat(offset))
    }
}
