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

    // Fidelity wave 3 — containing-block width channel (css-sizing-3
    // §5.1). Parents with a statically-definite width publish their
    // CONTENT-BOX width here; this component resolves its own percent
    // width against it (threaded into SpacingContext below) instead of
    // against the capture canvas. Nil → canvas fallback (root
    // components, children of fit-content parents). See
    // ContainingBlock.swift for the full rationale.
    @Environment(\.containingBlockWidth) private var containingBlockWidth

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

    // Wave 6 (#32) — custom-property scope channel. Parents publish
    // their MERGED VariableStore (their own `variables` map layered
    // over their inherited chain); this component layers its own map on
    // top before resolving var() references (css-variables-1 §2:
    // custom properties inherit like any other inherited property).
    @Environment(\.cssVariables) private var inheritedVariables

    // Wave 6 (#39) — host-published surface geometry. Replaces the
    // hardcoded 390×844 capture-canvas numbers inside SpacingContext:
    // the harness pins its capture frame, a real app publishes its own
    // window. nil keeps the legacy defaults (see StyleViewport.swift).
    @Environment(\.styleViewport) private var styleViewport

    // Wave 7 (#33/#34) — dynamic-styling inputs. The platform dark-mode
    // signal drives BOTH prefers-color-scheme buckets and light-dark()
    // values (spec 06 §4: one surface, one scheme answer)…
    @Environment(\.colorScheme) private var platformColorScheme
    // …the spec 06 §6 forced-state set arrives from the host (the
    // capture harness's -forceState launch argument)…
    @Environment(\.forcedStyleStates) private var forcedStyleStates
    // …and `:disabled` reads SwiftUI's .disabled() propagation channel
    // directly (spec 06 §2: the host supplies the flag).
    @Environment(\.isEnabled) private var isEnabled

    // Wave 7 (#33) — real interaction state, written by the
    // InteractionBridge listeners attached in body (only when this
    // component carries a selector bucket needing them). @State keeps
    // view identity stable across restyles (spec 06 §5).
    @State private var isHovered = false
    @State private var isPressed = false
    // Focus rides SwiftUI's own focus system (spec 06 §2 `focus`).
    @FocusState private var isFocused: Bool

    /// The spec 06 §4 evaluation environment: render-SURFACE width (the
    /// host-published styleViewport — the capture canvas / app window,
    /// never the device screen; 390 = the legacy canvas default) plus
    /// the platform scheme signal.
    private var mediaEnvironment: MediaQueryEvaluator.Environment {
        MediaQueryEvaluator.Environment(
            surfaceWidthPx: styleViewport?.width ?? 390.0,
            prefersDark: platformColorScheme == .dark)
    }

    /// This component's interaction-state snapshot for StateResolver —
    /// real bridge flags plus the host's forced set.
    private var componentState: ComponentState {
        ComponentState(hovered: isHovered, pressed: isPressed,
                       focused: isFocused, disabled: !isEnabled,
                       // No generic checkable analogue on iOS (spec 06
                       // §2 "checked-if-cheap") — forced-only.
                       checked: false,
                       forced: forcedStyleStates)
    }

    /// Wave 7 — the component's OWN declarations after the spec 06 §3
    /// fold (base → active media buckets → active selector buckets,
    /// wire order, last writer wins) and the light-dark() rewrite.
    /// Bucket-free components return `component.properties` untouched.
    private var effectiveProperties: [IRProperty] {
        // §3 layering against the live state + environment.
        let layered = StateResolver.resolve(base: component.properties,
                                            selectors: component.selectors,
                                            media: component.media,
                                            state: componentState,
                                            environment: mediaEnvironment,
                                            componentName: component.name)
        // css-color-5 light-dark() arms resolve against the same scheme
        // signal — buckets may introduce light-dark values, so this
        // pass runs on the layered output.
        return LightDarkResolver.resolve(layered,
                                         prefersDark: mediaEnvironment.prefersDark)
    }

    /// The component's declarations with the parent's inheritable text
    /// properties merged underneath (css-cascade-4 inheritance).
    /// Wave 7: `own` is the state/media-RESOLVED list, so an active
    /// bucket's values flow into extraction and into the inheritance
    /// channel republished to children exactly like base declarations.
    private var mergedProperties: [IRProperty] {
        InheritedText.merge(own: effectiveProperties,
                            inherited: inheritedTextProperties)
    }

    /// Does any selector bucket resolve to `condition` at runtime v1?
    /// Gates the InteractionBridge listeners so ONLY selector-carrying
    /// components pay for input tracking.
    private func wantsCondition(_ condition: String) -> Bool {
        component.selectors?.contains {
            StateResolver.normalize($0.condition) == condition
        } ?? false
    }

    /// This element's full custom-property scope: own definitions
    /// shadowing the slot-parent chain (css-variables-1 §2 nearest-wins).
    private var mergedVariables: VariableStore {
        VariableStore.merge(inherited: inheritedVariables,
                            own: component.variables)
    }

    /// Wave 6 — the inheritance-merged declarations with every dynamic
    /// value RESOLVED: var() references substituted from the scope
    /// chain (§2.3, guaranteed-invalid ⇒ dropped = unset) and preserved
    /// calc()/relative expressions evaluated against the wave-3
    /// containing-block channel + the inheritance channel's font size.
    /// Static declarations pass through byte-identical, so variable-free
    /// fixtures render exactly as before.
    private var resolvedProperties: [IRProperty] {
        // em/rem base: the PARENT's computed font size, read from the
        // inheritance channel (the parent publishes resolved pixels).
        let inheritedFs = inheritedTextProperties
            .last(where: { $0.type == "FontSize" })
            .flatMap { ValueExtractors.extractPx($0.data) }
            .map(Double.init) ?? 16.0
        // calc() bases — same geometry the style chain itself uses:
        // host viewport (#39, legacy 390×844 when unpublished) and the
        // wave-3 containing block (root fallback mirrors
        // SpacingContext.containingBlockWidth exactly).
        var ctx = CalcEvaluator.EvalContext()
        ctx.viewportWidth = styleViewport?.width ?? 390.0
        ctx.viewportHeight = styleViewport?.height ?? 844.0
        ctx.percentBasisPx = containingBlockWidth.map(Double.init)
            ?? styleViewport?.rootContainingBlock
            ?? (ctx.viewportWidth - 32)
        return DynamicValueResolver.resolve(properties: mergedProperties,
                                            variables: mergedVariables,
                                            calc: ctx,
                                            inheritedFontSizePx: inheritedFs)
    }

    // MARK: - Flow membership (fidelity wave 3)

    /// True when a child is removed from normal flow — css-position-3
    /// §2.1: `position: absolute | fixed` boxes are absolutely
    /// positioned, do not participate in block flow, and are not
    /// flex/grid items (css-flexbox-1 §4 / css-grid-1 §6). Pure so the
    /// wave-3 XCTest pins the classification.
    ///
    /// public: the iOS harness's CaptureCanvas reuses this to give a
    /// ROOT-level absolute component the same card treatment the web
    /// canvas produces (collapsed flow height + overflow clip) — one
    /// classification, two consumers.
    public static func isOutOfFlow(_ child: IRComponent) -> Bool {
        let p = LayoutExtractor.extract(from: child.properties)?.position
        return p == .absolute || p == .fixed
    }

    /// Children that participate in the parent's normal flow (block /
    /// flex / grid layout). Everything the flow container renders.
    private var inFlowChildren: [IRComponent] {
        (component.children ?? []).filter { !Self.isOutOfFlow($0) }
    }

    /// Absolutely-positioned children. Rendered as a ZStack overlay on
    /// TOP of the in-flow content — CSS 2.1 Appendix E paint order:
    /// positioned descendants (step 8) paint after in-flow blocks and
    /// inlines (steps 4–7). The previous renderer put ALL children in
    /// one ZStack, so (a) in-flow siblings lost their block stacking
    /// and (b) a later in-flow sibling painted OVER the absolute box
    /// (trees/block-flow B_RelativeAnchor: the red top:10/left:20 box
    /// showed only a sliver below the orange in-flow sibling).
    private var outOfFlowChildren: [IRComponent] {
        (component.children ?? []).filter { Self.isOutOfFlow($0) }
    }

    /// Wave 5 — NEGATIVE z-index positioned children (CSS 2.1 Appendix E
    /// step 3: they paint BEFORE the in-flow content of their stacking
    /// context, i.e. visually BEHIND this element's own background). The
    /// overlay ZStack put them on top, so `z-index: -1` boxes showed
    /// through the parent's opaque background where web hides them
    /// (PL_ZNegative). They render via `.background` OUTSIDE the paint
    /// chain instead — behind everything this element paints.
    private var negativeZChildren: [IRComponent] {
        outOfFlowChildren.filter {
            (ItemPlacementExtractor.extract(from: $0.properties).paint.zIndex ?? 0) < 0
        }
    }

    /// The overlay half of the split: positioned children with
    /// non-negative z-index — Appendix E step 8, above in-flow content.
    private var overlayChildren: [IRComponent] {
        outOfFlowChildren.filter {
            (ItemPlacementExtractor.extract(from: $0.properties).paint.zIndex ?? 0) >= 0
        }
    }

    // public: explicit memberwise init — the synthesized one is internal,
    // so cross-module callers need this spelled out.
    public init(component: IRComponent) {
        self.component = component
    }

    // public: View protocol witness on a public type must be public.
    public var body: some View {
        // Build the style from the inheritance-merged, variable-RESOLVED
        // declarations (wave 6 — extraction sees concrete values), then
        // fold in the grid-stretch height (if the parent injected one and
        // the IR declared no explicit height — an explicit height always
        // wins per css-align-3 §9's "auto block size" precondition).
        let style: ComponentStyle = {
            var s = StyleBuilder.build(from: resolvedProperties)
            // Wave 6 (#39) — adopt the host-published surface geometry
            // FIRST so every resolver lane below (vw/vh, percent bases,
            // GeometryReader fallbacks) uses the capture canvas / app
            // window instead of the old hardcoded 390×844 literals.
            if let vp = styleViewport {
                s.spacing.context.viewportWidth = vp.width
                s.spacing.context.viewportHeight = vp.height
                s.spacing.context.rootContainingBlockPx = vp.rootContainingBlock
            }
            // Fidelity wave 3 — thread the parent-published containing
            // block into the resolver context FIRST so every percent
            // width below (own width, flex plans, child publication)
            // resolves against the parent's content box (css-sizing-3
            // §5.1) instead of the canvas.
            s.spacing.context.containingBlockWidthPx = containingBlockWidth.map(Double.init)
            if let h = gridStretchHeight, s.size.height == nil {
                s.size.height = .exact(px: h)
            }
            // Column-flex stretch (fidelity wave 2): same fold as the
            // grid/row channel but on the inline axis. An explicit CSS
            // width always wins (css-align-3 §9 auto-size precondition).
            if let w = flexStretchWidth, s.size.width == nil {
                s.size.width = .exact(px: w)
            }
            // Fidelity wave 3 — multicol full-width default
            // (Columns_Decorated, 0.556 → worst wave-3 row). A multicol
            // container is a BLOCK container (css-multicol-1 §1) whose
            // max-content inline size is N × content-max-content +
            // (N−1) × gap — for any real content with N ≥ 2 that
            // overflows the canvas, so the web reference (fit-content
            // capped by `max-width: 100%`, ComponentRenderer.tsx) lays
            // it out exactly containing-block wide while iOS hugged the
            // placeholder into a ~210px pill. Fold the containing-block
            // width in as the used width; an explicit CSS width always
            // wins. TODO: small-content multicol boxes (N × max-content
            // < canvas) would need static text measurement to hug.
            if s.size.width == nil, let n = s.columns?.count, n >= 2 {
                s.size.width = .exact(px: Double(s.spacing.context.containingBlockWidth))
            }
            return s
        }()

        if style.layout.display == .none {
            EmptyView()
        } else {
            // Phase 7 step 4: apply per-child positioning after container
            // selection so absolute/relative offsets stack on top of the
            // fully-styled element. Identity when position/zindex unset.
            // Wave 5: negative-z positioned children attach OUTSIDE the
            // styled box via `.background` — SwiftUI paints an outer
            // .background behind everything applied so far, which is
            // exactly Appendix E step 3 (behind this element's own
            // background). Their PositionApplier offsets anchor at the
            // same top-leading origin the overlay uses.
            let positioned = PositionApplier.apply(
                negativeZChildren.isEmpty
                    ? AnyView(layoutContainer(style: style).applyStyle(style))
                    : AnyView(layoutContainer(style: style).applyStyle(style)
                        .background(alignment: .topLeading) {
                            ZStack(alignment: .topLeading) {
                                positionedChildren(style: style,
                                                   children: negativeZChildren)
                            }
                        }),
                aggregate: style.layout7
            )
            // Wave 5 — `float: right | inline-end` (LTR: both anchor to
            // the containing block's right edge, CSS 2.1 §9.5.1 rule 1).
            // The greedy frame claims the containing block's width and
            // parks the styled box at its trailing edge — the harness-
            // scale half of float semantics (no sibling wrap-around).
            // left/inline-start floats already sit at the left edge in
            // this block-flow renderer, so they need no wrap.
            let fl = style.layout7?.float
            // Wave 7 (#33) — attach the interaction listeners on the
            // finished box. Gated per condition: a component without
            // hover/active/focus buckets attaches NOTHING (identity
            // branches inside InteractionBridge), keeping the
            // pre-wave-7 view hierarchy for the whole static corpus.
            Group {
                if fl == .right || fl == .inlineEnd {
                    positioned.frame(maxWidth: .infinity, alignment: .topTrailing)
                } else {
                    positioned
                }
            }
            .modifier(InteractionBridge(wantsHover: wantsCondition("hover"),
                                        wantsActive: wantsCondition("active"),
                                        wantsFocus: wantsCondition("focus"),
                                        hovered: $isHovered,
                                        pressed: $isPressed,
                                        focused: $isFocused))
        }
    }

    // MARK: - Container selection

    @ViewBuilder
    private func layoutContainer(style: ComponentStyle) -> some View {
        // Fidelity wave 3 — absolutely-positioned children are removed
        // from flow (css-position-3 §2.1) and paint ABOVE in-flow
        // content (CSS 2.1 Appendix E: positioned descendants are paint
        // step 8, after in-flow steps 4–7). The in-flow children keep
        // their normal container (block VStack / CSSFlexLayout / grid)
        // and the out-of-flow ones overlay it in a top-leading ZStack
        // so PositionApplier's offsets anchor at the parent's top-left
        // corner. Previously ALL children shared one ZStack: in-flow
        // siblings collapsed onto each other and painted over the
        // absolute box (B_RelativeAnchor).
        // Wave 5: only NON-negative z-index positioned children ride the
        // overlay; negative-z ones attach behind the styled box in
        // `body` (Appendix E step 3 vs step 8 split).
        if overlayChildren.isEmpty {
            flowContainer(style: style)
        } else {
            ZStack(alignment: .topLeading) {
                // In-flow content first — lower paint layer.
                flowContainer(style: style)
                // Positioned descendants after — upper paint layer.
                absoluteOverlay(style: style)
            }
        }
    }

    /// The normal-flow container for this component's IN-FLOW children
    /// (block / flex / grid selection). Split out of layoutContainer by
    /// the wave-3 absolute-positioning fix so the overlay wrap composes
    /// around any container kind.
    @ViewBuilder
    private func flowContainer(style: ComponentStyle) -> some View {
        // Phase 2: resolve gap via the new GapApplier. Row gap for vertical
        // stacks, column gap for horizontal. Zero when no gap/row/col-gap
        // is set on the IR.
        let gap = GapApplier.resolve(style.spacing.gap, context: style.spacing.context)
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
        // Wave 3: only IN-FLOW children count — a container whose only
        // children are absolutely positioned has zero flex/grid items
        // (css-flexbox-1 §4 / css-grid-1 §6) and lays out like an empty
        // block, exactly as the web reference does.
        let hasChildren = !inFlowChildren.isEmpty
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
            // per-child parameters ride the ItemPlacement layout value
            // attached by ComponentHost (v2 placement contract).
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

    // MARK: - Absolute overlay (fidelity wave 3)

    /// Render the absolutely-positioned children on top of the in-flow
    /// content. Each child renders through ComponentRenderer as usual —
    /// its own body applies PositionApplier, whose flexible
    /// top-leading frame + offset resolves top/left against this
    /// ZStack's corner. Declaration order breaks paint ties, matching
    /// CSS tree order within paint step 8 (CSS 2.1 Appendix E).
    ///
    /// Approximation: the CSS containing block for an absolute child is
    /// the PADDING box of its positioned ancestor; our ZStack wraps the
    /// container BEFORE the padding applier, so offsets anchor at the
    /// CONTENT-box corner instead. No wave fixture combines padding
    /// with absolute children; TODO when one does.
    @ViewBuilder
    private func absoluteOverlay(style: ComponentStyle) -> some View {
        // Overlay half only — negative-z children paint behind the
        // styled box via the `body` background split (wave 5).
        positionedChildren(style: style, children: overlayChildren)
    }

    /// Shared renderer for positioned children — used by BOTH halves of
    /// the wave-5 z-split (overlay ≥ 0, background < 0) so environment
    /// resets stay identical.
    @ViewBuilder
    private func positionedChildren(style: ComponentStyle,
                                    children: [IRComponent]) -> some View {
        // Inheritance flows into positioned children exactly like flow
        // children (css-cascade-4 — inheritance is by tree, not flow).
        // Wave 6: publish the RESOLVED declarations so children inherit
        // computed values (a var()-valued font-size flows down as px).
        let childInherited = InheritedText.inheritable(from: resolvedProperties)
        // Percent widths of absolute children resolve against the
        // positioned ancestor's box (content-box approximation — same
        // channel as flow children).
        let childCB = flexContentSize(style: style, vertical: false)
        ForEach(Array(children.enumerated()), id: \.offset) { _, child in
            // v2: children render through ComponentHost (placement
            // parent-data attached; inert here — the overlay ZStack
            // reads no layout values).
            ComponentHost(component: child)
                // Reset the size-injection channels: an absolute child
                // never stretch-inherits grid/flex geometry (it is not
                // an item of the parent's formatting context).
                .environment(\.gridStretchHeight, nil)
                .environment(\.flexStretchWidth, nil)
                .environment(\.containingBlockWidth, childCB)
                .environment(\.inheritedTextProperties, childInherited)
                // Custom-property scope (wave 6): positioned children
                // sit in the same slot-parent chain as flow children —
                // the merged store flows down uncut (css-variables-1 §2).
                .environment(\.cssVariables, mergedVariables)
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
            // wave. v2: per-item placement arrives WITH the subviews via
            // ItemPlacementKey (attached by ComponentHost); this call
            // passes ONLY the container's own policy (tracks, gaps,
            // *-items defaults) — zero child knowledge (design §2.1).
            CSSGridLayout(
                // Column tracks from the template; a template-less grid
                // is a single auto column per css-grid-1 §7.1.
                tracks: agg?.gridTemplateColumns?.tracks.map(\.kind) ?? [.automatic],
                // Row template feeds fixed row heights (60px tracks in
                // grid-2col/010_G2_AlignSelf).
                rowTemplate: agg?.gridTemplateRows?.tracks.map(\.kind),
                // Implicit rows take the first grid-auto-rows size.
                autoRows: agg?.gridAutoRows?.tracks.first?.kind,
                // Container-side alignment defaults — each arriving
                // item's justify/align-self resolves against these
                // inside the Layout (css-align-3 §6).
                justifyItems: agg?.justifyItems,
                alignItems: agg?.alignItems,
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
            // Template-areas grids (wave 5) — SAME CSSGridLayout engine
            // as plain track-list grids. The old iOS 16 Grid/GridRow
            // path matched children to cells BY NAME (child "box1" only
            // rendered if an area was literally called "box1"): children
            // whose name matched no area were DROPPED, and a child whose
            // area spanned N cells rendered N duplicate copies. Instead,
            // each child's own grid-area claim (a NAMED line riding
            // ItemPlacement) is resolved against this template inside
            // the Layout — browser-verified semantics in
            // GridPlacer.resolveNames.
            CSSGridLayout(
                // Column tracks: explicit template wins; otherwise the
                // area template's width defines that many auto columns
                // (css-grid-1 §7.3: each column in the areas grammar
                // creates an explicit auto track).
                tracks: agg?.gridTemplateColumns?.tracks.map(\.kind)
                    ?? Array(repeating: GridTrack.Kind.automatic,
                             count: max(1, agg?.gridTemplateAreas?.map(\.count).max() ?? 1)),
                // Row template / implicit-row sizing identical to the
                // plain-grid path above.
                rowTemplate: agg?.gridTemplateRows?.tracks.map(\.kind),
                autoRows: agg?.gridAutoRows?.tracks.first?.kind,
                justifyItems: agg?.justifyItems,
                alignItems: agg?.alignItems,
                rowGap: gap.row,
                columnGap: gap.column,
                definiteWidth: style.size.width != nil,
                // The named-area map — consumed only by the claims
                // resolver (css-grid-1 §8.3).
                templateAreas: agg?.gridTemplateAreas
            ) {
                contentOrPlaceholder(style: style)
            }
        default:
            // Fallback — vertical stack. Keeps the switch exhaustive.
            VStack(alignment: .leading, spacing: gap.row) {
                contentOrPlaceholder(style: style)
            }
        }
    }

    // MARK: - Grid stretch pre-pass (fidelity wave 1, v2-refactored)

    /// Static stretch-height map for a `display: grid` container, keyed
    /// by SORTED-CHILD index — the paint-chain half of grid stretch
    /// (css-align-3 §9): the row height is injected into the child's
    /// environment so its OWN background/border paint at the stretched
    /// height (an outer .frame can't reach the child's paint chain).
    ///
    /// v2 note: this is a RENDERER pre-pass over this component's own
    /// children, not container-side child inspection — the placement
    /// requests it simulates come from the exact same
    /// ItemPlacementExtractor claims ComponentHost attaches for
    /// CSSGridLayout, so the simulated assignment provably matches what
    /// the Layout resolves at measure time. Returns nil for non-grid
    /// parents so contentOrPlaceholder skips the env injection entirely.
    private func gridStretchHeights(style: ComponentStyle) -> [Int: CGFloat]? {
        // Only grid containers with children get a plan. Mirrors the
        // gridKind decision in layoutContainer: an explicit template
        // routes to the grid path even without `display: grid` (legacy
        // behaviour of GridApplier.containerKind).
        // Wave 3: plan over IN-FLOW children only — absolute children
        // are not grid items (css-grid-1 §6) and render via the overlay.
        let rawChildren = inFlowChildren
        // Wave 5: template-areas grids (.grid kind) now flow through the
        // same CSSGridLayout, so they take the same stretch plan.
        let kind = style.layout7.flatMap { GridApplier.containerKind(for: $0) }
        guard let parentAgg = style.layout7,
              parentAgg.display == .grid
                || kind == .lazyVGrid || kind == .grid,
              !rawChildren.isEmpty else { return nil }
        // Same ordering contentOrPlaceholder renders with (CSS `order`).
        let children = FlexboxApplier.sorted(rawChildren)
        // A parent that carries text AND children renders the text as a
        // leading placeholder — CSS wraps loose grid text in an
        // anonymous grid ITEM, so it participates in placement (as a
        // nil-placement subview: auto-placed, start-aligned).
        let hasLeadingText = (component.text?.isEmpty == false)
        var requests: [GridItemRequest] = []
        // Track per-child "block size may stretch" flags for the env map.
        var stretchFlags: [Bool] = []
        if hasLeadingText {
            // The anonymous text item auto-places at the first free cell.
            requests.append(GridItemRequest())
        }
        // Same template facts CSSGridLayout.claims() resolves against —
        // keeping this simulation bit-identical to the Layout (wave 5:
        // named lines + negative indices resolve before assignment).
        let areas = parentAgg.gridTemplateAreas
        let explicitRows = max(parentAgg.gridTemplateRows?.tracks.count ?? 0,
                               areas?.count ?? 0)
        let cols = parentAgg.gridTemplateColumns?.tracks.count
            ?? max(1, areas?.map(\.count).max() ?? 1)
        for child in children {
            // The child's v2 placement claims — SAME extraction the
            // ComponentHost attaches, so this simulation and the
            // CSSGridLayout resolution can never drift.
            let claim = ItemPlacementExtractor.extract(from: child.properties)
            requests.append(GridPlacer.resolveNames(claim.grid.request,
                                                    areas: areas,
                                                    columnCount: cols,
                                                    explicitRowCount: explicitRows))
            // Stretch candidate: effective block-axis keyword is
            // stretch/normal/auto (the grid default) AND the child has
            // no explicit block size in the IR (css-align-3 §9 auto-size
            // precondition — the explicitHeight fact on the claim).
            let effAlign: AlignmentKeyword? = {
                if let s = claim.grid.alignSelf, s != .auto { return s }
                return parentAgg.alignItems
            }()
            let stretchy = effAlign == nil || effAlign == .stretch || effAlign == .normal
            stretchFlags.append(stretchy && !claim.explicitHeight)
        }
        // Resolve stretch heights: every row the item covers must be a
        // statically-knowable FIXED track — a `grid-template-rows` px
        // entry inside the template, or the `grid-auto-rows` px size for
        // implicit rows (wave 5: the old plan handled only span-1 items
        // inside the template, so LineNumbers items hugged 30px in 40px
        // auto-rows and LineSpans `d` never covered its 2-row span).
        var stretchHeights: [Int: CGFloat] = [:]
        let rowTracks = parentAgg.gridTemplateRows?.tracks
        let autoRow = parentAgg.gridAutoRows?.tracks.first?.kind
        // Row gap joins the summed tracks for multi-row spans — same
        // resolver lane the container's own layout call uses.
        let rowGapPx = GapApplier.resolve(style.spacing.gap,
                                          context: style.spacing.context).row
        let (cells, _) = GridPlacer.assign(requests, columnCount: cols)
        let offset = hasLeadingText ? 1 : 0
        for i in 0..<children.count where stretchFlags[i] {
            let cell = cells[i + offset]
            // Sum the fixed heights of every covered row; bail on the
            // first non-fixed one (content-sized rows stretch to content
            // — an identity the paint chain already renders).
            var total: CGFloat = 0
            var allFixed = true
            for r in cell.row..<(cell.row + cell.rowSpan) {
                if let tpl = rowTracks, r < tpl.count {
                    // Explicit template row — only a px literal is static.
                    if case .fixed(let px) = tpl[r].kind { total += px }
                    else { allFixed = false; break }
                } else if case .fixed(let px)? = autoRow {
                    // Implicit row — grid-auto-rows px size (§7.6).
                    total += px
                } else {
                    allFixed = false; break
                }
            }
            // Spanning items cover the (span−1) gaps too (css-align-3
            // gutters are part of the spanned area, browser-verified:
            // rows 3→5 of 36px tracks + 6px gap stretch to 78px).
            if allFixed {
                stretchHeights[i] = total + rowGapPx * CGFloat(cell.rowSpan - 1)
            }
        }
        return stretchHeights
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
            // Wave 3: percent widths resolve against the threaded
            // containing block (parent content box, canvas at root) —
            // the same basis SizeApplier paints with.
            : SizeApplierResolve.exact(style.size.width, ctx: ctx,
                                       parent: ctx.containingBlockWidth)
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
        // A leading text placeholder participates in the Layout as an
        // extra item — index mapping would shift; no wave fixture mixes
        // text with flexed children, so bail honestly.
        guard component.text?.isEmpty != false else { return nil }
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
                // Wave 3: child percent widths resolve against THIS
                // container's containing block for the static plan —
                // close enough for the plan's purposes (the child's own
                // paint pass re-resolves against the container's
                // content box via the environment channel).
                : SizeApplierResolve.exact(cs.width, ctx: ctx,
                                           parent: ctx.containingBlockWidth)
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
        // The placeholder only appears when the component has NO
        // children at all — a parent whose children are ALL absolutely
        // positioned still renders empty in-flow content (web parity:
        // ComponentRenderer.tsx keys the placeholder on
        // `children.length`), while its boxes arrive via the overlay.
        if component.children?.isEmpty == false {
            // Bug 1 mixed-content fix — see
            // testing/titan/investigations/swarm-002/css-text-decor__text-decoration-decorating-box-thickness-001.json
            //
            // When the parent carries text AND children, render the text
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
            // (v2 rename: the wire field is `text`, formerly `_text`.)
            if let t = component.text, !t.isEmpty {
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
            // Wave 3: in-flow children only — absolute/fixed boxes render
            // via the layoutContainer overlay (see absoluteOverlay).
            let children = FlexboxApplier.sorted(inFlowChildren)
            // Parent aggregate for flex-child decoration. Nil fallback
            // keeps us on the legacy-layout path when Phase 7 has not
            // touched this component.
            let parentAgg = style.layout7
            // Grid stretch map (fidelity wave 1): nil for non-grid
            // parents. Every child gets the env value SET explicitly —
            // including nil — so a grandparent's injection can never
            // leak past its own children.
            let stretchHeights = gridStretchHeights(style: style)
            // Inheritable text declarations for the children — computed
            // from the MERGED, variable-RESOLVED list so grandparents'
            // values ride through parents that don't redeclare them
            // (transitive cascade) and children inherit COMPUTED values
            // (css-cascade-4 §7.3 — a var()-valued font-size flows down
            // as the resolved pixels, keeping chained em bases honest).
            let childInherited = InheritedText.inheritable(from: resolvedProperties)
            // Fidelity wave 2 — flex parents route through CSSFlexLayout
            // (single-line) which consumes per-child ItemPlacement.flex
            // claims. FlowLayout (wrap) keeps the legacy FlexChildModifier.
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
            // Fidelity wave 3 — containing-block publication
            // (css-sizing-3 §5.1): children resolve percent widths
            // against THIS box's content width. Definite only when our
            // own width is statically known (flexContentSize subtracts
            // the padding band + painted borders); nil resets the
            // channel for fit-content parents so a grandparent's basis
            // never leaks past its own children.
            let childCB: CGFloat? = flexContentSize(style: style, vertical: false)
            ForEach(Array(children.enumerated()), id: \.offset) { index, child in
                // Build the child's aggregate once so FlexChildModifier
                // (legacy wrap path) and the stretch env computation can
                // read align-self / flex-basis / flex-grow without
                // re-parsing. This is a duplicated pass over the child's
                // property list, but it's cheap (string-compare loop).
                // (The CSSFlexLayout inputs themselves now ride the v2
                // ItemPlacement layout value attached by ComponentHost —
                // extracted from the same properties, so identical.)
                let childAgg: LayoutAggregate? = {
                    guard parentAgg?.display == .flex else { return nil }
                    var a = LayoutAggregate()
                    FlexboxExtractor.extract(from: child.properties, into: &a)
                    return a.touched ? a : nil
                }()
                // Fidelity wave 2 — crossAuto mirrors css-flexbox-1
                // §8.3's stretch precondition: no explicit cross-axis
                // size in the IR (used by the stretch env injection).
                let crossAuto = !child.properties.contains {
                    isColumn ? ($0.type == "Width" || $0.type == "InlineSize")
                             : ($0.type == "Height" || $0.type == "BlockSize")
                }
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
                // Bug 2 list-marker — when the parent's source tag is an
                // ordered/unordered list and this child is an <li>,
                // prepend a numeric/bullet marker. SwiftUI has no
                // ::marker pseudo, so we emit it inline via an HStack
                // with a leading Text. Markers follow the simple-numeric
                // algorithm: 1-based index + ". " for <ol>, "• " for
                // <ul>. Honors only the common cases; full CSS Counter
                // Styles L3 (arabic-indic, lower-roman, etc.) requires
                // the ListStyleType property on the <li> which existing
                // iOS appliers already extract — we leave that to a
                // follow-up. (v2 rename: the hint now lives at
                // meta.sourceTag, formerly `_tag`.)
                let parentTag = (component.meta?.sourceTag ?? "").lowercased()
                let isListItem = (child.meta?.sourceTag ?? "").lowercased() == "li"
                Group {
                    if isListItem && (parentTag == "ol" || parentTag == "ul") {
                        HStack(alignment: .firstTextBaseline, spacing: 4) {
                            Text(parentTag == "ol" ? "\(index + 1)." : "•")
                            if !isCSSFlex, let ca = childAgg, let pa = parentAgg {
                                // Marker rows keep the legacy decoration;
                                // the host's placement inside the HStack
                                // is invisible to the outer Layout (layout
                                // values don't cross container boundaries)
                                // — same as the pre-v2 no-spec behaviour.
                                ComponentHost(component: child)
                                    .modifier(FlexboxApplier.childModifier(for: ca, parent: pa))
                            } else {
                                ComponentHost(component: child)
                            }
                        }
                    } else if isCSSFlex {
                        // CSSFlexLayout parent — the flex claims ride the
                        // ItemPlacement layout value ComponentHost
                        // attaches; the legacy frame-based child modifier
                        // would fight the Layout's placement.
                        ComponentHost(component: child)
                    } else if let ca = childAgg, let pa = parentAgg {
                        // FlowLayout (wrap) keeps the legacy decoration.
                        ComponentHost(component: child)
                            .modifier(FlexboxApplier.childModifier(for: ca, parent: pa))
                    } else {
                        // Block/grid parents — CSSGridLayout reads the
                        // host-attached grid claims; block parents leave
                        // the placement inert.
                        ComponentHost(component: child)
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
                             stretchHeights?[index]
                                ?? (isColumn ? flexMainSizes?[index] : flexStretch))
                .environment(\.flexStretchWidth,
                             isColumn ? flexStretch : flexMainSizes?[index])
                // Containing block (wave 3): always written — definite
                // content width or nil — so the channel resets at every
                // tree level (no grandparent leak).
                .environment(\.containingBlockWidth, childCB)
                // Text inheritance (css-cascade-4): publish this
                // element's merged inheritable declarations for the
                // child. Always written so each level's channel is
                // exactly its parent's merged set — no accumulation
                // beyond the CSS-inherited property list.
                .environment(\.inheritedTextProperties, childInherited)
                // Custom-property scope (wave 6, css-variables-1 §2):
                // publish this element's merged VariableStore — own
                // definitions shadowing the inherited chain — so every
                // child resolves var() against exactly its slot-parent
                // chain (TK_TwoLevelShadow's mid `--accent` repaint).
                .environment(\.cssVariables, mergedVariables)
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
                if let layers = style.backgroundImage?.layers,
                   let first = layers.first,
                   case .linear(_, let stops) = first {
                    let colors = stops.compactMap { $0.color.toSwiftUIColor() ?? Color.clear }
                    if colors.count >= 2 {
                        return LinearGradient(colors: colors,
                                              startPoint: .leading,
                                              endPoint: .trailing)
                    }
                }
                // Wave 5: a SOLID background-color clips to the glyphs
                // too (css-backgrounds-4 §2.2) — the browser paints no
                // rectangular box, just bg-coloured text
                // (PW_Background_Effects_01: web shows a #3498db label
                // on the bare canvas). StyleBuilder suppresses the rect
                // paint; here the colour becomes the glyph fill via the
                // same foregroundStyle path (a two-stop constant
                // gradient keeps PlaceholderLabel's API unchanged).
                if let bg = style.backgroundColor {
                    return LinearGradient(colors: [bg, bg],
                                          startPoint: .leading,
                                          endPoint: .trailing)
                }
                return nil
            }()
            // Bug 1 leaf-text: when the component carries `text` (the
            // v2 rename of `_text`), render it verbatim instead of the
            // underscore-stripped component name. Mirrors web
            // PlaceholderContent.text and brings iOS leaf-text rendering
            // in parity with the web fix (swarm-001/css-color__color-001).
            // Absent text → existing behaviour (placeholder shows name).
            PlaceholderLabel(
                name: component.name,
                rawText: component.text,
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
        // Fidelity wave 3 — CSS line-box leading split (CSS 2.1 §10.8):
        // `spacing` makes each line ADVANCE exactly line-height px;
        // `halfLeading` restores the band above the first / below the
        // last line that browsers paint and SwiftUI's between-lines-only
        // `.lineSpacing` dropped (IH_LineHeight band-sits-high channel).
        // Both are 0 when the IR declared no line-height.
        let leading = LineBoxMetrics.leading(lineHeightPx: textConfig.lineHeight,
                                             fontSizePx: textConfig.fontSize ?? 16,
                                             design: textConfig.fontDesign)
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
            // Fidelity wave 3 — first/last half-leading (CSS 2.1
            // §10.8.1): browsers centre each line's glyphs inside a
            // line box `line-height` tall, so half the leading paints
            // ABOVE the first line and BELOW the last. Adding it as
            // real vertical padding (a) grows an auto-height multi-line
            // box to exactly N × line-height like the web reference and
            // (b) keeps the single-line case byte-identical — content
            // + 2 × half-leading = line-height, the same box the
            // minHeight frame below already produced (glyphs were
            // already centred in it). Zero when no line-height set.
            .padding(.vertical, leading.halfLeading)
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
            // Fidelity wave 3 — line ADVANCE = line-height exactly.
            // The extra between-lines space is `line-height − content
            // area height` (ascent + descent of the rendered face), NOT
            // `line-height − font-size` as before: font-size undershoots
            // the content area by ~21% of an em for Inter, so every
            // advance overshot the browser's by that difference while
            // the box total still came out short (no first/last band).
            .lineSpacing(leading.spacing)
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
