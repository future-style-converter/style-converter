//
//  GradientStopResolver.swift
//  StyleEngine/background — wave 46, lane Y2 (css-images natives).
//
//  The PURE stop pipeline every gradient flavour shares:
//    1. css-images-4 §3.4.3 colour-stop FIXUP — declared percents win,
//       <length> stops resolve against the gradient-line length, a stop
//       behind its predecessor is clamped forward, and nil runs spread
//       evenly between their positioned neighbours. Before this lane
//       iOS spread EVERY nil stop over the whole count and dropped px
//       stops, so `yellow, blue 70%, green 0` painted green→blue
//       (WPT gradient-move-stops, iOS 0.81) and `white, black, white
//       30px` had no period at all (gradient-border-box, 0.645).
//    2. REPEATING expansion (css-images-4 §3.4.4 / images-3 §3.3.3):
//       the stop span tiles the line in both directions. SwiftUI's
//       gradient types have no repeat option, so the copies are
//       materialised as explicit stops.
//    3. Unit-range CLIP — SwiftUI wants 0…1 locations; §3.4.3's "before
//       the first stop / after the last" colours become boundary stops.
//  Step 4 — SUBDIVISION in the authored interpolation space — lives in
//  GradientRamp.swift (the per-pair colour math this file samples for
//  its clip boundaries).
//

import Foundation

enum GradientStopResolver {

    typealias Stop = GradientApplier.RGBAStop

    // MARK: - 1. Fixup (§3.4.3)

    /// Resolve positions. `lengthPx` is the gradient line length (the
    /// radius for radial flavours); nil means px stops cannot resolve
    /// here (conic stops are angles — the converter never emits a
    /// length for them) and such a stop behaves as unpositioned, with a
    /// breadcrumb so the gap is never silent.
    static func fixup(_ stops: [BackgroundImageStop], lengthPx: Double?) -> [Stop] {
        guard !stops.isEmpty else { return [] }
        // Step 0 — declared position per stop (fraction) or nil.
        var loc: [Double?] = stops.map { s in
            if let p = s.position { return p }
            if let px = s.positionPx {
                if let len = lengthPx, len > 0 { return px / len }
                PropertyTracker.logOnce(key: "gradient-px-stop-no-length",
                    message: "gradient <length> stop position has no gradient-line length here — treating it as unpositioned")
            }
            return nil
        }
        // Step 1 — first/last default to 0% / 100%.
        if loc[0] == nil { loc[0] = 0 }
        if loc[loc.count - 1] == nil { loc[loc.count - 1] = 1 }
        // Step 2 — a stop behind any predecessor is clamped forward.
        var running = loc[0]!
        for i in 1..<loc.count {
            if let v = loc[i] { running = max(running, v); loc[i] = running }
        }
        // Step 3 — nil runs spread evenly between positioned neighbours.
        var i = 1
        while i < loc.count {
            guard loc[i] == nil else { i += 1; continue }
            var j = i
            while loc[j] == nil { j += 1 }          // j = next positioned (last is never nil)
            let prev = loc[i - 1]!, next = loc[j]!
            let runLen = j - i
            for k in 0..<runLen {
                // prev + (next − prev) × (k+1)/(run+1): for an all-nil list
                // this is exactly i/(n−1), the pre-wave even spread.
                loc[i + k] = prev + (next - prev) * (Double(k + 1) / Double(runLen + 1))
            }
            i = j
        }
        return zip(stops, loc).map { s, l in
            let (r, g, b, a) = rgba(s.color)
            return Stop(r: r, g: g, b: b, a: a, loc: l!)
        }
    }

    /// Static sRGB block or the transparent fallback (dynamic colours
    /// cannot resolve without a context — same visual as before).
    private static func rgba(_ c: ColorValue) -> (Double, Double, Double, Double) {
        if case .srgb(let r, let g, let b, let a) = c { return (r, g, b, a) }
        return (0, 0, 0, 0)
    }

    // MARK: - 2. Repeating expansion

    /// Ceiling on materialised copies. A period needing more copies than
    /// this is finer than ~1/253 of the gradient line (≈1.5px on the
    /// 390px capture line) — a lattice no capture resolves, and
    /// enumerating its thousands of stops would cost every render for a
    /// moiré nobody can see. Past the cap the tiling degrades to the
    /// §3.3.3 average solid below, NEVER to a partial lattice.
    static let maxCopies = 256

    /// Tile the stop span across [0, 1]. A span that cannot be tiled —
    /// zero-length, or finer than `maxCopies` allows — renders as the
    /// spec's "solid average colour" (css-images-3 §3.3.3).
    static func expandRepeating(_ stops: [Stop]) -> [Stop] {
        guard let first = stops.first, let last = stops.last, stops.count >= 2 else { return stops }
        let period = last.loc - first.loc
        // Zero-length gradient — §3.3.3's stated degenerate rendering.
        guard period > 1e-9 else { return solidAverage(stops) }
        // Copies k with first.loc + k·period spanning [−period, 1+period]
        // so the clip step always finds a crossing segment at 0 and 1.
        let kMin = Int(floor((0 - first.loc) / period)) - 1
        let kMax = Int(ceil((1 - first.loc) / period)) + 1
        guard kMax - kMin + 1 <= maxCopies else {
            // More copies than the cap allows. This branch used to
            // TRUNCATE the lattice (kMin = 0, kMax = maxCopies − 1),
            // which is not a degraded picture but a WRONG one: a 1px
            // period on the 390px capture line striped only the first
            // 256px and left the remaining ~134px flat in the last
            // stop's colour, with a hard seam between the two zones. A
            // lattice this fine integrates to its average colour — the
            // very rendering §3.3.3 prescribes for the other period it
            // cannot draw (zero-length) — so paint that flat colour over
            // the whole line instead, with a breadcrumb so the
            // degradation is never silent.
            PropertyTracker.logOnce(key: "gradient-repeating-period-too-fine",
                message: "repeating gradient period needs \(kMax - kMin + 1) copies (cap \(maxCopies)) — painting the css-images-3 §3.3.3 average colour instead of a partial lattice")
            return solidAverage(stops)
        }
        var out: [Stop] = []
        out.reserveCapacity((kMax - kMin + 1) * stops.count)
        for k in kMin...kMax {
            let shift = Double(k) * period
            for s in stops { out.append(Stop(r: s.r, g: s.g, b: s.b, a: s.a, loc: s.loc + shift)) }
        }
        // Monotonicity repair (wave 48, F3 — the Kotlin twin's wave-47
        // clamp, GradientStopResolver.kt): copy k's LAST stop and copy
        // k+1's FIRST stop are meant to coincide at first.loc +
        // (k+1)·period, but the two float paths (`last.loc + k·p` vs
        // `first.loc + (k+1)·p`) can land one ulp apart in either order
        // (S4's executed Double sweep misordered 6023 of 23275 rational
        // lattices — e.g. period 1/5, anchor 0.13 emits
        // …0.93000000000000016, 0.93000000000000005… — a NON-monotonic
        // list, which SwiftUI's Gradient treats as undefined stop order).
        // Clamp forward to the running max — the same §3.4.3 rule-2
        // clamp, applied to the materialised lattice; a one-ulp shift is
        // invisible, an unordered gradient input is not.
        var run = out[0].loc
        for idx in 1..<out.count {
            if out[idx].loc < run { out[idx].loc = run } else { run = out[idx].loc }
        }
        return out
    }

    /// css-images-3 §3.3.3's degenerate rendering: "the average color of
    /// all the color stops", painted flat across the whole line. The
    /// unweighted stop mean is the spec's own wording (not the
    /// length-weighted integral of the ramp), and both boundary stops
    /// carry the SAME colour, so no seam can appear anywhere on the line.
    private static func solidAverage(_ stops: [Stop]) -> [Stop] {
        let n = Double(stops.count)
        // The exact expression the zero-period branch has always used —
        // the committed zero-period pin stays bit-for-bit.
        let avg = stops.reduce((0.0, 0.0, 0.0, 0.0)) { ($0.0 + $1.r / n, $0.1 + $1.g / n, $0.2 + $1.b / n, $0.3 + $1.a / n) }
        return [Stop(r: avg.0, g: avg.1, b: avg.2, a: avg.3, loc: 0),
                Stop(r: avg.0, g: avg.1, b: avg.2, a: avg.3, loc: 1)]
    }

    // MARK: - 3. Unit clip

    /// Confine to 0…1: stops outside are replaced by the colour the ramp
    /// has AT the boundary (interpolated in `interp`), and a ramp that
    /// starts after 0 / ends before 1 pads with its end colours (§3.4.3
    /// "the first stop's colour before it, the last stop's after it").
    /// Already-in-range lists come back untouched (byte stability).
    static func clipToUnit(_ stops: [Stop], interp: GradientInterpolation) -> [Stop] {
        guard let first = stops.first, let last = stops.last else { return stops }
        guard first.loc < 0 || last.loc > 1 else { return stops }
        var out: [Stop] = []
        for (i, s) in stops.enumerated() {
            if s.loc < 0 {
                // Crossing into range: emit the colour at 0.
                if i + 1 < stops.count, stops[i + 1].loc >= 0 { out.append(sample(s, stops[i + 1], at: 0, interp: interp)) }
                continue
            }
            if s.loc > 1 {
                // Leaving range: emit the colour at 1 and stop.
                if i > 0, stops[i - 1].loc <= 1 { out.append(sample(stops[i - 1], s, at: 1, interp: interp)) }
                break
            }
            out.append(s)
        }
        // Wave 48 (lane W1), byte-parallel with the Kotlin twin: EVERY
        // stop past one end of the line leaves the loop with no in-range
        // stop and no crossing pair — but §3.4.3's padding rule still
        // defines the rendering: everything before the first stop takes
        // the first stop's colour, everything after the last stop the
        // last's. A line ending before its first stop therefore paints
        // the FIRST colour uniformly; one starting after its last stop
        // the LAST colour. Returning [] here made the ramp unpaintable
        // for the css-break/background-image-006 clone shape
        // (`linear-gradient(green 80px, red 140px)` on a fragment line
        // shorter than 80px — the ref is all-green for exactly that
        // reason); on Android the same shape was a per-frame draw NPE
        // that shipped the cell unmeasured (wave-48 W1, device-proven).
        if out.isEmpty {
            // All stops above 1 ⇒ [0,1] lies before the first stop.
            let pad = first.loc > 1 ? first : last
            return [withLoc(pad, 0), withLoc(pad, 1)]
        }
        if out[0].loc > 0 { out.insert(withLoc(out[0], 0), at: 0) }
        if out[out.count - 1].loc < 1 { out.append(withLoc(out[out.count - 1], 1)) }
        return out
    }

    /// The ramp colour at absolute location `x` between two stops.
    private static func sample(_ a: Stop, _ b: Stop, at x: Double, interp: GradientInterpolation) -> Stop {
        let span = b.loc - a.loc
        let t = span > 0 ? (x - a.loc) / span : 1
        var s = GradientRamp.interpolate(a, b, t: t, interp: interp)
        s.loc = x
        return s
    }

    private static func withLoc(_ s: Stop, _ loc: Double) -> Stop {
        var c = s; c.loc = loc; return c
    }
}
