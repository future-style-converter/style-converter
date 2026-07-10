package app.parsing

// IRFlattener contract tests (schema/spec/03-children.md):
//  - pre-order traversal (parent before children, subtrees contiguous,
//    source order among siblings) so flat order == composition order
//  - slot.parent stamped on every non-root, roots slot-free
//  - children stripped everywhere (v2 is flat-only)
//  - duplicate id = convert-time ERROR (ambiguous slot target)
//  - the real fixtures/fidelity/trees/nested-3level.json flattens to the
//    18-component document with a max slot-walk depth of exactly 3
//    (the freeze's headline verification case).

import app.irmodels.IRComponent
import app.irmodels.IRDocument
import app.irmodels.IRSlot
import app.parsing.css.cssParsing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IRFlattenerTest {

    // Tiny builder for nested test trees — properties stay empty because
    // the flattener only touches structure, never property payloads.
    private fun comp(id: String, vararg children: IRComponent) = IRComponent(
        id = id,
        name = id.uppercase(),
        properties = mutableListOf(),
        children = children.toList().ifEmpty { null }
    )

    @Test
    fun `pre-order - parent first, subtrees contiguous, sibling source order kept`() {
        // root(a(b, c), d) — pre-order is root, a, b, c, d.
        val doc = IRDocument(listOf(comp("root", comp("a", comp("b"), comp("c")), comp("d"))))
        val flat = IRFlattener.flatten(doc)
        assertEquals(listOf("root", "a", "b", "c", "d"), flat.components.map { it.id })
    }

    @Test
    fun `slot parent stamped on children, roots stay slot-free`() {
        val doc = IRDocument(listOf(comp("root", comp("a", comp("b")))))
        val flat = IRFlattener.flatten(doc)
        val byId = flat.components.associateBy { it.id }
        // Root has no slot (spec 03: omit slot entirely for roots).
        assertNull(byId["root"]!!.slot)
        // Each child points at its immediate parent, default slot name.
        assertEquals(IRSlot(parent = "root"), byId["a"]!!.slot)
        assertEquals("content", byId["a"]!!.slot!!.name)
        assertEquals(IRSlot(parent = "a"), byId["b"]!!.slot)
    }

    @Test
    fun `children stripped from every flattened entry`() {
        val doc = IRDocument(listOf(comp("root", comp("a", comp("b")))))
        val flat = IRFlattener.flatten(doc)
        // The v2 wire has no children key; the in-memory flat form mirrors
        // that with null children on every entry.
        flat.components.forEach { assertNull(it.children, "children must be stripped on '${it.id}'") }
    }

    @Test
    fun `multiple roots keep document source order, each subtree contiguous`() {
        val doc = IRDocument(listOf(comp("r1", comp("r1c")), comp("r2", comp("r2c"))))
        val flat = IRFlattener.flatten(doc)
        assertEquals(listOf("r1", "r1c", "r2", "r2c"), flat.components.map { it.id })
    }

    @Test
    fun `duplicate id anywhere in the document is a convert error`() {
        // A deep grandchild clashing with a sibling root is just as
        // ambiguous as two adjacent siblings clashing — both must throw.
        val doc = IRDocument(listOf(comp("root", comp("dup")), comp("dup")))
        val ex = assertFailsWith<IllegalArgumentException> { IRFlattener.flatten(doc) }
        assertTrue(ex.message!!.contains("duplicate component id 'dup'"))
    }

    @Test
    fun `flatten is idempotent - an already-flat document passes through unchanged`() {
        val flatInput = IRDocument(listOf(
            comp("root"),
            comp("a").copy(slot = IRSlot(parent = "root"))
        ))
        val out = IRFlattener.flatten(flatInput)
        // Existing slots survive; order untouched; nothing restamped.
        assertEquals(flatInput.components, out.components)
    }

    @Test
    fun `nested-3level fixture flattens to 18 components with slot-walk depth 3`() {
        // Locate the repo root from the test working dir (converter/) by
        // walking up until the fixture appears — same technique as
        // SchemaConformanceTest.
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "fixtures/fidelity/trees/nested-3level.json").exists()) dir = dir.parentFile
        val fixture = File(dir ?: error("repo root not found"), "fixtures/fidelity/trees/nested-3level.json")

        // Full real pipeline: authoring JSON → cssParsing (nested tree) →
        // IRFlattener (flat v2 form). This is the freeze's verification case.
        val nested = cssParsing(Json.parseToJsonElement(fixture.readText()).jsonObject)
        val flat = IRFlattener.flatten(nested)

        // 18 components total (3 roots × their subtrees), all flat.
        assertEquals(18, flat.components.size)
        flat.components.forEach { assertNull(it.children) }

        // Depth via slot walk: leaf → parent → ... → root. Max must be
        // exactly 3 (root, row, cell) for this 3-level tree.
        val byId = flat.components.associateBy { it.id }
        fun depth(c: IRComponent): Int {
            var node = c
            var d = 1
            while (node.slot != null) {
                node = byId[node.slot!!.parent] ?: error("dangling slot parent '${node.slot!!.parent}'")
                d++
            }
            return d
        }
        assertEquals(3, flat.components.maxOf { depth(it) })

        // Sibling-order rule sanity: the first root's children appear in
        // source order immediately interleaved with their own subtrees
        // (pre-order), and every child's slot resolves to a component that
        // appeared EARLIER in the array (parents precede children).
        val indexOf = flat.components.withIndex().associate { (i, c) -> c.id to i }
        flat.components.forEach { c ->
            c.slot?.let { s ->
                assertTrue(indexOf[s.parent]!! < indexOf[c.id]!!, "parent '${s.parent}' must precede child '${c.id}'")
            }
        }
    }
}
