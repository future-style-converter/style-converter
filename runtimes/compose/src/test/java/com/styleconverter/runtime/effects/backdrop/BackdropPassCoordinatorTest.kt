package com.styleconverter.runtime.effects.backdrop

// Pins the two-pass state machine and the IR gate in front of it. These are
// the guards that keep this lane away from everything it must not touch: a
// document that does not declare backdrop-filter never leaves DISABLED, so
// FilterApplier keeps its historical no-op and the committed captures (the
// 327-pair dark-stage baseline included) are byte-identical.

import androidx.compose.ui.geometry.Offset
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRMedia
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.ir.IRSelector
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropPassCoordinatorTest {

    // An empty function list is enough — the scan keys on the property TYPE.
    private fun prop(type: String) = IRProperty(type, JsonArray(emptyList()))

    private fun component(
        id: String,
        properties: List<IRProperty> = emptyList(),
        selectors: List<IRSelector> = emptyList(),
        media: List<IRMedia> = emptyList(),
        children: List<IRComponent>? = null,
    ) = IRComponent(
        id = id,
        name = id,
        properties = properties,
        selectors = selectors,
        media = media,
        children = children,
    )

    // ── the state machine ─────────────────────────────────────────────────

    @Test
    fun `idle coordinator disables every hook`() {
        val c = BackdropPassCoordinator()
        assertFalse(c.enabled)
        assertEquals(BackdropPass.DISABLED, c.pass)
        assertNull(c.backdrop)
    }

    @Test
    fun `arming a backdrop document starts in the sample pass`() {
        val c = BackdropPassCoordinator()
        c.beginDocument(declaresBackdropFilter = true)
        assertTrue(c.enabled)
        assertEquals(BackdropPass.SAMPLE, c.pass)
        // Nothing to sample from yet — elements must not draw a patch during
        // pass A even if they somehow reach the painter.
        assertNull(c.backdrop)
    }

    @Test
    fun `arming a plain document stays disabled`() {
        val c = BackdropPassCoordinator()
        c.beginDocument(declaresBackdropFilter = false)
        assertFalse(c.enabled)
        assertEquals(BackdropPass.DISABLED, c.pass)
    }

    @Test
    fun `reset clears the previous fixture's backdrop`() {
        // A leaked bitmap would filter the NEXT fixture against the previous
        // document's pixels — the failure mode this guards.
        val c = BackdropPassCoordinator()
        c.beginDocument(true)
        c.publishCanvasOrigin(Offset(0f, 12f))
        c.reset()
        assertFalse(c.enabled)
        assertEquals(BackdropPass.DISABLED, c.pass)
        assertNull(c.backdrop)
        assertEquals(Offset.Zero, c.canvasOriginInWindow)
    }

    @Test
    fun `canvas origin is published for the window to image mapping`() {
        val c = BackdropPassCoordinator()
        c.publishCanvasOrigin(Offset(0f, 63f))
        assertEquals(Offset(0f, 63f), c.canvasOriginInWindow)
    }

    // ── the IR gate ───────────────────────────────────────────────────────

    @Test
    fun `document without backdrop filter is not armed`() {
        val roots = listOf(
            component("a", properties = listOf(prop("BackgroundColor"), prop("Filter"))),
        )
        assertFalse(documentDeclaresBackdropFilter(roots))
    }

    @Test
    fun `backdrop filter on a root is found`() {
        val roots = listOf(component("a", properties = listOf(prop("BackdropFilter"))))
        assertTrue(documentDeclaresBackdropFilter(roots))
    }

    @Test
    fun `backdrop filter nested two levels down is found`() {
        // backdrop-filter-basic.html's shape: .colorbox > .filterbox.
        val roots = listOf(
            component(
                "colorbox",
                properties = listOf(prop("BackgroundColor")),
                children = listOf(
                    component(
                        "wrapper",
                        children = listOf(
                            component("filterbox", properties = listOf(prop("BackdropFilter"))),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(documentDeclaresBackdropFilter(roots))
    }

    @Test
    fun `conditional and media declarations are found`() {
        // A :hover- or media-only backdrop-filter still needs the two-pass
        // host once its condition resolves; over-answering true costs one
        // empty extra pass, under-answering drops the feature silently.
        val hover = listOf(
            component(
                "a",
                selectors = listOf(IRSelector("hover", listOf(prop("BackdropFilter")))),
            ),
        )
        assertTrue(documentDeclaresBackdropFilter(hover))
        val media = listOf(
            component(
                "a",
                media = listOf(IRMedia("(min-width: 100px)", listOf(prop("BackdropFilter")))),
            ),
        )
        assertTrue(documentDeclaresBackdropFilter(media))
    }

    @Test
    fun `empty document is not armed`() {
        assertFalse(documentDeclaresBackdropFilter(emptyList()))
    }
}
