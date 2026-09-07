//
//  AnimationDriver.swift
//  StyleEngine/animations — wave 8 (keyframes execution, issue #35).
//
//  The PURE timing half of animation runtime v1 (spec 07 §3): absolute
//  timeline time t → eased, directed iteration progress in [0, 1], or
//  nil when the animation applies nothing at t (outside the active
//  interval with a fill mode that says "don't fill"). Pure functions so
//  XCTest pins every arithmetic branch — the SwiftUI side (a
//  TimelineView clock or the pinned CAPTURE_ANIMATION_TIME) only decides
//  WHAT t to ask about, never how t maps to progress.
//
//  The phase arithmetic mirrors web-animations-1 §4.6.6 (before/active/
//  after phases, directed progress, transformed progress) because the
//  web reference implementation IS the Web Animations API — matching
//  its math is what keeps t=0 / t=mid captures comparable across
//  platforms (docs/DYNAMIC_CAPTURE.md §4).
//

import Foundation

/// Pure time→progress math. Namespace enum (StyleBuilder convention).
enum AnimationDriver {

    // MARK: - Timing input

    /// One animation's resolved timing tuple — the per-index slice of the
    /// AnimationsConfig comma lists after css-animations-1 §5.2 list
    /// matching (short lists repeat; AnimationResolver does the slicing).
    struct Timing: Equatable {
        /// animation-duration in ms (≥ 0; CSS initial 0s).
        var durationMs: Double = 0
        /// animation-delay in ms — negative delays advance the start
        /// point per css-animations-1 §4.7.
        var delayMs: Double = 0
        /// animation-iteration-count — nil means `infinite`; fractional
        /// counts end mid-iteration (spec 07 §3). CSS initial 1.
        var iterations: Double? = 1
        /// animation-direction (css-animations-1 §4.5).
        var direction: AnimationDirectionKind = .normal
        /// animation-fill-mode — which stop applies outside the active
        /// interval (css-animations-1 §4.8).
        var fillMode: AnimationFillModeKind = .none
        /// animation-timing-function — nil = the `ease` default, which
        /// the parser normalizes to cubic-bezier(.25,.1,.25,1). NOT
        /// consumed by `progress` (which returns RAW directed progress):
        /// css-animations-1 §4.4 applies the timing function BETWEEN
        /// consecutive keyframes, so the easing runs per SEGMENT inside
        /// KeyframeInterpolator — browser-verified on the 3-stop pulse
        /// (scale 1→1.6→1, ease, t=0.5 shows exactly 1.6: raw progress
        /// selects the middle stop; easing the whole iteration would
        /// land at ≈1.24 and diverge from the web reference).
        var timingFunction: AnimationTimingFn? = nil
        /// animation-play-state — paused freezes the LIVE clock at its
        /// current progress (§3); the pinned capture clock composes by
        /// simply evaluating at the pinned t (spec 07 §5: capture wins).
        var playState: AnimationPlayStateKind = .running
    }

    /// The `ease` keyword's bezier — the CSS initial timing function
    /// (css-easing-1 §2.3); used when no timing-function is declared.
    static let easeDefault = AnimationTimingFn.cubicBezier(x1: 0.25, y1: 0.1, x2: 0.25, y2: 1.0)

    // MARK: - Phase arithmetic (web-animations-1 §4.6.6)

    /// RAW directed progress at absolute timeline second t — or nil when
    /// nothing applies (before phase without backwards fill, after phase
    /// without forwards fill). Deliberately UN-eased: the timing
    /// function applies per keyframe segment (css-animations-1 §4.4) in
    /// KeyframeInterpolator, so the driver's output is the pure timeline
    /// position that selects the segment.
    static func progress(atSeconds t: Double, timing: Timing) -> Double? {
        // Local time: absolute t minus the delay — a NEGATIVE delay
        // advances the start point (css-animations-1 §4.7).
        let localMs = t * 1000.0 - timing.delayMs
        // Active duration: duration × iteration count (∞ for infinite).
        let iterations = timing.iterations
        let activeMs: Double = iterations.map { timing.durationMs * max(0, $0) } ?? .infinity

        // ── Before phase (web-animations-1: local < 0) ────────────────
        if localMs < 0 {
            // Only backwards/both fill paints the first frame during the
            // delay; otherwise the base styles apply (nil = no overlay).
            guard timing.fillMode == .backwards || timing.fillMode == .both else { return nil }
            // The applied frame is the START of the first iteration —
            // direction decides whether that start is 0 or 1.
            return directed(iteration: 0, progress: 0, direction: timing.direction)
        }

        // ── After phase (finite animations only) ──────────────────────
        // durationMs == 0 completes instantly (spec 07 §3: fill still
        // applies), which the `localMs >= activeMs` test covers (0 ≥ 0).
        if localMs >= activeMs {
            guard timing.fillMode == .forwards || timing.fillMode == .both else { return nil }
            // End-state iteration index + in-iteration progress: whole
            // counts end at progress 1 of iteration (n−1); fractional
            // counts end mid-iteration floor(n) at the fractional part
            // (css-animations-1 §4.4 fractional counts).
            let n = iterations ?? 1 // infinite never reaches the after phase in practice
            let whole = n.rounded(.down)
            let endIteration: Double
            let endProgress: Double
            if n <= 0 {
                // 0 iterations: the animation ends where it began —
                // iteration 0 at progress 0 (nothing ever played).
                endIteration = 0; endProgress = 0
            } else if n == whole {
                endIteration = whole - 1; endProgress = 1
            } else {
                endIteration = whole; endProgress = n - whole
            }
            return directed(iteration: endIteration, progress: endProgress,
                            direction: timing.direction)
        }

        // ── Active phase ──────────────────────────────────────────────
        // Iteration index + within-iteration progress from the local
        // clock. durationMs > 0 is guaranteed here (0-duration animations
        // always land in the after phase above).
        let iteration = (localMs / timing.durationMs).rounded(.down)
        let raw = localMs / timing.durationMs - iteration
        return directed(iteration: iteration, progress: raw,
                        direction: timing.direction)
    }

    /// Directed progress (web-animations-1 §4.7.6): flip odd/even
    /// iterations per animation-direction so `alternate` ping-pongs.
    static func directed(iteration: Double, progress: Double,
                         direction: AnimationDirectionKind) -> Double {
        // Parity of the CURRENT iteration index (0-based).
        let odd = iteration.truncatingRemainder(dividingBy: 2) >= 1
        switch direction {
        case .normal:            return progress
        case .reverse:           return 1 - progress
        case .alternate:         return odd ? 1 - progress : progress
        case .alternateReverse:  return odd ? progress : 1 - progress
        }
    }

    /// Segment easing: run the timing function over a LOCAL progress
    /// fraction — the between-keyframes application of css-animations-1
    /// §4.4 (KeyframeInterpolator calls this per segment) and the
    /// whole-window application for transitions (a transition IS one
    /// segment).
    ///
    /// INPUT progress is clamped to [0, 1]; OUTPUT progress is not, and
    /// must not be. css-easing-1 §2.1 constrains x1/x2 to [0, 1] but
    /// leaves y1/y2 free, which is exactly what makes back-ease curves
    /// like cubic-bezier(0.68, -0.55, 0.265, 1.55) overshoot both ends.
    /// This used to clamp the output "because overshoot extrapolation is
    /// not implemented in the interpolator" — but it is: both callers
    /// (KeyframeInterpolator and TransitionResolver) feed the result
    /// straight into KeyframeInterpolator.lerp, which is `x + (y - x) * u`
    /// with no clamp and therefore extrapolates correctly. The clamp
    /// flattened every overshoot curve to a plain ease, and since Compose
    /// does NOT clamp, the same CSS animated differently on the two
    /// platforms. Caught by EasingReferenceTests against the css-easing-1
    /// table (14 samples of the back-ease case).
    static func ease(_ p: Double, with fn: AnimationTimingFn?) -> Double {
        let clamped = min(1, max(0, p))
        switch fn ?? easeDefault {
        case .cubicBezier(let x1, let y1, let x2, let y2):
            return bezier(clamped, x1: x1, y1: y1, x2: x2, y2: y2)
        case .steps(let count, let position):
            return step(clamped, count: count, position: position)
        case .linearStops(let stops):
            return linear(clamped, stops: stops)
        }
    }

    // MARK: - cubic-bezier evaluation (css-easing-1 §2.4)

    /// y at input progress x for cubic-bezier(x1,y1,x2,y2): solve the
    /// parametric x(u) = x with Newton–Raphson (bisection fallback for
    /// flat derivatives), then evaluate y(u). Standard curve solver —
    /// the same algorithm Blink/WebKit use, so eased mid-captures match
    /// the browser to sub-pixel precision.
    static func bezier(_ x: Double, x1: Double, y1: Double, x2: Double, y2: Double) -> Double {
        // Endpoints are exact by definition — skip the solve.
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        // Linear fast-path: control points on the diagonal.
        if x1 == y1 && x2 == y2 { return x }
        // Cubic coefficients for P0=(0,0), P3=(1,1) (css-easing-1 form).
        func coef(_ a: Double, _ b: Double) -> (c: Double, b: Double, a: Double) {
            let c = 3 * a
            let bb = 3 * (b - a) - c
            let aa = 1 - c - bb
            return (c, bb, aa)
        }
        let cx = coef(x1, x2), cy = coef(y1, y2)
        func sampleX(_ u: Double) -> Double { ((cx.a * u + cx.b) * u + cx.c) * u }
        func sampleY(_ u: Double) -> Double { ((cy.a * u + cy.b) * u + cy.c) * u }
        func sampleDX(_ u: Double) -> Double { (3 * cx.a * u + 2 * cx.b) * u + cx.c }
        // Newton–Raphson: 8 rounds is plenty for capture-grade precision.
        var u = x
        for _ in 0..<8 {
            let err = sampleX(u) - x
            if abs(err) < 1e-7 { return sampleY(u) }
            let d = sampleDX(u)
            if abs(d) < 1e-6 { break } // flat slope → bisection below
            u -= err / d
        }
        // Bisection fallback — unconditionally convergent on [0, 1].
        var lo = 0.0, hi = 1.0
        u = x
        while hi - lo > 1e-7 {
            if sampleX(u) < x { lo = u } else { hi = u }
            u = (lo + hi) / 2
        }
        return sampleY(u)
    }

    // MARK: - steps() evaluation (css-easing-1 §2.3.1)

    /// Step easing: divide the input into `count` intervals and jump per
    /// the position keyword. `jumps` differs per keyword (jump-none has
    /// count−1 rises, jump-both count+1) — the css-easing-1 §2.3.1 table.
    static func step(_ p: Double, count: Int, position: StepsPosition) -> Double {
        // Defensive: a non-positive step count is unrepresentable CSS —
        // treat as a single end-jump so we never divide by zero.
        let n = max(1, count)
        // Current step from the input progress.
        var current = (p * Double(n)).rounded(.down)
        // Rising positions take the step AT the interval start.
        if position == .start || position == .jumpStart || position == .jumpBoth {
            current += 1
        }
        // Jump count per the spec table.
        let jumps: Double
        switch position {
        case .jumpNone:              jumps = Double(n - 1)
        case .jumpBoth:              jumps = Double(n + 1)
        default:                     jumps = Double(n)
        }
        // Clamp: input 1.0 must produce exactly 1.0 (after-flag rule) and
        // jump-none's missing final rise clamps mid-range values.
        return min(1, max(0, jumps == 0 ? 1 : current / jumps))
    }

    // MARK: - linear() stops (css-easing-1 §2.2, parser's linearStops)

    /// Piecewise-linear easing through the parsed stop list. Missing
    /// percents fill per spec: first 0, last 100, interior evenly spread
    /// between the nearest specified neighbors.
    static func linear(_ p: Double, stops: [LinearStop]) -> Double {
        guard !stops.isEmpty else { return p }
        // Resolve positions: explicit percent (0–100 → 0–1) or an even
        // spread — the parser has already ordered the stops.
        let n = stops.count
        var pos = [Double](repeating: 0, count: n)
        for (i, s) in stops.enumerated() {
            if let pct = s.percent { pos[i] = pct / 100.0 }
            else if i == 0 { pos[i] = 0 }
            else if i == n - 1 { pos[i] = 1 }
            else { pos[i] = Double(i) / Double(n - 1) } // even fill (v1 approximation)
        }
        // Clamp outside the covered range to the boundary values.
        if p <= pos[0] { return stops[0].value }
        if p >= pos[n - 1] { return stops[n - 1].value }
        // Find the surrounding segment and lerp.
        for i in 1..<n where p <= pos[i] {
            let span = pos[i] - pos[i - 1]
            let u = span > 0 ? (p - pos[i - 1]) / span : 1
            return stops[i - 1].value + (stops[i].value - stops[i - 1].value) * u
        }
        return stops[n - 1].value
    }
}
