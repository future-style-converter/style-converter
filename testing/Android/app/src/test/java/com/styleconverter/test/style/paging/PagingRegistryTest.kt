package com.styleconverter.test.style.paging

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PagingRegistryTest {

    @Before
    fun prime() {
        PagingRegistration.hashCode()
    }

    @Test
    fun `paging IR properties are registered`() {
        listOf(
            "BreakBefore", "BreakAfter", "BreakInside",
            "PageBreakBefore", "PageBreakAfter", "PageBreakInside",
            "MarginBreak"
        ).forEach {
            assertTrue("$it missing", PropertyRegistry.isMigrated(it))
        }
    }
}
