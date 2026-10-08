//
//  SpentHyphen.swift
//  StyleEngine/typography/wrapping — wave 54 (lane L3, U2-ios).
//
//  css-text-3 §5.5: a line breaks AT a soft wrap opportunity, and once taken
//  that opportunity is SPENT — the hyphen string the break materialised at
//  the line end (css-text-3 §5.3 / css-text-4 §6.3) offers no further break.
//
//  THE DEFECT, measured on the wave53-final pictures. iOS hyphenate-character-
//  001 painted group 2 as `ini-/tial/-/iza-/tion`, with a LONE `-` line: the
//  pre-broken line `tial‐` is 48 px against the 43.2 px box, and
//  `GreedyLineBreaker.hasUnbreakableOverflowingLine` asked
//  `DecorationOps.hasSoftWrapOpportunity` of the WHOLE line — which counts
//  `-` and U+2010 (UAX #14 HY/BA). The line was judged breakable, the
//  `.fixedSize(horizontal:)` gate stayed off, and TextKit re-wrapped it at
//  the box edge. With `hyphenate-character: "/-/"` (-004) the same happens to
//  `tial/-/` and `phen/-/`, which the ref lets OVERFLOW (CSS 2.1 §9.5).
//  On Compose a fired run renders with softWrap = false, so the twin needs no
//  such rule (the line simply overflows, as in the ref).
//
//  The rule: test the line MINUS one trailing spent hyphen string. Scoped to
//  an author-declared string (`spentHyphen` nil = no strip), so the UA-hyphen
//  population is byte-identical; generalising it to U+2010 is queued
//  (hyphenate-character brief §5, not done here).
//

// Foundation only: pure string arithmetic, unit-testable under Catalyst.
import Foundation

/// The css-text-3 §5.5 "spent opportunity" view of a pre-broken line.
enum SpentHyphen {
    /// [line] as the soft-wrap-opportunity test must see it: with ONE trailing
    /// occurrence of [spentHyphen] removed when the line ends with it and has
    /// ink before it. Compared on unicode SCALARS, so a hyphen string that
    /// starts with a combining mark cannot fold into the preceding grapheme.
    /// Identity for nil, for `""`, and for a line that does not end with it.
    static func opportunityText(_ line: String, spentHyphen: String?) -> String {
        // Nothing declared, or nothing painted: nothing was spent.
        guard let h = spentHyphen, !h.isEmpty else { return line }
        // Scalar arrays: exact suffix comparison, no canonical folding.
        let ls = Array(line.unicodeScalars), hs = Array(h.unicodeScalars)
        // The line must END with the string and keep some ink before it.
        guard ls.count > hs.count, Array(ls.suffix(hs.count)) == hs else { return line }
        // Rebuild the line without the spent string.
        var view = String.UnicodeScalarView()
        // Everything before the trailing hyphen string, in order.
        view.append(contentsOf: ls.dropLast(hs.count))
        // The test string — never painted, only tested for opportunities.
        return String(view)
    }
}
