//
//  ScrollTimelineApplier.swift
//  StyleEngine/scrolling — Phase 9.
//
//  Identity modifier. `scroll-timeline` names a scroll container for a
//  later `animation-timeline: <name>` reference — there is nothing to
//  visually emit at the container itself. When the animations applier
//  grows iOS 17+ `scrollTransition` support, this modifier can publish
//  the name/axis into the environment so descendants can look it up.
//

import SwiftUI

struct ScrollTimelineApplier: ViewModifier {
    let config: ScrollTimelineConfig?

    func body(content: Content) -> some View {
        // Identity — Phase 9 is registration + config only.
        guard let cfg = config, cfg.touched else { return AnyView(content) }
        // TODO(Phase 9.integration): publish `cfg.timeline?.name` / `cfg.axis`
        // into the environment so descendant `.engineAnimations(_:)` calls
        // can resolve a scroll-driven timeline reference on iOS 17+.
        let _ = cfg
        return AnyView(content)
    }
}

extension View {
    /// Identity when nil / untouched so callers can attach unconditionally.
    func engineScrollTimeline(_ config: ScrollTimelineConfig?) -> some View {
        modifier(ScrollTimelineApplier(config: config))
    }
}
