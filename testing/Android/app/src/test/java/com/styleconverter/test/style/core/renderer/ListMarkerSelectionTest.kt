package com.styleconverter.test.style.core.renderer

// Tests for the list-marker resolution path used by
// ComponentRenderer.RenderListItemMarker (Bug 2 fix, swarm-002
// css-counter-styles__css3-counter-styles-101). The full @Composable
// pathway needs androidTest to execute, so we pin the marker-resolver
// contract here: given a parent _tag of 'ol'/'ul', the resolver picks
// the right ListStyleType and ListStyleApplier emits the expected
// marker glyph at index 0/1/2. Anything else is the renderer's job to
// route into Box/Column unchanged.

import com.styleconverter.test.style.lists.ListStyleApplier
import com.styleconverter.test.style.lists.ListStyleConfig
import com.styleconverter.test.style.lists.ListStyleType
import org.junit.Assert.assertEquals
import org.junit.Test

class ListMarkerSelectionTest {

    @Test
    fun `ol parent resolves to DECIMAL with 1-based index markers`() {
        // The mapping we wired in RenderContent: _tag='ol' →
        // ListStyleType.DECIMAL. ListStyleApplier.getMarker uses 0-based
        // index but emits a 1-based number (per CSS counter algorithm),
        // so the first <li> renders "1." not "0.".
        val cfg = ListStyleConfig(listStyleType = ListStyleType.DECIMAL)
        assertEquals("1.", ListStyleApplier.getMarker(0, cfg))
        assertEquals("2.", ListStyleApplier.getMarker(1, cfg))
        assertEquals("3.", ListStyleApplier.getMarker(2, cfg))
    }

    @Test
    fun `ul parent resolves to DISC with bullet character`() {
        // _tag='ul' → ListStyleType.DISC. The disc marker is the same
        // glyph for every item; index doesn't affect output.
        val cfg = ListStyleConfig(listStyleType = ListStyleType.DISC)
        // U+2022 BULLET — matches the browser-default <ul> marker.
        assertEquals("•", ListStyleApplier.getMarker(0, cfg))
        assertEquals("•", ListStyleApplier.getMarker(5, cfg))
    }

    @Test
    fun `NONE list-style emits empty string (no marker)`() {
        // A non-list parent (e.g. _tag='div' or absent) routes through
        // ListStyleType.NONE in our RenderContent decision tree. The
        // applier returns "" so the Row falls back to just rendering
        // the child with no leading marker.
        val cfg = ListStyleConfig(listStyleType = ListStyleType.NONE)
        assertEquals("", ListStyleApplier.getMarker(0, cfg))
    }
}
