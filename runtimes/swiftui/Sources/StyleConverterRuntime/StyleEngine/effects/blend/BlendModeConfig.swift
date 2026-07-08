//
//  BlendModeConfig.swift
//  StyleEngine/effects/blend — Phase 4.
//
//  Both `mix-blend-mode` and `background-blend-mode` map onto SwiftUI's
//  `BlendMode` enum. `mix-blend-mode` blends the element with what's
//  beneath it (applied at the compositing level); `background-blend-mode`
//  blends an element's own background layers. SwiftUI only exposes one
//  `.blendMode(_:)` per view, so we use `mix` for the view itself and
//  track the background list for future per-layer work.
//

import SwiftUI

struct BlendModeConfig: Equatable {
    // CSS `mix-blend-mode`. Nil when no property was present; identity
    // render when nil.
    var mix: BlendMode? = nil
    // CSS `background-blend-mode` — ORDERED list of modes matching the
    // BackgroundImage layers. Empty when property absent. SwiftUI lacks
    // a per-layer blend API, so the applier uses the first entry only
    // (and only when no `mix` is set, since the two can't coexist on a
    // single `.blendMode(_:)`).
    var background: [BlendMode] = []

    // Short-circuit helper.
    var hasAny: Bool { mix != nil || !background.isEmpty }
}
