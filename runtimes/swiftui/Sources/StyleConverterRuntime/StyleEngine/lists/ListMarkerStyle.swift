//
//  ListMarkerStyle.swift
//  StyleEngine/lists — wave 24, lane LF (B-RC3 parts 1+2).
//
//  The resolved `list-style-*` state ONE `<li>` renders its marker with,
//  plus the cascade that produces it. Twin of Compose's
//  lists/ListStyleConfig.kt + ListStyleExtractor.resolveMarkerConfig.
//
//  Why it exists: ComponentRenderer synthesised the marker from the
//  PARENT's `meta.sourceTag` alone — `<ol>` ⇒ "1.", `<ul>` ⇒ "•" — and
//  discarded the item's own declarations. The live wire puts them on the
//  child: tools/titan/runs/wave23-final/sections/css-lists/per-test-ir/
//  wpt__css-lists__change-list-style-type-001.json carries
//  {"type":"ListStyleType","data":"square"} (also "none", "upper-roman",
//  "decimal") on each `li`, while the `ul` parent carries only
//  {"type":"ListStylePosition","data":"INSIDE"}. Every item painted a
//  plain disc.
//

import Foundation

/// The counter styles this runtime can render (css-counter-styles-3 §6
/// predefined styles). Mirrors Compose `lists.ListStyleType` 1:1 so the
/// two natives can never drift on which keywords they claim.
enum ListMarkerType: String, Equatable {
    // §6.1 symbolic. `noMarker` is `list-style-type: none` — deliberately
    // NOT spelled `none`: inside a `-> ListMarkerType?` function
    // `return .none` silently resolves to `Optional.none` (nil), which
    // made the `none` keyword fall back to the container's disc instead
    // of suppressing the marker. Caught by ListMarkerTests.
    case disc, circle, square, noMarker
    // §6.2 numeric / alphabetic
    case decimal
    case decimalLeadingZero
    case lowerAlpha, upperAlpha
    case lowerRoman, upperRoman
    // §6.2 international
    case lowerGreek, upperGreek
    case armenian, georgian, hebrew
    case cjkDecimal
    case hiragana, katakana
    case hiraganaIroha, katakanaIroha
}

/// css-lists-3 §3.5 — where the marker box sits relative to the item's
/// principal box. Resolved and carried; the GEOMETRY it should drive is
/// B-RC3 part 3 and is deliberately deferred (see ComponentRenderer's
/// marker branch for the exact deferral note).
enum ListMarkerPosition: String, Equatable {
    case inside, outside
}

/// The item's resolved marker state.
struct ListMarkerConfig: Equatable {
    var type: ListMarkerType
    /// Initial value of `list-style-position` is `outside` (css-lists-3 §3.1).
    var position: ListMarkerPosition = .outside
}

enum ListMarkerResolver {

    /// The UA-stylesheet marker family a list container hands its items —
    /// HTML §15.3.7 (`ol { list-style-type: decimal }`,
    /// `ul, menu, dir { list-style-type: disc }`). `nil` for anything that
    /// is not a list container, which is how the renderer decides NOT to
    /// synthesise a marker at all.
    static func uaDefault(sourceTag: String?) -> ListMarkerType? {
        switch (sourceTag ?? "").lowercased() {
        case "ol": return .decimal
        case "ul", "menu", "dir": return .disc
        default: return nil
        }
    }

    /// Resolve the marker configuration for one `<li>`.
    ///
    /// Order is the CSS cascade for an inherited property (css-lists-3
    /// §3.1 — all three `list-style-*` longhands are "Inherited: yes";
    /// css-cascade-4 §4.3):
    ///   1. the container's UA default for `parentTag`,
    ///   2. the container's declarations — the caller passes the already
    ///      inheritance-merged set (`InheritedText.inheritable`),
    ///   3. the item's OWN declarations, which win.
    ///
    /// ## Wave 25 — the cascade inversion this order used to have
    /// Slot 2 is a MERGED list, so an ancestor's `list-style-type` used to
    /// arrive there and beat the container's UA default. That is
    /// backwards: css-cascade-4 §4.3 consults inheritance only when the
    /// cascade produced NO value for the element, and the UA sheet's
    /// `ul { list-style-type: disc }` (HTML §15.3.7) IS a declaration on
    /// the container element. The repair is NOT a fold reorder here — an
    /// author declaration on the container must still beat the UA rule
    /// (the live `marker-text-matches-armenian` `<ol>` declares `armenian`
    /// and must keep it). It happens one step earlier, at the inheritance
    /// merge, where the container's OWN list is still separable from what
    /// it inherited: `ListStyleUaRule.apply` substitutes the UA value for
    /// an ancestor-inherited one. By the time `parentProperties` reaches
    /// this function it is already cascade-correct, so the plain
    /// parent-then-child fold below is right.
    ///
    /// `list-style-position` and `-image` have no UA declaration on
    /// `ul`/`ol`, so an ancestor's value for those legitimately still
    /// reaches the item through slot 2.
    ///
    /// - Returns: `nil` when `parentTag` is not a list container.
    static func resolve(parentTag: String?,
                        parentProperties: [IRProperty],
                        childProperties: [IRProperty]) -> ListMarkerConfig? {
        guard let ua = uaDefault(sourceTag: parentTag) else { return nil }
        var cfg = ListMarkerConfig(type: ua)
        // One last-wins fold: parent entries first, child entries last.
        for p in parentProperties + childProperties {
            switch p.type {
            case "ListStyleType":
                // nil ⇒ keyword this runtime does not model; keep the
                // running value (see `markerType(from:)`).
                if let t = markerType(from: p.data) { cfg.type = t }
            case "ListStylePosition":
                if let pos = markerPosition(from: p.data) { cfg.position = pos }
            // Wave 25: the UNEXPANDED shorthand. InheritedText.inheritedTypes
            // has always carried "ListStyle" down the inheritance channel,
            // but no branch here applied it — a wire doc carrying the
            // shorthand claimed the property and rendered nothing. It is
            // unreachable from this repo's converter (pinned by a live run
            // — see ListStyleShorthand's header) but tolerated by
            // schema/spec/05-versioning.md, so a foreign producer can emit
            // it. `image` is deliberately dropped: ListMarkerConfig models
            // no marker image and ListMarkerText paints glyphs only.
            case "ListStyle":
                if let expansion = ListStyleShorthand
                    .expand(ValueExtractors.extractKeyword(p.data)) {
                    cfg.type = expansion.type
                    cfg.position = expansion.position
                }
            default: break
            }
        }
        return cfg
    }

    /// - Returns: `nil` when the keyword names no counter style we model.
    ///
    /// KNOWN GAP (not a silent fallthrough): `@counter-style` at-rules are
    /// not carried on the IR wire, so a custom name — the live
    /// `marker-text-matches-disc` / `-circle` fixtures declare
    /// `list-style-type: my-disc` / `my-circle` — cannot be resolved to
    /// its symbols. Keeping the container's UA default (`•` under a
    /// `<ul>`) is what those two @counter-style rules happen to define,
    /// and is preferred over css-counter-styles-3 §2's "treat an
    /// UNDEFINED name as decimal" (they ARE defined — the wire lost the
    /// rule). Same decision as the Compose twin.
    static func markerType(from data: IRValue?) -> ListMarkerType? {
        guard let kw = ValueExtractors.extractKeyword(data) else { return nil }
        return markerType(fromKeyword: kw)
    }

    /// The counter-style keyword table, split out of `markerType(from:)`
    /// so ListStyleShorthand resolves a shorthand's type component from
    /// the SAME table (a second copy would be free to drift). Accepts both
    /// wire spellings — hyphenated (`upper-roman`, what the live css-lists
    /// IR carries) and underscored — and any casing.
    static func markerType(fromKeyword rawKeyword: String) -> ListMarkerType? {
        let kw = rawKeyword.lowercased().replacingOccurrences(of: "_", with: "-")
        switch kw {
        case "disc": return .disc
        case "circle": return .circle
        case "square": return .square
        case "none": return .noMarker
        case "decimal": return .decimal
        case "decimal-leading-zero": return .decimalLeadingZero
        // css-counter-styles-3 §6.2: lower-latin is an ALIAS of lower-alpha.
        case "lower-alpha", "lower-latin": return .lowerAlpha
        case "upper-alpha", "upper-latin": return .upperAlpha
        case "lower-roman": return .lowerRoman
        case "upper-roman": return .upperRoman
        case "lower-greek": return .lowerGreek
        case "upper-greek": return .upperGreek
        case "armenian": return .armenian
        case "georgian": return .georgian
        case "hebrew": return .hebrew
        case "cjk-decimal": return .cjkDecimal
        case "hiragana": return .hiragana
        case "katakana": return .katakana
        case "hiragana-iroha": return .hiraganaIroha
        case "katakana-iroha": return .katakanaIroha
        default: return nil
        }
    }

    /// - Returns: `nil` for an unrecognised keyword — same keep-the-base
    ///   rule as `markerType`. The live wire emits the position UPPERCASED
    ///   (`{"type":"ListStylePosition","data":"INSIDE"}` in the css-lists
    ///   per-test IR) while the type stays lowercase-hyphenated
    ///   (`"upper-roman"`), so the `.lowercased()` here is load-bearing.
    static func markerPosition(from data: IRValue?) -> ListMarkerPosition? {
        switch ValueExtractors.extractKeyword(data)?.lowercased() {
        case "inside": return .inside
        case "outside": return .outside
        default: return nil
        }
    }
}
