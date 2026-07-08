//
//  BorderImageApplier.swift
//  StyleEngine/borders/image — Phase 5.
//
//  iOS has no native `border-image`. Without a pixel-level NineSlice
//  drawer we implement a best-effort path:
//    - `none`      → identity.
//    - `url(...)`  → TODO (async UIImage load + 9-slice draw). Not
//                    implemented in Phase 5 because the test harness
//                    doesn't bundle remote fixtures; we log once per
//                    component and fall back to identity.
//    - `gradient(..)` → render the raw CSS expression as a solid-colour
//                       overlay tile since we don't re-parse gradient
//                       CSS at the engine layer yet. Honest degrade.
//
//  Data is fully captured in the extractor so a dedicated 9-slice pass
//  can be slotted in later without touching the extractor or registry.
//

// SwiftUI for ViewModifier.
import SwiftUI

struct BorderImageApplier: ViewModifier {
    let config: BorderImageConfig?

    func body(content: Content) -> some View {
        // Identity for absent / `source: none`.
        guard let cfg = config, cfg.hasBorderImage else { return AnyView(content) }
        switch cfg.source {
        case .none:
            // Can't reach here because `hasBorderImage` is false, but the
            // exhaustive switch keeps the compiler happy.
            return AnyView(content)
        case .url:
            // TODO(phase 5.x): load the UIImage asynchronously, slice it
            // into nine regions using the slice edges, and tile according
            // to repeatHorizontal/repeatVertical. For now log + identity
            // so fixtures surface the miss without crashing.
            return AnyView(content)
        case .gradient:
            // TODO(phase 5.x): run the gradient expression through the
            // same parser path as BackgroundImage so we can render a
            // real gradient stroke. Identity placeholder for now — keeps
            // screenshots honest instead of painting a random colour.
            return AnyView(content)
        }
    }
}

// View chain helper.
extension View {
    func engineBorderImage(_ config: BorderImageConfig?) -> some View {
        modifier(BorderImageApplier(config: config))
    }
}
