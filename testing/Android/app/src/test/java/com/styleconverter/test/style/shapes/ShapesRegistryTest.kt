package com.styleconverter.test.style.shapes

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShapesRegistryTest {

    @Before
    fun prime() {
        ShapesRegistration.hashCode()
    }

    @Test
    fun `all 5 shape properties registered under shapes owner`() {
        listOf(
            "ShapeOutside", "ShapeMargin", "ShapePadding",
            "ShapeInside", "ShapeImageThreshold"
        ).forEach {
            assertTrue(
                "$it owner = ${PropertyRegistry.ownerOf(it)}",
                PropertyRegistry.ownerOf(it) == "shapes"
            )
        }
    }
}
