//
//  ScrollingExtractor.swift
//  StyleEngine/scrolling — Phase 10.
//
//  Walks a property list once and fills a ScrollingConfig. Ownership
//  lives in `ScrollingProperty.set`, which is unioned into
//  `PropertyRegistry.migrated` so coverage introspection sees every
//  name routed through the engine.
//

import Foundation

/// Registry ownership for the non-timeline scrolling family.
///
/// Names mirror the Kotlin parsers under
/// `src/main/kotlin/app/parsing/css/properties/longhands/scrolling/` and
/// the IR-model stems under `app/irmodels/properties/scrolling/`.
enum ScrollingProperty {
    /// Explicit, diff-auditable list — matches the 60-component coverage of
    /// `fixtures/properties/scrolling/longtail.json` minus the 3 scroll-
    /// timeline longhands (already owned by `ScrollTimelineProperty`).
    /// (Retro P2e repointed this: the `README-phase10` index it named was
    /// deleted by the 2026-07-08 restructure, commit 02e4c457.)
    static let names: [String] = [
        // scroll-behavior (interactions/ parser folder, scrolling category).
        "ScrollBehavior",
        // scroll-snap-*.
        "ScrollSnapAlign", "ScrollSnapStop", "ScrollSnapType",
        // scroll-margin-* (4 physical + 4 logical = 8).
        "ScrollMarginTop", "ScrollMarginRight", "ScrollMarginBottom", "ScrollMarginLeft",
        "ScrollMarginBlockStart", "ScrollMarginBlockEnd",
        "ScrollMarginInlineStart", "ScrollMarginInlineEnd",
        // scroll-padding-* (4 physical + 4 logical = 8).
        "ScrollPaddingTop", "ScrollPaddingRight", "ScrollPaddingBottom", "ScrollPaddingLeft",
        "ScrollPaddingBlockStart", "ScrollPaddingBlockEnd",
        "ScrollPaddingInlineStart", "ScrollPaddingInlineEnd",
        // overscroll-behavior(-x/-y/-block/-inline) (5).
        "OverscrollBehavior",
        "OverscrollBehaviorX", "OverscrollBehaviorY",
        "OverscrollBehaviorBlock", "OverscrollBehaviorInline",
        // scrollbar-* (3).
        "ScrollbarColor", "ScrollbarGutter", "ScrollbarWidth",
        // overflow-anchor / overflow-clip-margin (2).
        "OverflowAnchor", "OverflowClipMargin",
        // scroll-start longhands + targets (9).
        "ScrollStart",
        "ScrollStartX", "ScrollStartY",
        "ScrollStartBlock", "ScrollStartInline",
        "ScrollStartTarget",
        "ScrollStartTargetX", "ScrollStartTargetY",
        "ScrollStartTargetBlock", "ScrollStartTargetInline",
        // scroll-marker-group / scroll-target-group (2).
        "ScrollMarkerGroup", "ScrollTargetGroup",
    ]
    /// Set form for the PropertyRegistry union.
    static var set: Set<String> { Set(names) }
}

enum ScrollingExtractor {

    /// Walk every property once; non-owned names skipped so call sites
    /// can pass the entire property list verbatim.
    static func extract(from properties: [IRProperty]) -> ScrollingConfig? {
        var cfg = ScrollingConfig()
        let owned = ScrollingProperty.set
        for p in properties where owned.contains(p.type) {
            // Best-effort keyword extraction for single-keyword values
            // (scroll-behavior, snap-align, overscroll-behavior, etc.).
            // For structured values (ScrollbarColor two-token pair,
            // ScrollMargin lengths) we fall back to a debug dump so the
            // audit map still records that the property was seen. NOTHING
            // reads these strings today: retro P2b (A6#10) deleted the
            // identity `ScrollingApplier` they were kept for — the family's
            // iOS analogues are listed in ScrollingConfig's header.
            if let kw = ValueExtractors.extractKeyword(p.data) {
                cfg.rawByType[p.type] = kw
            } else {
                cfg.rawByType[p.type] = String(describing: p.data)
            }
            cfg.touched = true
        }
        return cfg.touched ? cfg : nil
    }
}
