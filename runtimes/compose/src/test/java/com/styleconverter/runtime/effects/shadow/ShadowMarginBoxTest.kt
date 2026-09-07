package com.styleconverter.runtime.effects.shadow

// Retro round-2 F1 (skeptic S3 must-fix) — the box-shadow painters' MARGIN-box
// correction. StyleApplier installs the effects step (3) OUTER of the layout
// step (4), whose MarginApplier emits positive margins as `absolutePadding`,
// so both shadow draw nodes are sized by the element's MARGIN box (CSS2 §8.1)
// while css-backgrounds-3 §6.1.1 knocks the shadow out of — and hangs its
// perimeter on — the BORDER box. R6's knockout read the node size: on R6's
// own fixture BSK_Op55_RingDominant (10px box, `margin: 24px`, `box-shadow:
// 0 0 0 20px`) the Difference clip knocked out the whole 58×58 node and the
// ring sat at (−20,−20)–(78,78): a 98×98 ring with a 24px ground gap instead
// of the 50×50 ring the fixture expects (`_expect.box` [50,50] ±1). The four
// resolved bands (MarginApplier.resolvedInsets — literally step 4's padding)
// are now threaded EffectsFacade → ShadowApplier → both painters and
// subtracted inside ShadowGeometry, exactly as the backdrop lane does.
//
// Three layers are pinned here without a renderer (no Robolectric; the
// Difference clip itself is an android.graphics call, pinned on device by the
// fixture): (1) the pure geometry; (2) the facade→painter threading, read
// back out of the draw lambda's captured state by TYPE (the same reflection
// idiom ClipCollapsedMarginThreadingTest uses for the clip lane) — a Kotlin
// lambda keeps each captured value in a typed field whether it compiles to a
// class or an invokedynamic hidden class; (3) the PREMISE, on the real chain:
// StyleApplier.applyProperties of the fixture's verbatim IR folds the shadow
// DrawBehind OUTER of the margin PaddingElement, so a future re-order (which
// would make the subtraction wrong) is caught. The StyleApplier gate that
// resolves the insets for shadow carriers is pinned by
// StyleApplierShadowMarginThreadingTest (F1's seam patch).
// Proven able to fail (mutations run by F1 on an isolated copy): dropping the
// `band(...)` terms from ShadowGeometry.borderBoxRect fails `border box is
// the node minus its margin bands` and `fixture ring`; dropping the
// `marginInsets` argument from EffectsFacade's applyShadow call fails both
// `facade threads …` tests.

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.StyleApplier
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.effects.EffectsFacade
import com.styleconverter.runtime.spacing.MarginInsets
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowMarginBoxTest {

    private val m24 = MarginInsets(24.dp, 24.dp, 24.dp, 24.dp)
    private val ring = Color(52f / 255f, 152f / 255f, 219f / 255f)   // #3498db

    private fun elementNames(modifier: Modifier): List<String> =
        modifier.foldIn(emptyList<String>()) { acc, element -> acc + element.javaClass.name }

    /**
     * Every MarginInsets a chain element captured, found by TYPE two levels
     * deep (element → draw lambda → captured value). JDK/Kotlin runtime
     * objects are not descended into; static fields are skipped.
     */
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

    // ── (1) pure geometry ─────────────────────────────────────────────────

    @Test
    fun `border box is the node minus its margin bands`() {
        // S3's pin: BSK_Op55_RingDominant's 58×58 node (10 + 2·24) with 24px
        // bands on every side → the (24,24)–(34,34) box, 10×10.
        assertEquals(Rect(24f, 24f, 34f, 34f), ShadowGeometry.borderBoxRect(58f, 58f, 0f, 0f, 24f, 24f, 24f, 24f))
        // Asymmetric bands strip each side independently.
        val box = ShadowGeometry.borderBoxRect(100f, 50f, 0f, 0f, 10f, 0f, 30f, 5f)
        assertEquals(Rect(10f, 0f, 70f, 45f), box)
    }

    @Test
    fun `margin bands and position offset add on the origin and never on the size`() {
        val box = ShadowGeometry.borderBoxRect(58f, 58f, 5f, 7f, 24f, 24f, 24f, 24f)
        assertEquals(Rect(29f, 31f, 39f, 41f), box)
        assertEquals(10f, box.width, 0f)
        assertEquals(10f, box.height, 0f)
    }

    @Test
    fun `fixture ring - the perimeter is the border box inflated by the spread`() {
        // `box-shadow: 0 0 0 20px` on the 10×10 border box → (4,4)–(54,54):
        // the 50×50 ring `_expect.box` names. The margin-box formula gave
        // (−20,−20)–(78,78), the 98×98 ring S3 predicted RED.
        val ring = ShadowGeometry.outsetShadowRect(58f, 58f, 0f, 0f, 20f, 0f, 0f, 24f, 24f, 24f, 24f)
        assertEquals(Rect(4f, 4f, 54f, 54f), ring)
        assertEquals(50f, ring.width, 0f)
        // At zero spread and zero shadow offset the perimeter IS the border box.
        assertEquals(
            ShadowGeometry.borderBoxRect(58f, 58f, 0f, 0f, 24f, 24f, 24f, 24f),
            ShadowGeometry.outsetShadowRect(58f, 58f, 0f, 0f, 0f, 0f, 0f, 24f, 24f, 24f, 24f),
        )
    }

    @Test
    fun `corpus carrier fractional-box-with-shadow hugs its border box`() {
        // wave49-final css-view-transitions fractional-box-with-shadow-new/-old:
        // 100.125×50.875 box, margin 15, `box-shadow: -2px -3px 0 7px`. Node =
        // 130.125×80.875; the ring must sit 7px around the box at (15,15),
        // slid by (−2,−3): (6,5)–(120.125,69.875). The old formula put its
        // left edge at −9 — the whole 15px margin band read as shadow.
        val r = ShadowGeometry.outsetShadowRect(130.125f, 80.875f, -2f, -3f, 7f, 0f, 0f, 15f, 15f, 15f, 15f)
        assertEquals(6f, r.left, 1e-4f)
        assertEquals(5f, r.top, 1e-4f)
        assertEquals(120.125f, r.right, 1e-4f)
        assertEquals(69.875f, r.bottom, 1e-4f)
    }

    @Test
    fun `zero bands reduce to the pre-fix formulas and negative bands never grow the box`() {
        // Byte-identity for the margin-less corpus: the eight-arg form with
        // zero bands equals the legacy call on every edge.
        assertEquals(ShadowGeometry.outsetShadowRect(100f, 50f, 20f, 10f, 5f), ShadowGeometry.outsetShadowRect(100f, 50f, 20f, 10f, 5f, 0f, 0f, 0f, 0f, 0f, 0f))
        assertEquals(ShadowGeometry.borderBoxRect(120f, 60f, 75f, 125f), ShadowGeometry.borderBoxRect(120f, 60f, 75f, 125f, 0f, 0f, 0f, 0f))
        // A negative margin rides Modifier.offset (no band to subtract) —
        // clamped to 0, like BackdropSampleGeometry.borderBox.
        assertEquals(ShadowGeometry.borderBoxRect(58f, 58f), ShadowGeometry.borderBoxRect(58f, 58f, 0f, 0f, -10f, -10f, -10f, -10f))
    }

    @Test
    fun `corner radius percentages resolve against the border box dimension`() {
        // css-backgrounds-3 §4.1: `border-radius: 50%` on the 10px border box
        // is 5px, grown by the 20px spread → 25 — NOT 50% of the 58px node.
        assertEquals(25f, ShadowGeometry.cornerRadiusPx(0f, 0.5f, 10f, 20f), 0f)
        // A Dp radius is already resolved; the fraction wins when present.
        assertEquals(8f, ShadowGeometry.cornerRadiusPx(8f, null, 10f, 0f), 0f)
        assertEquals(5f, ShadowGeometry.cornerRadiusPx(8f, 0.5f, 10f, 0f), 0f)
    }

    // ── (2) facade → painter threading ────────────────────────────────────

    @Test
    fun `facade threads the margin bands into the outset painter's draw node`() {
        val cfg = EffectsFacade.EffectsConfig(shadows = ShadowConfig(listOf(ShadowData(spreadRadius = 20.dp, color = ring))))
        val threaded = EffectsFacade.apply(Modifier, cfg, marginInsets = m24)
        assertEquals(listOf(m24), capturedMarginInsets(threaded))
        // Default: the painter captures NONE, the margin-less contract.
        assertEquals(listOf(MarginInsets.NONE), capturedMarginInsets(EffectsFacade.apply(Modifier, cfg)))
    }

    @Test
    fun `facade threads the margin bands into the inset painter's draw node`() {
        val cfg = EffectsFacade.EffectsConfig(shadows = ShadowConfig(listOf(ShadowData(blurRadius = 6.dp, inset = true))))
        val threaded = EffectsFacade.apply(Modifier, cfg, marginInsets = m24)
        assertEquals(listOf(m24), capturedMarginInsets(threaded))
    }

    @Test
    fun `margin bands keep the single draw node per painter`() {
        // The bands ride INSIDE the existing lambdas — no new node, so the
        // chain pins in ShadowApplierTest / ShadowPositionOffsetTest hold.
        val cfg = ShadowConfig(listOf(ShadowData(spreadRadius = 3.dp, color = ring)))
        val withBands = elementNames(ShadowApplier.applyShadow(Modifier, cfg, positionOffset = DpOffset(6.dp, 4.dp), marginInsets = m24))
        assertEquals(elementNames(ShadowApplier.applyShadow(Modifier, cfg)), withBands)
        assertEquals(withBands.joinToString(), 1, withBands.size)
        assertTrue(withBands[0], withBands[0].contains("DrawBehind"))
    }

    // ── (3) the premise, on the live chain ────────────────────────────────

    @Test
    fun `the shadow DrawBehind is OUTER of the margin PaddingElement on the live chain`() {
        // BSK_Op55_RingDominant's verbatim converter output (fixtures/
        // properties/effects/box-shadow-opacity-knockout.json → IR).
        val chain = elementNames(StyleApplier.applyProperties(ringDominantIr()))
        val shadow = chain.indexOfFirst { it.contains("DrawBehindElement") }
        val margin = chain.indexOfFirst { it.contains("layout.PaddingElement") }
        assertTrue("no shadow DrawBehind in $chain", shadow >= 0)
        assertTrue("no margin PaddingElement in $chain", margin >= 0)
        // Outer-to-inner fold order: the shadow node is sized by everything
        // INSIDE it — the margin padding included — which is the whole reason
        // the bands must be subtracted. A re-order would silently double-strip.
        assertTrue("shadow ($shadow) must be outer of the margin padding ($margin): $chain", shadow < margin)
    }

    private fun ringDominantIr(): List<IRProperty> {
        val p = { s: String -> Json.parseToJsonElement(s) }
        return listOf(
            IRProperty("Width", p("""{"type":"length","px":10.0}""")),
            IRProperty("Height", p("""{"type":"length","px":10.0}""")),
            IRProperty("MarginTop", p("""{"px":24.0}""")), IRProperty("MarginRight", p("""{"px":24.0}""")),
            IRProperty("MarginBottom", p("""{"px":24.0}""")), IRProperty("MarginLeft", p("""{"px":24.0}""")),
            IRProperty("BackgroundColor", p("""{"srgb":{"r":0.9058823529411765,"g":0.2980392156862745,"b":0.23529411764705882},"original":"#e74c3c"}""")),
            IRProperty("BoxShadow", p("""[{"x":{"px":0.0},"y":{"px":0.0},"blur":{"px":0.0},"spread":{"px":20.0},"c":{"srgb":{"r":0.20392156862745098,"g":0.596078431372549,"b":0.8588235294117647},"original":"#3498db"}}]""")),
            IRProperty("Opacity", p("""{"alpha":0.55,"original":{"type":"number","value":0.55}}""")),
        )
    }
}
