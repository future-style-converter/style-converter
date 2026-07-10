package com.styleconverter.runtime.core.renderer

// Fidelity wave 1 — pinning tests for:
//  1. mergeInherited — the runtime CSS-inheritance channel (parent's
//     inheritable TEXT properties flow under the child's own declarations).
//  2. INHERITED_TEXT_PROPERTY_TYPES — the whitelist itself: layout/box
//     properties must never inherit, and Color is deliberately excluded
//     (the web reference placeholder always overrides color with its
//     luminance pick — verified by pixel-sampling the IT_Family capture).
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
        for (t in listOf("Width", "Height", "PaddingTop", "BackgroundColor", "BorderTopWidth", "Display")) {
            assertFalse("$t must not inherit", t in ComponentRenderer.INHERITED_TEXT_PROPERTY_TYPES)
        }
    }

    @Test
    fun `font properties inherit but Color does not`() {
        for (t in listOf("FontFamily", "FontSize", "FontWeight", "LetterSpacing", "TextAlign", "TextTransform")) {
            assertTrue("$t must inherit", t in ComponentRenderer.INHERITED_TEXT_PROPERTY_TYPES)
        }
        // Color is excluded on purpose: the web harness placeholder span
        // always sets an explicit color (bg-luminance pick), so inheriting
        // Color on Android would diverge FROM the reference.
        assertFalse("Color" in ComponentRenderer.INHERITED_TEXT_PROPERTY_TYPES)
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
