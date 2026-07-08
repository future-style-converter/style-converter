package com.styleconverter.test.style.lists

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ListsRegistryTest {

    @Before
    fun prime() {
        ListsRegistration.hashCode()
    }

    @Test
    fun `all 3 list-style properties registered`() {
        listOf("ListStyleType", "ListStylePosition", "ListStyleImage").forEach {
            assertTrue(
                "$it owner = ${PropertyRegistry.ownerOf(it)}",
                PropertyRegistry.ownerOf(it) == "lists"
            )
        }
    }
}
