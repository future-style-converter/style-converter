//
//  AnimationsApplier.swift
//  StyleEngine/animations — Phase 9.
//
//  Phase 9 is a *registration + config + self-test* pass — the applier
//  is intentionally an identity ViewModifier. Every CSS animation /
//  transition feature maps to a very different SwiftUI API shape
//  (`withAnimation { state.x = … }`, `.animation(_:value:)` bound to a
//  value, `.transition(_:)` on an if/else branch, or iOS 17's
//  `scrollTransition` for scroll-driven timelines). Wiring those
//  requires a state-carrier + trigger model that the current
//  ComponentRenderer does not have — that's a separate integration
//  effort.
//
//  We still attach the modifier so the dispatch shape matches the
//  Filter/Mask/Transforms appliers. That lets the renderer call
//  `.engineAnimations(cfg)` on every view uniformly, and when the
//  runtime wiring lands we flip the body in this one file.
//
//  Best-effort exception: `animation-play-state: paused` with a
//  present duration is approximated as `.animation(nil, value: …)` —
//  it would freeze the animation at its current frame, which matches
//  CSS's `paused` semantics for static screenshot rendering. We leave
//  it as a TODO rather than wiring the state binding here because
//  SwiftUI's `.animation(_:value:)` needs a concrete value the modifier
//  can observe, and that value lives in the child content, not the
//  modifier scope.
//

import SwiftUI

struct AnimationsApplier: ViewModifier {
    let config: AnimationsConfig?

    func body(content: Content) -> some View {
        // Identity short-circuit. Future wiring hooks in here.
        guard let cfg = config, cfg.touched else { return AnyView(content) }
        // TODO(Phase 9.integration): translate cfg.duration / cfg.timingFunction
        // into a SwiftUI Animation and attach via `.animation(_:value:)` bound
        // to a state-carrier the renderer injects. Requires ComponentRenderer
        // to maintain per-component animatable state.
        // TODO(Phase 9.integration): cfg.transitionProperty + cfg.transitionDuration
        // → `.transition(_:)` on conditional views. Needs a driver (e.g. tap)
        // to toggle the branch; the gallery uses static renders so tests can't
        // observe transition mid-flight.
        // TODO(Phase 9.integration): cfg.timeline / cfg.range — iOS 17+
        // `scrollTransition(axis:)`. Graceful no-op on iOS 16 is required
        // because project.yml pins deploymentTarget 16.0.
        // TODO(Phase 9.integration): cfg.viewTransition* — iOS 18 view
        // transitions API (`.viewTransition(.zoom)` equivalent). No-op until
        // deployment target bumps.
        // Best-effort paused: freeze by disabling animations. Harmless when
        // no animation is active (there isn't one today).
        if cfg.playState?.first == .paused {
            return AnyView(content.transaction { $0.animation = nil })
        }
        return AnyView(content)
    }
}

extension View {
    /// Chain helper. Identity when cfg is nil / untouched so callers can
    /// attach unconditionally.
    func engineAnimations(_ config: AnimationsConfig?) -> some View {
        modifier(AnimationsApplier(config: config))
    }
}
