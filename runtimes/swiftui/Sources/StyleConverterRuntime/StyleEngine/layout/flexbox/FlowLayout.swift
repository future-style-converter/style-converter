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
//  WAVE 47 (lane Z7) — per-line justify-content and the non-stretch
//  align-content keywords now PLACE through CSSFlexMath.mainOffsets,
//  the same §8.2 distribution the nowrap path uses (css-align-3 has ONE
//  <content-distribution> grammar for both axes). Measured against the
//  frozen Chromium refs: flex-gap-decorations-040…042 distribute each
//  line's leftover into its item gaps (space-between/around/evenly) and
//  047…049 position the line block (the row rules sit centred in the
//  distributed line gaps). Nil keywords take mainOffsets' packed
//  default, byte-identical to the removed accumulation loops.
//
//  STILL TODO here (named, not silently skipped): wrap-reverse
//  ordering. Column-direction wrap is also unimplemented — this Layout
//  lays out ROWS whatever `flex-direction` says (the pre-wave-25
//  behaviour), which is why the build-time plan refuses column
//  containers instead of injecting a cross size the placement would
//  contradict (that wall is what keeps flex-gap-decorations-043/045
//  failing on iOS).
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

    /// Container `justify-content` (§8.2) — wave 47 (lane Z7): applied
    /// PER LINE through the same CSSFlexMath.mainOffsets the nowrap path
    /// uses (css-align-3 §8.3: content distribution is a per-line
    /// operation). Nil = the initial `normal` → packed start, which is
    /// byte-identical to the pre-wave-47 accumulation loop (measured:
    /// the wave-46 iOS captures for flex-gap-decorations-040…042 packed
    /// every line flush-left where the Chromium ref distributes them).
    var justifyContent: AlignmentKeyword? = nil
    /// True when the IR declared an explicit MAIN (inline) size — wave
    /// 47: a definite-width container's content box IS that width, so
    /// `sizeThatFits` must claim the proposal instead of hugging the
    /// widest line, or the per-line §8.2 distribution above never sees
    /// the free space (measured via the raster pins: a `width: 170`
    /// wrap container reported 150 — its widest line — and SwiftUI
    /// placed the Layout hugged inside the outer frame, so
    /// `bounds.width` at placement was 150 and space-between had
    /// nothing to hand out). False = CSS shrink-to-fit hugging, the
    /// pre-wave-47 report, byte for byte.
    var definiteMain: Bool = false

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
        // Content (hugged) extents — the CSS shrink-to-fit report.
        let hugWidth = lines.map(\.main).max() ?? 0
        let hugHeight = lines.reduce(0) { $0 + $1.cross }
            + verticalSpacing * CGFloat(max(0, lines.count - 1))
        // Wave 47 (lane Z7) — a DEFINITE axis claims the proposal: the
        // CSS content box of a `width: 170px` (or `height: 150px`) flex
        // container is that size whatever its lines hug to, and the
        // §8.2/§9.6 distribution at placement time needs `bounds` to BE
        // that box (see `definiteMain`). max() keeps overflow honest —
        // lines wider/taller than the box still spill like CSS
        // `overflow: visible` (the pre-wave-47 behavior for them).
        // Under stretch the lines already sum to the definite cross, so
        // the height claim is the number the old code returned anyway.
        let width = definiteMain
            ? max(proposal.width.flatMap { $0.isFinite ? $0 : nil } ?? hugWidth, hugWidth)
            : hugWidth
        let height = definiteCross
            ? max(proposal.height.flatMap { $0.isFinite ? $0 : nil } ?? hugHeight, hugHeight)
            : hugHeight
        return CGSize(width: width, height: height)
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
        // Wave 47 (lane Z7) — §9.6 align-content POSITIONING keywords.
        // Line cross positions come from the same distribution math the
        // main axis uses (css-align-3 defines one <content-distribution>
        // grammar for both): sizes = the line crosses, available = the
        // container's definite cross, keyword = align-content. Under the
        // initial normal/stretch the lines were already stretched to
        // consume the leftover (plan → FlexWrapPlan.stretchLines), so
        // free space is zero and these offsets reproduce the pre-wave-47
        // packed accumulation bit for bit; center/end/space-* now place
        // the line BLOCK where css-flexbox-1 §9.6 says instead of
        // packing at cross-start (WPT flex-gap-decorations-047…049: the
        // Chromium ref's row rules sit centred in the DISTRIBUTED line
        // gaps). `definiteCross` gates it exactly like stretch: a
        // hugging container has no leftover to distribute.
        let lineYs = CSSFlexMath.mainOffsets(
            sizes: lines.map(\.cross),
            available: definiteCross ? bounds.height : nil,
            gap: verticalSpacing,
            justify: alignContentStretches ? nil : alignContent)
        for (li, line) in lines.enumerated() {
            let y = bounds.minY + lineYs[li]
            // §8.2 justify-content per line: item main positions from
            // the shared nowrap math. Nil justify → packed start →
            // byte-identical to the old `x += size + gap` walk.
            let xs = CSSFlexMath.mainOffsets(
                sizes: line.items.map(\.size.width),
                available: bounds.width,
                gap: horizontalSpacing,
                justify: justifyContent)
            for (k, item) in line.items.enumerated() {
                let x = bounds.minX + xs[k]
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
            }
        }
    }
}
