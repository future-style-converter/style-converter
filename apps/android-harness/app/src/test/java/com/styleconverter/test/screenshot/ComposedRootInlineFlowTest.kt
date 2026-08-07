package com.styleconverter.test.screenshot

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.layout.InlineBlockAtom
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * wave-34 lane H (H1) — unit pins for [composedRootInlineBoxes], the composed
 * canvas's per-ROOT read of the runtime's inline-block atom facade.
 *
 * WHY it exists: CSS2/abspos/static-inside-inline-block's three
 * `display: inline-block` boxes are document ROOTS. The composed canvas
 * stacks roots in the harness's own Column, which is not a runtime block box,
 * so the wave-33 nested inline-atom lane never saw them and the boxes were
 * block-STACKED — Android 0.8965 / iOS 0.9088 against a web at 0.9803.
 *
 * WHAT THIS FILE PINS: only the harness's half — which wire facts each root
 * contributes (B6's text/runs, B7's body-root line-height) and that a
 * run-free document gets NO plan, so the frozen stacking loop keeps running
 * verbatim. The gate table itself and the §9.4.2 packing are pinned in the
 * runtime's InlineBlockAtomTest / InlineBlockAtomTests.swift, which is where
 * they live — this test must never re-derive them.
 *
 * Plain junit:4.13.2 — the resolver is a pure function over the decoded IR.
 */
class ComposedRootInlineFlowTest {

    // ── the target document ──────────────────────────────────────────────

    @Test fun `the target document's three inline-block roots are atoms`() {
        // CSS2/abspos/static-inside-inline-block, verbatim: a prose <p> root
        // then three 100x100 declared inline-blocks (the middle one holding
        // the abspos green square as a CHILD, which generates no line box).
        val boxes = composedRootInlineBoxes(
            listOf(
                prose("Test passes if there is a filled green square and no red."),
                inlineBlock(100.0, 100.0),
                inlineBlock(100.0, 100.0),
                inlineBlock(100.0, 100.0),
            )
        )
        assertNull("the prose root is not an atom", boxes[0])
        for (i in 1..3) {
            assertEquals("root $i", InlineBlockAtom.RootBox(100.0, 100.0), boxes[i])
        }
        // …and they segment into [<p>] + one 3-member run.
        val segs = InlineBlockAtom.rootSegments(boxes, listOf(16.0, 16.0, 0.0, 0.0))
        assertNotNull(segs)
        assertEquals(listOf(1, 2, 3), segs!!.single { it.isRun }.indices)
    }

    // ── B6: own line-box content leaves the lane ─────────────────────────

    @Test fun `a root with its own text is not an atom`() {
        // §10.8.1 — own line boxes move the baseline off the bottom margin
        // edge, which is the whole reason B6 exists.
        val boxes = composedRootInlineBoxes(
            listOf(
                inlineBlock(100.0, 100.0).copy(_text = "hello"),
                inlineBlock(100.0, 100.0),
            )
        )
        assertNull(boxes[0])
        assertNotNull(boxes[1])
        // One atom is not a run, so the harness keeps the frozen stack.
        assertNull(InlineBlockAtom.rootSegments(boxes, listOf(0.0, 0.0)))
    }

    // ── B7: the document body's own line box ─────────────────────────────

    @Test fun `a body-root line-height refuses every root atom`() {
        // The packer's 16/4 strut pins are solved for the ref's INJECTED
        // body line box; an author override invalidates them, exactly like
        // absolute-tables-013's `line-height: 0` <td> in the nested lane.
        val body = IRComponent(
            id = "body", name = "body",
            properties = listOf(prop("LineHeight", """{"type":"length","px":0}""")),
            role = "body-root",
        )
        val boxes = composedRootInlineBoxes(
            listOf(body, inlineBlock(100.0, 100.0), inlineBlock(100.0, 100.0))
        )
        assertNull(boxes[1])
        assertNull(boxes[2])
        // Without the body-root declaration the same two roots ARE atoms —
        // this is the control that proves the gate is the line-height, not
        // the extra leading root.
        val control = composedRootInlineBoxes(
            listOf(
                IRComponent(id = "body", name = "body", role = "body-root"),
                inlineBlock(100.0, 100.0), inlineBlock(100.0, 100.0),
            )
        )
        assertNotNull(control[1])
        assertNotNull(control[2])
    }

    // ── the frozen path ──────────────────────────────────────────────────

    @Test fun `an ordinary document produces no boxes and therefore no plan`() {
        // The overwhelming majority of the composed corpus: nothing declares
        // inline-block at root level, so every entry is null, rootSegments
        // returns null, and ComposedCaptureCanvas runs its frozen loop.
        val boxes = composedRootInlineBoxes(
            listOf(prose("a"), block(100.0, 100.0), prose("b"))
        )
        assertEquals(listOf(null, null, null), boxes)
        assertNull(InlineBlockAtom.rootSegments(boxes, listOf(16.0, 0.0, 16.0)))
    }

    // ── wire helpers ─────────────────────────────────────────────────────

    private fun prop(type: String, json: String) =
        IRProperty(type = type, data = Json.parseToJsonElement(json))

    private fun len(type: String, px: Double) =
        prop(type, """{"type":"length","px":$px}""")

    /** A `<p>`-shaped prose root: text, no declared display. */
    private fun prose(text: String) =
        IRComponent(id = "p", name = "p", _text = text, _tag = "p")

    /** A declared `display: inline-block` root with a definite border box. */
    private fun inlineBlock(w: Double, h: Double) = IRComponent(
        id = "ib", name = "ib",
        properties = listOf(prop("Display", "\"INLINE_BLOCK\""), len("Width", w), len("Height", h)),
    )

    /** The same box declared `display: block` — the B1 control. */
    private fun block(w: Double, h: Double) = IRComponent(
        id = "b", name = "b",
        properties = listOf(prop("Display", "\"BLOCK\""), len("Width", w), len("Height", h)),
    )
}
