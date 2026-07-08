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

struct ComponentRenderer: View {
    let component: IRComponent

    var body: some View {
        let style = StyleBuilder.build(from: component.properties)

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
            return GridApplier.containerKind(for: agg)
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
            // Empty flex containers: SwiftUI's HStack hugs content tight,
            // but LazyVGrid (.grid case below) doesn't — same issue
            // resolved for grids above. Empty flex doesn't expand by
            // default, so the ordinary HStack path is fine here.
            HStack(
                alignment: style.layout.align.verticalAlignment,
                spacing: gap.column
            ) {
                contentOrPlaceholder(style: style)
            }
        case .flexColumn:
            VStack(
                alignment: style.layout.align.horizontalAlignment,
                spacing: gap.row
            ) {
                contentOrPlaceholder(style: style)
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
            // Map GridTemplateColumns → [GridItem] via GridApplier.
            let items = GridApplier.gridItems(
                for: agg?.gridTemplateColumns,
                columnGap: gap.column
            )
            LazyVGrid(columns: items, spacing: gap.row) {
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
                    clipTextGradient: nil
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
            ForEach(Array(children.enumerated()), id: \.offset) { index, child in
                // Build the child's aggregate once so FlexChildModifier
                // can read align-self / flex-basis / flex-grow without
                // re-parsing. This is a duplicated pass over the child's
                // property list, but it's cheap (string-compare loop).
                let childAgg: LayoutAggregate? = {
                    guard parentAgg?.display == .flex else { return nil }
                    var a = LayoutAggregate()
                    FlexboxExtractor.extract(from: child.properties, into: &a)
                    return a.touched ? a : nil
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
                if isListItem && (parentTag == "ol" || parentTag == "ul") {
                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text(parentTag == "ol" ? "\(index + 1)." : "•")
                        if let ca = childAgg, let pa = parentAgg {
                            ComponentRenderer(component: child)
                                .modifier(FlexboxApplier.childModifier(for: ca, parent: pa))
                        } else {
                            ComponentRenderer(component: child)
                        }
                    }
                } else if let ca = childAgg, let pa = parentAgg {
                    ComponentRenderer(component: child)
                        .modifier(FlexboxApplier.childModifier(for: ca, parent: pa))
                } else {
                    ComponentRenderer(component: child)
                }
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
                clipTextGradient: clipText
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
            .multilineTextAlignment(textConfig.textAlign)
            // No hard line cap — let the text wrap to fit the available
            // width and rely on the parent box's height to clip overflow.
            // SwiftUI by default truncates at one line when the layout
            // pass can't determine a height; the `.fixedSize(vertical:)`
            // tells the layout to grow vertically as needed instead, so
            // wrapping behaves like web's natural overflow on
            // placeholder-only fixtures (Typography_TextShadow_*,
            // Typography_FontWeight_Boundary_1000 etc.).
            .lineLimit(nil)
            .fixedSize(horizontal: false, vertical: true)
            // CSS `line-height` — total line-box height. SwiftUI's
            // `.lineSpacing` adds EXTRA between lines, which is invisible
            // for a single-line placeholder. Force the text frame to be
            // at least `lineHeight` tall so the surrounding box grows
            // to match web/Android's line-box height (e.g. font-size 14
            // + line-height 2 = 28px text box, not the bare 14px iOS
            // would otherwise allocate).
            .frame(minHeight: textConfig.lineHeight ?? 0,
                   alignment: .center)
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

// MARK: - Alignment bridges

private extension LayoutConfig.Align {
    var verticalAlignment: VerticalAlignment {
        switch self {
        case .flexStart: return .top
        case .flexEnd:   return .bottom
        case .center:    return .center
        case .baseline:  return .firstTextBaseline
        case .stretch:   return .center
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
