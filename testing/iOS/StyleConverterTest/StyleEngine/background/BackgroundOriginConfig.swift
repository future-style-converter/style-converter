//
//  BackgroundOriginConfig.swift
//  StyleEngine/background — Phase 4.
//
//  CSS `background-origin` — which box the background positioning area
//  starts from (border-box / padding-box / content-box). IR shape is an
//  array of `{type: "<kind>"}` objects, one per layer. This property
//  only matters when `background-position` is non-zero; for a layer
//  that fills (gradient without position) the result is indistinguishable
//  from the default.
//

import Foundation

// Per-layer origin enum. Normalised from IR strings.
enum BackgroundOriginMode: Equatable {
    case borderBox
    case paddingBox
    case contentBox
}

struct BackgroundOriginConfig: Equatable {
    // All layers' origins, retained even though the applier is identity.
    var layers: [BackgroundOriginMode] = []
    // True when the property appeared in IR.
    var hasAny: Bool { !layers.isEmpty }
}
