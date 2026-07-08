//
//  BorderRadiusApplier.swift
//  StyleEngine/borders/radius — Phase 5.
//
//  ViewModifier that clips content to the extracted BorderRadiusConfig
//  so background colour, images, and descendant views all respect the
//  rounded box. The border stroke itself is painted by BorderSideApplier
//  (which reads the same config) — keeping them separate lets us skip
//  the clip for elements that only need rounded strokes without a
//  colour-clipping paint.
//
//  This file intentionally stays tiny — all the geometry lives in
//  `BorderRadiusShape`.
//

// SwiftUI for the ViewModifier API.
import SwiftUI

struct BorderRadiusApplier: ViewModifier {
    // Nil when no border-*-radius property appears in the IR.
    let config: BorderRadiusConfig?

    func body(content: Content) -> some View {
        // Zero-radius: identity — avoids allocating a Shape for the
        // overwhelmingly common "no rounded corners" case.
        guard let cfg = config, cfg.hasAny else { return AnyView(content) }
        // Clip to the custom Shape. SwiftUI forwards hit-testing through
        // `.clipShape`, so taps/press events still land on visible pixels
        // only — matches CSS's `overflow: hidden` semantics.
        return AnyView(content.clipShape(BorderRadiusShape(radius: cfg)))
    }
}

// Public chain helper, following the `.engine*` naming convention.
extension View {
    func engineBorderRadius(_ config: BorderRadiusConfig?) -> some View {
        modifier(BorderRadiusApplier(config: config))
    }
}
