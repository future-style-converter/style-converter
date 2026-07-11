package com.styleconverter.runtime.core.renderer

// Fidelity wave 1 (+ wave 9, #37) — pinning tests for:
//  1. mergeInherited — the runtime CSS-inheritance channel (parent's
//     inheritable properties flow under the child's own declarations).
//  2. INHERITED_PROPERTY_TYPES — the whitelist itself: layout/box
//     properties must never inherit; wave 9 broadened the original
//     14-text-property channel to the full CSS inherited set our IR
//     carries, INCLUDING Color (currentColor consumers resolve against
//     the merged list — the leaf placeholder keeps its own contrast-pick
//     gate, see LocalColorIsInheritedOnly).
//  3. hasDefiniteSize — the definite-size predicate that gates flex-grow →
//     Modifier.weight translation (css-flexbox-1 §9.7: free space only
//     exists on a definite main axis; the web reference's fit-content
//     default means an unsized container hugs and grow is a no-op).

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InheritanceAndFlexGatingTest {

    private fun prop(t: String, j: String) = IRProperty(t, Json.parseToJsonElement(j))

    // ── mergeInherited ───────────────────────────────────────────────────

    @Test
    fun `inherited properties fill types the child did not declare`() {
        val inherited = listOf(
            prop("FontSize", """{"px":15.0}"""),
            prop("FontFamily", """["monospace"]""")
        )
        val own = listOf(prop("FontFamily", """["serif"]"""))
        val merged = ComponentRenderer.mergeInherited(own, inherited)
        // FontSize arrives from the parent; FontFamily keeps the child's own.
        assertTrue(merged.any { it.type == "FontSize" })
        val family = merged.first { it.type == "FontFamily" }
        assertTrue(family.data.toString().contains("serif"))
        // No duplicate FontFamily entries.
        assertEquals(1, merged.count { it.type == "FontFamily" })
    }

    @Test
    fun `empty inherited channel returns own list unchanged`() {
        val own = listOf(prop("Width", """{"type":"length","px":100.0}"""))
        assertEquals(own, ComponentRenderer.mergeInherited(own, emptyList()))
    }

    // ── whitelist shape ──────────────────────────────────────────────────

    @Test
    fun `box properties never inherit`() {
        for (t in listOf(
            "Width", "Height", "PaddingTop", "BackgroundColor", "BorderTopWidth", "Display",
            // Wave 9 spot-checks: decoration propagates (not inherits), and
            // the box-model / positioning families stay uninheritable.
            "TextDecorationLine", "Margin", "Position", "BoxShadow", "Opacity", "Overflow"
        )) {
            assertFalse("$t must not inherit", t in ComponentRenderer.INHERITED_PROPERTY_TYPES)
        }
    }

    @Test
    fun `wave 1 text properties and wave 9 Color inherit`() {
        for (t in listOf("FontFamily", "FontSize", "FontWeight", "LetterSpacing", "TextAlign", "TextTransform")) {
            assertTrue("$t must inherit", t in ComponentRenderer.INHERITED_PROPERTY_TYPES)
        }
        // Wave 9 (#37): Color inherits so currentColor consumers (border /
        // outline / text-emphasis extractors read "Color" from the MERGED
        // list) resolve against the ancestor chain like the browser. The
        // leaf placeholder keeps web parity through the separate
        // LocalColorIsInheritedOnly gate — NOT by excluding Color here.
        assertTrue("Color" in ComponentRenderer.INHERITED_PROPERTY_TYPES)
    }

    @Test
    fun `wave 9 extended inherited set carries the css-cascade yes-table`() {
        // css-cascade-4 "Inherited: yes" properties our IR carries — each
        // must ride the channel (fixture trees/inheritance-extended.json
        // exercises the visible subset; this pin covers the whole list).
        for (t in listOf(
            "Visibility", "Cursor",
            "ListStyleType", "ListStylePosition", "ListStyleImage",
            "Quotes", "TextShadow",
            "OverflowWrap", "WordBreak", "Hyphens",
            "TextEmphasisStyle", "TextEmphasisColor", "TextEmphasisPosition",
            "RubyAlign", "RubyPosition",
            "CaptionSide", "BorderCollapse", "BorderSpacing", "EmptyCells",
            "Orphans", "Widows"
        )) {
            assertTrue("$t must inherit (wave 9)", t in ComponentRenderer.INHERITED_PROPERTY_TYPES)
        }
    }

    // ── wave 9: transitive chains through the merge/filter pair ─────────

    /** One parent→child hop as RenderComponent performs it: merge the
     *  channel under own, then re-filter what flows to grandchildren. */
    private fun hop(channel: List<IRProperty>, own: List<IRProperty>): List<IRProperty> =
        ComponentRenderer.mergeInherited(own, channel)
            .filter { it.type in ComponentRenderer.INHERITED_PROPERTY_TYPES }

    @Test
    fun `color survives a chain of three when nobody redeclares`() {
        val root = listOf(
            prop("Color", """{"srgb":{"r":0.5,"g":0.0,"b":0.0,"a":1.0}}"""),
            prop("TextShadow", """[{"x":2.0,"y":2.0,"blur":3.0}]""")
        )
        // root → mid (declares nothing) → leaf (declares nothing).
        val midChannel = hop(root.filter { it.type in ComponentRenderer.INHERITED_PROPERTY_TYPES }, emptyList())
        val leafChannel = hop(midChannel, emptyList())
        // Both inherited values arrive at the leaf untouched.
        assertTrue(leafChannel.any { it.type == "Color" && it.data.toString().contains("0.5") })
        assertTrue(leafChannel.any { it.type == "TextShadow" })
    }

    @Test
    fun `mid-chain redeclaration wins for descendants`() {
        val rootChannel = listOf(prop("Color", """{"srgb":{"r":1.0,"g":0.0,"b":0.0,"a":1.0}}"""))
        // mid redeclares Color — its own value must replace the root's in
        // what flows onward (css-cascade-4: children inherit the parent's
        // COMPUTED value, and own declarations beat inherited ones).
        val midOwn = listOf(prop("Color", """{"srgb":{"r":0.0,"g":0.0,"b":1.0,"a":1.0}}"""))
        val leafChannel = hop(rootChannel, midOwn)
        assertEquals(1, leafChannel.count { it.type == "Color" })
        assertTrue(leafChannel.first { it.type == "Color" }.data.toString().contains("\"b\":1.0"))
    }

    @Test
    fun `root sees initial values - empty channel adds nothing`() {
        // At the tree root the channel is empty (LocalInheritedProperties
        // default) — merge must be an identity and nothing may leak in.
        val own = listOf(prop("Width", """{"type":"length","px":100.0}"""))
        assertEquals(own, ComponentRenderer.mergeInherited(own, emptyList()))
        // And a root with no declarations inherits nothing at all.
        assertTrue(hop(emptyList(), emptyList()).isEmpty())
    }

    // ── hasDefiniteSize (flex-grow gating) ───────────────────────────────

    @Test
    fun `resolved px width is definite`() {
        assertTrue(ComponentRenderer.hasDefiniteSize(
            listOf(prop("Width", """{"type":"length","px":320.0}""")), widthAxis = true))
    }

    @Test
    fun `percentage width is definite`() {
        assertTrue(ComponentRenderer.hasDefiniteSize(
            listOf(prop("Width", """{"type":"percentage","value":50.0}""")), widthAxis = true))
    }

    @Test
    fun `absent or unresolvable width is indefinite`() {
        // No Width at all — the nested-3level cellA case (flex-grow children
        // must NOT translate to weight; the container hugs like web).
        assertFalse(ComponentRenderer.hasDefiniteSize(emptyList(), widthAxis = true))
        // em width the parser couldn't resolve (px:null) is also indefinite.
        assertFalse(ComponentRenderer.hasDefiniteSize(
            listOf(prop("Width", """{"type":"length","px":null}""")), widthAxis = true))
    }

    @Test
    fun `height axis reads Height and BlockSize`() {
        assertTrue(ComponentRenderer.hasDefiniteSize(
            listOf(prop("Height", """{"type":"length","px":60.0}""")), widthAxis = false))
        // Width must NOT satisfy the height axis.
        assertFalse(ComponentRenderer.hasDefiniteSize(
            listOf(prop("Width", """{"type":"length","px":60.0}""")), widthAxis = false))
    }
}
