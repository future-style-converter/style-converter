//
//  ListItemMarkerGate.swift
//  StyleEngine/lists — wave 30, lane 3 (fix B1).
//
//  Does a box generate a `::marker` from its OWN `display`? BYTE-PARALLEL
//  TWIN of Compose lists/ListItemMarkerGate.kt: same four predicates, same
//  names, same citations, same measured justification. Change one, change
//  both.
//
//  ## The defect this repairs (measured on the LIVE wave29-final section)
//  Both natives gated marker synthesis on the PARENT alone —
//  `ListMarkerResolver.uaDefault(sourceTag: parentTag) != nil` plus a
//  child `sourceTag == "li"`. css-lists-3 §3.1 is explicit that the marker
//  follows the BOX, not the tag: "an element with `display: list-item`
//  generates a `::marker` pseudo-element". A
//  `<div style="display:list-item">` — which is exactly what
//  `css-lists/change-list-style-position-002` and `-003` are built from —
//  therefore rendered with NO marker on either native, while the web
//  runtime just sets `display:list-item` and lets Chromium generate it.
//
//  Ink columns of tools/titan/runs/wave29-final/sections/css-lists/
//  {screenshots,ios-screenshots}/wpt__css-lists__change-list-style-
//  position-003.png:
//
//      rows    web                                iOS / Android
//      16–20   outer border-top                   outer border-top
//      21–40   LEADING LINE BOX + disc @30–34     — (absent)
//      41–45   inner border-top                   21–25
//      46–65   inner line box + `1.` @50–61       26–45 (`text`)
//
//  i.e. the whole inner box sits 20px — one line box — too high on both
//  natives and neither marker is painted. min-SSIM 0.9317 (iOS↔web) /
//  0.9314 (Android↔web), the pair's only failing term.
//
//  ## The placement this gate selects
//  The marker of a `display: list-item` box is that box's FIRST INLINE BOX
//  (css-lists-3 §3.2). Its in-flow content on all three runtimes is
//  BLOCK-level — web renders `_text` through its own placeholder node and
//  the natives through `PlaceholderLabel` / `PlaceholderContent` — so the
//  marker can never share a line with it and owns a LEADING line box. That
//  is what web renders above (`1.` on one line, `text` on the next) and
//  what the natives must render to match. The browser REFERENCE puts
//  `1. text` on ONE line, because there the item's text is a real inline
//  run; that divergence is shared by all three runtimes and is NOT this
//  gate's to fix (it needs the interleaved inline-run IR shape
//  `contentOrPlaceholder`'s Bug-1 note already calls out).
//

import Foundation

enum ListItemMarkerGate {

    /// The wire keyword `display: list-item` computes to (schema 02-values).
    private static let listItemKeyword = "LIST_ITEM"

    /// Is this box a list item BY ITS OWN `display` (css-lists-3 §3.1)?
    ///
    /// Last-wins over the property list, matching every other extractor in
    /// this package: a later `Display` entry shadows an earlier one.
    /// `ValueExtractors.normalize` accepts both wire spellings — the live
    /// v2 wire emits the UPPER_SNAKE `"LIST_ITEM"`, a foreign producer
    /// tolerated by schema/spec/05-versioning.md may emit the CSS
    /// `"list-item"`.
    static func isListItemDisplay(_ properties: [IRProperty]) -> Bool {
        var listItem = false
        for p in properties where p.type == "Display" {
            guard let keyword = ValueExtractors.extractKeyword(p.data) else { continue }
            listItem = ValueExtractors.normalize(keyword) == listItemKeyword
        }
        return listItem
    }

    /// The marker state a SELF-MARKING list item resolves, i.e. with no
    /// list container to supply a UA default.
    ///
    /// The base is `ListMarkerConfig`'s own default — `disc` / `outside`,
    /// the INITIAL values of `list-style-type` and `-position` (css-lists-3
    /// §3.1). That is the right base here precisely because there is no
    /// container: HTML §15.3.9's `ul { list-style-type: disc }` /
    /// `ol { … decimal }` are declarations on the CONTAINER element, and a
    /// `display: list-item` `<div>` matches neither.
    ///
    /// - Parameter properties: the item's INHERITANCE-MERGED list — all
    ///   three `list-style-*` longhands are "Inherited: yes" (css-lists-3
    ///   §3.1), so an ancestor's declaration legitimately reaches this box.
    static func ownMarkerConfig(_ properties: [IRProperty]) -> ListMarkerConfig {
        // Routed through the shared resolver with a `ul` stand-in ONLY to
        // reuse its fold; the disc base it installs is the same initial
        // value the CSS default gives, and every declaration on the element
        // still wins over it (last-wins fold, child slot).
        ListMarkerResolver.resolve(parentTag: "ul",
                                   parentProperties: [],
                                   childProperties: properties)
            ?? ListMarkerConfig(type: .disc)
    }

    /// Must this component paint its OWN leading marker line box?
    ///
    /// All four gates are load-bearing:
    ///
    /// - `display: list-item` — the css-lists-3 §3.1 rule itself.
    /// - `sourceTag != "li"`. An `<li>` under a list container is ALREADY
    ///   served by the parent-loop marker path (`markerPlacement` here,
    ///   `RenderListItemMarker` on Compose), and the live wire puts
    ///   `display: list-item` on those too
    ///   (`change-list-style-type-002`, `add-inline-child-after-marker-001`
    ///   / `-002`, `change-list-style-position-001` all carry it on the
    ///   `li`), so without this gate every one of them would paint TWO
    ///   markers. KNOWN GAP, unchanged by this lane: an `<li>` whose parent
    ///   is not a list container still gets no marker on either path — no
    ///   document in the corpus has that shape.
    ///
    ///   A SECOND, iOS-only double-paint shape is left open deliberately
    ///   and stated here rather than silently: this renderer's parent-loop
    ///   marker branch is gated on a non-empty marker STRING (a baked
    ///   `meta.markerText` supplies one for ANY child, tagged or not),
    ///   where Compose's is gated on `isListParent && childTag == "li"` —
    ///   a pre-existing wave-27 divergence, not one this lane made. A
    ///   TAGLESS child that carries BOTH a baked `meta.markerText` and
    ///   `display: list-item; list-style-position: inside` would therefore
    ///   take the row marker here and this leading one as well. Closing it
    ///   needs the parent's decision at the child's own content emitter,
    ///   which crosses a `ComponentHost` boundary; no document in the
    ///   corpus has the shape (every baked marker in it sits on a tagged
    ///   `<li>`, which this gate already excludes).
    /// - `position == .inside` only. css-lists-3 §3.2 puts an `outside`
    ///   marker in the item's MARGIN area, left of the border box — a
    ///   LEADING LINE BOX is the `inside` geometry and would instead push
    ///   the item's whole content down by a line the browser does not
    ///   have. MEASURED, which is why this is a gate and not a TODO — and
    ///   stated here as measured, because an earlier draft of this comment
    ///   got the fixture wrong: `change-list-style-position-002` is three
    ///   NESTED `display: list-item` boxes of which only the INNERMOST
    ///   declares `list-style-position: inside` (per-test IR:
    ///   tools/titan/runs/wave29-final/sections/css-lists/per-test-ir/
    ///   wpt__css-lists__change-list-style-position-002.json — the outer
    ///   two carry no position at all, i.e. the initial `outside`). So the
    ///   gate DOES fire on that innermost box; that is the correct §3.2
    ///   answer for an `inside` marker and it is measurably small — the
    ///   wave-30 skeptic's A/B bounds that single firing at ~0.0002 SSIM
    ///   (0.9951 against the pre-gate 0.9953). What the gate protects is
    ///   the OUTER two: the reference hangs their markers LEFT of the
    ///   border box (ink columns 39/57/… against a content edge at 56), so
    ///   emitting a leading line box for them WOULD be a real regression.
    ///   The document's live wave29-final scores, for the record, are
    ///   0.9998 iOS↔Android / 0.9953 iOS↔web / 0.9953 Android↔web — not
    ///   the "1.0000 / 1.0000 / 0.9992" previously claimed here.
    ///   The margin-area hang is the still-deferred B-RC3 part 3 geometry
    ///   already documented at both renderers' marker branches.
    /// - a non-empty marker string — `list-style-type: none` generates NO
    ///   marker box at all (css-lists-3 §3.1), so the content must start at
    ///   the item's own content edge.
    static func rendersOwnLeadingMarker(sourceTag: String?,
                                        properties: [IRProperty]) -> Bool {
        guard isListItemDisplay(properties) else { return false }
        guard sourceTag?.lowercased() != "li" else { return false }
        guard ownMarkerConfig(properties).position == .inside else { return false }
        return !ownMarkerText(properties).isEmpty
    }

    /// The marker string a self-marking list item paints.
    ///
    /// KNOWN GAP (documented, not a silent fallthrough): the ordinal is
    /// always 1. css-lists-3 §4 increments the `list-item` counter once per
    /// list item in document order, so a RUN of sibling
    /// `display:list-item` boxes should read 1, 2, 3 — reaching that number
    /// needs the sibling index at this call site, which the own-marker path
    /// (emitted from INSIDE the item's own content, not from its parent's
    /// children loop) does not have. Both corpus documents that reach this
    /// path (`change-list-style-position-002` / `-003`) nest their list
    /// items rather than making them siblings, so every marker there IS the
    /// first of its list and the number is correct.
    static func ownMarkerText(_ properties: [IRProperty]) -> String {
        ListMarkerText.marker(index: 0, config: ownMarkerConfig(properties))
    }
}
