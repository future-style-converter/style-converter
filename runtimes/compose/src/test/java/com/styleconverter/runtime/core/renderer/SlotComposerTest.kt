package com.styleconverter.runtime.core.renderer

// SlotComposer unit pinning — the composer edge cases of spec 03 that
// aren't reachable from converter-emitted goldens (which are always
// well-formed): dangling parents, cycles, deep chains, name funneling.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocument
import com.styleconverter.runtime.core.ir.IRSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SlotComposerTest {

    private fun c(id: String, parent: String? = null, slotName: String = "content") =
        IRComponent(id = id, name = id.uppercase(),
            slot = parent?.let { IRSlot(parent = it, name = slotName) })

    @Test
    fun `sibling order rule — flat array order is composition order`() {
        val roots = SlotComposer.compose(IRDocument(listOf(
            c("r"), c("a", parent = "r"), c("b", parent = "r"), c("x", parent = "a")
        )))
        assertEquals(1, roots.size)
        assertEquals(listOf("a", "b"), roots[0].children!!.map { it.id })
        assertEquals(listOf("x"), roots[0].children!![0].children!!.map { it.id })
    }

    @Test
    fun `multiple roots keep their flat-array order`() {
        val roots = SlotComposer.compose(IRDocument(listOf(
            c("r1"), c("k1", parent = "r2"), c("r2"), c("k2", parent = "r1")
        )))
        // Root order = array order of the slot-free entries; children
        // attach to their parents regardless of where they appear.
        assertEquals(listOf("r1", "r2"), roots.map { it.id })
        assertEquals(listOf("k2"), roots[0].children!!.map { it.id })
        assertEquals(listOf("k1"), roots[1].children!!.map { it.id })
    }

    @Test
    fun `dangling parent promotes to root with no children mutation`() {
        val roots = SlotComposer.compose(IRDocument(listOf(
            c("r"), c("lost", parent = "nope")
        )))
        assertEquals(listOf("r", "lost"), roots.map { it.id })
        assertNull(roots[0].children)
        assertNull(roots[1].children)
    }

    @Test
    fun `cycle breaks at first member in array order instead of vanishing`() {
        // a→b→a: neither reaches a root; the composer must promote the
        // first cycle member (array order) and keep the rest reachable.
        val roots = SlotComposer.compose(IRDocument(listOf(
            c("r"), c("a", parent = "b"), c("b", parent = "a")
        )))
        assertEquals(listOf("r", "a"), roots.map { it.id })
        // b stays composed under a after the break.
        assertEquals(listOf("b"), roots[1].children!!.map { it.id })
    }

    @Test
    fun `duplicate ids error loudly`() {
        try {
            SlotComposer.compose(IRDocument(listOf(c("a"), c("a", parent = "a"))))
            fail("expected duplicate-id error")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("duplicate"))
        }
    }

    @Test
    fun `non-default slot names funnel into the single children list today`() {
        // Multi-slot containers are a v2 reservation: until a container
        // declares named slots, every child lands in the one children
        // list, in array order — name is carried but not yet routed.
        val roots = SlotComposer.compose(IRDocument(listOf(
            c("r"), c("h", parent = "r", slotName = "header"), c("b", parent = "r")
        )))
        assertEquals(listOf("h", "b"), roots[0].children!!.map { it.id })
        assertEquals("header", roots[0].children!![0].slot!!.name)
    }

    @Test
    fun `slot-free documents pass through untouched (v1 nested and Mode B)`() {
        // Mode B: flat, no slots — everything is a root, list unchanged.
        val flat = listOf(c("a"), c("b"))
        assertEquals(flat, SlotComposer.compose(IRDocument(flat)))
        // v1 nested: children already in place — identity as well.
        val nested = listOf(IRComponent(id = "p", name = "P",
            children = listOf(IRComponent(id = "k", name = "K"))))
        assertEquals(nested, SlotComposer.compose(IRDocument(nested)))
    }
}
