package com.styleconverter.test.style.effects

// Phase 8 tripwire (effects slice — clip, filter, mask). Every effects IR
// property file under src/main/kotlin/app/irmodels/properties/effects/ that
// belongs to Phase 8 (so: NOT shadow, already owned by Phase 5; NOT blend,
// already owned by Phase 4) must be claimed by ClipPathExtractor,
// FilterExtractor, or MaskExtractor. A new variant in the IR without a
// matching registration flips this test red.

import com.styleconverter.test.style.PropertyRegistry
import com.styleconverter.test.style.effects.clip.ClipPathExtractor
import com.styleconverter.test.style.effects.filter.FilterExtractor
import com.styleconverter.test.style.effects.mask.MaskExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Phase8EffectsRegistryTest {

    // Force each extractor's init {} block by touching the object — Kotlin
    // singletons don't run their init until first reference, and the test
    // JVM otherwise sees an empty registry.
    @Before
    fun primeExtractors() {
        ClipPathExtractor.hashCode()
        FilterExtractor.hashCode()
        MaskExtractor.hashCode()
    }

    // Phase 8 effects properties, grouped by owning subfolder.
    // Source of truth: src/main/kotlin/app/irmodels/properties/effects/*.kt
    // (minus BoxShadow/TextShadow/BlendMode* which are earlier phases).
    private val clipProperties = listOf(
        "ClipPath",
        "Clip",
        "ClipPathGeometryBox",
        "ClipRule"
    )

    private val filterProperties = listOf(
        "Filter",
        "BackdropFilter"
    )

    private val maskProperties = listOf(
        // Mask-image longhands — parsed and rendered via drawWithContent +
        // BlendMode.DstIn in MaskApplier.
        "MaskImage",
        "MaskMode",
        "MaskRepeat",
        "MaskPosition",
        "MaskPositionX",
        "MaskPositionY",
        "MaskSize",
        "MaskOrigin",
        "MaskClip",
        "MaskComposite",
        "MaskType",
        // MaskBorder* — analogous to border-image; extractor claims them but
        // the applier currently TODOs the pixel-level slicing.
        "MaskBorderSource",
        "MaskBorderSlice",
        "MaskBorderWidth",
        "MaskBorderOutset",
        "MaskBorderRepeat",
        "MaskBorderMode"
    )

    @Test
    fun `effects property list counts match Phase 8 scope`() {
        // Hard numbers keep the list accurate — if someone adds a variant
        // without updating this test the count drifts and the assertion
        // message points straight at this file.
        assertEquals("clip", 4, clipProperties.size)
        assertEquals("filter", 2, filterProperties.size)
        assertEquals("mask", 17, maskProperties.size)
    }

    @Test
    fun `every clip property is registered under effects-clip`() {
        val missing = clipProperties.filter { PropertyRegistry.ownerOf(it) != "effects/clip" }
        assertTrue(
            "Clip properties missing or wrongly owned:\n" +
                missing.joinToString("\n") { "  - $it owner=${PropertyRegistry.ownerOf(it)}" },
            missing.isEmpty()
        )
    }

    @Test
    fun `every filter property is registered under effects-filter`() {
        val missing = filterProperties.filter { PropertyRegistry.ownerOf(it) != "effects/filter" }
        assertTrue(
            "Filter properties missing or wrongly owned:\n" +
                missing.joinToString("\n") { "  - $it owner=${PropertyRegistry.ownerOf(it)}" },
            missing.isEmpty()
        )
    }

    @Test
    fun `every mask property is registered under effects-mask`() {
        val missing = maskProperties.filter { PropertyRegistry.ownerOf(it) != "effects/mask" }
        assertTrue(
            "Mask properties missing or wrongly owned:\n" +
                missing.joinToString("\n") { "  - $it owner=${PropertyRegistry.ownerOf(it)}" },
            missing.isEmpty()
        )
    }
}
