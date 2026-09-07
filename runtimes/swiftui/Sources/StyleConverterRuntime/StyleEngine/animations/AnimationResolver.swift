//
//  AnimationResolver.swift
//  StyleEngine/animations — wave 8 (keyframes execution, issue #35).
//
//  The GLUE of animation runtime v1: a component's resolved declarations
//  + the document's keyframes map + a clock reading t → the effective
//  declarations at t. Composition:
//
//    AnimationsExtractor  (which animations, with what timing lists)
//      → per-animation Timing slice   (css-animations-1 §5.2 list matching)
//      → AnimationDriver.progress     (phase/direction/fill/easing math)
//      → KeyframeInterpolator.apply   (tier interpolation, IR value space)
//
//  Runs BETWEEN DynamicValueResolver and StyleBuilder in the renderer —
//  animation overlays the fully-resolved cascade exactly like CSS's
//  animation origin sits above the author origin (css-cascade-5 §6.2).
//
//  Dangling animation-name (a name the document does not define) is the
//  spec 07 §1.3 DEFINED NO-OP: base styles render, one PropertyTracker
//  breadcrumb per name — never an error, never a dropped component.
//

import Foundation

/// Pure resolution façade the renderer calls once per body evaluation.
enum AnimationResolver {

    // MARK: - Resolve

    /// Effective declarations at absolute timeline second `t`.
    /// `pinnedClock` says t came from the CAPTURE_ANIMATION_TIME hook —
    /// the pinned clock overrides `animation-play-state: paused` (spec 07
    /// §5: capture-at-t composes with paused by winning); a LIVE paused
    /// animation instead freezes at its initial frame (t = 0 — v1 has no
    /// running→paused flip tracking, so "current progress" is the start).
    static func resolve(properties: [IRProperty],
                        keyframes: [String: [IRKeyframeStop]],
                        atSeconds t: Double,
                        pinnedClock: Bool,
                        componentName: String = "") -> [IRProperty] {
        // Cheap gate: no AnimationName → nothing to do (the whole static
        // corpus takes this branch — allocation-free pass-through).
        guard let cfg = AnimationsExtractor.extract(from: properties),
              let names = cfg.name, !names.isEmpty else { return properties }
        var out = properties
        // Multi-animation lists apply in declaration order; a later
        // animation overwrites an earlier one for the same property
        // (css-animations-1 §4.1 — later names win), which the overlay's
        // whole-value replacement gives us for free.
        for (index, entry) in names.enumerated() {
            // `animation-name: none` — an explicit hole in the list that
            // still consumes a timing-list slot (§5.2).
            guard case .identifier(let name) = entry else { continue }
            guard let stops = keyframes[name] else {
                // Spec 07 §1.3 dangling reference: defined no-op + one
                // breadcrumb per name per process.
                PropertyTracker.logOnce(
                    key: "keyframes-dangling:\(name)",
                    message: "animation-name '\(name)' has no matching keyframes set — defined no-op (spec 07 §1.3, component '\(componentName)')")
                continue
            }
            let timing = timingSlice(cfg, index: index)
            // LIVE paused clock: freeze at the initial frame; the pinned
            // capture clock evaluates at the pinned t regardless (§5).
            let effectiveT = (!pinnedClock && timing.playState == .paused) ? 0 : t
            // Phase math → RAW directed progress; nil = nothing applies
            // at t (delay without backwards fill, ended without forwards
            // fill). Easing runs per keyframe segment INSIDE the
            // interpolator (css-animations-1 §4.4).
            guard let p = AnimationDriver.progress(atSeconds: effectiveT, timing: timing)
            else { continue }
            out = KeyframeInterpolator.apply(stops: stops, progress: p,
                                             timing: timing.timingFunction,
                                             to: out, componentName: componentName)
        }
        return out
    }

    // MARK: - Timing list matching (css-animations-1 §5.2)

    /// The per-animation timing tuple: index i of every comma list, short
    /// lists repeating (list[i % count]) — excess entries ignored.
    static func timingSlice(_ cfg: AnimationsConfig, index: Int) -> AnimationDriver.Timing {
        // Cyclic pick — nil/empty lists fall back to the CSS initial.
        func pick<T>(_ list: [T]?) -> T? {
            guard let list = list, !list.isEmpty else { return nil }
            return list[index % list.count]
        }
        var t = AnimationDriver.Timing()
        t.durationMs = pick(cfg.duration)?.milliseconds ?? 0        // initial 0s
        t.delayMs = pick(cfg.delay)?.milliseconds ?? 0              // initial 0s
        // iteration-count: `.infinite` → nil (the driver's ∞ marker).
        switch pick(cfg.iterationCount) {
        case .some(.infinite):        t.iterations = nil
        case .some(.count(let n)):    t.iterations = n
        case .none:                   t.iterations = 1              // initial 1
        }
        t.direction = pick(cfg.direction) ?? .normal                // initial normal
        t.fillMode = pick(cfg.fillMode) ?? .none                    // initial none
        t.timingFunction = pick(cfg.timingFunction)                 // nil = ease default
        t.playState = pick(cfg.playState) ?? .running               // initial running
        return t
    }
}
