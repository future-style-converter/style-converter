//
//  TransformInheritance.swift
//  StyleEngine/transforms — wave 49, lane A6.
//
//  `transform: inherit`.
//
//  css-cascade-4 §7.3.2: the `inherit` keyword makes an element's computed
//  value for a property EQUAL to the parent's computed value for that same
//  property. `transform` is not an inherited property, so this only
//  happens when an author writes the keyword — and when they do, the
//  effect compounds: the child paints its own copy of the parent's matrix
//  INSIDE the parent's already-transformed coordinate space.
//
//  THE DEFECT (wave-48 corpus, `css-transforms/css-transform-inherit-scale`):
//  a 50x50 `.parent` with `transform: scale(2)` holds a 50x50 `.child`
//  with `transform: inherit`, over a 200x200 red FAIL square the green
//  child must completely cover — 50 px × 2 (own) × 2 (ancestor) = 200 px.
//  Measured green-square boxes against the frozen reference:
//
//      ref      green [16,88 .. 215,287]  200x200   (no red anywhere)
//      web      green [16,88 .. 215,287]  200x200   ssim 0.9990  PASS
//      iOS      green [66,138 .. 165,237] 100x100   ssim 0.9972  FAIL
//                                                   + 30000 px of RED
//
//  100x100 is exactly one factor of two: the ancestor's scale reaches the
//  child through the view hierarchy, and the child's OWN (inherited)
//  scale was dropped — `TransformsExtractor.applyTransform` only accepted
//  `{"type":"functions"}` and `"none"`, so the keyword payload fell out.
//
//  THE MECHANISM. Same ambient-channel pattern as the wave-19 3D
//  rendering context in BackfaceCulling.swift, and for the same reason:
//  the answer depends on an ANCESTOR, and a per-element extractor is
//  parents-ignorant by the IR v2 contract. `TransformsApplier` publishes
//  its own computed `transform` function list to its subtree; a
//  descendant whose aggregate carries `transformInherits` reads it back
//  and renders it as if it had been declared inline.
//
//  DOCUMENTED APPROXIMATION (identical to the 3D channel's, stated here
//  rather than left implicit): an element with NO transform properties at
//  all never reaches the applier body (the `touched` short-circuit), so it
//  forwards this channel unchanged instead of resetting it to `none`. A
//  `transform: inherit` whose parent declares no transform therefore reads
//  the nearest TRANSFORMED ancestor's value rather than `none`. Closing
//  that would mean writing an environment value on every untransformed
//  element in the tree; the corpus carries no such shape, and the cost is
//  paid on every view.
//
//  COMPOSE PARITY: not mirrored. `TransformApplier` on Compose returns a
//  `Modifier`, which cannot read or provide a CompositionLocal — the
//  provider would have to live in `core/renderer/ComponentRenderer.kt`,
//  outside this lane's ownership. Named as a gap, not silently skipped;
//  see the lane report for why the Android cell would not flip on this
//  fix alone in any case (its abspos static position for the child is
//  independently displaced by the parent's own offsets).
//

import SwiftUI

/// Ambient computed `transform` function list of the nearest transformed
/// ancestor. Default empty = `transform: none` above, which is also the
/// correct answer for a root-level `transform: inherit` (the initial
/// containing block has no transform).
private struct InheritedTransformKey: EnvironmentKey {
    static let defaultValue: [TransformFn] = []
}

extension EnvironmentValues {
    /// Read and written only by TransformsApplier — the same visibility
    /// contract `transforms3DAccumulated` states.
    var cssInheritedTransform: [TransformFn] {
        get { self[InheritedTransformKey.self] }
        set { self[InheritedTransformKey.self] = newValue }
    }
}

/// Pure resolution step, split out so XCTest can pin it without a view
/// hierarchy (the TransformsMath / BackfaceCulling pattern).
enum TransformInheritance {

    /// Substitutes the ancestor's list when the aggregate asked to
    /// inherit. Returns the aggregate unchanged otherwise, so every
    /// non-`inherit` element keeps its exact previous behaviour.
    static func resolve(_ agg: TransformsAggregate,
                        inherited: [TransformFn]) -> TransformsAggregate {
        guard agg.transformInherits else { return agg }
        var out = agg
        // The keyword replaces the whole property value, so the list is
        // empty here and this is an assignment, not a merge. Longhands
        // (`translate` / `rotate` / `scale`) are separate properties and
        // are deliberately untouched — `transform: inherit` inherits
        // `transform` and nothing else (css-cascade-4 §7.3.2 is per
        // property).
        out.functions = inherited
        return out
    }

    /// What an element publishes to its descendants: its own computed
    /// `transform`. For an element that itself inherited, that is the
    /// value it resolved to — so a chain of `transform: inherit` carries
    /// the same matrix all the way down, which is what §7.3.2 says.
    static func published(_ resolved: TransformsAggregate) -> [TransformFn] {
        resolved.functions
    }
}
