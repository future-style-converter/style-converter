package com.styleconverter.runtime.table

import com.styleconverter.runtime.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TableRegistryTest {

    @Before
    fun prime() {
        TableRegistration.hashCode()
    }

    @Test
    fun `all 5 table properties registered under table owner`() {
        listOf("BorderCollapse", "BorderSpacing", "CaptionSide", "EmptyCells", "TableLayout").forEach {
            assertTrue(
                "$it owner = ${PropertyRegistry.ownerOf(it)}",
                PropertyRegistry.ownerOf(it) == "table"
            )
        }
    }
}
