//
//  GreedyLineBreaker.swift
//  StyleEngine/typography — lane IOS-TEXT fix 1.
//
//  Greedy line breaking for real element text. SwiftUI Text delegates
//  wrapping to TextKit, whose default lineBreakStrategy includes
//  push-out ("orphan avoidance"): a run that Chromium and Compose both
//  break greedily as "Grumpy wizards vex / 0123" comes out
//  "Grumpy wizards / vex 0123" on iOS — word x-extents proved the glyph
//  advances are pixel-identical across the three platforms, ONLY the
//  break strategy differs (pixel-verified, lane diagnosis).
//
//  The lane's prescribed vehicle was a UIViewRepresentable UILabel with
//  NSParagraphStyle.lineBreakStrategy = []. That approach is GATED on
//  ImageRenderer rasterizing platform views — and Apple documents that
//  ImageRenderer "does not render views provided by native platform
//  frameworks (AppKit and UIKit)", i.e. the capture pipeline would get
//  an empty layer (the wave-3 Canvas lesson, but by-design this time).
//  So instead the greedy algorithm is implemented HERE, over the same
//  font metrics: words are measured with the exact resolved UIFont (+
//  letter/word-spacing kerning) via NSAttributedString — the same
//  TextKit measurement SwiftUI lays Text out with — and the run is
//  pre-broken with hard newlines. SwiftUI Text renders the hard-wrapped
//  string (fully ImageRenderer-safe), and since our lines fit the wrap
//  width by construction, TextKit's push-out never gets a soft break to
//  move. The algorithm core takes an injected measurer so XCTest pins
//  the break positions without any registered font.
//

// UIKit for UIFont/NSAttributedString measurement in the production
// measurer; the algorithm core is Foundation-pure.
import UIKit

enum GreedyLineBreaker {

    // MARK: - Algorithm core (pure, measurer-injected)

    /// Break `text` into greedy lines at `maxWidth`: words accumulate
    /// left-to-right and a word moves to the next line the moment the
    /// candidate line no longer fits — exactly Chromium's default
    /// css-text-3 §5 behaviour and Compose's Paragraph layout (no
    /// look-ahead, no push-out). A single word wider than `maxWidth`
    /// stays alone on its line and overflows, like CSS without
    /// overflow-wrap. Pre-existing hard newlines are preserved as
    /// paragraph boundaries. Words are space-separated (the converter's
    /// text channel is whitespace-collapsed upstream, matching the
    /// css-text-3 §4.1 collapse the browser applied to the reference).
    /// - Parameters:
    ///   - hyphenate: wave 40 (lane T2) — the css-text-3 §6.1 `auto`
    ///     dictionary, as "every offset inside this word where a hyphen
    ///     may be inserted" (AutoHyphenation.breakOffsets). nil — the
    ///     default and every pre-wave-40 call site — leaves the fold
    ///     BYTE-IDENTICAL: the branch below is entered only when a word
    ///     does not fit AND a hyphenator was supplied AND it found a
    ///     usable point. This is the platform's whole hyphenation seam,
    ///     because on iOS the line breaker is ours: TextKit only ever
    ///     sees the hard-newlined result (Compose instead switches
    ///     Minikin's own hyphenator on — see AutoHyphenation.kt).
    ///   - hyphenChar: the glyph painted at a taken hyphenation point
    ///     (§6.1 UA-defined, css-text-4 `hyphenate-character` overrides).
    ///     It is a REAL character in the returned string, so it is
    ///     measured and rendered exactly like any other glyph — which is
    ///     what keeps the fit test honest (a line that ends in a hyphen
    ///     must fit WITH the hyphen).
    static func lines(text: String,
                      maxWidth: CGFloat,
                      measure: (String) -> CGFloat,
                      hyphenate: ((String) -> [Int])? = nil,
                      hyphenChar: String = AutoHyphenation.defaultHyphenCharacter) -> [String] {
        var out: [String] = []
        // Hard breaks split paragraphs; each wraps independently.
        for para in text.components(separatedBy: "\n") {
            // Collapse-split into words; an all-space paragraph yields
            // an empty visual line, preserved for count stability.
            let words = para.split(separator: " ").map(String.init)
            guard !words.isEmpty else { out.append(""); continue }
            // Greedy fold. `line` is the committed-so-far content of the
            // current line; empty means the line has not been opened yet
            // (only possible after a hyphenated commit, since the first
            // word always opens a line — CSS 2.1 §9.5 lets an overlong
            // first word overflow rather than break).
            var line = words[0]
            // Wave 40: an opening word may itself be too wide, and under
            // `hyphens: auto` that is no longer a reason to overflow —
            // the dictionary may have a point inside it. Identity when
            // `hyphenate` is nil (the guard inside returns nil at once).
            if hyphenate != nil, measure(line) > maxWidth {
                line = openWithHyphenation(line, maxWidth: maxWidth, out: &out,
                                           measure: measure,
                                           hyphenate: hyphenate,
                                           hyphenChar: hyphenChar)
            }
            for word in words.dropFirst() {
                // Candidate = current line + separator + next word,
                // measured as ONE string so the space's own advance and
                // any kerning are included exactly as rendered.
                let candidate = line + " " + word
                if measure(candidate) <= maxWidth {
                    line = candidate       // fits → keep accumulating
                    continue
                }
                // Wave 40 — before moving the whole word down, offer the
                // dictionary the chance to split it ACROSS the break, which
                // is what `hyphens: auto` asks for. `prefix` is the largest
                // head that still fits after the current line (with the
                // hyphen glyph included in the measurement).
                if let (head, tail) = hyphenatedSplit(
                    word, after: line + " ", maxWidth: maxWidth,
                    measure: measure, hyphenate: hyphenate, hyphenChar: hyphenChar) {
                    out.append(line + " " + head)
                    // The tail opens the next line and may need splitting
                    // again (a long word can span three lines in a narrow box).
                    line = tail
                    if hyphenate != nil, measure(line) > maxWidth {
                        line = openWithHyphenation(line, maxWidth: maxWidth, out: &out,
                                                   measure: measure,
                                                   hyphenate: hyphenate,
                                                   hyphenChar: hyphenChar)
                    }
                    continue
                }
                out.append(line)           // commit the full line…
                line = word                // …and open the next one
                // …which may itself be overlong and hyphenatable. The
                // `hyphenate != nil` guard keeps the no-hyphenation path
                // free of the extra measurement it never needed, so the
                // fold is identical in COST as well as in output.
                if hyphenate != nil, measure(line) > maxWidth {
                    line = openWithHyphenation(line, maxWidth: maxWidth, out: &out,
                                               measure: measure,
                                               hyphenate: hyphenate,
                                               hyphenChar: hyphenChar)
                }
            }
            out.append(line)               // trailing partial line
        }
        return out
    }

    /// Repeatedly split `word` at dictionary points while the remainder
    /// still overflows an EMPTY line, appending each hyphenated head to
    /// `out`; returns the final remainder (which opens the current line).
    ///
    /// Identity — returns `word` and appends nothing — whenever there is
    /// no hyphenator or the dictionary offers no point that fits, which
    /// is the CSS 2.1 §9.5 overflow the pre-wave-40 fold already produced.
    private static func openWithHyphenation(
        _ word: String,
        maxWidth: CGFloat,
        out: inout [String],
        measure: (String) -> CGFloat,
        hyphenate: ((String) -> [Int])?,
        hyphenChar: String
    ) -> String {
        var rest = word
        // Bounded: every accepted split strictly shortens `rest`, and the
        // loop stops the moment no point fits.
        while let (head, tail) = hyphenatedSplit(
            rest, after: "", maxWidth: maxWidth,
            measure: measure, hyphenate: hyphenate, hyphenChar: hyphenChar) {
            out.append(head)
            rest = tail
            if measure(rest) <= maxWidth { break }
        }
        return rest
    }

    /// The largest dictionary split of `word` whose head — placed after
    /// `prefix` and followed by `hyphenChar` — still fits `maxWidth`.
    ///
    /// Greedy, exactly like the word fold: css-text-3 §5 breaks at the
    /// LAST opportunity that fits, which is what Chromium's and Minikin's
    /// line breakers both do. nil when there is no hyphenator, no point,
    /// or no point small enough — the caller then falls back to the
    /// unhyphenated behaviour, unchanged.
    private static func hyphenatedSplit(
        _ word: String,
        after prefix: String,
        maxWidth: CGFloat,
        measure: (String) -> CGFloat,
        hyphenate: ((String) -> [Int])?,
        hyphenChar: String
    ) -> (head: String, tail: String)? {
        guard let hyphenate = hyphenate else { return nil }
        let offsets = hyphenate(word)
        guard !offsets.isEmpty else { return nil }
        // Walk DOWN from the last opportunity so the first fit found is
        // the greediest one.
        let utf16 = word.utf16
        for off in offsets.reversed() {
            // CF answers in UTF-16 offsets; a Character boundary is not
            // guaranteed (a point inside a surrogate pair or a combining
            // sequence is nonsense to split at), so a failed conversion
            // SKIPS the point rather than forcing it.
            guard let u16 = utf16.index(utf16.startIndex, offsetBy: off,
                                        limitedBy: utf16.endIndex),
                  let split = String.Index(u16, within: word) else { continue }
            let head = String(word[word.startIndex..<split]) + hyphenChar
            let tail = String(word[split...])
            guard !tail.isEmpty else { continue }
            if measure(prefix + head) <= maxWidth {
                return (head, tail)
            }
        }
        return nil
    }

    /// Wave 37 (lane W7, rule B) — does any committed line overflow the
    /// wrap width WITH NOWHERE LEFT TO BREAK?
    ///
    /// `lines(text:maxWidth:measure:)` guarantees a fit for every line
    /// EXCEPT one holding a single word wider than `maxWidth`, which CSS
    /// 2.1 §9.5 / css-text-3 §5.2 require to overflow the line box rather
    /// than break (the emergency break is reserved for `overflow-wrap:
    /// break-word|anywhere` / `word-break: break-all`). TextKit does not
    /// know that, so the caller must take the run out of the
    /// width-constrained layout — see the `.fixedSize(horizontal:)` gate
    /// in PlaceholderLabel.
    ///
    /// BOTH halves matter. This breaker splits on SPACES only, so its
    /// "word" is coarser than UAX #14's: `regu-lation` (a real U+002D
    /// hyphen-minus, class HY) and `foo\u{200B}bar` are one word here but
    /// two break opportunities to the platform breaker — and there the
    /// platform is RIGHT, `hyphens` does not govern them. Firing on such
    /// a line stops a wrap the ref performs: measured on iOS
    /// css-text/hyphens-none-012 (the `regu-lation imple-menta-tion` box)
    /// 0.8548 → 0.8431 with the width-only test, restored by this
    /// per-line `hasSoftWrapOpportunity` veto. So an overflowing line
    /// only counts when it is genuinely unbreakable — the same predicate
    /// wave 21 applied to a whole run, applied here per line.
    ///
    /// `tolerance` absorbs the sub-point rounding between the fit test
    /// (`NSAttributedString.size().width`, fractional) and the width the
    /// layout actually proposes; without it a line that measured exactly
    /// `maxWidth` could report as overflowing on a ½-point difference and
    /// pull a perfectly fitting run out of the constrained layout.
    ///
    /// Wave 40 (lane T2) — `dictionaryHyphenation` VETOES the claim per
    /// line. The `hasSoftWrapOpportunity` predicate answers "does UAX #14
    /// give this line a break?", which is the complete answer only while
    /// the hyphenator is off; under `hyphens: auto` §6.1 adds the
    /// dictionary's points, and `lines(…)` has ALREADY spent them (the
    /// hyphenated head carries a real hyphen character, so a line that
    /// still overflows here genuinely had nowhere to go). Lines with no
    /// letters to hyphenate keep the claim — the digit runs in
    /// css-text/hyphens-punctuation-001 are the measured case. Byte-
    /// parallel with the Kotlin twin's identically named parameter.
    static func hasUnbreakableOverflowingLine(_ lines: [String],
                                              maxWidth: CGFloat,
                                              tolerance: CGFloat = 0.5,
                                              dictionaryHyphenation: Bool = false,
                                              measure: (String) -> CGFloat) -> Bool {
        lines.contains {
            measure($0) > maxWidth + tolerance
                && !DecorationOps.hasSoftWrapOpportunity($0)
                && !(dictionaryHyphenation && AutoHyphenation.hasDictionaryOpportunity($0))
        }
    }

    // MARK: - Production measurer (TextKit metrics)

    /// A measurer over the EXACT resolved render font + spacing values,
    /// so the greedy fit test uses the same advances SwiftUI's Text
    /// layout (TextKit-backed) will produce. Letter-spacing rides
    /// `.kern` on every glyph (SwiftUI `.tracking` equivalent);
    /// word-spacing ADDS to the kern of each space character
    /// (css-text-3 §8.1 — word-separator advance), mirroring the render
    /// path in PlaceholderLabel.wordSpacedText.
    /// - Parameter scriptFallback: wave 34 (lane F1) — when true, the SAME
    ///   per-script bundled faces the render lane installs
    ///   (`ScriptFallbackFonts`) are written over this measurement string
    ///   too. Without it the fit test would measure Armenian / Arabic-Indic
    ///   / Bengali / Khmer / Hebrew text through CoreText's cascade while
    ///   the render used Noto, so the greedy pre-break would put line
    ///   breaks where NEITHER surface wraps — and the whole point of this
    ///   lane is that a wrap point is the residual (the Rule-43 banner's
    ///   item (c): armenian-007's 0.78 is ONE wrap point, not glyph ink).
    ///   Defaults false so every pre-lane call site and XCTest pin keeps
    ///   its exact advances.
    static func measurer(font: UIFont,
                         letterSpacingPx: CGFloat?,
                         wordSpacingPx: CGFloat?,
                         scriptFallback: Bool = false) -> (String) -> CGFloat {
        return { s in
            // Base attributes: the render face + uniform tracking.
            var attrs: [NSAttributedString.Key: Any] = [.font: font]
            if let t = letterSpacingPx { attrs[.kern] = t }
            let a = NSMutableAttributedString(string: s, attributes: attrs)
            // Per-script faces, at the SAME point size as the base font so
            // the substituted glyphs measure at the size they render. The
            // segmenter emits UTF-16 offsets, which is exactly the index
            // space NSMutableAttributedString ranges use.
            // `substitutionEnabled` is the platform's MEASURED verdict on
            // whether bundled faces improve ref parity (see its banner). It
            // gates measurement and render together — measuring with a face
            // the render does not install is the one combination that breaks
            // where NEITHER surface wraps.
            if scriptFallback, ScriptFallbackFonts.substitutionEnabled,
               ScriptRunSegmenter.needsFallback(s) {
                for run in ScriptRunSegmenter.segment(s) {
                    guard let f = ScriptFallbackFonts.uiFont(for: run.script,
                                                            size: font.pointSize)
                    else { continue }
                    a.addAttribute(.font, value: f,
                                   range: NSRange(location: run.start,
                                                  length: run.end - run.start))
                }
            }
            // Word-spacing: extra kern on each word separator, ON TOP of
            // tracking (CSS adds both to the separator's advance).
            // Lane IOS wave 5 (finding 6) — separators are U+0020 SPACE
            // and U+00A0 NO-BREAK SPACE (css-text-3 §8.1 lists NBSP as a
            // word-separator character; Compose's applyWordSpacingSpans
            // handles both, so the measurer must too or NBSP-separated
            // runs measure narrower than they render).
            if let ws = wordSpacingPx, ws != 0 {
                let ns = s as NSString
                for i in 0..<ns.length
                where ns.character(at: i) == 0x20 || ns.character(at: i) == 0xA0 {
                    a.addAttribute(.kern,
                                   value: (letterSpacingPx ?? 0) + ws,
                                   range: NSRange(location: i, length: 1))
                }
            }
            // Single-line intrinsic width — the greedy fit metric.
            return a.size().width
        }
    }
}
