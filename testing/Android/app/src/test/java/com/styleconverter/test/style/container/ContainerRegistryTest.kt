package com.styleconverter.test.style.container

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContainerRegistryTest {

    @Before
    fun prime() {
        ContainerRegistration.hashCode()
    }

    @Test
    fun `container query properties registered under container owner`() {
        listOf("Container", "ContainerName", "ContainerType").forEach {
            assertTrue(
                "$it owner = ${PropertyRegistry.ownerOf(it)}",
                PropertyRegistry.ownerOf(it) == "container"
            )
        }
    }
}
