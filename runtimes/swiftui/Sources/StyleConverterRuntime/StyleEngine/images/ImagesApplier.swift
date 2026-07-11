//
//  ImagesApplier.swift
//  StyleEngine/images — Phase 10. Identity (see ImagesConfig header).
//
//  Wave 8 (#36) note — `object-fit`'s geometry mapping (cover/contain/
//  fill/none/scale-down → the scale math of css-images-3 §5.5, identical
//  to background-size §3.9) now EXISTS on iOS in
//  background/BackgroundImageGeometry.tileSize. It stays unwired here
//  because the IR carries no replaced-element content: components have
//  no <img> payload channel, so there is no intrinsic raster for
//  object-fit to size (the web reference renders the same empty box).
//  When a content-image channel lands, route its raster through
//  BackgroundURLImageResolver + tileSize with the objectFit keyword
//  mapped cover→cover / contain→contain / fill→explicit(100%,100%).
//

import Foundation

enum ImagesApplier {
    static func contribute(_ cfg: ImagesConfig?) { _ = cfg }
}
