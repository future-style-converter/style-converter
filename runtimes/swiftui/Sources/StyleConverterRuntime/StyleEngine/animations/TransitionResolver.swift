//
//  TransitionResolver.swift
//  StyleEngine/animations — wave 8 (transitions execution, issue #35).
//
//  State-change motion (spec 07 §4): when a wave-7 selector/media flip
//  (StateResolver re-resolution) CHANGES a property's effective value,
//  interpolate old → new over transition-duration/-delay/-timing for the
//  properties named in transition-property (or `all`). Reuses the exact
//  interpolation tier of KeyframeInterpolator — one rule set for both
//  motion kinds — and the css-transitions-1 §2 list-matching rule:
//  durations/delays/timings pair with the transition-property list by
//  index (short lists repeat).
//
//  The renderer owns flip DETECTION (it snapshots the pre-flip effective
//  list in ComponentRenderer.onChange); this file is the pure blend so
//  XCTest pins the math. Non-tier / non-lerpable changes switch
//  discretely at the transition ENDPOINT (spec 07 §4 gives the platform
//  the endpoint-or-midpoint choice; endpoint matches how the old value
//  keeps applying until the flip completes) and are logged once.
//
//  Capture posture (docs/DYNAMIC_CAPTURE.md §4): iOS cannot OBSERVE a
//  mid-capture flip — ImageRenderer is one synchronous pass over a fresh
//  view graph per component — so ComponentRenderer.motionEffectiveProperties
//  DECLARES the origin instead: under a pinned clock with a forced state it
//  calls this blend with `from:` = the same effective fold minus the forced
//  set. StateResolver is pure, so that pre-flip list is exactly reproducible
//  without having rendered it, and the interpolation below is shared with
//  the live path unchanged.
//

import Foundation

/// Pure old→new blend at elapsed time. Namespace enum.
enum TransitionResolver {

    /// CSS property-name → IR type map for transition-property entries
    /// (the wire carries lowercased CSS names; extractors use IR types).
    /// Tier v1 names only — everything else changes discretely anyway.
    static let cssNameToType: [String: String] = [
        "opacity": "Opacity", "transform": "Transform",
        "translate": "Translate", "scale": "Scale", "rotate": "Rotate",
        "background-color": "BackgroundColor", "color": "Color",
        "width": "Width", "height": "Height",
    ]

    /// Does this property list DECLARE transitions that could animate a
    /// flip? Gates the renderer's snapshot machinery so the static corpus
    /// never pays for onChange tracking.
    static func hasTransitions(_ properties: [IRProperty]) -> Bool {
        guard let cfg = AnimationsExtractor.extract(from: properties) else { return false }
        // A transition needs a positive duration to be observable; the
        // property list defaults to `all` when only durations are set
        // (css-transitions-1 §2.1 initial value `all`).
        return cfg.transitionDuration?.contains(where: { $0.milliseconds > 0 }) ?? false
    }

    // MARK: - Blend

    /// Effective declarations `elapsed` seconds after a flip from the
    /// `old` effective list to the `new` one. `settled` reports whether
    /// every animating property has reached its endpoint (the renderer
    /// stops the frame clock on true).
    static func blend(from old: [IRProperty], to new: [IRProperty],
                      elapsedSeconds: Double,
                      componentName: String = "") -> (properties: [IRProperty], settled: Bool) {
        // Timing comes from the POST-flip list (css-transitions-1 §3:
        // the after-change style's transition-* values govern).
        guard let cfg = AnimationsExtractor.extract(from: new),
              let durations = cfg.transitionDuration, !durations.isEmpty
        else { return (new, true) }
        // transition-property: nil/absent means `all` (§2.1 initial).
        let propEntries = cfg.transitionProperty ?? [.all]
        // `none` anywhere as the sole entry disables transitions.
        if propEntries.count == 1, case .none = propEntries[0] { return (new, true) }

        var out = new
        var settled = true
        let elapsedMs = elapsedSeconds * 1000.0

        // Old values by type for the diff (last-wins per type, matching
        // extractor scan behavior on the resolved list).
        var oldByType: [String: IRValue] = [:]
        for p in old { oldByType[p.type] = p.data }

        // Walk the NEW list: any type whose value changed AND is covered
        // by a transition-property entry animates from old → new.
        for (i, p) in new.enumerated() {
            guard let oldData = oldByType[p.type], oldData != p.data else { continue }
            guard let entryIndex = coveringEntry(for: p.type, entries: propEntries) else { continue }
            // §2 list matching: timing lists pair with the property list
            // by index, short lists repeating.
            func pick<T>(_ list: [T]?) -> T? {
                guard let list = list, !list.isEmpty else { return nil }
                return list[entryIndex % list.count]
            }
            let durationMs = pick(durations)?.milliseconds ?? 0
            let delayMs = pick(cfg.transitionDelay)?.milliseconds ?? 0
            let timingFn = pick(cfg.transitionTimingFunction)
            // Zero-duration transition = instant change (already `new`).
            guard durationMs > 0 else { continue }
            // Raw progress with delay; the transition holds the OLD value
            // during the delay (css-transitions-1 §3 start delay).
            let raw = (elapsedMs - delayMs) / durationMs
            if raw >= 1 { continue } // reached the endpoint — `new` stands
            settled = false
            if raw <= 0 {
                // Still in the delay window — old value applies whole.
                out[i] = IRProperty(type: p.type, data: oldData)
                continue
            }
            // Same easing pipeline as keyframes (ease default, css-easing).
            let eased = AnimationDriver.ease(raw, with: timingFn)
            if KeyframeInterpolator.tier.contains(p.type),
               let blended = KeyframeInterpolator.lerp(oldData, p.data, eased) {
                out[i] = IRProperty(type: p.type, data: blended)
            } else {
                // Non-tier or structurally non-lerpable: discrete switch
                // at the ENDPOINT — old holds while in flight, logged
                // once per type (spec 07 §4 honesty valve).
                PropertyTracker.logOnce(
                    key: "transition-discrete:\(p.type)",
                    message: "transition on '\(p.type)' is not interpolable — switching discretely at the endpoint (spec 07 §4, component '\(componentName)')")
                out[i] = IRProperty(type: p.type, data: oldData)
            }
        }
        return (out, settled)
    }

    /// Index of the transition-property entry covering `type`, or nil
    /// when no entry names it. LAST matching entry wins (css-transitions-1
    /// §2.1: later duplicates override), and `all` covers every type.
    static func coveringEntry(for type: String,
                              entries: [TransitionPropertyEntry]) -> Int? {
        var found: Int? = nil
        for (i, e) in entries.enumerated() {
            switch e {
            case .all:
                found = i
            case .propertyName(let css):
                if cssNameToType[css] == type { found = i }
                // Unmapped CSS names (outside tier v1) simply never cover
                // an IR type — their changes apply discretely via the
                // uncovered path (no log needed: value change is instant,
                // which IS correct for a non-animatable property).
            case .none:
                // `none` in a longer list clears nothing retroactively in
                // v1 — the sole-`none` case is handled by the caller.
                break
            }
        }
        return found
    }
}
