package com.styleconverter.test.style.experimental

import com.styleconverter.test.style.PropertyRegistry
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
