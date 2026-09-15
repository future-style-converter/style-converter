//
//  GapDecorationBands.swift
//  StyleEngine/columns — wave 50, lane B10 (docs/BACKLOG.md queue 7(a)).
//  PURE, view-free. RULE-parallel with Compose's
//  …/columns/GapDecorationBands.kt: same `resolve` entry point, same
//  gates, same fallback. The ARITHMETIC deliberately differs: Compose
//  rounds base sizes / gap / container cross to Int and splits the
//  leftover as an integer share with the remainder handed to the leading
//  lines (FlexWrapLines.stretchLines), while Swift keeps CGFloat and
//  gives every line an equal fractional share (FlexWrapPlan.stretchLines)
//  — observable as Android's 1px offset on flex-gap-decorations-046's
//  second band. (Derivation, from that test's own numbers below: base
//  [50,50,50], gap 5, container cross 180 → leftover 20; Compose lines
//  57/57/56, Swift 56.67 each, so the second band sits at 119 on Compose
//  against 118.33 here — two-thirds of a point, one pixel once
//  rasterised. The first band differs by only a third of a point and does
//  not cross a pixel boundary.)
//
//  THE DEFECT. GapDecorationLines.toLines gives each line the UNION of
//  its item frames as its cross extent. That is the line box only while
//  the items fill their line. `align-content: stretch` — which is what
//  the flex initial `normal` computes to, css-align-3 §5.1 — grows every
//  line into a DEFINITE container cross size (css-flexbox-1 §9.4 step 8),
//  and the union then under-reports the line box at both ends.
//
//  MEASURED, against the frozen refs under tools/wpt/refs/
//  9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
//  white-black-ink-font-lh-imgpad-htmlpins/css-gaps/ and the wave49-final
//  ios-screenshots of the same tests:
//
//    flex-gap-decorations-045 (column flex, 120pt content width,
//    column-gap 5, two lines of 50pt-wide items): Chromium's line boxes
//    are content-x [0,57.5] and [62.5,120], so its 5pt red column rule
//    lands on image x [76,81]. The union model centres the same rule in
//    [50,63] and paints it at image x [72,77] — FOUR POINTS LEFT — while
//    the gold row rules come out 7.5pt short on each line.
//
//    flex-gap-decorations-046 (row flex, 180pt content height, row-gap 5,
//    three lines of 50pt-tall items): ref gold bands at image y [73,78]
//    and [134,139]; iOS paints [70,74] and [131,136] — the ~3.5pt the
//    backlog item quotes.
//
//  WHY THE ARITHMETIC IS RE-RUN HERE. FlowLayout already computes these
//  bands (it calls FlexWrapPlan.stretchLines), but a SwiftUI `Layout` has
//  no channel to a sibling overlay: the item frames only reach the
//  painter because each child raises an Anchor<CGRect> preference. Rather
//  than invent a second channel, this file calls THE SAME pure function
//  the layout calls, on the same inputs — one implementation, so the two
//  cannot drift.
//
//  THE ONE INPUT THAT IS TAKEN ON TRUST is the used cross gap. It cannot
//  be cross-checked against the item frames (the identity
//  `container = Σ lineSize + (n-1)·gap` is satisfied for EVERY gap once
//  the sizes are derived from it), so it is read from the same IR the
//  layout reads, and it is `nil` — refusing the whole reconstruction —
//  whenever the wire left it unresolved. Everything else IS checked: the
//  reconstruction must contain the items it claims to describe.
//

import CoreGraphics

/// Promotes item-union line extents to real §9.4-step-8 line boxes.
enum GapDecorationBands {

    /// Slack allowed when testing that an item union sits inside its
    /// reconstructed band. SwiftUI lays out in fractional points and the
    /// frames arrive through an anchor resolve, so an exact test would
    /// reject on arithmetic dust. Half a point is below the thinnest rule
    /// this family can paint (CSS `thin` == 1px).
    private static let containmentEps: CGFloat = 0.5

    /// Replace each line's cross extent with its §9.4-step-8 line box.
    ///
    /// - Parameters:
    ///   - lines: lines in flow order, as `GapDecorationLines.lines`
    ///     returns them. Flow order is cross order for a wrapping flex
    ///     container, and the reconstruction packs lines from the
    ///     cross-start edge (§9.6 with no distribution keyword).
    ///   - contentSize: the container's CONTENT box — the overlay's
    ///     GeometryReader size, which is the box css-flexbox-1 §9.4
    ///     distributes into.
    ///   - mainHorizontal: true for a row-direction container; decides
    ///     which physical axis is the cross axis.
    ///   - crossGapPx: the used cross-axis gap (`row-gap` for a row
    ///     container, `column-gap` for a column one), or nil when the
    ///     wire left it unresolved — in which case nothing is rebuilt.
    ///   - alignContentStretches: whether `align-content` distributes the
    ///     leftover cross space INTO the lines rather than merely placing
    ///     the line block (css-align-3 §5.1).
    /// - Returns: the lines with corrected cross extents, or `lines`
    ///   unchanged when the reconstruction is inadmissible.
    static func resolve(lines: [GapLine],
                        contentSize: CGSize,
                        mainHorizontal: Bool,
                        crossGapPx: CGFloat?,
                        alignContentStretches: Bool) -> [GapLine] {
        // One line has no inter-line gap; the only thing a band would
        // change is the run length of its own item-gap rules, and the
        // item union is the only evidence available for it.
        guard lines.count > 1 else { return lines }
        // Step 8 fires for `normal`/`stretch` only (css-align-3 §5.1).
        guard alignContentStretches else { return lines }
        // An unresolved gap makes the band arithmetic meaningless.
        guard let gap = crossGapPx else {
            _ = PropertyTracker.logOnce(
                key: "gapdec.bands.unresolved-cross-gap",
                message: "gap decorations: cross gap unresolved — kept the item-union line extents")
            return lines
        }
        // The container's cross content extent.
        let containerCross = mainHorizontal ? contentSize.height : contentSize.width
        guard containerCross > 0, containerCross.isFinite else { return lines }

        // §9.4 step 7 input: each line's content-derived cross size.
        let base = lines.map { $0.crossEnd - $0.crossStart }
        // §9.4 step 8 — equal share of the leftover. FlexWrapPlan owns
        // the rule, and FlowLayout calls the same function, so the bands
        // rebuilt here are the bands the items were placed against.
        let sizes = FlexWrapPlan.stretchLines(base: base,
                                              containerCross: containerCross,
                                              gap: gap)
        // No leftover ⇒ the lines already hug their items ⇒ the union IS
        // the line box. Returning the input keeps every already-correct
        // container byte-identical.
        guard sizes != base else { return lines }

        // §9.6 with no distribution keyword: lines pack from the
        // cross-start edge, separated by the used gap.
        var bands: [(start: CGFloat, end: CGFloat)] = []
        var cursor: CGFloat = 0
        for size in sizes {
            bands.append((cursor, cursor + size))
            cursor += size + gap
        }

        // AGREEMENT GATE. The reconstruction is a claim about where the
        // renderer put the lines; the item frames are the evidence. A
        // container that did NOT lay out this way — `wrap-reverse` on the
        // frozen path, an overflowing line, a cross size that was not
        // definite after all — fails containment, and the union model,
        // derived from the same evidence, is the honest answer.
        for (i, line) in lines.enumerated() {
            if line.crossStart < bands[i].start - containmentEps
                || line.crossEnd > bands[i].end + containmentEps {
                _ = PropertyTracker.logOnce(
                    key: "gapdec.bands.disagree",
                    message: "gap decorations: rebuilt line bands do not contain the placed items — kept the item-union extents")
                return lines
            }
        }
        // Accepted: paint against the line boxes, not the item unions.
        return lines.enumerated().map { i, line in
            var out = line
            out.crossStart = bands[i].start
            out.crossEnd = bands[i].end
            return out
        }
    }
}
