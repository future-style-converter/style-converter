//
//  CSSTokenParser.swift
//  StyleEngine/core/variables — wave 6 (dynamic values, issue #32).
//
//  Tiny single-token CSS value parsers used AFTER var() substitution:
//  a substituted declaration holds a raw author string ("#e74c3c",
//  "64px", "50%") that never went through the Kotlin converter's
//  primitive parsers, so the runtime needs a minimal reader for the
//  value families token fixtures exercise. Anything unrecognised parses
//  to nil — the caller degrades to unset, never guesses.
//

import Foundation

enum CSSTokenParser {

    // MARK: - Colors

    /// Parsed sRGB color, 0–1 floats — the IR `srgb` block shape.
    struct RGBA: Equatable {
        let r: Double, g: Double, b: Double, a: Double
    }

    /// Parse a substituted color token: #hex (3/4/6/8), rgb()/rgba()
    /// (comma or space syntax), `transparent`, and the CSS Level 1-ish
    /// named subset below. nil for spaces the converter would normally
    /// pre-resolve (oklch/hsl/…) — those can't appear in a var() value
    /// without the author writing them, and guessing sRGB for them here
    /// would silently diverge from the browser.
    static func color(_ raw: String) -> RGBA? {
        // Author strings arrive verbatim — classify on a trimmed,
        // case-normalised copy (color KEYWORDS are case-insensitive;
        // hex digits too).
        let s = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        // Hex forms first — the dominant token-fixture family.
        if s.hasPrefix("#") { return hexColor(String(s.dropFirst())) }
        // rgb()/rgba() functional forms.
        if s.hasPrefix("rgb") { return rgbColor(s) }
        // `transparent` = rgba(0,0,0,0) per css-color-4 §6.1.
        if s == "transparent" { return RGBA(r: 0, g: 0, b: 0, a: 0) }
        // Named subset — the CSS basic keywords (css-color-4 §6.1).
        return namedColors[s]
    }

    /// #rgb / #rgba / #rrggbb / #rrggbbaa → RGBA (css-color-4 §5).
    private static func hexColor(_ hex: String) -> RGBA? {
        // Expand shorthand digits (#f00 → #ff0000) per spec.
        let expanded: String
        switch hex.count {
        case 3, 4: expanded = hex.map { "\($0)\($0)" }.joined()
        case 6, 8: expanded = hex
        default:   return nil
        }
        // Parse byte pairs; any non-hex character fails the whole token.
        var bytes: [Double] = []
        var i = expanded.startIndex
        while i < expanded.endIndex {
            let j = expanded.index(i, offsetBy: 2)
            guard let b = UInt8(expanded[i..<j], radix: 16) else { return nil }
            bytes.append(Double(b) / 255.0)
            i = j
        }
        // Alpha defaults to opaque when absent (6-digit form).
        return RGBA(r: bytes[0], g: bytes[1], b: bytes[2],
                    a: bytes.count == 4 ? bytes[3] : 1.0)
    }

    /// rgb(R,G,B[,A]) / rgb(R G B[ / A]) with 0–255 or % channels.
    private static func rgbColor(_ s: String) -> RGBA? {
        // Slice the argument list out of the functional notation.
        guard let open = s.firstIndex(of: "("), s.hasSuffix(")") else { return nil }
        let args = s[s.index(after: open)..<s.index(before: s.endIndex)]
        // Normalise both syntaxes to one component list: commas and the
        // space-syntax `/` alpha separator all become spaces.
        let parts = args
            .replacingOccurrences(of: ",", with: " ")
            .replacingOccurrences(of: "/", with: " ")
            .split(separator: " ").map(String.init)
        guard parts.count == 3 || parts.count == 4 else { return nil }
        // Channels: percentage → fraction; number → /255 (css-color-4 §4.1).
        func channel(_ t: String) -> Double? {
            if t.hasSuffix("%"), let v = Double(t.dropLast()) { return v / 100.0 }
            if let v = Double(t) { return v / 255.0 }
            return nil
        }
        // Alpha: percentage → fraction; number is ALREADY 0–1 (§4.1).
        func alpha(_ t: String) -> Double? {
            if t.hasSuffix("%"), let v = Double(t.dropLast()) { return v / 100.0 }
            return Double(t)
        }
        guard let r = channel(parts[0]), let g = channel(parts[1]),
              let b = channel(parts[2]) else { return nil }
        let a = parts.count == 4 ? (alpha(parts[3]) ?? 1.0) : 1.0
        return RGBA(r: r, g: g, b: b, a: a)
    }

    /// css-color-4 §6.1 basic named colors (+ a few common extended
    /// ones the fixtures use). Deliberately small: exotic names should
    /// come through the converter pre-resolved, not through var().
    private static let namedColors: [String: RGBA] = [
        "black":   RGBA(r: 0, g: 0, b: 0, a: 1),
        "white":   RGBA(r: 1, g: 1, b: 1, a: 1),
        "red":     RGBA(r: 1, g: 0, b: 0, a: 1),
        "lime":    RGBA(r: 0, g: 1, b: 0, a: 1),
        "blue":    RGBA(r: 0, g: 0, b: 1, a: 1),
        "yellow":  RGBA(r: 1, g: 1, b: 0, a: 1),
        "cyan":    RGBA(r: 0, g: 1, b: 1, a: 1),
        "aqua":    RGBA(r: 0, g: 1, b: 1, a: 1),
        "magenta": RGBA(r: 1, g: 0, b: 1, a: 1),
        "fuchsia": RGBA(r: 1, g: 0, b: 1, a: 1),
        "gray":    RGBA(r: 128 / 255.0, g: 128 / 255.0, b: 128 / 255.0, a: 1),
        "grey":    RGBA(r: 128 / 255.0, g: 128 / 255.0, b: 128 / 255.0, a: 1),
        "green":   RGBA(r: 0, g: 128 / 255.0, b: 0, a: 1),
        "maroon":  RGBA(r: 128 / 255.0, g: 0, b: 0, a: 1),
        "navy":    RGBA(r: 0, g: 0, b: 128 / 255.0, a: 1),
        "olive":   RGBA(r: 128 / 255.0, g: 128 / 255.0, b: 0, a: 1),
        "purple":  RGBA(r: 128 / 255.0, g: 0, b: 128 / 255.0, a: 1),
        "teal":    RGBA(r: 0, g: 128 / 255.0, b: 128 / 255.0, a: 1),
        "silver":  RGBA(r: 192 / 255.0, g: 192 / 255.0, b: 192 / 255.0, a: 1),
        "orange":  RGBA(r: 1, g: 165 / 255.0, b: 0, a: 1),
    ]

    // MARK: - Lengths

    /// Classified single length token (post-substitution).
    enum LengthToken: Equatable {
        /// Absolute pixels (any absolute unit, canonicalised).
        case px(Double)
        /// Percentage — stays symbolic so the sizing/spacing lanes
        /// resolve it against their own containing-block basis.
        case percent(Double)
        /// A calc()/nested expression — evaluate via CalcEvaluator.
        case expression(String)
        /// An intrinsic/auto keyword — forwarded as a keyword shape.
        case keyword(String)
    }

    /// Parse a substituted length-ish token ("64px", "50%", "1.5em" →
    /// handled by calc lane, "auto", "calc(…)"). nil = unparseable.
    /// Font-relative and viewport units return `.expression` so ONE
    /// resolver (CalcEvaluator, which owns those bases) computes them.
    static func length(_ raw: String) -> LengthToken? {
        // Trim — substituted values carry the author's spacing verbatim.
        let s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !s.isEmpty else { return nil }
        let lower = s.lowercased()
        // calc()/min()/max() expressions go to the evaluator lane.
        // (Only calc is evaluated today; min/max would return nil there.)
        if lower.hasPrefix("calc(") { return .expression(s) }
        // Sizing keywords pass through as keywords for the legacy shapes.
        if ["auto", "min-content", "max-content", "fit-content", "none"]
            .contains(lower) { return .keyword(lower) }
        // Percentage stays symbolic (containing-block-relative).
        if lower.hasSuffix("%"), let v = Double(lower.dropLast()) {
            return .percent(v)
        }
        // Split number + unit ("64px" → 64, "px").
        let digits = lower.prefix { $0.isNumber || $0 == "." || $0 == "-" }
        let unit = lower.dropFirst(digits.count).trimmingCharacters(in: .whitespaces)
        guard let n = Double(digits) else { return nil }
        switch unit {
        // Absolute units — canonical px at 96dpi (spec 02 table).
        case "px", "":  return .px(n)
        case "pt":      return .px(n * 4.0 / 3.0)
        case "pc":      return .px(n * 16.0)
        case "in":      return .px(n * 96.0)
        case "cm":      return .px(n * 96.0 / 2.54)
        case "mm":      return .px(n * 96.0 / 25.4)
        case "q":       return .px(n * 96.0 / 25.4 / 4.0)
        // Relative units — route through the calc lane so em/rem/vw
        // resolve against the SAME bases nested calc terms use.
        case "em", "rem", "vw", "vh", "vmin", "vmax":
            return .expression(s)
        // Unknown unit — refuse (degrades to unset upstream).
        default:        return nil
        }
    }
}
