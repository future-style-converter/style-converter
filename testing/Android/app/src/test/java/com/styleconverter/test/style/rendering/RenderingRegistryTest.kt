package com.styleconverter.test.style.rendering

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RenderingRegistryTest {

    @Before
    fun prime() {
        RenderingRegistration.hashCode()
    }

    private val props = listOf(
        "ImageRendering", "ImageOrientation", "ImageResolution",
        "ColorRendering", "ColorInterpolation", "ColorInterpolationFilters",
        // TextRendering owned by typography/.
        "ShapeRendering",
        "Zoom", "InterpolateSize", "ContentVisibility",
        "ForcedColorAdjust", "PrintColorAdjust",
        "FieldSizing", "InputSecurity"
    )

    @Test
    fun `every rendering IR property is registered`() {
        val missing = props.filter { !PropertyRegistry.isMigrated(it) }
        assertTrue("Missing: $missing", missing.isEmpty())
    }
}
