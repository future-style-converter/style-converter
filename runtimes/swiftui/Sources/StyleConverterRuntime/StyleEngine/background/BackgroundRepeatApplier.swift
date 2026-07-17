//
//  BackgroundRepeatApplier.swift
//  StyleEngine/background — Phase 4.
//
//  Identity by design — NOT because repeat is unimplemented: the real
//  tiling runs inside BackgroundImageApplier, which threads the parsed
//  BackgroundRepeatConfig into the per-layer paint (url() rasters via
//  BackgroundURLImageView, gradients via BackgroundGradientTileView,
//  both on BackgroundTileMath's §3.7 repeat/space/round arithmetic).
//  Repeat must compose with size/position INSIDE the layer paint — a
//  standalone view modifier applied after the fact could not re-tile an
//  already-painted layer. This applier exists only for per-property
//  contract symmetry (one Applier file per property).
//

import SwiftUI

struct BackgroundRepeatApplier: ViewModifier {
    // Config held for diagnostics and contract symmetry (header note).
    let config: BackgroundRepeatConfig?

    func body(content: Content) -> some View {
        // Intentional identity — tiling happens in BackgroundImageApplier.
        content
    }
}

extension View {
    // Symmetric chain helper — reserved for future tiling work.
    func engineBackgroundRepeat(_ config: BackgroundRepeatConfig?) -> some View {
        modifier(BackgroundRepeatApplier(config: config))
    }
}
