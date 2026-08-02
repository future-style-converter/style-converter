//
//  GapDecorationLines.swift
//  StyleEngine/columns — wave 24, lane GAPS-I. PURE, view-free.
//
//  (a) LINE GROUPING BY CROSS-AXIS POSITION. Byte-parallel with the
//  Compose twin (…/columns/GapDecorationLines.kt): same member names
//  mainOf / crossOf / groupIntoLines / toLines / betweenLineGaps.
//
//  Pinned against FRESH renders of the WPT css-gaps flex refs (the
//  capture-browser-ref.mjs recipe — same puppeteer BROWSER_LAUNCH_ARGS,
//  same injected canvas CSS); the committed refs are stale:
//
//    • ref 008 (flex-wrap: nowrap, six 50px items in a 200px box) must
//      stay ONE line even though the items overflow — so the wrap test
//      is "did the main cursor move BACKWARDS", never "did we pass the
//      content width".
//    • ref 007 (align-items: flex-end, items 18px and 40px tall) pins
//      the line's cross extent as the UNION of its items: the rules are
//      40 tall (ref divs [52,2,10,40]), not 18.
//

import CoreGraphics

/// One flex line: its cross extent plus its items' main-axis spans, in
/// flow order. (Kotlin twin: data class GapFlexLine.)
struct GapLine: Equatable {
    /// Cross-axis start of the line box (union of its items).
    var crossStart: CGFloat
    /// Cross-axis end of the line box.
    var crossEnd: CGFloat
    /// Each item's main-axis span, in flow order.
    var itemSpans: [GapSpan]

    /// The gaps BETWEEN adjacent items — the bands an item-gap rule is
    /// painted into. Zero-width bands are dropped: `column-gap: 0`
    /// decorates nothing.
    var gapBands: [GapSpan] {
        // Pairwise walk over the flow-ordered items.
        zip(itemSpans, itemSpans.dropFirst())
            .map { GapSpan(start: $0.end, end: $1.start) }
            .filter(\.isPositive)
    }
}

/// Line grouping. (Kotlin twin: object GapDecorationLines.)
enum GapDecorationLines {

    /// The rect's MAIN-axis span for this container.
    static func mainOf(_ r: CGRect, mainHorizontal: Bool) -> GapSpan {
        // Row direction → main is x; column direction → main is y.
        mainHorizontal ? GapSpan(start: r.minX, end: r.maxX)
                       : GapSpan(start: r.minY, end: r.maxY)
    }

    /// The rect's CROSS-axis span for this container.
    static func crossOf(_ r: CGRect, mainHorizontal: Bool) -> GapSpan {
        // Exactly the other axis from mainOf.
        mainHorizontal ? GapSpan(start: r.minY, end: r.maxY)
                       : GapSpan(start: r.minX, end: r.maxX)
    }

    /// Split flow-ordered item rects into lines. A new line starts when
    /// the main cursor moves BACKWARDS — the only signal a flex wrap
    /// leaves behind, and the one that keeps ref 008's overflowing
    /// nowrap row a single line.
    static func groupIntoLines(items: [CGRect], mainHorizontal: Bool) -> [[CGRect]] {
        // Nothing measured yet (first layout pass).
        guard !items.isEmpty else { return [] }
        var out: [[CGRect]] = []
        // Main-axis end of the previously placed item.
        var lastMainEnd: CGFloat = -.infinity
        for item in items {
            let main = mainOf(item, mainHorizontal: mainHorizontal)
            // 0.01pt slack absorbs flex arithmetic noise between lines.
            if out.isEmpty || main.start < lastMainEnd - 0.01 {
                out.append([item])
            } else {
                out[out.count - 1].append(item)
            }
            lastMainEnd = main.end
        }
        return out
    }

    /// Collapse grouped rects into GapLines.
    static func toLines(_ grouped: [[CGRect]], mainHorizontal: Bool) -> [GapLine] {
        grouped.compactMap { group in
            // An empty group cannot describe a line box.
            guard let first = group.first else { return nil }
            var cross = crossOf(first, mainHorizontal: mainHorizontal)
            var spans: [GapSpan] = []
            for r in group {
                let c = crossOf(r, mainHorizontal: mainHorizontal)
                // UNION of the items' cross extents — ref 007's pin.
                cross.start = min(cross.start, c.start)
                cross.end = max(cross.end, c.end)
                spans.append(mainOf(r, mainHorizontal: mainHorizontal))
            }
            return GapLine(crossStart: cross.start, crossEnd: cross.end,
                           itemSpans: spans)
        }
    }

    /// One-shot convenience: rects → lines.
    static func lines(from items: [CGRect], mainHorizontal: Bool) -> [GapLine] {
        toLines(groupIntoLines(items: items, mainHorizontal: mainHorizontal),
                mainHorizontal: mainHorizontal)
    }

    /// The cross-axis gap band between each pair of adjacent lines — the
    /// bands a line-gap rule is painted into. Non-positive bands
    /// (touching or overlapping lines) are dropped.
    static func betweenLineGaps(_ lines: [GapLine]) -> [GapSpan] {
        // Pairwise walk over the lines in flow order.
        zip(lines, lines.dropFirst())
            .map { GapSpan(start: $0.crossEnd, end: $1.crossStart) }
            .filter(\.isPositive)
    }
}
