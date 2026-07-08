//
//  BackgroundRepeatConfig.swift
//  StyleEngine/background — Phase 4.
//
//  CSS `background-repeat` per layer. IR shape:
//    ["repeat"|"no-repeat"|"space"|"round"]  OR
//    [{x: <kw>, y: <kw>}]
//  We parse into a simple per-layer tuple. SwiftUI has no gradient
//  repeat primitive — the applier is a stub.
//

import Foundation

// One layer's repeat value — the X and Y axes may differ.
struct BackgroundRepeatLayer: Equatable {
    // Normalised lower-case keyword: repeat / no-repeat / space / round.
    var x: String
    // Same axis vocabulary.
    var y: String
}

struct BackgroundRepeatConfig: Equatable {
    // One entry per background-image layer.
    var layers: [BackgroundRepeatLayer] = []
    // Short-circuit for the applier.
    var hasAny: Bool { !layers.isEmpty }
}
