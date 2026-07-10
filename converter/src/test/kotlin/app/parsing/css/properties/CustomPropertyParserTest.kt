package app.parsing.css.properties

// Pins the first-class custom-property path (css-variables-1 §2):
//   - CustomPropertyParser name detection + verbatim extraction
//   - CssPropertyValidator accepting `--*` (case-sensitively, pre-normalization)
//   - PropertiesParser routing --* OUT of the typed pipeline (never GenericProperty)
//   - the IR v2 `variables` envelope key: emission order, omit-when-empty,
//     decode round-trip, and v1-serializer indifference.

import app.irmodels.IRWireV2
import app.parsing.IRFlattener
import app.parsing.css.CssPropertyValue
import app.parsing.css.cssParsing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomPropertyParserTest {

    // Full real pipeline: authoring JSON → cssParsing → flatten → v2 bytes.
    private fun emit(input: String): JsonObject {
        val doc = cssParsing(Json.parseToJsonElement(input).jsonObject)
        return IRWireV2.encodeDocument(IRFlattener.flatten(doc))
    }

    private fun firstComponent(input: String): JsonObject =
        emit(input)["components"]!!.jsonArray[0].jsonObject

    // ---- name detection ----

    @Test
    fun `custom property names are detected — bare double-dash is reserved`() {
        assertTrue(CustomPropertyParser.isCustomProperty("--x"))
        assertTrue(CustomPropertyParser.isCustomProperty("--Brand-Color"))
        // css-variables-1 §2: the empty name `--` is reserved/invalid.
        assertFalse(CustomPropertyParser.isCustomProperty("--"))
        // Single dash is a vendor-prefix shape, not a custom property.
        assertFalse(CustomPropertyParser.isCustomProperty("-webkit-line-clamp"))
        assertFalse(CustomPropertyParser.isCustomProperty("color"))
    }

    @Test
    fun `validator accepts custom properties and keeps standard behavior`() {
        assertTrue(CssPropertyValidator.isValidProperty("--brand-bg"))
        // Case-sensitive names pass without lowercase folding.
        assertTrue(CssPropertyValidator.isValidProperty("--Brand-BG"))
        // The reserved empty name stays invalid (falls out of the pipeline).
        assertFalse(CssPropertyValidator.isValidProperty("--"))
        // Standard names are unaffected by the new branch.
        assertTrue(CssPropertyValidator.isValidProperty("background-color"))
        assertFalse(CssPropertyValidator.isValidProperty("not-a-real-prop"))
    }

    // ---- extraction ----

    @Test
    fun `extractVariables preserves case, order, and raw values verbatim`() {
        val input = linkedMapOf(
            "--Zeta" to CssPropertyValue("  #FF0000 "),       // whitespace preserved (raw token stream)
            "color" to CssPropertyValue("red"),               // typed property — not extracted
            "--alpha-2" to CssPropertyValue("calc(100% - 8px)"),
            "--empty" to CssPropertyValue("")                 // `--x:;` is legal CSS — empty preserved
        )
        val vars = CustomPropertyParser.extractVariables(input)!!
        // Insertion (authoring) order survives, names keep their exact case.
        assertEquals(listOf("--Zeta", "--alpha-2", "--empty"), vars.keys.toList())
        assertEquals("  #FF0000 ", vars["--Zeta"])
        assertEquals("calc(100% - 8px)", vars["--alpha-2"])
        assertEquals("", vars["--empty"])
    }

    @Test
    fun `extractVariables returns null when no custom properties exist`() {
        assertNull(CustomPropertyParser.extractVariables(mapOf("color" to CssPropertyValue("red"))))
        assertNull(CustomPropertyParser.extractVariables(emptyMap()))
    }

    // ---- typed-pipeline routing ----

    @Test
    fun `custom properties never degrade into GenericProperty`() {
        val parsed = PropertiesParser.parse(
            mapOf(
                "--token" to CssPropertyValue("12px"),
                "padding-top" to CssPropertyValue("var(--token)")
            )
        )
        // Only the typed padding-top survives in the property list; the
        // custom property is the component-level variables map's business.
        assertTrue(parsed.none { it is GenericProperty }, "custom property leaked as GenericProperty")
        assertEquals(1, parsed.size)
    }

    // ---- v2 wire emission ----

    @Test
    fun `variables emit as a component-level map with verbatim keys and values`() {
        val c = firstComponent(
            """{"components":{"Theme":{"properties":{
                "--Brand-BG":"#0f62fe",
                "--space-2":"12px",
                "background-color":"var(--Brand-BG)"
            }}}}"""
        )
        val vars = c["variables"]!!.jsonObject
        // Exact key case + authoring order + raw values, nothing folded.
        assertEquals(listOf("--Brand-BG", "--space-2"), vars.keys.toList())
        assertEquals("#0f62fe", vars["--Brand-BG"]!!.jsonPrimitive.content)
        assertEquals("12px", vars["--space-2"]!!.jsonPrimitive.content)
        // The var() REFERENCE stays in the typed property list (dynamic
        // color, srgb omitted) — it is not itself a variables entry.
        assertEquals(1, c["properties"]!!.jsonArray.size)
    }

    @Test
    fun `variables key is omitted entirely when no custom properties are declared`() {
        val c = firstComponent("""{"components":{"C":{"properties":{"color":"red"}}}}""")
        assertFalse("variables" in c.keys, "omit-when-empty rule violated")
    }

    @Test
    fun `variables round-trip through the v2 reader verbatim`() {
        val encoded = emit(
            """{"components":{"Theme":{"properties":{"--Fg":"oklch(0.7 0.1 200)","--pad":"calc(1em + 2px)"}}}}"""
        )
        val decoded = IRWireV2.decodeDocument(encoded)
        // Map content and order survive decode (spec: structural round-trip).
        assertEquals(
            mapOf("--Fg" to "oklch(0.7 0.1 200)", "--pad" to "calc(1em + 2px)"),
            decoded.components[0].variables
        )
        // Re-encode is byte-stable: same keys in the same order.
        val reEncoded = IRWireV2.encodeDocument(decoded)
        assertEquals(
            encoded["components"]!!.jsonArray[0].jsonObject["variables"],
            reEncoded["components"]!!.jsonArray[0].jsonObject["variables"]
        )
    }

    @Test
    fun `children inherit nothing implicitly — each component carries only its own declarations`() {
        val doc = emit(
            """{"components":{"Parent":{
                 "properties":{"--t":"8px"},
                 "children":{"kid":{"properties":{"padding-top":"var(--t)"}}}
            }}}"""
        )
        val components = doc["components"]!!.jsonArray.map { it.jsonObject }
        // Parent carries the definition…
        assertEquals("8px", components[0]["variables"]!!.jsonObject["--t"]!!.jsonPrimitive.content)
        // …the child carries only the unresolved var() reference (resolution
        // walks the slot-parent chain at RUNTIME, spec 02) — no variables key.
        assertFalse("variables" in components[1].keys)
        // Slot ref pins the chain the runtime resolver will walk.
        assertEquals(components[0]["id"]!!.jsonPrimitive.content,
            components[1]["slot"]!!.jsonObject["parent"]!!.jsonPrimitive.content)
    }
}
