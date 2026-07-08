package com.styleconverter.runtime.content

import com.styleconverter.runtime.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContentRegistryTest {

    @Before
    fun prime() {
        ContentRegistration.hashCode()
    }

    @Test
    fun `content and counter properties registered`() {
        listOf("Content", "Quotes", "CounterReset", "CounterIncrement", "CounterSet").forEach {
            assertTrue(
                "$it not registered",
                PropertyRegistry.isMigrated(it)
            )
        }
    }
}
