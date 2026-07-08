//
//  BackgroundPositionApplier.swift
//  StyleEngine/background — Phase 4.
//
//  SwiftUI gradients fill their container — there's no "position the
//  gradient at X,Y" primitive for background layers. For raster URL
//  layers we'd need `.position(x:y:)` inside an overlaid Image, which
//  we don't ship yet. This applier therefore records the parsed config
//  for diagnostics and renders identity. Documented limitation.
//

import SwiftUI

struct BackgroundPositionApplier: ViewModifier {
    // Parsed position; retained even though we don't act on it, for
    // symmetry with the other appliers and for future raster support.
    let config: BackgroundPositionConfig?

    func body(content: Content) -> some View {
        // Explicit identity; see file header for rationale.
        content
    }
}

extension View {
    // Chain helper — parity with the other background families.
    func engineBackgroundPosition(_ config: BackgroundPositionConfig?) -> some View {
        modifier(BackgroundPositionApplier(config: config))
    }
}
