package com.styleconverter.runtime.navigation

import com.styleconverter.runtime.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NavigationRegistryTest {

    @Before
    fun prime() {
        NavigationRegistration.hashCode()
    }

    @Test
    fun `nav family registered under navigation owner`() {
        listOf("NavUp", "NavDown", "NavLeft", "NavRight", "ReadingOrder").forEach {
            assertTrue(
                "$it owner = ${PropertyRegistry.ownerOf(it)}",
                PropertyRegistry.ownerOf(it) == "navigation"
            )
        }
    }
}
