package com.styleconverter.test.style.global

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GlobalRegistryTest {

    @Before
    fun prime() {
        GlobalRegistration.hashCode()
    }

    @Test
    fun `all shorthand is registered under global owner`() {
        assertTrue(
            "All owner = ${PropertyRegistry.ownerOf("All")}",
            PropertyRegistry.ownerOf("All") == "global"
        )
    }
}
