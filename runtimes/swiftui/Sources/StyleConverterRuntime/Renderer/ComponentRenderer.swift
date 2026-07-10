//
//  ComponentRenderer.swift
//  StyleConverterTest
//
//  Recursive SwiftUI renderer: IRComponent → View.
//
//  Picks the right layout container based on `Display` / `FlexDirection`, then
//  applies the rest of the style via StyleBuilder + .applyStyle(). Children
//  render recursively; leaves fall back to a placeholder label showing the
//  component's name.
//

import SwiftUI

// public: this is the package's primary product surface — the harness
// (CaptureCanvas, ComponentGallery) and any SDUI consumer instantiate it
// to turn an IRComponent into a SwiftUI view.
public struct ComponentRenderer: View {
    let component: IRComponent

    // Fidelity wave 1 — grid stretch injection. css-align-3 §9: a grid
    // item whose block size is `auto` and whose align-self resolves to
    // stretch fills its row track. The parent grid computes the row
    // height statically (explicit grid-template-rows) and passes it via
    // this environment slot; we fold it into SizeConfig BEFORE the style
    // chain so the child's own background/border paint at the stretched
    // height (an outer .frame can't reach the child's paint chain).
    @Environment(\.gridStretchHeight) private var gridStretchHeight

    // Fidelity wave 2 — column-flex analogue of gridStretchHeight:
    // css-flexbox-1 §8.3 stretch on the INLINE axis. A column-flex
    // parent with a definite width publishes its content width here for
    // children whose align resolves to stretch and whose width is auto;
    // folded into SizeConfig below so the child's own paint chain
    // (background/borders) covers the stretched width.
    @Environment(\.flexStretchWidth) private var flexStretchWidth

    // Fidelity wave 1 — CSS text inheritance channel. The converter
    // flattens no cascade, so a parent's font-size/family/weight/
    // letter-spacing/text-align never reached child renders on iOS while
    // the web reference inherits them natively (inheritance-typography
    // IT_* trees). Parents publish their inheritable declarations here;
    // we merge them UNDER the component's own (own always wins) before
    // building the style, then re-publish the merged set for
    // grandchildren — reproducing the transitive chain. Mirrors the
    // Android LocalInheritedProperties channel 1:1.
    @Environment(\.inheritedTextProperties) private var inheritedTextProperties

    /// The component's declarations with the parent's inheritable text
    /// properties merged underneath (css-cascade-4 inheritance).
    private var mergedProperties: [IRProperty] {
        InheritedText.merge(own: component.properties,
                            inherited: inheritedTextProperties)
    }

    // public: explicit memberwise init — the synthesized one is internal,
    // so cross-module callers need this spelled out.
    public init(component: IRComponent) {
        self.component = component
    }

    // public: View protocol witness on a public type must be public.
    public var body: some View {
        // Build the style from the inheritance-merged declarations, then
        // fold in the grid-stretch height (if the parent injected one and
        // the IR declared no explicit height — an explicit height always
        // wins per css-align-3 §9's "auto block size" precondition).
        let style: ComponentStyle = {
            var s = StyleBuilder.build(from: mergedProperties)
            if let h = gridStretchHeight, s.size.height == nil {
                s.size.height = .exact(px: h)
            }
            // Column-flex stretch (fidelity wave 2): same fold as the
            // grid/row channel but on the inline axis. An explicit CSS
            // width always wins (css-align-3 §9 auto-size precondition).
            if let w = flexStretchWidth, s.size.width == nil {
                s.size.width = .exact(px: w)
            }
            return s
        }()

        if style.layout.display == .none {
            EmptyView()
        } else {
            // Phase 7 step 4: apply per-child positioning after container
            // selection so absolute/relative offsets stack on top of the
            // fully-styled element. Identity when position/zindex unset.
            PositionApplier.apply(
                AnyView(layoutContainer(style: style).applyStyle(style)),
                aggregate: style.layout7
            )
        }
    }

    // MARK: - Container selection

    @ViewBuilder
    private func layoutContainer(style: ComponentStyle) -> some View {
        // Phase 2: resolve gap via the new GapApplier. Row gap for vertical
        // stacks, column gap for horizontal. Zero when no gap/row/col-gap
        // is set on the IR.
        let gap = GapApplier.resolve(style.spacing.gap, context: style.spacing.context)
        // Phase 7 step 4: detect absolute/fixed positioned children —
        // their parent must be a ZStack(alignment: .topLeading) so
        // offsets resolve against the upper-left corner.
        let needsZStack: Bool = {
            guard let kids = component.children else { return false }
            let aggs: [LayoutAggregate?] = kids.map { LayoutExtractor.extract(from: $0.properties) }
            return PositionApplier.needsZStackWrap(forChildren: aggs)
        }()
        // Phase 7 step 3: grid container selection. Runs ahead of the
        // flex-wrap branch so grid containers with wrap hints still
        // route to the LazyVGrid / Grid path.
        //
        // BUT: skip the grid path entirely when the component has no
        // children. SwiftUI's `LazyVGrid(columns: [.adaptive(...)])` is
        // greedy — with zero items it still expands to fill its parent's
        // available width (358 px in CaptureCanvas). Android's
        // LazyVerticalGrid + `wrapContentSize()` hugs content tight on
        // empty input, and web (post fit-content fix) does the same. The
        // visual-test fixtures Grid_Simple / Grid_ThreeCol /
        // Grid_FixedTracks have `display: grid` set but no children, so
        // they rendered ~358 px on iOS vs ~80–200 px on Android/web,
        // dragging the cross-platform SSIM down to 0.35–0.49.
        //
        // For an empty grid, "grid layout" has nothing to express anyway —
        // there are no items to place into tracks. Routing through the
        // block path (VStack(alignment: .leading)) makes iOS hug the
        // PlaceholderLabel just like the other platforms.
        let hasChildren = !(component.children?.isEmpty ?? true)
        let gridKind: ContainerDecision.ContainerKind? = {
            guard hasChildren, let agg = style.layout7 else { return nil }
            // Explicit templates / auto-flow pick their container kind;
            // a bare `display: grid` with children (no template at all)
            // still IS a grid per css-grid-1 — a single auto column —
            // so route it through the CSS grid layout too instead of
            // falling to the legacy 2-col adaptive LazyVGrid.
            if let k = GridApplier.containerKind(for: agg) { return k }
            return agg.display == .grid ? .lazyVGrid : nil
        }()
        // Phase 7 step 2: route flex containers through FlexboxApplier's
        // FlowLayout when `flex-wrap: wrap|wrap-reverse` is set.
        if let kind = gridKind {
            // Grid path — LazyVGrid / LazyHGrid / iOS 16 Grid.
            gridContainer(kind: kind, style: style, gap: gap)
        } else if needsZStack {
            // Any non-grid parent with absolute/fixed children wraps in a
            // top-leading ZStack so PositionApplier's .offset calls anchor
            // to the correct corner.
            ZStack(alignment: .topLeading) {
                contentOrPlaceholder(style: style)
            }
        } else if let layoutAgg = style.layout7,
           layoutAgg.display == .flex,
           layoutAgg.flexWrap == .wrap || layoutAgg.flexWrap == .wrapReverse {
            FlowLayout(
                horizontalSpacing: gap.column,
                verticalSpacing: gap.row
            ) {
                contentOrPlaceholder(style: style)
            }
        } else {
        switch style.layout.display {
        case .flexRow:
            // Fidelity wave 2 — real CSS flexbox via CSSFlexLayout. The
            // legacy HStack ignored flex-grow/shrink/basis (children
            // stayed at intrinsic size, FR_GrowBasis/FR_ShrinkBasis),
            // couldn't distribute justify-content free space, and never
            // stretched an auto-height child (FR_AlignSelf `d`). The
            // custom Layout implements css-flexbox-1 §8–§9 directly;
            // per-child parameters ride the FlexItemSpec layout value
            // attached in contentOrPlaceholder.
            //
            // BUT only when there ARE children (css-flexbox-1 §4: flex
            // items are the container's in-flow children — an empty
            // container has zero items, so align-items/justify-content
            // have nothing to act on and its own box must not change).
            // The harness's PlaceholderLabel is NOT CSS content: the web
            // reference (apps/web-harness ComponentRenderer.tsx) rewrites
            // display→block for childless flex/grid containers so the
            // label lays out in normal block flow. Routing it through
            // CSSFlexLayout made `align-items: center` vertically centre
            // the label inside the declared height — the 055
            // Flex_AlignCenter regression (baseline/web keep it at the
            // top). Same guard as the empty-grid case below.
            if hasChildren {
                CSSFlexLayout(
                    axis: .horizontal,
                    reverse: style.layout7?.flexDirection == .rowReverse,
                    justify: style.layout7?.justifyContent,
                    alignItems: style.layout7?.alignItems,
                    gap: gap.column,
                    // Only an explicit CSS main/cross size lets the layout
                    // flex against the proposal; otherwise it hugs content
                    // exactly like the web harness's fit-content box.
                    definiteMain: style.size.width != nil,
                    definiteCross: style.size.height != nil
                ) {
                    contentOrPlaceholder(style: style)
                }
            } else {
                // Childless flex → block path, exactly like the empty
                // grid: the placeholder hugs top-leading and the outer
                // style chain still paints the declared box (height /
                // padding / background), matching web's display→block.
                VStack(alignment: .leading, spacing: gap.row) {
                    contentOrPlaceholder(style: style)
                }
            }
        case .flexColumn:
            // Column axis: main = vertical. Same engine as .flexRow —
            // this is what makes `justify-content: space-between`
            // finally act in column direction (FC_SpaceBetween).
            // Same childless guard as .flexRow above: zero flex items →
            // block path so the placeholder is never aligned/justified.
            if hasChildren {
                CSSFlexLayout(
                    axis: .vertical,
                    reverse: style.layout7?.flexDirection == .columnReverse,
                    justify: style.layout7?.justifyContent,
                    alignItems: style.layout7?.alignItems,
                    gap: gap.row,
                    definiteMain: style.size.height != nil,
                    definiteCross: style.size.width != nil
                ) {
                    contentOrPlaceholder(style: style)
                }
            } else {
                // Web parity (display→block rewrite for empty containers);
                // see the .flexRow comment for the full rationale.
                VStack(alignment: .leading, spacing: gap.row) {
                    contentOrPlaceholder(style: style)
                }
            }
        case .grid:
            // Grid subset: render as an adaptive LazyVGrid with 2 columns
            // when there ARE children. Empty grids fall through to the
            // block-style VStack path: `LazyVGrid(columns: [.adaptive(...)])`
            // is greedy and expands to fill the parent's available width
            // even with zero items, which mismatches Android (wrap content)
            // and web (fit-content) for the empty Grid_Simple /
            // Grid_ThreeCol / Grid_FixedTracks fixtures. See the gridKind
            // computation above for the matching guard.
            if hasChildren {
                LazyVGrid(
                    columns: [GridItem(.adaptive(minimum: 80), spacing: gap.column)],
                    spacing: gap.row
                ) {
                    contentOrPlaceholder(style: style)
                }
            } else {
                VStack(alignment: .leading, spacing: gap.row) {
                    contentOrPlaceholder(style: style)
                }
            }
        case .inline:
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                contentOrPlaceholder(style: style)
            }
        case .none:
            EmptyView()
        case .block:
            VStack(
                alignment: .leading,
                spacing: gap.row
            ) {
                contentOrPlaceholder(style: style)
            }
        }
        }
    }

    // MARK: - Grid container (Phase 7 step 3)

    /// Render a grid container — routes to LazyVGrid / LazyHGrid for plain
    /// track-list grids or to iOS 16's Grid/GridRow for template-areas
    /// grids. Spanning within LazyVGrid isn't supported by SwiftUI, so
    /// template-areas components go through the Grid path.
    @ViewBuilder
    private func gridContainer(
        kind: ContainerDecision.ContainerKind,
        style: ComponentStyle,
        gap: (row: CGFloat, column: CGFloat)
    ) -> some View {
        let agg = style.layout7  // safe — gridKind only fires when non-nil
        switch kind {
        case .lazyVGrid:
            // Fidelity wave 1: real CSS-grid subset via CSSGridLayout.
            // LazyVGrid centred items, ignored placement/spans, ignored
            // grid-template-rows, and expanded greedily to the canvas
            // width — all four flagged in the grid-2col/nested-3level
            // wave. The plan (placement + alignment per subview) is
            // computed once and shared with the stretch-height env
            // injection in contentOrPlaceholder.
            let plan = gridPlan(style: style)
            CSSGridLayout(
                // Column tracks from the template; a template-less grid
                // is a single auto column per css-grid-1 §7.1.
                tracks: agg?.gridTemplateColumns?.tracks.map(\.kind) ?? [.automatic],
                // Row template feeds fixed row heights (60px tracks in
                // grid-2col/010_G2_AlignSelf).
                rowTemplate: agg?.gridTemplateRows?.tracks.map(\.kind),
                // Implicit rows take the first grid-auto-rows size.
                autoRows: agg?.gridAutoRows?.tracks.first?.kind,
                requests: plan?.requests ?? [],
                justify: plan?.justify ?? [],
                align: plan?.align ?? [],
                rowGap: gap.row,
                columnGap: gap.column,
                // Explicit CSS width ⇒ fr/% tracks split the proposal;
                // otherwise fit-content hug (web harness parity).
                definiteWidth: style.size.width != nil
            ) {
                contentOrPlaceholder(style: style)
            }
        case .lazyHGrid:
            // Column auto-flow — rows drive the LazyHGrid layout.
            let items = GridApplier.gridItems(
                for: agg?.gridTemplateRows,
                columnGap: gap.row
            )
            LazyHGrid(rows: items, spacing: gap.column) {
                contentOrPlaceholder(style: style)
            }
        case .grid:
            // iOS 16+ Grid for template-areas grids. We emit one GridRow
            // per template-areas row; each cell renders the first child
            // whose grid-area name matches the cell. Unnamed cells ("."),
            // or areas with no matching child, render as empty space.
            // TODO: this is a pragmatic mapping — it doesn't yet handle
            // spanned cells across adjacent rows (would need gridCellMerge).
            templateAreasGrid(style: style, gap: gap)
        default:
            // Fallback — vertical stack. Keeps the switch exhaustive.
            VStack(alignment: .leading, spacing: gap.row) {
                contentOrPlaceholder(style: style)
            }
        }
    }

    /// Render a template-areas grid using iOS 16 Grid / GridRow. Each
    /// IRComponent child with `grid-area: <name>` lands in every cell
    /// whose area matches that name. Without a match the cell is a
    /// transparent spacer so the track layout still resolves.
    @ViewBuilder
    private func templateAreasGrid(
        style: ComponentStyle,
        gap: (row: CGFloat, column: CGFloat)
    ) -> some View {
        let areas = style.layout7?.gridTemplateAreas ?? []
        let children = component.children ?? []
        // Precompute a (name → child) lookup so cell rendering is O(1).
        // A child without an explicit grid-area falls back to its name
        // field — matches the "children named after the area" pattern
        // in grid-template-areas.json fixtures.
        let byArea: [String: IRComponent] = Dictionary(
            uniqueKeysWithValues: children.map { c -> (String, IRComponent) in
                // Extract grid-area name from the child's IRProperty list.
                for p in c.properties where p.type == "GridArea" {
                    if let line = GridExtractor.parseGridLine(p.data),
                       let n = line.name {
                        return (n, c)
                    }
                }
                // Fall back on the component's own name — test fixtures
                // use matching names for areas + children.
                return (c.name, c)
            }
        )
        // Build the grid. `Grid` is iOS 16+ (matches deployment target).
        Grid(horizontalSpacing: gap.column, verticalSpacing: gap.row) {
            ForEach(Array(areas.enumerated()), id: \.offset) { _, row in
                GridRow {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, cellName in
                        if cellName == "." {
                            // Empty cell — transparent placeholder keeps
                            // track widths consistent.
                            Color.clear
                        } else if let child = byArea[cellName] {
                            ComponentRenderer(component: child)
                                // Text inheritance flows into area-placed
                                // children the same as flow children.
                                .environment(\.inheritedTextProperties,
                                             InheritedText.inheritable(from: mergedProperties))
                        } else {
                            // Named but no matching child — still reserve
                            // the cell so the grid stays rectangular.
                            Color.clear
                        }
                    }
                }
            }
        }
    }

    // MARK: - Grid plan (fidelity wave 1)

    /// Everything CSSGridLayout needs about this container's items, in
    /// SUBVIEW order (leading `_text` placeholder first when present,
    /// then the `order`-sorted children). Also carries the stretch-
    /// height map keyed by SORTED-CHILD index for the environment
    /// injection in contentOrPlaceholder.
    struct GridPlan {
        /// Placement request per subview.
        var requests: [GridItemRequest]
        /// Inline-axis alignment per subview.
        var justify: [GridItemAlign]
        /// Block-axis alignment per subview.
        var align: [GridItemAlign]
        /// Sorted-child index → fixed row height to stretch to.
        var stretchHeights: [Int: CGFloat]
    }

    /// Build the grid plan for a `display: grid` container. Returns nil
    /// for non-grid parents so contentOrPlaceholder can skip the env
    /// injection entirely.
    private func gridPlan(style: ComponentStyle) -> GridPlan? {
        // Only grid containers with children get a plan. Mirrors the
        // gridKind decision in layoutContainer: an explicit template
        // routes to the grid path even without `display: grid` (legacy
        // behaviour of GridApplier.containerKind).
        guard let parentAgg = style.layout7,
              parentAgg.display == .grid
                || GridApplier.containerKind(for: parentAgg) == .lazyVGrid,
              let rawChildren = component.children, !rawChildren.isEmpty else { return nil }
        // Same ordering contentOrPlaceholder renders with (CSS `order`).
        let children = FlexboxApplier.sorted(rawChildren)
        // A parent that carries _text AND children renders the text as a
        // leading placeholder — CSS wraps loose grid text in an
        // anonymous grid ITEM, so it participates in placement.
        let hasLeadingText = (component._text?.isEmpty == false)
        var requests: [GridItemRequest] = []
        var justify: [GridItemAlign] = []
        var align: [GridItemAlign] = []
        // Track per-child "block size may stretch" flags for the env map.
        var stretchFlags: [Bool] = []
        if hasLeadingText {
            // The anonymous text item auto-places at the first free cell
            // and start-aligns (it has no self-alignment properties).
            requests.append(GridItemRequest())
            justify.append(.start)
            align.append(.start)
        }
        for child in children {
            // Full layout aggregate — placement longhands + self-align.
            let a = LayoutExtractor.extract(from: child.properties)
            var rq = GridItemRequest()
            // Placement lines are 1-based; spans ride on either longhand
            // (`grid-column-start: span 2` / `grid-column-end: span 2`).
            rq.colStart = a?.gridColumnStart?.line
            rq.colEnd   = a?.gridColumnEnd?.line
            rq.rowStart = a?.gridRowStart?.line
            rq.rowEnd   = a?.gridRowEnd?.line
            rq.colSpan  = a?.gridColumnStart?.span ?? a?.gridColumnEnd?.span
            rq.rowSpan  = a?.gridRowStart?.span ?? a?.gridRowEnd?.span
            requests.append(rq)
            // Self-alignment resolves against the container's *-items
            // defaults (css-align-3 §6).
            justify.append(GridPlacer.resolveAlign(self: a?.justifySelf,
                                                   items: parentAgg.justifyItems))
            align.append(GridPlacer.resolveAlign(self: a?.alignSelf,
                                                 items: parentAgg.alignItems))
            // Stretch candidate: effective block-axis keyword is
            // stretch/normal/auto (the grid default) AND the child has
            // no explicit block size in the IR.
            let effAlign: AlignmentKeyword? = {
                if let s = a?.alignSelf, s != .auto { return s }
                return parentAgg.alignItems
            }()
            let stretchy = effAlign == nil || effAlign == .stretch || effAlign == .normal
            let hasHeight = child.properties.contains {
                $0.type == "Height" || $0.type == "BlockSize"
            }
            stretchFlags.append(stretchy && !hasHeight)
        }
        // Resolve stretch heights: the item's assigned row must be a
        // FIXED template track (only then is the row height knowable
        // before measurement — auto rows are content-sized and stretch
        // to content is an identity).
        var stretchHeights: [Int: CGFloat] = [:]
        if let rowTracks = parentAgg.gridTemplateRows?.tracks {
            let cols = parentAgg.gridTemplateColumns?.tracks.count ?? 1
            let (cells, _) = GridPlacer.assign(requests, columnCount: cols)
            let offset = hasLeadingText ? 1 : 0
            for i in 0..<children.count where stretchFlags[i] {
                let cell = cells[i + offset]
                // Only single-row items stretch to one track cleanly.
                guard cell.rowSpan == 1, cell.row < rowTracks.count else { continue }
                if case .fixed(let px) = rowTracks[cell.row].kind {
                    stretchHeights[i] = px
                }
            }
        }
        return GridPlan(requests: requests, justify: justify,
                        align: align, stretchHeights: stretchHeights)
    }

    // MARK: - Flex stretch geometry (fidelity wave 2)

    /// Definite CONTENT size of a flex container on one axis: declared
    /// border-box size minus the padding band and painted border widths
    /// (CSS 2.1 §8.1 box model; the harness renders border-box). Nil when
    /// the size is not declared/resolvable — indefinite containers
    /// can't statically size children (depends on measurement; deferred).
    private func flexContentSize(style: ComponentStyle, vertical: Bool) -> CGFloat? {
        let ctx = style.spacing.context
        // Declared size on the requested axis (percent heights degrade
        // to auto, same rule as SizeApplier).
        let raw: CGFloat? = vertical
            ? SizeApplierResolve.exact(style.size.height, ctx: ctx,
                                       parent: CGFloat(ctx.viewportHeight),
                                       allowPercent: false)
            : SizeApplierResolve.exact(style.size.width, ctx: ctx,
                                       parent: CGFloat(ctx.viewportWidth) - 32)
        guard var v = raw else { return nil }
        // Padding band — same resolver lane as PaddingApplier so em/%
        // agree with the painted inset. Horizontal axis subtracts the
        // left/right band, vertical the top/bottom one.
        if let p = style.spacing.padding {
            func px(_ lv: LengthValue) -> CGFloat {
                switch SpacingResolver.resolve(lv, ctx: ctx, isPadding: true) {
                case .px(let n):      return n
                case .percent(let f): return f * CGFloat(ctx.viewportWidth)
                case .auto, .skip:    return 0
                }
            }
            v -= vertical ? (px(p.top) + px(p.bottom)) : (px(p.left) + px(p.right))
        }
        // Painted border widths shrink the content box too.
        if let b = style.borderSides {
            func bw(_ s: BorderSideConfig) -> CGFloat {
                s.hasBorder ? (s.effectiveWidth ?? 0) : 0
            }
            v -= vertical ? (bw(b.top) + bw(b.bottom)) : (bw(b.start) + bw(b.end))
        }
        return v > 0 ? v : nil
    }

    /// Static main-axis flex plan (fidelity wave 2). CSSFlexLayout places
    /// children at their §9.7-resolved main sizes, but a flexed child's
    /// own paint chain (background/border) still hugs its intrinsic size
    /// — the resolved size has to be folded into the child's SizeConfig
    /// the same way grid/flex stretch is (env injection). This pre-pass
    /// re-runs the pure CSSFlexMath with statically-knowable inputs and
    /// returns one main size per sorted child, or nil when any input is
    /// dynamic (auto basis with no explicit main size, indefinite
    /// container, anonymous leading text) — those fall back to the
    /// Layout-only path where positions are right but paint hugs.
    private func flexMainPlan(style: ComponentStyle,
                              children: [IRComponent],
                              column: Bool) -> [CGFloat]? {
        // Definite main-axis content size or bail.
        guard let available = flexContentSize(style: style, vertical: column)
        else { return nil }
        // A leading `_text` placeholder participates in the Layout as an
        // extra item — index mapping would shift; no wave fixture mixes
        // text with flexed children, so bail honestly.
        guard component._text?.isEmpty != false else { return nil }
        let ctx = style.spacing.context
        // Main-axis gap through the same resolver as the container.
        let g = GapApplier.resolve(style.spacing.gap, context: ctx)
        let gap = column ? g.row : g.column
        var items: [CSSFlexMath.ItemInput] = []
        for child in children {
            // Flex factors from the child's aggregate.
            var a = LayoutAggregate()
            FlexboxExtractor.extract(from: child.properties, into: &a)
            let basisPx: CGFloat? = {
                if case .px(let p)? = a.flexBasis { return p }
                return nil
            }()
            // Explicit main size fallback (css-flexbox-1 §9.2.3.A —
            // basis auto defers to the main-size property).
            let cs = SizeExtractor.extract(from: child.properties)
            let explicit: CGFloat? = column
                ? SizeApplierResolve.exact(cs.height, ctx: ctx,
                                           parent: CGFloat(ctx.viewportHeight),
                                           allowPercent: false)
                : SizeApplierResolve.exact(cs.width, ctx: ctx,
                                           parent: CGFloat(ctx.viewportWidth) - 32)
            // Content-derived basis (text measurement) is not statically
            // knowable — bail to the dynamic path.
            guard let basis = basisPx ?? explicit else { return nil }
            // The web harness's 50×30 min floor applies per axis when
            // the child declares nothing there (StyleBuilder.minFloor)
            // — it clamps flexed sizes in the browser too (a browser
            // min-width beats flex shrink/grow, §9.7 min violation).
            let floor = StyleBuilder.minFloor(for: cs)
            let minMain = (column ? floor.height : floor.width) ?? 0
            items.append(.init(basis: basis, min: minMain,
                               grow: CGFloat(a.flexGrow ?? 0),
                               shrink: CGFloat(a.flexShrink ?? 1)))
        }
        return CSSFlexMath.mainSizes(items: items, available: available, gap: gap)
    }

    // MARK: - Content

    @ViewBuilder
    private func contentOrPlaceholder(style: ComponentStyle) -> some View {
        if let rawChildren = component.children, !rawChildren.isEmpty {
            // Bug 1 mixed-content fix — see
            // testing/titan/investigations/swarm-002/css-text-decor__text-decoration-decorating-box-thickness-001.json
            //
            // When the parent carries `_text` AND children, render the text
            // as a leading sibling so the parent's text-decoration / color
            // / font has a glyph stream to attach to. SwiftUI's Text
            // inherits no styling so we route through PlaceholderLabel to
            // pick up the parent's TextConfig (font, color, line-height,
            // text-indent) exactly the way the leaf-text branch below does.
            // Without this fix, components shaped like
            // <div>abc <span>x</span> def</div> only rendered the child's
            // "x" — the parent's underline had nothing to draw under.
            // Known limitation: leading-only; full inline-flow ordering
            // would need an interleaved inlineRuns IR shape.
            if let t = component._text, !t.isEmpty {
                PlaceholderLabel(
                    name: t,
                    color: style.text.color,
                    textConfig: style.text,
                    backgroundColor: style.backgroundColor,
                    clipTextGradient: nil,
                    // text-align only has room to act when the box is
                    // wider than the glyph run — i.e. when the IR set an
                    // explicit width (see fillWidth doc on the label).
                    fillWidth: style.size.width != nil
                )
            }
            // Phase 7 step 2: sort children by CSS `order` BEFORE rendering.
            // SwiftUI has no runtime analogue, so the reordering happens
            // at build time. When no child carries Order, this is a no-op.
            let children = FlexboxApplier.sorted(rawChildren)
            // Parent aggregate for flex-child decoration. Nil fallback
            // keeps us on the legacy-layout path when Phase 7 has not
            // touched this component.
            let parentAgg = style.layout7
            // Grid stretch map (fidelity wave 1): nil for non-grid
            // parents. Every child gets the env value SET explicitly —
            // including nil — so a grandparent's injection can never
            // leak past its own children.
            let plan = gridPlan(style: style)
            // Inheritable text declarations for the children — computed
            // from the MERGED list so grandparents' values ride through
            // parents that don't redeclare them (transitive cascade).
            let childInherited = InheritedText.inheritable(from: mergedProperties)
            // Fidelity wave 2 — flex parents route through CSSFlexLayout
            // (single-line) which consumes per-child FlexItemSpec layout
            // values. FlowLayout (wrap) keeps the legacy FlexChildModifier.
            let isWrapFlex = parentAgg?.flexWrap == .wrap
                || parentAgg?.flexWrap == .wrapReverse
            let isCSSFlex = parentAgg?.display == .flex && !isWrapFlex
            // Column flex? Decides which axis counts as "cross" for the
            // stretch checks below.
            let isColumn = parentAgg?.flexDirection == .column
                || parentAgg?.flexDirection == .columnReverse
            // Definite cross-axis CONTENT size of this flex container —
            // needed to stretch auto-cross-size children with painted
            // backgrounds (css-flexbox-1 §8.3 / css-align-3 §9). Like the
            // wave-1 grid fix, an outer .frame can't reach the child's
            // paint chain, so the value is injected via the environment
            // and folded into the child's own SizeConfig.
            let flexLineCross: CGFloat? = isCSSFlex
                ? flexContentSize(style: style, vertical: !isColumn)
                : nil
            // Static §9.7 main sizes for paint-chain injection (nil when
            // any input is dynamic — Layout still positions correctly).
            let flexMainSizes: [CGFloat]? = isCSSFlex
                ? flexMainPlan(style: style, children: children, column: isColumn)
                : nil
            ForEach(Array(children.enumerated()), id: \.offset) { index, child in
                // Build the child's aggregate once so FlexChildModifier /
                // FlexItemSpec can read align-self / flex-basis / flex-grow
                // without re-parsing. This is a duplicated pass over the
                // child's property list, but it's cheap (string-compare loop).
                let childAgg: LayoutAggregate? = {
                    guard parentAgg?.display == .flex else { return nil }
                    var a = LayoutAggregate()
                    FlexboxExtractor.extract(from: child.properties, into: &a)
                    return a.touched ? a : nil
                }()
                // Fidelity wave 2 — per-child flex spec for CSSFlexLayout.
                // crossAuto mirrors css-flexbox-1 §8.3's stretch
                // precondition: no explicit cross-axis size in the IR.
                let crossAuto = !child.properties.contains {
                    isColumn ? ($0.type == "Width" || $0.type == "InlineSize")
                             : ($0.type == "Height" || $0.type == "BlockSize")
                }
                let spec = FlexItemSpec(
                    grow: CGFloat(childAgg?.flexGrow ?? 0),
                    shrink: CGFloat(childAgg?.flexShrink ?? 1),
                    basisPx: {
                        if case .px(let px)? = childAgg?.flexBasis { return px }
                        return nil
                    }(),
                    alignSelf: childAgg?.alignSelf,
                    crossAuto: crossAuto
                )
                // Stretch injection value: only when this child's resolved
                // alignment is stretch AND its cross size is auto AND the
                // container's cross content size is definite.
                let flexStretch: CGFloat? = {
                    guard isCSSFlex, crossAuto,
                          CSSFlexMath.resolvedAlign(self: childAgg?.alignSelf,
                                                    items: parentAgg?.alignItems) == .stretch
                    else { return nil }
                    return flexLineCross
                }()
                // Bug 2 list-marker — when the parent _tag is an ordered/
                // unordered list and this child is an <li>, prepend a
                // numeric/bullet marker. SwiftUI has no ::marker pseudo,
                // so we emit it inline via an HStack with a leading Text.
                // Markers follow the simple-numeric algorithm: 1-based
                // index + ". " for <ol>, "• " for <ul>. Honors only the
                // common cases; full CSS Counter Styles L3 (arabic-indic,
                // lower-roman, etc.) requires the ListStyleType property
                // on the <li> which existing iOS appliers already extract
                // — we leave that to a follow-up.
                let parentTag = (component._tag ?? "").lowercased()
                let isListItem = (child._tag ?? "").lowercased() == "li"
                Group {
                    if isListItem && (parentTag == "ol" || parentTag == "ul") {
                        HStack(alignment: .firstTextBaseline, spacing: 4) {
                            Text(parentTag == "ol" ? "\(index + 1)." : "•")
                            if !isCSSFlex, let ca = childAgg, let pa = parentAgg {
                                ComponentRenderer(component: child)
                                    .modifier(FlexboxApplier.childModifier(for: ca, parent: pa))
                            } else {
                                ComponentRenderer(component: child)
                            }
                        }
                    } else if isCSSFlex {
                        // CSSFlexLayout parent — parameters ride the
                        // layout value; the legacy frame-based child
                        // modifier would fight the Layout's placement.
                        ComponentRenderer(component: child)
                            .flexItem(spec)
                    } else if let ca = childAgg, let pa = parentAgg {
                        // FlowLayout (wrap) keeps the legacy decoration.
                        ComponentRenderer(component: child)
                            .modifier(FlexboxApplier.childModifier(for: ca, parent: pa))
                    } else {
                        ComponentRenderer(component: child)
                    }
                }
                // Grid + flex size injection (css-align-3 §9 stretch and
                // css-flexbox-1 §9.7 flexed main sizes) — the values are
                // ALWAYS written (nil when nothing to inject) so the
                // environment resets at every tree level. Height channel:
                // grid row stretch, row-flex cross stretch, column-flex
                // MAIN size. Width channel: column-flex cross stretch,
                // row-flex MAIN size. The fold in `body` only fires when
                // the child declared no explicit size on that axis.
                .environment(\.gridStretchHeight,
                             plan?.stretchHeights[index]
                                ?? (isColumn ? flexMainSizes?[index] : flexStretch))
                .environment(\.flexStretchWidth,
                             isColumn ? flexStretch : flexMainSizes?[index])
                // Text inheritance (css-cascade-4): publish this
                // element's merged inheritable declarations for the
                // child. Always written so each level's channel is
                // exactly its parent's merged set — no accumulation
                // beyond the CSS-inherited property list.
                .environment(\.inheritedTextProperties, childInherited)
            }
        } else {
            // CSS `background-clip: text` + a `background-image`
            // gradient: render the gradient as the text fill instead
            // of as the rectangular bg. SwiftUI exposes
            // `.foregroundStyle(<ShapeStyle>)` which accepts a
            // LinearGradient directly, so we hand the gradient stops
            // to PlaceholderLabel and let it pick between gradient and
            // solid-colour foreground.
            let clipText: LinearGradient? = {
                guard style.backgroundClip?.mode == .text else { return nil }
                guard let layers = style.backgroundImage?.layers,
                      let first = layers.first else { return nil }
                if case .linear(_, let stops) = first {
                    let colors = stops.compactMap { $0.color.toSwiftUIColor() ?? Color.clear }
                    if colors.count < 2 { return nil }
                    return LinearGradient(colors: colors,
                                          startPoint: .leading,
                                          endPoint: .trailing)
                }
                return nil
            }()
            // Bug 1 leaf-text: when the component carries `_text`,
            // render it verbatim instead of the underscore-stripped
            // component name. Mirrors web PlaceholderContent.text and
            // brings iOS leaf-text rendering in parity with the web
            // _text fix (swarm-001/css-color__color-001). Absent _text
            // → existing behaviour (placeholder shows the name).
            PlaceholderLabel(
                name: component.name,
                rawText: component._text,
                color: style.text.color,
                textConfig: style.text,
                backgroundColor: style.backgroundColor,
                clipTextGradient: clipText,
                // CSS `text-align: right|center` positions the line box
                // inside the element's CONTENT box. SwiftUI's
                // .multilineTextAlignment only aligns lines against each
                // other, so a single-line label in a wider explicit-width
                // box stayed flush left (background/004_TextBlock,
                // borders/015_TextBlock). Fill the proposed width only
                // when the IR declared a width — otherwise the box hugs
                // (fit-content) and alignment is a no-op anyway.
                fillWidth: style.size.width != nil
            )
        }
    }
}

// MARK: - Placeholder

/// Mirrors the web/Android placeholder: the component's name, centered, small,
/// with smart light/dark contrast based on background luminance.
///
/// `rawText` (Bug 1, swarm-001/css-color__color-001 + swarm-002 mixed-content):
/// when non-nil/non-empty, this string is rendered verbatim instead of the
/// underscore-stripped `name`. Used for the IR's `_text` channel, which
/// carries the actual element text content (a green sentence, an
/// arabic-indic glyph, the parent's "abc def" surrounding a styled
/// child). Absent rawText → existing placeholder behaviour (component
/// name with underscores → spaces), preserving the 327-pair baseline.
private struct PlaceholderLabel: View {
    let name: String
    var rawText: String? = nil
    let color: Color?
    let textConfig: TextConfig
    let backgroundColor: Color?
    // Non-nil → CSS `background-clip: text` is in effect; the gradient
    // paints inside the glyph shape via `.foregroundStyle`. Wins over
    // `color` when set so the rendered output matches web's clipped
    // text only (no rectangular bg fill).
    var clipTextGradient: LinearGradient? = nil
    // Fidelity wave 1 — when true, the label expands to the full
    // proposed width so `text-align` (multilineTextAlignment + frame
    // alignment below) can position the glyph run inside a box that is
    // wider than the text. ONLY set when the IR declared an explicit
    // width: expanding unconditionally would defeat the fit-content
    // hugging every placeholder-only fixture depends on.
    var fillWidth: Bool = false

    var body: some View {
        // Resolve the visible string: rawText wins when present (the IR
        // carried explicit element text content), otherwise fall back to
        // the legacy name-with-underscores-stripped placeholder.
        let visibleText: String = {
            if let t = rawText, !t.isEmpty { return t }
            return name.replacingOccurrences(of: "_", with: " ")
        }()
        let textView = Text(visibleText)
            .font(font)
        // SwiftUI's `.foregroundStyle` accepts ANY ShapeStyle including
        // LinearGradient, so when bg-clip:text is on we replace the
        // foreground colour with the gradient.
        return Group {
            if let g = clipTextGradient {
                textView.foregroundStyle(g)
            } else {
                textView.foregroundColor(resolvedColor)
            }
        }
            // Fidelity wave 2 — text-shadow paints behind the GLYPHS
            // (css-text-decor-3 §4), so the `.shadow` chain attaches
            // right here on the text, before any frame/background can
            // widen the shadow caster. One call per CSS layer; SwiftUI
            // radius is the gaussian σ ≈ CSS blur-radius / 2.
            .modifier(GlyphShadows(layers: textConfig.shadows))
            .multilineTextAlignment(textConfig.textAlign)
            // No hard line cap — let the text wrap to fit the available
            // width and rely on the parent box's height to clip overflow.
            // SwiftUI by default truncates at one line when the layout
            // pass can't determine a height; the `.fixedSize(vertical:)`
            // tells the layout to grow vertically as needed instead, so
            // wrapping behaves like web's natural overflow on
            // placeholder-only fixtures (Typography_TextShadow_*,
            // Typography_FontWeight_Boundary_1000 etc.).
            //
            // Fidelity wave 2 — horizontal is `noWrap` when white-space/
            // text-wrap declared nowrap (css-text-4 §5.1): the run lays
            // out on ONE line at full intrinsic width, overflowing the
            // box to the right exactly like the web reference
            // (Typography_C20/C21 previously wrapped to 2 lines).
            .lineLimit(nil)
            .fixedSize(horizontal: textConfig.noWrap, vertical: true)
            // CSS `line-height` — total line-box height. SwiftUI's
            // `.lineSpacing` adds EXTRA between lines, which is invisible
            // for a single-line placeholder. Force the text frame to be
            // at least `lineHeight` tall so the surrounding box grows
            // to match web/Android's line-box height (e.g. font-size 14
            // + line-height 2 = 28px text box, not the bare 14px iOS
            // would otherwise allocate).
            //
            // fillWidth (fidelity wave 1): `maxWidth: .infinity` accepts
            // the parent's proposal so text-align has room to act; the
            // frame's horizontal alignment mirrors the CSS keyword
            // (right → trailing, center → center).
            //
            // Fidelity wave 2 — minHeight is nil (NOT 0) when the IR set
            // no line-height. Passing 0 made this frame ADOPT the parent
            // height proposal (FrameLayout: a constrained axis clamps
            // the proposal, not the child), so in a fixed-height box
            // shorter than the wrapped text the frame shrank to the
            // proposal and the `.center` alignment spilled glyphs ABOVE
            // the box top — impossible in CSS block flow (Typography_C04
            // /C19, Spacing_C08). The trailing `.fixedSize(vertical:)`
            // pins the frame at its ideal (text) height so overflow now
            // hangs BELOW the box like web/Android; `.center` remains
            // only for the single-line half-leading case where the
            // line box (minHeight) exceeds the glyph height.
            .frame(maxWidth: fillWidth ? .infinity : nil,
                   minHeight: textConfig.lineHeight,
                   alignment: Alignment(horizontal: fillHorizontal,
                                        vertical: .center))
            .fixedSize(horizontal: false, vertical: true)
            .lineSpacing(max(0, (textConfig.lineHeight ?? 0) - (textConfig.fontSize ?? 16)))
            // CSS `text-indent` — push the text right by the indent
            // amount. SwiftUI lacks a first-line-only API, so we use
            // leading padding which inherits to wrapped lines too. For
            // the placeholder fixture set (mostly single-line) this
            // matches web/Android pixel-for-pixel; multi-line cases
            // diverge but no fixture exercises that today.
            .padding(.leading, textConfig.textIndentPx ?? 0)
            .padding(4)
    }

    /// Horizontal frame alignment mirroring the CSS text-align keyword.
    /// Leading when not filling (identity — the frame hugs the text).
    private var fillHorizontal: HorizontalAlignment {
        guard fillWidth else { return .leading }
        switch textConfig.textAlign {
        case .center:   return .center
        case .trailing: return .trailing
        case .leading:  return .leading
        }
    }

    private var font: Font {
        // Build via `.system(size:design:)` so generic-family signals
        // (`font-family: serif/monospace/ui-rounded`) survive — placing
        // `.font(...)` directly on Text overrides any container-level
        // font, so the design has to be baked in here. See TextConfig
        // header for the full chain.
        //
        // Placeholder default size = 16pt to mirror the web browser's
        // inherited body default (16px). The web placeholder
        // (`PlaceholderContent` in `ComponentRenderer.tsx`) sets no
        // explicit font-size and therefore renders at the harness body's
        // size, which CapturePage doesn't override → 16px. Previously this
        // path defaulted to 11pt, leaving placeholder boxes ~30% smaller
        // than their web counterparts on every placeholder-only fixture
        // without an explicit `font-size` (Card_Complete, Input_Field,
        // Outline_Solid, Shadow_Simple, Neumorphic_Light, Tag_Chip, …).
        // Box dimensions follow text size for `width: fit-content` /
        // `wrapContentSize` containers, so the 11→16 bump propagates to
        // overall component geometry, which is what SSIM is comparing.
        // Use bundled Inter as the default placeholder face (cross-platform
        // parity with the Android InterFontFamily and the web @font-face
        // declaration). Only fall back to the system font when the CSS
        // explicitly requested a generic-family signal (.serif / .mono /
        // .rounded) — those paths legitimately want SF Pro variants.
        let size = textConfig.fontSize ?? 16
        var f: Font
        if textConfig.fontDesign == .default {
            f = .custom("Inter", size: size)
        } else {
            f = Font.system(size: size, design: textConfig.fontDesign)
        }
        if let w = textConfig.fontWeight { f = f.weight(w) }
        if textConfig.fontItalic { f = f.italic() }
        // Fidelity wave 2 — `font-variant-caps: small-caps` composes on
        // the label's own font. The box-level FontMod can't reach this
        // Text (a direct `.font` wins over container fonts, Apple docs),
        // so the caps variant must be baked in here (Typography_C06).
        if textConfig.smallCaps { f = f.smallCaps() }
        return f
    }

    private var resolvedColor: Color {
        if let c = color { return c }
        // Web default: inherit #eee @ 0.7 on dark bg; dark text on light bg.
        // Note: alpha is intentionally NOT folded into the luminance here.
        // For translucent fills like rgba(255,255,255,0.2) the raw RGB is
        // white (luminance 1.0) so we pick dark text — which is what iOS
        // renders correctly anyway because `backdrop-filter: blur(...)` is
        // implemented via `.thinMaterial`, lightening the captured surface
        // to a mid-grey. Dark-on-mid-grey is the legible pick. The earlier
        // Glass_Effect bug on Android was caused by a different mechanism
        // (Modifier.blur on the foreground destroyed the placeholder text
        // outright); see FilterApplier.applyBackdropFilters for the fix.
        if let bg = backgroundColor,
           let components = bg.rgbComponents {
            let luminance = 0.299 * components.r + 0.587 * components.g + 0.114 * components.b
            return luminance > 0.6
                ? Color(white: 0.2).opacity(0.7)
                : Color(white: 0.93).opacity(0.7)
        }
        return Color(white: 0.93).opacity(0.7)
    }
}

// MARK: - Glyph shadows (fidelity wave 2)

/// Chains one `.shadow(...)` per CSS text-shadow layer directly on the
/// glyph view (css-text-decor-3 §4 — the shadow caster is the text, not
/// the element box). CSS blur-radius ≈ 2σ while SwiftUI's radius is the
/// gaussian σ, hence the ÷2. Empty layer list → identity (no modifier).
private struct GlyphShadows: ViewModifier {
    /// CSS-ordered shadow layers bridged from TypographyAggregate.
    let layers: [TextShadowLayer]
    func body(content: Content) -> some View {
        // Fold the layers left-to-right; later layers wrap the already-
        // shadowed view which visually approximates CSS's painted-behind
        // stacking for the small offsets the fixtures use.
        layers.reduce(AnyView(content)) { acc, l in
            AnyView(acc.shadow(color: l.color ?? .black.opacity(0.5),
                               radius: l.radius / 2, x: l.x, y: l.y))
        }
    }
}

// MARK: - Alignment bridges

private extension LayoutConfig.Align {
    var verticalAlignment: VerticalAlignment {
        switch self {
        case .flexStart: return .top
        case .flexEnd:   return .bottom
        case .center:    return .center
        case .baseline:  return .firstTextBaseline
        // css-flexbox-1 §8.3: `stretch` only stretches items whose cross
        // size is `auto`; items with a definite cross size are aligned
        // as `flex-start`. Our items are intrinsically sized (fit-content
        // harness parity), so the visible behaviour of the default
        // `align-items: stretch` is TOP alignment — web/Android agree;
        // the old `.center` mapping floated shorter items mid-row.
        case .stretch:   return .top
        }
    }

    var horizontalAlignment: HorizontalAlignment {
        switch self {
        case .flexStart: return .leading
        case .flexEnd:   return .trailing
        case .center:    return .center
        case .baseline:  return .leading
        case .stretch:   return .leading
        }
    }
}

// MARK: - Color luminance helper

private extension Color {
    /// Reasonable sRGB component extraction via UIKit bridge. Fails for
    /// named / pattern colors but all our IR colors go through
    /// Color(.sRGB, ...) so this works for us.
    var rgbComponents: (r: Double, g: Double, b: Double, a: Double)? {
        let ui = UIColor(self)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        guard ui.getRed(&r, green: &g, blue: &b, alpha: &a) else { return nil }
        return (Double(r), Double(g), Double(b), Double(a))
    }
}
