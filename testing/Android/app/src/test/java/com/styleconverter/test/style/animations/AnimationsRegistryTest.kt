package com.styleconverter.test.style.animations

// Phase 9 tripwire: every IR property file under
// src/main/kotlin/app/irmodels/properties/animations/ must be claimed in
// PropertyRegistry by AnimationsRegistration (26 names, owner="animations").
// The three scroll-timeline longhands live in irmodels/properties/scrolling/
// and are covered by ScrollTimelineRegistryTest under owner="scrolling".
//
// Pattern mirrors TypographyRegistryTest.

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnimationsRegistryTest {

    // Kotlin `object`s are lazy — without a reference the init block never
    // fires and the registry stays empty in the test JVM. `hashCode()` is the
    // cheapest way to force class-load.
    @Before
    fun primeExtractors() {
        AnimationsRegistration.hashCode()
    }

    // The canonical 26 animations/ IR property names (one per file under
    // src/main/kotlin/app/irmodels/properties/animations/). Alphabetized so
    // diffs stay readable as the IR grows.
    private val animationsProperties: List<String> = listOf(
        "AnimationComposition",
        "AnimationDelay",
        "AnimationDirection",
        "AnimationDuration",
        "AnimationFillMode",
        "AnimationIterationCount",
        "AnimationName",
        "AnimationPlayState",
        "AnimationRange",
        "AnimationRangeEnd",
        "AnimationRangeStart",
        "AnimationTimeline",
        "AnimationTimingFunction",
        "TimelineScope",
        "TransitionBehavior",
        "TransitionDelay",
        "TransitionDuration",
        "TransitionProperty",
        "TransitionTimingFunction",
        "ViewTimeline",
        "ViewTimelineAxis",
        "ViewTimelineInset",
        "ViewTimelineName",
        "ViewTransitionClass",
        "ViewTransitionGroup",
        "ViewTransitionName"
    )

    @Test
    fun `animations property list is the canonical 26`() {
        // Guard against typos in the list above — the count drifting points
        // straight at this file rather than at a cryptic registry miss.
        assertEquals(
            "Expected 26 animations IR property names (one per file under " +
                "src/main/kotlin/app/irmodels/properties/animations/).",
            26,
            animationsProperties.size
        )
    }

    @Test
    fun `every animations IR property is registered`() {
        // Collect misses first so a single run reports the full gap.
        val missing = animationsProperties.filter { !PropertyRegistry.isMigrated(it) }
        assertTrue(
            "Animations properties not registered with PropertyRegistry:\n" +
                missing.joinToString("\n") { "  - $it" },
            missing.isEmpty()
        )
    }

    @Test
    fun `every animations registration has the animations owner`() {
        // Owner must be exactly "animations" — catches accidental typos like
        // "animation" that the boolean isMigrated() check would silently pass.
        val badOwners = animationsProperties
            .mapNotNull { name -> PropertyRegistry.ownerOf(name)?.let { name to it } }
            .filter { (_, owner) -> owner != "animations" }
        assertTrue(
            "Animations properties with non-animations owners:\n" +
                badOwners.joinToString("\n") { (n, o) -> "  - $n -> $o" },
            badOwners.isEmpty()
        )
    }
}
