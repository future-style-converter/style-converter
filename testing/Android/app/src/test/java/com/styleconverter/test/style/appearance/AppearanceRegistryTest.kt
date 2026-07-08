package com.styleconverter.test.style.appearance

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppearanceRegistryTest {

    @Before
    fun prime() {
        AppearanceRegistration.hashCode()
    }

    @Test
    fun `appearance properties are registered`() {
        listOf("Appearance", "AppearanceVariant", "ColorAdjust", "ImageRenderingQuality").forEach {
            assertTrue("$it missing", PropertyRegistry.isMigrated(it))
        }
    }
}
