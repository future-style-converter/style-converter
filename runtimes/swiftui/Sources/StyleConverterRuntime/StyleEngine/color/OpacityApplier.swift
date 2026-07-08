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
        if let alpha = config?.alpha {
            return AnyView(content.opacity(alpha))
        }
        return AnyView(content)
    }
}

// Public call surface used by StyleBuilder.applyStyle.
extension View {
    // Mirrors the `.engineSpacingPadding` etc. naming convention.
    func engineOpacity(_ config: OpacityConfig?) -> some View {
        modifier(OpacityApplier(config: config))
    }
}
