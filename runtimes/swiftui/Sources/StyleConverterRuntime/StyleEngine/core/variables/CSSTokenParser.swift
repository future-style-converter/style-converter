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
        // Wave 22 — the FULL css-color-4 §6.1 named-color table, generated
        // byte-for-byte from the Compose twin (CssVariableResolver.namedColors)
        // so the natives can never diverge on a named token again (the old
        // 28-name table painted coral/skyblue decorations wrong on iOS).
        // Components are exact n/255 Double ratios, matching the parser's
        // own hex path so equal tokens compare bit-identical.
        "transparent": RGBA(r: 0, g: 0, b: 0, a: 0),
        "aliceblue": RGBA(r: 240.0/255.0, g: 248.0/255.0, b: 255.0/255.0, a: 1),
        "antiquewhite": RGBA(r: 250.0/255.0, g: 235.0/255.0, b: 215.0/255.0, a: 1),
        "aqua": RGBA(r: 0.0/255.0, g: 255.0/255.0, b: 255.0/255.0, a: 1),
        "aquamarine": RGBA(r: 127.0/255.0, g: 255.0/255.0, b: 212.0/255.0, a: 1),
        "azure": RGBA(r: 240.0/255.0, g: 255.0/255.0, b: 255.0/255.0, a: 1),
        "beige": RGBA(r: 245.0/255.0, g: 245.0/255.0, b: 220.0/255.0, a: 1),
        "bisque": RGBA(r: 255.0/255.0, g: 228.0/255.0, b: 196.0/255.0, a: 1),
        "black": RGBA(r: 0.0/255.0, g: 0.0/255.0, b: 0.0/255.0, a: 1),
        "blanchedalmond": RGBA(r: 255.0/255.0, g: 235.0/255.0, b: 205.0/255.0, a: 1),
        "blue": RGBA(r: 0.0/255.0, g: 0.0/255.0, b: 255.0/255.0, a: 1),
        "blueviolet": RGBA(r: 138.0/255.0, g: 43.0/255.0, b: 226.0/255.0, a: 1),
        "brown": RGBA(r: 165.0/255.0, g: 42.0/255.0, b: 42.0/255.0, a: 1),
        "burlywood": RGBA(r: 222.0/255.0, g: 184.0/255.0, b: 135.0/255.0, a: 1),
        "cadetblue": RGBA(r: 95.0/255.0, g: 158.0/255.0, b: 160.0/255.0, a: 1),
        "chartreuse": RGBA(r: 127.0/255.0, g: 255.0/255.0, b: 0.0/255.0, a: 1),
        "chocolate": RGBA(r: 210.0/255.0, g: 105.0/255.0, b: 30.0/255.0, a: 1),
        "coral": RGBA(r: 255.0/255.0, g: 127.0/255.0, b: 80.0/255.0, a: 1),
        "cornflowerblue": RGBA(r: 100.0/255.0, g: 149.0/255.0, b: 237.0/255.0, a: 1),
        "cornsilk": RGBA(r: 255.0/255.0, g: 248.0/255.0, b: 220.0/255.0, a: 1),
        "crimson": RGBA(r: 220.0/255.0, g: 20.0/255.0, b: 60.0/255.0, a: 1),
        "cyan": RGBA(r: 0.0/255.0, g: 255.0/255.0, b: 255.0/255.0, a: 1),
        "darkblue": RGBA(r: 0.0/255.0, g: 0.0/255.0, b: 139.0/255.0, a: 1),
        "darkcyan": RGBA(r: 0.0/255.0, g: 139.0/255.0, b: 139.0/255.0, a: 1),
        "darkgoldenrod": RGBA(r: 184.0/255.0, g: 134.0/255.0, b: 11.0/255.0, a: 1),
        "darkgray": RGBA(r: 169.0/255.0, g: 169.0/255.0, b: 169.0/255.0, a: 1),
        "darkgreen": RGBA(r: 0.0/255.0, g: 100.0/255.0, b: 0.0/255.0, a: 1),
        "darkgrey": RGBA(r: 169.0/255.0, g: 169.0/255.0, b: 169.0/255.0, a: 1),
        "darkkhaki": RGBA(r: 189.0/255.0, g: 183.0/255.0, b: 107.0/255.0, a: 1),
        "darkmagenta": RGBA(r: 139.0/255.0, g: 0.0/255.0, b: 139.0/255.0, a: 1),
        "darkolivegreen": RGBA(r: 85.0/255.0, g: 107.0/255.0, b: 47.0/255.0, a: 1),
        "darkorange": RGBA(r: 255.0/255.0, g: 140.0/255.0, b: 0.0/255.0, a: 1),
        "darkorchid": RGBA(r: 153.0/255.0, g: 50.0/255.0, b: 204.0/255.0, a: 1),
        "darkred": RGBA(r: 139.0/255.0, g: 0.0/255.0, b: 0.0/255.0, a: 1),
        "darksalmon": RGBA(r: 233.0/255.0, g: 150.0/255.0, b: 122.0/255.0, a: 1),
        "darkseagreen": RGBA(r: 143.0/255.0, g: 188.0/255.0, b: 143.0/255.0, a: 1),
        "darkslateblue": RGBA(r: 72.0/255.0, g: 61.0/255.0, b: 139.0/255.0, a: 1),
        "darkslategray": RGBA(r: 47.0/255.0, g: 79.0/255.0, b: 79.0/255.0, a: 1),
        "darkslategrey": RGBA(r: 47.0/255.0, g: 79.0/255.0, b: 79.0/255.0, a: 1),
        "darkturquoise": RGBA(r: 0.0/255.0, g: 206.0/255.0, b: 209.0/255.0, a: 1),
        "darkviolet": RGBA(r: 148.0/255.0, g: 0.0/255.0, b: 211.0/255.0, a: 1),
        "deeppink": RGBA(r: 255.0/255.0, g: 20.0/255.0, b: 147.0/255.0, a: 1),
        "deepskyblue": RGBA(r: 0.0/255.0, g: 191.0/255.0, b: 255.0/255.0, a: 1),
        "dimgray": RGBA(r: 105.0/255.0, g: 105.0/255.0, b: 105.0/255.0, a: 1),
        "dimgrey": RGBA(r: 105.0/255.0, g: 105.0/255.0, b: 105.0/255.0, a: 1),
        "dodgerblue": RGBA(r: 30.0/255.0, g: 144.0/255.0, b: 255.0/255.0, a: 1),
        "firebrick": RGBA(r: 178.0/255.0, g: 34.0/255.0, b: 34.0/255.0, a: 1),
        "floralwhite": RGBA(r: 255.0/255.0, g: 250.0/255.0, b: 240.0/255.0, a: 1),
        "forestgreen": RGBA(r: 34.0/255.0, g: 139.0/255.0, b: 34.0/255.0, a: 1),
        "fuchsia": RGBA(r: 255.0/255.0, g: 0.0/255.0, b: 255.0/255.0, a: 1),
        "gainsboro": RGBA(r: 220.0/255.0, g: 220.0/255.0, b: 220.0/255.0, a: 1),
        "ghostwhite": RGBA(r: 248.0/255.0, g: 248.0/255.0, b: 255.0/255.0, a: 1),
        "gold": RGBA(r: 255.0/255.0, g: 215.0/255.0, b: 0.0/255.0, a: 1),
        "goldenrod": RGBA(r: 218.0/255.0, g: 165.0/255.0, b: 32.0/255.0, a: 1),
        "gray": RGBA(r: 128.0/255.0, g: 128.0/255.0, b: 128.0/255.0, a: 1),
        "green": RGBA(r: 0.0/255.0, g: 128.0/255.0, b: 0.0/255.0, a: 1),
        "greenyellow": RGBA(r: 173.0/255.0, g: 255.0/255.0, b: 47.0/255.0, a: 1),
        "grey": RGBA(r: 128.0/255.0, g: 128.0/255.0, b: 128.0/255.0, a: 1),
        "honeydew": RGBA(r: 240.0/255.0, g: 255.0/255.0, b: 240.0/255.0, a: 1),
        "hotpink": RGBA(r: 255.0/255.0, g: 105.0/255.0, b: 180.0/255.0, a: 1),
        "indianred": RGBA(r: 205.0/255.0, g: 92.0/255.0, b: 92.0/255.0, a: 1),
        "indigo": RGBA(r: 75.0/255.0, g: 0.0/255.0, b: 130.0/255.0, a: 1),
        "ivory": RGBA(r: 255.0/255.0, g: 255.0/255.0, b: 240.0/255.0, a: 1),
        "khaki": RGBA(r: 240.0/255.0, g: 230.0/255.0, b: 140.0/255.0, a: 1),
        "lavender": RGBA(r: 230.0/255.0, g: 230.0/255.0, b: 250.0/255.0, a: 1),
        "lavenderblush": RGBA(r: 255.0/255.0, g: 240.0/255.0, b: 245.0/255.0, a: 1),
        "lawngreen": RGBA(r: 124.0/255.0, g: 252.0/255.0, b: 0.0/255.0, a: 1),
        "lemonchiffon": RGBA(r: 255.0/255.0, g: 250.0/255.0, b: 205.0/255.0, a: 1),
        "lightblue": RGBA(r: 173.0/255.0, g: 216.0/255.0, b: 230.0/255.0, a: 1),
        "lightcoral": RGBA(r: 240.0/255.0, g: 128.0/255.0, b: 128.0/255.0, a: 1),
        "lightcyan": RGBA(r: 224.0/255.0, g: 255.0/255.0, b: 255.0/255.0, a: 1),
        "lightgoldenrodyellow": RGBA(r: 250.0/255.0, g: 250.0/255.0, b: 210.0/255.0, a: 1),
        "lightgray": RGBA(r: 211.0/255.0, g: 211.0/255.0, b: 211.0/255.0, a: 1),
        "lightgreen": RGBA(r: 144.0/255.0, g: 238.0/255.0, b: 144.0/255.0, a: 1),
        "lightgrey": RGBA(r: 211.0/255.0, g: 211.0/255.0, b: 211.0/255.0, a: 1),
        "lightpink": RGBA(r: 255.0/255.0, g: 182.0/255.0, b: 193.0/255.0, a: 1),
        "lightsalmon": RGBA(r: 255.0/255.0, g: 160.0/255.0, b: 122.0/255.0, a: 1),
        "lightseagreen": RGBA(r: 32.0/255.0, g: 178.0/255.0, b: 170.0/255.0, a: 1),
        "lightskyblue": RGBA(r: 135.0/255.0, g: 206.0/255.0, b: 250.0/255.0, a: 1),
        "lightslategray": RGBA(r: 119.0/255.0, g: 136.0/255.0, b: 153.0/255.0, a: 1),
        "lightslategrey": RGBA(r: 119.0/255.0, g: 136.0/255.0, b: 153.0/255.0, a: 1),
        "lightsteelblue": RGBA(r: 176.0/255.0, g: 196.0/255.0, b: 222.0/255.0, a: 1),
        "lightyellow": RGBA(r: 255.0/255.0, g: 255.0/255.0, b: 224.0/255.0, a: 1),
        "lime": RGBA(r: 0.0/255.0, g: 255.0/255.0, b: 0.0/255.0, a: 1),
        "limegreen": RGBA(r: 50.0/255.0, g: 205.0/255.0, b: 50.0/255.0, a: 1),
        "linen": RGBA(r: 250.0/255.0, g: 240.0/255.0, b: 230.0/255.0, a: 1),
        "magenta": RGBA(r: 255.0/255.0, g: 0.0/255.0, b: 255.0/255.0, a: 1),
        "maroon": RGBA(r: 128.0/255.0, g: 0.0/255.0, b: 0.0/255.0, a: 1),
        "mediumaquamarine": RGBA(r: 102.0/255.0, g: 205.0/255.0, b: 170.0/255.0, a: 1),
        "mediumblue": RGBA(r: 0.0/255.0, g: 0.0/255.0, b: 205.0/255.0, a: 1),
        "mediumorchid": RGBA(r: 186.0/255.0, g: 85.0/255.0, b: 211.0/255.0, a: 1),
        "mediumpurple": RGBA(r: 147.0/255.0, g: 112.0/255.0, b: 219.0/255.0, a: 1),
        "mediumseagreen": RGBA(r: 60.0/255.0, g: 179.0/255.0, b: 113.0/255.0, a: 1),
        "mediumslateblue": RGBA(r: 123.0/255.0, g: 104.0/255.0, b: 238.0/255.0, a: 1),
        "mediumspringgreen": RGBA(r: 0.0/255.0, g: 250.0/255.0, b: 154.0/255.0, a: 1),
        "mediumturquoise": RGBA(r: 72.0/255.0, g: 209.0/255.0, b: 204.0/255.0, a: 1),
        "mediumvioletred": RGBA(r: 199.0/255.0, g: 21.0/255.0, b: 133.0/255.0, a: 1),
        "midnightblue": RGBA(r: 25.0/255.0, g: 25.0/255.0, b: 112.0/255.0, a: 1),
        "mintcream": RGBA(r: 245.0/255.0, g: 255.0/255.0, b: 250.0/255.0, a: 1),
        "mistyrose": RGBA(r: 255.0/255.0, g: 228.0/255.0, b: 225.0/255.0, a: 1),
        "moccasin": RGBA(r: 255.0/255.0, g: 228.0/255.0, b: 181.0/255.0, a: 1),
        "navajowhite": RGBA(r: 255.0/255.0, g: 222.0/255.0, b: 173.0/255.0, a: 1),
        "navy": RGBA(r: 0.0/255.0, g: 0.0/255.0, b: 128.0/255.0, a: 1),
        "oldlace": RGBA(r: 253.0/255.0, g: 245.0/255.0, b: 230.0/255.0, a: 1),
        "olive": RGBA(r: 128.0/255.0, g: 128.0/255.0, b: 0.0/255.0, a: 1),
        "olivedrab": RGBA(r: 107.0/255.0, g: 142.0/255.0, b: 35.0/255.0, a: 1),
        "orange": RGBA(r: 255.0/255.0, g: 165.0/255.0, b: 0.0/255.0, a: 1),
        "orangered": RGBA(r: 255.0/255.0, g: 69.0/255.0, b: 0.0/255.0, a: 1),
        "orchid": RGBA(r: 218.0/255.0, g: 112.0/255.0, b: 214.0/255.0, a: 1),
        "palegoldenrod": RGBA(r: 238.0/255.0, g: 232.0/255.0, b: 170.0/255.0, a: 1),
        "palegreen": RGBA(r: 152.0/255.0, g: 251.0/255.0, b: 152.0/255.0, a: 1),
        "paleturquoise": RGBA(r: 175.0/255.0, g: 238.0/255.0, b: 238.0/255.0, a: 1),
        "palevioletred": RGBA(r: 219.0/255.0, g: 112.0/255.0, b: 147.0/255.0, a: 1),
        "papayawhip": RGBA(r: 255.0/255.0, g: 239.0/255.0, b: 213.0/255.0, a: 1),
        "peachpuff": RGBA(r: 255.0/255.0, g: 218.0/255.0, b: 185.0/255.0, a: 1),
        "peru": RGBA(r: 205.0/255.0, g: 133.0/255.0, b: 63.0/255.0, a: 1),
        "pink": RGBA(r: 255.0/255.0, g: 192.0/255.0, b: 203.0/255.0, a: 1),
        "plum": RGBA(r: 221.0/255.0, g: 160.0/255.0, b: 221.0/255.0, a: 1),
        "powderblue": RGBA(r: 176.0/255.0, g: 224.0/255.0, b: 230.0/255.0, a: 1),
        "purple": RGBA(r: 128.0/255.0, g: 0.0/255.0, b: 128.0/255.0, a: 1),
        "rebeccapurple": RGBA(r: 102.0/255.0, g: 51.0/255.0, b: 153.0/255.0, a: 1),
        "red": RGBA(r: 255.0/255.0, g: 0.0/255.0, b: 0.0/255.0, a: 1),
        "rosybrown": RGBA(r: 188.0/255.0, g: 143.0/255.0, b: 143.0/255.0, a: 1),
        "royalblue": RGBA(r: 65.0/255.0, g: 105.0/255.0, b: 225.0/255.0, a: 1),
        "saddlebrown": RGBA(r: 139.0/255.0, g: 69.0/255.0, b: 19.0/255.0, a: 1),
        "salmon": RGBA(r: 250.0/255.0, g: 128.0/255.0, b: 114.0/255.0, a: 1),
        "sandybrown": RGBA(r: 244.0/255.0, g: 164.0/255.0, b: 96.0/255.0, a: 1),
        "seagreen": RGBA(r: 46.0/255.0, g: 139.0/255.0, b: 87.0/255.0, a: 1),
        "seashell": RGBA(r: 255.0/255.0, g: 245.0/255.0, b: 238.0/255.0, a: 1),
        "sienna": RGBA(r: 160.0/255.0, g: 82.0/255.0, b: 45.0/255.0, a: 1),
        "silver": RGBA(r: 192.0/255.0, g: 192.0/255.0, b: 192.0/255.0, a: 1),
        "skyblue": RGBA(r: 135.0/255.0, g: 206.0/255.0, b: 235.0/255.0, a: 1),
        "slateblue": RGBA(r: 106.0/255.0, g: 90.0/255.0, b: 205.0/255.0, a: 1),
        "slategray": RGBA(r: 112.0/255.0, g: 128.0/255.0, b: 144.0/255.0, a: 1),
        "slategrey": RGBA(r: 112.0/255.0, g: 128.0/255.0, b: 144.0/255.0, a: 1),
        "snow": RGBA(r: 255.0/255.0, g: 250.0/255.0, b: 250.0/255.0, a: 1),
        "springgreen": RGBA(r: 0.0/255.0, g: 255.0/255.0, b: 127.0/255.0, a: 1),
        "steelblue": RGBA(r: 70.0/255.0, g: 130.0/255.0, b: 180.0/255.0, a: 1),
        "tan": RGBA(r: 210.0/255.0, g: 180.0/255.0, b: 140.0/255.0, a: 1),
        "teal": RGBA(r: 0.0/255.0, g: 128.0/255.0, b: 128.0/255.0, a: 1),
        "thistle": RGBA(r: 216.0/255.0, g: 191.0/255.0, b: 216.0/255.0, a: 1),
        "tomato": RGBA(r: 255.0/255.0, g: 99.0/255.0, b: 71.0/255.0, a: 1),
        "turquoise": RGBA(r: 64.0/255.0, g: 224.0/255.0, b: 208.0/255.0, a: 1),
        "violet": RGBA(r: 238.0/255.0, g: 130.0/255.0, b: 238.0/255.0, a: 1),
        "wheat": RGBA(r: 245.0/255.0, g: 222.0/255.0, b: 179.0/255.0, a: 1),
        "white": RGBA(r: 255.0/255.0, g: 255.0/255.0, b: 255.0/255.0, a: 1),
        "whitesmoke": RGBA(r: 245.0/255.0, g: 245.0/255.0, b: 245.0/255.0, a: 1),
        "yellow": RGBA(r: 255.0/255.0, g: 255.0/255.0, b: 0.0/255.0, a: 1),
        "yellowgreen": RGBA(r: 154.0/255.0, g: 205.0/255.0, b: 50.0/255.0, a: 1),
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
