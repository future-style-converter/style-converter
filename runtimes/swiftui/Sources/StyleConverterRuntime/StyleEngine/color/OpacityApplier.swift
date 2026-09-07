//
//  OpacityApplier.swift
//  StyleEngine/color — Phase 4.
//
//  ViewModifier wrapping SwiftUI's `.opacity(_:)`. Trivial, but lives in
//  its own file so the migration registry entry is self-contained and
//  the per-property contract holds (extractor + applier per property).
//

// SwiftUI for ViewModifier.
import SwiftUI

struct OpacityApplier: ViewModifier {
    // Config from OpacityExtractor. Nil = "no opacity property present",
    // body returns content unchanged.
    let config: OpacityConfig?

    func body(content: Content) -> some View {
        // Fast path. `.opacity(1.0)` is not a no-op in SwiftUI — it
        // forces a compositing layer — so we only attach the modifier
        // when an explicit value came from the IR.
        guard let alpha = config?.alpha else { return AnyView(content) }

        // alpha == 1 is the identity: no group, no fade, no layer cost.
        guard alpha < 1 else { return AnyView(content.opacity(alpha)) }

        // css-color-4 §3.3: an opacity below 1 makes the element a
        // stacking context, and "the element (including its descendants)
        // is composited as a GROUP, then blended into the backdrop at
        // that opacity". SwiftUI's `.opacity(_:)` alone does NOT do that
        // — without an explicit `.compositingGroup()` it fades each child
        // LAYER independently, so overlapping siblings show through one
        // another instead of the flattened group being faded once.
        //
        // Measured on fixtures/composition-test.json's Nested_OpacityGroup
        // (white parent at opacity 0.5 over the #1A1A2E stage, with two
        // overlapping children). For the blue child #2980b9 = (41,128,185):
        //
        //   group-composited  0.5*child + 0.5*page          = ( 33,  77, 116)
        //   per-child faded   0.5*child + 0.5*fadedParent   = ( 90, 134, 168)
        //
        //   web      ( 33,  77, 116)   correct
        //   Android  ( 33,  77, 116)   correct
        //   iOS      ( 90, 134, 168)   per-child            <- the bug
        //
        // Max per-channel divergence 80. Android reaches the same result
        // through Compose's saveLayer stack, and IsolationApplier only
        // emits `.compositingGroup()` for an explicit `isolation: isolate`
        // — the default `auto` left opacity ungrouped.
        //
        // When isolation IS `isolate` this nests a group inside a group.
        // That is semantically idempotent (flattening an already-flat
        // buffer changes no pixel) and costs one extra offscreen layer
        // only on elements that asked for isolation anyway — cheaper
        // than making this applier's correctness depend on a sibling's.
        return AnyView(content.compositingGroup().opacity(alpha))
    }
}

// Public call surface used by StyleBuilder.applyStyle.
extension View {
    // Mirrors the `.engineSpacingPadding` etc. naming convention.
    func engineOpacity(_ config: OpacityConfig?) -> some View {
        modifier(OpacityApplier(config: config))
    }
}
