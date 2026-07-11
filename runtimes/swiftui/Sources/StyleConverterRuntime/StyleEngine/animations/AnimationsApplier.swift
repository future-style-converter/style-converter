//
//  AnimationsApplier.swift
//  StyleEngine/animations — Phase 9; execution landed in wave 8 (#35).
//
//  WAVE-8 STATUS: keyframe + transition EXECUTION does NOT live in this
//  modifier — it runs as a property-space pass ahead of StyleBuilder
//  (AnimationResolver + KeyframeInterpolator + TransitionResolver,
//  driven by ComponentRenderer's TimelineView clock / the pinned
//  CAPTURE_ANIMATION_TIME hook). That altitude was chosen deliberately:
//  interpolating IR payloads keeps every existing applier untouched and
//  makes the math XCTest-pinnable, where a SwiftUI-native
//  `withAnimation` model would leave progress unobservable (spec 07 §5
//  deterministic-capture contract).
//
//  This modifier therefore STAYS an identity ViewModifier for the
//  timing family it aggregates — the remaining TODOs below cover the
//  features the property-space driver does not model yet (scroll-driven
//  timelines, view transitions), which map to iOS 17/18 APIs above our
//  iOS 16 deployment target.
//
//  Best-effort exception: `animation-play-state: paused` with a
//  present duration is approximated as `.animation(nil, value: …)` —
//  it would freeze the animation at its current frame, which matches
//  CSS's `paused` semantics for static screenshot rendering. (The
//  wave-8 driver ALSO honors paused in property space — see
//  AnimationResolver.resolve's live-clock freeze.)
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
