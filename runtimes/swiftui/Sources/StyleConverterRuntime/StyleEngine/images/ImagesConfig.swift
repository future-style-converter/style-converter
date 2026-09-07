//
//  ImagesConfig.swift
//  StyleEngine/images — Phase 10.
//
//  image-rendering, object-fit, object-position, object-view-box.
//  Nothing is applied — SwiftUI's ContentMode is an Image-level
//  parameter, not a modifier, and the SDUI runtime doesn't surface that
//  hook. Retro P2b deleted the identity applier (A6#10); its wave-8 (#36)
//  finding is the part worth keeping: object-fit's geometry mapping
//  (cover/contain/fill/none/scale-down → the concrete-object-size scale
//  math of css-images-3's `object-fit`, which is the same algorithm
//  css-backgrounds-3's `background-size` uses) ALREADY exists on iOS in
//  background/BackgroundImageGeometry.tileSize. It stays unwired because
//  the IR carries no replaced-element content — components have no <img>
//  payload channel, so there is no intrinsic raster for object-fit to
//  size, and the web reference renders the same empty box. When a
//  content-image channel lands, route its raster through
//  BackgroundURLImageResolver + tileSize with the objectFit keyword
//  mapped cover→cover / contain→contain / fill→explicit(100%,100%).
//

import Foundation

struct ImagesConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
