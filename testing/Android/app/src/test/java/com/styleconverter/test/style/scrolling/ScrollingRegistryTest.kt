package com.styleconverter.test.style.scrolling

// Phase 10 tripwire — every scrolling-family IR property emitted by the
// CSS parser must be claimed on PropertyRegistry so the legacy
// StyleApplier switch never silently re-dispatches them.

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScrollingRegistryTest {

    @Before
    fun prime() {
        // Force init{} on all scrolling-owner registration facades.
        ScrollingRegistration.hashCode()
        ScrollTimelineRegistration.hashCode()
    }

    private val props = listOf(
        // Base overflow longhands are owned by visibility/; this test
        // only asserts the scrolling-owned names.
        "OverflowAnchor", "OverflowClipMargin",
        "ScrollBehavior",
        "ScrollSnapType", "ScrollSnapAlign", "ScrollSnapStop",
        "ScrollMarginTop", "ScrollMarginRight",
        "ScrollMarginBottom", "ScrollMarginLeft",
        "ScrollMarginBlockStart", "ScrollMarginBlockEnd",
        "ScrollMarginInlineStart", "ScrollMarginInlineEnd",
        "ScrollPaddingTop", "ScrollPaddingRight",
        "ScrollPaddingBottom", "ScrollPaddingLeft",
        "ScrollPaddingBlockStart", "ScrollPaddingBlockEnd",
        "ScrollPaddingInlineStart", "ScrollPaddingInlineEnd",
        "OverscrollBehavior",
        "OverscrollBehaviorX", "OverscrollBehaviorY",
        "OverscrollBehaviorBlock", "OverscrollBehaviorInline",
        "ScrollbarWidth", "ScrollbarColor", "ScrollbarGutter",
        "ScrollStart", "ScrollStartX", "ScrollStartY",
        "ScrollStartBlock", "ScrollStartInline",
        "ScrollStartTarget",
        "ScrollStartTargetX", "ScrollStartTargetY",
        "ScrollStartTargetBlock", "ScrollStartTargetInline",
        "ScrollMarkerGroup", "ScrollTargetGroup",
        "ScrollTimeline", "ScrollTimelineName", "ScrollTimelineAxis"
    )

    @Test
    fun `every scrolling IR property is registered`() {
        val missing = props.filter { !PropertyRegistry.isMigrated(it) }
        assertTrue(
            "Scrolling properties missing from registry:\n  ${missing.joinToString("\n  ")}",
            missing.isEmpty()
        )
    }

    @Test
    fun `scroll-start family is registered under scrolling owner`() {
        val bad = listOf(
            "ScrollStart", "ScrollStartX", "ScrollStartTarget", "ScrollMarkerGroup"
        ).filter { PropertyRegistry.ownerOf(it) != "scrolling" }
        assertTrue("Wrong owner: $bad", bad.isEmpty())
    }
}
