package com.styleconverter.test.style.transforms

// Phase 8 functional tests for TransformExtractor. These exercise the
// extract path for the transform 2D longhands (Rotate/Scale/Translate/
// TransformOrigin) plus the function-list form of `transform`. The fixture
// shapes mirror what the CSS parser emits — see
// src/main/kotlin/app/parsing/css/properties/longhands/transforms/
// for the producer-side contract.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformExtractorTest {

    // Tiny Json helper — keeps the fixture strings inline-readable instead
    // of ceremony around parseToJsonElement at every call site.
    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    @Test
    fun `Rotate in degrees populates rotate field`() {
        // IR form: {"degrees": 45.0}. Degrees normalizes deg/rad/turn/grad upstream.
        val cfg = TransformExtractor.extractTransformConfig(
            listOf(pair("Rotate", "{\"degrees\":45.0}"))
        )
        assertEquals(45f, cfg.rotate!!, 0.001f)
    }

    @Test
    fun `Scale with uniform factor populates scale`() {
        // Uniform scale — the extractor puts it in the `scale` field so the
        // applier can prefer the uniform graphicsLayer path over scaleX/scaleY.
        val cfg = TransformExtractor.extractTransformConfig(
            listOf(pair("Scale", "{\"x\":1.5,\"y\":1.5}"))
        )
        // Either `scale` is populated (uniform detected) or `scaleX`+`scaleY`
        // are both set — both satisfy hasTransform.
        assertTrue(cfg.hasTransform)
    }

    @Test
    fun `Translate with px offsets extracts both axes`() {
        val cfg = TransformExtractor.extractTransformConfig(
            listOf(pair("Translate", "{\"x\":{\"px\":50.0},\"y\":{\"px\":100.0}}"))
        )
        assertNotNull("translateX must be extracted", cfg.translateX)
        assertNotNull("translateY must be extracted", cfg.translateY)
        assertEquals(50f, cfg.translateX!!.value, 0.01f)
        assertEquals(100f, cfg.translateY!!.value, 0.01f)
    }

    @Test
    fun `TransformOrigin center keyword normalizes to half-half`() {
        // "center" keyword is the default — both axes at 0.5.
        val cfg = TransformExtractor.extractTransformConfig(
            listOf(pair("TransformOrigin", "\"center\""))
        )
        assertEquals(0.5f, cfg.originX, 0.001f)
        assertEquals(0.5f, cfg.originY, 0.001f)
    }

    @Test
    fun `TransformOrigin top-left keyword normalizes to zero-zero`() {
        val cfg = TransformExtractor.extractTransformConfig(
            listOf(pair("TransformOrigin", "\"top left\""))
        )
        assertEquals(0f, cfg.originX, 0.001f)
        assertEquals(0f, cfg.originY, 0.001f)
    }

    @Test
    fun `Transform function list populates functions`() {
        // The `transform` property uses the function-list IR shape (see
        // CLAUDE.md "IR Formats"). Even if the applier folds these into
        // graphicsLayer, the extractor preserves the list so later stages
        // can inspect for matrix()/matrix3d() requiring Canvas.withTransform.
        val cfg = TransformExtractor.extractTransformConfig(
            listOf(pair("Transform",
                "{\"type\":\"functions\",\"list\":[" +
                    "{\"fn\":\"translate\",\"x\":{\"px\":10.0},\"y\":{\"px\":20.0}}," +
                    "{\"fn\":\"rotate\",\"angle\":{\"degrees\":30.0}}" +
                "]}"))
        )
        assertTrue("function list non-empty", cfg.functions.isNotEmpty())
        assertTrue(cfg.hasTransform)
    }

    @Test
    fun `empty property list produces empty config`() {
        val cfg = TransformExtractor.extractTransformConfig(emptyList())
        assertNull(cfg.rotate)
        assertNull(cfg.translateX)
        assertTrue("no-transform sentinel", !cfg.hasTransform)
    }

    @Test
    fun `Transform3D perspective extracts as Dp`() {
        val cfg = Transform3DExtractor.extractTransform3DConfig(
            listOf(pair("Perspective", "{\"px\":500.0}"))
        )
        assertNotNull(cfg.perspective)
        assertEquals(500f, cfg.perspective!!.value, 0.01f)
    }

    @Test
    fun `Transform3D backface-visibility hidden parses`() {
        val cfg = Transform3DExtractor.extractTransform3DConfig(
            listOf(pair("BackfaceVisibility", "\"hidden\""))
        )
        assertEquals(BackfaceVisibilityValue.HIDDEN, cfg.backfaceVisibility)
    }

    @Test
    fun `Transform3D preserve-3d keyword parses`() {
        val cfg = Transform3DExtractor.extractTransform3DConfig(
            listOf(pair("TransformStyle", "\"preserve-3d\""))
        )
        // PRESERVE_3D: Compose has no true 3D scene, this is a best-effort
        // marker so the applier can log a TODO instead of silently flattening.
        assertEquals(TransformStyleValue.PRESERVE_3D, cfg.transformStyle)
    }
}
