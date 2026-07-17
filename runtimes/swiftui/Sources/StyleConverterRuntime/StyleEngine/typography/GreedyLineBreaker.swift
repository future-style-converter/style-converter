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

    // MARK: - Production measurer (TextKit metrics)

    /// A measurer over the EXACT resolved render font + spacing values,
    /// so the greedy fit test uses the same advances SwiftUI's Text
    /// layout (TextKit-backed) will produce. Letter-spacing rides
    /// `.kern` on every glyph (SwiftUI `.tracking` equivalent);
    /// word-spacing ADDS to the kern of each space character
    /// (css-text-3 §8.1 — word-separator advance), mirroring the render
    /// path in PlaceholderLabel.wordSpacedText.
    static func measurer(font: UIFont,
                         letterSpacingPx: CGFloat?,
                         wordSpacingPx: CGFloat?) -> (String) -> CGFloat {
        return { s in
            // Base attributes: the render face + uniform tracking.
            var attrs: [NSAttributedString.Key: Any] = [.font: font]
            if let t = letterSpacingPx { attrs[.kern] = t }
            let a = NSMutableAttributedString(string: s, attributes: attrs)
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
