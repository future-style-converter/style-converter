package com.styleconverter.runtime.core.renderer

// Fidelity wave 3 — pinning tests for the block-flow / placeholder fixes:
//
//  1. autoMarginAlignment — CSS 2.1 §10.3.3 auto-margin resolution for
//     block-level children (B_MarginAuto: web centers the 120px child in
//     the 300px parent, Android left-pinned it, 0.805 / 17.3% px).
//
//  2. placeholderOverflow — CSS 2.1 §11.1.1 initial `overflow: visible`:
//     the placeholder Text must not clip glyphs unless the fixture
//     declares clipping intent (Transforms_BoxModel: 6px content box
//     erased the label entirely under Compose's Clip default).
//
//  3. Inherited typography must reach the placeholder TextStyle — the
//     wave-2 inheritance channel only fed the modifier chain, so children
//     with EMPTY properties rendered 16sp defaults (IH_FontSize 0.865,
//     IH_LineHeight 0.702). The merge + extractTextStyle pair pins the
//     full path: parent FontSize/LineHeight land in the child's style.

import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.typography.TextStyleApplier
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockFlowFidelityWave3Test {

    private fun prop(t: String, s: String) = IRProperty(t, Json.parseToJsonElement(s))

    // ── CSS 2.1 §10.3.3 auto margins ─────────────────────────────────────

    @Test
    fun `both auto margins with definite width center the child`() {
        // Exact IR of B_MarginAuto's child: width + margin-left/right: auto.
        val align = ComponentRenderer.autoMarginAlignment(
            listOf(
                prop("Width", """{"type":"length","px":120.0}"""),
                prop("MarginLeft", "\"auto\""),
                prop("MarginRight", "\"auto\"")
            )
        )
        assertEquals(Alignment.TopCenter, align)
    }

    @Test
    fun `left-only auto margin pushes the child to the end`() {
        // §10.3.3: one auto margin absorbs ALL the free space.
        val align = ComponentRenderer.autoMarginAlignment(
            listOf(
                prop("Width", """{"type":"length","px":120.0}"""),
                prop("MarginLeft", "\"auto\"")
            )
        )
        assertEquals(Alignment.TopEnd, align)
    }

    @Test
    fun `right-only auto margin keeps the child at the left edge`() {
        // Box stays left — the wrapper would be a no-op, so null (skip).
        val align = ComponentRenderer.autoMarginAlignment(
            listOf(
                prop("Width", """{"type":"length","px":120.0}"""),
                prop("MarginRight", "\"auto\"")
            )
        )
        assertNull(align)
    }

    @Test
    fun `auto margins without a definite width resolve to zero`() {
        // §10.3.3: width:auto absorbs the space instead of the margins.
        val align = ComponentRenderer.autoMarginAlignment(
            listOf(
                prop("MarginLeft", "\"auto\""),
                prop("MarginRight", "\"auto\"")
            )
        )
        assertNull(align)
    }

    @Test
    fun `numeric margins never trigger the alignment wrapper`() {
        val align = ComponentRenderer.autoMarginAlignment(
            listOf(
                prop("Width", """{"type":"length","px":120.0}"""),
                prop("MarginLeft", """{"px":8.0}"""),
                prop("MarginRight", """{"px":8.0}""")
            )
        )
        assertNull(align)
    }

    // ── placeholder overflow policy ──────────────────────────────────────

    @Test
    fun `wrapping text without clip intent paints visible`() {
        // Transforms_BoxModel: no text-overflow, no line-clamp → Visible.
        val overflow = ComponentRenderer.placeholderOverflow(
            properties = listOf(prop("Height", """{"type":"length","px":48.0}""")),
            effectiveMaxLines = Int.MAX_VALUE,
            declared = TextOverflow.Clip
        )
        assertEquals(TextOverflow.Visible, overflow)
    }

    @Test
    fun `explicit text-overflow keeps the declared clipping`() {
        // Ellipsis fixtures must keep their behaviour (wave-1 landmine).
        val overflow = ComponentRenderer.placeholderOverflow(
            properties = listOf(prop("TextOverflow", "\"ELLIPSIS\"")),
            effectiveMaxLines = Int.MAX_VALUE,
            declared = TextOverflow.Ellipsis
        )
        assertEquals(TextOverflow.Ellipsis, overflow)
    }

    @Test
    fun `line-clamp keeps the declared clipping`() {
        val overflow = ComponentRenderer.placeholderOverflow(
            properties = emptyList(),
            effectiveMaxLines = 2,
            declared = TextOverflow.Clip
        )
        assertEquals(TextOverflow.Clip, overflow)
    }

    // ── inherited typography reaches the placeholder style ──────────────

    @Test
    fun `merged parent font-size lands in the child text style`() {
        // IH_FontSize: parent font-size 22px, child declares nothing.
        val inherited = listOf(prop("FontSize", """{"px":22.0,"original":{"type":"length","px":22.0}}"""))
        val merged = ComponentRenderer.mergeInherited(emptyList(), inherited)
        val style = TextStyleApplier.extractTextStyle(merged)
        assertEquals(22.sp, style.fontSize)
    }

    @Test
    fun `merged parent unitless line-height resolves against the inherited font-size`() {
        // IH_LineHeight: font-size 14px + line-height 2 → 28sp line box.
        val inherited = listOf(
            prop("FontSize", """{"px":14.0,"original":{"type":"length","px":14.0}}"""),
            prop("LineHeight", """{"multiplier":2.0,"original":{"type":"number","value":2.0}}""")
        )
        val merged = ComponentRenderer.mergeInherited(emptyList(), inherited)
        val style = TextStyleApplier.extractTextStyle(merged)
        assertEquals(14.sp, style.fontSize)
        assertEquals(28.sp, style.lineHeight)
    }

    @Test
    fun `child redeclaration beats the inherited value`() {
        // IH_FontSize's second child: font-size 12px wins over parent 22px.
        val inherited = listOf(prop("FontSize", """{"px":22.0}"""))
        val own = listOf(prop("FontSize", """{"px":12.0}"""))
        val merged = ComponentRenderer.mergeInherited(own, inherited)
        val style = TextStyleApplier.extractTextStyle(merged)
        assertEquals(12.sp, style.fontSize)
    }
}
