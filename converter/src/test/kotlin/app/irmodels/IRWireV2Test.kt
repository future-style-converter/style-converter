package app.irmodels

// IRWireV2 codec tests — pins the v2 envelope and component byte shapes
// (schema/ir-v2.schema.json) and the structural round-trip guarantees:
// slot MUST round-trip (it is structural, not a droppable hint), the
// version pair is mandatory, and `children` is a hard error everywhere.

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IRWireV2Test {

    private val json = Json

    private fun comp(
        id: String,
        slot: IRSlot? = null,
        text: String? = null,
        role: String? = null,
        tag: String? = null,
        pseudos: JsonObject? = null
    ) = IRComponent(
        id = id, name = "N_$id", properties = mutableListOf(),
        slot = slot, text = text, role = role, tag = tag, pseudos = pseudos
    )

    // ---- envelope ----

    @Test
    fun `document envelope carries the version pair and flat components`() {
        val doc = IRDocument(listOf(comp("a"), comp("b", slot = IRSlot("a"))))
        val obj = IRWireV2.encodeDocument(doc)
        // Version pair: writer stamps both (spec 01 v2 table).
        assertEquals(2, obj["irVersion"]!!.jsonPrimitive.int)
        assertEquals(2, obj["minReaderVersion"]!!.jsonPrimitive.int)
        // Exactly the three envelope keys — nothing else at top level.
        assertEquals(setOf("irVersion", "minReaderVersion", "components"), obj.keys)
        assertEquals(2, obj["components"]!!.jsonArray.size)
    }

    @Test
    fun `encode refuses a document still carrying nested children`() {
        val nested = comp("root").copy(children = listOf(comp("kid")))
        val ex = assertFailsWith<IllegalArgumentException> {
            IRWireV2.encodeDocument(IRDocument(listOf(nested)))
        }
        assertTrue(ex.message!!.contains("flat"))
    }

    // ---- component field shapes ----

    @Test
    fun `slot emits parent and omits the default content name`() {
        val obj = IRWireV2.encodeDocument(IRDocument(listOf(comp("a"), comp("b", slot = IRSlot("a")))))
        val b = obj["components"]!!.jsonArray[1].jsonObject
        val slot = b["slot"]!!.jsonObject
        assertEquals("a", slot["parent"]!!.jsonPrimitive.content)
        // Default name "content" stays off the wire (schema documents the default).
        assertEquals(setOf("parent"), slot.keys)
    }

    @Test
    fun `non-default slot name is emitted`() {
        val obj = IRWireV2.encodeDocument(IRDocument(listOf(comp("a"), comp("b", slot = IRSlot("a", "header")))))
        val slot = obj["components"]!!.jsonArray[1].jsonObject["slot"]!!.jsonObject
        assertEquals("header", slot["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `v2 renames - text is unprefixed and tag role group under meta`() {
        val obj = IRWireV2.encodeDocument(IRDocument(listOf(
            comp("a", text = "hello", role = "body-root", tag = "ol")
        )))
        val c = obj["components"]!!.jsonArray[0].jsonObject
        // _text → text (structural content field).
        assertEquals("hello", c["text"]!!.jsonPrimitive.content)
        assertFalse("_text" in c)
        // _tag + _role → meta:{sourceTag, role} (grouped droppable hints).
        val meta = c["meta"]!!.jsonObject
        assertEquals("ol", meta["sourceTag"]!!.jsonPrimitive.content)
        assertEquals("body-root", meta["role"]!!.jsonPrimitive.content)
        assertFalse("_role" in c)
        assertFalse("_tag" in c)
    }

    @Test
    fun `meta is omitted entirely when no hint has a value`() {
        val obj = IRWireV2.encodeDocument(IRDocument(listOf(comp("a"))))
        val c = obj["components"]!!.jsonArray[0].jsonObject
        assertFalse("meta" in c)
        assertFalse("text" in c)
        assertFalse("slot" in c)
        assertFalse("pseudos" in c)
    }

    @Test
    fun `pseudos payload is forwarded verbatim under the renamed key`() {
        // Opaque extractor-shaped payload — the converter never interprets it.
        val payload = buildJsonObject {
            put("before", buildJsonObject { putJsonArray("properties") {}; put("_text", "»") })
        }
        val obj = IRWireV2.encodeDocument(IRDocument(listOf(comp("a", pseudos = payload))))
        val c = obj["components"]!!.jsonArray[0].jsonObject
        assertEquals(payload, c["pseudos"]!!.jsonObject)
        assertFalse("_pseudo" in c)
    }

    // ---- round-trip (slot is structural: MUST survive decode) ----

    @Test
    fun `structural fields round-trip through encode-decode`() {
        val doc = IRDocument(listOf(
            comp("a", text = "root text", role = "body-root", tag = "section"),
            comp("b", slot = IRSlot("a", "header")),
            comp("c", slot = IRSlot("a"))
        ))
        val decoded = IRWireV2.decodeDocument(IRWireV2.encodeDocument(doc))
        assertEquals(3, decoded.components.size)
        val byId = decoded.components.associateBy { it.id }
        // slot round-trips exactly — including the default-name reconstruction.
        assertNull(byId["a"]!!.slot)
        assertEquals(IRSlot("a", "header"), byId["b"]!!.slot)
        assertEquals(IRSlot("a", "content"), byId["c"]!!.slot)
        // renamed metadata fields round-trip.
        assertEquals("root text", byId["a"]!!.text)
        assertEquals("body-root", byId["a"]!!.role)
        assertEquals("section", byId["a"]!!.tag)
    }

    // ---- hard errors on decode ----

    @Test
    fun `decoding a component with a children key is a hard error`() {
        val bad = buildJsonObject {
            put("irVersion", 2); put("minReaderVersion", 2)
            putJsonArray("components") {
                add(buildJsonObject {
                    put("id", "a"); put("name", "A"); putJsonArray("properties") {}
                    putJsonArray("children") {}
                })
            }
        }
        val ex = assertFailsWith<IllegalArgumentException> { IRWireV2.decodeDocument(bad) }
        assertTrue(ex.message!!.contains("children"))
    }

    @Test
    fun `decoding without the version pair is refused`() {
        // Missing irVersion = a v1 document — belongs to the legacy reader.
        assertFailsWith<IllegalArgumentException> {
            IRWireV2.decodeDocument(buildJsonObject { putJsonArray("components") {} })
        }
    }

    @Test
    fun `reader refuses a document demanding a newer reader`() {
        val future = buildJsonObject {
            put("irVersion", 2)
            put("minReaderVersion", 99) // demands a reader we don't implement
            putJsonArray("components") {}
        }
        assertFailsWith<IllegalArgumentException> { IRWireV2.decodeDocument(future) }
    }
}
