//
//  TextDecorationLineExtractor.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextDecorationLineProperty { static let name = "TextDecorationLine" }

enum TextDecorationLineExtractor {
    static func extract(from properties: [IRProperty]) -> TextDecorationLineConfig? {
        var cfg = TextDecorationLineConfig()
        var touched = false
        for prop in properties where prop.type == TextDecorationLineProperty.name {
            touched = true
            // Gather keywords from string, array-of-strings, or object forms.
            let tokens = gatherTokens(prop.data)
            // Reset per-prop so later occurrences cleanly override.
            cfg = TextDecorationLineConfig()
            // Wave 38 (lane N3) — the css-text-decor-3 §2.1 `||` GRAMMAR
            // GATE. `underline || overline || line-through || blink` admits
            // each keyword AT MOST ONCE and `none` is a standalone
            // alternative, so `blink blink` / `blink underline blink` /
            // `none underline` are INVALID: css-syntax-3 §9 drops the whole
            // declaration and the property keeps its initial `none`. The
            // live wire carries exactly those values (the converter's
            // parser admits duplicates — measured on
            // css/css-text-decor/text-decoration-line.html), and unlike web
            // this runtime has no browser downstream to re-validate them,
            // so the gate lands here. LOUD, never a silent fallthrough; the
            // reset `cfg` above is already the all-false `none` state, so
            // `continue` leaves the property at its initial value.
            guard DecorationColorOps.lineListIsValid(tokens) else {
                _ = PropertyTracker.logOnce(
                    key: "TextDecorationLine.invalid.\(tokens.joined(separator: " "))",
                    message: "TextDecorationLine(invalid '\(tokens.joined(separator: " "))' — "
                        + "css-text-decor-3 §2.1 || allows each keyword once; declaration dropped)")
                continue
            }
            for tok in tokens {
                switch tok {
                case "underline":    cfg.underline = true
                case "overline":     cfg.overline = true
                case "line-through": cfg.lineThrough = true
                case "blink":        cfg.blink = true
                default: break    // `none` / unknown → no flags
                }
            }
        }
        return touched ? cfg : nil
    }

    // Normalise the various IR shapes to a flat list of lower-cased tokens.
    // Lane IOS-TEXT fix 7: the v2 wire carries the converter's ENUM names
    // (`["LINE_THROUGH"]`, live output on fixtures/properties/typography),
    // so a bare `.lowercased()` produced "line_through" — which silently
    // missed the "line-through" switch case above and DROPPED the
    // strikethrough (a no-silent-fallthrough violation: the default branch
    // swallowed a value the platform fully supports). Underscores are
    // normalised to hyphens so enum-name tokens and CSS keyword tokens
    // land on the same canonical spelling.
    private static func gatherTokens(_ v: IRValue) -> [String] {
        // Shared canonicaliser: CSS keywords are ASCII case-insensitive
        // (css-text-decor-3 §2.1) and the wire's enum spelling swaps the
        // hyphen for an underscore — undo both. Wave 38 (lane N3) routes it
        // through DecorationColorOps so the ONE spelling rule serves the
        // keyword table, this extractor and the `||` validity gate — three
        // readers that must never disagree about token identity.
        func canon(_ s: String) -> String {
            DecorationColorOps.canonicalLineToken(s)
        }
        if let s = v.stringValue {
            return s.split(separator: " ").map { canon(String($0)) }
        }
        if case .array(let entries) = v {
            return entries.compactMap { $0.stringValue.map(canon) }
        }
        if case .object(let o) = v, let kw = o["keyword"]?.stringValue {
            return [canon(kw)]
        }
        return []
    }
}
