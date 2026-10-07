//
//  SoftHyphenPolicy.swift
//  StyleEngine/typography/wrapping — wave 37 (lane W7, rule A).
//
//  css-text-3 §5.3 `hyphens`, the SOFT HYPHEN half.
//
//  U+00AD SOFT HYPHEN is a *conditional* character: it is invisible
//  unless the line actually breaks at it, in which case the UA paints
//  the hyphenate-character there. §5.3 defines the three keywords over
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
//  is a real opportunity. Who takes it depends on the path (wave 41): a
//  greedily pre-broken run spends it inside the fold itself
//  (WordBreakOpportunities — the taken point comes back as a painted
//  hyphen, the rest disappear before TextKit ever sees the string), and
//  a run the pre-break declines keeps the platform's own handling.
//
//  Twin: SoftHyphenPolicy.kt (Compose), same two entry points, same
//  identity contract. Pure + Foundation-only so XCTest pins it without
//  a renderer — the one side effect is wave 53's `admitsPreBreak`
//  refusal breadcrumb (PropertyTracker.logOnce, stderr, deduped). Its
//  Compose counterpart is the PreBreakPipeline `:166` guard (wave 53
//  F1), which needs no vertical clause: Compose's wrap-width latch is
//  written by the horizontal Text only, so a vertical run declines there
//  by construction.
//

import Foundation

enum SoftHyphenPolicy {

    /// U+00AD SOFT HYPHEN — the one conditional character §5.3 governs.
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

    /// Does this run ask for DICTIONARY hyphenation (§5.3 `auto`:
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

    // MARK: - Wave 53 (lane L2, F1-iOS) — who may enter the greedy pre-break

    /// U+00AD as a SCALAR: the membership test below walks unicode scalars,
    /// not Characters, so a soft hyphen can never hide inside a grapheme
    /// cluster with a neighbour (U+00AD is GCB=Control and stands alone in
    /// practice; the scalar walk makes that a non-question).
    private static let softHyphenScalar: Unicode.Scalar = "\u{00AD}"

    /// May the label's greedy pre-break (ComponentRenderer.swift's `broken`
    /// closure) take this run? The precondition the seam used to spell
    /// inline, plus ONE new clause.
    ///
    /// - Until wave 53: `text.contains(" ") || dictionary` — a space to split
    ///   on, or a `hyphens: auto` dictionary that can break a single word.
    ///   Kept VERBATIM and evaluated FIRST, so every run it admitted is
    ///   still admitted by the same test.
    /// - Wave 53 (css-text-3 §5.3 `manual`: U+00AD IS a break opportunity
    ///   and the hyphenate character is painted when the line breaks there):
    ///   a SPACE-LESS run that carries a soft hyphen is admitted too. Before,
    ///   TextKit took the soft hyphen on its own (drawing `high-`/`way`) but
    ///   the composed line-box pin counted ONE line — `singleLineText`, since
    ///   U+00AD is not whitespace — so every hyphens-span-001 / -out-of-flow-
    ///   001 box was 26 px tall where the ref's is 46 px (wave52-ship ios f
    ///   0.8652 / 0.8935). Pre-broken, the run reaches the label as
    ///   `high‐\nway`: `preBroken` makes the line count exact (2) and the box
    ///   pins at 2 × 20 + 6 px (CSS 2.1 §10.8), the hyphens-out-of-flow-002
    ///   shape that already passes (its `lang` satisfies the dictionary arm).
    /// - PLAN wave 53 §9 D3: the U+00AD clause is HORIZONTAL-ONLY. The label's
    ///   wrap width (`textWrapWidth`) is the PHYSICAL width whatever the
    ///   writing mode, so in a vertical run it is the block axis, not the
    ///   line axis (css-writing-modes-4 §3) — pre-breaking there would fit
    ///   lines against the wrong extent. hyphens-vertical-001's
    ///   `hyphen\u{AD}ation` box reaches this test; it is refused, named once
    ///   (no silent fallthrough), and keeps its pre-wave-53 rendering byte for
    ///   byte. (A vertical run admitted by the two older clauses is untouched:
    ///   that is pre-existing behaviour this lane does not own.)
    ///
    /// - Parameters:
    ///   - text: the display string after text-transform and the `hyphens:
    ///     none` strip — exactly what the pre-break would measure.
    ///   - dictionary: a `hyphens: auto` dictionary is live for this run
    ///     (`hyphenLocale != nil` at the seam).
    ///   - horizontal: the run's USED writing mode is horizontal-tb.
    static func admitsPreBreak(_ text: String, dictionary: Bool, horizontal: Bool) -> Bool {
        // The pre-wave-53 precondition, verbatim and first.
        if text.contains(" ") || dictionary { return true }
        // No space, no dictionary, no soft hyphen: nothing to break — wave
        // 21's whole-run gate (`wptUnbreakableRun`) owns the run, as before.
        guard text.unicodeScalars.contains(softHyphenScalar) else { return false }
        // D3: a vertical run's wrap width is the wrong axis — refuse, named.
        guard horizontal else {
            _ = PropertyTracker.logOnce(
                key: "soft-hyphen:prebreak:vertical",
                message: "hyphens: a space-less soft-hyphen run in a vertical writing "
                    + "mode is not pre-broken (the label's wrap width is the physical "
                    + "width, not the vertical line axis); TextKit keeps the run")
            return false
        }
        // A space-less U+00AD run in a horizontal line: the fold owns it.
        return true
    }
}
