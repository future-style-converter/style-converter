package com.styleconverter.runtime

// PropertyScope — the Android mirror of the converter's frozen v2
// style-ownership classification (converter
// src/main/kotlin/app/irmodels/PropertyScope.kt; normative lists in
// schema/spec/03-children.md).
//
// WIRE DECISION (v2 freeze): scope is NOT emitted on the IR wire. Each
// platform PropertyRegistry mirrors the classification instead, so
// routing is machine-checkable (tools/visual/coverage-audit.mjs parity)
// without duplicating derivable data on the wire. The two non-SELF sets
// below are CLOSED and byte-parallel with the converter's — a divergence
// between the four copies is a contract bug, pinned by PropertyScopeTest.

/** The three ownership scopes of the v2 placement contract. */
enum class PropertyScope {
    /** Styles the declaring component's own box. Default for everything unlisted. */
    SELF,
    /** The container's layout policy for its open slot (no child knowledge). */
    CONTAINER,
    /** Child-carried parent-data; inert until measured by a matching container. */
    ITEM;

    companion object {
        // CONTAINER-scoped CSS properties (design §1.3 list, frozen at v2):
        // layout-policy declarations a container makes about its own slot.
        private val CONTAINER_PROPERTIES = setOf(
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
            "column-rule-style", "column-rule-color"
        )

        // ITEM-scoped CSS properties (design §1.3 list, frozen at v2):
        // the audit's "Handled at Container Level" list, moved to the
        // child as parent-data (core/placement/ItemPlacement).
        private val ITEM_PROPERTIES = setOf(
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
            "inset-inline-start", "inset-inline-end"
        )

        /**
         * Classify a CSS property by its kebab-case name.
         * Unknown / unlisted names are SELF by definition — the two
         * non-default lists above are closed sets frozen with the v2
         * contract.
         */
        fun of(cssPropertyName: String): PropertyScope = when (cssPropertyName.lowercase()) {
            in CONTAINER_PROPERTIES -> CONTAINER
            in ITEM_PROPERTIES -> ITEM
            else -> SELF
        }

        /** The frozen CONTAINER list, exposed for parity tooling/tests. */
        fun containerProperties(): Set<String> = CONTAINER_PROPERTIES

        /** The frozen ITEM list, exposed for parity tooling/tests. */
        fun itemProperties(): Set<String> = ITEM_PROPERTIES
    }
}
