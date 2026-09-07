//
//  RenderingConfig.swift
//  StyleEngine/rendering — Phase 10.
//
//  Render-quality hints + color-space hints + zoom/interpolate-size.
//  Nothing in this bag reaches a modifier (retro P2b deleted the identity
//  applier, A6#10). Two notes it carried:
//    • image-rendering maps ambiguously — nearest vs linear is
//      `.interpolation(.none|.high)` on Image only, and the SDUI runtime
//      does not instantiate Image directly.
//    • `zoom` is NOT this family's business since the wave-37 convergence:
//      it has a real triplet in this folder (ZoomConfig / ZoomExtractor /
//      ZoomApplier) where ZoomLayout moves the layout slot and a
//      size-transparent `.scaleEffect(anchor: .topLeading)` supplies the
//      paint, chained outermost in StyleBuilder.applyGroupEffects. "Zoom"
//      stays in RenderingProperty.names for registry ownership only; the
//      string recorded for it here is inert.
//  The rest have no analogue (color-rendering, color-interpolation,
//  print-color-adjust, forced-color-adjust, content-visibility,
//  field-sizing, input-security, interpolate-size, image-orientation,
//  image-resolution).
//

import Foundation

struct RenderingConfig: Equatable {
    /// Raw string payload keyed by IR property type.
    var rawByType: [String: String] = [:]
    /// True when any owned property was seen.
    var touched: Bool = false
}
