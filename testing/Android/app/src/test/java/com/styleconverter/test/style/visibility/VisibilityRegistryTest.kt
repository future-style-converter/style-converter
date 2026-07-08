package com.styleconverter.test.style.visibility

// Phase 8 tripwire (visibility slice). The `visibility` IR property plus the
// five overflow longhands must be claimed under the `visibility` owner so the
// Phase 8 coverage matrix in testing/README.md has a single folder to point
// at. The actual runtime logic still lives in InteractionExtractor (for
// `visibility`) and OverflowExtractor (for the five overflow* longhands) —
// this registration facade is documented in VisibilityRegistration.kt.

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisibilityRegistryTest {

    @Before
    fun primeExtractors() {
        // Priming the facade forces its init {} block, which is the only
        // thing that writes the six visibility-owner entries into the registry.
        VisibilityRegistration.hashCode()
    }

    private val visibilityProperties: List<String> = listOf(
        "Visibility",
        "Overflow",
        "OverflowX",
        "OverflowY",
        "OverflowBlock",
        "OverflowInline"
    )

    @Test
    fun `every visibility-family IR property is registered under visibility owner`() {
        val badOwners = visibilityProperties
            .map { name -> name to PropertyRegistry.ownerOf(name) }
            .filter { (_, owner) -> owner != "visibility" }
        assertTrue(
            "Visibility properties missing or wrongly owned:\n" +
                badOwners.joinToString("\n") { (n, o) -> "  - $n -> $o" },
            badOwners.isEmpty()
        )
    }
}
