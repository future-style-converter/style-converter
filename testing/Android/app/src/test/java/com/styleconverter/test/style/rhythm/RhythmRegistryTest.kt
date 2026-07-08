package com.styleconverter.test.style.rhythm

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RhythmRegistryTest {

    @Before
    fun prime() {
        RhythmRegistration.hashCode()
    }

    @Test
    fun `block-step family is registered under rhythm owner`() {
        listOf("BlockStep", "BlockStepAlign", "BlockStepInsert", "BlockStepRound", "BlockStepSize").forEach {
            assertTrue(
                "$it owner = ${PropertyRegistry.ownerOf(it)}",
                PropertyRegistry.ownerOf(it) == "rhythm"
            )
        }
    }
}
