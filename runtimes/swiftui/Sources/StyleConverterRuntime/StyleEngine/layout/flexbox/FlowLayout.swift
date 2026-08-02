//
//  FlowLayout.swift
//  StyleEngine/layout/flexbox — the `flex-wrap: wrap` container.
//
//  Split out of FlexboxApplier.swift at wave 25 (lane IGAPS, CAL-RC5):
//  the cross-axis pass below pushed that file past the 300-line split
//  threshold, and the wrap layout is an independent unit anyway — the
//  nowrap path is CSSFlexLayout.
//
//  WHAT WAVE 25 ADDS — §8.4 CROSS-AXIS STRETCH. The pre-wave-25 wrap
//  layout placed every child at its INTRINSIC size, which meant a
//  width-only flex item had no cross size at all:
//  flex-gap-decorations-001 and -002 declare
//  `.flex-item { width: 50px }` with no height inside a `height: 110px`
//  wrap container, and rendered as an empty box on iOS. CSS says the
//  opposite — `align-items` is initially `normal`, which behaves as
//  `stretch` in flex (css-align-3 §6.1), so an item with an AUTO cross
//  size fills its LINE's cross extent (css-flexbox-1 §8.4). The line's
//  own cross extent comes from §9.6: `align-content: normal` also
//  behaves as stretch, so when the container's cross size is definite
//  the leftover cross space is divided EQUALLY among the lines. For 002
//  that is (110 − 10 row-gap) / 2 = 50px per line, and each 50px-wide
//  item stretches to 50px tall — the ref's four squares.
//
//  ROUND 3 (lane ISTRETCH) — WHERE THE STRETCH ACTUALLY LANDS. This
//  Layout proposes the line's cross extent to a stretching subview, and
//  a PROPOSAL is advisory: an IR child ignores it and reports its
//  intrinsic cross for every proposal, `.infinity` included. So the
//  forced cross size is folded into the child's own SizeConfig at BUILD
//  time (ComponentRenderer.flexWrapStretchPlan → the `gridStretchHeight`
//  channel), exactly as the grid and column-flex paths already do, which
//  is iOS's answer to Compose measuring stretch items with a fixed
//  cross band. The two runs MUST agree, so the line arithmetic below no
//  longer lives here: it moved to `FlexWrapPlan`, which both call.
//
//  STILL TODO here (named, not silently skipped): per-line
//  justify-content, wrap-reverse ordering, and the non-stretch
//  align-content keywords (start/center/end/space-*). Those need the
//  same treatment the nowrap path got in fidelity wave 2. Column-
//  direction wrap is also unimplemented — this Layout lays out ROWS
//  whatever `flex-direction` says (the pre-wave-25 behaviour), which is
//  why the build-time plan refuses column containers instead of
//  injecting a cross size the placement would contradict.
//

import SwiftUI

/// Wrapping flex container (`flex-wrap: wrap` / `wrap-reverse`).
/// Rows are laid left→right and broken when the next item would exceed
/// the proposed width; lines stack top→bottom.
@available(iOS 16.0, *)
struct FlowLayout: Layout {
    /// Horizontal gap between items on a single line, in points.
    var horizontalSpacing: CGFloat = 0
    /// Vertical gap between wrapped lines, in points.
    var verticalSpacing: CGFloat = 0
    /// Container `align-items` (§8.3 cross-axis default). Nil = the CSS
    /// initial `normal`, which behaves as `stretch` in a flex container.
    var alignItems: AlignmentKeyword? = nil
    /// True when the IR declared an explicit cross (block) size. Only
    /// then is there leftover cross space for §9.6 to hand to the lines;
    /// otherwise the container hugs its lines and stretch is a no-op.
    var definiteCross: Bool = false
    /// Container `align-content`. Nil = the CSS initial `normal`, which
    /// behaves as `stretch` for a flex container (css-align-3 §5.3).
    ///
    /// SKEPTIC (wave 25) — §9.6's equal division fires ONLY under
    /// normal/stretch. Every other keyword leaves the leftover as free
    /// space and merely POSITIONS the line block, so stretching under
    /// `center` / `space-between` moves the lines to coordinates the
    /// browser never produces. The Compose lane's FlexWrapLines gates
    /// its identical step-8 on exactly this predicate
    /// (`alignContentStretches`); without the gate here the two runtimes
    /// disagree the moment a fixture declares align-content on a wrap
    /// container. Corpus-inert today: no WPT or property fixture pairs
    /// `align-content` with `flex-wrap` (grep AlignContent+FlexWrap → 0).
    var alignContent: AlignmentKeyword? = nil

    /// css-align-3 §5.3 — does `align-content` distribute leftover cross
    /// space to the LINES (rather than merely position them)? Delegated
    /// to FlexWrapPlan so the Layout and the renderer's build-time plan
    /// can never answer it differently.
    private var alignContentStretches: Bool {
        FlexWrapPlan.alignContentStretches(alignContent)
    }

    /// One measured item inside a line.
    private struct Item {
        /// Index into `subviews`.
        var index: Int
        /// Intrinsic size — the wrap decision and the main-axis size.
        var size: CGSize
        /// Effective alignment after align-self / align-items resolution.
        var align: AlignmentKeyword
        /// True when §8.4 lets this item take the whole line cross:
        /// stretch alignment AND an auto (undeclared) cross size.
        var stretches: Bool
    }

    /// One wrapped line.
    private struct Line {
        /// Items in declaration order.
        var items: [Item] = []
        /// Main extent including inter-item gaps.
        var main: CGFloat = 0
        /// Cross extent — max intrinsic item cross, then §9.6-stretched.
        var cross: CGFloat = 0
    }

    /// Break the subviews into lines and resolve every line's cross
    /// extent. Shared by both protocol methods so the wrap decision and
    /// the stretch arithmetic can never drift between them.
    ///
    /// `maxMain` is the width to wrap against; `availCross` is the
    /// container's definite cross size, or nil when it hugs.
    private func plan(subviews: Subviews,
                      maxMain: CGFloat,
                      availCross: CGFloat?) -> [Line] {
        // Pass 1 — measure every item and resolve its §8.3 alignment.
        var items: [Item] = []
        for (i, sub) in subviews.enumerated() {
            // Ideal size at an unspecified proposal — CSS `flex-basis:
            // auto` on the main axis, and the item's own cross size.
            let size = sub.sizeThatFits(.unspecified)
            // v2 placement contract: the child carries its own claim
            // (same channel CSSFlexLayout reads). Nil = an anonymous
            // subview such as the harness placeholder → CSS initials.
            let placement = sub[ItemPlacementKey.self]
            let align = CSSFlexMath.resolvedAlign(self: placement?.flex.alignSelf,
                                                  items: alignItems)
            // §8.3: stretch only acts on items with an AUTO cross size.
            // This layout is row-direction only, so the cross axis is
            // always the block axis → Height/BlockSize.
            let crossAuto = placement.map { !$0.explicitHeight } ?? true
            items.append(Item(index: i, size: size, align: align,
                              stretches: align == .stretch && crossAuto))
        }
        // §9.3 line collection — the SHARED rule (FlexWrapPlan), so the
        // renderer's build-time plan breaks at exactly these indices and
        // the injected cross size can never belong to a different line.
        let ranges = FlexWrapPlan.breakLines(mainSizes: items.map(\.size.width),
                                             containerMain: maxMain,
                                             gap: horizontalSpacing)
        var lines: [Line] = ranges.map { r in
            var line = Line()
            line.items = (r.first...r.last).map { items[$0] }
            // Main extent = summed item mains + the inter-item gaps.
            line.main = line.items.reduce(0) { $0 + $1.size.width }
                + horizontalSpacing * CGFloat(r.count - 1)
            // §9.4 step 7 — hypothetical line cross = the tallest item.
            line.cross = line.items.map(\.size.height).max() ?? 0
            return line
        }
        // §9.6 align-content stretch (the initial `normal`): a definite
        // container cross size hands its leftover space out EQUALLY.
        // `definiteCross` keeps a hugging container untouched — that is
        // what keeps the committed Flex_Wrap baseline inert — and the
        // keyword gate lives in FlexWrapPlan.definiteCross.
        let stretched = FlexWrapPlan.stretchLines(
            base: lines.map(\.cross),
            containerCross: FlexWrapPlan.definiteCross(
                alignContentStretches: alignContentStretches,
                hasDefiniteCross: definiteCross,
                cross: availCross),
            gap: verticalSpacing)
        for i in lines.indices { lines[i].cross = stretched[i] }
        return lines
    }

    /// Wrapped size: widest line × summed line crosses plus row gaps.
    func sizeThatFits(proposal: ProposedViewSize,
                      subviews: Subviews,
                      cache: inout ()) -> CGSize {
        // `replacingUnspecifiedDimensions` gives a sane width when the
        // proposal is `.unspecified` (SwiftUI's ideal-size probe).
        let lines = plan(subviews: subviews,
                         maxMain: proposal.replacingUnspecifiedDimensions().width,
                         availCross: proposal.height)
        let height = lines.reduce(0) { $0 + $1.cross }
            + verticalSpacing * CGFloat(max(0, lines.count - 1))
        return CGSize(width: lines.map(\.main).max() ?? 0, height: height)
    }

    /// Place every item, stretching the auto-cross ones to their line.
    func placeSubviews(in bounds: CGRect,
                       proposal: ProposedViewSize,
                       subviews: Subviews,
                       cache: inout ()) {
        // By placement time `bounds` IS the resolved box, so it is the
        // authority for both axes (the pre-wave-25 layout already used
        // bounds.width for the wrap decision).
        let lines = plan(subviews: subviews,
                         maxMain: bounds.width,
                         availCross: bounds.height)
        var y = bounds.minY
        for line in lines {
            var x = bounds.minX
            for item in line.items {
                // Stretching items take the line's whole cross extent;
                // everyone else keeps the size they measured at.
                let cross = item.stretches ? line.cross : item.size.height
                // §8.3 offset of this item inside the line box.
                let dy = CSSFlexMath.crossOffset(itemCross: cross,
                                                 lineCross: line.cross,
                                                 align: item.align)
                subviews[item.index].place(
                    at: CGPoint(x: x, y: y + dy),
                    anchor: .topLeading,
                    // A stretched item is PROPOSED the line cross. That
                    // is enough for an elastic subview (a bare Color) but
                    // NOT for an IR child, which answers with its
                    // intrinsic cross whatever it is offered — those get
                    // the same number injected into their SizeConfig at
                    // build time (flexWrapStretchPlan), so by the time
                    // this proposal is made the child already measures at
                    // the line cross and the two agree. A non-stretched
                    // item gets exactly the proposal the pre-wave-25
                    // layout gave it, byte for byte.
                    proposal: item.stretches
                        ? ProposedViewSize(width: item.size.width, height: cross)
                        : ProposedViewSize(item.size))
                x += item.size.width + horizontalSpacing
            }
            y += line.cross + verticalSpacing
        }
    }
}
