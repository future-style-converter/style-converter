//
//  IsolationApplier.swift
//  StyleEngine/performance — Phase 4.
//
//  Maps IsolationConfig to SwiftUI's `.compositingGroup()`. For `auto`
//  we emit nothing so the view integrates normally with its ancestors'
//  blending context; for `isolate` we force a new compositing group, the
//  closest SwiftUI primitive to CSS isolation.
//

import SwiftUI

struct IsolationApplier: ViewModifier {
    let config: IsolationConfig?

    func body(content: Content) -> some View {
        // Identity when absent or `auto`.
        guard let cfg = config, cfg.mode == .isolate else {
            return AnyView(content)
        }
        // `.compositingGroup()` flattens the children into an offscreen
        // buffer before blending — matches CSS's stacking context.
        return AnyView(content.compositingGroup())
    }
}

extension View {
    // Chain helper.
    func engineIsolation(_ config: IsolationConfig?) -> some View {
        modifier(IsolationApplier(config: config))
    }
}
