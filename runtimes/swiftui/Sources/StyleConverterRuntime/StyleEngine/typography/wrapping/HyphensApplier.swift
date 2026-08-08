//
//  HyphensApplier.swift
//  StyleEngine/typography/wrapping — Phase 6, wave 37 (lane W7).
//
//  Phase 6 shipped this as a documented identity: "SwiftUI Text has no
//  hyphenation toggle". That is still true of the two halves of
//  css-text-3 §6.1 that need a LAYOUT seam —
//
//    • `auto` — dictionary hyphenation. TextKit can do it
//      (NSParagraphStyle.hyphenationFactor / usesDefaultHyphenation), but
//      only through a UIKit label, and ImageRenderer refuses to
//      rasterise platform views (the wall GreedyLineBreaker's banner
//      documents). Still a no-op here, and now a NAMED wall rather than
//      an unexplained one — see tools/titan/wpt-not-applicable.mjs
//      `requires-hyphenation-dictionary`.
//
//    …and the keyword is now also the VETO on the unbreakable-word
//    overflow rule (SoftHyphenPolicy.hasDictionaryOpportunities): under
//    `auto` the ref DOES break inside the word, so claiming the §5.2
//    overflow there moves us further away, not closer.
//
//  — but NOT of the third:
//
//    • `none` — "words are not broken at line breaks, even if characters
//      inside the words suggest break points". That one needs no layout
//      seam at all, because the characters it must ignore (U+00AD SOFT
//      HYPHEN) are conditional and invisible when unbroken: deleting
//      them from the string is an exact implementation. This applier now
//      raises that flag on the aggregate; PlaceholderLabel spends it via
//      SoftHyphenPolicy.displayString before the greedy pre-break
//      measures the run.
//
//  `manual` (the initial value) and `auto` leave the flag false, so every
//  document that does not declare `hyphens: none` renders byte-for-byte
//  as before. Twin: Compose reads the same keyword through
//  TextStyleApplier.extractHyphens and applies the same policy object.
//

import Foundation

enum HyphensApplier {
    static func contribute(_ cfg: HyphensConfig?, into agg: inout TypographyAggregate) {
        // No declaration on this element → leave the inherited/default
        // state untouched (the property IS inherited, and an absent
        // declaration must not clear an ancestor's `none`).
        guard let cfg = cfg else { return }
        // Last-write-wins, exactly like every other aggregate flag: a
        // later `hyphens: manual` on the same element must be able to
        // clear an earlier `none` in the same property list.
        agg.hyphensMode = cfg.mode?.lowercased()
        // `touched` is what makes TypographyExtractor return a non-nil
        // aggregate, and StyleBuilder only mirrors the aggregate into
        // TextConfig when it is non-nil — so without this the keyword is
        // silently dropped on an element whose ONLY typography
        // declaration is `hyphens` (measured: css-text/
        // hyphens-none-shy-on-2nd-line-001, whose single component
        // declares Hyphens + Width and nothing else, kept painting the
        // soft-hyphen break at its frozen 0.9555). Raised only for the
        // two keywords that CHANGE something — `manual` is the initial
        // value and must leave the aggregate nil exactly as before, or
        // every `hyphens: manual` element in the corpus would newly
        // acquire a typography modifier chain.
        if agg.hyphensMode == "none" || agg.hyphensMode == "auto" {
            agg.touched = true
        }
        // No-silent-fallthrough: `auto` is the one keyword this runtime
        // cannot honour in full. Say so ONCE per process rather than
        // degrade quietly — the degradation (auto → manual's explicit
        // opportunities) is correct for untagged content and a wall for
        // language-tagged content; see SoftHyphenPolicy for the measured
        // both-sides record.
        if SoftHyphenPolicy.wantsDictionaryHyphenation(agg.hyphensMode) {
            _ = PropertyTracker.logOnce(
                key: "hyphens:auto",
                message: "hyphens: auto — dictionary hyphenation unavailable "
                    + "(no ImageRenderer-safe TextKit seam); falls back to the "
                    + "explicit opportunities `manual` allows")
        }
    }
}
