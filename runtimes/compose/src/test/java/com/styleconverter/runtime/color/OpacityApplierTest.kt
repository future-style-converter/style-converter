package com.styleconverter.runtime.color

// Wave 43 (lane V2) — pins for the CSS-opacity NO-CLIP fix.
//
// The defect (wave-42 W8's bytecode-proven deferral): opacity was applied
// via Modifier.alpha, which IS graphicsLayer(alpha, clip = TRUE)
// (ui-android 1.11.4 AlphaKt bytecode: default-mask 520187 leaves alpha +
// clip the only explicit params, clip pushed iconst_1). CSS opacity forms
// a transparency group but NEVER clips (css-color-4 §3.3). Measured: WPT
// css-color/composited-filters-under-opacity android-ref 0.9401 — the
// `left: 50px` child cropped at the opacity parent's right edge (x=116).
//
// The fix must ALSO avoid graphicsLayer(alpha, clip = false): GraphicsLayerV29
// (ui-graphics-android 1.11.4) routes alpha to RenderNode.setAlpha with
// hasOverlappingRendering(true) under CompositingStrategy.Auto, and HWUI
// then simulates a layer AT THE NODE'S OWN BOUNDS (AOSP
// RenderNodeDrawable::setViewProperties saveLayerAlpha(&bounds, α)) — the
// crop survives at composite time. So these pins assert the STRONGER
// contract: opacity < 1 installs the unbounded draw-time group (the
// IsolationApplier precedent) and NO GraphicsLayer element of any kind.
// Platform saveLayer calls stay inside the draw lambda (android.graphics
// is a throwing stub on this JVM suite), so the chain folds without
// drawing — the IsolationApplierTest / FilterGroupApplierTest technique.

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpacityApplierTest {

    // Fold the modifier into concrete element class names — install/skip
    // decisions become assertable without a renderer (IsolationApplierTest
    // idiom; Modifier.alpha would surface here as a GraphicsLayerElement).
    private fun elementNames(modifier: Modifier): List<String> =
        modifier.foldIn(emptyList<String>()) { acc, element -> acc + element.javaClass.name }

    // Verbatim-JSON helper matching ColorExtractorTest's input idiom.
    private fun parse(j: String): JsonElement = Json.parseToJsonElement(j)

    @Test
    fun `opacity below 1 installs one unbounded draw-time group`() {
        val names = elementNames(OpacityApplier.applyOpacity(Modifier, 0.5f))
        // Exactly one drawWithContent node — the unbounded saveLayer group.
        assertEquals(1, names.size)
        assertTrue(
            "expected the draw-time group element in $names",
            names.any { it.contains("DrawWithContent") }
        )
    }

    @Test
    fun `opacity below 1 installs NO graphicsLayer of any kind`() {
        val names = elementNames(OpacityApplier.applyOpacity(Modifier, 0.5f))
        // The whole point of the fix: ANY GraphicsLayer element here means
        // node-bounds buffer physics returned — clip=true crops at record
        // time (AlphaKt) and even clip=false crops at composite time
        // (HWUI's overlapping-rendering saveLayerAlpha at node bounds).
        assertFalse(
            "no graphicsLayer may install in $names",
            names.any { it.contains("GraphicsLayer") }
        )
    }

    @Test
    fun `opacity 1 is the identity - no layer no group`() {
        // Parity with the retired Modifier.alpha fast path (== 1f -> this):
        // explicit `opacity: 1` must keep producing a byte-identical chain.
        assertEquals(emptyList<String>(), elementNames(OpacityApplier.applyOpacity(Modifier, 1f)))
    }

    @Test
    fun `opacity above 1 clamps to 1 and stays the identity`() {
        // css-color-4 §3.3: out-of-range values clip to [0,1] — 1.5 renders
        // like 1, which renders normally (no group).
        assertEquals(emptyList<String>(), elementNames(OpacityApplier.applyOpacity(Modifier, 1.5f)))
    }

    @Test
    fun `negative opacity clamps to 0 and still installs the group`() {
        // -0.5 clamps to 0: fully transparent — the group MUST install
        // (alpha-0 layer draws nothing), never fall through to visible.
        val names = elementNames(OpacityApplier.applyOpacity(Modifier, -0.5f))
        assertEquals(1, names.size)
        assertTrue(names.any { it.contains("DrawWithContent") })
    }

    @Test
    fun `applyColors wires opacity through the group with no graphicsLayer`() {
        // The live route: StyleApplier -> ColorApplier.applyColors step 1.
        val names = elementNames(
            ColorApplier.applyColors(Modifier, ColorConfig(opacity = 0.5f))
        )
        assertTrue(names.any { it.contains("DrawWithContent") })
        assertFalse(names.any { it.contains("GraphicsLayer") })
    }

    @Test
    fun `composited-filters-under-opacity IR builds the uncropped group`() {
        // The EXACT Opacity property the wave42-final gate consumed for the
        // measured victim (per-test-ir/wpt__css-color__composited-filters-
        // under-opacity.json, component wpt__css-color__composited-filters-
        // under-opacity__1-244: the `opacity: 0.5` parent of the two abspos
        // .content children) — through the real extractor, then the applier.
        val cfg = ColorExtractor.extractColorConfig(listOf(
            "Opacity" to parse("{\"alpha\":0.5,\"original\":{\"type\":\"number\",\"value\":0.5}}")
        ))
        // Extractor sanity: the 0.5 alpha envelope lands in the config.
        assertEquals(0.5f, cfg.opacity)
        val names = elementNames(ColorApplier.applyColors(Modifier, cfg))
        // Opacity is this component's only paint property → exactly the
        // group element, and never the clipping layer that cropped x>116.
        assertEquals(1, names.size)
        assertTrue(names[0].contains("DrawWithContent"))
        assertFalse(names.any { it.contains("GraphicsLayer") })
    }

    @Test
    fun `ColorApplier applyOpacity delegates to the same group`() {
        // The public convenience surface must share the exact semantics.
        val names = elementNames(ColorApplier.applyOpacity(Modifier, 0.25f))
        assertEquals(1, names.size)
        assertTrue(names.any { it.contains("DrawWithContent") })
        assertFalse(names.any { it.contains("GraphicsLayer") })
    }

    // ── wave-43 skeptic S2: the two structural gaps the pins above miss ──
    //
    // The tests above prove WHAT installs (a draw-time group, never a
    // graphicsLayer). They say nothing about WHERE it sits in the chain or
    // what happens when two of them stack — the two ways this fix can be
    // silently undone by an unrelated edit. S2 built both as transient
    // probes; promoted here as permanent pins.

    @Test
    fun `the group folds OUTER of BOTH background-paint branches`() {
        // ORDER pin. ColorApplier.applyColors step 1 installs opacity
        // FIRST so the group wraps the background paint chained after it;
        // invert the order and the bg paints OUTSIDE the group, leaving
        // `opacity: 0.5` with a fully opaque background (the pre-wave-35
        // Edge_ZeroOpacity defect: red box on Android, transparent on
        // iOS/web). foldIn walks outermost → innermost, so "outer" is
        // simply a smaller index.
        //
        // Branch 1 — Modifier.background, the flood-fill route.
        val flood = elementNames(
            ColorApplier.applyColors(
                Modifier, ColorConfig(backgroundColor = Color.Red, opacity = 0.5f)
            )
        )
        val floodGroup = flood.indexOfFirst { it.contains("DrawWithContent") }
        val floodBg = flood.indexOfFirst { it.contains("Background") }
        assertTrue("group missing in $flood", floodGroup >= 0)
        assertTrue("bg fill missing in $flood", floodBg >= 0)
        assertTrue("group must be OUTER of the bg fill: $flood", floodGroup < floodBg)

        // Branch 2 — the drawBehind inset rect taken when background-clip
        // shrinks the paint area (css-backgrounds-3 §2.7). It is a
        // SEPARATE `result = …` assignment in applyColors, so it can drift
        // out of order independently of branch 1; pinned alongside it.
        val inset = elementNames(
            ColorApplier.applyColors(
                Modifier,
                ColorConfig(
                    backgroundColor = Color.Red, opacity = 0.5f,
                    backgroundClipInsets = BackgroundClipInsets(
                        top = 2.dp, right = 2.dp, bottom = 2.dp, left = 2.dp
                    )
                )
            )
        )
        val insetGroup = inset.indexOfFirst { it.contains("DrawWithContent") }
        val insetBg = inset.indexOfFirst { it.contains("DrawBehind") }
        assertTrue("group missing in $inset", insetGroup >= 0)
        assertTrue("inset bg fill missing in $inset", insetBg >= 0)
        assertTrue("group must be OUTER of the inset bg: $inset", insetGroup < insetBg)
    }

    @Test
    fun `stacked opacity stays per-application - two independent groups`() {
        // NESTING pin. css-color-4 §3.3 makes opacity a PER-ELEMENT group:
        // an `opacity: 0.5` child inside an `opacity: 0.5` parent shows
        // through at 0.25 because the inner group flattens first and the
        // outer one composites the result again. Two applications must
        // therefore leave two INDEPENDENT saveLayer groups in the chain —
        // a "merge them / keep the last alpha" optimisation would collapse
        // 0.25 to 0.5 and is exactly what this forbids.
        val names = elementNames(
            OpacityApplier.applyOpacity(OpacityApplier.applyOpacity(Modifier, 0.5f), 0.5f)
        )
        assertEquals(
            "expected exactly two nested groups in $names",
            2, names.count { it.contains("DrawWithContent") }
        )
        // And neither application may reach for a layer node (the whole
        // point of the fix — see the header's HWUI bytecode note).
        assertFalse(
            "no graphicsLayer may install in $names",
            names.any { it.contains("GraphicsLayer") }
        )
    }

    @Test
    fun `group chain builds without touching platform classes`() {
        // JVM discipline pin: building (not drawing) the chain must never
        // construct android.graphics objects — they throw on this suite.
        // A successful fold plus size check proves saveLayerAlpha stays
        // inside the draw lambda (IsolationApplierTest's same pin).
        assertEquals(1, elementNames(OpacityApplier.applyOpacity(Modifier, 0.5f)).size)
    }
}
