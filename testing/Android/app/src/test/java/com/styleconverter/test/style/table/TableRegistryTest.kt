package com.styleconverter.test.style.table

import com.styleconverter.test.style.PropertyRegistry
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
