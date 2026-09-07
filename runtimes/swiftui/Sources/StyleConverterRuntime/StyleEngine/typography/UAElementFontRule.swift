//
//  UAElementFontRule.swift
//  StyleEngine/typography — wave-40 lane T7 (iOS depth tails).
//
//  The UA stylesheet's ELEMENT-KEYED font declarations, applied as a
//  CASCADE step at the inheritance-merge seam — the exact shape and the
//  exact site of lists/ListStyleUaRule.swift, for the exact same reason.
//
//  ## The defect this closes (measured, not theoretical)
//
//  The natives carry no user-agent stylesheet. Web does not need one: the
//  harness renders a real `<h3>`/`<sup>` and Chromium's own html.css supplies
//  `h3 { font-size: 1.17em; font-weight: bold }` — which is precisely why the
//  web column passes every cell this rule is aimed at while iOS paints the
//  heading at the plain 16px body face.
//
//  MEASURED on the frozen wave39-final refs
//  (tools/wpt/refs/9b5435e5…/white-black-ink-font-lh-imgpad-htmlpins):
//   * css-text-decor/text-decoration-color — an `<h3>` with NO author font
//     declaration. Ref rows 39…52 and 62…75 carry the two heading lines, a
//     23px line pitch; the ref body pins `line-height: 1.25`
//     (capture-browser-ref.mjs REF_LINE_HEIGHT), so the ref's heading face is
//     23 / 1.25 = 18.4px ≈ 1.17 × 16. iOS painted it at 16px REGULAR and
//     scored 0.6185 against web's PASS.
//   * css-text-decor/text-decoration-inset-014 — `h1 { font-family: monospace }`,
//     no font-size. Ref underline row y=152 spans x=71…258 = 187px for
//     "ultra-quick" + the test's 0.5ch inset each side = 12ch, i.e. 15.58px
//     per `ch`; a 0.6em monospace advance puts the ref face at 25.97 ≈ 26px =
//     2 × the 13px monospace UA default (MonospaceUAFontSize.fixedDefaultPx).
//     iOS painted 13px.
//
//  ## Why the em base is the element's OWN medium, not the parent's size
//
//  The second measurement is the load-bearing one. Chromium's html.css
//  declares `h1 { font-size: 2em }`, and 2 × 26px only comes out at 26 if the
//  `em` resolves against the 13px FIXED default rather than the 16px parent —
//  Blink's monospace quirk applies to a non-absolute size, which a UA `em` is.
//  So the base here is `MonospaceUAFontSize.resolvePx(...) ?? 16`, reusing the
//  SAME gate StyleBuilder consults, so the two can never disagree about which
//  default a family resolves to.
//
//  ## Cascade position (css-cascade-4 §4.3 / §6.1)
//
//  A UA declaration is a declaration ON THE ELEMENT: it BEATS inheritance
//  (inheritance is consulted only when the cascade produced nothing) and LOSES
//  to the author's own declaration. So the rule fires only when the element's
//  OWN list declares nothing in the guarded family, and it SUBSTITUTES the
//  inherited entry in place rather than appending — StyleBuilder reads
//  `properties.first(where: FontSize)`, so an appended entry would sit behind
//  the inherited one and never be seen.
//
//  ## Scope, stated rather than discovered
//
//  Keyed on `meta.sourceTag`, which the 327-pair product corpus never carries
//  (`grep -rn '"_tag"' fixtures/properties fixtures/components fixtures/fidelity`
//  → nothing), so every committed baseline is byte-identical by construction.
//  Across all 30 depth-48 sections of wave39-final exactly 8 tests carry a
//  heading with no author font-size and 8 carry a bare sup/sub — and ALL of
//  them fail on iOS today, so this rule cannot cost a passing cell.
//
//  ## KNOWN GAPS (named, not hidden)
//
//   * `vertical-align: super/sub` — the other half of the UA sup/sub rule — is
//     NOT modelled. The SwiftUI block container has no inline line box (see
//     Renderer/InlineRunPlan.swift's HONEST SCOPE note), so a sup is painted as
//     its own stacked label and there is no baseline to shift it against.
//     Shipping the size half alone is the honest subset: it removes ink the ref
//     does not have, and adds none the ref does.
//   * The UA heading MARGINS (`h1 { margin: 0.67em 0 }` …) are NOT injected
//     here — spacing/UABlockMargin.swift already owns them for the composed
//     capture, resolved at each heading's own face size, and duplicating them
//     would double every heading's block advance.
//   * `font-size: smaller` is modelled as parent ÷ 1.2. Blink resolves the
//     keyword through its absolute-size TABLE when the parent size itself came
//     from a keyword (16px `medium` → 13px `small`), which differs from
//     16 / 1.2 = 13.33 by a third of a pixel. Naming the difference is cheaper
//     than carrying a "this size came from a keyword" channel the wire does
//     not have.
//
//  TWIN STATUS: still iOS-only. `typography/UAElementFontRule.kt` does not
//  exist; Android shows the identical defect in the same captures
//  (`css-text-decor/text-decoration-color` android 0.6164 F at
//  wave49-final). The wave-40 reason given here — "this lane owns no Android
//  device" — was a LANE's constraint, not a standing one, and it stood as
//  the whole plan for nine waves without ever becoming work (retro finding
//  A4#7). It is now a QUEUED backlog item: docs/BACKLOG.md ranked-queue
//  entry (g), which carries the target cell and the iOS gate table's caveat
//  that only leaf-text hosts improve (iOS 0.674 after the gated heading
//  face). Point a port at that entry, not at this banner.
//

import Foundation

enum UAElementFontRule {

    /// The UA `font-size` multiplier for a tag, as a factor on the element's
    /// own `medium` size. CSS 2.1 Appendix D.2 / Chromium html.css:
    /// `h1 { font-size: 2em } h2 { 1.5em } h3 { 1.17em } h4 { 1em }
    ///  h5 { .83em } h6 { .67em }` and `sub, sup { font-size: smaller }`.
    ///
    /// `smaller` is the 1.2 ratio CSS Fonts 4 §3.5 uses between adjacent
    /// absolute-size keywords — see the KNOWN GAP above for the table nuance.
    /// Returns nil for every tag the UA sheet gives no font-size, which is the
    /// overwhelming majority and the fast path.
    static func sizeMultiplier(forTag tag: String?) -> Double? {
        switch (tag ?? "").lowercased() {
        case "h1": return 2.0
        case "h2": return 1.5
        case "h3": return 1.17
        case "h4": return 1.0
        case "h5": return 0.83
        case "h6": return 0.67
        case "sub", "sup": return 1.0 / 1.2
        default:   return nil
        }
    }

    /// Does the UA sheet declare `font-weight: bold` for this tag? Only the
    /// six headings do (`b`/`strong` do too, but they are deliberately NOT in
    /// this table: the corpus has passing cells carrying a bare `<b>`, so
    /// widening the tag set would put a capture at risk this lane cannot
    /// verify on device — the sizeMultiplier table is the same shape and the
    /// same argument).
    static func isBold(forTag tag: String?) -> Bool {
        switch (tag ?? "").lowercased() {
        case "h1", "h2", "h3", "h4", "h5", "h6": return true
        default: return false
        }
    }

    /// Does the HEADING half apply to a heading with these composed children?
    ///
    /// It applies to a heading whose content is its own single text run, and
    /// STANDS DOWN for one that hosts child boxes. Not timidity — a MEASURED
    /// interaction with a modelling gap this lane does not close.
    ///
    /// A SwiftUI block container has no inline line box, so a heading like
    /// `the <u>ultra-quick</u> fox` is painted as a VERTICAL STACK of its
    /// fragments, one label per run (Renderer/InlineRunPlan.swift states the
    /// scope). Handing that stack the correct 2em face does not make it
    /// browser-like; it makes every stacked fragment twice as tall, so the
    /// capture drifts FURTHER from a ref that lays the same words on two
    /// lines. Measured on the private w40-t7 sim over the frozen wave39-final
    /// per-test IR, ungated heading face vs the wave39-final iOS column:
    ///
    ///   text-decoration-color      h3, leaf text   0.6185 → 0.6740   BETTER
    ///   text-decoration-inset-011  h1 + <u> chain  0.6999 → 0.6290   WORSE
    ///   text-decoration-inset-014  h1 + <u> chain  0.9317 → 0.9150   WORSE
    ///
    /// (inset-005/006 move the same way with a smaller magnitude; every one of
    /// these cells fails on iOS before and after, so nothing passing was ever
    /// at stake — the gate is about not making a failing capture worse.)
    ///
    /// The REAL fix for the stacked cells is an inline formatting context on
    /// this platform, which is a wave-scale mechanism of its own; when it
    /// lands, this gate should be deleted, not widened.
    static func headingAppliesTo(hasElementChildren: Bool) -> Bool {
        !hasElementChildren
    }

    /// Property types whose presence in the element's OWN declarations means
    /// the author sized this element — the UA `font-size` then loses
    /// (css-cascade-4 §6.1). `Font` is the unexpanded shorthand: it carries a
    /// size component, so it blocks exactly like the longhand.
    static let sizeDeclaring: Set<String> = ["FontSize", "Font"]

    /// The weight twin of `sizeDeclaring`.
    static let weightDeclaring: Set<String> = ["FontWeight", "Font"]

    /// The `em` base the UA multiplier resolves against, in px.
    ///
    /// Two cases, in cascade order:
    ///  1. An INHERITED `font-size` — the parent's computed pixels, which is
    ///     what `em` means (css-values-4 §6.1.1). Present in `merged` whenever
    ///     any ancestor declared a size; read with `first`, the same read
    ///     StyleBuilder performs, so the two never disagree about which entry
    ///     is the element's size.
    ///  2. Otherwise the UA `medium` keyword: 16px, or Chromium's 13px
    ///     `defaultFixedFontSize` when the element's FIRST declared family is
    ///     the monospace generic. That branch is why inset-014's monospace
    ///     `<h1>` measures 26px = 2 × 13 in the frozen ref and not 32; it is
    ///     consulted through MonospaceUAFontSize so one table decides the
    ///     fixed default for the whole runtime.
    ///
    /// Reads the MERGED list for both, because `font-size` and `font-family`
    /// are inherited: a heading under a monospace ancestor resolves exactly
    /// like one that declares the family itself.
    static func emBasePx(merged: [IRProperty]) -> Double {
        if let inherited = merged.first(where: { $0.type == "FontSize" }),
           let px = ValueExtractors.extractPx(inherited.data) {
            return Double(px)
        }
        return MonospaceUAFontSize.resolvePx(from: merged) ?? 16.0
    }

    /// Apply the UA element-font declarations to one element's merged list.
    ///
    /// - Parameters:
    ///   - sourceTag: the element's `meta.sourceTag`. Nil / untabled ⇒ the
    ///     input is returned untouched (the byte-stability fast path).
    ///   - own: the element's OWN declarations, post bucket/media fold. A
    ///     guarded type here means the author declared it and the UA loses.
    ///   - merged: the inheritance-merged list to correct.
    ///   - hasElementChildren: does this component host composed child boxes?
    ///     Gates the HEADING half only — see `headingAppliesTo` for the
    ///     measurement that put the gate there.
    /// - Returns: `merged`, with the FontSize / FontWeight entries substituted
    ///   in place (or appended when the list carries none).
    static func apply(sourceTag: String?,
                      own: [IRProperty],
                      merged: [IRProperty],
                      hasElementChildren: Bool = false) -> [IRProperty] {
        // Untabled tag — the whole 327-pair corpus and almost every WPT
        // component. Returning the input identity-preserves the array.
        let headingTag = isBold(forTag: sourceTag)
        let applies = headingTag ? headingAppliesTo(hasElementChildren: hasElementChildren) : true
        let multiplier = applies ? sizeMultiplier(forTag: sourceTag) : nil
        let bold = headingTag && applies
        guard multiplier != nil || bold else { return merged }

        // SIZE half. Author declaration wins; otherwise the UA value is the
        // element's own `medium` scaled by the tag's multiplier.
        var out = merged
        if let multiplier,
           !own.contains(where: { sizeDeclaring.contains($0.type) }) {
            // Emitted in the wire's plain-px length shape ({"px": n}) — the
            // shape every length extractor reads first, and the right one for
            // an absolute computed value with no relative-unit machinery left.
            let px = multiplier * emBasePx(merged: merged)
            let entry = IRProperty(type: "FontSize",
                                   data: .object(["px": .double(px)]))
            out = substituting(out, type: "FontSize", with: entry)
        }
        // WEIGHT half — same cascade rule, same substitution.
        if bold, !own.contains(where: { weightDeclaring.contains($0.type) }) {
            // `{"weight": 700}` is the shape the converter emits for `bold`
            // (see the FontWeight payloads in the pinned per-test IR).
            let entry = IRProperty(type: "FontWeight",
                                   data: .object(["weight": .double(700)]))
            out = substituting(out, type: "FontWeight", with: entry)
        }
        return out
    }

    /// Replace the FIRST entry of `type` with `entry`, dropping every later
    /// one; append when the list carries none.
    ///
    /// In-place substitution rather than an append is load-bearing, not
    /// tidiness: StyleBuilder reads `properties.first(where: { $0.type ==
    /// "FontSize" })`, so an appended UA entry would sit BEHIND the inherited
    /// one and never be consulted. Dropping the later duplicates keeps the
    /// last-wins folds (which most extractors use) agreeing with that first-
    /// wins read — with exactly one entry, both orders answer the same.
    ///
    /// The LONGHAND only: the guard sets above may name the `Font` shorthand,
    /// but a shorthand can only reach the merged list through the element's
    /// OWN declarations (`font` is not an inherited type), and an own
    /// declaration has already made this rule stand down — so rewriting a
    /// shorthand entry here is unreachable, and silently dropping its other
    /// components would be the wrong repair if it ever were not.
    private static func substituting(_ properties: [IRProperty],
                                     type: String,
                                     with entry: IRProperty) -> [IRProperty] {
        var replaced = false
        var out = properties.compactMap { property -> IRProperty? in
            guard property.type == type else { return property }
            guard !replaced else { return nil }
            replaced = true
            return entry
        }
        if !replaced { out.append(entry) }
        return out
    }
}
