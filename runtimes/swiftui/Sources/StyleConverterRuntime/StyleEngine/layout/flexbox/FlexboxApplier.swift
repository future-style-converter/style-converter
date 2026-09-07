//
//  FlexboxApplier.swift
//  StyleEngine/layout/flexbox — Phase 7 step 2 (flexbox).
//
//  Reads flex fields out of a populated LayoutAggregate and produces:
//    • Per-child modifiers (align-self override, flex-basis sizing,
//      flex-grow priority).
//    • A child-sorter that re-orders a [IRComponent] array by `order`
//      BEFORE rendering — SwiftUI has no equivalent of CSS `order`.
//
//  SwiftUI's stack initialisers consume alignment+spacing at construction
//  time, which is why the CONTAINER choice is not a modifier at all:
//  ComponentRenderer builds the `ContainerDecision.ContainerKind` itself,
//  at container-construction time (`gridKind` / the flex-wrap branch), from
//  the same LayoutAggregate this file reads.
//
//  Retro P2b (finding A6#3): the `containerDecision(for:)` scaffold that
//  used to live here was deleted. It had no production caller — the two
//  call sites were both in LayoutTests — and its `switch aggregate.display`
//  was inverted: `display` is `DisplayKeyword?`, so `case .none` matched
//  NIL (an omitted display returned kind `.none` = "no container") while
//  `display: none` fell through to the flex default, and `case .flex, nil`
//  was unreachable (the Swift 5.9 compiler said so: "case is already
//  handled by previous patterns"). Nothing rendered from it, so nothing
//  moved when it went.
//
//  The wrap container itself (`FlowLayout`) moved to FlowLayout.swift at
//  wave 25 — its §8.4/§9.6 cross-axis pass took this file past the
//  300-line split threshold.
//

import SwiftUI

enum FlexboxApplier {

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
