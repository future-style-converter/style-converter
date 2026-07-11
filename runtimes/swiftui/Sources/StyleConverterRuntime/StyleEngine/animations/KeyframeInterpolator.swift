//
//  KeyframeInterpolator.swift
//  StyleEngine/animations — wave 8 (keyframes execution, issue #35).
//
//  The VALUE half of animation runtime v1: eased progress p + a sorted
//  stop list → the property overlay applied on top of the component's
//  resolved declarations. Interpolation follows the ANIMATABLE TIER v1
//  (spec 07 §2): numbers linear, lengths in px, colors component-wise in
//  sRGB, matching transform function lists per-function — one rule set on
//  all three platforms so time-pinned captures stay comparable.
//
//  Interpolation happens in IR VALUE SPACE: the blended result is a
//  regular {type, data} property whose payload the existing extractors
//  already understand (Opacity {alpha}, lengths {px}, colors {srgb},
//  Transform {type:functions, list}). That keeps every applier untouched
//  — animation is "just" a property rewrite ahead of StyleBuilder, the
//  same altitude StateResolver's bucket fold runs at.
//
//  Non-tier properties in stops are STEP-APPLIED at stop boundaries and
//  logged once per type (spec 07 §2's honesty valve) — never dropped,
//  never silently interpolated by guess.
//

import Foundation

/// Pure stop-list → property-overlay math. Namespace enum.
enum KeyframeInterpolator {

    /// The spec 07 §2 animatable tier: IR property types the runtime
    /// INTERPOLATES. Everything else in a stop step-applies (logged).
    static let tier: Set<String> = [
        "Opacity", "Transform", "Translate", "Scale", "Rotate",
        "BackgroundColor", "Color", "Width", "Height",
    ]

    // MARK: - Apply

    /// Overlay the animation's state at RAW directed progress `p` onto
    /// `base`. `timing` is the property-level animation-timing-function,
    /// applied to each SEGMENT's local fraction (css-animations-1 §4.4:
    /// the timing function runs between consecutive keyframes — easing
    /// the whole iteration instead diverges on any 3-stop track; the
    /// browser-verified pin is MK_Pulse at t=0.5 showing exactly the
    /// middle stop). In-stop timing functions are a MAY at runtime v1
    /// (spec 07 §3) — the property-level one governs every segment.
    /// `stops` arrive offset-sorted (wire contract, spec 07 §1.2); equal
    /// offsets keep authoring order so the per-type track builder below
    /// implements the css-animations-1 §4.2 last-wins cascade by simple
    /// later-overwrites-earlier iteration.
    static func apply(stops: [IRKeyframeStop], progress p: Double,
                      timing: AnimationTimingFn? = nil,
                      to base: [IRProperty], componentName: String) -> [IRProperty] {
        var out = base
        // Property types in first-appearance order (deterministic output
        // ordering — matters only for test stability, not semantics).
        var order: [String] = []
        // type → [(offset, data)] track, last-wins per equal offset.
        var tracks: [String: [(offset: Double, data: IRValue)]] = [:]
        for stop in stops {
            for prop in stop.properties {
                if tracks[prop.type] == nil { order.append(prop.type); tracks[prop.type] = [] }
                if let last = tracks[prop.type]!.last, last.offset == stop.offset {
                    // Same offset declared twice → the later stop wins
                    // (§4.2 cascade; wire order IS authoring order).
                    tracks[prop.type]![tracks[prop.type]!.count - 1] = (stop.offset, prop.data)
                } else {
                    tracks[prop.type]!.append((stop.offset, prop.data))
                }
            }
        }
        for type in order {
            guard var track = tracks[type], !track.isEmpty else { continue }
            // Missing 0%/100% frames use the UNDERLYING value — the
            // component's own resolved declaration (css-animations-1
            // §4.2: unspecified endpoint properties use the computed
            // value). When the base never declares the property either,
            // fall back to the nearest authored stop and say so once —
            // inventing a CSS initial here would silently guess.
            let baseData = base.last(where: { $0.type == type })?.data
            if track[0].offset > 0 {
                if let b = baseData { track.insert((0, b), at: 0) }
                else {
                    PropertyTracker.logOnce(
                        key: "keyframe-endpoint:\(type)",
                        message: "keyframe track '\(type)' has no 0% stop and no base declaration — clamping to the first authored stop (component '\(componentName)')")
                    track.insert((0, track[0].data), at: 0)
                }
            }
            if track[track.count - 1].offset < 1 {
                if let b = baseData { track.append((1, b)) }
                else { track.append((1, track[track.count - 1].data)) } // same clamp; logged above if both missing
            }
            // Segment lookup: the pair [a, b] whose offsets bracket p.
            var a = track[0], b = track[track.count - 1]
            for i in 1..<track.count where p <= track[i].offset {
                a = track[i - 1]; b = track[i]; break
            }
            let span = b.offset - a.offset
            let rawU = span > 0 ? min(1, max(0, (p - a.offset) / span)) : 1
            // §4.4 — ease the LOCAL segment fraction with the property-
            // level timing function (nil = the `ease` keyword default).
            let u = AnimationDriver.ease(rawU, with: timing)
            // Tier types interpolate; everything else steps at the stop
            // boundary (value switches when p crosses b's offset).
            let blended: IRValue
            if tier.contains(type), let lerped = lerp(a.data, b.data, u) {
                blended = lerped
            } else {
                if tier.contains(type) {
                    // Tier type whose payloads didn't match structurally
                    // (e.g. translateX list vs rotate list) — the §2
                    // discrete fallback, logged so fidelity reports can
                    // tell "interpolated" from "stepped".
                    PropertyTracker.logOnce(
                        key: "keyframe-discrete:\(type)",
                        message: "keyframe values for '\(type)' are not structurally interpolable — step-applying at stop boundaries (spec 07 §2, component '\(componentName)')")
                } else {
                    // Non-tier: carried + step-applied per the spec's
                    // honesty valve, logged once per type.
                    PropertyTracker.logOnce(
                        key: "keyframe-nontier:\(type)",
                        message: "keyframe property '\(type)' is outside animatable tier v1 — step-applied at stop boundaries (spec 07 §2, component '\(componentName)')")
                }
                // Boundary switch: the stop's value applies once p
                // reaches its offset; before that the previous holds.
                blended = p >= b.offset ? b.data : a.data
            }
            overlay(&out, property: IRProperty(type: type, data: blended))
        }
        return out
    }

    // MARK: - Structural value lerp (tier rule set)

    /// Interpolate two IR payloads that are STRUCTURALLY parallel:
    ///   • numbers lerp linearly (opacity scalars, scale factors, srgb
    ///     components, px inside {px}, deg inside {deg} — spec 07 §2's
    ///     "numbers linear, lengths px, colors sRGB" in one rule);
    ///   • objects lerp key-wise when the key sets match ("original"
    ///     metadata is dropped — authored text has no midpoint);
    ///   • arrays lerp element-wise when lengths match (transform
    ///     function lists — the {fn:"…"} string leaves must be EQUAL,
    ///     which IS the css-transforms-1 matching-list precondition);
    ///   • equal strings/bools pass through; anything else → nil and the
    ///     caller falls back to the discrete step rule.
    static func lerp(_ a: IRValue, _ b: IRValue, _ u: Double) -> IRValue? {
        switch (a, b) {
        // Numeric leaves — the workhorse. Int/Double mix freely (the
        // wire emits 0 vs 0.0 depending on the Kotlin boxing).
        case (.double, _), (.int, _):
            guard let x = a.doubleValue, let y = b.doubleValue else { return nil }
            return .double(x + (y - x) * u)
        // Equal strings survive (function names, keyword carriers);
        // unequal strings have no midpoint → discrete.
        case (.string(let x), .string(let y)):
            return x == y ? a : nil
        case (.bool(let x), .bool(let y)):
            return x == y ? a : nil
        case (.null, .null):
            return a
        // Objects: key-wise lerp over the UNION being equal key sets.
        // "original" (authored-text metadata) is stripped first — the
        // extractors prefer the computed fields (srgb/px/alpha) and the
        // authored strings of two different stops never blend.
        case (.object(var x), .object(var y)):
            x.removeValue(forKey: "original")
            y.removeValue(forKey: "original")
            guard Set(x.keys) == Set(y.keys) else { return nil }
            var outObj: [String: IRValue] = [:]
            for (k, xv) in x {
                guard let yv = y[k], let blended = lerp(xv, yv, u) else { return nil }
                outObj[k] = blended
            }
            return .object(outObj)
        // Arrays: element-wise, equal lengths only (transform lists —
        // css-transforms-1 §interpolation matched-list rule).
        case (.array(let x), .array(let y)):
            guard x.count == y.count else { return nil }
            var outArr: [IRValue] = []
            for (xv, yv) in zip(x, y) {
                guard let blended = lerp(xv, yv, u) else { return nil }
                outArr.append(blended)
            }
            return .array(outArr)
        default:
            return nil
        }
    }

    // MARK: - Overlay primitive

    /// Same replace-first-occurrence / append semantics as
    /// StateResolver.overlay (spec 06 §3): whole-value replacement per
    /// property type, duplicates collapse to one entry.
    static func overlay(_ current: inout [IRProperty], property p: IRProperty) {
        var replaced = false
        current = current.compactMap { existing in
            guard existing.type == p.type else { return existing }
            if replaced { return nil }
            replaced = true
            return p
        }
        if !replaced { current.append(p) }
    }
}
