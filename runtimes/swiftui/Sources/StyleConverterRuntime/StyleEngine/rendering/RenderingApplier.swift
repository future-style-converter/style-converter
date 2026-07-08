//
//  RenderingApplier.swift
//  StyleEngine/rendering — Phase 10.
//
//  Identity. image-rendering maps ambiguously in SwiftUI (nearest vs
//  linear is available via `.interpolation(.none|.high)` on Image
//  only, and the SDUI runtime doesn't control Image directly). zoom
//  could theoretically map to a scaleEffect but that collides with
//  the Phase 8 transforms pipeline. Everything else has no analog.
//
//  TODO(phase-11): plumb image-rendering through the Image pipeline
//  (requires a hook where SDUI instantiates Image nodes).
//

import Foundation

enum RenderingApplier {
    static func contribute(_ cfg: RenderingConfig?) { _ = cfg }
}
