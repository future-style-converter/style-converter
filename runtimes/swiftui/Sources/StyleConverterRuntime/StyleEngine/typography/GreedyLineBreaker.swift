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

    // MARK: - Wave 52 (lane L9, F4): the drawn-marker clamp over the fold

    /// css-overflow-4 §4.2 — the UA `block-ellipsis: auto` string. U+2026
    /// HORIZONTAL ELLIPSIS is what Chromium paints, hence what every
    /// frozen line-clamp ref carries (block-ellipsis-025's fourth line is
    /// this one glyph alone). Twin: GreedyLineBreaker.kt
    /// BLOCK_ELLIPSIS_MARKER.
    static let blockEllipsisMarker = "\u{2026}"

    /// A fixed-count `line-clamp` whose marker is DRAWN (css-overflow-4
    /// §5.1: `line-clamp: <n>` expands to `max-lines: <n>` +
    /// `block-ellipsis: auto`; §4.2: `auto` = the UA ellipsis). The
    /// renderer passes nil for a marker-suppressed clamp — `no-ellipsis` /
    /// `""`, which LineClampCap.leafLineLimit already nils out of
    /// `textConfig.lineClampLimit` — so the seam forwards that limit as-is.
    struct Clamp {
        /// The cap N — lines past it are hidden.
        let lines: Int
        /// The string placed at the end of line N; a REAL character in the
        /// returned string, measured and rendered like any other glyph —
        /// the fit test is honest only because of that.
        var marker: String = blockEllipsisMarker
    }

    /// Apply a drawn-marker `clamp` to greedy `lines`: keep lines 1…N and,
    /// on line N, hide content back to the LATEST soft wrap opportunity at
    /// which `kept + marker` fits `maxWidth` (`clampHead`); when none fits
    /// the line is the marker alone. This is css-overflow-4 §4.2's placement rule
    /// as Chromium performs it — content at the end of the last line is
    /// hidden at soft wrap opportunities until the ellipsis FITS, never a
    /// character-level truncation: block-ellipsis-025's ref shows the
    /// whole 34ch `supercalifragilisticexpialidocious` displaced off line
    /// 4 (it overflows the 32.5ch box, so even "word + …" cannot fit)
    /// leaving `…` alone, where SwiftUI's `.lineLimit` + `.truncationMode
    /// (.tail)` appended `…` AFTER the full word — the label's width is
    /// its widest line under `.fixedSize(horizontal:)`, so the intrinsic
    /// width had room (ios f 0.9495).
    ///
    /// Identity — the SAME array (value-equal, no marker) — when the
    /// paragraph fits the cap (a clamp only ever REMOVES lines) or the cap
    /// is non-positive. With the marker baked, `.lineLimit(N)` on the
    /// label becomes inert (exactly N lines), so TextKit has nothing left
    /// to truncate. ALSO identity when line N's hidden tail would cross an
    /// opportunity this walk does not model (`clampHead` → nil): the label
    /// then keeps wave 51's `.lineLimit` tail truncation. Twin:
    /// GreedyLineBreaker.kt `clampLines`.
    /// - Parameters:
    ///   - source: wave 52 fix pass (skeptic M1) — the run's text as the
    ///     fold received it, so line N's whole words get back the U+00AD
    ///     soft hyphens the display string dropped (`markedLine`):
    ///     block-ellipsis-028's ref hides `cally` at `uncharacteristi<U+00AD>cally`'s
    ///     soft hyphen and paints `uncharacteristi‐…`. nil = no soft hyphens.
    ///   - hyphenChar: the glyph a soft-hyphen cut paints (§5.3) — the
    ///     same one the fold paints at a taken soft hyphen.
    static func clampLines(_ lines: [String],
                           clamp: Clamp,
                           maxWidth: CGFloat,
                           measure: (String) -> CGFloat,
                           source: String? = nil,
                           hyphenChar: String = AutoHyphenation.defaultHyphenCharacter) -> [String] {
        // Nothing hidden → nothing to mark (a 4-line clamp on a 3-line
        // paragraph paints no ellipsis, css-overflow-4 §4.2 "if content
        // overflows").
        guard clamp.lines > 0, lines.count > clamp.lines else { return lines }
        var kept = Array(lines[0..<clamp.lines])
        let last = kept[clamp.lines - 1]
        // The whole of line N plus the marker fits: nothing to hide.
        if measure(last + clamp.marker) <= maxWidth {
            kept[clamp.lines - 1] = last + clamp.marker
            return kept
        }
        // Hide back to the latest opportunity that leaves room, or decline.
        guard let head = clampHead(markedLine(last, source: source),
                                   marker: clamp.marker, maxWidth: maxWidth,
                                   measure: measure, hyphenChar: hyphenChar) else {
            // No silent fallthrough: the wave-51 truncation stays, named once.
            PropertyTracker.logOnce(
                key: "line-clamp:block-ellipsis:unmodelled-opportunity",
                message: "line-clamp: the hidden tail crosses an ideographic / "
                    + "SA-script break opportunity the clamp walk does not model; "
                    + "the marker is left to .lineLimit tail truncation")
            return lines
        }
        kept[clamp.lines - 1] = head + clamp.marker
        return kept
    }

    /// Wave 52 fix pass (skeptic M1) — the longest prefix of `marked` (line
    /// N, untaken soft hyphens restored) that ends at a soft wrap
    /// opportunity and fits beside `marker`; "" when none does; nil =
    /// DECLINE. css-overflow-4 §4.2 hides content "at soft wrap
    /// opportunities", and the fold's U+0020-only word split is coarser
    /// than UAX #14: block-ellipsis-030 is `123<U+1680>5 789` at 5ch,
    /// whose OGHAM SPACE MARK (class BA) the ref breaks at (`123…`), where
    /// a U+0020-only cut found nothing and baked `…` alone. The walk goes
    /// from the END, boundary by boundary, so the first fit is the latest.
    /// It declines the moment the hidden tail holds an ideograph or an
    /// SA-script letter (UAX #14 ID / SA: opportunities between letters,
    /// which only a class table or a dictionary can place) — a later
    /// opportunity might exist there. Twin: GreedyLineBreaker.kt `clampHead`.
    static func clampHead(_ marked: String, marker: String, maxWidth: CGFloat,
                          measure: (String) -> CGFloat, hyphenChar: String) -> String? {
        // Code points, not Characters, so both twins index identically.
        let s = marked.unicodeScalars.map(\.value)
        var p = s.count - 1
        // Boundary p sits between s[p-1] and s[p]; s[p...] is hidden.
        while p >= 1 {
            // The hidden tail just grew by s[p] — an unmodelled class ends it.
            if unmodelledOpportunity(s[p]) { return nil }
            if let head = cutHead(s, at: p, hyphenChar: hyphenChar),
               measure(head + marker) <= maxWidth { return head }
            p -= 1
        }
        // Nothing fits: the whole line is hidden (025's 34ch word), unless
        // its first code point is itself an unmodelled opportunity.
        if let first = s.first, unmodelledOpportunity(first) { return nil }
        return ""
    }

    /// The kept DISPLAY text when line N breaks at boundary `p`, or nil when
    /// UAX #14 (as approximated here) gives no opportunity there.
    private static func cutHead(_ s: [UInt32], at p: Int, hyphenChar: String) -> String? {
        let before = s[p - 1], after = s[p]
        // §5.3: a soft-hyphen break paints the hyphenate character.
        var suffix = ""
        if isClampSeparator(after) && !isClampSeparator(before) {
            // Before a space run (SP / BA spaces): it hangs and is hidden
            // with the tail (css-text-3 §4.1.3) — 030's U+1680, 029's U+0020.
        } else if after == 0x200B {
            // ZERO WIDTH SPACE (ZW): breaks after it; inkless, so cutting
            // before it paints the same picture.
        } else if after == 0xAD, !isClampSeparator(before) {
            // An untaken soft hyphen (manual): taken here, so it paints —
            // unless a literal hyphen already ends the head (`analyze`'s rule).
            suffix = (before == 0x2D || before == 0x2010) ? "" : hyphenChar
        } else if [0x2D, 0x2010, 0x2013, 0x2014].contains(before), p >= 2,
                  !isClampSeparator(after), !isClampSeparator(s[p - 2]),
                  !((before == 0x2D || before == 0x2010) && (0x30...0x39).contains(after)) {
            // Break AFTER a hyphen or dash (HY/BA/B2) — not word-initial
            // (UAX #14 LB20.1) and not HY × NU (`1-2`), as `analyze` rules.
        } else {
            return nil
        }
        // Remaining soft hyphens have no advance; trailing spaces/ZWSP hang.
        var head = s[0..<p].filter { $0 != 0xAD }
        while let l = head.last, isClampSeparator(l) || l == 0x200B { head.removeLast() }
        return String(String.UnicodeScalarView(head.compactMap(Unicode.Scalar.init))) + suffix
    }

    /// UAX #14 space separators that are break opportunities (SP and the
    /// BA-class Zs), i.e. Zs minus the no-break U+00A0 / U+2007 / U+202F.
    private static func isClampSeparator(_ c: UInt32) -> Bool {
        c == 0x20 || c == 0x09 || c == 0x1680 || (0x2000...0x2006).contains(c)
            || (0x2008...0x200A).contains(c) || c == 0x205F || c == 0x3000
    }

    /// Code points whose opportunities sit BETWEEN letters (ID ideographs,
    /// Hangul, fullwidth forms; SA Thai/Lao/Myanmar/Khmer/Tai) — the
    /// classes the walk cannot place, so a hidden tail holding one declines.
    private static func unmodelledOpportunity(_ c: UInt32) -> Bool {
        ((0x2E80...0x9FFF).contains(c) && c != 0x3000) || (0xAC00...0xD7AF).contains(c)
            || (0xF900...0xFAFF).contains(c) || (0xFF00...0xFFEF).contains(c)
            || (0x20000...0x3FFFF).contains(c) || (0x0E00...0x0EFF).contains(c)
            || (0x1000...0x109F).contains(c) || (0x1780...0x17FF).contains(c)
            || (0x1950...0x19DF).contains(c) || (0x1A20...0x1AAF).contains(c)
            || (0xAA60...0xAADF).contains(c)
    }

    /// Line N with each WHOLE word's untaken soft hyphens restored from
    /// `source` (the display word → its raw spelling). A word split across
    /// lines N-1/N matches nothing and keeps its display text — its soft
    /// hyphens are a stated loss (the clamp then hides it whole).
    static func markedLine(_ line: String, source: String?) -> String {
        // No soft hyphen anywhere → the display line IS the marked line.
        guard let source, source.unicodeScalars.contains("\u{AD}") else { return line }
        var raw: [String: String] = [:]
        // The fold's own word split (U+0020, paragraphs at "\n").
        for word in source.split(whereSeparator: { $0 == " " || $0 == "\n" })
        where word.unicodeScalars.contains("\u{AD}") {
            // First spelling wins; analyze() is the fold's display mapping.
            let display = WordBreakOpportunities.analyze(String(word)).text
            if raw[display] == nil { raw[display] = String(word) }
        }
        // Token-wise: the fold joins every line's words with ONE U+0020.
        return line.split(separator: " ", omittingEmptySubsequences: false)
            .map { raw[String($0)] ?? String($0) }.joined(separator: " ")
    }

    /// The drawn-marker clamp a label's pre-break should apply, or nil.
    /// `limit` is the label's `TextConfig.lineClampLimit` — already nil for
    /// a marker-suppressed clamp (LineClampCap.leafLineLimit, WPT
    /// block-ellipsis-023/-024) — but it is ALSO set by the bare
    /// `max-lines` longhand (TypographyAggregate.lineLimit folds both),
    /// whose block-ellipsis is the initial `none` (css-overflow-4 §5.1:
    /// only the `line-clamp` shorthand sets `auto`). So a marker is baked
    /// only when the component itself declares a fixed-count `line-clamp`
    /// that does not suppress it (css-overflow/line-clamp/discard/
    /// discard-multicol-004 carries `max-lines: 5` alone and must keep
    /// its marker-less discard). `line-clamp` is not inherited
    /// (css-overflow-4 §5.1), so the component's OWN list is the right
    /// one. Twin: DrawnLineClamp.kt `cap` (Compose).
    static func drawnClamp(limit: Int?, properties: [IRProperty]) -> Clamp? {
        // No cap reached the label (no clamp, `none`, or suppressed marker).
        guard let n = limit, n >= 1 else { return nil }
        // The `line-clamp` declaration itself: fixed count, marker drawn.
        guard let cfg = LineClampExtractor.extract(from: properties),
              cfg.lines != nil, !cfg.markerSuppressed else { return nil }
        // The label's effective cap (the tighter of max-lines / line-clamp).
        return Clamp(lines: n)
    }

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
    ///
    /// Wave 41 (lane T3) — the fold now models IN-WORD opportunities via
    /// `WordBreakOpportunities`: U+00AD soft hyphens (a `none` run never
    /// reaches here with any — SoftHyphenPolicy deletes them first) and
    /// literal `-`/U+2010 break-afters. Words carrying NEITHER, folded
    /// with no dictionary, take decisions byte-identical to wave 40 —
    /// analyze() returns the word itself with no ops, so every split
    /// probe declines at once. Returned lines are the DISPLAY strings:
    /// soft hyphens are gone (taken ones replaced by `hyphenChar`), so
    /// TextKit renders exactly what was measured and the line COUNT the
    /// box height is pinned from is exact — the wave40-final css-text
    /// captures show the undercount this repairs (hyphens-manual-011:
    /// 3 rendered lines centered in a 2-line frame, 0.8832).
    /// - Parameters:
    ///   - hyphenate: wave 40 (lane T2) — the css-text-3 §5.3 `auto`
    ///     dictionary, as "every offset inside this word where a hyphen
    ///     may be inserted" (AutoHyphenation.breakOffsets). nil for every
    ///     run that is not `hyphens: auto` with a language tag. §5.3's
    ///     priority rule is applied HERE: a word whose characters
    ///     explicitly suggest break points (a soft hyphen or a literal
    ///     hyphen) never consults the dictionary — the WPT
    ///     hyphens-auto-control ref breaks `fragilistic\u{AD}expiali` at
    ///     the conditional hyphen in ALL THREE widths, ignoring the
    ///     automatic points before and after it.
    ///   - hyphenChar: the glyph painted at a taken hyphenation point
    ///     (§6.1 UA-defined, css-text-4 `hyphenate-character` overrides).
    ///     It is a REAL character in the returned string, so it is
    ///     measured and rendered exactly like any other glyph — which is
    ///     what keeps the fit test honest (a line that ends in a hyphen
    ///     must fit WITH the hyphen).
    ///   - clamp: wave 52 (lane L9) — a drawn-marker `line-clamp`, or nil
    ///     (every pre-wave-52 caller): the greedy lines are trimmed to the
    ///     cap with the marker placed by `clampLines` (at the latest soft
    ///     wrap opportunity that fits — `clampHead`). Both twins take it
    ///     in the same position with the same default.
    static func lines(text: String,
                      maxWidth: CGFloat,
                      measure: (String) -> CGFloat,
                      hyphenate: ((String) -> [Int])? = nil,
                      hyphenChar: String = AutoHyphenation.defaultHyphenCharacter,
                      clamp: Clamp? = nil) -> [String] {
        // The fold proper (below), then the wave-52 clamp trim over it —
        // identity when no clamp rides, byte-for-byte the wave-41 result.
        let folded = fold(text: text, maxWidth: maxWidth, measure: measure,
                          hyphenate: hyphenate, hyphenChar: hyphenChar)
        guard let clamp else { return folded }
        // `text` rides along so the clamp sees line N's soft hyphens.
        return clampLines(folded, clamp: clamp, maxWidth: maxWidth, measure: measure,
                          source: text, hyphenChar: hyphenChar)
    }

    /// The greedy fold itself — see `lines` for the contract.
    private static func fold(text: String,
                             maxWidth: CGFloat,
                             measure: (String) -> CGFloat,
                             hyphenate: ((String) -> [Int])?,
                             hyphenChar: String) -> [String] {
        var out: [String] = []
        // Hard breaks split paragraphs; each wraps independently.
        for para in text.components(separatedBy: "\n") {
            // Collapse-split into words (an all-space paragraph yields an
            // empty visual line, preserved for count stability), then
            // decompose each into its display text + explicit ops.
            let words = para.split(separator: " ")
                .map { WordBreakOpportunities.analyze(String($0)) }
            guard !words.isEmpty else { out.append(""); continue }
            // Greedy fold. `line` is the committed-so-far content of the
            // current line. The first word always opens a line; an
            // opening word that overflows breaks at its own ops first
            // (CSS 2.1 §9.5 overflow only remains for words with none).
            var line = open(words[0], maxWidth: maxWidth, out: &out,
                            measure: measure, hyphenate: hyphenate,
                            hyphenChar: hyphenChar)
            for word in words.dropFirst() {
                // Candidate = current line + separator + next word,
                // measured as ONE string so the space's own advance and
                // any kerning are included exactly as rendered.
                let candidate = line + " " + word.text
                if measure(candidate) <= maxWidth {
                    line = candidate       // fits → keep accumulating
                    continue
                }
                // Before moving the whole word down, offer its ops the
                // chance to split it ACROSS the break — the head stays on
                // the current line (an op inside the word sits AFTER the
                // space, so it is the greedier break). No overflow
                // fallback here: if no head fits, the plain space break
                // below is the correct earlier opportunity.
                if let (head, tail) = split(word, after: line + " ",
                                            maxWidth: maxWidth, measure: measure,
                                            hyphenate: hyphenate, hyphenChar: hyphenChar,
                                            overflowFallback: false) {
                    out.append(line + " " + head)
                    // The tail opens the next line and may need splitting
                    // again (a long word can span three lines in a narrow box).
                    line = open(tail, maxWidth: maxWidth, out: &out,
                                measure: measure, hyphenate: hyphenate,
                                hyphenChar: hyphenChar)
                    continue
                }
                out.append(line)           // commit the full line…
                line = open(word, maxWidth: maxWidth, out: &out,
                            measure: measure, hyphenate: hyphenate,
                            hyphenChar: hyphenChar)
            }
            out.append(line)               // trailing partial line
        }
        return out
    }

    /// Open a line with `word`, splitting at its opportunities while the
    /// remainder still overflows; each head becomes a committed line and
    /// the final remainder is returned as the (open) current line.
    ///
    /// Identity — returns `word.text` and appends nothing — whenever the
    /// word fits, or has no ops and no dictionary, or nothing usable
    /// fits: that last case is the CSS 2.1 §9.5 overflow the pre-wave-40
    /// fold already produced, EXCEPT for explicit ops, where the
    /// overflow fallback breaks at the first op to minimize the overflow
    /// (see `WordBreakOpportunities.split`).
    private static func open(
        _ word: WordBreakOpportunities.Word,
        maxWidth: CGFloat,
        out: inout [String],
        measure: (String) -> CGFloat,
        hyphenate: ((String) -> [Int])?,
        hyphenChar: String
    ) -> String {
        var rest = word
        // Bounded: every accepted split strictly shortens `rest`, and the
        // loop stops the moment no point is usable.
        while measure(rest.text) > maxWidth,
              let (head, tail) = split(rest, after: "", maxWidth: maxWidth,
                                       measure: measure, hyphenate: hyphenate,
                                       hyphenChar: hyphenChar,
                                       // An opening word that cannot fit any
                                       // head still breaks at its FIRST
                                       // explicit op (Chromium minimizes the
                                       // overflow; hyphens-none-012's ref
                                       // wraps `imple-menta-tion` in a box
                                       // `imple-` alone overflows).
                                       overflowFallback: true) {
            out.append(head)
            rest = tail
        }
        return rest.text
    }

    /// Resolve the ops for `word` — §6.1 priority first — and delegate to
    /// the shared greedy split walk.
    ///
    /// Explicit marks (soft hyphens recorded by `analyze`, literal
    /// hyphens) suppress the dictionary for THIS word: css-text-3 §5.3
    /// gives characters inside the word priority over the hyphenation
    /// resource. Only a mark-free word under `hyphens: auto` consults
    /// `hyphenate`, whose offsets become paint-a-hyphen points exactly as
    /// in wave 40. The overflow fallback is confined to explicit ops so
    /// the wave-40 dictionary contract (no fitting point ⇒ whole-word
    /// overflow) is preserved byte for byte.
    private static func split(
        _ word: WordBreakOpportunities.Word,
        after prefix: String,
        maxWidth: CGFloat,
        measure: (String) -> CGFloat,
        hyphenate: ((String) -> [Int])?,
        hyphenChar: String,
        overflowFallback: Bool
    ) -> (head: String, tail: WordBreakOpportunities.Word)? {
        if !word.ops.isEmpty {
            return WordBreakOpportunities.split(
                word, ops: word.ops, after: prefix, maxWidth: maxWidth,
                measure: measure, hyphenChar: hyphenChar,
                overflowFallback: overflowFallback)
        }
        // Mark-free word: the dictionary (when engaged) owns the split.
        guard let hyphenate = hyphenate else { return nil }
        let ops = hyphenate(word.text)
            .map { WordBreakOpportunities.Op(offset: $0, paintsHyphen: true) }
        guard !ops.isEmpty else { return nil }
        return WordBreakOpportunities.split(
            word, ops: ops, after: prefix, maxWidth: maxWidth,
            measure: measure, hyphenChar: hyphenChar,
            // Dictionary points never use the fallback — wave-40 contract.
            overflowFallback: false)
    }

    /// Wave 37 (lane W7, rule B) — does any committed line overflow the
    /// wrap width WITH NOWHERE LEFT TO BREAK?
    ///
    /// `lines(text:maxWidth:measure:)` guarantees a fit for every line
    /// EXCEPT one holding a single word wider than `maxWidth`, which CSS
    /// 2.1 §9.5 / css-text-3 §5.5 require to overflow the line box rather
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
    /// the hyphenator is off; under `hyphens: auto` §5.3 adds the
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
