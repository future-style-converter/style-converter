//
//  ItemPlacement.swift
//  StyleEngine/core/placement — IR v2 Slot & Placement contract.
//
//  The child-carried parent-data union of design §2.2: every component
//  packages its ITEM-scoped declarations (align-self, order, flex-*,
//  grid placement, z-index — the frozen PropertyScope.item list) into
//  ONE ItemPlacement value that rides a SwiftUI LayoutValueKey. The
//  container's layout implementation reads each arriving child's
//  placement and consumes ONLY the block matching its own kind
//  (CSSFlexLayout → flex, CSSGridLayout → grid); blocks for other
//  container kinds are inert data — exactly `grid-area` on a flex child
//  in a browser.
//
//  This is what keeps engines composition-agnostic: the child never
//  knows its destination (ComponentHost attaches placement
//  unconditionally — inert under a plain VStack), and the container
//  never knows its arrivals until measure time (LayoutValueKey values
//  arrive WITH the subviews).
//

import SwiftUI

// MARK: - The placement union

/// Per-component placement parent-data (design §2.2 union). Extracted
/// once per component by ItemPlacementExtractor from the component's OWN
/// properties — no parent participation.
struct ItemPlacement: Equatable {

    /// Grid-container claims (css-grid-1 §8): explicit line placement +
    /// spans + per-axis self-alignment. Line/span semantics identical to
    /// GridItemRequest (1-based lines, nil = auto).
    struct GridClaim: Equatable {
        /// Placement request consumed by CSSGridLayout's placer.
        var request = GridItemRequest()
        /// `justify-self` — inline-axis alignment inside the cell.
        var justifySelf: AlignmentKeyword? = nil
        /// `align-self` — block-axis alignment inside the cell.
        var alignSelf: AlignmentKeyword? = nil
    }

    /// Flex-container claims (css-flexbox-1): the exact inputs the
    /// former FlexItemSpec carried, now sourced from the child itself.
    struct FlexClaim: Equatable {
        /// `flex-grow` — share of positive free space (CSS initial 0).
        var grow: CGFloat = 0
        /// `flex-shrink` — scaled share of negative free space (initial 1).
        var shrink: CGFloat = 1
        /// `flex-basis: <length>` in px; nil = auto/content → intrinsic.
        var basisPx: CGFloat? = nil
        /// Wave 48 (lane W7) — `flex-basis: <percentage>` as the raw
        /// percent (100 = 100%). Resolved against the container's MAIN
        /// content size by the consuming layout (css-flexbox-1 §7.2.3) —
        /// the child cannot know its container's axis or size, so the
        /// claim rides unresolved, exactly like the grid claims do.
        var basisPercent: CGFloat? = nil
        /// `align-self` override; nil/auto → container align-items.
        var alignSelf: AlignmentKeyword? = nil
    }

    /// Paint-order claim (design §4.3): z-index is ITEM-scoped. Carried
    /// for contract completeness/introspection; the visual effect is
    /// applied by the component's own PositionApplier (SwiftUI .zIndex
    /// operates among siblings wherever the view lands), so container
    /// layouts do not consume this block.
    struct PaintClaim: Equatable {
        /// `z-index` integer value; nil = auto.
        var zIndex: Double? = nil
    }

    /// Grid block — read only by CSSGridLayout.
    var grid = GridClaim()
    /// Flex block — read only by CSSFlexLayout.
    var flex = FlexClaim()
    /// Paint block — informational (see PaintClaim doc).
    var paint = PaintClaim()
    /// Axis-agnostic size FACTS (not requests): whether the IR declared
    /// an explicit inline/block size. The consuming container resolves
    /// "cross-axis auto?" against its OWN axis (css-flexbox-1 §8.3
    /// stretch precondition) — the child cannot know which axis is cross.
    var explicitWidth = false
    /// True when the IR declared Height/BlockSize (see explicitWidth).
    var explicitHeight = false
}

// MARK: - LayoutValueKey channel

/// The parent-data channel (design §3.2). Default nil marks an ANONYMOUS
/// subview — e.g. the harness's leading `_text` placeholder — which is
/// not a component and auto-places with start alignment in grid (the
/// pre-v2 renderer behaviour) and CSS-initial claims in flex.
struct ItemPlacementKey: LayoutValueKey {
    /// nil = no component behind this subview (anonymous item).
    static let defaultValue: ItemPlacement? = nil
}

extension View {
    /// Attach a component's placement parent-data for whatever container
    /// it lands in. Inert unless the direct Layout parent reads the key.
    func stylePlacement(_ placement: ItemPlacement?) -> some View {
        layoutValue(key: ItemPlacementKey.self, value: placement)
    }
}

// MARK: - Extractor

/// IRProperty list → ItemPlacement. One extraction per component,
/// invoked by ComponentHost (render path) and by the renderer's static
/// grid-stretch pre-pass, so both consumers provably share one source.
enum ItemPlacementExtractor {

    /// Extract every ITEM-scoped claim from a component's own properties.
    /// Never fails — absent declarations leave CSS-initial defaults.
    static func extract(from properties: [IRProperty]) -> ItemPlacement {
        var p = ItemPlacement()
        // Grid + paint claims ride the full layout aggregate — the same
        // extractor lane the renderer's gridPlan used pre-v2, so line/
        // span/self-alignment values are bit-identical to the old path.
        if let agg = LayoutExtractor.extract(from: properties) {
            // Placement lines are 1-based; spans ride on either longhand
            // (`grid-column-start: span 2` / `grid-column-end: span 2`).
            p.grid.request.colStart = agg.gridColumnStart?.line
            p.grid.request.colEnd   = agg.gridColumnEnd?.line
            p.grid.request.rowStart = agg.gridRowStart?.line
            p.grid.request.rowEnd   = agg.gridRowEnd?.line
            p.grid.request.colSpan  = agg.gridColumnStart?.span ?? agg.gridColumnEnd?.span
            p.grid.request.rowSpan  = agg.gridRowStart?.span ?? agg.gridRowEnd?.span
            // NAMED lines ride the claim raw (css-grid-1 §8.3) — e.g.
            // `grid-area: media` reaches us as GridRowStart{name:"media"}.
            // Only the container can turn a name into a line number (it
            // owns grid-template-areas), so resolution happens inside
            // CSSGridLayout via GridPlacer.resolveNames at measure time.
            p.grid.request.colStartName = agg.gridColumnStart?.name
            p.grid.request.colEndName   = agg.gridColumnEnd?.name
            p.grid.request.rowStartName = agg.gridRowStart?.name
            p.grid.request.rowEndName   = agg.gridRowEnd?.name
            // Self-alignment keywords raw — resolution against the
            // container's *-items defaults happens INSIDE the layout
            // (css-align-3 §6), which is the only party that knows them.
            p.grid.justifySelf = agg.justifySelf
            p.grid.alignSelf   = agg.alignSelf
            // Paint claim (informational — see PaintClaim).
            p.paint.zIndex = agg.zIndex
        }
        // Flex claims through the dedicated flexbox lane — identical to
        // the childAgg pass the renderer ran per child pre-v2.
        var flexAgg = LayoutAggregate()
        FlexboxExtractor.extract(from: properties, into: &flexAgg)
        p.flex.grow   = CGFloat(flexAgg.flexGrow ?? 0)
        p.flex.shrink = CGFloat(flexAgg.flexShrink ?? 1)
        // Only a definite px basis is a static claim; auto/content fall
        // back to the subview's intrinsic size at measure time.
        if case .px(let px)? = flexAgg.flexBasis { p.flex.basisPx = px }
        // Wave 48 (lane W7): a percent basis rides unresolved — the
        // consuming layout resolves it against its own main content size
        // (css-flexbox-1 §7.2.3), which the child cannot know here.
        if case .percent(let pct)? = flexAgg.flexBasis { p.flex.basisPercent = pct }
        p.flex.alignSelf = flexAgg.alignSelf
        // Size facts — presence checks identical to the pre-v2 renderer's
        // crossAuto (Width/InlineSize vs Height/BlockSize) and gridPlan's
        // hasHeight stretch precondition.
        p.explicitWidth = properties.contains {
            $0.type == "Width" || $0.type == "InlineSize"
        }
        p.explicitHeight = properties.contains {
            $0.type == "Height" || $0.type == "BlockSize"
        }
        return p
    }
}
