package app.irmodels

// PropertyScope classification tests — pins the frozen CONTAINER / ITEM
// lists of the v2 Slot & Placement contract (schema/spec/03-children.md).
// The platform PropertyRegistry mirrors are audited against these same
// lists by tools/visual/coverage-audit.mjs; this suite is the converter-
// side anchor.

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PropertyScopeTest {

    @Test
    fun `container list matches the frozen design set`() {
        // Layout policy a container declares about its own open slot —
        // display, flex-*, distribution, gaps, grid templates, multicol.
        val expected = setOf(
            "display",
            "flex-direction", "flex-wrap", "flex-flow",
            "justify-content", "align-items", "align-content",
            "place-content", "place-items", "justify-items",
            "gap", "row-gap", "column-gap",
            "grid-template-columns", "grid-template-rows", "grid-template-areas",
            "grid-template", "grid",
            "grid-auto-columns", "grid-auto-rows", "grid-auto-flow",
            "align-tracks", "justify-tracks", "masonry-auto-flow",
            "columns", "column-count", "column-width", "column-fill",
            "column-span", "column-rule", "column-rule-width",
            "column-rule-style", "column-rule-color",
            // CSS Gap Decorations L1 (wave 24): declared on the flex/grid/
            // multicol container, painted in the container's own gutters —
            // never child-carried, so CONTAINER exactly like row-gap.
            "row-rule", "row-rule-width", "row-rule-style", "row-rule-color",
            "column-rule-break", "row-rule-break",
            "column-rule-inset", "row-rule-inset", "rule-overlap"
        )
        assertEquals(expected, PropertyScope.containerProperties())
        expected.forEach { assertEquals(PropertyScope.CONTAINER, PropertyScope.of(it), it) }
    }

    @Test
    fun `item list matches the frozen design set`() {
        // Child-carried placement claims — the audit's "Handled at
        // Container Level" list moved to the child as parent-data.
        val expected = setOf(
            "align-self", "justify-self", "place-self",
            "order",
            "flex-grow", "flex-shrink", "flex-basis", "flex",
            "grid-area",
            "grid-column", "grid-column-start", "grid-column-end",
            "grid-row", "grid-row-start", "grid-row-end",
            "z-index",
            "position",
            "top", "right", "bottom", "left",
            "inset", "inset-block", "inset-inline",
            "inset-block-start", "inset-block-end",
            "inset-inline-start", "inset-inline-end"
        )
        assertEquals(expected, PropertyScope.itemProperties())
        expected.forEach { assertEquals(PropertyScope.ITEM, PropertyScope.of(it), it) }
    }

    @Test
    fun `everything unlisted is SELF - representative sample`() {
        // The ~530 default case: styles the declaring component's own box.
        listOf(
            "color", "background-color", "padding-top", "margin-left",
            "border-top-width", "font-size", "opacity", "transform",
            "width", "height", "box-shadow", "overflow-x"
        ).forEach { assertEquals(PropertyScope.SELF, PropertyScope.of(it), it) }
    }

    @Test
    fun `the two non-default lists never overlap`() {
        // A property with two owners would make placement routing
        // ambiguous — structural invariant of the contract.
        assertTrue(PropertyScope.containerProperties().intersect(PropertyScope.itemProperties()).isEmpty())
    }

    @Test
    fun `classification is case-insensitive at the boundary`() {
        // Defensive: property names should already be lowercase kebab-case
        // (parser boundary), but classification must not silently mis-route
        // a stray uppercase name into SELF.
        assertEquals(PropertyScope.ITEM, PropertyScope.of("Align-Self"))
        assertEquals(PropertyScope.CONTAINER, PropertyScope.of("DISPLAY"))
    }

    @Test
    fun `IRProperty extension derives scope from propertyName`() {
        // The extension val is the single wiring point between the 550
        // property classes and the classifier — no per-file edits.
        val item = object : IRProperty { override val propertyName = "z-index" }
        assertEquals(PropertyScope.ITEM, item.scope)
        val container = object : IRProperty { override val propertyName = "grid-template-columns" }
        assertEquals(PropertyScope.CONTAINER, container.scope)
        val self = object : IRProperty { override val propertyName = "background-color" }
        assertEquals(PropertyScope.SELF, self.scope)
    }
}
