package com.styleconverter.runtime.effects.shadow

// Retro round-2 F1 (skeptic S3 must-fix), the seam half — pins the ONE line
// outside F1's ownership that the margin-box correction needs: StyleApplier's
// step-3 `marginInsets` read used to be gated on `hasBackdropFilters` alone
// ("consumed ONLY by the backdrop path"), so the box-shadow painters —
// which subtract the bands since F1 — would have kept receiving
// MarginInsets.NONE and gone on knocking out the MARGIN box. The gate now
// also fires on `config.effects.shadows.hasShadow`. This test drives the
// REAL StyleApplier.applyProperties on verbatim converter output and reads
// the insets back out of the shadow draw lambda's captured state by TYPE
// (the same idiom as ShadowMarginBoxTest / ClipCollapsedMarginThreadingTest).
// Proven able to fail: on the tree WITHOUT the gate change, the
// RingDominant and fractional-box rows read MarginInsets.NONE (F1 ran it
// both ways on an isolated copy).

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.StyleApplier
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.spacing.MarginInsets
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class StyleApplierShadowMarginThreadingTest {

    private val p = { s: String -> Json.parseToJsonElement(s) }

    /** Every MarginInsets a chain element captured, found by TYPE two levels deep. */
    private fun capturedMarginInsets(modifier: Modifier): List<MarginInsets> {
        val found = mutableListOf<MarginInsets>()
        fun scan(obj: Any, depth: Int) {
            if (obj is MarginInsets) { found += obj; return }
            if (depth == 0) return
            val name = obj.javaClass.name
            if (name.startsWith("java.") || name.startsWith("kotlin.")) return
            for (f in obj.javaClass.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers) || f.type.isPrimitive) continue
                f.isAccessible = true
                val v = runCatching { f.get(obj) }.getOrNull() ?: continue
                scan(v, depth - 1)
            }
        }
        modifier.foldIn(Unit) { _, element -> scan(element, 2) }
        return found
    }

    // BSK_Op55_RingDominant — verbatim converter output of
    // fixtures/properties/effects/box-shadow-opacity-knockout.json.
    private val ringDominant = listOf(
        IRProperty("Width", p("""{"type":"length","px":10.0}""")),
        IRProperty("Height", p("""{"type":"length","px":10.0}""")),
        IRProperty("MarginTop", p("""{"px":24.0}""")), IRProperty("MarginRight", p("""{"px":24.0}""")),
        IRProperty("MarginBottom", p("""{"px":24.0}""")), IRProperty("MarginLeft", p("""{"px":24.0}""")),
        IRProperty("BackgroundColor", p("""{"srgb":{"r":0.9058823529411765,"g":0.2980392156862745,"b":0.23529411764705882},"original":"#e74c3c"}""")),
        IRProperty("BoxShadow", p("""[{"x":{"px":0.0},"y":{"px":0.0},"blur":{"px":0.0},"spread":{"px":20.0},"c":{"srgb":{"r":0.20392156862745098,"g":0.596078431372549,"b":0.8588235294117647},"original":"#3498db"}}]""")),
        IRProperty("Opacity", p("""{"alpha":0.55,"original":{"type":"number","value":0.55}}""")),
    )

    // wave49-final css-view-transitions fractional-box-with-shadow-new__1 —
    // the RELATIVE box (left 0.4px) with margin 15 and the darkgreen ring.
    private val fractionalBox = listOf(
        IRProperty("BoxShadow", p("""[{"x":{"px":-2},"y":{"px":-3},"blur":{"px":0},"spread":{"px":7},"c":{"srgb":{"r":0,"g":0.39215686274509803,"b":0},"original":"darkgreen"}}]""")),
        IRProperty("Width", p("""{"type":"length","px":100.125}""")),
        IRProperty("Height", p("""{"type":"length","px":50.875}""")),
        IRProperty("Position", p("\"RELATIVE\"")),
        IRProperty("Left", p("""{"px":0.4}""")),
        IRProperty("Top", p("""{"px":0}""")),
        IRProperty("MarginTop", p("""{"px":15}""")), IRProperty("MarginRight", p("""{"px":15}""")),
        IRProperty("MarginBottom", p("""{"px":15}""")), IRProperty("MarginLeft", p("""{"px":15}""")),
        IRProperty("BackgroundColor", p("""{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":{"r":0,"g":128,"b":0}}""")),
        IRProperty("Display", p("\"BLOCK\"")),
    )

    @Test
    fun `a margined shadow carrier hands its resolved bands to the shadow painter`() {
        val captured = capturedMarginInsets(StyleApplier.applyProperties(ringDominant))
        assertEquals(listOf(MarginInsets(24.dp, 24.dp, 24.dp, 24.dp)), captured)
    }

    @Test
    fun `the wave49 fractional-box carrier hands its 15px bands to the shadow painter`() {
        val captured = capturedMarginInsets(StyleApplier.applyProperties(fractionalBox))
        assertEquals(listOf(MarginInsets(15.dp, 15.dp, 15.dp, 15.dp)), captured)
    }

    @Test
    fun `a margin-less shadow carrier still hands NONE`() {
        // BSK_Op55_SpreadRing: same fixture, no margins — the gate fires (a
        // shadow is declared) but resolvedInsets short-circuits to NONE.
        val spreadRing = ringDominant.filterNot { it.type.startsWith("Margin") }
        assertEquals(listOf(MarginInsets.NONE), capturedMarginInsets(StyleApplier.applyProperties(spreadRing)))
    }
}
