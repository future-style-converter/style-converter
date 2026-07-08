//
//  LayoutAggregate.swift
//  StyleEngine/layout — Phase 7, step 1 (scaffold only).
//
//  Layout is the second family (after typography) that cannot be expressed
//  as "one modifier per property": `display` interacts with `flex-direction`,
//  `justify-content`, `align-items`, `flex-wrap` to choose an HStack vs
//  VStack vs LazyVGrid/LazyHGrid *container*; `position` + `inset-*`
//  interacts with the parent ZStack; `grid-template-*` + `grid-area` needs
//  a coordinated decision at container-construction time. So — like the
//  typography aggregate — every layout extractor folds into this shared
//  struct and a single container-builder reads it.
//
//  THIS FILE IS SCAFFOLD. No extractor populates it yet; every field is
//  Optional / stub. The shape is frozen here so downstream steps (2-5)
//  can add extractors without re-shuffling public types.
//

import SwiftUI

// MARK: - Keyword enums (shells — flesh out as each step lands)

/// `flex-direction` keyword set. CSS: row | row-reverse | column |
/// column-reverse. Drives the SwiftUI container axis choice (H vs V) and
/// whether the child order is reversed at container-build time.
enum FlexDirectionKeyword: Equatable {
    /// Default — horizontal, leading→trailing.
    case row
    /// Horizontal, trailing→leading. Implemented by reversing children.
    case rowReverse
    /// Vertical, top→bottom.
    case column
    /// Vertical, bottom→top. Implemented by reversing children.
    case columnReverse
}

/// `flex-wrap` keyword set. CSS: nowrap | wrap | wrap-reverse. SwiftUI
/// has no native flex-wrap; the container builder will approximate with
/// a `FlowLayout` iOS 16+ custom layout in step 2.
enum FlexWrapKeyword: Equatable {
    /// Default — all items on one line.
    case nowrap
    /// Wrap to next line; lines flow top→bottom.
    case wrap
    /// Wrap to next line; lines flow bottom→top.
    case wrapReverse
}

/// `display` keyword set — the container-type selector. Parallel to the
/// Android `DisplayKeyword` enum under `layout/flexbox/DisplayConfig.kt`.
/// Only the subset SwiftUI can represent is modelled.
enum DisplayKeyword: Equatable {
    /// Default block-level box — rendered as a VStack leading-aligned.
    case block
    /// Inline flow — approximated as HStack; full-spec inline run
    /// reconstruction is out of scope for the mobile runtime.
    case inline
    /// `display: flex` — main axis from `flex-direction`.
    case flex
    /// `display: grid` — delegates to LazyVGrid/LazyHGrid.
    case grid
    /// `display: none` — child is omitted from the tree. Short-circuits
    /// rendering entirely in ComponentRenderer when seen.
    case none
    /// `display: contents` — child is hoisted into the parent. Approximated
    /// by rendering children inline without a wrapper.
    case contents
}

/// Shared CSS alignment keyword space covering `justify-content`,
/// `align-items`, `align-content`, `align-self`, `justify-items`,
/// `justify-self`. Not every keyword is valid on every property at the
/// CSS spec level, but keeping one enum keeps the aggregate narrow.
enum AlignmentKeyword: Equatable {
    /// CSS `start` / `flex-start` / `left` → leading alignment.
    case start
    /// CSS `end` / `flex-end` / `right` → trailing alignment.
    case end
    /// CSS `center` → centre alignment.
    case center
    /// CSS `stretch` → fills the cross axis (container dependent).
    case stretch
    /// CSS `baseline` — SwiftUI `.firstTextBaseline`.
    case baseline
    /// CSS `space-between` — gap distributed between items, edges flush.
    case spaceBetween
    /// CSS `space-around` — gap around each item, half gap at edges.
    case spaceAround
    /// CSS `space-evenly` — equal gap between + at edges.
    case spaceEvenly
    /// CSS `self-start` (align-self / justify-self only).
    case selfStart
    /// CSS `self-end` (align-self / justify-self only).
    case selfEnd
    /// CSS `auto` (align-self / justify-self default).
    case auto
    /// CSS `normal` (initial value for many alignment longhands).
    case normal
}

/// `position` keyword set. SwiftUI analogues:
/// static → no-op; relative → `.offset(...)`; absolute/fixed →
/// ZStack child with `.position(...)`; sticky → scroll-view overlay (TODO).
enum PositionKind: Equatable {
    /// CSS default — element flows normally.
    case staticPos
    /// `relative` — offsets from normal position, still takes flow space.
    case relative
    /// `absolute` — positioned against nearest positioned ancestor ZStack.
    case absolute
    /// `fixed` — positioned against viewport / root ZStack.
    case fixed
    /// `sticky` — follows scroll until threshold crossed. SwiftUI stub.
    case sticky
}

/// `clear` keyword set. Floats are rarely used in SDUI content — tracked
/// so we can faithfully reject content that depends on float/clear layout.
enum ClearKeyword: Equatable {
    /// `none` — default.
    case none
    /// `left`.
    case left
    /// `right`.
    case right
    /// `both`.
    case both
    /// `inline-start`.
    case inlineStart
    /// `inline-end`.
    case inlineEnd
}

/// `float` keyword set. Same rationale as `ClearKeyword`.
enum FloatKeyword: Equatable {
    /// `none` — default.
    case none
    /// `left`.
    case left
    /// `right`.
    case right
    /// `inline-start`.
    case inlineStart
    /// `inline-end`.
    case inlineEnd
}

/// `grid-auto-flow` keyword set — determines auto-placement direction.
enum GridAutoFlowKeyword: Equatable {
    /// `row` — default, fill rows first.
    case row
    /// `column` — fill columns first.
    case column
    /// `row dense` — row + dense-packing backfill.
    case rowDense
    /// `column dense` — column + dense-packing backfill.
    case columnDense
}

// MARK: - Value structs (shells)

/// `flex-basis` accepts a length, `auto`, or `content`. SwiftUI doesn't
/// distinguish, but the container builder needs the three cases to map
/// correctly (length → fixed frame, auto → no override, content → intrinsic).
enum FlexBasisValue: Equatable {
    /// Absolute pixel length (post CSS → px normalisation).
    case px(CGFloat)
    /// `auto` — main-size from width/height or content.
    case auto
    /// `content` — main-size strictly from content, ignore width/height.
    case content
}

/// One grid track (size entry) in `grid-template-columns` / `-rows`.
/// Step 3 widens this beyond the scaffold's bare `px` to carry every
/// track variant the CSS parser emits: fr weights, auto, percent,
/// minmax(a,b), and adaptive (repeat(auto-fill|auto-fit, minmax(...))).
struct GridTrack: Equatable {
    /// Discriminated union of supported track shapes. Kept as a nested
    /// enum so the applier's switch is exhaustive.
    enum Kind: Equatable {
        /// `40px` — absolute fixed size in points.
        case fixed(px: CGFloat)
        /// `1fr` / `2fr` — flexible weight, sums across the track list.
        case flexible(weight: CGFloat)
        /// `auto` — sized to content. SwiftUI → GridItem(.flexible()).
        case automatic
        /// `N%` — percentage of parent width. SwiftUI has no direct
        /// percent GridItem; applier converts to a fixed size only when
        /// the parent width is known at render time.
        case percent(CGFloat)
        /// `minmax(a, b)` — raw bounds captured. Applier picks the
        /// closest GridItem (adaptive or flexible).
        case minmax(min: CGFloat?, max: CGFloat?)
        /// `repeat(auto-fill|auto-fit, minmax(a, b))` — collapses to
        /// a single GridItem(.adaptive(minimum: a, maximum: b)).
        case adaptive(min: CGFloat?, max: CGFloat?)
    }

    /// The kind of track. Phase 7 step 3 initialises this directly; the
    /// scaffold used a bare optional `px` field — the `fixed(px:)` case
    /// is the migration target.
    var kind: Kind
}

/// `grid-template-columns` / `grid-template-rows` list. Shell today.
struct GridTrackList: Equatable {
    /// Tracks in declared order. Repeat() expansion happens in the extractor.
    var tracks: [GridTrack]
}

/// `inset-*` / top/right/bottom/left bundle. All optional — a `nil` side
/// means CSS `auto`, which SwiftUI encodes as "don't constrain that edge".
struct InsetRect: Equatable {
    /// Top offset in points from the parent positioning context.
    var top: CGFloat?
    /// Right offset (resolved to trailing post layoutDirection).
    var right: CGFloat?
    /// Bottom offset in points.
    var bottom: CGFloat?
    /// Left offset (resolved to leading post layoutDirection).
    var left: CGFloat?
}

/// `grid-area` / `grid-column` / `grid-row` line references. Step 3
/// expands this to handle spans and named lines.
struct GridLine: Equatable {
    /// Integer line index (1-based per CSS).
    var line: Int?
    /// Span count when `span N` was specified.
    var span: Int?
    /// Named line reference.
    var name: String?
}

// MARK: - The aggregate

/// Rolled-up layout state produced by the Phase 7 extractors.
///
/// Scaffold only in step 1: every field is nil-default and no extractor
/// writes to it. The container builder reads `containerDecision()` to
/// pick a SwiftUI stack/grid; leaf modifiers (offset / zIndex / position)
/// are applied per-child by `LayoutApplier.apply(...)`.
struct LayoutAggregate: Equatable {

    // MARK: - Flexbox axes

    /// `display` — selects the container kind.
    var display: DisplayKeyword? = nil
    /// `flex-direction` — main-axis direction within a flex container.
    var flexDirection: FlexDirectionKeyword? = nil
    /// `flex-wrap` — whether children wrap onto multiple lines.
    var flexWrap: FlexWrapKeyword? = nil

    // MARK: - Flex item sizing

    /// `flex-grow` — 0-based ratio for distributing free main-axis space.
    var flexGrow: Double? = nil
    /// `flex-shrink` — 0-based ratio for absorbing overflow.
    var flexShrink: Double? = nil
    /// `flex-basis` — initial main-axis size before grow/shrink apply.
    var flexBasis: FlexBasisValue? = nil
    /// `order` — integer reorder key; default 0. Nil = unset.
    var order: Int? = nil

    // MARK: - Alignment (container + item)

    /// `justify-content` — main-axis packing in flex/grid containers.
    var justifyContent: AlignmentKeyword? = nil
    /// `align-items` — cross-axis alignment default for every child.
    var alignItems: AlignmentKeyword? = nil
    /// `align-content` — multi-line cross-axis distribution.
    var alignContent: AlignmentKeyword? = nil
    /// `align-self` — cross-axis alignment override for this child.
    var alignSelf: AlignmentKeyword? = nil
    /// `justify-items` — inline-axis default alignment (grid containers).
    var justifyItems: AlignmentKeyword? = nil
    /// `justify-self` — inline-axis override for this child.
    var justifySelf: AlignmentKeyword? = nil

    // MARK: - Grid tracks + placement

    /// `grid-template-columns` — explicit column track sizes.
    var gridTemplateColumns: GridTrackList? = nil
    /// `grid-template-rows` — explicit row track sizes.
    var gridTemplateRows: GridTrackList? = nil
    /// `grid-template-areas` — 2D name grid for `grid-area` lookup.
    var gridTemplateAreas: [[String]]? = nil
    /// `grid-auto-columns` — implicit track size for columns.
    var gridAutoColumns: GridTrackList? = nil
    /// `grid-auto-rows` — implicit track size for rows.
    var gridAutoRows: GridTrackList? = nil
    /// `grid-auto-flow` — auto-placement algorithm direction.
    var gridAutoFlow: GridAutoFlowKeyword? = nil
    /// `grid-area` — shorthand placement reference (name or 4 lines).
    var gridArea: GridLine? = nil
    /// `grid-column` — inline-axis line placement.
    var gridColumn: GridLine? = nil
    /// `grid-row` — block-axis line placement.
    var gridRow: GridLine? = nil

    // MARK: - Position + inset

    /// `position` — positioning scheme selector.
    var position: PositionKind? = nil
    /// Resolved inset rect from top/right/bottom/left + logical insets.
    var inset: InsetRect? = nil
    /// `z-index` — nil encodes CSS `auto` (default paint order).
    var zIndex: Double? = nil

    // MARK: - Root / flow (rare on mobile)

    /// `clear` — CSS float-clearance keyword. Usually no-op on mobile.
    var clear: ClearKeyword? = nil
    /// `float` — CSS float keyword. Usually no-op on mobile.
    var float: FloatKeyword? = nil

    // MARK: - Touch flag

    /// True when at least one Phase 7 extractor wrote into the aggregate.
    /// Mirrors the `TypographyAggregate.touched` short-circuit pattern —
    /// lets `LayoutApplier` return the view untouched on `nil`/unchanged.
    var touched: Bool = false
}

// MARK: - Container decision

/// The axis a SwiftUI stack lays out along. Separate from the CSS keyword
/// enums so the container builder can be written against SwiftUI terms.
enum ContainerAxis: Equatable {
    /// Horizontal — HStack.
    case horizontal
    /// Vertical — VStack.
    case vertical
}

/// Nested decision describing "what SwiftUI container should wrap the
/// children of this component, and how should it be configured?". The
/// container builder in step 2 will compute this from a populated
/// aggregate. For step 1 the scaffold provides a safe `default` so
/// callers can reference the type.
struct ContainerDecision: Equatable {

    /// The shape of the container itself.
    enum ContainerKind: Equatable {
        /// HStack / VStack along the given axis.
        case stack(ContainerAxis)
        /// ZStack — used for absolute/fixed positioning.
        case zstack
        /// LazyVGrid for column-based grids.
        case lazyVGrid
        /// LazyHGrid for row-based grids.
        case lazyHGrid
        /// iOS 16+ `Grid` / `GridRow` — used for `grid-template-areas`
        /// layouts which need spanning cells that LazyVGrid can't express.
        case grid
        /// No container — children rendered inline (display: contents /
        /// display: none short-circuit).
        case none
    }

    /// The chosen container shape.
    var kind: ContainerKind
    /// Cross-axis alignment passed to the stack/grid initialiser.
    var alignment: AlignmentKeyword
    /// `gap` / `row-gap` / `column-gap` resolved to points. Nil = default.
    var spacing: CGFloat?
    /// True when at least one child has `position: absolute | fixed`, which
    /// forces the parent to be a ZStack(alignment: .topLeading) so the
    /// child's .offset(...) anchors at the top-left of the parent bounds.
    /// Phase 7 step 4 — set by the ComponentRenderer after inspecting
    /// children; PositionApplier.needsZStackWrap(forChildren:) computes it.
    var needsZStackWrap: Bool = false

    /// Safe default used by callers during the scaffold phase: a plain
    /// vertical stack, leading alignment, no explicit spacing. Matches
    /// the current `ComponentRenderer` fallback layout.
    static let `default` = ContainerDecision(
        kind: .stack(.vertical),
        alignment: .start,
        spacing: nil,
        needsZStackWrap: false
    )
}
