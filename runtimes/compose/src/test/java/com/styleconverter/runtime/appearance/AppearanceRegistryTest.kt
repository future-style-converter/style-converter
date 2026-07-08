package com.styleconverter.runtime.appearance

import com.styleconverter.runtime.PropertyRegistry
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
