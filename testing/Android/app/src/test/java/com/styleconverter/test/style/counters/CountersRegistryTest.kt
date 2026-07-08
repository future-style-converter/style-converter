package com.styleconverter.test.style.counters

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CountersRegistryTest {

    @Before
    fun prime() {
        CountersRegistration.hashCode()
    }

    @Test
    fun `counter-* properties are registered`() {
        listOf("CounterReset", "CounterIncrement", "CounterSet").forEach {
            assertTrue("$it missing", PropertyRegistry.isMigrated(it))
        }
    }
}
