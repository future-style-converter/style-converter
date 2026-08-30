package com.styleconverter.runtime.transforms

// Wave 48 (lane W6) — the ordered transform-list composer. Every fixture
// string below is a VERBATIM converter/corpus payload (source named per
// test): fixtures were converted with `:converter:run --to ir` and the IR
// `Transform` property pasted unedited; the corpus payload comes from
// tools/titan/runs/wave48-cal per-test-ir. The expected numbers are shown
// as arithmetic in the comments, mirroring the _expect discipline in
// fixtures/combinations/transform-list-order.json.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransformListComposerTest {

    // Parse helper — identical idiom to TransformExtractorTest.
    private fun parse(s: String) = Json.parseToJsonElement(s)

    // Build a TransformConfig from (type, json) property pairs, going
    // through the REAL extractor so the payload shapes stay honest.
    private fun config(vararg props: Pair<String, String>): TransformConfig =
        TransformExtractor.extractTransformConfig(props.map { (t, j) -> t to parse(j) })

    // Map a point through an affine — the same math the canvas/graphicsLayer
    // routes ultimately hand to Skia, used here to assert geometry.
    private fun map(m: TransformListComposer.Affine2D, x: Float, y: Float): Pair<Float, Float> =
        Pair(m.a * x + m.c * y + m.tx, m.b * x + m.d * y + m.ty)

    // ------------------------------------------------------------------
    // Transform_Combined — fixtures/visual-test.json, the ledgered
    // iOS-Android divergence this lane closes.
    // ------------------------------------------------------------------

    // Verbatim IR (converter output for `rotate(5deg) scale(1.1) translateX(10px)`).
    private val transformCombined =
        """{"type":"functions","list":[{"fn":"rotate","a":{"deg":5.0}},{"fn":"scale","x":1.1,"y":1.1},{"fn":"translateX","x":{"px":10.0}}]}"""

    @Test
    fun `Transform_Combined is eligible and decomposes to rotate-scale`() {
        val cfg = config("Transform" to transformCombined)
        val steps = TransformListComposer.stepsFor(cfg)
        assertNotNull("three-function 2D list must take the ordered path", steps)
        val fields = TransformListComposer.decomposeRotateScale(TransformListComposer.linearOf(steps!!))
        // Uniform scale commutes with rotation, so the linear part is
        // exactly R(5°)·S(1.1) — expressible in graphicsLayer fields.
        assertNotNull(fields)
        assertEquals(5f, fields!!.rotationDeg, 1e-3f)
        assertEquals(1.1f, fields.scaleX, 1e-4f)
        assertEquals(1.1f, fields.scaleY, 1e-4f)
    }

    @Test
    fun `Transform_Combined translate is scaled then rotated - the ledgered fix`() {
        val cfg = config("Transform" to transformCombined)
        val steps = TransformListComposer.stepsFor(cfg)!!
        // css-transforms-1 §11: the translateX(10px) is mapped by scale(1.1)
        // then rotate(5deg): 10·1.1 = 11; (11·cos5°, 11·sin5°) =
        // (10.9581, 0.9587). The legacy accumulation rendered (10, 0) —
        // predicting a (0.96, 0.96)px NW displacement; the measurement
        // (S6-audited definition, fix lane F5: centroid of the ±32/channel
        // fill-band pixels on the COMMITTED baselines) reads ~1.2px per
        // axis on Android__041_Transform_Combined.png (centroid 49.6,29.6
        // vs web 50.8,30.8) — same direction, magnitude within the
        // band-thresholded centroid's own quantization of the 0.96px
        // prediction, not "exactly" it.
        val m = TransformListComposer.affineOf(steps, density = 1f, widthPx = 100f, heightPx = 50f)
        assertEquals(10.9581f, m.tx, 1e-3f)
        assertEquals(0.9587f, m.ty, 1e-3f)
    }

    // ------------------------------------------------------------------
    // TLO_ScaleXRotate — fixtures/combinations/transform-list-order.json,
    // the waivered red case: shear residue, canvas route.
    // ------------------------------------------------------------------

    // Verbatim IR for `scaleX(2) rotate(45deg)` on a 40x40 box.
    private val tloScaleXRotate =
        """{"type":"functions","list":[{"fn":"scaleX","x":2.0},{"fn":"rotate","a":{"deg":45.0}}]}"""

    @Test
    fun `TLO_ScaleXRotate has shear residue - graphicsLayer must refuse`() {
        val cfg = config("Transform" to tloScaleXRotate)
        val steps = TransformListComposer.stepsFor(cfg)
        assertNotNull(steps)
        // S(2,1)·R(45°) has columns (1.414, 0.707) and (-1.414, 0.707) —
        // dot product -1.5, not orthogonal, so NO R·S decomposition exists.
        // The decompose must return null (canvas route), never an
        // approximation: feeding graphicsLayer here is exactly the old bug.
        assertNull(TransformListComposer.decomposeRotateScale(TransformListComposer.linearOf(steps!!)))
    }

    @Test
    fun `TLO_ScaleXRotate composed matrix renders the 113x57 bbox from _expect`() {
        val cfg = config("Transform" to tloScaleXRotate)
        val steps = TransformListComposer.stepsFor(cfg)!!
        // 40x40 box, default center origin (20,20). Fixture arithmetic:
        // rotate maps points first (56.57 diamond), then scaleX(2) doubles
        // width only -> 113.14 x 56.57 (box [113,57] in the fixture).
        val f = TransformListComposer.conjugateByOrigin(
            TransformListComposer.affineOf(steps, 1f, 40f, 40f), 20f, 20f,
        )
        val corners = listOf(map(f, 0f, 0f), map(f, 40f, 0f), map(f, 0f, 40f), map(f, 40f, 40f))
        val w = corners.maxOf { it.first } - corners.minOf { it.first }
        val h = corners.maxOf { it.second } - corners.minOf { it.second }
        assertEquals(113.137f, w, 1e-2f)
        assertEquals(56.569f, h, 1e-2f)
    }

    // ------------------------------------------------------------------
    // The four rigid/uniform TLO cases — order shows in the centroid.
    // Verbatim IR from the same fixture conversion.
    // ------------------------------------------------------------------

    private val tloRotateTranslate =
        """{"type":"functions","list":[{"fn":"rotate","a":{"deg":45.0}},{"fn":"translate","x":{"px":60.0},"y":{"px":0.0}}]}"""
    private val tloTranslateRotate =
        """{"type":"functions","list":[{"fn":"translate","x":{"px":60.0},"y":{"px":0.0}},{"fn":"rotate","a":{"deg":45.0}}]}"""
    private val tloScaleTranslate =
        """{"type":"functions","list":[{"fn":"scale","x":2.0,"y":2.0},{"fn":"translate","x":{"px":30.0},"y":{"px":0.0}}]}"""
    private val tloTranslateScale =
        """{"type":"functions","list":[{"fn":"translate","x":{"px":30.0},"y":{"px":0.0}},{"fn":"scale","x":2.0,"y":2.0}]}"""

    // Center of the 40x40 fixture box after conjugation about itself —
    // the centroid the ledger measured on real captures.
    private fun centerAfter(payload: String): Pair<Float, Float> {
        val steps = TransformListComposer.stepsFor(config("Transform" to payload))!!
        val f = TransformListComposer.conjugateByOrigin(
            TransformListComposer.affineOf(steps, 1f, 40f, 40f), 20f, 20f,
        )
        return map(f, 20f, 20f)
    }

    @Test
    fun `rotate then translate carries the offset into the rotation - centroid +42_43`() {
        // rotate(45) translate(60,0): translate maps the point first, the
        // rotation carries it to (60·cos45, 60·sin45) = (+42.43, +42.43).
        // Ledger-measured web (137,138) vs Android's wrong (155,96) — this
        // pins the web/spec answer.
        val (x, y) = centerAfter(tloRotateTranslate)
        assertEquals(20f + 42.4264f, x, 1e-2f)
        assertEquals(20f + 42.4264f, y, 1e-2f)
    }

    @Test
    fun `translate then rotate leaves the offset unrotated - centroid +60_0`() {
        // translate(60,0) rotate(45): rotate maps the point first (about
        // the box, no centroid shift), then +60 in x. Same answer the
        // legacy accumulation produced — this case is the pixel-stability
        // pin for the ordered path (baseline must not move).
        val (x, y) = centerAfter(tloTranslateRotate)
        assertEquals(80f, x, 1e-2f)
        assertEquals(20f, y, 1e-2f)
    }

    @Test
    fun `scale then translate doubles the offset - centroid +60_0`() {
        // scale(2) translate(30,0): translate first, scale doubles it.
        // Ledger-measured web centroid +60 (156,96) vs Android's +30
        // (126,96) — the order-insensitivity signature this lane fixes.
        val (x, y) = centerAfter(tloScaleTranslate)
        assertEquals(80f, x, 1e-2f)
        assertEquals(20f, y, 1e-2f)
    }

    @Test
    fun `translate then scale keeps the offset - centroid +30_0`() {
        // translate(30,0) scale(2): scale maps the point first (about
        // center via conjugation), then +30. Matches legacy — stability pin.
        val (x, y) = centerAfter(tloTranslateScale)
        assertEquals(50f, x, 1e-2f)
        assertEquals(20f, y, 1e-2f)
    }

    @Test
    fun `TLO_RotateScaleX decomposes exactly - stays on graphicsLayer`() {
        // Verbatim IR for `rotate(45deg) scaleX(2)`: R(45)·S(2,1) IS the
        // R·S shape — decomposition must return exactly those values, so
        // the render equals the legacy path's (this case passed before,
        // degenerately; it must keep passing non-degenerately).
        val cfg = config(
            "Transform" to """{"type":"functions","list":[{"fn":"rotate","a":{"deg":45.0}},{"fn":"scaleX","x":2.0}]}""",
        )
        val fields = TransformListComposer.decomposeRotateScale(
            TransformListComposer.linearOf(TransformListComposer.stepsFor(cfg)!!),
        )
        assertNotNull(fields)
        assertEquals(45f, fields!!.rotationDeg, 1e-3f)
        assertEquals(2f, fields.scaleX, 1e-4f)
        assertEquals(1f, fields.scaleY, 1e-4f)
    }

    // ------------------------------------------------------------------
    // Eligibility guards — each names the route that keeps ownership.
    // ------------------------------------------------------------------

    @Test
    fun `single-function list stays on the legacy path`() {
        // Verbatim Transform_Rotate (visual-test.json): one function is
        // order-trivial; legacy graphicsLayer renders it exactly, and
        // keeping it there leaves 229 single-function fixture components
        // (plus 130 corpus components) byte-stable.
        val cfg = config(
            "Transform" to """{"type":"functions","list":[{"fn":"rotate","a":{"deg":15.0}}]}""",
        )
        assertNull(TransformListComposer.stepsFor(cfg))
    }

    @Test
    fun `skew-bearing list stays on the canvas skew path`() {
        // Verbatim Edge_MultiTransform (visual-test.json): the trailing
        // skewX(5deg) routes to applyTransformsWithSkew before the ordered
        // path is consulted; stepsFor must agree (ordering that path is a
        // named deferred item, not silently absorbed).
        val cfg = config(
            "Transform" to """{"type":"functions","list":[{"fn":"rotate","a":{"deg":10.0}},{"fn":"scale","x":0.9,"y":0.9},{"fn":"translateX","x":{"px":5.0}},{"fn":"skewX","x":{"deg":5.0}}]}""",
        )
        assertNull(TransformListComposer.stepsFor(cfg))
    }

    @Test
    fun `3D-bearing list stays on the legacy path`() {
        // Verbatim corpus payload (wave48-cal css-transforms
        // css-rotate-2d-3d-001 component __2): rotate3d(1,0,0,60deg)
        // extracts to RotateX -> has3DTransform -> ineligible. This is the
        // ONLY multi-function list in the whole 30-section corpus, and it
        // must not change paths.
        val cfg = config(
            "Transform" to """{"type":"functions","list":[{"fn":"rotate","a":{"deg":90}},{"fn":"rotate3d","x":1,"y":0,"z":0,"a":{"deg":60}}]}""",
        )
        assertNull(TransformListComposer.stepsFor(cfg))
    }

    // ------------------------------------------------------------------
    // Standalone properties mixing into the list (css-transforms-2 §5).
    // ------------------------------------------------------------------

    @Test
    fun `Transforms_C02_Rotate standalone scale composes before the list`() {
        // Verbatim IR (fixtures/fidelity/transforms.combos.json): `rotate:
        // none` (extracts to null), `scale: 150%` (uniform 1.5), `transform:
        // rotate(15deg)`. §5 order: scale property, THEN the list — uniform
        // scale commutes with rotation so the result equals the legacy
        // accumulation (θ=15°, s=1.5): an identical-matrix stability pin.
        val cfg = config(
            "Rotate" to """{"type":"none"}""",
            "Scale" to """{"type":"uniform","value":1.5}""",
            "Transform" to """{"type":"functions","list":[{"fn":"rotate","a":{"deg":15.0}}]}""",
        )
        val steps = TransformListComposer.stepsFor(cfg)
        assertNotNull("props + list mix is order-relevant and must compose", steps)
        val fields = TransformListComposer.decomposeRotateScale(TransformListComposer.linearOf(steps!!))
        assertNotNull(fields)
        assertEquals(15f, fields!!.rotationDeg, 1e-3f)
        assertEquals(1.5f, fields.scaleX, 1e-4f)
        assertEquals(1.5f, fields.scaleY, 1e-4f)
    }

    // ------------------------------------------------------------------
    // Decomposition edge: reflections must be exact-or-refuse.
    // ------------------------------------------------------------------

    @Test
    fun `reflection decomposes exactly with signed scaleY`() {
        // Synthetic augmentation (no corpus payload carries a negative
        // scale in a multi-function list): scaleX(-1) rotate(10deg) —
        // L = S(-1,1)·R(10) has orthogonal columns, and the exact
        // decomposition is R(170°)·S(1,-1): the sign lives in scaleY.
        // TransformApplier.decomposeMatrix2D gets this wrong (negates
        // scaleX without flipping the basis), which is why the ordered
        // path uses THIS decomposition.
        val cfg = config(
            "Transform" to """{"type":"functions","list":[{"fn":"scaleX","x":-1.0},{"fn":"rotate","a":{"deg":10.0}}]}""",
        )
        val fields = TransformListComposer.decomposeRotateScale(
            TransformListComposer.linearOf(TransformListComposer.stepsFor(cfg)!!),
        )
        assertNotNull(fields)
        assertEquals(170f, fields!!.rotationDeg, 1e-3f)
        assertEquals(1f, fields.scaleX, 1e-4f)
        assertEquals(-1f, fields.scaleY, 1e-4f)
    }
}
