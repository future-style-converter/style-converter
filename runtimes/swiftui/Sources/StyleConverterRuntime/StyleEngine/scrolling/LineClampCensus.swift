//
//  LineClampCensus.swift
//  StyleEngine/scrolling — Wave 46 (lane Y1): the line-box CENSUS behind
//  LineClampCap, for clamp roots whose line boxes are NOT all one height.
//
//  A uniform cap (N × the root's line box — the Compose wave-41 model)
//  is exact only while every line box in the container is the root's.
//  css-overflow-4 §5 counts LINE BOXES, each as tall as the run that
//  produced it (CSS 2.1 §10.8): line-clamp-005/-006/-007 nest a
//  `font: 24px/48px` child inside a `16px/32px` root, so the ref closes
//  a 3-line clamp at 32+32+48 = 112px while the uniform cap says 96 —
//  the wave-45 Android capture shows exactly that 16px-short yellow box
//  on -005, and on -006 (clamp 5: 32+32+48+48+32 = 192 vs 160) the cap
//  discards "Line 5" which the ref keeps (Android 0.9446, iOS — uncapped
//  — passes only because it paints everything). The census walks the
//  container's inline content IN DOCUMENT ORDER (meta.runs when present,
//  else leading text then children — the order the renderer paints),
//  accumulating each run's line boxes until the Nth, and bails to the
//  uniform model the moment a run's line count cannot be PROVEN —
//  an under-count would clip a visible line, which is worse than the
//  drift the census removes.
//

import CoreGraphics
import Foundation

/// One run of the container's inline content: the height of ITS line
/// boxes and how many it produces — nil when the count is not provable
/// (a soft-wrapping run of unknown wrap width).
struct LineClampRun: Equatable {
    let lineBoxPx: CGFloat
    let exactLines: Int?
    /// Vertical padding + border + margin ABOVE a block child's first
    /// line box (0 for the root's own text runs); nil when the child's
    /// band is declared in a shape this reader cannot resolve.
    var leadingBandPx: CGFloat? = 0
    /// Non-nil: the run is an UNFRAGMENTABLE box of this full height that
    /// produces NO line boxes of this container — a scroll container
    /// (css-overflow-4 §5 fragments the container's own line boxes; a
    /// child with a non-`visible` overflow is an independent formatting
    /// context the break cannot enter — line-clamp-007's `overflow: auto`
    /// child stays whole and "Line 5" after it is the 3rd line box) or a
    /// box with an explicit height (the `<br>` placeholders' 0 / 20px).
    var monolithicPx: CGFloat? = nil
}

enum LineClampCensus {

    /// The census verdict for a clamp of `lines` over `runs`.
    enum Verdict: Equatable {
        /// The Nth line box was reached: the content box height at its
        /// bottom edge — the cap.
        case capped(CGFloat)
        /// Every run is proven and together they hold FEWER than N line
        /// boxes: nothing to discard, no cap needed.
        case short
        /// A count is unprovable but EVERY run lays out on the root's
        /// line box with no bands and no monolithic box: the uniform
        /// N × root-line-box model is then exact whatever the counts are
        /// (the Compose wave-41 cap) — apply it.
        case uniform
        /// A count is unprovable AND some run has a different line box,
        /// a band or a monolithic height: the Nth line box's bottom edge
        /// cannot be known, and a guessed cap would clip a visible line
        /// (block-ellipsis-003's 1.5em child on a 16px root, where the
        /// uniform 60px cap slices "Line 3" in half on Android). No cap.
        case unbounded
    }

    /// Walk the runs in order, taking line boxes until N are taken.
    static func verdict(lines: Int, runs: [LineClampRun], rootLineBoxPx: CGFloat) -> Verdict {
        // Pure defensive guard — the grammar is <integer [1,∞]>.
        guard lines >= 1 else { return .unbounded }
        var remaining = lines
        var height: CGFloat = 0
        for run in runs {
            // A monolithic box is kept whole and yields no line box.
            if let m = run.monolithicPx { height += m; continue }
            // An unprovable count or band before the Nth box: decide
            // between the uniform model and no cap on the WHOLE list.
            guard let n = run.exactLines, let band = run.leadingBandPx else {
                return fallback(runs, rootLineBoxPx: rootLineBoxPx)
            }
            // A glyph-less run produces no line box — it costs nothing.
            if n == 0 { continue }
            // The child's top band sits above its first line box.
            height += band
            // Take what this run contributes, up to the clamp.
            let taken = min(n, remaining)
            height += CGFloat(taken) * run.lineBoxPx
            remaining -= taken
            // The Nth line box closes the content box.
            if remaining == 0 { return .capped(height) }
        }
        // Ran out of content before the clamp: nothing is discarded.
        return .short
    }

    /// The unprovable-count split: uniform iff every run is a plain run
    /// on the root's line box (then N × box is exact regardless of how
    /// the lines distribute), else unbounded.
    static func fallback(_ runs: [LineClampRun], rootLineBoxPx: CGFloat) -> Verdict {
        let uniform = runs.allSatisfy {
            $0.monolithicPx == nil && $0.leadingBandPx == 0 && $0.lineBoxPx == rootLineBoxPx
        }
        return uniform ? .uniform : .unbounded
    }

    /// The number of line boxes a text run produces under a white-space
    /// keyword, or nil when soft wrapping could add lines we cannot count
    /// without a layout pass. Keyword spellings: the live wire ships the
    /// serializer's enum names (`PRE_WRAP`, lowercased by WhiteSpaceExtractor
    /// to `pre_wrap`); the hyphen forms cover CSS-keyword-shaped documents.
    ///
    /// css-text-3 §4.1.1 (segment breaks) + §5.1 (wrapping):
    ///   • `pre`/`nowrap` never soft-wrap → `pre` keeps each segment as
    ///     one line box, `nowrap` collapses breaks → exactly one line;
    ///   • `pre-wrap`/`pre-line`/`break-spaces` preserve breaks but wrap
    ///     at spaces → provable only when no segment holds a space/tab;
    ///   • `normal` collapses breaks INTO spaces (wrap opportunities) →
    ///     provable only for a single whitespace-free word.
    /// An empty run produces no line box (0).
    static func exactLineCount(_ text: String, whiteSpace: String?) -> Int? {
        // No glyphs → no line box, under every keyword.
        if text.isEmpty { return 0 }
        // Normalize the two spellings once.
        let ws = (whiteSpace ?? "normal").lowercased().replacingOccurrences(of: "_", with: "-")
        // Hard segment breaks: one line box per segment (reduce, not
        // split — an empty trailing segment is still a line box, like
        // LineBoxMetrics.renderedLineCount).
        let hardLines = text.reduce(1) { $1 == "\n" ? $0 + 1 : $0 }
        // Any soft-wrap opportunity inside a segment → not provable.
        let hasSpace = text.contains(" ") || text.contains("\t")
        switch ws {
        // No soft wrapping at all: segments ARE the lines.
        case "pre": return hardLines
        // No soft wrapping, breaks collapsed: one line, whatever the text.
        case "nowrap": return 1
        // Breaks preserved, spaces wrap → provable without spaces.
        case "pre-wrap", "pre-line", "break-spaces":
            return hasSpace ? nil : hardLines
        // `normal` (and anything unknown): breaks become spaces.
        default:
            return (hasSpace || text.contains("\n")) ? nil : 1
        }
    }
}
