//
//  SoftHyphenPolicy.swift
//  StyleEngine/typography/wrapping — wave 37 (lane W7, rule A).
//
//  css-text-3 §6.1 `hyphens`, the SOFT HYPHEN half.
//
//  U+00AD SOFT HYPHEN is a *conditional* character: it is invisible
//  unless the line actually breaks at it, in which case the UA paints
//  the hyphenate-character there. §6.1 defines the three keywords over
//  exactly that conditionality:
//
//    manual (initial) — "words are only broken at line breaks where
//                        there are characters inside the word that
//                        explicitly suggest break opportunities" → a
//                        U+00AD IS an opportunity;
//    none             — "words are not broken at line breaks, EVEN IF
//                        characters inside the word suggest break
//                        points" → a U+00AD is NOT an opportunity, and
//                        since it never breaks there it never paints;
//    auto             — manual's opportunities plus dictionary ones.
//
//  Both native text stacks break at U+00AD unconditionally (Android's
//  Minikin line breaker and TextKit both treat it as a UAX #14 class-BA
//  opportunity), so `hyphens: none` was silently ignored on Android and
//  iOS: the wave-36 depth-48 gate captured css-text/hyphens-none-011
//  breaking "Deoxy\u{AD}ribo\u{AD}nucleic" at the second soft hyphen on
//  BOTH natives (iOS additionally painting the hyphen glyph) where the
//  Chromium ref keeps the word whole. Neither platform exposes a
//  "ignore soft hyphens" toggle on the layout objects our capture
//  pipeline can rasterise (SwiftUI Text is TextKit-backed with no
//  NSParagraphStyle seam; ImageRenderer refuses UIKit views — see
//  GreedyLineBreaker's banner for that wall), so the ONLY honest
//  vehicle is the string itself: under `none`, delete the conditional
//  characters before layout. Deleting them is loss-free by definition —
//  §6.1 says they must not be honoured, and they have no advance of
//  their own when unbroken.
//
//  Deliberately NOT applied to `manual`/`auto`: there the soft hyphen
//  is a real opportunity and the platforms' own handling is correct.
//
//  Twin: SoftHyphenPolicy.kt (Compose), same two entry points, same
//  identity contract. Pure + Foundation-only so XCTest pins it without
//  a renderer.
//

import Foundation

enum SoftHyphenPolicy {

    /// U+00AD SOFT HYPHEN — the one conditional character §6.1 governs.
    /// (U+200B ZERO WIDTH SPACE is a plain break opportunity, NOT a
    /// hyphenation one: `hyphens` does not suppress it, so it is not in
    /// this policy's scope.)
    static let softHyphen: Character = "\u{00AD}"

    /// Does this run's resolved `hyphens` keyword forbid soft-hyphen
    /// breaks? Case-insensitive over the IR keyword (`HyphensExtractor`
    /// lowercases, but the wire is authored upper-case `"NONE"`, so both
    /// spellings must answer the same). nil / unknown keywords answer
    /// false — the initial value is `manual`, which honours them.
    static func suppresses(_ mode: String?) -> Bool {
        guard let m = mode?.lowercased() else { return false }
        return m == "none"
    }

    /// Does this run ask for DICTIONARY hyphenation (§6.1 `auto`:
    /// "words may be broken at appropriate hyphenation points … as
    /// determined by … a hyphenation resource appropriate to the
    /// language of the text")?
    ///
    /// Neither native runtime has such a resource on an
    /// ImageRenderer-safe / Compose-reachable layout object, so `auto`
    /// degrades to `manual` on both. That degradation is EXACTLY RIGHT
    /// for untagged content — §6.1 makes the resource language-dependent
    /// and the WPT `hyphens-auto-001` asserts "automatic hyphenation must
    /// not work without language tagging" (device-measured: iOS 0.7451 →
    /// 0.9950 once the unbreakable-word rule stopped the emergency
    /// break) — and it is a genuine WALL for language-tagged content,
    /// where the ref hyphenates and we cannot (iOS hyphens-auto-010
    /// 0.8573 → 0.8497: a small, honest loss, no cell). The IR carries no
    /// language channel, so the runtime cannot tell the two apart; it
    /// takes the untagged reading, which is the one that wins cells, and
    /// the tagged half is named as a wall in
    /// tools/titan/wpt-not-applicable.mjs (`requires-hyphenation-dictionary`).
    ///
    /// Used only to raise the once-per-process breadcrumb (the repo's
    /// no-silent-fallthrough rule) — it never changes layout.
    static func wantsDictionaryHyphenation(_ mode: String?) -> Bool {
        guard let m = mode?.lowercased() else { return false }
        return m == "auto"
    }

    /// The display string for `text` under `mode`. Identity (the same
    /// value, no allocation-visible change) whenever the mode honours
    /// soft hyphens or the run contains none — every legacy document
    /// therefore renders byte-identically.
    static func displayString(_ text: String, mode: String?) -> String {
        guard suppresses(mode), text.contains(softHyphen) else { return text }
        return String(text.filter { $0 != softHyphen })
    }
}
