package com.styleconverter.runtime.columns

// Wave 12 — the multicol vertical-writing bail must fire for an INHERITED
// writing mode (honesty fix). css-writing-modes-4 §3.1: writing-mode
// inherits — and the css-break background-image-001/002 fixtures declare
// `writing-mode: vertical-rl` on the PARENT .container while the multicol
// children carry only column properties. MultiColumnExtractor reads
// WritingMode off the SAME property list it's handed, so with the
// inheritance channel missing WritingMode the wave-10 bail
// (verticalWritingMode → unfragmented + logOnce) could never fire: the
// vertical captures rendered pixel-identical to the horizontal test with
// no breadcrumb.
//
// The fix adds "WritingMode" to ComponentRenderer.INHERITED_PROPERTY_TYPES
// — the renderer already hands the multicol branch the MERGED list
// (mergedComponent.properties), so no extractor plumbing changes. This
// suite pins the channel membership and drives the exact merge → extract
// pipeline the renderer runs (integration-shaped, no device — the
// standing suite contract).

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiColumnInheritedWritingModeTest {

    /** IR wire shape: keyword properties are bare JSON string primitives. */
    private fun prop(t: String, j: String) = IRProperty(t, Json.parseToJsonElement(j))

    // The parent .container's declarations (the css-break fixture wire:
    // WritingMode VERTICAL_RL + the 472px InlineSize).
    private val parent = listOf(
        prop("WritingMode", "\"VERTICAL_RL\""),
        prop("InlineSize", """{"px":472}""")
    )

    // The multicol child's OWN declarations — column properties only, no
    // WritingMode (exactly the fixture shape that exposed the gap).
    private val childOwn = listOf(
        prop("ColumnCount", "3"),
        prop("ColumnFill", "\"AUTO\"")
    )

    /** The renderer's parent→child hop: filter the parent's effective list
     *  by the whitelist (what RenderComponent publishes on
     *  LocalInheritedProperties), then merge UNDER the child's own. */
    private fun mergedChildProperties(own: List<IRProperty>): List<IRProperty> {
        val channel = parent.filter { it.type in ComponentRenderer.INHERITED_PROPERTY_TYPES }
        return ComponentRenderer.mergeInherited(own, channel)
    }

    // ── channel membership ───────────────────────────────────────────────

    @Test
    fun `writing-mode is on the inheritance whitelist`() {
        // css-writing-modes-4 §3.1 "Inherited: yes" — the wave-12 fix.
        assertTrue("WritingMode" in ComponentRenderer.INHERITED_PROPERTY_TYPES)
    }

    @Test
    fun `inline-size stays off the whitelist`() {
        // Box-model guard: the parent's 472px InlineSize must NOT ride the
        // same channel (sizing never inherits in CSS).
        assertFalse("InlineSize" in ComponentRenderer.INHERITED_PROPERTY_TYPES)
    }

    // ── the bail through the real merge → extract pipeline ───────────────

    @Test
    fun `inherited vertical-rl reaches the multicol config and flags the bail`() {
        // Exactly what the renderer does: merge the channel under the
        // child's own list, then hand the pairs to the multicol extractor.
        val merged = mergedChildProperties(childOwn)
        val config = MultiColumnExtractor.extractMultiColumnConfig(
            merged.map { it.type to it.data }
        )
        // The wave-10 bail flag now sees the PARENT's vertical writing mode
        // — MultiColumnLayout turns this into fragmentationAllowed=false +
        // the one-time "vertical writing-mode (blocked-platform)" log.
        assertTrue("inherited vertical-rl must flag the bail", config.verticalWritingMode)
        // And the column properties themselves still extract from the own
        // declarations (the merge appends own AFTER inherited).
        assertTrue(config.columnCount == 3)
    }

    @Test
    fun `child's own horizontal-tb beats the inherited vertical mode`() {
        // Cascade rule: own declarations always win over the channel — a
        // child re-declaring horizontal-tb keeps fragmentation live.
        val own = childOwn + prop("WritingMode", "\"HORIZONTAL_TB\"")
        val config = MultiColumnExtractor.extractMultiColumnConfig(
            mergedChildProperties(own).map { it.type to it.data }
        )
        assertFalse("own horizontal-tb must override", config.verticalWritingMode)
    }

    @Test
    fun `no writing-mode anywhere keeps the horizontal default`() {
        // Channel empty of WritingMode ⇒ extractor default (horizontal-tb)
        // ⇒ no bail — the whole horizontal multicol corpus is untouched.
        val config = MultiColumnExtractor.extractMultiColumnConfig(
            ComponentRenderer.mergeInherited(childOwn, emptyList()).map { it.type to it.data }
        )
        assertFalse(config.verticalWritingMode)
    }
}
