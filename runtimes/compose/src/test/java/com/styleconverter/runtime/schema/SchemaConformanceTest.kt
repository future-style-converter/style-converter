package com.styleconverter.runtime.schema

// Compose-runtime side of the IR v1 wire contract
// (schema/ir-v1.schema.json + schema/spec/*). Decodes EVERY golden fixture
// in schema/conformance/fixtures/ through the runtime's real IR-loading
// path — kotlinx Json { ignoreUnknownKeys = true } into core.ir.IRDocument,
// exactly what the Android harness does at app launch
// (apps/android-harness .../ui/ComponentListScreen.kt) — then asserts
// sentinel extractions per shape family.
//
// NOTE the documented caveat (schema/spec/04-metadata-fields.md): this
// model has no `_role`/`_pseudo` fields, so ignoreUnknownKeys silently
// drops them. The sentinel here is therefore "decode survives their
// presence", not "they round-trip" — flipping that requires a model
// change, not a test change.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocument
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.spacing.PaddingExtractor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SchemaConformanceTest {

    // Same Json configuration the harness uses at runtime — the whole
    // point is exercising the REAL load path, not an idealized one.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // Locate repo-root/schema/conformance/fixtures by walking up from the
    // test working directory (AGP unit tests run in the module dir,
    // runtimes/compose; walking up keeps this robust to harness cwd quirks).
    private val fixturesDir: File by lazy {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, "schema/ir-v1.schema.json").exists()) dir = dir.parentFile
        File(requireNotNull(dir) { "repo root (schema/ir-v1.schema.json) not found above ${System.getProperty("user.dir")}" },
            "schema/conformance/fixtures")
    }

    private fun load(name: String): IRDocument =
        json.decodeFromString(IRDocument.serializer(), File(fixturesDir, name).readText())

    // Depth-first walk — children are ARRAYS on the wire (spec 03).
    private fun walk(components: List<IRComponent>, visit: (IRComponent) -> Unit) {
        for (c in components) {
            visit(c)
            c.children?.let { walk(it, visit) }
        }
    }

    private fun byName(doc: IRDocument, name: String): IRComponent {
        var found: IRComponent? = null
        walk(doc.components) { if (it.name == name) found = it }
        return requireNotNull(found) { "component $name not in fixture" }
    }

    // Adapt the runtime property list to the extractor's (type, data) pairs.
    private fun pairs(c: IRComponent) = c.properties.map { it.type to it.data }

    // ---- umbrella: every golden decodes through the real path ----

    @Test
    fun `every golden fixture decodes through the runtime IRDocument model`() {
        val fixtures = fixturesDir.listFiles { f -> f.extension == "json" }!!.sorted()
        assertTrue("expected the full golden set, found ${fixtures.size}", fixtures.size >= 12)
        for (file in fixtures) {
            // Decode MUST NOT throw — including unknown property types
            // (spec 05 tolerance rule 1) and unknown metadata (_role,
            // _pseudo — the spec-04 ignoreUnknownKeys caveat).
            val doc = json.decodeFromString(IRDocument.serializer(), file.readText())
            walk(doc.components) { c ->
                assertTrue("${file.name}: empty id", c.id.isNotEmpty())
            }
        }
    }

    // ---- per-family sentinels ----

    @Test
    fun `lengths — 16px padding extracts to Exact 16 through the real spacing engine`() {
        val doc = load("lengths-all-forms.json")
        val cfg = PaddingExtractor.extract(pairs(byName(doc, "Lengths_PxOnly")))
        assertEquals(LengthValue.Exact(16.0), cfg.top)               // {"px":16.0} → Exact
        val dual = PaddingExtractor.extract(pairs(byName(doc, "Lengths_DualStorage")))
        assertEquals(LengthValue.Exact(16.0), dual.right)            // 12pt dual form: px wins
        val esc = PaddingExtractor.extract(pairs(byName(doc, "Lengths_PercentAndKeyword")))
        assertTrue("bare number is a percentage (spec 02)", esc.left is LengthValue.Relative)
    }

    @Test
    fun `lengths — relative EM form has no px and stays Relative`() {
        val doc = load("lengths-all-forms.json")
        val cmp = byName(doc, "Lengths_RelativeOriginalOnly")
        // Raw wire check: the EM payload must NOT carry a px key.
        val em = cmp.properties.first { it.type == "PaddingBottom" }.data.jsonObject
        assertNull("relative units must not be pre-computed", em["px"])
        val cfg = PaddingExtractor.extract(pairs(cmp))
        assertTrue(cfg.bottom is LengthValue.Relative)
    }

    @Test
    fun `colors — sRGB dual storage sentinel r ~ 0_2039`() {
        val doc = load("colors.json")
        val bg = byName(doc, "Colors_StaticForms").properties.first { it.type == "BackgroundColor" }
        val r = bg.data.jsonObject["srgb"]!!.jsonObject["r"]!!.jsonPrimitive.double
        assertEquals(0.20392156862745098, r, 1e-12)
        // Dynamic currentColor: original present, srgb absent (spec 02).
        val accent = byName(doc, "Colors_SpecialKeywords").properties.first { it.type == "AccentColor" }
        assertNull(accent.data.jsonObject["srgb"])
    }

    @Test
    fun `shape-radius — keyword survives verbatim, length form deep-flattened to px`() {
        val doc = load("shape-radius.json")
        val kw = byName(doc, "Shape_Circle_Keyword").properties.first().data.jsonObject
        assertEquals("farthest-side", kw["r"]!!.jsonPrimitive.content)
        val len = byName(doc, "Shape_Circle_Length").properties.first().data.jsonObject
        assertEquals(40.0, len["px"]!!.jsonPrimitive.double, 0.0)
        assertNull("deep-flatten removed the r key (spec 02)", len["r"])
    }

    @Test
    fun `text-and-role — _text round-trips, _role presence tolerated but dropped (spec 04 caveat)`() {
        val doc = load("text-and-role.json")
        assertEquals("Test passes if this text is green", byName(doc, "TextRole_BodyRoot")._text)
        assertEquals("", byName(doc, "TextRole_EmptyStringText")._text)   // "" is meaningful, not null
        assertNull(byName(doc, "TextRole_NoMetadata")._text)
        // _role: the model has no field for it — decode surviving IS the
        // documented current behavior. (Round-trip requires a model change.)
    }

    @Test
    fun `children-nesting — array-out tree with grandchild text at depth 2`() {
        val doc = load("children-nesting.json")
        val parent = byName(doc, "Parent")
        val child = parent.children!![0]
        assertEquals("child__0", child.name)                          // map key became name (spec 03)
        assertEquals("leaf text", child.children!![0]._text)
        assertNull("omit-when-empty: never an empty array", byName(doc, "LeafSibling").children)
    }

    @Test
    fun `selectors-media — buckets decode with full property envelopes`() {
        val doc = load("selectors-media.json")
        val c = byName(doc, "SelMedia_Both")
        assertEquals("hover", c.selectors[0].condition)               // no leading colon (spec 01)
        assertEquals("BackgroundColor", c.selectors[0].properties[0].type)
        assertEquals("(min-width: 768px)", c.media[0].query)
        assertTrue(byName(doc, "SelMedia_OmittedWhenEmpty").selectors.isEmpty())
    }

    @Test
    fun `unknown-property-tolerance — unknown types preserved as data, known neighbours extract`() {
        val doc = load("unknown-property-tolerance.json")
        val mixed = byName(doc, "Unknown_MixedWithKnown")
        // The unknown property is retained in the generic (type, data)
        // envelope — dispatch skips it later; decode must keep it.
        assertEquals("FrobnicateCorner", mixed.properties[0].type)
        // And the known sibling still extracts through the real engine.
        assertEquals("Width", mixed.properties[1].type)
        val prim = byName(doc, "Unknown_PrimitiveData")
        val cfg = PaddingExtractor.extract(pairs(prim))
        assertEquals(LengthValue.Exact(16.0), cfg.top)
        // Generic envelope keeps its _unmapped marker on the wire.
        val generic = byName(doc, "Unknown_GenericEnvelope").properties[0]
        assertEquals("Generic", generic.type)
        assertTrue(generic.data.jsonObject["_unmapped"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `property-envelope-edge — primitive, array and empty payloads all decode`() {
        val doc = load("property-envelope-edge.json")
        val prim = byName(doc, "Envelope_PrimitiveData")
        assertEquals("auto", prim.properties.first { it.type == "MarginLeft" }.data.jsonPrimitive.content)
        assertEquals(700.0, prim.properties.first { it.type == "FontWeight" }.data.jsonPrimitive.double, 0.0)
        assertTrue(byName(doc, "Envelope_EmptyObjectData").properties[0].data.jsonObject.isEmpty())
        assertEquals(150.0,
            byName(doc, "Envelope_ArrayData").properties.first { it.type == "TransitionDelay" }
                .data.jsonArray[0].jsonObject["ms"]!!.jsonPrimitive.double, 0.0)
        assertTrue(byName(doc, "Envelope_EmptyPropertiesList").properties.isEmpty())
    }

    @Test
    fun `transform-list — function list and rotate dual storage decode`() {
        val doc = load("transform-list.json")
        val list = byName(doc, "Transform_FunctionList").properties[0].data.jsonObject["list"]!!.jsonArray
        assertEquals("rotate", list[1].jsonObject["fn"]!!.jsonPrimitive.content)
        assertEquals(45.0, list[1].jsonObject["a"]!!.jsonObject["deg"]!!.jsonPrimitive.double, 0.0)
        val rotate = byName(doc, "Transform_RotateAngleDual").properties[0].data.jsonObject
        assertEquals(180.0, rotate["deg"]!!.jsonPrimitive.double, 0.0)   // 0.5turn normalized
        assertEquals("TURN", rotate["original"]!!.jsonObject["u"]!!.jsonPrimitive.content)
    }

    @Test
    fun `font-weight-keywords — keyword dual storage and numeric primitive both resolve to 700`() {
        val doc = load("font-weight-keywords.json")
        val bold = byName(doc, "FontWeight_BoldKeyword").properties[0].data.jsonObject
        assertEquals(700.0, bold["weight"]!!.jsonPrimitive.double, 0.0)
        assertEquals("bold", bold["original"]!!.jsonPrimitive.content)
        val numeric = byName(doc, "FontWeight_NumericPrimitive").properties[0].data.jsonPrimitive
        assertEquals(700.0, numeric.double, 0.0)
    }
}
