package com.styleconverter.runtime.paging

import com.styleconverter.runtime.PropertyRegistry
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
