//
//  AnimationEnvironment.swift
//  Renderer — wave 8 (keyframes execution + deterministic motion capture).
//
//  Two host→runtime environment channels, mirroring the styleViewport /
//  forcedStyleStates pattern (#39 / wave 7):
//
//    styleKeyframes       — the document-level `keyframes` wire block
//                           (spec 07 §1.2). @keyframes are DOCUMENT-scoped
//                           in CSS, so the map cannot ride any single
//                           component; the host (harness ContentView, an
//                           SDUI shell) decodes the document and publishes
//                           the block over its render tree. nil (default)
//                           = keyframe-free document → the renderer's
//                           animation path is never entered.
//
//    animationCaptureTime — the spec 07 §5 CAPTURE_ANIMATION_TIME hook:
//                           evaluate EVERY animation at absolute timeline
//                           second t, paused. nil (default) = live clock.
//                           0 is meaningful (the initial frame, fill-mode
//                           arithmetic applied) — never conflate with nil.
//

import SwiftUI

/// Document keyframes channel — default nil keeps every pre-wave-8 host
/// byte-identical (no keyframes, no animation resolution, no clock).
private struct StyleKeyframesKey: EnvironmentKey {
    static let defaultValue: [String: [IRKeyframeStop]]? = nil
}

/// Pinned capture clock — default nil = live TimelineView clock.
private struct AnimationCaptureTimeKey: EnvironmentKey {
    static let defaultValue: Double? = nil
}

public extension EnvironmentValues {
    /// The document-level keyframes map (spec 07 §1.2), published by the
    /// host that decoded the IRDocument. Names are the case-preserved
    /// wire keys; AnimationName identifiers match against them verbatim.
    // public: set by the harness (CaptureCanvas / gallery) and SDUI hosts.
    var styleKeyframes: [String: [IRKeyframeStop]]? {
        get { self[StyleKeyframesKey.self] }
        set { self[StyleKeyframesKey.self] = newValue }
    }

    /// The deterministic-capture clock (spec 07 §5): when set, every
    /// animation renders its state at absolute timeline second t, paused
    /// — two captures at the same t are byte-comparable. When nil the
    /// renderer drives a live frame clock for animated components only.
    // public: set by the harness from its -animationTime launch argument
    // (docs/DYNAMIC_CAPTURE.md §4, iOS transport row).
    var animationCaptureTime: Double? {
        get { self[AnimationCaptureTimeKey.self] }
        set { self[AnimationCaptureTimeKey.self] = newValue }
    }
}
