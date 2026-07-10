package app.schema

// Converter-side conformance suite for the IR wire contracts
// (schema/ir-v2.schema.json + schema/ir-v1.schema.json + schema/spec/*).
// The JS runner (schema/conformance/run.mjs) validates documents with
// ajv; inside the JVM we skip ajv and instead assert the emitted
// JsonObject shapes directly for each sentinel family — this pins that
// SERIALIZING real parse results still reproduces the exact wire forms
// the golden fixtures document.
//
// Since the v2 freeze the DEFAULT emission is the flat-list v2 wire
// (IRFlattener + IRWireV2), so the sentinel families below are asserted
// against v2 bytes. The deprecated v1 path keeps its own pinning through
// CssParsingTextAndChildrenTest (legacy nested serializer, byte-stable
// for the deprecation window).
//
// Ground truth being pinned:
//  - IRLengthSerializer dual storage        (ValueTypes.kt, spec 02)
//  - PaddingValue/MarginValue escapes       (ValueTypes.kt, spec 02)
//  - ShapeRadius keyword/length asymmetry   (ClipPathSerializers.kt, spec 02)
//  - v2 envelope: irVersion/minReaderVersion (IRWireV2.kt, spec 01)
//  - v2 component field omission + renames  (IRWireV2.kt, spec 01/04)
//  - flat list + slot refs                  (IRFlattener.kt, spec 03)
//  - {type,data} property envelope          (IRPropertySerializer.kt, spec 01)

import app.irmodels.IRComponent
import app.irmodels.IRDocument
import app.irmodels.IRWireV2
import app.parsing.IRFlattener
import app.parsing.css.cssParsing
import app.parsing.css.properties.GenericProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
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
    // (cssParsing → IRFlattener → IRWireV2) and return the JsonObject —
    // i.e. exactly the bytes the DEFAULT `--to ir` writes to disk since
    // the v2 freeze.
    private fun emit(input: String): JsonObject {
        val doc = cssParsing(Json.parseToJsonElement(input).jsonObject)
        return IRWireV2.encodeDocument(IRFlattener.flatten(doc))
    }

    // Convenience: first component of the emitted document.
    private fun firstComponent(input: String): JsonObject =
        emit(input)["components"]!!.jsonArray[0].jsonObject

    // Convenience: data payload of the Nth property of the first component.
    private fun data(input: String, index: Int = 0) =
        firstComponent(input)["properties"]!!.jsonArray[index].jsonObject["data"]!!

    // ---- v2 envelope (spec 01) ----

    @Test
    fun `document carries the v2 version pair and nothing else at top level`() {
        val doc = emit("""{"components":{"C":{"properties":{"color":"red"}}}}""")
        // Writer stamps both version fields; readers refuse newer minReader.
        assertEquals(2, doc["irVersion"]!!.jsonPrimitive.int)
        assertEquals(2, doc["minReaderVersion"]!!.jsonPrimitive.int)
        // Strict top level: exactly the three envelope keys.
        assertEquals(setOf("irVersion", "minReaderVersion", "components"), doc.keys)
    }

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

    // ---- v2 metadata renames (spec 01/04, v2/text-and-role.json) ----

    @Test
    fun `text and meta are emitted under the v2 names and omitted when absent`() {
        val with = firstComponent(
            """{"components":{"C":{"properties":{"color":"green"},"_text":"Test passes","_role":"body-root","_tag":"p"}}}"""
        )
        // _text → text: structural content field, unprefixed in v2.
        assertEquals("Test passes", with["text"]!!.jsonPrimitive.content)
        assertFalse("_text" in with, "v2 never emits underscore names")
        // _tag + _role → meta:{sourceTag, role}: grouped droppable hints.
        val meta = with["meta"]!!.jsonObject
        assertEquals("body-root", meta["role"]!!.jsonPrimitive.content)
        assertEquals("p", meta["sourceTag"]!!.jsonPrimitive.content)
        assertFalse("_role" in with)
        assertFalse("_tag" in with)

        val without = firstComponent("""{"components":{"C":{"properties":{"color":"green"}}}}""")
        assertFalse("text" in without, "omit-when-null keeps hint-free fixtures byte-stable")
        assertFalse("meta" in without)
        assertFalse("pseudos" in without)
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

    // ---- flat list + slot refs (spec 03, v2/children-nesting.json) ----

    @Test
    fun `nested children input flattens to slot refs — no children key anywhere`() {
        val doc = emit(
            """{"components":{"Parent":{
                 "properties":{"display":"flex"},
                 "children":{"child__0":{"properties":{"width":"50px"},
                   "children":{"grandchild__0__0":{"properties":{"height":"10px"},"_text":"leaf text"}}}}
               }}}"""
        )
        val components = doc["components"]!!.jsonArray
        // Pre-order flat list: parent, child, grandchild.
        assertEquals(3, components.size)
        val parent = components[0].jsonObject
        val child = components[1].jsonObject
        val grandchild = components[2].jsonObject
        // No children key survives on ANY entry — hard error in v2 docs.
        components.forEach { assertFalse("children" in it.jsonObject, "v2 wire must not carry children") }
        // The child's map key still becomes its name (input contract unchanged).
        assertEquals("child__0", child["name"]!!.jsonPrimitive.content)
        // slot chain: child → parent, grandchild → child (on the CHILD side).
        assertEquals(
            parent["id"]!!.jsonPrimitive.content,
            child["slot"]!!.jsonObject["parent"]!!.jsonPrimitive.content
        )
        assertEquals(
            child["id"]!!.jsonPrimitive.content,
            grandchild["slot"]!!.jsonObject["parent"]!!.jsonPrimitive.content
        )
        // Roots carry no slot at all.
        assertFalse("slot" in parent)
        // Leaf text rides the renamed structural field.
        assertEquals("leaf text", grandchild["text"]!!.jsonPrimitive.content)
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
        // serializer branch directly (IRPropertySerializer's GenericProperty
        // case) through the v2 document codec.
        val doc = IRDocument(
            components = listOf(
                IRComponent(
                    id = "generic-001",
                    name = "GenericHost",
                    properties = mutableListOf(GenericProperty("hanging-punctuation", "first allow-end"))
                )
            )
        )
        val prop = IRWireV2.encodeDocument(doc)["components"]!!.jsonArray[0]
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

    // Resolve the repo root from the test working dir (converter/) by
    // walking up until schema/ir-v1.schema.json appears.
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "schema/ir-v1.schema.json").exists()) dir = dir.parentFile
        return dir ?: error("repo root not found")
    }

    @Test
    fun `v1 golden fixtures satisfy the legacy structural envelope contract`() {
        // v1 goldens stay validatable for the deprecation window (spec 05).
        val fixturesDir = File(repoRoot(), "schema/conformance/fixtures")
        val fixtures = fixturesDir.listFiles { f -> f.extension == "json" }!!.sorted()
        assertTrue(fixtures.size >= 10, "expected the full v1 golden set, found ${fixtures.size}")

        // Recursive component check mirroring the v1 schema's envelope rules.
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
            assertEquals(setOf("components"), doc.keys, "${file.name}: v1 document level must be exactly {components}")
            doc["components"]!!.jsonArray.forEachIndexed { i, cmp ->
                checkComponent(cmp.jsonObject, "${file.name}/components/$i")
            }
        }
    }

    @Test
    fun `v2 golden fixtures satisfy the flat structural envelope contract`() {
        val fixturesDir = File(repoRoot(), "schema/conformance/fixtures/v2")
        val fixtures = fixturesDir.listFiles { f -> f.extension == "json" }!!.sorted()
        // 12 v1-mirrors + slot-composition + placement-claims.
        assertTrue(fixtures.size >= 14, "expected the full v2 golden set, found ${fixtures.size}")

        // Flat component check mirroring the v2 schema's envelope rules.
        fun checkComponent(cmp: JsonObject, ids: Set<String>, where: String) {
            val allowed = setOf("id", "name", "properties", "selectors", "media", "slot", "text", "pseudos", "meta")
            assertTrue(allowed.containsAll(cmp.keys), "$where has unknown envelope keys: ${cmp.keys - allowed}")
            // The flat-list hard rule: children must never appear.
            assertFalse("children" in cmp, "$where carries a children key — v2 is flat-only")
            assertTrue(cmp["id"]!!.jsonPrimitive.content.isNotEmpty(), "$where id empty")
            for (prop in cmp["properties"]!!.jsonArray) {
                assertEquals(setOf("type", "data"), prop.jsonObject.keys, "$where property envelope drifted")
            }
            // Slot refs must resolve within the document (no dangling
            // parents in GOLDENS — dangling is only composer-tolerated).
            cmp["slot"]?.jsonObject?.let { slot ->
                assertTrue(slot.keys.all { it in setOf("parent", "name") }, "$where slot has unknown keys")
                assertTrue(slot["parent"]!!.jsonPrimitive.content in ids, "$where slot.parent dangles")
            }
            // meta, when present, is non-empty and strict.
            cmp["meta"]?.jsonObject?.let { meta ->
                assertTrue(meta.isNotEmpty(), "$where meta present but empty")
                assertTrue(meta.keys.all { it in setOf("sourceTag", "role") }, "$where meta has unknown keys")
            }
        }
        for (file in fixtures) {
            val doc = Json.parseToJsonElement(file.readText()).jsonObject
            assertEquals(
                setOf("irVersion", "minReaderVersion", "components"),
                doc.keys,
                "${file.name}: v2 document level must be exactly the version pair + components"
            )
            assertEquals(2, doc["irVersion"]!!.jsonPrimitive.int, "${file.name}: irVersion must be 2")
            assertEquals(2, doc["minReaderVersion"]!!.jsonPrimitive.int, "${file.name}: minReaderVersion must be 2")
            val components = doc["components"]!!.jsonArray
            val ids = components.map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet()
            assertEquals(components.size, ids.size, "${file.name}: duplicate component ids")
            components.forEachIndexed { i, cmp ->
                checkComponent(cmp.jsonObject, ids, "${file.name}/components/$i")
            }
        }
    }
}
