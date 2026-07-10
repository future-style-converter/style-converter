package com.styleconverter.runtime

// PropertyScope mirror pinning — the Android copy of the converter's
// frozen v2 classification (schema/spec/03-children.md). The two
// non-SELF sets are CLOSED: these counts and spot members are the freeze
// itself, byte-parallel with converter PropertyScopeTest. If this test
// needs editing, that's a cross-platform contract change, not a refactor.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyScopeTest {

    @Test
    fun `frozen set sizes match the v2 contract`() {
        // 33 CONTAINER + 28 ITEM entries, frozen at the v2 freeze
        // (identical counts pinned converter-side).
        assertEquals(33, PropertyScope.containerProperties().size)
        assertEquals(28, PropertyScope.itemProperties().size)
    }

    @Test
    fun `classification of() spot checks across all three scopes`() {
        // CONTAINER: layout policy the container declares about its slot.
        assertEquals(PropertyScope.CONTAINER, PropertyScope.of("display"))
        assertEquals(PropertyScope.CONTAINER, PropertyScope.of("grid-template-columns"))
        assertEquals(PropertyScope.CONTAINER, PropertyScope.of("column-span"))   // multicol family decision
        assertEquals(PropertyScope.CONTAINER, PropertyScope.of("gap"))
        // ITEM: child-carried parent-data claims (core/placement).
        assertEquals(PropertyScope.ITEM, PropertyScope.of("align-self"))
        assertEquals(PropertyScope.ITEM, PropertyScope.of("grid-column-start"))
        assertEquals(PropertyScope.ITEM, PropertyScope.of("z-index"))
        assertEquals(PropertyScope.ITEM, PropertyScope.of("inset-inline-start"))
        assertEquals(PropertyScope.ITEM, PropertyScope.of("position"))
        // SELF: the ~530 default case, including anything unknown.
        assertEquals(PropertyScope.SELF, PropertyScope.of("background-color"))
        assertEquals(PropertyScope.SELF, PropertyScope.of("width"))
        assertEquals(PropertyScope.SELF, PropertyScope.of("not-a-real-property"))
        // Case-insensitive by kebab-name.
        assertEquals(PropertyScope.ITEM, PropertyScope.of("Align-Self"))
    }

    @Test
    fun `the two frozen sets are disjoint`() {
        // A property with two owners would make routing ambiguous.
        val overlap = PropertyScope.containerProperties() intersect PropertyScope.itemProperties()
        assertTrue("CONTAINER and ITEM sets overlap: $overlap", overlap.isEmpty())
    }
}
