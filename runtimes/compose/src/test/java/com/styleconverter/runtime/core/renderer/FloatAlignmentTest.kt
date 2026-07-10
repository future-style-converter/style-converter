package com.styleconverter.runtime.core.renderer

// Wave-5 pinning — CSS 2.1 §9.5 float approximation. `float: right` (and
// the logical `inline-end` in the LTR-normalized engine) must shift the box
// to the END of its containing block; web floats PW_Borders_Layout_03's
// 200px green box flush against the right canvas edge while Android left it
// at the left (A-w 0.406 / 55% px, iOS sharing the identical native gap).

import androidx.compose.ui.Alignment
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatAlignmentTest {

    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    @Test
    fun `float right aligns the box to the end`() {
        val a = ComponentRenderer.floatEndAlignment(listOf(prop("Float", "\"RIGHT\"")))
        assertEquals(Alignment.TopEnd, a)
    }

    @Test
    fun `float inline-end aligns to the end in LTR`() {
        // css-logical-1 §2.1: inline-end maps to right under LTR.
        val a = ComponentRenderer.floatEndAlignment(listOf(prop("Float", "\"INLINE_END\"")))
        assertEquals(Alignment.TopEnd, a)
    }

    @Test
    fun `float left and none keep block-start flow`() {
        // Left/inline-start is the block default — no wrapper, no-op.
        assertNull(ComponentRenderer.floatEndAlignment(listOf(prop("Float", "\"LEFT\""))))
        assertNull(ComponentRenderer.floatEndAlignment(listOf(prop("Float", "\"NONE\""))))
        assertNull(ComponentRenderer.floatEndAlignment(emptyList()))
    }
}
