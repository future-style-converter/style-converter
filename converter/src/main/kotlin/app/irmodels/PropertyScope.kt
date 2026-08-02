package app.irmodels

// PropertyScope — machine-readable style-ownership classification for the
// IR v2 Slot & Placement contract (schema/spec/03-children.md §scope).
//
// The classification answers ONE question per property: who owns its
// effect when components compose?
//
//   SELF      — styles the component's own box (the ~530 default case).
//   CONTAINER — the container's layout policy for whatever arrives in its
//               open slot; declared on the container, never mentions
//               children (display, flex-direction, gap, grid-template-*…).
//   ITEM      — declared on the CHILD, packaged as parent-data, activates
//               only when the child lands in a matching container
//               (align-self, order, flex-grow, grid-area, z-index,
//               position + insets…).
//
// WIRE DECISION (recorded per the v2 freeze): scope is NOT emitted on the
// IR wire. The v2 wire (design §1.1/§2) carries properties as plain
// {type,data} envelopes on whichever component declared them; the
// placement routing (design §2.2 ItemPlacement) happens in the runtime
// engines, which mirror this classification in their PropertyRegistry so
// tools/visual/coverage-audit.mjs can machine-check parity. Emitting a
// per-property scope byte would duplicate derivable data on half a
// million wires — the classification lists below ARE the contract, and
// they are frozen in schema/spec/03-children.md.

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
            "column-rule-style", "column-rule-color",
            // gap decorations (CSS Gap Decorations L1): every one of these is
            // declared ON the flex/grid/multicol container and paints in the
            // container's own gutters — it never mentions or travels with a
            // child, so the whole family is CONTAINER-scoped like row-gap.
            "row-rule", "row-rule-width", "row-rule-style", "row-rule-color",
            "column-rule-break", "row-rule-break",
            "column-rule-inset", "row-rule-inset", "rule-overlap"
        )

        // ITEM-scoped CSS properties (design §1.3 list, frozen at v2):
        // the audit's "Handled at Container Level" list, moved to the
        // child as parent-data.
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

/**
 * Every IRProperty carries a scope, derived from its canonical CSS name.
 * An extension val (not an abstract member) so the 550 property files
 * need no edits and the classification has exactly one source of truth.
 */
val IRProperty.scope: PropertyScope
    get() = PropertyScope.of(propertyName)
