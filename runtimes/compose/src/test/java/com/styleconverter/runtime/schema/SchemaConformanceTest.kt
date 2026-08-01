package com.styleconverter.runtime.schema

// Compose-runtime side of the IR wire contract — now DUAL-version:
//
//   v2 (schema/ir-v2.schema.json + spec 01/03/05): every golden in
//   schema/conformance/fixtures/v2/ decodes through the runtime's REAL
//   v2 load path — IRDocumentDecoder (strict envelope walk) — exactly
//   what both Android harness screens do at app launch. Structural
//   sentinels cover the renames (text/pseudos/meta), the slot round-trip
//   rule, SlotComposer tree assembly, and the ItemPlacement claims of
//   the placement-claims golden.
//
//   v1 (deprecation window): the original golden set stays validated
//   through the SAME decode() entry point, which routes versionless
//   documents to the legacy tolerant path (Json { ignoreUnknownKeys })
//   with a deprecation warning — byte-identical decode behaviour to the
//   pre-freeze harness, pinned until the window closes.
//
// The v1-era caveat (blanket ignoreUnknownKeys silently dropping _role)
// is CLOSED for v2: unknown envelope keys are a loud error (spec 05
// tolerance rule 2) and meta.role now round-trips — both pinned below.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocument
import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import com.styleconverter.runtime.core.placement.ItemPlacementExtractor
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import com.styleconverter.runtime.core.renderer.SlotComposer
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.spacing.PaddingExtractor
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class SchemaConformanceTest {

    // Locate repo-root/schema/conformance/fixtures by walking up from the
    // test working directory (AGP unit tests run in the module dir,
    // runtimes/compose; walking up keeps this robust to harness cwd quirks).
    private val fixturesDir: File by lazy {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, "schema/ir-v1.schema.json").exists()) dir = dir.parentFile
        File(requireNotNull(dir) { "repo root (schema/ir-v1.schema.json) not found above ${System.getProperty("user.dir")}" },
            "schema/conformance/fixtures")
    }

    private val v2Dir: File by lazy { File(fixturesDir, "v2") }

    // THE real load path — the same entry point both harness screens use.
    private fun load(name: String): IRDocument =
        IRDocumentDecoder.decode(File(fixturesDir, name).readText())

    private fun loadV2(name: String): IRDocument =
        IRDocumentDecoder.decode(File(v2Dir, name).readText())

    // Depth-first walk — children are ARRAYS in-memory (v1 wire / composed v2).
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

    private fun byId(doc: IRDocument, id: String): IRComponent =
        requireNotNull(doc.components.firstOrNull { it.id == id }) { "component $id not in fixture" }

    // Adapt the runtime property list to the extractor's (type, data) pairs.
    private fun pairs(c: IRComponent) = c.properties.map { it.type to it.data }

    private fun assertThrowsIae(json: String, needle: String) {
        try {
            IRDocumentDecoder.decode(json)
            fail("expected IllegalArgumentException containing '$needle'")
        } catch (e: IllegalArgumentException) {
            assertTrue("message '${e.message}' should mention '$needle'",
                e.message?.contains(needle, ignoreCase = true) == true)
        }
    }

    // ════════════════════════════════ v1 window ═══════════════════════════

    @Test
    fun `v1 window — every v1 golden still decodes through the real load path`() {
        val fixtures = fixturesDir.listFiles { f -> f.extension == "json" }!!.sorted()
        assertTrue("expected the full golden set, found ${fixtures.size}", fixtures.size >= 12)
        for (file in fixtures) {
            // Versionless documents route to the tolerant legacy path —
            // decode MUST NOT throw, including unknown property types
            // (spec 05 tolerance rule 1) and unknown metadata (_role).
            val doc = IRDocumentDecoder.decode(file.readText())
            walk(doc.components) { c ->
                assertTrue("${file.name}: empty id", c.id.isNotEmpty())
            }
        }
    }

    @Test
    fun `v1 window — lengths sentinel extracts through the real spacing engine`() {
        val doc = load("lengths-all-forms.json")
        val cfg = PaddingExtractor.extract(pairs(byName(doc, "Lengths_PxOnly")))
        assertEquals(LengthValue.Exact(16.0), cfg.top)               // {"px":16.0} → Exact
        val dual = PaddingExtractor.extract(pairs(byName(doc, "Lengths_DualStorage")))
        assertEquals(LengthValue.Exact(16.0), dual.right)            // 12pt dual form: px wins
        val esc = PaddingExtractor.extract(pairs(byName(doc, "Lengths_PercentAndKeyword")))
        assertTrue("bare number is a percentage (spec 02)", esc.left is LengthValue.Relative)
    }

    @Test
    fun `v1 window — colors dual storage sentinel r ~ 0_2039`() {
        val doc = load("colors.json")
        val bg = byName(doc, "Colors_StaticForms").properties.first { it.type == "BackgroundColor" }
        val r = bg.data.jsonObject["srgb"]!!.jsonObject["r"]!!.jsonPrimitive.double
        assertEquals(0.20392156862745098, r, 1e-12)
        // Dynamic currentColor: original present, srgb absent (spec 02).
        val accent = byName(doc, "Colors_SpecialKeywords").properties.first { it.type == "AccentColor" }
        assertNull(accent.data.jsonObject["srgb"])
    }

    @Test
    fun `v1 window — _text round-trips, nested children arrays intact`() {
        val doc = load("text-and-role.json")
        assertEquals("Test passes if this text is green", byName(doc, "TextRole_BodyRoot")._text)
        assertEquals("", byName(doc, "TextRole_EmptyStringText")._text)   // "" is meaningful, not null
        assertNull(byName(doc, "TextRole_NoMetadata")._text)

        val nested = load("children-nesting.json")
        val parent = byName(nested, "Parent")
        val child = parent.children!![0]
        assertEquals("child__0", child.name)                          // map key became name (spec 03 v1)
        assertEquals("leaf text", child.children!![0]._text)
        assertNull("omit-when-empty: never an empty array", byName(nested, "LeafSibling").children)
    }

    @Test
    fun `v1 window — unknown property types preserved as data, known neighbours extract`() {
        val doc = load("unknown-property-tolerance.json")
        val mixed = byName(doc, "Unknown_MixedWithKnown")
        assertEquals("FrobnicateCorner", mixed.properties[0].type)
        assertEquals("Width", mixed.properties[1].type)
        val prim = byName(doc, "Unknown_PrimitiveData")
        val cfg = PaddingExtractor.extract(pairs(prim))
        assertEquals(LengthValue.Exact(16.0), cfg.top)
    }

    @Test
    fun `v1 window — composer passes nested v1 documents through untouched`() {
        val doc = load("children-nesting.json")
        // No slot refs anywhere → compose() is the identity: same root
        // list instance semantics, children arrays untouched.
        assertEquals(doc.components, SlotComposer.compose(doc))
    }

    // ════════════════════════════════ v2 ══════════════════════════════════

    @Test
    fun `v2 — every v2 golden decodes flat through the strict decoder`() {
        val fixtures = v2Dir.listFiles { f -> f.extension == "json" }!!.sorted()
        assertTrue("expected the full v2 golden set, found ${fixtures.size}", fixtures.size >= 14)
        for (file in fixtures) {
            val doc = IRDocumentDecoder.decode(file.readText())
            for (c in doc.components) {
                assertTrue("${file.name}: empty id", c.id.isNotEmpty())
                // Flat-list rule: no v2 component ever carries children.
                assertNull("${file.name}: '${c.id}' decoded with children", c.children)
            }
        }
    }

    @Test
    fun `v2 — renames land on the runtime model (text, meta_sourceTag, meta_role)`() {
        val doc = loadV2("text-and-role.json")
        // `text` (v2) maps onto the in-memory `_text` the renderer reads.
        assertEquals("Test passes if this text is green", byId(doc, "textrole-body-001")._text)
        assertEquals("", byId(doc, "textrole-emptytext-002")._text)   // "" preserved, not nulled
        assertNull(byId(doc, "textrole-absent-003")._text)
        // meta.role round-trips — the exact field the v1 path dropped
        // (spec 04 caveat, closed by the v2 freeze).
        assertEquals("body-root", byId(doc, "textrole-body-001").role)
        assertNull(byId(doc, "textrole-absent-003").role)
    }

    @Test
    fun `v2 — meta_decorations lands on the runtime model verbatim`() {
        // Wave-22 lane DECOR. ORDER is outermost-first and colour tokens
        // stay AS AUTHORED — the converter forwards `meta` verbatim, so
        // this is NOT the IR sRGB leaf; DecorationWire resolves the token
        // at paint time (schema/spec/04-metadata-fields.md).
        val doc = loadV2("decorations.json")
        val chain = byId(doc, "decor-chain-002").decorations!!
        assertEquals(listOf("underline", "overline", "line-through"), chain.map { it.line })
        assertEquals(listOf("blue", "gray", "green"), chain.map { it.color })
        // An omitted colour stays null = currentColor (§2.2 initial).
        val cc = byId(doc, "decor-currentcolor-003").decorations!!
        assertEquals(1, cc.size)
        assertNull(cc[0].color)
        // Functional + hex tokens survive verbatim to the runtime.
        assertEquals(
            listOf("#00ff00", "rgb(0, 0, 255)"),
            byId(doc, "decor-functional-colour-004").decorations!!.map { it.color }
        )
        // A component with no decorations keeps null (absence ≠ emptiness).
        assertNull(byId(doc, "decor-container-001").decorations)
    }

    @Test
    fun `v2 — slot round-trips with default-name reconstruction`() {
        val doc = loadV2("children-nesting.json")
        val child = byId(doc, "child__0-002")
        assertEquals("parent-001", child.slot!!.parent)
        // Wire omits name == "content"; the decoder reconstructs the
        // documented default (spec 01) — slot MUST round-trip whole.
        assertEquals("content", child.slot!!.name)
        // Roots omit slot entirely.
        assertNull(byId(doc, "parent-001").slot)
        assertNull(byId(doc, "leaf-sibling-004").slot)
    }

    @Test
    fun `v2 — composer inverts the flattener - children-nesting matches its v1 twin`() {
        // The v2 golden is the converter's flatten of the SAME authoring
        // input as the v1 golden — composing it back must reproduce the
        // v1 nested tree (ids, names, text, child order) exactly. This is
        // the order-stability proof the capture pipeline relies on.
        val v1 = load("children-nesting.json").components
        val v2 = SlotComposer.compose(loadV2("children-nesting.json"))
        fun shape(c: IRComponent): String =
            "${c.id}/${c.name}/${c._text ?: ""}[" +
                (c.children ?: emptyList()).joinToString(",") { shape(it) } + "]"
        assertEquals(
            v1.joinToString(";") { shape(it) },
            v2.joinToString(";") { shape(it) }
        )
    }

    @Test
    fun `v2 — slot-composition golden assembles the 3-level tree in sibling order`() {
        val roots = SlotComposer.compose(loadV2("slot-composition.json"))
        assertEquals(1, roots.size)
        val root = roots[0]
        assertEquals("slotroot-001", root.id)
        // Sibling order rule: flat-array order IS composition order.
        assertEquals(listOf("band__0-002", "band__1-005"), root.children!!.map { it.id })
        assertEquals(listOf("cell__0__0-003", "cell__0__1-004"), root.children!![0].children!!.map { it.id })
        assertEquals(listOf("cell__1__0-006"), root.children!![1].children!!.map { it.id })
    }

    @Test
    fun `v2 — placement-claims golden routes ITEM claims through the placement union`() {
        val doc = loadV2("placement-claims.json")
        // Grid line claims (1-based CSS lines; css-grid-1 §8.3).
        val line = ItemPlacementExtractor.extract(byId(doc, "line-claim__1-003").properties)
        assertEquals(1, line.grid.colStart)
        assertEquals(3, line.grid.colEnd)
        assertEquals(2, line.grid.rowStart)
        // Named-area / span forms serialize as non-number shapes → auto
        // (the documented honest fallback of the line-number engine).
        val area = ItemPlacementExtractor.extract(byId(doc, "area-claim__0-002").properties)
        assertNull(area.grid.colStart)
        assertNull(area.grid.rowStart)
        val span = ItemPlacementExtractor.extract(byId(doc, "span-claim__2-004").properties)
        assertNull(span.grid.colStart)      // "span 2" → auto
        assertEquals(1, span.grid.rowStart)
        // Flex claims + shared alignment/order claims.
        val flex = ItemPlacementExtractor.extract(byId(doc, "flex-claim__0-006").properties)
        assertEquals(1f, flex.flex.grow)
        assertEquals(0f, flex.flex.shrink)
        assertEquals(40.0, flex.flex.basisPx!!, 0.0)
        assertEquals(ComponentRenderer.AlignSelf.CENTER, flex.alignSelf)
        assertEquals(2, flex.order)
        // Paint claim.
        val paint = ItemPlacementExtractor.extract(byId(doc, "paint-claim__1-007").properties)
        assertEquals(3, paint.zIndex)
        // CSS-initial defaults for absent fields (design §2.2).
        assertEquals(0f, paint.flex.grow)
        assertEquals(1f, paint.flex.shrink)
        assertEquals(0, paint.order)
        assertEquals(ComponentRenderer.AlignSelf.AUTO, paint.alignSelf)
        // Wire decision: position/insets stay ORDINARY property envelopes
        // on the child — no special wire block.
        val abs = byId(doc, "abs-claim__2-008")
        assertEquals("ABSOLUTE",
            abs.properties.first { it.type == "Position" }.data.jsonPrimitive.content)
        assertEquals(4.0,
            abs.properties.first { it.type == "Top" }.data.jsonObject["px"]!!.jsonPrimitive.double, 0.0)
    }

    @Test
    fun `v2 — unknown property types tolerated, known neighbours extract (spec 05 rule 1)`() {
        val doc = loadV2("unknown-property-tolerance.json")
        val mixed = byName(doc, "Unknown_MixedWithKnown")
        assertEquals("FrobnicateCorner", mixed.properties[0].type)
        assertEquals("Width", mixed.properties[1].type)
        val prim = byName(doc, "Unknown_PrimitiveData")
        assertEquals(LengthValue.Exact(16.0), PaddingExtractor.extract(pairs(prim)).top)
    }

    @Test
    fun `v2 — selectors and media buckets decode with full property envelopes`() {
        val doc = loadV2("selectors-media.json")
        val c = byName(doc, "SelMedia_Both")
        assertEquals("hover", c.selectors[0].condition)               // no leading colon (spec 01)
        assertEquals("BackgroundColor", c.selectors[0].properties[0].type)
        assertEquals("(min-width: 768px)", c.media[0].query)
        assertTrue(byName(doc, "SelMedia_OmittedWhenEmpty").selectors.isEmpty())
    }

    // ── v2 strictness: the loud-error matrix (spec 05 tolerance rule 2) ──

    @Test
    fun `v2 strict — children key is a hard decode error`() {
        assertThrowsIae(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[],"children":[]}]}""",
            "children"
        )
    }

    @Test
    fun `v2 strict — unknown document key errors`() {
        assertThrowsIae(
            """{"irVersion":2,"minReaderVersion":2,"componentz":[],"components":[]}""",
            "componentz"
        )
    }

    @Test
    fun `v2 strict — unknown component key errors (the closed _role caveat)`() {
        assertThrowsIae(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[],"_role":"body-root"}]}""",
            "_role"
        )
    }

    @Test
    fun `v2 strict — reader refuses minReaderVersion greater than 2`() {
        assertThrowsIae(
            """{"irVersion":2,"minReaderVersion":3,"components":[]}""",
            "requires reader"
        )
    }

    @Test
    fun `v2 strict — missing minReaderVersion errors`() {
        assertThrowsIae(
            """{"irVersion":2,"components":[]}""",
            "minReaderVersion"
        )
    }

    @Test
    fun `v2 strict — slot without parent errors`() {
        assertThrowsIae(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[],"slot":{"name":"content"}}]}""",
            "parent"
        )
    }

    @Test
    fun `v2 strict — empty meta errors (minProperties 1)`() {
        assertThrowsIae(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[],"meta":{}}]}""",
            "meta"
        )
    }

    @Test
    fun `v2 strict — unknown property wrapper key errors`() {
        assertThrowsIae(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[{"type":"Width","data":{},"scope":"ITEM"}]}]}""",
            "scope"
        )
    }

    // ── composer edge cases (spec 03) ─────────────────────────────────────

    @Test
    fun `v2 composer — dangling slot parent becomes a root, not an error`() {
        val doc = IRDocumentDecoder.decode(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[]},
                {"id":"b","name":"B","properties":[],"slot":{"parent":"ghost"}}]}"""
        )
        val roots = SlotComposer.compose(doc)
        assertEquals(listOf("a", "b"), roots.map { it.id })
        assertNull(roots[1].children)
    }

    @Test
    fun `v2 composer — duplicate ids are a loud error`() {
        val doc = IRDocumentDecoder.decode(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A1","properties":[]},
                {"id":"a","name":"A2","properties":[],"slot":{"parent":"a"}}]}"""
        )
        try {
            SlotComposer.compose(doc)
            fail("expected duplicate-id error")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("duplicate"))
        }
    }

    @Test
    fun `v2 composer — Mode B zero-slot documents pass through as roots`() {
        val doc = IRDocumentDecoder.decode(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[]},
                {"id":"b","name":"B","properties":[]}]}"""
        )
        assertEquals(doc.components, SlotComposer.compose(doc))
    }
}
