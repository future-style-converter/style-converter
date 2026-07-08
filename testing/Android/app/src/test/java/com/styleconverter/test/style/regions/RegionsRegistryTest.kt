package com.styleconverter.test.style.regions

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegionsRegistryTest {

    @Before
    fun prime() {
        RegionsRegistration.hashCode()
    }

    private val props = listOf(
        "FlowInto", "FlowFrom", "RegionFragment",
        "Continue", "CopyInto",
        "WrapFlow", "WrapThrough",
        "WrapBefore", "WrapAfter", "WrapInside"
    )

    @Test
    fun `all 10 regions props registered under regions owner`() {
        val bad = props.filter { PropertyRegistry.ownerOf(it) != "regions" }
        assertTrue("Wrong owner or missing: $bad", bad.isEmpty())
    }
}
