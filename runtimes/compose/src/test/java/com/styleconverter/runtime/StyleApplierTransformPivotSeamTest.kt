package com.styleconverter.runtime

// retro F3 (skeptic S2, must-fix) — the A11#0 abspos-pivot WIRING, seen from
// StyleApplier. R1's TransformPivotTest pins the arithmetic helper only:
// S2's mutation M2 (StyleApplier passing `pivotShift = DpOffset.Zero`, i.e.
// the seam severed with the helper intact) left 330 JVM tests green. This
// class drives the REAL chain — StyleApplier.extractConfig → applyConfig on
// the verbatim IR of the pin fixture's positioned movers — then folds the
// resulting Modifier, finds the transform step's graphicsLayer node and RUNS
// its block against a recording GraphicsLayerScope sized like the 40x40
// node, so the assertion is on the pivot the layer would actually use:
// transform-origin (css-transforms-1 §4, default 50% 50% of the OWN border
// box) plus the SAME (left, top) the step-4 absoluteOffset node carries
// (PositionApplier.resolvedOffset — the value form of that node). With M2
// re-applied, the recorded pivot is (20,20) and every test below fails.

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Dp
import com.styleconverter.runtime.layout.position.PositionApplier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleApplierTransformPivotSeamTest {

    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)

    // VERBATIM: `:converter:run --to ir` of fixtures/combinations/
    // transform-abspos-pivot.json (retro/skeptics/S2/ir-tap/tmpOutput.json),
    // the `mover` child of each pinned row — identical wires except for the
    // Transform list, which is the row's one variable.
    private fun moverPairs(transform: String): List<Pair<String, JsonElement?>> = listOf(
        "Position" to j("\"ABSOLUTE\""),
        "Left" to j("""{"px":60.0}"""),
        "Top" to j("""{"px":20.0}"""),
        "Width" to j("""{"type":"length","px":40.0}"""),
        "Height" to j("""{"type":"length","px":40.0}"""),
        "BackgroundColor" to j("""{"srgb":{"r":0.9058823529411765,"g":0.2980392156862745,"b":0.23529411764705882},"original":"#e74c3c"}"""),
        "Transform" to j(transform),
    )

    private val rotate45 = """{"type":"functions","list":[{"fn":"rotate","a":{"deg":45.0}}]}"""      // TAP_Rotate45_Anchored
    private val scaleX2 = """{"type":"functions","list":[{"fn":"scaleX","x":2.0}]}"""                 // TAP_ScaleX2_Anchored
    private val rotateY60 = """{"type":"functions","list":[{"fn":"rotateY","a":{"deg":60.0}}]}"""    // TAP_RotateY60_Anchored

    // The node the fixture's movers occupy: 40x40 at density 1 (the seam
    // is density-free — dp·1 = px — so the numbers read straight off the CSS).
    private val w = 40f
    private val h = 40f

    /** Every Modifier.Element of the built chain, in fold order (outer → inner). */
    private fun elementsOf(m: Modifier): List<Modifier.Element> =
        m.foldIn(mutableListOf<Modifier.Element>()) { acc, e -> acc.apply { add(e) } }

    /** The full StyleApplier chain for one mover wire — the seam under test lives inside applyConfig. */
    private fun chainFor(transform: String): Modifier =
        StyleApplier.applyConfig(Modifier, StyleApplier.extractConfig(moverPairs(transform)))

    /**
     * A recording GraphicsLayerScope. The transform routes write their
     * fields into whatever scope Compose hands them; here that is this
     * object, so the pivot / scale / rotation they computed can be read
     * back. `size` and `density` are what the 40x40 node reports at 1x —
     * the two inputs TransformPivot.axisPx resolves the fraction against.
     */
    private class RecordingScope(w: Float, h: Float) : GraphicsLayerScope {
        override var scaleX = 1f
        override var scaleY = 1f
        override var alpha = 1f
        override var translationX = 0f
        override var translationY = 0f
        override var shadowElevation = 0f
        override var rotationX = 0f
        override var rotationY = 0f
        override var rotationZ = 0f
        override var cameraDistance = 8f
        override var transformOrigin: TransformOrigin = TransformOrigin.Center
        override var shape: Shape = RectangleShape
        override var clip = false
        override val density = 1f
        override val fontScale = 1f
        override val size: Size = Size(w, h)
    }

    /**
     * Run the chain's single graphicsLayer block against a recording scope.
     * The element class is androidx's internal BlockGraphicsLayerElement
     * (`Modifier.graphicsLayer { }`), so its `block` is read reflectively —
     * the device-free window into what the layer would compute.
     */
    private fun recordLayer(chain: Modifier): RecordingScope {
        val layers = elementsOf(chain).filter { it.javaClass.simpleName == "BlockGraphicsLayerElement" }
        assertEquals("exactly one graphicsLayer node expected for a rotate/scale mover", 1, layers.size)
        val getter = layers[0].javaClass.getDeclaredMethod("getBlock").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val block = getter.invoke(layers[0]) as GraphicsLayerScope.() -> Unit
        return RecordingScope(w, h).apply { block() }
    }

    /** The pivot the layer set, back in px of the node frame. */
    private fun pivotPxOf(scope: RecordingScope): Pair<Float, Float> =
        scope.transformOrigin.pivotFractionX * w to scope.transformOrigin.pivotFractionY * h

    /** A private backing field of a library element (Dp fields may surface as Dp or raw Float). */
    private fun dpFieldOf(element: Any, name: String): Float {
        // The field is private in foundation-layout — reflection is the
        // device-free window into which offset the node will apply.
        val v: Any? = element.javaClass.declaredFields.first { it.name == name }.apply { isAccessible = true }.get(element)
        return when (v) {
            is Dp -> v.value                                // boxed inline class
            is Float -> v                                   // unboxed raw dp
            else -> error("unexpected $name field shape: $v")   // null included — refuse, never guess
        }
    }

    @Test
    fun `the transform node is OUTER of the abspos offset node — the premise of the conjugation`() {
        // applyConfig chains step 2 (transforms) before step 4 (layout, whose
        // last modifier is PositionApplier's absoluteOffset). Compose's
        // offset node reports the CHILD's size and places the child (left,
        // top) inside it, which is exactly why the outer transform node's
        // size-based origin misses (left, top) and must be conjugated.
        val els = elementsOf(chainFor(rotate45)).map { it.javaClass.simpleName }
        val layer = els.indexOf("BlockGraphicsLayerElement")
        val offset = els.indexOf("OffsetElement")
        assertTrue("graphicsLayer node missing from $els", layer >= 0)
        assertTrue("absoluteOffset node missing from $els", offset >= 0)
        assertTrue("transform (index $layer) must be outer of the offset (index $offset)", layer < offset)
    }

    @Test
    fun `rotate(45deg) mover pivots at origin plus the chained offset — (80,40), not (20,20)`() {
        val config = StyleApplier.extractConfig(moverPairs(rotate45))
        val chain = StyleApplier.applyConfig(Modifier, config)
        // The offset the step-4 node will apply INSIDE the transform node:
        // read off the chained OffsetElement itself, and equal to the value
        // form the seam passes (resolvedOffset) — the two cannot disagree.
        val offsetNode = elementsOf(chain).single { it.javaClass.simpleName == "OffsetElement" }
        val resolved = PositionApplier.resolvedOffset(config.layout.position)
        assertEquals(60f, dpFieldOf(offsetNode, "x"), 0f)
        assertEquals(20f, dpFieldOf(offsetNode, "y"), 0f)
        assertEquals(60f, resolved.x.value, 0f)
        assertEquals(20f, resolved.y.value, 0f)
        // The pivot the layer set: css-transforms-1 §4 default origin 50% 50%
        // of the OWN 40x40 box = (20,20), plus the (60,20) the offset node
        // moves the content by = (80,40) — the fixture's `_expect` centre.
        // Severed (S2's M2: pivotShift = DpOffset.Zero) this reads (20,20).
        val scope = recordLayer(chain)
        val (px, py) = pivotPxOf(scope)
        assertEquals(w * 0.5f + resolved.x.value, px, 1e-4f)
        assertEquals(h * 0.5f + resolved.y.value, py, 1e-4f)
        assertEquals(80f, px, 1e-4f)
        assertEquals(40f, py, 1e-4f)
        // …and the rotation itself rides the same layer, so the fixture's
        // 56.57 diamond is centred on the anchor (TAP_Rotate45_Anchored [57,57]).
        assertEquals(45f, scope.rotationZ, 1e-4f)
    }

    @Test
    fun `scaleX(2) and rotateY(60deg) movers pivot at x = 80 and carry the fixture's predicted scales`() {
        // TAP_ScaleX2_Anchored: pivot x = 60 + 20 = 80 → the mover spans
        // 80 ± 2·20 = 40..120, centred on the anchor → union [80,40]. With
        // the parent-frame pivot 20 it would span 100..180 → [120,40].
        val sx = recordLayer(chainFor(scaleX2))
        assertEquals(80f, pivotPxOf(sx).first, 1e-4f)
        assertEquals(40f, pivotPxOf(sx).second, 1e-4f)
        assertEquals(2f, sx.scaleX, 1e-4f)
        assertEquals(1f, sx.scaleY, 1e-4f)
        // TAP_RotateY60_Anchored: no perspective in effect → css-transforms-2
        // §4.1 flattens rotateY(60°) to scaleX(cos 60° = 0.5) about x = 80,
        // so the mover hides inside the anchor at 70..90 → union [40,40];
        // the parent-frame pivot 20 gives 40..60 → [60,40] — the left·(1 −
        // cos) mechanism that sat css-transform-3d-rotateY-positive 10px
        // left of the frozen ref on Android.
        val ry = recordLayer(chainFor(rotateY60))
        assertEquals(80f, pivotPxOf(ry).first, 1e-4f)
        assertEquals(40f, pivotPxOf(ry).second, 1e-4f)
        assertEquals(0.5f, ry.scaleX, 1e-4f)
        assertEquals(0f, ry.rotationY, 0f)                 // orthographic: the cos is a scale, never a camera rotation
        assertEquals(80f + 0.5f * (60f - 80f), 70f, 0f)    // left edge 60 → 70, shown as arithmetic
    }
}
