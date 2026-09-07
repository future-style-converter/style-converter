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
//  WAVE 48 (lane W7) — TWO ADDITIONS:
//
//  1. THE COLUMN AXIS (`vertical`). The pre-wave-48 layout laid out ROWS
//     whatever `flex-direction` said, so `flex-flow: column wrap` — a
//     wrapped COLUMN flow whose lines stand side by side horizontally —
//     rendered as stacked rows (WPT css-gaps flex-gap-decorations-043:
//     Chromium staggers two space-between columns at x 0/50; the iOS
//     capture stacked full-width rows; -045 likewise, plus its stretched
//     lines). With `vertical: true` every step below transposes: items
//     flow top→bottom, §9.3 breaks against the HEIGHT budget, the line
//     cross axis is INLINE, and lines line up left→right. All arithmetic
//     stays in the same FlexWrapPlan / CSSFlexMath helpers — only the
//     coordinate each number lands on changes, so `vertical: false`
//     (every pre-wave-48 call site) is byte-identical to the old code.
//
//  2. STATIC MAIN-SIZE BASIS (`flex-basis` px/percent). The layout used
//     to size every item purely from measurement, so a percent basis —
//     serialized as a bare number, see FlexboxExtractor.extractFlexBasis
//     — was dropped and flex-gap-decorations-025's `flex-basis: 100%`
//     items collapsed to intrinsic 0 width (blank iOS capture). The item
//     main size now honors the ItemPlacement basis claim, but ONLY for
//     inflexible items (`flex-grow: 0`): this layout runs no §9.7
//     resolution (its named gap since wave 25), so adopting a basis that
//     grow would rewrite (e.g. `flex: 1 1 0%`, css-values
//     calc-size-flex-007) would paint geometry no browser shows — those
//     keep the measured fallback. The adopted basis is clamped to the
//     line budget, the same shrink approximation Compose's FlexWrapRow
//     documents as its KNOWN GAP.
//
//  STILL TODO here (named, not silently skipped): wrap-reverse
//  ordering — the renderer keeps `wrap-reverse` containers on the
//  row-flow behaviour they render with today (vertical is gated to
//  `flex-wrap: wrap`).
//

import SwiftUI

/// Wrapping flex container (`flex-wrap: wrap`).
/// Row mode (default): lines are laid left→right and broken when the
/// next item would exceed the proposed width; lines stack top→bottom.
/// Column mode (`vertical: true`): items are laid top→bottom and broken
/// against the proposed height; lines stand side by side left→right.
@available(iOS 16.0, *)
struct FlowLayout: Layout {
    /// Horizontal gap in points: between items on a line in row mode,
    /// between LINES in column mode (`column-gap` either way).
    var horizontalSpacing: CGFloat = 0
    /// Vertical gap in points: between lines in row mode, between items
    /// in a column in column mode (`row-gap` either way).
    var verticalSpacing: CGFloat = 0
    /// Container `align-items` (§8.3 cross-axis default). Nil = the CSS
    /// initial `normal`, which behaves as `stretch` in a flex container.
    var alignItems: AlignmentKeyword? = nil
    /// True when the IR declared an explicit CROSS size (block size in
    /// row mode, inline size in column mode). Only then is there leftover
    /// cross space for §9.6 to hand to the lines; otherwise the container
    /// hugs its lines and stretch is a no-op.
    var definiteCross: Bool = false
    /// Container `align-content`. Nil = the CSS initial `normal`, which
    /// behaves as `stretch` for a flex container (css-align-3 §5.1).
    ///
    /// SKEPTIC (wave 25) — §9.6's equal division fires ONLY under
    /// normal/stretch. Every other keyword leaves the leftover as free
    /// space and merely POSITIONS the line block, so stretching under
    /// `center` / `space-between` moves the lines to coordinates the
    /// browser never produces. The Compose lane's FlexWrapLines gates
    /// its identical step-8 on exactly this predicate
    /// (`alignContentStretches`).
    var alignContent: AlignmentKeyword? = nil

    /// Container `justify-content` (§8.2) — wave 47 (lane Z7): applied
    /// PER LINE through the same CSSFlexMath.mainOffsets the nowrap path
    /// uses (css-align-3 §8: content distribution is a per-line
    /// operation). Nil = the initial `normal` → packed start.
    var justifyContent: AlignmentKeyword? = nil
    /// True when the IR declared an explicit MAIN size (inline in row
    /// mode, block in column mode) — wave 47: a definite-size container's
    /// content box IS that size, so `sizeThatFits` must claim the
    /// proposal instead of hugging, or the per-line §8.2 distribution
    /// never sees the free space (measured via the raster pins: a
    /// `width: 170` wrap container reported 150 — its widest line — and
    /// space-between had nothing to hand out).
    var definiteMain: Bool = false
    /// Wave 48 (lane W7) — column-direction wrap (`flex-flow: column
    /// wrap`). False is the pre-wave-48 row flow, byte for byte.
    var vertical: Bool = false

    /// css-align-3 §5.1 — does `align-content` distribute leftover cross
    /// space to the LINES (rather than merely position them)? Delegated
    /// to FlexWrapPlan so the Layout and the renderer's build-time plan
    /// can never answer it differently.
    private var alignContentStretches: Bool {
        FlexWrapPlan.alignContentStretches(alignContent)
    }

    /// The gap between items along the MAIN axis (`column-gap` for rows,
    /// `row-gap` for columns — css-align-3 §8's axis mapping).
    private var mainSpacing: CGFloat { vertical ? verticalSpacing : horizontalSpacing }
    /// The gap between LINES along the cross axis (the other one).
    private var crossSpacing: CGFloat { vertical ? horizontalSpacing : verticalSpacing }

    /// One measured item inside a line.
    private struct Item {
        /// Index into `subviews`.
        var index: Int
        /// Main-axis extent: the §9.2.3 basis claim when adopted (wave
        /// 48), else the measured intrinsic size on the main axis.
        var main: CGFloat
        /// Cross-axis extent as measured.
        var cross: CGFloat
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
    /// `maxMain` is the main-axis budget to wrap against; `availCross`
    /// is the container's definite cross size, or nil when it hugs.
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
            // The cross axis is block in row mode (Height/BlockSize),
            // inline in column mode (Width/InlineSize) — the placement
            // carries both facts precisely so the container can pick.
            let crossAuto = placement.map {
                vertical ? !$0.explicitWidth : !$0.explicitHeight
            } ?? true
            // Wave 48 — §9.2.3.A/B used flex basis for INFLEXIBLE items:
            // a px basis directly, a percent basis against the line
            // budget when it is definite (css-flexbox-1 §7.2.3; an
            // infinite budget is indefinite → percentage behaves as
            // auto/content, i.e. the measured size). grow > 0 keeps the
            // measured fallback — no §9.7 runs here (header, item 2) —
            // and the clamp to `maxMain` is the Compose KNOWN-GAP shrink
            // approximation.
            let basis: CGFloat? = {
                guard let flex = placement?.flex, flex.grow == 0 else { return nil }
                if let px = flex.basisPx { return min(px, maxMain) }
                if let pct = flex.basisPercent, maxMain.isFinite {
                    return min(maxMain * pct / 100, maxMain)
                }
                return nil
            }()
            let measuredMain = vertical ? size.height : size.width
            items.append(Item(index: i,
                              main: basis ?? measuredMain,
                              cross: vertical ? size.width : size.height,
                              align: align,
                              stretches: align == .stretch && crossAuto))
        }
        // §9.3 line collection — the SHARED rule (FlexWrapPlan), so the
        // renderer's build-time plan breaks at exactly these indices and
        // the injected cross size can never belong to a different line.
        let ranges = FlexWrapPlan.breakLines(mainSizes: items.map(\.main),
                                             containerMain: maxMain,
                                             gap: mainSpacing)
        var lines: [Line] = ranges.map { r in
            var line = Line()
            line.items = (r.first...r.last).map { items[$0] }
            // Main extent = summed item mains + the inter-item gaps.
            line.main = line.items.reduce(0) { $0 + $1.main }
                + mainSpacing * CGFloat(r.count - 1)
            // §9.4 step 7 — hypothetical line cross = the largest item.
            line.cross = line.items.map(\.cross).max() ?? 0
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
            gap: crossSpacing)
        for i in lines.indices { lines[i].cross = stretched[i] }
        return lines
    }

    /// Wrapped size: content extents per axis, definite axes claiming
    /// their proposal (wave 47) — see `definiteMain`/`definiteCross`.
    func sizeThatFits(proposal: ProposedViewSize,
                      subviews: Subviews,
                      cache: inout ()) -> CGSize {
        // `replacingUnspecifiedDimensions` gives a sane budget when the
        // proposal is `.unspecified` (SwiftUI's ideal-size probe).
        let resolved = proposal.replacingUnspecifiedDimensions()
        let lines = plan(subviews: subviews,
                         maxMain: vertical ? resolved.height : resolved.width,
                         availCross: vertical ? proposal.width : proposal.height)
        // Content (hugged) extents — the CSS shrink-to-fit report.
        let hugMain = lines.map(\.main).max() ?? 0
        let hugCross = lines.reduce(0) { $0 + $1.cross }
            + crossSpacing * CGFloat(max(0, lines.count - 1))
        // Wave 47 (lane Z7) — a DEFINITE axis claims the proposal: the
        // CSS content box of a definite-size flex container is that size
        // whatever its lines hug to, and the §8.2/§9.6 distribution at
        // placement time needs `bounds` to BE that box. max() keeps
        // overflow honest — lines beyond the box still spill like CSS
        // `overflow: visible`. Under stretch the lines already sum to
        // the definite cross, so the cross claim is the hug anyway.
        let proposedMain = (vertical ? proposal.height : proposal.width)
            .flatMap { $0.isFinite ? $0 : nil }
        let proposedCross = (vertical ? proposal.width : proposal.height)
            .flatMap { $0.isFinite ? $0 : nil }
        let main = definiteMain ? max(proposedMain ?? hugMain, hugMain) : hugMain
        let cross = definiteCross ? max(proposedCross ?? hugCross, hugCross) : hugCross
        // Land each axis extent back on its coordinate.
        return vertical ? CGSize(width: cross, height: main)
                        : CGSize(width: main, height: cross)
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
                         maxMain: vertical ? bounds.height : bounds.width,
                         availCross: vertical ? bounds.width : bounds.height)
        // Wave 47 (lane Z7) — §9.6 align-content POSITIONING keywords:
        // line cross positions from the same distribution math the main
        // axis uses (css-align-3 defines one <content-distribution>
        // grammar for both). Under the initial normal/stretch the lines
        // were already stretched to consume the leftover, so free space
        // is zero and these offsets reproduce the packed accumulation
        // bit for bit; center/end/space-* place the line BLOCK where
        // css-flexbox-1 §9.6 says. `definiteCross` gates it exactly like
        // stretch: a hugging container has no leftover to distribute.
        let lineCrossStarts = CSSFlexMath.mainOffsets(
            sizes: lines.map(\.cross),
            available: definiteCross ? (vertical ? bounds.width : bounds.height) : nil,
            gap: crossSpacing,
            justify: alignContentStretches ? nil : alignContent)
        for (li, line) in lines.enumerated() {
            let lineCross = lineCrossStarts[li]
            // §8.2 justify-content per line: item main positions from
            // the shared nowrap math. Nil justify → packed start →
            // byte-identical to the old accumulation walk.
            let mains = CSSFlexMath.mainOffsets(
                sizes: line.items.map(\.main),
                available: vertical ? bounds.height : bounds.width,
                gap: mainSpacing,
                justify: justifyContent)
            for (k, item) in line.items.enumerated() {
                // Stretching items take the line's whole cross extent;
                // everyone else keeps the size they measured at.
                let cross = item.stretches ? line.cross : item.cross
                // §8.3 offset of this item inside the line box.
                let dCross = CSSFlexMath.crossOffset(itemCross: cross,
                                                     lineCross: line.cross,
                                                     align: item.align)
                // Transpose the (main, cross) pair back onto (x, y).
                let x = vertical ? bounds.minX + lineCross + dCross
                                 : bounds.minX + mains[k]
                let y = vertical ? bounds.minY + mains[k]
                                 : bounds.minY + lineCross + dCross
                subviews[item.index].place(
                    at: CGPoint(x: x, y: y),
                    anchor: .topLeading,
                    // A stretched item is PROPOSED the line cross. That
                    // is enough for an elastic subview (a bare Color) but
                    // NOT for an IR child, which answers with its
                    // intrinsic cross whatever it is offered — those get
                    // the same number injected into their SizeConfig at
                    // build time (flexWrapStretchPlan), so by the time
                    // this proposal is made the child already measures at
                    // the line cross and the two agree. A non-stretched
                    // item is proposed its own (basis-adopted) extents —
                    // for a measured item that is exactly the pre-wave-48
                    // proposal, byte for byte.
                    proposal: ProposedViewSize(
                        width: vertical ? cross : item.main,
                        height: vertical ? item.main : cross))
            }
        }
    }
}
