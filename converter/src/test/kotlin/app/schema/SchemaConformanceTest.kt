package app.schema

// Converter-side conformance suite for the IR v1 wire contract
// (schema/ir-v1.schema.json + schema/spec/*). The JS runner
// (schema/conformance/run.mjs) validates documents with ajv; inside the
// JVM we skip ajv and instead assert the emitted JsonObject shapes
// directly for each sentinel family — this pins that SERIALIZING real
// parse results still reproduces the exact wire forms the golden
// fixtures document.
//
// Ground truth being pinned:
//  - IRLengthSerializer dual storage        (ValueTypes.kt, spec 02)
//  - PaddingValue/MarginValue escapes       (ValueTypes.kt, spec 02)
//  - ShapeRadius keyword/length asymmetry   (ClipPathSerializers.kt, spec 02)
//  - IRComponentSerializer field omission   (IRDocument.kt, spec 01/04)
//  - children map-in / array-out            (CssParsing.kt, spec 03)
//  - {type,data} property envelope          (IRPropertySerializer.kt, spec 01)

import app.irmodels.IRComponent
import app.irmodels.IRDocument
import app.parsing.css.cssParsing
import app.parsing.css.properties.GenericProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchemaConformanceTest {

    // Parse a raw authoring-side JSON string through the REAL pipeline
    // (cssParsing) and serialize the resulting IRDocument back to a
    // JsonObject — i.e. exactly the bytes `--to ir` writes to disk.
    private fun emit(input: String): JsonObject {
        val doc = cssParsing(Json.parseToJsonElement(input).jsonObject)
        return Json.encodeToJsonElement(IRDocument.serializer(), doc).jsonObject
    }

    // Convenience: first component of the emitted document.
    private fun firstComponent(input: String): JsonObject =
        emit(input)["components"]!!.jsonArray[0].jsonObject

    // Convenience: data payload of the Nth property of the first component.
    private fun data(input: String, index: Int = 0) =
        firstComponent(input)["properties"]!!.jsonArray[index].jsonObject["data"]!!

    // ---- lengths (spec 02, lengths-all-forms.json) ----

    @Test
    fun `px length emits px-only object`() {
        val d = data("""{"components":{"C":{"properties":{"padding-top":"16px"}}}}""").jsonObject
        assertEquals(16.0, d["px"]!!.jsonPrimitive.double)
        // px-only: absolutely no "original" for a native-px input.
        assertEquals(setOf("px"), d.keys)
    }

    @Test
    fun `pt length emits dual storage with original v-u`() {
        val d = data("""{"components":{"C":{"properties":{"padding-top":"12pt"}}}}""").jsonObject
        assertEquals(16.0, d["px"]!!.jsonPrimitive.double)          // 12pt * 96/72 = 16px
        val original = d["original"]!!.jsonObject
        assertEquals(12.0, original["v"]!!.jsonPrimitive.double)
        assertEquals("PT", original["u"]!!.jsonPrimitive.content)
    }

    @Test
    fun `em length emits original-only — no px key for relative units`() {
        val d = data("""{"components":{"C":{"properties":{"padding-top":"2em"}}}}""").jsonObject
        assertNull(d["px"], "relative units are runtime-dependent — px must be absent")
        assertEquals("EM", d["original"]!!.jsonObject["u"]!!.jsonPrimitive.content)
    }

    @Test
    fun `percent padding emits a raw number primitive`() {
        val d = data("""{"components":{"C":{"properties":{"padding-left":"10%"}}}}""")
        assertTrue(d is JsonPrimitive && !d.isString, "percent escape is a bare number")
        assertEquals(10.0, d.jsonPrimitive.double)
    }

    @Test
    fun `auto margin emits a raw string primitive`() {
        val d = data("""{"components":{"C":{"properties":{"margin-left":"auto"}}}}""")
        assertEquals("auto", d.jsonPrimitive.content)
    }

    // ---- ShapeRadius asymmetry (spec 02, shape-radius.json) ----

    @Test
    fun `circle keyword radius stays a string under r`() {
        val d = data("""{"components":{"C":{"properties":{"clip-path":"circle(farthest-side)"}}}}""").jsonObject
        assertEquals("circle", d["type"]!!.jsonPrimitive.content)
        assertEquals("farthest-side", d["r"]!!.jsonPrimitive.content)
    }

    @Test
    fun `circle length radius deep-flattens — px inlined, r key gone`() {
        val d = data("""{"components":{"C":{"properties":{"clip-path":"circle(40px)"}}}}""").jsonObject
        assertEquals("circle", d["type"]!!.jsonPrimitive.content)
        assertEquals(40.0, d["px"]!!.jsonPrimitive.double)
        assertNull(d["r"], "deep-flatten inlines the single {px} object; r must vanish (spec 02)")
    }

    @Test
    fun `ellipse two-axis keywords keep rx and ry — no flattening with two fields`() {
        val d = data("""{"components":{"C":{"properties":{"clip-path":"ellipse(closest-side farthest-side)"}}}}""").jsonObject
        assertEquals("closest-side", d["rx"]!!.jsonPrimitive.content)
        assertEquals("farthest-side", d["ry"]!!.jsonPrimitive.content)
    }

    // ---- metadata omission (spec 01/04, text-and-role.json) ----

    @Test
    fun `_text and _role are forwarded when present and omitted when absent`() {
        val with = firstComponent(
            """{"components":{"C":{"properties":{"color":"green"},"_text":"Test passes","_role":"body-root"}}}"""
        )
        assertEquals("Test passes", with["_text"]!!.jsonPrimitive.content)
        assertEquals("body-root", with["_role"]!!.jsonPrimitive.content)

        val without = firstComponent("""{"components":{"C":{"properties":{"color":"green"}}}}""")
        assertFalse("_text" in without, "omit-when-null keeps legacy fixtures byte-stable")
        assertFalse("_role" in without)
    }

    @Test
    fun `selectors and media are omitted when empty and strict when present`() {
        val cmp = firstComponent(
            """{"components":{"C":{
                 "properties":{"background-color":"#ffffff"},
                 "selectors":[{"selector":":hover","properties":{"background-color":"#000000"}}],
                 "media":[{"query":"(min-width: 768px)","properties":{"width":"100px"}}]
               }}}"""
        )
        val selector = cmp["selectors"]!!.jsonArray[0].jsonObject
        // Condition is stored WITHOUT the leading colon (spec 01).
        assertEquals("hover", selector["condition"]!!.jsonPrimitive.content)
        assertEquals(setOf("condition", "properties"), selector.keys)
        val media = cmp["media"]!!.jsonArray[0].jsonObject
        assertEquals("(min-width: 768px)", media["query"]!!.jsonPrimitive.content)

        val bare = firstComponent("""{"components":{"C":{"properties":{"width":"1px"}}}}""")
        assertFalse("selectors" in bare)
        assertFalse("media" in bare)
    }

    // ---- children map-in / array-out (spec 03, children-nesting.json) ----

    @Test
    fun `children map input flattens to an array with map key as name`() {
        val cmp = firstComponent(
            """{"components":{"Parent":{
                 "properties":{"display":"flex"},
                 "children":{"child__0":{"properties":{"width":"50px"},
                   "children":{"grandchild__0__0":{"properties":{"height":"10px"},"_text":"leaf text"}}}}
               }}}"""
        )
        val children = cmp["children"]!!
        assertTrue(children is JsonArray, "wire children must be an ARRAY (map is input-only)")
        val child = children.jsonArray[0].jsonObject
        assertEquals("child__0", child["name"]!!.jsonPrimitive.content)
        val grandchild = child["children"]!!.jsonArray[0].jsonObject
        assertEquals("leaf text", grandchild["_text"]!!.jsonPrimitive.content)
    }

    // ---- property envelope (spec 01, unknown-property-tolerance.json) ----

    @Test
    fun `every emitted property is exactly a type-data envelope`() {
        val doc = emit(
            """{"components":{"C":{"properties":{
                 "padding-top":"16px","margin-left":"auto","font-weight":"bold",
                 "display":"none","opacity":"0.5"}}}}"""
        )
        for (component in doc["components"]!!.jsonArray) {
            for (prop in component.jsonObject["properties"]!!.jsonArray) {
                assertEquals(setOf("type", "data"), prop.jsonObject.keys, "no extra envelope keys allowed")
            }
        }
    }

    @Test
    fun `GenericProperty serializes with the _unmapped marker under type Generic`() {
        // Generic only triggers for valid-but-unparsed CSS names, so pin the
        // serializer branch directly (IRPropertySerializer's GenericProperty case).
        val doc = IRDocument(
            components = listOf(
                IRComponent(
                    id = "generic-001",
                    name = "GenericHost",
                    properties = mutableListOf(GenericProperty("hanging-punctuation", "first allow-end"))
                )
            )
        )
        val prop = Json.encodeToJsonElement(IRDocument.serializer(), doc)
            .jsonObject["components"]!!.jsonArray[0]
            .jsonObject["properties"]!!.jsonArray[0].jsonObject
        assertEquals("Generic", prop["type"]!!.jsonPrimitive.content)
        val d = prop["data"]!!.jsonObject
        assertEquals("hanging-punctuation", d["propertyName"]!!.jsonPrimitive.content)
        assertEquals("first allow-end", d["rawValue"]!!.jsonPrimitive.content)
        assertEquals(true, d["_unmapped"]!!.jsonPrimitive.content.toBoolean())
    }

    // ---- font-weight (spec 02, font-weight-keywords.json) ----

    @Test
    fun `font-weight keyword keeps dual storage while numeric flattens to a primitive`() {
        val bold = data("""{"components":{"C":{"properties":{"font-weight":"bold"}}}}""").jsonObject
        assertEquals(700.0, bold["weight"]!!.jsonPrimitive.double)
        assertEquals("bold", bold["original"]!!.jsonPrimitive.content)

        val numeric = data("""{"components":{"C":{"properties":{"font-weight":"700"}}}}""")
        assertTrue(numeric is JsonPrimitive && !numeric.isString, "numeric weight is a bare number")
        assertEquals(700.0, numeric.jsonPrimitive.double)
    }

    // ---- golden fixtures stay envelope-clean (mini structural check, no ajv in JVM) ----

    @Test
    fun `all golden fixtures satisfy the structural envelope contract`() {
        // Resolve the repo root from the test working dir (converter/) by
        // walking up until schema/ir-v1.schema.json appears.
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "schema/ir-v1.schema.json").exists()) dir = dir.parentFile
        val fixturesDir = File(dir ?: error("repo root not found"), "schema/conformance/fixtures")
        val fixtures = fixturesDir.listFiles { f -> f.extension == "json" }!!.sorted()
        assertTrue(fixtures.size >= 10, "expected the full golden set, found ${fixtures.size}")

        // Recursive component check mirroring the schema's envelope rules.
        fun checkComponent(cmp: JsonObject, where: String) {
            val allowed = setOf("id", "name", "properties", "selectors", "media", "children", "_text", "_tag", "_role", "_pseudo")
            assertTrue(allowed.containsAll(cmp.keys), "$where has unknown envelope keys: ${cmp.keys - allowed}")
            assertTrue(cmp["id"]!!.jsonPrimitive.content.isNotEmpty(), "$where id empty")
            for (prop in cmp["properties"]!!.jsonArray) {
                assertEquals(setOf("type", "data"), prop.jsonObject.keys, "$where property envelope drifted")
            }
            cmp["children"]?.jsonArray?.forEachIndexed { i, child ->
                checkComponent(child.jsonObject, "$where/children/$i")
            }
        }
        for (file in fixtures) {
            val doc = Json.parseToJsonElement(file.readText()).jsonObject
            assertEquals(setOf("components"), doc.keys, "${file.name}: document level must be exactly {components}")
            doc["components"]!!.jsonArray.forEachIndexed { i, cmp ->
                checkComponent(cmp.jsonObject, "${file.name}/components/$i")
            }
        }
    }
}
