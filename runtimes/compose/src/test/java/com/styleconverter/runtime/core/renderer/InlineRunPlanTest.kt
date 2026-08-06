package com.styleconverter.runtime.core.renderer

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Wave-32 lane R — the `meta.runs` render plan (InlineRunPlan.kt).
 *
 * WHY THIS EXISTS. `_text` is ONE string and the child list has ONE order,
 * so the wire could say `run + children` or `children + run` but never
 * `run / child / run`. Through wave-31 the extractor marked the difference
 * loudly (`inline-run-reordered`, 1,203 components / 472 fixtures) and
 * shipped the concatenation. The canonical victim is
 * CSS2/abspos/static-inside-inline-001 — `<span><div id=abspos></div> X
 * </span>` — where the whole assertion is box-vs-text order: with the div
 * first the preceding inline fragment is empty (zero-height line box,
 * §9.4.2) and the abspos' static top is 0; with 'X' first it is 100.
 *
 * The plan is a PURE function of the wire list and the composed children,
 * which is exactly why it is unit-testable without a device: the three
 * rules a renderer owns (referenced-once, unreferenced-still-render,
 * dangling-warn-and-skip) all live here.
 */
class InlineRunPlanTest {

    private fun comp(id: String, name: String) =
        IRComponent(id = id, name = name, properties = emptyList())

    private val kids = listOf(comp("a-id", "a-name"), comp("b-id", "b-name"))

    @Test
    fun `absent, empty and childless inputs fall back to the default path`() {
        // null (not an empty plan) is what keeps every pre-wave-32
        // composition byte-identical.
        assertNull(InlineRunPlan.resolve(null, kids))
        assertNull(InlineRunPlan.resolve(emptyList(), kids))
        assertNull(InlineRunPlan.resolve(listOf(IRRun(text = "x")), emptyList()))
    }

    @Test
    fun `a child resolves by its AUTHORING KEY, falling back to id`() {
        // The reference is the child's `name` on the converter-emitted wire
        // — the converter mints ids at the flatten boundary, so a
        // producer-written id would name nothing after the hop.
        assertEquals(
            listOf(InlineRunPlan.Entry.Child(1)),
            InlineRunPlan.resolve(listOf(IRRun(child = "b-name")), kids)!!.entries
        )
        // …and in the extractor-direct pipeline key == name == id, so the id
        // spelling must resolve identically rather than dangle.
        assertEquals(
            listOf(InlineRunPlan.Entry.Child(0)),
            InlineRunPlan.resolve(listOf(IRRun(child = "a-id")), kids)!!.entries
        )
    }

    @Test
    fun `document order is preserved and leftovers are reported`() {
        val plan = InlineRunPlan.resolve(
            listOf(IRRun(text = "lead"), IRRun(child = "a-name")), kids
        )!!
        assertEquals(
            listOf(InlineRunPlan.Entry.Text("lead"), InlineRunPlan.Entry.Child(0)),
            plan.entries
        )
        // Rule 4 — a child the list did not name still renders, after the
        // runs. A partial list can never make a box disappear.
        assertEquals(listOf(1), plan.unreferenced)
    }

    @Test
    fun `the glue case interleaves text, child, text`() {
        // `the quick <u>brown</u> fox` with a surviving <u>: the shape the
        // single `_text` string cannot express.
        val plan = InlineRunPlan.resolve(
            listOf(IRRun(text = "the quick "), IRRun(child = "a-name"), IRRun(text = " fox")),
            kids
        )!!
        assertEquals(
            listOf(
                InlineRunPlan.Entry.Text("the quick "),
                InlineRunPlan.Entry.Child(0),
                InlineRunPlan.Entry.Text(" fox")
            ),
            plan.entries
        )
    }

    @Test
    fun `a whitespace-only run survives but an empty one is dropped`() {
        // Rule 6 — a single space between two boxes IS the inter-run word
        // space and has real advance width; a zero-length run has neither
        // glyphs nor a box and would only add an empty Text node.
        val plan = InlineRunPlan.resolve(
            listOf(IRRun(child = "a-name"), IRRun(text = " "), IRRun(text = ""),
                   IRRun(child = "b-name")),
            kids
        )!!
        assertEquals(
            listOf(
                InlineRunPlan.Entry.Child(0),
                InlineRunPlan.Entry.Text(" "),
                InlineRunPlan.Entry.Child(1)
            ),
            plan.entries
        )
    }

    @Test
    fun `a dangling key is skipped, every other position kept`() {
        // Rule 5, mirroring the dangling-slot.parent rule: warn-and-skip,
        // never a throw — a droppable hint may not make a page unrenderable.
        val plan = InlineRunPlan.resolve(
            listOf(IRRun(text = "a"), IRRun(child = "nobody"), IRRun(text = "b")), kids
        )!!
        assertEquals(
            listOf(InlineRunPlan.Entry.Text("a"), InlineRunPlan.Entry.Text("b")),
            plan.entries
        )
        // Neither child was claimed, so both still render.
        assertEquals(listOf(0, 1), plan.unreferenced)
    }

    @Test
    fun `a duplicate reference paints once, at its FIRST position`() {
        val plan = InlineRunPlan.resolve(
            listOf(IRRun(child = "a-name"), IRRun(text = "x"), IRRun(child = "a-name")), kids
        )!!
        assertEquals(
            listOf(InlineRunPlan.Entry.Child(0), InlineRunPlan.Entry.Text("x")),
            plan.entries
        )
        assertEquals(listOf(1), plan.unreferenced)
    }

    @Test
    fun `a fully dangling list falls back rather than rendering nothing`() {
        // The honest failure mode: the wire asked for an order we could not
        // reconstruct, so paint the pre-wave-32 approximation, not an empty
        // container.
        assertNull(InlineRunPlan.resolve(listOf(IRRun(child = "nobody")), kids))
    }
}
