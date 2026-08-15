//
//  PseudoRunStyle.swift
//  StyleEngine/content — wave 43, lane V7 (styled pseudo text).
//
//  The RAW-string → TYPED IRProperty conversion for the styling
//  declarations a `pseudos` bucket may carry. Wave-42's bridge refused
//  every bucket declaring styling, so the census's styled population —
//  MEASURED against wave42-final per-test-ir: `color: red` ×7
//  (css-display/display-contents-dynamic-before-after-001) and
//  `font-size: 3em` ×1 (css-contain/contain-content-011, whose Chromium
//  ref paints the counter number at 48px) — rendered NOTHING on iOS.
//
//  WHY TYPED OUTPUT: the fold rewrites `component.text`, and the ONE
//  channel that styles that text is the component's typed property list
//  (ColorExtractor/FontSizeExtractor/FontFamilyExtractor). So the honest
//  bridge for a styled pseudo is raw string → the EXACT typed wire shapes
//  those extractors document, appended to the folded copy's properties by
//  PseudoTextFold — never a second styling channel that could drift.
//  font-weight is deliberately absent: the wave-43 census counts ZERO
//  before/after buckets declaring it — wire it when the corpus carries one.
//  Mirrors the Compose twin (content/PseudoStyledDeclarations.kt) shape
//  for shape so the natives cannot diverge on the same bucket.
//

import Foundation

/// Pure conversion table — never instantiated (mirrors PseudoTextBridge).
enum PseudoRunStyle {

    /// Outcome of converting ONE raw styling declaration.
    enum Conversion {
        /// Converted — append `property` to the folded copy's typed list.
        case typed(IRProperty)
        /// Consumed as a spec-level no-op: the declaration asks for what
        /// the run already does (css-cascade-5 §7.3 CSS-wide keywords on
        /// inherited properties collapse to "no own declaration").
        case inert
        /// Beyond this conversion — the caller refuses the bucket and
        /// names the declaration (a half-styled fold would lie).
        case unsupported
    }

    /// css-cascade-5 §7.3 CSS-wide keywords. color / font-size /
    /// font-family are all INHERITED properties, so inherit / unset /
    /// revert(-layer) resolve to the inherited value and initial to the
    /// UA default — exactly what an undeclared pseudo run renders.
    private static let cssWide: Set<String> = [
        "inherit", "initial", "unset", "revert", "revert-layer",
    ]

    /// Convert one raw declaration to its typed IR form, or nil when
    /// `prop` is not a styling declaration this file owns (the bridge's
    /// walk keeps its own handling for content/display/counter-*).
    ///
    /// - Parameters:
    ///   - prop: declaration name, trimmed + lowercased by the caller
    ///     (css-syntax-3 §5: names are ASCII-case-insensitive).
    ///   - raw: the author string, verbatim from the wire.
    ///   - emBasePx: the resolution base for em/% font-size — the
    ///     ORIGINATING element's own font-size (css-values-4 §5.1.1: em on
    ///     font-size resolves against the inherited size, and the pseudo's
    ///     parent IS its originating element), threaded by the fold.
    static func convert(prop: String, raw: String, emBasePx: Double) -> Conversion? {
        switch prop {
        // css-color-4 §3.1: `color` sets the text ink of the run.
        case "color": return colorConversion(raw)
        // css-fonts-4 §2.4: `font-size` sets the run's own glyph size.
        case "font-size": return fontSizeConversion(raw, emBasePx: emBasePx)
        // css-fonts-4 §2.1: `font-family` sets the run's face list.
        case "font-family": return fontFamilyConversion(raw)
        // Anything else is outside this file's ownership.
        default: return nil
        }
    }

    /// `color: <raw>` → the typed `{"srgb":{r,g,b,a}}` wire shape
    /// `extractColor` reads (0–1 floats, spec 02-values.md normalization).
    private static func colorConversion(_ raw: String) -> Conversion {
        // Keywords are case-insensitive; classify on a trimmed copy.
        let t = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        // CSS-wide keywords collapse to the run's existing default ink.
        if cssWide.contains(t) { return .inert }
        // The SHARED post-substitution token parser (hex / rgb() / the
        // css-color-4 §6.1 named table) — one color vocabulary with the
        // var() lane, generated in lockstep with the Compose twin.
        guard let c = CSSTokenParser.color(raw) else { return .unsupported }
        // Emit the exact IRColor srgb block the typed extractor reads,
        // plus `original` so the payload stays debuggable like the wire's.
        return .typed(IRProperty(type: "Color", data: .object([
            // Pre-resolved sRGB — the static-color path of extractColor.
            "srgb": .object([
                "r": .double(c.r), "g": .double(c.g),
                "b": .double(c.b), "a": .double(c.a),
            ]),
            // The author string, mirrored like converter output does.
            "original": .string(t),
        ])))
    }

    /// `font-size: <raw>` → the `{"px": N}` blob FontSizeExtractor
    /// unwraps. Unlike the Compose twin this resolves em/%/rem HERE:
    /// the iOS extractor has no symbolic branch, and the fold's call
    /// site owns the correct base anyway (the host's own size — see
    /// `convert`'s emBasePx doc).
    private static func fontSizeConversion(_ raw: String, emBasePx: Double) -> Conversion {
        // Keywords are case-insensitive; numbers unaffected by lowercase.
        let t = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        // CSS-wide keywords collapse to the run's existing inherited size.
        if cssWide.contains(t) { return .inert }
        // Split "3em" → number prefix + unit suffix (css-syntax-3 §4.3.11:
        // a dimension token is a number immediately followed by its unit).
        let digits = t.prefix { $0.isNumber || $0 == "." || $0 == "-" || $0 == "+" }
        let unit = t.dropFirst(digits.count).trimmingCharacters(in: .whitespaces)
        // A non-numeric prefix (keyword sizes, calc(), var()) is beyond
        // this conversion — the bridge refuses and names it, never guesses.
        guard let v = Double(digits) else { return .unsupported }
        // Negative font sizes are invalid per css-fonts-4 §2.4 — refuse.
        guard v >= 0 else { return .unsupported }
        // Every supported unit resolves to canonical 96dpi px (spec 02).
        let px: Double
        switch unit {
        case "px": px = v                       // canonical already.
        case "pt": px = v * 4.0 / 3.0           // 1pt = 1/72in → 4/3 px.
        case "pc": px = v * 16.0                // 1pc = 12pt = 16px.
        case "in": px = v * 96.0                // 96 px per CSS inch.
        case "cm": px = v * 96.0 / 2.54         // metric via the CSS inch.
        case "mm": px = v * 96.0 / 25.4
        case "q":  px = v * 96.0 / 25.4 / 4.0   // 1Q = 1/4 mm.
        // em/% resolve against the threaded inherited base (css-values-4
        // §5.1.1 / css-fonts-4 §2.4 — both use the inherited size).
        case "em": px = v * emBasePx
        case "%":  px = v / 100.0 * emBasePx
        // rem resolves against the ROOT size — the 16px browser default
        // the harness pins (documents never style the root font-size).
        case "rem": px = v * 16.0
        // vw/ch/… have no static base at this seam — refused, not forged.
        default: return .unsupported
        }
        // The plain px blob — FontSizeExtractor.extract reads exactly this.
        return .typed(IRProperty(type: "FontSize", data: .object(["px": .double(px)])))
    }

    /// `font-family: <raw>` → the array-of-strings shape
    /// FontFamilyExtractor walks (bare strings classify as face names or
    /// generic keywords there — css-fonts-4 §5.2 prioritised list).
    private static func fontFamilyConversion(_ raw: String) -> Conversion {
        // Keyword check on the whole value: `font-family: inherit` is a
        // CSS-wide keyword, not a family named "inherit" (css-fonts-4 §2.1).
        let t = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        // The wave-43 census's entire font-family population is `inherit`
        // (×27, all ::marker) — the run's default face already IS the
        // inherited/document face at this seam, so: no-op.
        if cssWide.contains(t) { return .inert }
        // Top-level comma split — family names cannot contain unquoted
        // commas (css-fonts-4 §2.1 grammar); quotes are stripped here
        // because FontFamilyExtractor matches bare names verbatim.
        let names = raw.split(separator: ",").map {
            $0.trimmingCharacters(in: .whitespacesAndNewlines)
                .trimmingCharacters(in: CharacterSet(charactersIn: "\"'"))
        }.filter { !$0.isEmpty }
        // An empty list is a malformed declaration — refuse rather than
        // emit a typed payload that claims "declared but empty".
        guard !names.isEmpty else { return .unsupported }
        // The array-of-bare-strings shape the extractor classifies.
        return .typed(IRProperty(type: "FontFamily",
                                 data: .array(names.map { .string($0) })))
    }
}
