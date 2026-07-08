package com.styleconverter.test.style.transforms

// Phase 8 tripwire (transforms slice). Every transform-family IR property
// file under src/main/kotlin/app/irmodels/properties/transforms/ must be
// claimed by either TransformExtractor (2D family) or Transform3DExtractor
// (3D-context family). If a new transform property ships without a matching
// PropertyRegistry call this test goes red, catching the drift before the
// legacy dispatch silently re-claims coverage.

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransformsRegistryTest {

    // Kotlin `object`s are lazy — without a reference their init {} block
    // never fires in the test JVM and PropertyRegistry stays empty. Touching
    // hashCode() is a zero-cost way to force class init.
    @Before
    fun primeExtractors() {
        TransformExtractor.hashCode()
        Transform3DExtractor.hashCode()
    }

    // Canonical set of transform IR property names, one per file under
    // src/main/kotlin/app/irmodels/properties/transforms/. Kept alphabetized
    // so diffs read cleanly when the IR grows.
    private val transformProperties: List<String> = listOf(
        // 3D context — owned by Transform3DExtractor.
        "BackfaceVisibility",
        "Perspective",
        "PerspectiveOrigin",
        // 2D — owned by TransformExtractor.
        "Rotate",
        "Scale",
        "Transform",
        "TransformBox",
        "TransformOrigin",
        "TransformStyle",
        "Translate"
    )

    @Test
    fun `transform property list is the canonical 10`() {
        // Guard against typos in the list above.
        assertTrue(
            "Expected 10 transform IR property names (one per file under " +
                "src/main/kotlin/app/irmodels/properties/transforms/).",
            transformProperties.size == 10
        )
    }

    @Test
    fun `every transform IR property is registered`() {
        val missing = transformProperties.filter { !PropertyRegistry.isMigrated(it) }
        assertTrue(
            "Transform properties not registered with PropertyRegistry:\n" +
                missing.joinToString("\n") { "  - $it" },
            missing.isEmpty()
        )
    }

    @Test
    fun `every transform registration has a transforms owner`() {
        // Owner must be exactly "transforms" — Phase 8 keeps the 2D + 3D
        // triplets in the same folder, so the owner string is flat.
        val badOwners = transformProperties
            .mapNotNull { name -> PropertyRegistry.ownerOf(name)?.let { name to it } }
            .filter { (_, owner) -> owner != "transforms" }
        assertTrue(
            "Transform properties with non-transforms owners:\n" +
                badOwners.joinToString("\n") { (n, o) -> "  - $n -> $o" },
            badOwners.isEmpty()
        )
    }
}
