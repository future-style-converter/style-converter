package com.styleconverter.runtime.typography

// Wave 54 (lane L5, U1-android, GO-SMALL) — pins the Compose UA element-font
// rule on the VERBATIM wave53-final per-test IR (UAHeadingFaceWire, below),
// replaying seam-1's cascade step (UAHeadingFaceReplay, below). PLAN §2 L5:
//   • the block-in-inline h1 → 32 / 700 (the HIGH target's whole mechanism);
//   • inset-001's h1 (extractor-baked 32/700) → the SAME list instance —
//     mutation: drop the own-declaration guard;
//   • the folded inset-005/-006/-014 hosts and the inset-011 control → the
//     SAME list (the heading half stands down for a host of child boxes:
//     the fold-aware gate is HELD, lane note "Fix pass") — mutation:
//     headingStandsDown returns false;
//   • inset-014's 2em on the monospace base → 26 (the rule, gate forced open)
//     — mutation: base 16 instead of MonospaceUAFontSize;
//   • the text-decoration-color `sup` → 13.333; an untagged component → identity;
//   • the step writes ONLY FontSize / FontWeight — mutation: it writes Color.
// Plus the end-to-end read: TextStyleApplier must PAINT the injected face
// (a `{"weight": 700.0}` payload would read as null there — intOrNull).
// The seam CALL SITE is pinned by UAElementFontRuleSeamWiringTest, which
// ships inside seam-1.patch with the hunk it reads.

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import com.styleconverter.runtime.core.renderer.SlotComposer
import com.styleconverter.runtime.core.types.ValueExtractors
import com.styleconverter.runtime.core.variables.DynamicValueResolver
import com.styleconverter.runtime.lists.ListStyleUaRule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UAElementFontRuleTest {

    /** The first FontSize px — DynamicValueResolver's own (first-wins) read. */
    private fun sizePx(list: List<IRProperty>): Float? = DynamicValueResolver.fontSizePxOf(list)

    /** The FontWeight the converter's shape decodes to (intOrNull read). */
    private fun weight(list: List<IRProperty>): Int? =
        list.firstOrNull { it.type == "FontWeight" }?.let { ValueExtractors.extractFontWeight(it.data) }

    // The four leaf <h1>s of block-in-inline-015-print (author margin: 0, no font).
    private val blockInInlineH1s = listOf(0, 2, 4, 6).zip(listOf(190, 194, 198, 202))
        .map { (n, id) -> "block-in-inline-015-print__${n}__0-$id" }

    // The heading hosts that carry child boxes: three folded targets + the bailing control.
    private val hostsWithChildren = listOf(
        UAHeadingFaceWire.INSET_005 to "text-decoration-inset-005__1__0-069",
        UAHeadingFaceWire.INSET_006 to "text-decoration-inset-006__1__0-076",
        UAHeadingFaceWire.INSET_014 to "text-decoration-inset-014__1__0-110",
        UAHeadingFaceWire.INSET_011 to "text-decoration-inset-011__1__0-095",
    )

    @Test
    fun `block-in-inline-015-print - every leaf h1 takes the UA 2em bold face`() {
        for (id in blockInInlineH1s) {
            val site = UAHeadingFaceReplay.site(UAHeadingFaceWire.BLOCK_IN_INLINE_015_PRINT, id)
            // The verbatim h1 declares only its four margins — no face, no children.
            assertNull("$id carries no own size", sizePx(site.own))
            assertFalse(UAElementFontRule.headingStandsDown(site.component))
            val out = UAHeadingFaceReplay.seam(site)
            // 2 × the 16px medium, bold; exactly one entry each (first-/last-wins agree).
            assertEquals(32f, sizePx(out)!!, 1e-4f)
            assertEquals(700, weight(out))
            assertEquals(1, out.count { it.type == "FontSize" })
            assertEquals(1, out.count { it.type == "FontWeight" })
        }
    }

    @Test
    fun `block-in-inline-015-print - TextStyleApplier paints the injected face`() {
        val site = UAHeadingFaceReplay.site(UAHeadingFaceWire.BLOCK_IN_INLINE_015_PRINT, blockInInlineH1s[0])
        val style = TextStyleApplier.extractTextStyle(UAHeadingFaceReplay.seam(site))
        // The paragraph pipeline's own reads: 32sp, bold.
        assertEquals(32f.sp, style.fontSize)
        assertEquals(FontWeight.Bold, style.fontWeight)
    }

    @Test
    fun `inset-001 - the extractor-baked h1 (own 32 and 700) comes back as the SAME list`() {
        val site = UAHeadingFaceReplay.site(UAHeadingFaceWire.INSET_001, "text-decoration-inset-001__1__0-053")
        // The author (here the extractor's UA_H1_PROPS bake) declared both.
        assertEquals(32f, sizePx(site.own)!!, 1e-4f)
        assertSame(site.mergedPreUa, UAHeadingFaceReplay.seam(site))
    }

    @Test
    fun `GO-SMALL - every heading host with child boxes keeps its list (005, 006, 014 held, 011 control)`() {
        for ((doc, id) in hostsWithChildren) {
            val site = UAHeadingFaceReplay.site(doc, id)
            // Unsized h1 hosts (no own face) — the rule WOULD fire without the gate.
            assertNull("$id carries no own size", sizePx(site.own))
            assertTrue(id, UAElementFontRule.headingStandsDown(site.component))
            // Identity: the Android capture of all four documents stays byte-identical.
            assertSame(id, site.mergedPreUa, UAHeadingFaceReplay.seam(site))
        }
    }

    @Test
    fun `inset-014 - the rule's em base on the verbatim monospace host is 13 (gate forced open)`() {
        val site = UAHeadingFaceReplay.site(UAHeadingFaceWire.INSET_014, "text-decoration-inset-014__1__0-110")
        // The rule alone, as the held fold-aware gate would call it: 2 × 13 = 26, not 2 × 16.
        val out = UAElementFontRule.apply(site.component._tag, site.own, site.mergedPreUa, standsDown = false)
        assertEquals(26f, sizePx(out)!!, 1e-4f)
        assertEquals(700, weight(out))
    }

    @Test
    fun `text-decoration-color - the leaf sup takes smaller and the leaf h3 takes 1_17em bold`() {
        val doc = UAHeadingFaceWire.TEXT_DECORATION_COLOR
        val sup = UAHeadingFaceReplay.seam(UAHeadingFaceReplay.site(doc, "text-decoration-color__9__0__1-033"))
        // 16 ÷ 1.2; sup/sub carry no UA weight.
        assertEquals(16f / 1.2f, sizePx(sup)!!, 1e-3f)
        assertNull(weight(sup))
        val h3 = UAHeadingFaceReplay.seam(UAHeadingFaceReplay.site(doc, "wpt__css-text-decor__text-decoration-color__0-021"))
        assertEquals(18.72f, sizePx(h3)!!, 1e-3f)
        assertEquals(700, weight(h3))
    }

    @Test
    fun `the step writes only FontSize and FontWeight (every other entry kept, in order)`() {
        val face = setOf("FontSize", "FontWeight")
        for ((doc, id) in listOf(
            UAHeadingFaceWire.BLOCK_IN_INLINE_015_PRINT to blockInInlineH1s[0],
            UAHeadingFaceWire.TEXT_DECORATION_COLOR to "text-decoration-color__9__0__1-033",
            UAHeadingFaceWire.TEXT_DECORATION_COLOR to "wpt__css-text-decor__text-decoration-color__0-021",
        )) {
            val site = UAHeadingFaceReplay.site(doc, id)
            val out = UAHeadingFaceReplay.seam(site)
            // A UA step that also wrote Color would change the fold's ink base (brief §4 D).
            assertEquals(id, site.mergedPreUa.filter { it.type !in face }, out.filter { it.type !in face })
        }
    }

    @Test
    fun `an untagged component is returned as the SAME list`() {
        // inset-005's relative div: no meta.sourceTag on the wire.
        val site = UAHeadingFaceReplay.site(UAHeadingFaceWire.INSET_005, "wpt__css-text-decor__text-decoration-inset-005__2-068")
        assertNull(site.component._tag)
        assertSame(site.mergedPreUa, UAHeadingFaceReplay.seam(site))
    }

    @Test
    fun `an inherited size is substituted in place and is the em base`() {
        // Synthetic: an inherited 20px FontSize ahead of Color.
        val inherited = IRProperty("FontSize", Json.parseToJsonElement("""{"px":20}"""))
        val color = IRProperty("Color", Json.parseToJsonElement("""{"srgb":{"r":0,"g":0,"b":0}}"""))
        val out = UAElementFontRule.apply("h2", emptyList(), listOf(inherited, color))
        // 1.5 × 20, at the inherited entry's position, one entry only.
        assertEquals(30f, sizePx(out)!!, 1e-4f)
        assertEquals("FontSize", out.first().type)
        assertEquals(1, out.count { it.type == "FontSize" })
    }

    @Test
    fun `the standsDown verdict withholds the heading half but never the sup half`() {
        val merged = listOf(IRProperty("Color", Json.parseToJsonElement("""{"srgb":{"r":0,"g":0,"b":0}}""")))
        // A heading hosting child boxes: identity.
        assertSame(merged, UAElementFontRule.apply("h1", emptyList(), merged, standsDown = true))
        // A sup ignores the flag (its half is never gated).
        assertEquals(16f / 1.2f, sizePx(UAElementFontRule.apply("sup", emptyList(), merged, standsDown = true))!!, 1e-3f)
    }
}

/** seam-1's cascade step replayed root → target over a verbatim document: for each node, own →
 *  mergeInherited → ListStyleUaRule → UAElementFontRule(standsDown = headingStandsDown) → the
 *  INHERITED subset handed down. Exact for these documents because its premises — no `All`, no
 *  buckets, no var() on the path (so AllReset, the bucket fold and DynamicValueResolver are the
 *  identity) — are ASSERTED per node. Folded in from UAHeadingFaceReplay.kt (fix pass, R2-N8). */
internal object UAHeadingFaceReplay {

    /** One element at the seam: its own list and its PRE-UA merged list. */
    data class Site(val component: IRComponent, val own: List<IRProperty>, val mergedPreUa: List<IRProperty>)

    /** Root → target path through the production decoder + composer (as the harness reads it). */
    private fun path(doc: String, id: String): List<IRComponent> {
        fun walk(node: IRComponent, trail: List<IRComponent>): List<IRComponent>? =
            if (node.id == id) trail + node else node.children.orEmpty().firstNotNullOfOrNull { walk(it, trail + node) }
        val found = SlotComposer.compose(IRDocumentDecoder.decode(doc)).firstNotNullOfOrNull { walk(it, emptyList()) }
        assertNotNull("component $id must exist in the verbatim document", found)
        return found!!
    }

    /** The seam's pre-UA site for [id], replaying every ancestor's seam. */
    fun site(doc: String, id: String): Site {
        var inherited = emptyList<IRProperty>()
        val nodes = path(doc, id)
        for ((index, node) in nodes.withIndex()) {
            assertFalse("no All on the path", node.properties.any { it.type == "All" })
            assertTrue("no buckets on the path", node.selectors.isEmpty() && node.media.isEmpty())
            assertFalse("no var() on the path", node.properties.any { "var(" in it.data.toString() })
            val own = node.properties
            val preUa = ListStyleUaRule.apply(node._tag, own, ComponentRenderer.mergeInherited(own, inherited))
            if (index == nodes.lastIndex) return Site(node, own, preUa)
            // An ancestor publishes its POST-UA list's inheritable subset.
            inherited = seam(Site(node, own, preUa)).filter { it.type in ComponentRenderer.INHERITED_PROPERTY_TYPES }
        }
        error("unreachable: the path always ends at the target")
    }

    /** seam-1's output for [site]: the UA font step with the seam's stand-down verdict. */
    fun seam(site: Site): List<IRProperty> = UAElementFontRule.apply(
        site.component._tag, site.own, site.mergedPreUa,
        standsDown = UAElementFontRule.headingStandsDown(site.component),
    )
}

/** The VERBATIM per-test IR: each constant is the exact bytes of
 *  tools/titan/runs/wave53-final/sections/<section>/per-test-ir/<stem>.json (byte-identical in
 *  wave54-open), spliced by tools/titan/results/wave54-ua-heading-face/gen-wire-constants.py
 *  --splice-kt — never hand-edited (`--check-kt` re-verifies). Folded in from UAHeadingFaceWire.kt. */
internal object UAHeadingFaceWire {
    // ── BEGIN GENERATED WIRE
    // tools/titan/runs/wave53-final/sections/css-break/per-test-ir/wpt__css-break__block-in-inline-015-print.json — sha256 1470c4b909ce0053706c2769b92a35a6f837b7aa03dea96aa59e4725d1c0920a (VERBATIM bytes)
    const val BLOCK_IN_INLINE_015_PRINT: String = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-break__block-in-inline-015-print__0-188","name":"wpt__css-break__block-in-inline-015-print__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"meta":{"role":"body-root"}},{"id":"wpt__css-break__block-in-inline-015-print__1-189","name":"wpt__css-break__block-in-inline-015-print__1","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__0__0-190","name":"block-in-inline-015-print__0__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__1-189"},"text":"one","meta":{"sourceTag":"h1"}},{"id":"wpt__css-break__block-in-inline-015-print__2-191","name":"wpt__css-break__block-in-inline-015-print__2","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__1__0-192","name":"block-in-inline-015-print__1__0","properties":[{"type":"BreakAfter","data":"PAGE"}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__2-191"}},{"id":"wpt__css-break__block-in-inline-015-print__3-193","name":"wpt__css-break__block-in-inline-015-print__3","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__2__0-194","name":"block-in-inline-015-print__2__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__3-193"},"text":"two","meta":{"sourceTag":"h1"}},{"id":"wpt__css-break__block-in-inline-015-print__4-195","name":"wpt__css-break__block-in-inline-015-print__4","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__3__0-196","name":"block-in-inline-015-print__3__0","properties":[{"type":"BreakAfter","data":"PAGE"}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__4-195"}},{"id":"wpt__css-break__block-in-inline-015-print__5-197","name":"wpt__css-break__block-in-inline-015-print__5","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__4__0-198","name":"block-in-inline-015-print__4__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__5-197"},"text":"three","meta":{"sourceTag":"h1"}},{"id":"wpt__css-break__block-in-inline-015-print__6-199","name":"wpt__css-break__block-in-inline-015-print__6","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__5__0-200","name":"block-in-inline-015-print__5__0","properties":[{"type":"BreakAfter","data":"PAGE"}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__6-199"}},{"id":"wpt__css-break__block-in-inline-015-print__7-201","name":"wpt__css-break__block-in-inline-015-print__7","properties":[{"type":"Display","data":"INLINE"}]},{"id":"block-in-inline-015-print__6__0-202","name":"block-in-inline-015-print__6__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__7-201"},"text":"four","meta":{"sourceTag":"h1"}}]}"""

    // tools/titan/runs/wave53-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-inset-001.json — sha256 57d3cf016e74984147add9a2cbbce9bd5284c5cbeb5e5d11ae930fc53cbd2c02 (VERBATIM bytes)
    const val INSET_001: String = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-inset-001__0-050","name":"wpt__css-text-decor__text-decoration-inset-001__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"body-root"}},{"id":"wpt__css-text-decor__text-decoration-inset-001__1-051","name":"wpt__css-text-decor__text-decoration-inset-001__1","properties":[{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"The underline for \"brown\" should be offset by 10px to the right:","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-inset-001__2-052","name":"wpt__css-text-decor__text-decoration-inset-001__2","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}]},{"id":"text-decoration-inset-001__1__0-053","name":"text-decoration-inset-001__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontSize","data":{"px":32,"original":{"type":"length","px":32}}},{"type":"FontWeight","data":{"weight":700,"original":"bold"}},{"type":"MarginTop","data":{"px":21.44}},{"type":"MarginBottom","data":{"px":21.44}}],"slot":{"parent":"wpt__css-text-decor__text-decoration-inset-001__2-052"},"text":"the quick brown fox","meta":{"sourceTag":"h1","decorations":[{"line":"underline","color":"black"}]}}]}"""

    // tools/titan/runs/wave53-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-inset-005.json — sha256 13530ccf4b3e1aa25f71ea121deb5d9f8f0609470a7c2b6c496dd36bc42ec9e6 (VERBATIM bytes)
    const val INSET_005: String = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-inset-005__0-066","name":"wpt__css-text-decor__text-decoration-inset-005__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"body-root"}},{"id":"wpt__css-text-decor__text-decoration-inset-005__1-067","name":"wpt__css-text-decor__text-decoration-inset-005__1","properties":[{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"The underline for \"ultra-quick brown\" should be extended by .25em at each end; super- and sub-scripts should be unaffected:","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-inset-005__2-068","name":"wpt__css-text-decor__text-decoration-inset-005__2","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}]},{"id":"text-decoration-inset-005__1__0-069","name":"text-decoration-inset-005__1__0","properties":[{"type":"Position","data":"ABSOLUTE"}],"slot":{"parent":"wpt__css-text-decor__text-decoration-inset-005__2-068"},"text":"the fox","meta":{"sourceTag":"h1","runs":[{"text":"the "},{"child":"text-decoration-inset-005__1__0__0"},{"text":" fox"}]}},{"id":"text-decoration-inset-005__1__0__0-070","name":"text-decoration-inset-005__1__0__0","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"slot":{"parent":"text-decoration-inset-005__1__0-069"},"text":"ultra- bn","meta":{"sourceTag":"u","runs":[{"text":"ultra-"},{"child":"text-decoration-inset-005__1__0__0__0"},{"text":" b"},{"child":"text-decoration-inset-005__1__0__0__1"},{"text":"n"}]}},{"id":"text-decoration-inset-005__1__0__0__0-071","name":"text-decoration-inset-005__1__0__0__0","properties":[],"slot":{"parent":"text-decoration-inset-005__1__0__0-070"},"text":"quick","meta":{"sourceTag":"sup","role":"ws-after"}},{"id":"text-decoration-inset-005__1__0__0__1-072","name":"text-decoration-inset-005__1__0__0__1","properties":[],"slot":{"parent":"text-decoration-inset-005__1__0__0-070"},"text":"row","meta":{"sourceTag":"sub"}}]}"""

    // tools/titan/runs/wave53-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-inset-006.json — sha256 802e7ac120bb32d6a7910efe19a2eb215b4fa39a3e66a0755e310ec1b9a88865 (VERBATIM bytes)
    const val INSET_006: String = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-inset-006__0-073","name":"wpt__css-text-decor__text-decoration-inset-006__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"body-root"}},{"id":"wpt__css-text-decor__text-decoration-inset-006__1-074","name":"wpt__css-text-decor__text-decoration-inset-006__1","properties":[{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"The underline for \"ultra-quick brown\" should be trimmed by .25em at each end; super- and sub-scripts should be unaffected:","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-inset-006__2-075","name":"wpt__css-text-decor__text-decoration-inset-006__2","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}]},{"id":"text-decoration-inset-006__1__0-076","name":"text-decoration-inset-006__1__0","properties":[{"type":"Position","data":"ABSOLUTE"}],"slot":{"parent":"wpt__css-text-decor__text-decoration-inset-006__2-075"},"text":"the fox","meta":{"sourceTag":"h1","runs":[{"text":"the "},{"child":"text-decoration-inset-006__1__0__0"},{"text":" fox"}]}},{"id":"text-decoration-inset-006__1__0__0-077","name":"text-decoration-inset-006__1__0__0","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"slot":{"parent":"text-decoration-inset-006__1__0-076"},"text":"ultra- bwn","meta":{"sourceTag":"u","runs":[{"text":"ultra-"},{"child":"text-decoration-inset-006__1__0__0__0"},{"text":" b"},{"child":"text-decoration-inset-006__1__0__0__1"},{"text":"wn"}]}},{"id":"text-decoration-inset-006__1__0__0__0-078","name":"text-decoration-inset-006__1__0__0__0","properties":[],"slot":{"parent":"text-decoration-inset-006__1__0__0-077"},"text":"quick","meta":{"sourceTag":"sup","role":"ws-after"}},{"id":"text-decoration-inset-006__1__0__0__1-079","name":"text-decoration-inset-006__1__0__0__1","properties":[],"slot":{"parent":"text-decoration-inset-006__1__0__0-077"},"text":"ro","meta":{"sourceTag":"sub"}}]}"""

    // tools/titan/runs/wave53-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-inset-011.json — sha256 f5edf74789d4b461d6ccd9019fb5c0738afea5f71e0b2f5cc5804c2164548270 (VERBATIM bytes)
    const val INSET_011: String = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-inset-011__0-092","name":"wpt__css-text-decor__text-decoration-inset-011__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontFamily","data":["times new roman","serif"]}],"meta":{"role":"body-root"}},{"id":"wpt__css-text-decor__text-decoration-inset-011__1-093","name":"wpt__css-text-decor__text-decoration-inset-011__1","properties":[{"type":"FontFamily","data":["times new roman","serif"]},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"The underline of the first phrase should be trimmed by 10px at both ends; the underline of the second should be offset by 10px to the left at both start and end:","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-inset-011__2-094","name":"wpt__css-text-decor__text-decoration-inset-011__2","properties":[{"type":"Position","data":"RELATIVE"},{"type":"MarginLeft","data":{"px":10}},{"type":"FontFamily","data":["times new roman","serif"]},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"lang":"ar"}},{"id":"text-decoration-inset-011__1__0-095","name":"text-decoration-inset-011__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"LineHeight","data":{"multiplier":2,"original":{"type":"number","value":2}}},{"type":"Width","data":{"type":"length","original":{"v":10,"u":"EM"}}},{"type":"Direction","data":"RTL"}],"slot":{"parent":"wpt__css-text-decor__text-decoration-inset-011__2-094"},"text":"—","meta":{"sourceTag":"h1","lang":"ar","runs":[{"child":"text-decoration-inset-011__1__0__0"},{"text":" —"},{"child":"text-decoration-inset-011__1__0__1"},{"text":" "},{"child":"text-decoration-inset-011__1__0__2"}]}},{"id":"text-decoration-inset-011__1__0__0-096","name":"text-decoration-inset-011__1__0__0","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}},{"type":"TextUnderlineOffset","data":{"type":"length","px":20}}],"slot":{"parent":"text-decoration-inset-011__1__0-095"},"text":"صِف خَلقَ خَودِ كَمِثلِ الشَمسِ إِذ بَزَغَت","meta":{"sourceTag":"u","role":"ws-after","lang":"ar"}},{"id":"text-decoration-inset-011__1__0__1-097","name":"text-decoration-inset-011__1__0__1","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":0}}],"slot":{"parent":"text-decoration-inset-011__1__0-095"},"meta":{"sourceTag":"br","role":"line-break","lang":"ar"}},{"id":"text-decoration-inset-011__1__0__2-098","name":"text-decoration-inset-011__1__0__2","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"TextUnderlineOffset","data":{"type":"length","px":20}}],"slot":{"parent":"text-decoration-inset-011__1__0-095"},"text":"يَحظى الضَجيعُ بِها نَجلاءَ مِعطارِ","meta":{"sourceTag":"u","lang":"ar"}}]}"""

    // tools/titan/runs/wave53-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-inset-014.json — sha256 1445c4233d82b65ec96fbeed0ce515dc722397e3e537c71a943aceb7c17f4987 (VERBATIM bytes)
    const val INSET_014: String = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-inset-014__0-107","name":"wpt__css-text-decor__text-decoration-inset-014__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"body-root"}},{"id":"wpt__css-text-decor__text-decoration-inset-014__1-108","name":"wpt__css-text-decor__text-decoration-inset-014__1","properties":[{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"The underline for \"ultra-quick brown\" should be extended by 0.5ch at each end of each fragment:","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-inset-014__2-109","name":"wpt__css-text-decor__text-decoration-inset-014__2","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}]},{"id":"text-decoration-inset-014__1__0-110","name":"text-decoration-inset-014__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"FontFamily","data":["monospace"]},{"type":"Width","data":{"type":"length","original":{"v":16,"u":"CH"}}}],"slot":{"parent":"wpt__css-text-decor__text-decoration-inset-014__2-109"},"text":"the fox","meta":{"sourceTag":"h1","runs":[{"text":"the "},{"child":"text-decoration-inset-014__1__0__0"},{"text":" fox"}]}},{"id":"text-decoration-inset-014__1__0__0-111","name":"text-decoration-inset-014__1__0__0","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BoxDecorationBreak","data":"CLONE"}],"slot":{"parent":"text-decoration-inset-014__1__0-110"},"text":"ultra-quick brown","meta":{"sourceTag":"u"}}]}"""

    // tools/titan/runs/wave53-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-color.json — sha256 f815b121e532dd84948f6d482d1698dfcfaa7eea5c66fdd93820fb870b544861 (VERBATIM bytes)
    const val TEXT_DECORATION_COLOR: String = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-color__0-021","name":"wpt__css-text-decor__text-decoration-color__0","properties":[],"text":"Each line of this test should match its text decoration color description:","meta":{"sourceTag":"h3","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-color__1-022","name":"wpt__css-text-decor__text-decoration-color__1","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"Color","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"gray"}},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}}],"text":"Gray text with blue underline"},{"id":"wpt__css-text-decor__text-decoration-color__2-023","name":"wpt__css-text-decor__text-decoration-color__2","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__3-024","name":"wpt__css-text-decor__text-decoration-color__3","properties":[{"type":"TextDecorationLine","data":["OVERLINE"]},{"type":"Color","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"Green text with black overline"},{"id":"wpt__css-text-decor__text-decoration-color__4-025","name":"wpt__css-text-decor__text-decoration-color__4","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__5-026","name":"wpt__css-text-decor__text-decoration-color__5","properties":[{"type":"TextDecorationLine","data":["LINE_THROUGH"]},{"type":"TextDecorationColor","data":{"srgb":{"r":1,"g":0.8431372549019608,"b":0},"original":"gold"}}],"text":"Black text with gold line-through"},{"id":"wpt__css-text-decor__text-decoration-color__6-027","name":"wpt__css-text-decor__text-decoration-color__6","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__7-028","name":"wpt__css-text-decor__text-decoration-color__7","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}}],"text":"Black text with blue underline, gray overline and green line-through","meta":{"decorations":[{"line":"underline","color":"blue"},{"line":"overline","color":"gray"},{"line":"line-through","color":"green"}]}},{"id":"wpt__css-text-decor__text-decoration-color__8-029","name":"wpt__css-text-decor__text-decoration-color__8","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__9-030","name":"wpt__css-text-decor__text-decoration-color__9","properties":[]},{"id":"text-decoration-color__9__0-031","name":"text-decoration-color__9__0","properties":[{"type":"TextDecorationLine","data":["LINE_THROUGH"]},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"wpt__css-text-decor__text-decoration-color__9-030"},"meta":{"sourceTag":"span"}},{"id":"text-decoration-color__9__0__0-032","name":"text-decoration-color__9__0__0","properties":[{"type":"TextDecorationLine","data":["OVERLINE"]},{"type":"TextDecorationColor","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"gray"}}],"slot":{"parent":"text-decoration-color__9__0-031"},"text":"subscript text","meta":{"sourceTag":"sub","role":"ws-after"}},{"id":"text-decoration-color__9__0__1-033","name":"text-decoration-color__9__0__1","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}}],"slot":{"parent":"text-decoration-color__9__0-031"},"text":"superscript text","meta":{"sourceTag":"sup"}},{"id":"wpt__css-text-decor__text-decoration-color__10-034","name":"wpt__css-text-decor__text-decoration-color__10","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__11-035","name":"wpt__css-text-decor__text-decoration-color__11","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"Generic","data":{"propertyName":"-webkit-text-fill-color","rawValue":"transparent","_unmapped":true}},{"type":"Generic","data":{"propertyName":"-webkit-text-stroke-width","rawValue":"1px","_unmapped":true}},{"type":"Generic","data":{"propertyName":"-webkit-text-stroke-color","rawValue":"black","_unmapped":true}},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"text":"Transparent fill with black stroke text and green underline"},{"id":"wpt__css-text-decor__text-decoration-color__12-036","name":"wpt__css-text-decor__text-decoration-color__12","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__13-037","name":"wpt__css-text-decor__text-decoration-color__13","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"Generic","data":{"propertyName":"-webkit-text-stroke-width","rawValue":"thin","_unmapped":true}},{"type":"Generic","data":{"propertyName":"-webkit-text-stroke-color","rawValue":"LinkText","_unmapped":true}}],"text":"LinkText stroke and underline with black fill"},{"id":"wpt__css-text-decor__text-decoration-color__14-038","name":"wpt__css-text-decor__text-decoration-color__14","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}}]}"""
    // ── END GENERATED WIRE
}
