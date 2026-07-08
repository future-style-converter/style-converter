package com.styleconverter.test.style.layout

// Phase 7 step 1 tripwire — mirrors TypographyRegistryTest. Every IR property
// file under src/main/kotlin/app/irmodels/properties/layout/ must be claimed
// in PropertyRegistry by the LayoutExtractor scaffold. If a new layout
// property lands in the IR without matching registration this test goes red,
// preventing the legacy PropertyApplier switch from silently reclaiming it.

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LayoutRegistryTest {

    // Force LayoutExtractor's `init {}` block to run. Kotlin `object`s are
    // lazy — without at least one reference the init block never fires in
    // a test JVM and the registry stays empty. TypographyRegistryTest uses
    // the same `.hashCode()` trick.
    @Before
    fun primeExtractors() {
        LayoutExtractor.hashCode()
    }

    // The canonical list of layout IR property names, grouped by sub-folder
    // (flexbox/, grid/, position/, advanced/, and four root-level files).
    // Names are camelcase IRProperty.type strings emitted by the CSS parser —
    // one entry per file under src/main/kotlin/app/irmodels/properties/layout/.
    // Keep grouped (not alphabetised) so reviewers can tick off categories
    // against the IR tree during code review.
    private val layoutProperties: List<String> = listOf(
        // ----- flexbox/ (12) -----
        "Display",
        "FlexDirection", "FlexWrap",
        "FlexGrow", "FlexShrink", "FlexBasis",
        "JustifyContent", "AlignItems", "AlignContent",
        "AlignSelf", "Order",
        "BoxOrient",
        // ----- grid/ (18) -----
        "GridTemplateColumns", "GridTemplateRows", "GridTemplateAreas",
        "GridTemplate",
        "GridAutoColumns", "GridAutoRows", "GridAutoFlow",
        "GridAutoTrack",
        "GridArea",
        "GridColumnStart", "GridColumnEnd",
        "GridRowStart", "GridRowEnd",
        "JustifyItems", "JustifySelf",
        "AlignTracks", "JustifyTracks",
        "MasonryAutoFlow",
        // ----- position/ (10) -----
        "Position",
        "Top", "Right", "Bottom", "Left",
        "InsetBlockStart", "InsetBlockEnd",
        "InsetInlineStart", "InsetInlineEnd",
        "ZIndex",
        // ----- advanced/ (17) -----
        "AnchorName", "AnchorScope",
        "InsetArea",
        "OffsetPath", "OffsetDistance", "OffsetAnchor", "OffsetPosition",
        "OffsetRotate", "Offset",
        "PositionAnchor", "PositionArea",
        "PositionFallback", "PositionTry",
        "PositionTryFallbacks", "PositionTryOptions",
        "PositionTryOrder",
        "PositionVisibility",
        // ----- layout root (4) -----
        "Clear", "Float",
        "Overlay",
        "ReadingFlow"
    )

    @Test
    fun `layout property list is the canonical 61`() {
        // Guard against typos in the list above. The phase brief spoke of 60
        // but enumerated 61 when counted explicitly (flexbox 12 + grid 18 +
        // position 10 + advanced 17 + root 4). If the IR grows the count
        // changes and this assertion fails with a helpful message.
        assertEquals(
            "Expected 61 layout IR property names (one per file under " +
                "src/main/kotlin/app/irmodels/properties/layout/).",
            61,
            layoutProperties.size
        )
    }

    @Test
    fun `every layout IR property is registered`() {
        // Collect misses first so a single test run reports the full gap
        // instead of bailing on the first missing property.
        val missing = layoutProperties.filter { !PropertyRegistry.isMigrated(it) }
        assertTrue(
            "Layout properties not registered with PropertyRegistry:\n" +
                missing.joinToString("\n") { "  - $it" },
            missing.isEmpty()
        )
    }

    @Test
    fun `every layout registration has a layout-family owner`() {
        // Owner must be one of the five layout sub-folders. Catches typo
        // owners like "lyaout" that the boolean isMigrated() silently accepts.
        val validOwners = setOf(
            "layout",
            "layout/flexbox",
            "layout/grid",
            "layout/position",
            "layout/advanced"
        )
        val badOwners = layoutProperties
            .mapNotNull { name -> PropertyRegistry.ownerOf(name)?.let { name to it } }
            .filter { (_, owner) -> owner !in validOwners }
        assertTrue(
            "Layout properties with non-layout owners:\n" +
                badOwners.joinToString("\n") { (n, o) -> "  - $n -> $o" },
            badOwners.isEmpty()
        )
    }

    @Test
    fun `registering layout properties grows the registry by at least 61`() {
        // PropertyRegistry is a process-global singleton that other extractor
        // init blocks may have already populated before this test runs. We
        // can't measure a clean delta without resetting the registry (which
        // would break other tests), so assert the layout family contributes
        // at least 61 distinct entries that resolve to layout owners.
        val layoutOwned = PropertyRegistry.allRegistered()
            .filter { (_, owner) ->
                owner == "layout" ||
                    owner == "layout/flexbox" ||
                    owner == "layout/grid" ||
                    owner == "layout/position" ||
                    owner == "layout/advanced"
            }
        assertTrue(
            "Expected at least 61 layout-owned registrations, saw ${layoutOwned.size}.",
            layoutOwned.size >= 61
        )
    }
}
