package com.styleconverter.test.style.interactions

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InteractionsRegistryTest {

    @Before
    fun prime() {
        InteractionsRegistration.hashCode()
    }

    private val props = listOf(
        // `Visibility` itself is owned by visibility/VisibilityRegistration.
        "Cursor", "PointerEvents", "TouchAction", "UserSelect",
        "ContentVisibility", "Appearance",
        "Resize", "Caret", "CaretShape", "Interactivity"
    )

    @Test
    fun `every interactions IR property is registered`() {
        val missing = props.filter { !PropertyRegistry.isMigrated(it) }
        assertTrue("Missing: $missing", missing.isEmpty())
    }
}
