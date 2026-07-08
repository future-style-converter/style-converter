package com.styleconverter.test.style.images

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImagesRegistryTest {

    @Before
    fun prime() {
        ImagesRegistration.hashCode()
    }

    private val props = listOf(
        "ObjectFit", "ObjectPosition", "ObjectViewBox",
        "ImageRendering", "ImageOrientation", "ImageResolution"
    )

    @Test
    fun `every images IR property is registered`() {
        val missing = props.filter { !PropertyRegistry.isMigrated(it) }
        assertTrue("Missing: $missing", missing.isEmpty())
    }
}
