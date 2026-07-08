package com.styleconverter.test.style.print

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrintRegistryTest {

    @Before
    fun prime() {
        PrintRegistration.hashCode()
    }

    private val props = listOf(
        // Orphans / Widows owned by typography/.
        "BreakBefore", "BreakAfter", "BreakInside",
        "PageBreakBefore", "PageBreakAfter", "PageBreakInside",
        "Page", "Size", "Bleed", "Marks", "MarginBreak",
        "BookmarkLabel", "BookmarkLevel", "BookmarkState", "BookmarkTarget",
        "FootnoteDisplay", "FootnotePolicy", "Leader"
    )

    @Test
    fun `every print IR property is registered`() {
        val missing = props.filter { !PropertyRegistry.isMigrated(it) }
        assertTrue("Missing: $missing", missing.isEmpty())
    }
}
