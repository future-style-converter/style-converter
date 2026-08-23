//
//  SvgPathParser.swift
//  StyleEngine/effects/clip — wave 46 (lane Y4).
//
//  SVG 1.1 §8.3 path-data grammar → SwiftUI `Path`, for css-shapes-1
//  §3.1 `path(<fill-rule>?, <string>)`. Twin of Android's
//  SvgPathParser.kt. Replaces ClipApplier's whitespace-split M/L/Q/C/Z
//  walker, which could not even read the WPT `path()` strings: SVG data
//  glues verbs to numbers and allows `,`/`-` as separators
//  (`M10,10h80v80h-80zM25,25h50v50h-50z` split into three garbage tokens
//  → an empty Path → iOS clipped EVERYTHING away on clip-path-path-001/
//  002, and rendered the red fallback on path-with-zoom).
//
//  Supports every command — M/m L/l H/h V/v C/c S/s Q/q T/t A/a Z/z —
//  with implicit repetition (an `M` repeat becomes `L`, per §8.3.2), the
//  number grammar (signs, decimals, exponents, juxtaposed `1.5.5`), and
//  arcs via the §F.6.5 endpoint→centre conversion, split into ≤90°
//  cubic Béziers. Relative commands are resolved against the current
//  point as the spec requires.
//

import SwiftUI

enum SvgPathParser {

    /// Parse `data`; nil when the string holds no drawable command.
    static func parse(_ data: String) -> Path? {
        var scanner = Scanner(data)
        var path = Path()
        // Current point, subpath start, and the previous control point for
        // the smooth (S/T) reflections — all SVG user units (CSS px).
        var cur = CGPoint.zero, start = CGPoint.zero
        var lastCubicControl: CGPoint? = nil, lastQuadControl: CGPoint? = nil
        var drew = false
        while let verb = scanner.nextCommand() {
            let relative = verb.isLowercase
            let upper = Character(verb.uppercased())
            // Each loop turn consumes one argument group; the `repeat`
            // flag keeps consuming groups while numbers remain (§8.3.2).
            var first = true
            // Set when an argument group cannot be read: the data is
            // malformed past this point, so parsing stops with whatever
            // was drawn (SVG §8.3.9 error handling renders up to the error).
            var malformed = false
            repeat {
                switch upper {
                case "M":
                    guard let p = scanner.point() else { malformed = true; break }
                    let to = relative ? cur + p : p
                    // Subsequent pairs after a moveto are implicit linetos.
                    if first { path.move(to: to); start = to } else { path.addLine(to: to) }
                    cur = to; drew = true
                case "L":
                    guard let p = scanner.point() else { malformed = true; break }
                    cur = relative ? cur + p : p; path.addLine(to: cur); drew = true
                case "H":
                    guard let x = scanner.number() else { malformed = true; break }
                    cur.x = relative ? cur.x + x : x; path.addLine(to: cur); drew = true
                case "V":
                    guard let y = scanner.number() else { malformed = true; break }
                    cur.y = relative ? cur.y + y : y; path.addLine(to: cur); drew = true
                case "C":
                    guard let c1 = scanner.point(), let c2 = scanner.point(), let p = scanner.point() else { malformed = true; break }
                    let a = relative ? cur + c1 : c1, b = relative ? cur + c2 : c2, to = relative ? cur + p : p
                    path.addCurve(to: to, control1: a, control2: b)
                    lastCubicControl = b; cur = to; drew = true
                case "S":
                    // First control = reflection of the previous cubic's
                    // second control about the current point (§8.3.6), or
                    // the current point itself when the previous command
                    // was not a cubic.
                    guard let c2 = scanner.point(), let p = scanner.point() else { malformed = true; break }
                    let a = lastCubicControl.map { cur * 2 - $0 } ?? cur
                    let b = relative ? cur + c2 : c2, to = relative ? cur + p : p
                    path.addCurve(to: to, control1: a, control2: b)
                    lastCubicControl = b; cur = to; drew = true
                case "Q":
                    guard let c = scanner.point(), let p = scanner.point() else { malformed = true; break }
                    let a = relative ? cur + c : c, to = relative ? cur + p : p
                    path.addQuadCurve(to: to, control: a)
                    lastQuadControl = a; cur = to; drew = true
                case "T":
                    // Control = reflection of the previous quadratic's
                    // control (§8.3.7), else the current point.
                    guard let p = scanner.point() else { malformed = true; break }
                    let a = lastQuadControl.map { cur * 2 - $0 } ?? cur
                    let to = relative ? cur + p : p
                    path.addQuadCurve(to: to, control: a)
                    lastQuadControl = a; cur = to; drew = true
                case "A":
                    guard let rx = scanner.number(), let ry = scanner.number(), let rot = scanner.number(),
                          let large = scanner.flag(), let sweep = scanner.flag(), let p = scanner.point()
                    else { malformed = true; break }
                    let to = relative ? cur + p : p
                    SvgArc.append(to: &path, from: cur, to: to, rx: rx, ry: ry,
                                  rotationDegrees: rot, largeArc: large, sweep: sweep)
                    cur = to; drew = true
                case "Z":
                    path.closeSubpath(); cur = start
                default:
                    // Not a path verb — the scanner only yields letters, so
                    // this is an unknown one: the data is in error from here
                    // (§8.3.9), stop with what was drawn (nil if nothing).
                    malformed = true
                }
                if malformed { return drew ? path : nil }
                // Smooth-curve memory only survives across consecutive
                // cubic / quadratic commands (§8.3.6 / §8.3.7).
                if upper != "C" && upper != "S" { lastCubicControl = nil }
                if upper != "Q" && upper != "T" { lastQuadControl = nil }
                first = false
            } while upper != "Z" && scanner.hasNumber()
        }
        return drew ? path : nil
    }

    /// Minimal cursor over the path string: commands are ASCII letters,
    /// numbers follow the SVG `number` production, separators are
    /// whitespace and commas. Flags (arc arguments) are single 0/1 digits
    /// that may be juxtaposed (`a1 1 0 01 5 5`).
    struct Scanner {
        private let chars: [Character]
        private var i = 0
        init(_ s: String) { chars = Array(s) }

        private mutating func skipSeparators() {
            while i < chars.count, chars[i].isWhitespace || chars[i] == "," { i += 1 }
        }
        /// Next command letter, or nil at end of data.
        mutating func nextCommand() -> Character? {
            skipSeparators()
            guard i < chars.count, chars[i].isLetter else { return nil }
            defer { i += 1 }
            return chars[i]
        }
        /// True when a number (not a command) is next — implicit repeats.
        mutating func hasNumber() -> Bool {
            skipSeparators()
            guard i < chars.count else { return false }
            let c = chars[i]
            return c.isNumber || c == "-" || c == "+" || c == "."
        }
        /// One SVG number: `[+-]? (digits (. digits?)? | . digits) ([eE][+-]?digits)?`.
        mutating func number() -> CGFloat? {
            skipSeparators()
            let startIndex = i
            if i < chars.count, chars[i] == "-" || chars[i] == "+" { i += 1 }
            var digits = 0
            while i < chars.count, chars[i].isNumber { i += 1; digits += 1 }
            if i < chars.count, chars[i] == "." {
                i += 1
                while i < chars.count, chars[i].isNumber { i += 1; digits += 1 }
            }
            guard digits > 0 else { i = startIndex; return nil }
            // Exponent — only when followed by at least one digit.
            if i < chars.count, chars[i] == "e" || chars[i] == "E" {
                var j = i + 1
                if j < chars.count, chars[j] == "-" || chars[j] == "+" { j += 1 }
                if j < chars.count, chars[j].isNumber {
                    i = j
                    while i < chars.count, chars[i].isNumber { i += 1 }
                }
            }
            return Double(String(chars[startIndex..<i])).map { CGFloat($0) }
        }
        mutating func point() -> CGPoint? {
            guard let x = number(), let y = number() else { return nil }
            return CGPoint(x: x, y: y)
        }
        /// Arc flag: a single `0` / `1` character.
        mutating func flag() -> Bool? {
            skipSeparators()
            guard i < chars.count, chars[i] == "0" || chars[i] == "1" else { return nil }
            defer { i += 1 }
            return chars[i] == "1"
        }
    }
}

// Tiny point arithmetic so the relative/reflection math above reads as
// the spec formulas.
private func + (a: CGPoint, b: CGPoint) -> CGPoint { CGPoint(x: a.x + b.x, y: a.y + b.y) }
private func - (a: CGPoint, b: CGPoint) -> CGPoint { CGPoint(x: a.x - b.x, y: a.y - b.y) }
private func * (a: CGPoint, k: CGFloat) -> CGPoint { CGPoint(x: a.x * k, y: a.y * k) }

/// SVG 1.1 §F.6.5 elliptical-arc endpoint → centre parameterisation, then
/// §F.6.6-style radius correction, emitted as ≤90° cubic segments.
enum SvgArc {
    static func append(to path: inout Path, from p1: CGPoint, to p2: CGPoint,
                       rx rxIn: CGFloat, ry ryIn: CGFloat, rotationDegrees: CGFloat,
                       largeArc: Bool, sweep: Bool) {
        // §F.6.2: zero radius → straight line; coincident endpoints → nothing.
        if p1 == p2 { return }
        var rx = abs(rxIn), ry = abs(ryIn)
        if rx == 0 || ry == 0 { path.addLine(to: p2); return }
        let phi = rotationDegrees * .pi / 180
        let cosPhi = cos(phi), sinPhi = sin(phi)
        // Step 1: (x1', y1') — midpoint-relative, un-rotated.
        let dx = (p1.x - p2.x) / 2, dy = (p1.y - p2.y) / 2
        let x1p = cosPhi * dx + sinPhi * dy
        let y1p = -sinPhi * dx + cosPhi * dy
        // §F.6.6: scale radii up when the endpoints are too far apart.
        let lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if lambda > 1 { rx *= sqrt(lambda); ry *= sqrt(lambda) }
        // Step 2: centre (cx', cy').
        let num = max(0, rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p)
        let den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        var coef = den == 0 ? 0 : sqrt(num / den)
        if largeArc == sweep { coef = -coef }
        let cxp = coef * (rx * y1p / ry), cyp = coef * -(ry * x1p / rx)
        // Step 3: centre in user space.
        let cx = cosPhi * cxp - sinPhi * cyp + (p1.x + p2.x) / 2
        let cy = sinPhi * cxp + cosPhi * cyp + (p1.y + p2.y) / 2
        // Step 4: start angle and sweep.
        func angle(_ ux: CGFloat, _ uy: CGFloat, _ vx: CGFloat, _ vy: CGFloat) -> CGFloat {
            let dot = ux * vx + uy * vy, len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
            var a = acos(max(-1, min(1, dot / len)))
            if ux * vy - uy * vx < 0 { a = -a }
            return a
        }
        let theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var dTheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if !sweep && dTheta > 0 { dTheta -= 2 * .pi }
        if sweep && dTheta < 0 { dTheta += 2 * .pi }
        // Emit: split into segments of at most 90° each, one cubic per
        // segment (the standard 4/3·tan(θ/4) control-point construction).
        let segments = max(1, Int(ceil(abs(dTheta) / (.pi / 2))))
        let delta = dTheta / CGFloat(segments)
        let t = 4 / 3 * tan(delta / 4)
        var th = theta1
        for _ in 0..<segments {
            let th2 = th + delta
            let (c1, s1) = (cos(th), sin(th)), (c2, s2) = (cos(th2), sin(th2))
            // Unit-circle points and tangents, mapped through the ellipse.
            func map(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
                CGPoint(x: cx + cosPhi * rx * x - sinPhi * ry * y,
                        y: cy + sinPhi * rx * x + cosPhi * ry * y)
            }
            let e1 = map(c1 - t * s1, s1 + t * c1)
            let e2 = map(c2 + t * s2, s2 - t * c2)
            let end = map(c2, s2)
            path.addCurve(to: end, control1: e1, control2: e2)
            th = th2
        }
    }
}
