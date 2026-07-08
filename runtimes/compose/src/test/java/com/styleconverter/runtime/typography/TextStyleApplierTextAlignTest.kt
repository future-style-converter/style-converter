package com.styleconverter.runtime.typography

// Regression test: extractTextStyle parsed the "TextAlign" IR property
// into a local variable but never copied it into the returned TextStyle,
// so text-align silently degraded to Start on every Android render
// (visual-test TextAlign_Center: web centered, Android left-flushed,
// Android-web SSIM 0.86). Pins the full keyword mapping while at it —
// the parser emits bare uppercase keywords (css-text-3 §6.1 values).

import androidx.compose.ui.text.style.TextAlign
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class TextStyleApplierTextAlignTest {

    private fun style(keyword: String) = TextStyleApplier.extractTextStyle(
        listOf(IRProperty(type = "TextAlign", data = JsonPrimitive(keyword)))
    )

    @Test
    fun `center reaches the returned TextStyle`() {
        // The regression: this came back Unspecified (→ Start downstream).
        assertEquals(TextAlign.Center, style("CENTER").textAlign)
    }

    @Test
    fun `start-family and end-family keywords map`() {
        assertEquals(TextAlign.Start, style("LEFT").textAlign)
        assertEquals(TextAlign.Start, style("START").textAlign)
        assertEquals(TextAlign.End, style("RIGHT").textAlign)
        assertEquals(TextAlign.End, style("END").textAlign)
        assertEquals(TextAlign.Justify, style("JUSTIFY").textAlign)
    }

    @Test
    fun `absent property stays Unspecified`() {
        // No TextAlign in the IR → Unspecified, so ComponentRenderer's
        // placeholder default (Start, matching CSS initial `start`) wins.
        assertEquals(
            TextAlign.Unspecified,
            TextStyleApplier.extractTextStyle(emptyList()).textAlign
        )
    }
}
