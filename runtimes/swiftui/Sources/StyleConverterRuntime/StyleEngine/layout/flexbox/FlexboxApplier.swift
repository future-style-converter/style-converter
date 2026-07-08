//
//  FlexboxApplier.swift
//  StyleEngine/layout/flexbox — Phase 7 step 2 (flexbox).
//
//  Reads flex fields out of a populated LayoutAggregate and produces:
//    • A ContainerDecision that ComponentRenderer uses to select
//      HStack / VStack / ZStack / FlowLayout for this component.
//    • Per-child modifiers (align-self override, flex-basis sizing,
//      flex-grow priority).
//    • A child-sorter that re-orders a [IRComponent] array by `order`
//      BEFORE rendering — SwiftUI has no equivalent of CSS `order`.
//
//  SwiftUI's stack initialisers consume alignment+spacing at construction
//  time, which is why this lives outside the per-modifier chain: a late
//  `.modifier(...)` cannot reshape the container after the fact. Callers
//  therefore read `containerDecision(for:)` up-front.
//

import SwiftUI

enum FlexboxApplier {

    // MARK: - Container decision

    /// Derives the SwiftUI container kind + alignment for `aggregate`.
    /// Only the flexbox subset is populated here (display != grid/none).
    /// The grid and position decisions are produced by sibling appliers
    /// in Phase 7 steps 3+4; this function returns a sensible flex default
    /// when the aggregate's `display` is unset or non-flex so the renderer
    /// can still route through LayoutApplier without a second branch.
    static func containerDecision(for aggregate: LayoutAggregate) -> ContainerDecision {
        // Grid / none / contents are out of this applier's scope — let
        // the sibling appliers own them. We return a safe vertical-stack
        // default so the renderer's call site never has to nil-check.
        switch aggregate.display {
        case .none:
            // `display: none` shortcuts to "no container"; the renderer
            // also checks this directly and emits EmptyView.
            return ContainerDecision(kind: .none, alignment: .start, spacing: nil)
        case .grid, .inline, .block, .contents, .none?:
            // Not our job — fall through to the default below so the
            // sibling grid/position/block appliers can take over.
            break
        case .flex, nil:
            // Flex container (or flex-only sibling properties set on a
            // container whose display was omitted — common in IR emitted
            // from Tailwind-style CSS that relies on default display:flex
            // being inferred from flex-direction being present).
            break
        }

        // Axis defaults to horizontal for flex (CSS initial value is row);
        // VStack → block display falls through to the default below.
        let axis: ContainerAxis = {
            switch aggregate.flexDirection {
            case .column, .columnReverse: return .vertical
            case .row, .rowReverse, nil:  return .horizontal
            }
        }()

        // Cross-axis alignment comes from align-items. Initial value per
        // the CSS spec is `stretch`, which we map to `.center` in SwiftUI
        // since SwiftUI has no stretch alignment for stacks — stretch is
        // enforced by .frame(maxWidth/maxHeight:.infinity) on children
        // instead.
        let cross = aggregate.alignItems ?? .stretch

        // FlexWrap → FlowLayout for wrap, plain stack otherwise. iOS 16+
        // always (project deployment target), so the @available branch is
        // safe to use unconditionally.
        if aggregate.display == .flex, aggregate.flexWrap == .wrap || aggregate.flexWrap == .wrapReverse {
            // FlowLayout is a custom Layout — see below. Cast as `.wrap`
            // via the ContainerDecision's `lazyHGrid` slot is wrong, so
            // we introduce a marker by shoving the axis back into `kind`
            // as a stack, then ComponentRenderer branches on
            // `aggregate.flexWrap` itself to pick FlowLayout. This keeps
            // the decision struct lean without a new enum case.
            return ContainerDecision(kind: .stack(axis), alignment: cross, spacing: nil)
        }

        // Fallback: a plain stack of the chosen axis. Spacing is NOT
        // populated here — GapApplier already resolved row/column gap
        // and the renderer passes that directly into the HStack/VStack
        // constructor. Leaving spacing nil prevents double-application.
        return ContainerDecision(kind: .stack(axis), alignment: cross, spacing: nil)
    }

    // MARK: - Child ordering (CSS `order`)

    /// Sort children by their `order` property BEFORE rendering — SwiftUI
    /// has no runtime analogue. Children without an explicit `order` are
    /// treated as the CSS default of 0. Stable sort so siblings with the
    /// same order preserve declaration order (matches the CSS spec).
    static func sorted(_ children: [IRComponent]) -> [IRComponent] {
        // Short-circuit: if no child carries an Order property, return
        // the input unchanged — cheaper than an O(n log n) sort that
        // would produce the same array.
        let anyOrdered = children.contains { child in
            child.properties.contains { $0.type == "Order" }
        }
        guard anyOrdered else { return children }

        // enumerated() gives us a stable tiebreaker for children with
        // equal `order` — Swift's `sort` is not stable so we sort pairs.
        return children.enumerated()
            .sorted { (a, b) in
                let oa = orderOf(a.element) ?? 0
                let ob = orderOf(b.element) ?? 0
                if oa != ob { return oa < ob }
                // Equal order → preserve original index (stability).
                return a.offset < b.offset
            }
            .map { $0.element }
    }

    /// Reads the raw integer Order value out of a child's property list.
    /// Nil when unset — caller treats this as the CSS default (0).
    private static func orderOf(_ child: IRComponent) -> Int? {
        for p in child.properties where p.type == "Order" {
            if let i = ValueExtractors.extractInt(p.data) { return i }
        }
        return nil
    }

    // MARK: - Per-child modifier (align-self / flex-basis / flex-grow)

    /// Produces a modifier that overrides the parent's cross-axis
    /// alignment for a single child (CSS `align-self`) and applies
    /// flex-basis + flex-grow hints. Consumed by ComponentRenderer as
    /// `child.modifier(FlexboxApplier.childModifier(for:parent:))`.
    static func childModifier(for child: LayoutAggregate, parent: LayoutAggregate) -> some ViewModifier {
        FlexChildModifier(child: child, parent: parent)
    }
}

// MARK: - FlexChildModifier

/// Per-child flexbox decoration:
///   • `align-self` overrides parent `align-items` on the cross axis
///     via `.frame(maxWidth:.infinity, alignment:…)` (the SwiftUI
///     idiom for "stretch the cell, anchor myself inside it").
///   • `flex-basis: <length>` → .frame(width:) or .frame(height:)
///     depending on the parent's axis.
///   • `flex-grow > 0` → .layoutPriority(grow) so children with a
///     higher grow ratio win intrinsic-size negotiation. This is a
///     coarse approximation — true CSS flex-grow distributes leftover
///     free space proportionally, which SwiftUI's layout system can't
///     express without a custom Layout. TODO: real semantics.
///   • `flex-shrink` has no SwiftUI analogue — TODO logged, no-op.
private struct FlexChildModifier: ViewModifier {
    /// Child aggregate (owns align-self / flex-basis / flex-grow/-shrink).
    let child: LayoutAggregate
    /// Parent aggregate — needed to know the main axis for flex-basis.
    let parent: LayoutAggregate

    func body(content: Content) -> some View {
        // Resolve the parent's main axis — defaults to horizontal like
        // CSS's initial `flex-direction: row`.
        let parentAxis: ContainerAxis = {
            switch parent.flexDirection {
            case .column, .columnReverse: return .vertical
            case .row, .rowReverse, nil:  return .horizontal
            }
        }()

        // Apply flex-basis first — fixes the main-axis size before grow
        // priority kicks in. `.auto` and `.content` are no-ops; SwiftUI
        // derives intrinsic main-axis size from the child itself.
        let basisApplied: AnyView = {
            if case .px(let px) = child.flexBasis {
                switch parentAxis {
                case .horizontal:
                    return AnyView(content.frame(width: px))
                case .vertical:
                    return AnyView(content.frame(height: px))
                }
            }
            return AnyView(content)
        }()

        // align-self override. SwiftUI has no per-child cross alignment
        // on stacks; the `.frame(maxCrossAxis: .infinity, alignment: ..)`
        // idiom approximates it. `.auto` (CSS initial) means "inherit
        // parent" — we emit no override in that case.
        let aligned: AnyView = {
            guard let sa = child.alignSelf, sa != .auto, sa != .normal else {
                return basisApplied
            }
            // Map cross-axis align to a SwiftUI Alignment. For horizontal
            // parents the cross axis is vertical, and vice versa.
            let alignment: Alignment = crossAxisAlignment(sa, parentAxis: parentAxis)
            switch parentAxis {
            case .horizontal:
                return AnyView(basisApplied.frame(maxHeight: .infinity, alignment: alignment))
            case .vertical:
                return AnyView(basisApplied.frame(maxWidth: .infinity, alignment: alignment))
            }
        }()

        // flex-grow → layoutPriority. Not strictly correct but matches
        // the developer intent of "bigger grow ⇒ take more space" in
        // common cases. TODO: implement true grow semantics via Layout.
        if let grow = child.flexGrow, grow > 0 {
            aligned.layoutPriority(grow)
        } else {
            aligned
        }
        // NOTE: flex-shrink is deliberately NOT acted on — SwiftUI
        // shrinks greedily by default. Honouring a shrink != 1 would
        // require a custom Layout; logged as TODO in the file header.
    }

    /// Map the shared AlignmentKeyword to a SwiftUI `Alignment` on the
    /// cross axis of the given parent axis.
    private func crossAxisAlignment(_ kw: AlignmentKeyword, parentAxis: ContainerAxis) -> Alignment {
        switch parentAxis {
        case .horizontal:
            // Cross axis is vertical.
            switch kw {
            case .start, .selfStart:   return .top
            case .end, .selfEnd:       return .bottom
            case .center:              return .center
            case .baseline:            return .top  // SwiftUI lacks .baseline in Alignment
            case .stretch:             return .center  // stretch is done via maxHeight, not alignment
            default:                   return .center
            }
        case .vertical:
            // Cross axis is horizontal.
            switch kw {
            case .start, .selfStart:   return .leading
            case .end, .selfEnd:       return .trailing
            case .center:              return .center
            case .stretch:             return .leading
            default:                   return .leading
            }
        }
    }
}

// MARK: - FlowLayout (flex-wrap)

/// Minimal wrapping-row Layout implementation for `flex-wrap: wrap`.
/// SwiftUI's stacks don't wrap; this custom `Layout` measures each
/// subview, and breaks to a new line when the current line would exceed
/// the proposed width. No cross-axis stretching — every child is placed
/// at its intrinsic size, lines are left-aligned, gaps are horizontal
/// and vertical spacing inputs. This is the minimum viable FlowLayout
/// called out in the Phase 7 spec; richer behaviour (justify-content
/// per line, wrap-reverse, align-content across lines) is a TODO.
@available(iOS 16.0, *)
struct FlowLayout: Layout {
    /// Horizontal gap between items on a single line, in points.
    var horizontalSpacing: CGFloat = 0
    /// Vertical gap between wrapped lines, in points.
    var verticalSpacing: CGFloat = 0

    /// Measures the total wrapped size given the proposed width. When
    /// width is nil/infinite we fall back to laying everything out on a
    /// single line — matches the CSS `nowrap` behaviour.
    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        // Prefer the replacement value SwiftUI passes ("replacingUnspecifiedDimensions"
        // gives a sane width when the proposal is .unspecified — typically
        // the container's ideal size).
        let maxWidth = proposal.replacingUnspecifiedDimensions().width
        var currentLineWidth: CGFloat = 0
        var currentLineHeight: CGFloat = 0
        var totalHeight: CGFloat = 0
        var maxUsedWidth: CGFloat = 0

        for subview in subviews {
            // Ask the subview for its ideal size at unspecified proposal
            // — gives us intrinsic width/height like CSS `flex-basis: auto`.
            let size = subview.sizeThatFits(.unspecified)
            // If this item would overflow, wrap to the next line.
            let wouldOverflow = currentLineWidth + (currentLineWidth > 0 ? horizontalSpacing : 0) + size.width > maxWidth
            if wouldOverflow && currentLineWidth > 0 {
                // Commit the finished line.
                totalHeight += currentLineHeight + verticalSpacing
                maxUsedWidth = max(maxUsedWidth, currentLineWidth)
                currentLineWidth = 0
                currentLineHeight = 0
            }
            // Accumulate this item onto the current line.
            currentLineWidth += (currentLineWidth > 0 ? horizontalSpacing : 0) + size.width
            currentLineHeight = max(currentLineHeight, size.height)
        }
        // Commit the final line.
        totalHeight += currentLineHeight
        maxUsedWidth = max(maxUsedWidth, currentLineWidth)
        return CGSize(width: maxUsedWidth, height: totalHeight)
    }

    /// Place subviews in wrapping-row order. Coordinates are relative to
    /// the bounds origin SwiftUI hands us.
    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        let maxWidth = bounds.width
        var x: CGFloat = bounds.minX
        var y: CGFloat = bounds.minY
        var currentLineHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            // Wrap-decision mirror of sizeThatFits — keep the two in sync.
            if x + (x > bounds.minX ? horizontalSpacing : 0) + size.width > bounds.minX + maxWidth && x > bounds.minX {
                // Start a new line at the left edge.
                y += currentLineHeight + verticalSpacing
                x = bounds.minX
                currentLineHeight = 0
            }
            // Insert horizontal spacing between items (but not before the first).
            if x > bounds.minX {
                x += horizontalSpacing
            }
            // `.at: x, y` places the top-leading corner; proposal gives
            // the subview its intrinsic size so intrinsic layout wins.
            subview.place(
                at: CGPoint(x: x, y: y),
                anchor: .topLeading,
                proposal: ProposedViewSize(size)
            )
            x += size.width
            currentLineHeight = max(currentLineHeight, size.height)
        }
    }
}
