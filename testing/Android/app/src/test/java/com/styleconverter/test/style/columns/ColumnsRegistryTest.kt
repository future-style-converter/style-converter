package com.styleconverter.test.style.columns

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ColumnsRegistryTest {

    @Before
    fun prime() {
        ColumnsRegistration.hashCode()
    }

    private val props = listOf(
        "ColumnCount", "ColumnWidth", "ColumnGap",
        "ColumnRuleWidth", "ColumnRuleStyle", "ColumnRuleColor",
        "ColumnSpan", "ColumnFill"
    )

    @Test
    fun `all 8 column properties are registered under columns owner`() {
        val bad = props.filter { PropertyRegistry.ownerOf(it) != "columns" }
        assertTrue("Wrong owner or missing: $bad", bad.isEmpty())
    }
}
