package com.styleconverter.test.style.content

import com.styleconverter.test.style.PropertyRegistry
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
