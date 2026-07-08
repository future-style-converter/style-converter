package com.styleconverter.test.style.performance

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PerformanceRegistryTest {

    @Before
    fun prime() {
        PerformanceRegistration.hashCode()
    }

    private val props = listOf(
        "Contain", "WillChange", "Isolation",
        "Zoom", "ImageRendering", "BoxSizing", "BoxDecorationBreak",
        "ContainIntrinsicSize",
        "ContainIntrinsicWidth", "ContainIntrinsicHeight",
        "ContainIntrinsicBlockSize", "ContainIntrinsicInlineSize",
        "ContentVisibility"
    )

    @Test
    fun `every performance IR property is registered`() {
        val missing = props.filter { !PropertyRegistry.isMigrated(it) }
        assertTrue("Missing: $missing", missing.isEmpty())
    }
}
