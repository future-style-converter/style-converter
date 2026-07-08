package com.styleconverter.test.style.scrolling

// Phase 9 tripwire: the three scroll-timeline longhand IR property files
// under src/main/kotlin/app/irmodels/properties/scrolling/
// (ScrollTimeline{,Name,Axis}Property.kt) must be claimed in
// PropertyRegistry by ScrollTimelineRegistration under owner="scrolling".

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScrollTimelineRegistryTest {

    @Before
    fun primeExtractors() {
        // Force the init {} block to fire by touching the object.
        ScrollTimelineRegistration.hashCode()
    }

    private val scrollTimelineProperties: List<String> = listOf(
        "ScrollTimeline",
        "ScrollTimelineAxis",
        "ScrollTimelineName"
    )

    @Test
    fun `scroll-timeline property list has three entries`() {
        assertEquals(3, scrollTimelineProperties.size)
    }

    @Test
    fun `every scroll-timeline IR property is registered`() {
        val missing = scrollTimelineProperties.filter { !PropertyRegistry.isMigrated(it) }
        assertTrue(
            "Scroll-timeline properties not registered with PropertyRegistry:\n" +
                missing.joinToString("\n") { "  - $it" },
            missing.isEmpty()
        )
    }

    @Test
    fun `scroll-timeline registrations use the scrolling owner`() {
        // Owner must be "scrolling" — matches the IR folder these properties
        // live under. Keeps the coverage audit consistent with the irmodels
        // tree layout.
        val badOwners = scrollTimelineProperties
            .mapNotNull { name -> PropertyRegistry.ownerOf(name)?.let { name to it } }
            .filter { (_, owner) -> owner != "scrolling" }
        assertTrue(
            "Scroll-timeline properties with non-scrolling owners:\n" +
                badOwners.joinToString("\n") { (n, o) -> "  - $n -> $o" },
            badOwners.isEmpty()
        )
    }
}
