//
//  GapDecorationSegments.swift
//  StyleEngine/columns — wave 24, lane GAPS-I. PURE, view-free.
//
//  Item frames + a GapDecorationsConfig → the exact rects css-gaps-1
//  says to paint. Byte-parallel with the Compose twin
//  (…/columns/GapDecorationSegments.kt): same `build` entry point over
//  the shared GapDecorationLines / GapIntervals primitives.
//
//  THE RENDER MODEL, re-derived from FRESH renders of the WPT css-gaps
//  flex refs (tools/wpt/css/css-gaps/flex/flex-gap-decorations-0NN-ref
//  .html through the capture-browser-ref.mjs recipe — same puppeteer
//  BROWSER_LAUNCH_ARGS, same injected canvas CSS; the committed refs are
//  stale). Ref coordinates below are the flexbox BORDER-box space the
//  refs' absolutely-positioned decoration divs live in; this file works
//  in CONTENT-box space, i.e. those numbers minus the 2px border.
//
//   (a) LINE GROUPING → GapDecorationLines (see that file).
//   (b) ITEM-GAP RULES (the column rules of a row-direction container)
//       sit centred in each adjacent-item gap band and span the LINE's
//       cross extent — ref 003's third column div is x102–112 × y62–112
//       = line 2 exactly.
//   (c) LINE-GAP RULES (row rules) sit centred in each adjacent-line gap
//       band and span the container's whole content main extent — ref
//       003's row divs are [2,52,170,10].
//   (d) BREAKS. SPANNING_ITEM (the CSS initial) lets an item-gap rule
//       CROSS the line gap when the neighbouring line has a band at the
//       same position — refs 001/002 paint one column rule down the
//       container's full height. A spanning item severs it AT THE LINE
//       EDGE: ref 012 turns `rule-overlap: column-over-row` on, which
//       makes those ends visible, and its column divs are y2–52 /
//       y122–172 with no bleed into the row gap. INTERSECTION cuts a
//       line-gap rule over BOTH neighbouring lines' item-gap bands —
//       ref 009's row children are x2–52 / 62–102 / 122–172.
//   (e) INSETS shrink (positive) or EXTEND (negative) both ends — ref
//       011's `column-rule-inset: -2px` grows 50px runs to 54px, the
//       first starting 2px ABOVE the content box.
//   (f) RULE-OVERLAP orders the two families (ref 003 row-over-column,
//       ref 012 column-over-row).
//
//  Test 006 is out of scope: `writing-mode: vertical-lr` is the wall.
//

import SwiftUI

/// One paintable rule run. `isRowRule` picks the family for the
/// `rule-overlap` ordering AND the line orientation (a column rule is
/// always a vertical line, a row rule always horizontal). The rect is in
/// the container's CONTENT-box space.
struct GapRuleSegment: Equatable {
    /// Where to paint, content-box relative.
    var rect: CGRect
    /// Resolved colour; nil = inherit (`currentColor`).
    var color: Color?
    /// Line style — strokes go through the border machinery.
    var style: BorderStyleValue
    /// True for `row-rule-*`, false for `column-rule-*`.
    var isRowRule: Bool
}

enum GapDecorationSegments {

    // MARK: - (b)+(d)+(e) item-gap rules

    /// Rules painted in the gaps BETWEEN ITEMS of a line. For a
    /// row-direction container these are the `column-rule-*` runs.
    /// Returns (main, cross) interval pairs in content-box coordinates.
    static func itemGapRuns(lines: [GapLine],
                            spec: GapRuleSpec,
                            contentCross: GapSpan) -> [(main: GapSpan, cross: GapSpan)] {
        // No ink → no runs (keeps the family free when a container
        // declares, say, only a colour).
        // `effectiveWidthPx`, not `widthPx`: an undeclared width is the
        // CSS initial `medium` (3px), not "no rule".
        guard spec.paints else { return [] }
        let w = spec.effectiveWidthPx
        // ONE RUN PER LINE, ALWAYS — an item-gap rule NEVER crosses a
        // line gap, whatever `*-rule-break` says.
        //
        // This was measured in Chrome 150, not inferred from the refs.
        // Take flex-gap-decorations-002's 2×2 layout, narrow BOTH rules
        // to 2px so neither can mask the other, and scan the column band
        // (x54) down through the row gap (y50…60):
        //     y49 red · y50–53 WHITE · y54–55 blue · y56–59 WHITE · y60 red
        // The column rule stops dead at each line edge; only the row
        // rule's own 2px band has ink in between. Repeating the scan
        // with `rule-overlap: column-over-row` and with
        // `column-rule-break: intersection` gives byte-identical
        // results. (Probe: tools/visual/_skeptic/probe-scan.mjs.)
        //
        // The refs cannot see this. agnostic/gap-decorations-001-ref
        // (the match for flex 002) draws its column-gap div `height:
        // 110px`, i.e. full-container, and flex/…-015-ref draws its row
        // rules as two 50px runs with a hole — but in BOTH the crossing
        // square is repainted by the other family, so a merged and an
        // unmerged rule are pixel-identical there. Reading the ref's
        // markup as the model is exactly the trap the 55px bleed in
        // ref 003 already demonstrates. The Compose twin
        // (GapDecorationSegments.withinLineSegments) is per-line too.
        //
        // `contentCross` is therefore unused; it stays in the signature
        // for the still-unmapped CSS `none` keyword (44 occurrences under
        // tools/wpt/css/css-gaps/, currently rejected by the converter
        // into GenericProperty and never reaching this file), which is
        // the one value that could want a container-wide extent.
        _ = contentCross
        // `*-rule-break` has nothing left to decide for THIS family in
        // flex: `normal`/`spanning-item` break at spanning items, which
        // css-flexbox-1 §5.2 forbids, and `intersection`'s crossings are
        // the line gaps a per-line run already stops at. Named, not
        // silently dropped — grid will need the distinction.
        _ = spec.breakMode
        return lines.flatMap { line -> [(main: GapSpan, cross: GapSpan)] in
            let extent = GapSpan(start: line.crossStart, end: line.crossEnd)
            // The inset moves both ends; negative EXTENDS them past the
            // line edge (ref 011's `column-rule-inset: -2px`).
            guard let cross = GapIntervals.inset(extent, insetPx: spec.insetPx) else {
                return []
            }
            return line.gapBands.map {
                (GapIntervals.band(gap: $0, widthPx: w), cross)
            }
        }
    }

    // MARK: - (c)+(d)+(e) line-gap rules

    /// Rules painted in the gaps BETWEEN LINES. For a row-direction
    /// container these are the `row-rule-*` runs.
    static func lineGapRuns(lines: [GapLine],
                            spec: GapRuleSpec,
                            contentMain: GapSpan) -> [(main: GapSpan, cross: GapSpan)] {
        // Same no-ink short circuit; one line has no line gaps.
        guard spec.paints, lines.count > 1 else { return [] }
        // Same `medium` initial as the item-gap family.
        let w = spec.effectiveWidthPx
        var out: [(main: GapSpan, cross: GapSpan)] = []
        for i in 0..<(lines.count - 1) {
            // The band between two adjacent line boxes.
            let gap = GapSpan(start: lines[i].crossEnd, end: lines[i + 1].crossStart)
            // A zero/negative band (touching lines) decorates nothing.
            guard gap.isPositive else { continue }
            let cross = GapIntervals.band(gap: gap, widthPx: w)
            // Default run: the whole content main extent — refs 003/010/
            // 011 all pin the row rule at the full 170px content width.
            var runs = [contentMain]
            if spec.breakMode == .intersection {
                // Ref 009: cut over the union of BOTH neighbouring lines'
                // item-gap bands — the crossing GAPS, not the crossing
                // rule widths (a rule may be narrower than its gap).
                runs = GapIntervals.subtract(
                    base: contentMain,
                    cuts: lines[i].gapBands + lines[i + 1].gapBands)
            }
            // SPANNING_ITEM would additionally break where an item spans
            // the LINE gap; a flex item can never span two flex lines
            // (css-flexbox-1 §5.2), so there is nothing to cut. Stated
            // rather than silently skipped.
            for r in runs {
                if let main = GapIntervals.inset(r, insetPx: spec.insetPx) {
                    out.append((main, cross))
                }
            }
        }
        return out
    }

    // MARK: - (f) assembly + paint order

    /// The full segment list for one container, BACK TO FRONT.
    /// `contentSize` is the container's CONTENT box; `items` are the
    /// measured item frames in that same space.
    static func build(items: [CGRect],
                      contentSize: CGSize,
                      mainHorizontal: Bool,
                      config: GapDecorationsConfig) -> [GapRuleSegment] {
        // Cheapest possible bail — the state every component in the
        // committed corpus is in (no *-rule-* resolves to ink).
        guard config.isActive else { return [] }
        let lines = GapDecorationLines.lines(from: items, mainHorizontal: mainHorizontal)
        guard !lines.isEmpty else { return [] }
        // Content extents split into (main, cross) for this axis.
        let mainExtent = GapSpan(start: 0,
                                 end: mainHorizontal ? contentSize.width : contentSize.height)
        let crossExtent = GapSpan(start: 0,
                                  end: mainHorizontal ? contentSize.height : contentSize.width)
        // Which family owns which gap kind: item gaps run ALONG the main
        // axis, so in a row-direction container they are the COLUMN gaps
        // and in a column-direction container the ROW gaps.
        let itemSpec = mainHorizontal ? config.column : config.row
        let lineSpec = mainHorizontal ? config.row : config.column
        // Item-gap family.
        let itemSegs = itemGapRuns(lines: lines, spec: itemSpec, contentCross: crossExtent)
            .map { GapRuleSegment(rect: place($0, mainHorizontal: mainHorizontal),
                                  color: itemSpec.color,
                                  style: itemSpec.style ?? .solid,
                                  isRowRule: !mainHorizontal) }
        // Line-gap family.
        let lineSegs = lineGapRuns(lines: lines, spec: lineSpec, contentMain: mainExtent)
            .map { GapRuleSegment(rect: place($0, mainHorizontal: mainHorizontal),
                                  color: lineSpec.color,
                                  style: lineSpec.style ?? .solid,
                                  isRowRule: mainHorizontal) }
        // rule-overlap: the winning family goes LAST so it paints on top.
        let (column, row) = mainHorizontal ? (itemSegs, lineSegs) : (lineSegs, itemSegs)
        return config.overlap == .rowOverColumn ? column + row : row + column
    }

    // MARK: - helpers

    /// (main, cross) → a rect in the container's coordinate space.
    private static func place(_ run: (main: GapSpan, cross: GapSpan),
                              mainHorizontal: Bool) -> CGRect {
        // Horizontal main → main is x, cross is y; vertical main swaps.
        mainHorizontal
            ? GapIntervals.rect(horizontal: run.main, vertical: run.cross)
            : GapIntervals.rect(horizontal: run.cross, vertical: run.main)
    }
}
