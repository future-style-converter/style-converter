package com.styleconverter.runtime.experimental

import com.styleconverter.runtime.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExperimentalRegistryTest {

    @Before
    fun prime() {
        ExperimentalRegistration.hashCode()
    }

    @Test
    fun `experimental properties registered under experimental owner`() {
        listOf("PresentationLevel", "Running", "StringSet").forEach {
            assertTrue(
                "$it owner = ${PropertyRegistry.ownerOf(it)}",
                PropertyRegistry.ownerOf(it) == "experimental"
            )
        }
    }
}
