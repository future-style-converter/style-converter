package com.styleconverter.test.style.math

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MathRegistryTest {

    @Before
    fun prime() {
        MathRegistration.hashCode()
    }

    @Test
    fun `math properties registered under math owner`() {
        listOf("MathDepth", "MathShift", "MathStyle").forEach {
            assertTrue(
                "$it owner = ${PropertyRegistry.ownerOf(it)}",
                PropertyRegistry.ownerOf(it) == "math"
            )
        }
    }
}
