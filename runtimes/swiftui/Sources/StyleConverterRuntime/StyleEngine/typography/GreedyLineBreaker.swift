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
    static func lines(text: String,
                      maxWidth: CGFloat,
                      measure: (String) -> CGFloat) -> [String] {
        var out: [String] = []
        // Hard breaks split paragraphs; each wraps independently.
        for para in text.components(separatedBy: "\n") {
            // Collapse-split into words; an all-space paragraph yields
            // an empty visual line, preserved for count stability.
            let words = para.split(separator: " ").map(String.init)
            guard !words.isEmpty else { out.append(""); continue }
            // Greedy fold: first word always opens the line (a line is
            // never empty — overlong first words overflow, CSS 2.1 §9.5).
            var line = words[0]
            for word in words.dropFirst() {
                // Candidate = current line + separator + next word,
                // measured as ONE string so the space's own advance and
                // any kerning are included exactly as rendered.
                let candidate = line + " " + word
                if measure(candidate) <= maxWidth {
                    line = candidate       // fits → keep accumulating
                } else {
                    out.append(line)       // commit the full line…
                    line = word            // …and open the next one
                }
            }
            out.append(line)               // trailing partial line
        }
        return out
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
    static func hasUnbreakableOverflowingLine(_ lines: [String],
                                              maxWidth: CGFloat,
                                              tolerance: CGFloat = 0.5,
                                              measure: (String) -> CGFloat) -> Bool {
        lines.contains {
            measure($0) > maxWidth + tolerance
                && !DecorationOps.hasSoftWrapOpportunity($0)
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
