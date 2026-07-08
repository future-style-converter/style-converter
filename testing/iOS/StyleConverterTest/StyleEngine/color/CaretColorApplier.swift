//
//  CaretColorApplier.swift
//  StyleEngine/color — Phase 4.
//
//  Stub applier. SwiftUI's TextField caret colour is not independently
//  customisable from iOS 16 through iOS 17 — it derives from `.tint`.
//  We keep the applier in place (per the per-property contract) so
//  StyleBuilder wiring doesn't differ between properties; the body is an
//  identity transform and the method exists purely for symmetry.
//
//  Known limitation documented in the Phase 4 report under "Open issues".
//  When Apple exposes a dedicated caret API we can swap this implementation
//  without touching StyleBuilder.
//

import SwiftUI

struct CaretColorApplier: ViewModifier {
    let config: CaretColorConfig?

    func body(content: Content) -> some View {
        // Pure identity until SwiftUI exposes caret-colour control. We
        // still walk through the ViewModifier indirection so the build
        // pipeline matches the other colour appliers and attaches a
        // uniform render-graph shape.
        content
    }
}

extension View {
    // Chain helper — currently a no-op pass-through, kept for parity.
    func engineCaretColor(_ config: CaretColorConfig?) -> some View {
        modifier(CaretColorApplier(config: config))
    }
}
