package com.styleconverter.runtime.transforms

// retro R1 (finding A11#0) — the abspos pivot. The transform node is OUTER
// of the position offset, so its pivot must be the child's centre in the
// node's frame: local centre + (left, top). Wires are VERBATIM from the
// audit's IR (retro/A11/runs/combinations__nested-transforms/ir.json) and
// from tools/titan/runs/wave49-final/sections/css-transforms/per-test-ir/.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TransformPivotTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun config(vararg props: Pair<String, String>): TransformConfig =
        TransformExtractor.extractTransformConfig(props.map { (t, j) -> t to parse(j) })

    // VERBATIM: nested-transforms `box` child — position absolute, left 60,
    // top 20, 40x40, transform rotate(45deg). Same Transform wire on all four
    // NT_* rows; only the parent's transform differs.
    private val rotate45 = """{"type":"functions","list":[{"fn":"rotate","a":{"deg":45}}]}"""

    @Test
    fun `axisPx adds the abspos shift to the resolved origin`() {
        // Fractional origin: 40 · 0.5 + 60 = 80 (x), 40 · 0.5 + 20 = 40 (y).
        assertEquals(80f, TransformPivot.axisPx(null, 1f, 0.5f, 40f, 60f), 0f)
        assertEquals(40f, TransformPivot.axisPx(null, 1f, 0.5f, 40f, 20f), 0f)
        // Length origin wins over the fraction (css-transforms-1 §4 lengths
        // reference the border box's top-left): 10dp · 2 density + 60 = 80.
        assertEquals(80f, TransformPivot.axisPx(10f, 2f, 0.5f, 40f, 60f), 0f)
        // No shift (unpositioned element) → the wave-49 pivot, unchanged.
        assertEquals(20f, TransformPivot.axisPx(null, 1f, 0.5f, 40f, 0f), 0f)
    }

    @Test
    fun `nested-transforms control centres at (80,40) in the parent frame`() {
        // Child 40x40 at (60,20): its centre in the node frame is (80,40).
        // With the conjugated pivot (80,40) the centre is a fixed point and
        // the top-left corner (60,20) — rel (−20,−20) — rotates 45° cw to
        // (−20·cos45 + 20·sin45, −20·sin45 − 20·cos45) = (0, −28.28):
        // the diamond's top tip at (80, 11.72), symmetric about x = 80.
        val c = config("Transform" to rotate45)
        val fixed = TransformMatrixComposer.ctm(c, 1f, 40f, 40f, pivotXPx = 80f, pivotYPx = 40f)
        val (cx, cy) = fixed.project(80f, 40f)
        assertEquals(80f, cx, 1e-3f)
        assertEquals(40f, cy, 1e-3f)
        val (tx, ty) = fixed.project(60f, 20f)
        assertEquals(80f, tx, 1e-3f)
        assertEquals(40f - 28.284f, ty, 1e-3f)
    }

    @Test
    fun `the wave-49 pivot sent the same centre to (48_3, 76_6) — the measured miss`() {
        // Legacy pivot = the child's local centre (20,20), expressed in the
        // PARENT frame — missing (left, top). The centre (80,40), rel (60,20),
        // rotates 45° cw to (60·cos45 − 20·sin45, 60·sin45 + 20·cos45) =
        // (28.28, 56.57) → (48.28, 76.57): the finding's predicted centre
        // (48.3, 76.6), whose diamond runs to y = 104.9 and was cut by the
        // capture canvas — the "vertical clip" of the wave-1 waivers.
        val c = config("Transform" to rotate45)
        val legacy = TransformMatrixComposer.ctm(c, 1f, 40f, 40f, pivotXPx = 20f, pivotYPx = 20f)
        val (cx, cy) = legacy.project(80f, 40f)
        assertEquals(48.284f, cx, 1e-3f)
        assertEquals(76.569f, cy, 1e-3f)
    }

    @Test
    fun `css-transform-3d-rotateY-positive sits 10px left under the wave-49 pivot`() {
        // VERBATIM: per-test-ir/wpt__css-transforms__css-transform-3d-rotateY-
        // positive.json, component …__2: position absolute, left 20, top 80,
        // 240x120, transform rotateY(60deg). Orthographic (§4.1): scaleX(cos 60°
        // = 0.5) about the pivot. Correct pivot x = 120 + 20 = 140 keeps the
        // box centre at 140 → x ∈ [80, 200]; the legacy pivot 120 gives
        // [70, 190] — exactly the left·(1 − cos 60°) = 10 px shift measured
        // on Android (x 86..205) against the frozen ref (96..215).
        val c = config("Transform" to """{"type":"functions","list":[{"fn":"rotateY","a":{"deg":60}}]}""")
        assertEquals(0.5f, OrthographicFlatten.of(0f, 60f).scaleX, 1e-6f)
        val pivotX = TransformPivot.axisPx(null, 1f, 0.5f, 240f, 20f)
        val pivotY = TransformPivot.axisPx(null, 1f, 0.5f, 120f, 80f)
        assertEquals(140f, pivotX, 0f)
        assertEquals(140f, pivotY, 0f)
        val fixed = TransformMatrixComposer.ctm(c, 1f, 240f, 120f, pivotX, pivotY)
        assertEquals(80f, fixed.project(20f, 80f).first, 1e-3f)       // left edge 20 → 80
        assertEquals(200f, fixed.project(260f, 200f).first, 1e-3f)    // right edge 260 → 200
        assertEquals(80f, fixed.project(20f, 80f).second, 1e-3f)      // y untouched by scaleX
        val legacy = TransformMatrixComposer.ctm(c, 1f, 240f, 120f, 120f, 60f)
        assertEquals(70f, legacy.project(20f, 80f).first, 1e-3f)      // 10 px left of the ref
    }
}
