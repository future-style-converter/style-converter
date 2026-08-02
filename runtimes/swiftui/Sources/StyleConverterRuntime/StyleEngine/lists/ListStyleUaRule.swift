//
//  ListStyleUaRule.swift
//  StyleEngine/lists — wave 25 (lane LF follow-up).
//
//  The UA `list-style-type` rule as a CASCADE step, applied where the
//  inheritance merge happens. TWIN of Compose
//  lists/ListStyleUaRule.kt — same rule, same gaps, same identity
//  fast-paths. Change one, change both.
//
//  ## The defect this repairs
//  Wave 24 resolved a <li>'s marker by folding: the container's UA
//  default → the container's INHERITANCE-MERGED declarations → the
//  item's own. Slot 2 is merged, so a `list-style-type` declared on an
//  ANCESTOR (body, an outer div) arrived there and beat the container's
//  UA default. That is the wrong cascade.
//
//  ## The correct semantics (verified against the specs, not guessed)
//  css-cascade-4 §4.3 — inheritance applies ONLY when the cascade
//  produced no value for the element: "if the cascade results in a
//  value, use it; otherwise, if the property is inherited … use the
//  computed value of the parent". An inherited value therefore LOSES to
//  any declaration on the element itself, whatever its origin — and the
//  UA stylesheet's `ul, menu, dir { list-style-type: disc }` /
//  `ol { list-style-type: decimal }` (HTML §15.3.9, verbatim in
//  Chromium's html.css) IS such a declaration, matching the container
//  ELEMENT. So for a <ul> with no author list-style-type the cascade
//  yields `disc` and inheritance is never consulted; the <li> — for
//  which the UA sheet declares no list-style-type — then inherits `disc`
//  from it. An ancestor's `list-style-type: square` never reaches the
//  marker.
//
//  An author declaration ON the container still wins (author beats UA,
//  css-cascade-4 §6.1) — which is why the fix cannot be a plain fold
//  reorder inside ListMarkerResolver: the live
//  `content-property/marker-text-matches-armenian` fixture puts
//  `list-style-type: armenian` on the <ol> itself and must keep painting
//  Armenian rather than the UA `decimal`.
//
//  ## Why here and not in the marker resolver
//  ListMarkerResolver receives ONE already-merged parent list, in which
//  an ancestor-inherited entry and the container's own are
//  indistinguishable (InheritedText.merge produces
//  `inherited-not-declared + own`, at most one entry per type). The merge
//  site is the only place still holding both lists, so the UA rule runs
//  there and every downstream consumer — the marker resolver, the
//  channel republished to descendants — then reads a cascade-correct
//  value.
//
//  ## KNOWN GAP (documented, not a silent fallthrough)
//  Chromium's html.css additionally declares `ul ul, ol ul { list-style-
//  type: circle }` and `ol ol ul, ol ul ul, ul ol ul, ul ul ul {
//  list-style-type: square }`. Those depend on NESTING DEPTH, which
//  neither this helper nor ListMarkerResolver.uaDefault is given — a
//  nested <ul> therefore still resolves to `disc` where a browser shows
//  `circle`. No fixture in the pinned corpus nests lists
//  (tools/titan/runs/wave24-final/sections/css-lists/per-test-ir/ — every
//  ul/ol there is a capture root), so the gap is unexercised today.
//

import Foundation

enum ListStyleUaRule {

    /// Property types whose presence on the element counts as "the author
    /// declared list-style-type here". `ListStyle` is the unexpanded
    /// shorthand (see ListStyleShorthand) — it carries a type component,
    /// so it blocks the UA substitution exactly like the longhand.
    static let typeDeclaring: Set<String> = ["ListStyleType", "ListStyle"]

    /// Replace an ancestor-inherited `ListStyleType` on a LIST CONTAINER
    /// with that container's UA-declared value.
    ///
    /// - Parameters:
    ///   - sourceTag: the element's `meta.sourceTag`; only ul/ol/menu/dir
    ///     carry a UA `list-style-type` declaration.
    ///   - own: the element's OWN declarations (pre-merge, after the
    ///     bucket/media/scheme fold) — a type here means the author
    ///     declared it and the UA rule loses.
    ///   - merged: the inheritance-merged list to correct.
    /// - Returns: `merged` unchanged whenever the rule does not fire (the
    ///   frozen-baseline byte-stability rule).
    static func apply(sourceTag: String?,
                      own: [IRProperty],
                      merged: [IRProperty]) -> [IRProperty] {
        // Only a list container has a UA list-style-type declaration.
        let uaKeyword: String
        switch ListMarkerResolver.uaDefault(sourceTag: sourceTag) {
        case .disc: uaKeyword = "disc"
        case .decimal: uaKeyword = "decimal"
        // uaDefault returns ONLY those two, or nil for a non-container.
        // Anything else means its table grew without this mapping: leave
        // the list untouched rather than invent a keyword — the marker
        // resolver still applies the enum default it derives from
        // parentTag, so nothing is silently dropped.
        default: return merged
        }
        // An own declaration (author origin) beats the UA rule — nothing
        // to do. This is the marker-text-matches-* shape.
        guard !own.contains(where: { typeDeclaring.contains($0.type) }) else {
            return merged
        }
        // No inherited entry to displace ⇒ the resolver's UA base already
        // stands. Early return keeps every existing capture bit-stable.
        guard merged.contains(where: { typeDeclaring.contains($0.type) }) else {
            return merged
        }
        let uaEntry = IRProperty(type: "ListStyleType", data: .string(uaKeyword))
        // An inherited `ListStyle` SHORTHAND carries three components at
        // once, and only ONE of them (the type) is displaced by the UA
        // rule: ul/ol have no UA list-style-position/-image declaration,
        // so those two keep inheriting (probe: Chromium gives
        // `list-style-position: inside` on the <li> of
        // `<div style="list-style-position:inside"><ul><li>`). Replacing
        // the whole entry would silently drop them — so instead KEEP the
        // shorthand and append the UA type after it; the resolver's fold
        // is last-wins, so the UA type overrides the shorthand's type
        // component while its position/image survive.
        if merged.contains(where: { $0.type == "ListStyle" }) {
            return merged.filter { $0.type != "ListStyleType" } + [uaEntry]
        }
        // Longhand-only path — substitute IN PLACE so the list order
        // (which several extractors read as last-wins) is otherwise
        // untouched: the first type-declaring entry becomes the UA value,
        // any further one is dropped as now-shadowed.
        var substituted = false
        return merged.compactMap { property in
            guard typeDeclaring.contains(property.type) else { return property }
            guard !substituted else { return nil }
            substituted = true
            return uaEntry
        }
    }
}
