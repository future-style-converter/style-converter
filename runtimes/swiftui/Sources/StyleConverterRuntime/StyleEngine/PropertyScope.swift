//
//  PropertyScope.swift
//  StyleEngine — IR v2 Slot & Placement contract.
//
//  iOS mirror of the converter's frozen style-ownership classification
//  (converter/src/main/kotlin/app/irmodels/PropertyScope.kt), pinned by
//  schema/spec/03-children.md. Scope is NOT emitted on the wire (the v2
//  freeze decision): properties travel as plain {type,data} envelopes on
//  whichever component declared them, and each platform PropertyRegistry
//  mirrors this classification so routing is machine-checkable.
//
//    selfScoped — styles the declaring component's own box (~530 props).
//    container  — the container's layout policy for its open slot.
//    item       — child-carried parent-data; routed to ItemPlacement and
//                 inert until measured by a matching container.
//
//  The two non-default sets below are CLOSED and byte-for-byte identical
//  to the converter lists — PlacementTests pins them, and
//  tools/visual/coverage-audit.mjs can cross-check platforms.
//

import Foundation

/// The three ownership scopes of the v2 placement contract. Case names
/// diverge from the Kotlin SELF/CONTAINER/ITEM only because `self` is a
/// Swift keyword; the classification is identical.
enum PropertyScope: Equatable {
    /// Styles the declaring component's own box (default for everything
    /// unlisted).
    case selfScoped
    /// The container's layout policy for its open slot (no child
    /// knowledge).
    case container
    /// Child-carried parent-data; inert until a matching container
    /// measures it.
    case item

    // CONTAINER-scoped CSS properties (design §1.3 list, frozen at v2) —
    // layout-policy declarations a container makes about its own slot.
    static let containerProperties: Set<String> = [
        // formatting-context switch
        "display",
        // flex container policy
        "flex-direction", "flex-wrap", "flex-flow",
        // content/item distribution (container side)
        "justify-content", "align-items", "align-content",
        "place-content", "place-items", "justify-items",
        // gutters
        "gap", "row-gap", "column-gap",
        // grid track/area definition
        "grid-template-columns", "grid-template-rows", "grid-template-areas",
        "grid-template", "grid",
        "grid-auto-columns", "grid-auto-rows", "grid-auto-flow",
        // masonry / track alignment (draft specs, catalogued in irmodels)
        "align-tracks", "justify-tracks", "masonry-auto-flow",
        // multicol container policy (design: "column-* (multicol)")
        "columns", "column-count", "column-width", "column-fill",
        "column-span", "column-rule", "column-rule-width",
        "column-rule-style", "column-rule-color",
    ]

    // ITEM-scoped CSS properties (design §1.3 list, frozen at v2) — the
    // audit's "Handled at Container Level" list, moved to the child.
    static let itemProperties: Set<String> = [
        // per-item alignment overrides
        "align-self", "justify-self", "place-self",
        // visual reordering
        "order",
        // flex claims
        "flex-grow", "flex-shrink", "flex-basis", "flex",
        // grid placement claims
        "grid-area",
        "grid-column", "grid-column-start", "grid-column-end",
        "grid-row", "grid-row-start", "grid-row-end",
        // paint order among siblings
        "z-index",
        // out-of-flow positioning + insets (containing block = the
        // immediate slot parent — design §4.4)
        "position",
        "top", "right", "bottom", "left",
        "inset", "inset-block", "inset-inline",
        "inset-block-start", "inset-block-end",
        "inset-inline-start", "inset-inline-end",
    ]

    /// Classify a CSS property by its kebab-case name. Unknown / unlisted
    /// names are selfScoped by definition — the two non-default lists are
    /// closed sets frozen with the v2 contract.
    static func of(_ cssPropertyName: String) -> PropertyScope {
        let key = cssPropertyName.lowercased()
        if containerProperties.contains(key) { return .container }
        if itemProperties.contains(key) { return .item }
        return .selfScoped
    }
}

extension PropertyRegistry {
    /// Registry hook mirroring the converter's `IRProperty.scope`
    /// extension: lets tooling ask the iOS engine how it routes a CSS
    /// property without touching the 550 triplet files.
    static func scope(ofCss cssPropertyName: String) -> PropertyScope {
        PropertyScope.of(cssPropertyName)
    }
}
