//
//  BackgroundSizeApplier.swift
//  StyleEngine/background — Phase 4.
//
//  This applier is an IDENTITY modifier — but `background-size` is NOT
//  unimplemented on iOS, and retro P2e replaced the paragraph that said so
//  ("SwiftUI has no 'sized background layer' primitive … a stub … when a
//  future phase adds raster URL support, this file gains a GeometryReader
//  branch"). Both halves aged out: raster `url()` layers landed in wave 8
//  (BackgroundImageApplier's raster path via BackgroundURLImageResolver),
//  and the sizing they need is resolved by BackgroundImageGeometry
//  (tileSize / gradientTileSize, css-backgrounds-3 §2.9 cover/contain and
//  the two-value form) which BackgroundImageApplier consumes — StyleBuilder
//  hands it `size: style.backgroundSize` at the `.engineBackgroundImage`
//  call, ABOVE this one in the chain.
//
//  So this file stays identity on purpose: the config it receives is
//  already honoured one modifier up, and applying it again here would
//  double-size the layer. It is kept (rather than deleted) because the
//  chain-helper name is the family's registered seam.
//

import SwiftUI

struct BackgroundSizeApplier: ViewModifier {
    // Kept nullable so StyleBuilder can chain unconditionally. Every value
    // means "render unchanged" HERE — BackgroundImageApplier already sized
    // the layer (see the file header).
    let config: BackgroundSizeConfig?

    func body(content: Content) -> some View {
        // No-op today. See file header for the rationale.
        content
    }
}

extension View {
    // Chain helper — identity by design, not by omission: the sizing runs in
    // BackgroundImageApplier (file header). Retro P2e replaced "Reserved for
    // future work", which read as an unimplemented property.
    func engineBackgroundSize(_ config: BackgroundSizeConfig?) -> some View {
        modifier(BackgroundSizeApplier(config: config))
    }
}
