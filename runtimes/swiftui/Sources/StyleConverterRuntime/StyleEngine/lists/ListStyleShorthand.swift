//
//  ListStyleShorthand.swift
//  StyleEngine/lists — wave 25 (lane LF follow-up).
//
//  The `list-style` SHORTHAND, css-lists-3 §3.6:
//  `<'list-style-position'> || <'list-style-image'> || <'list-style-type'>`.
//  TWIN of Compose lists/ListStyleShorthand.kt — same grammar, same
//  invalid-declaration rule, same global-keyword handling.
//
//  ## Why this exists — and why it is a DEFENSIVE path
//  Both natives already CLAIMED the `ListStyle` IR type (iOS:
//  InheritedText.inheritedTypes lists it so the shorthand rides the
//  inheritance channel; Compose: ListStyleExtractor.isListStyleProperty)
//  while neither had a branch that applied it — a wire doc carrying the
//  unexpanded shorthand claimed the property and then did nothing. This
//  file makes the claim honest.
//
//  The branch is nonetheless UNREACHABLE from this repo's own converter,
//  which was checked rather than assumed (wave-25 live run,
//  `--from css --to ir` on a fixture declaring `list-style: square inside
//  url(bullet.png)` / `upper-roman` / `none` / `inherit`):
//   - ShorthandRegistry.kt maps "list-style" to ListStyleExpander, which
//     ALWAYS expands — the converter logged `Expanded 'list-style: …' →
//     list-style-type, list-style-position, list-style-image` for every
//     input;
//   - the emitted IR carried only ListStyleType / ListStylePosition /
//     ListStyleImage (plus Generic for values no longhand parser models),
//     never {"type":"ListStyle"};
//   - there is no ListStyleProperty.kt in the converter's irmodels tree
//     and no "list-style" entry in PropertyParserRegistry, so the type
//     cannot be produced at all.
//
//  schema/spec/05-versioning.md says unknown property types are
//  TOLERATED, so a FOREIGN producer may legitimately put the shorthand on
//  the wire — which is exactly the case this handles.
//

import Foundation

enum ListStyleShorthand {

    /// The three longhands a `list-style` value expands to. `image` is
    /// carried for twin-parity with Compose's ListStyleConfig even though
    /// ListMarkerConfig models no marker image today — the iOS marker
    /// painter (ListMarkerText) emits glyphs only. Documented gap, not a
    /// silent drop: the resolver reads `type`/`position` and ignores
    /// `image` explicitly.
    struct Expansion: Equatable {
        var type: ListMarkerType
        var position: ListMarkerPosition
        var image: String?
    }

    /// css-lists-3 §3.5 — the two `list-style-position` keywords.
    private static let positionKeywords: Set<String> = ["inside", "outside"]

    /// Expand a `list-style` shorthand value.
    ///
    /// Shorthand semantics (css-cascade-4 §3): components the value omits
    /// are set to their INITIAL values, not left at whatever the element
    /// inherited — so `list-style: square` also forces `outside` / no
    /// image. That is why this returns a FULL expansion rather than a
    /// partial patch.
    ///
    /// - Returns: `nil` whenever the declaration is invalid or carries a
    ///   global keyword we cannot expand — the caller must then keep its
    ///   running value. CSS drops an invalid declaration wholesale
    ///   (css-syntax-3 §9), so that is the faithful behaviour rather than
    ///   a silent fallthrough.
    static func expand(_ raw: String?) -> Expansion? {
        let value = (raw ?? "").trimmingCharacters(in: .whitespaces)
        guard !value.isEmpty else { return nil }

        switch value.lowercased() {
        // css-cascade-4 §7.3: on an INHERITED property (all three
        // list-style longhands are "Inherited: yes", css-lists-3 §3.1)
        // both `inherit` and `unset` mean "take the parent's computed
        // value" — which the inheritance merge already put into the
        // caller's running config. Keeping it IS the resolution.
        case "inherit", "unset": return nil
        // `revert`/`revert-layer` roll back to the previous cascade
        // ORIGIN, which the runtime does not model (no per-origin
        // declaration stack exists). Documented gap: the running value
        // stands, which for a list container is the UA default
        // ListStyleUaRule already installed — right in the common
        // revert-to-UA case, wrong only when an author rule in an earlier
        // layer should have re-emerged.
        case "revert", "revert-layer": return nil
        // Every longhand back to its initial value (css-lists-3 §3.1).
        case "initial": return Expansion(type: .disc, position: .outside, image: nil)
        default: break
        }

        var type: ListMarkerType?
        var position: ListMarkerPosition?
        var image: String?
        // `none` is ambiguous between the type and image components; count
        // the occurrences and assign them after the explicit ones.
        var nones = 0

        for token in tokenize(value) {
            let lower = token.lowercased()
            if lower == "none" {
                nones += 1
            } else if positionKeywords.contains(lower) {
                // A repeated component makes the whole shorthand invalid.
                guard position == nil else { return nil }
                position = (lower == "inside") ? .inside : .outside
            } else if lower.hasPrefix("url("), lower.hasSuffix(")") {
                guard image == nil else { return nil }
                image = unquote(String(token.dropFirst(4).dropLast()))
            } else {
                // Anything left must be a counter-style name. An unknown
                // one (a custom @counter-style ident the wire cannot
                // carry — the same gap ListMarkerResolver.markerType
                // documents) invalidates the declaration rather than
                // half-applying it.
                guard let parsed = ListMarkerResolver.markerType(fromKeyword: lower),
                      type == nil else { return nil }
                type = parsed
            }
        }

        // css-lists-3 §3.6: a single `none` sets BOTH list-style-type and
        // list-style-image to none; two `none`s set one each. Fill the
        // still-empty slots in that order. (Image's initial value IS none,
        // so filling it changes nothing observable — the step exists to
        // count slots, which is what makes a third `none` detectably
        // invalid.)
        var remaining = nones
        if remaining > 0 && type == nil { type = .noMarker; remaining -= 1 }
        if remaining > 0 && image == nil { remaining -= 1 }
        guard remaining == 0 else { return nil }

        return Expansion(type: type ?? .disc,
                         position: position ?? .outside,
                         // Omitted ⇒ initial ⇒ `none` ⇒ nil here.
                         image: image)
    }

    /// Strip one layer of matching quotes from a `url()` body.
    private static func unquote(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespaces)
        for quote in ["\"", "'"] where trimmed.hasPrefix(quote) && trimmed.hasSuffix(quote)
            && trimmed.count >= 2 {
            return String(trimmed.dropFirst().dropLast())
        }
        return trimmed
    }

    /// Whitespace split that does NOT break inside parentheses, so
    /// `url(a b.png)` survives as one token. Mirrors the converter's
    /// ListStyleExpander.tokenize so reader and runtime agree on where a
    /// component ends.
    private static func tokenize(_ value: String) -> [String] {
        var tokens: [String] = []
        var current = ""
        var depth = 0
        for char in value {
            if char == "(" { depth += 1; current.append(char) }
            else if char == ")" { depth -= 1; current.append(char) }
            else if char.isWhitespace && depth == 0 {
                if !current.isEmpty { tokens.append(current); current = "" }
            } else { current.append(char) }
        }
        if !current.isEmpty { tokens.append(current) }
        return tokens
    }
}
