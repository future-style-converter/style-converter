//
//  LayoutExtractor.swift
//  StyleEngine/layout — Phase 7, step 1 (scaffold only).
//
//  Facade over every layout triplet extractor. Mirrors `TypographyExtractor`
//  (Phase 6) in shape: one `extract(from:)` entry point returning a
//  populated `LayoutAggregate?`. For THIS step the function returns `nil`
//  unconditionally — no per-triplet extractors exist yet. Steps 2-5 fill
//  in the flexbox, grid, position, advanced, and root groups respectively.
//
//  What this file *does* do today: register the 60 layout property type
//  names with `PropertyRegistry.migrated` so the renderer knows those
//  properties are owned by the layout engine (even if the engine currently
//  no-ops them — the legacy StyleBuilder still handles them in practice
//  because `LayoutApplier.apply(...)` is identity and callers read the
//  migration ledger via `union`-ing the set below).
//
//  Important: the 60 PascalCase type names below mirror the IR type
//  strings produced by `src/main/kotlin/app/parsing/css/properties/
//  longhands/PropertyParserRegistry.kt` (category folders:
//  layout/flexbox, layout/grid, layout/position, layout/advanced, plus
//  the four "root" longhands). If a name fails to parse on the CSS side,
//  the registration here is harmless — it just flags a non-existent
//  property as owned.
//

import Foundation

// MARK: - Property-name groupings (public so PropertyRegistry can union them)

/// Flexbox family — 12 longhands under layout/flexbox/.
/// Source of truth: irmodels/properties/layout/flexbox/ and the matching
/// parser folder.
enum LayoutFlexboxProperty {
    /// Exhaustive name set for Phase 7 step 2 (flexbox).
    static let set: Set<String> = [
        // Container-level flex keywords.
        "Display", "FlexDirection", "FlexWrap",
        // Per-item flex sizing.
        "FlexGrow", "FlexShrink", "FlexBasis",
        // Main/cross-axis alignment.
        "JustifyContent", "AlignItems", "AlignContent", "AlignSelf",
        // Child ordering.
        "Order",
        // Legacy flex-box keyword retained in the IR for fidelity.
        "BoxOrient",
    ]
}

/// Grid family — 18 longhands under layout/grid/.
enum LayoutGridProperty {
    /// Exhaustive name set for Phase 7 step 3 (grid).
    static let set: Set<String> = [
        // Explicit track templates.
        "GridTemplateColumns", "GridTemplateRows", "GridTemplateAreas",
        // Implicit (auto) tracks.
        "GridAutoColumns", "GridAutoRows", "GridAutoFlow",
        // IR also carries a unified GridAutoTrack for parser convenience.
        "GridAutoTrack",
        // Item placement.
        "GridArea",
        "GridColumnStart", "GridColumnEnd",
        "GridRowStart",    "GridRowEnd",
        // Grid-level alignment (justify-items / justify-self are grid-only
        // in practice — kept here so they co-locate with track setup).
        "JustifyItems", "JustifySelf",
        // Multi-track alignment (rare; spec-defined, rarely implemented).
        "AlignTracks", "JustifyTracks",
        // Masonry (CSS Grid Level 3 draft).
        "MasonryAutoFlow",
        // `grid-template` shorthand's IR form.
        "GridTemplate",
    ]
}

/// Position family — 10 longhands under layout/position/.
enum LayoutPositionProperty {
    /// Exhaustive name set for Phase 7 step 4 (position + inset).
    static let set: Set<String> = [
        // Core scheme selector.
        "Position",
        // Physical inset longhands.
        "Top", "Right", "Bottom", "Left",
        // Logical inset longhands (resolved against writing-mode later).
        "InsetBlockStart", "InsetBlockEnd",
        "InsetInlineStart", "InsetInlineEnd",
        // Paint-order.
        "ZIndex",
    ]
}

/// Advanced positioning / motion-path family — 17 longhands under
/// layout/advanced/. CSS Anchor Positioning + Motion Path modules.
enum LayoutAdvancedProperty {
    /// Exhaustive name set for Phase 7 step 5 (advanced). SwiftUI has no
    /// direct analogue for most of these — the eventual applier will log
    /// TODOs, but registering the names here keeps coverage honest.
    static let set: Set<String> = [
        // CSS Anchor Positioning.
        "AnchorName", "AnchorScope", "InsetArea",
        // CSS Motion Path.
        "OffsetPath", "OffsetDistance", "OffsetAnchor",
        "OffsetPosition", "OffsetRotate",
        // `offset` shorthand IR form.
        "Offset",
        // Anchor Positioning fallback chain.
        "PositionAnchor", "PositionArea",
        "PositionFallback",
        "PositionTry", "PositionTryFallbacks",
        "PositionTryOptions", "PositionTryOrder",
        "PositionVisibility",
    ]
}

/// Root / flow family — 4 top-level longhands that don't fit flex/grid/
/// position. `Overlay` + `ReadingFlow` are CSS Display Level 4 additions.
enum LayoutRootProperty {
    /// Exhaustive name set for Phase 7 step 5 (root).
    static let set: Set<String> = [
        // Classic float model — rarely used in SDUI content.
        "Clear", "Float",
        // CSS Display Level 4 — flow overlay.
        "Overlay",
        // Accessibility-driven reading-order override.
        "ReadingFlow",
    ]
}

/// Union of all five layout groups — 60 property type names total.
/// `PropertyRegistry.migrated` picks this up via `.union(...)`.
enum LayoutProperty {
    /// All layout-family property type names. The count is asserted by
    /// `LayoutSelfTest.run()` to catch accidental drift.
    static let set: Set<String> =
        LayoutFlexboxProperty.set
            .union(LayoutGridProperty.set)
            .union(LayoutPositionProperty.set)
            .union(LayoutAdvancedProperty.set)
            .union(LayoutRootProperty.set)
}

// MARK: - Extractor facade

enum LayoutExtractor {

    /// Runs every (future) layout extractor and folds them into one
    /// aggregate. Returns nil when no layout property was seen — this
    /// lets `LayoutApplier` short-circuit.
    ///
    /// STEP 1 IMPLEMENTATION: no extractors exist yet, so this always
    /// returns nil. Steps 2-5 will replace this with the typography-style
    /// `Applier.contribute(Extractor.extract(from:), into: &agg)` chain.
    /// Keeping the signature stable now means downstream callers
    /// (StyleBuilder / ComponentRenderer) can be wired in step 2 without
    /// churn.
    static func extract(from properties: [IRProperty]) -> LayoutAggregate? {
        // Start with an empty aggregate; each sub-extractor folds in
        // its own fields and flips `touched` when it writes.
        var agg = LayoutAggregate()

        // Step 2 — flexbox family. 11 properties are read
        // here; BoxOrient is intentionally skipped per task brief.
        FlexboxExtractor.extract(from: properties, into: &agg)

        // Step 3 — grid family (GridTemplate*/GridAuto*/GridArea +
        // JustifyItems/JustifySelf). Writes tracks/areas/flow/placement
        // into the aggregate's grid-* slots.
        GridExtractor.contribute(properties, into: &agg)

        // Step 4 — position family (Position/Top/Right/Bottom/Left +
        // InsetBlock*/InsetInline* + ZIndex). Resolves logical insets
        // to physical sides against the default (LTR) layout direction.
        PositionExtractor.contribute(properties, into: &agg)

        // Steps 5 (advanced / root) plug in next to this call —
        // parallel agents own those scopes.

        // Return nil when nothing wrote — lets LayoutApplier short-
        // circuit and leaves the renderer's legacy fallback untouched.
        return agg.touched ? agg : nil
    }
}
