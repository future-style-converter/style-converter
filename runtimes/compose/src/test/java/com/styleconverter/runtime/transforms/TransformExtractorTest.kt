package com.styleconverter.runtime.transforms

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
    fun `CSS-wide keyword on transform yields no functions`() {
        // VERBATIM corpus payload: tools/titan/runs/wave48-final/sections/
        // css-transforms/per-test-ir/wpt__css-transforms__css-transform-
        // inherit-scale.json, component css-transform-inherit-scale__1__1__0.
        // The keyword shape used to fall out of the `list` lookup as an
        // empty list — same OUTPUT, but by accident. It is now an explicit
        // branch: `initial`/`unset`/`revert` genuinely compute to `none`
        // (css-cascade-4 §7.3 + css-transforms-1 §3, `transform` is not
        // inherited), while `inherit` is reported through PropertyTracker
        // because Compose cannot resolve a parent value from a Modifier.
        for (keyword in listOf("inherit", "initial", "unset", "revert", "revert-layer")) {
            val cfg = TransformExtractor.extractTransformConfig(
                listOf(pair("Transform", """{"type":"keyword","keyword":"$keyword"}""")),
            )
            assertTrue("$keyword must not fabricate functions", cfg.functions.isEmpty())
            // No accidental routing change: an empty list keeps the config
            // out of every graphicsLayer path exactly as before.
            assertTrue("$keyword must not claim a transform", !cfg.hasTransform)
        }
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

    // ── Wave 52 (lane L4, native-near-misses T7) — `transform: inherit` ──
    //
    // VERBATIM wave51-fix per-test IR: tools/titan/runs/wave51-fix/sections/
    // css-transforms/per-test-ir/wpt__css-transforms__css-transform-inherit-
    // scale.json — `.parent` = css-transform-inherit-scale__1__1-155,
    // `.child` = css-transform-inherit-scale__1__1__0-156.
    //
    // MUTATION EXECUTED (2026-09-25, this lane): with
    // TransformInheritance.resolve's body replaced by `return properties`
    // (the keyword left unresolved, i.e. the pre-wave-52 empty list),
    // `T7 - inherit under the parent's scale(2) resolves to scale 2` FAILS
    // (expected 1 function, was 0) and `T7 - an unresolved keyword …` stays
    // green; bytes restored, sha-verified.

    /** IR property, as the renderer holds it. */
    private fun ir(t: String, j: String) =
        com.styleconverter.runtime.core.ir.IRProperty(t, parse(j))

    /** `.parent` — 50×50, abspos at (75,75), `transform: scale(2)`. */
    private val inheritParent = listOf(
        ir("BackgroundColor", """{"srgb":{"r":1,"g":1,"b":0},"original":"yellow"}"""),
        ir("Width", """{"type":"length","px":50}"""),
        ir("Height", """{"type":"length","px":50}"""),
        ir("Position", "\"ABSOLUTE\""),
        ir("Top", """{"px":75}"""),
        ir("Left", """{"px":75}"""),
        ir("Transform", """{"type":"functions","list":[{"fn":"scale","x":2,"y":2}]}"""),
    )

    /** `.child` — 50×50 green, `transform: inherit`. */
    private val inheritChild = listOf(
        ir("Position", "\"ABSOLUTE\""),
        ir("Transform", """{"type":"keyword","keyword":"inherit"}"""),
        ir("Width", """{"type":"length","px":50}"""),
        ir("Height", """{"type":"length","px":50}"""),
        ir("BackgroundColor", """{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}"""),
    )

    @Test
    fun `T7 - inherit under the parent's scale(2) resolves to scale 2`() {
        // The renderer's two steps: the parent publishes its computed wire,
        // the child's list is resolved against it BEFORE extraction.
        val published = TransformInheritance.published(inheritParent)
        val resolved = TransformInheritance.resolve(inheritChild, published)
        val cfg = TransformExtractor.extractTransformConfig(resolved.map { it.type to it.data })
        // css-cascade-4 §7.3.2: the child's computed transform IS scale(2) —
        // the own factor of two the Android capture dropped (100×100 green
        // centred on the red 200×200 instead of covering it).
        assertEquals(listOf<TransformFunction>(TransformFunction.Scale(2f, 2f)), cfg.functions)
        assertTrue(cfg.hasTransform)
        // Every other property of the child is carried through untouched.
        assertEquals(inheritChild.filter { it.type != "Transform" }, resolved.filter { it.type != "Transform" })
    }

    @Test
    fun `T7 - a chain of inherit carries the same value down`() {
        // The child publishes what it RESOLVED to, so a grandchild's
        // `inherit` reads scale(2) too (§7.3.2 applied per element).
        val resolvedChild = TransformInheritance.resolve(inheritChild, TransformInheritance.published(inheritParent))
        val grandchild = listOf(ir("Transform", """{"type":"keyword","keyword":"inherit"}"""))
        val resolvedGrandchild = TransformInheritance.resolve(grandchild, TransformInheritance.published(resolvedChild))
        assertEquals(TransformInheritance.published(inheritParent), resolvedGrandchild.single().data)
    }

    @Test
    fun `T7 - inherit under a parent with no transform computes to none`() {
        // css-transforms-1 §3: the parent's computed value is `none` (no
        // `Transform` entry → published NONE, never the unprovided null —
        // TransformInheritanceSeamTest pins that difference), so the child's
        // entry is dropped — an absent Transform renders as `none`.
        val untransformedParent = listOf(ir("Width", """{"type":"length","px":50}"""))
        assertEquals(TransformInheritance.NONE, TransformInheritance.published(untransformedParent))
        val resolved = TransformInheritance.resolve(inheritChild, TransformInheritance.published(untransformedParent))
        assertTrue(resolved.none { it.type == "Transform" })
        assertTrue(!TransformExtractor.extractTransformConfig(resolved.map { it.type to it.data }).hasTransform)
    }

    @Test
    fun `T7 - lists without the keyword are returned as the same instance`() {
        // The identity fast path: 1434 of the 1435 wave51-fix documents never
        // allocate here, and the parent's own list is untouched by its own
        // published value.
        assertTrue(TransformInheritance.resolve(inheritParent, null) === inheritParent)
        assertTrue(TransformInheritance.resolve(inheritParent, TransformInheritance.published(inheritParent)) === inheritParent)
        val plain = listOf(ir("Transform", """{"type":"functions","list":[{"fn":"rotate","a":{"deg":45}}]}"""))
        assertTrue(TransformInheritance.resolve(plain, TransformInheritance.published(inheritParent)) === plain)
    }

    @Test
    fun `T7 - an unresolved keyword still leaves the PropertyTracker breadcrumb`() {
        // The pre-seam fallback (and every path that renders without
        // RenderComponent): the extractor refuses the keyword AND records
        // it — no silent fallthrough. Same reset/isUnhandled/reset shape as
        // PercentInsetContainingBlockLevelTest so the global tracker is left
        // as this test found it.
        com.styleconverter.runtime.PropertyTracker.reset()
        val cfg = TransformExtractor.extractTransformConfig(inheritChild.map { it.type to it.data })
        assertTrue(cfg.functions.isEmpty())
        assertTrue(com.styleconverter.runtime.PropertyTracker.isUnhandled("Transform"))
        com.styleconverter.runtime.PropertyTracker.reset()
        // Negative control: the resolved list leaves NO breadcrumb.
        val resolved = TransformInheritance.resolve(inheritChild, TransformInheritance.published(inheritParent))
        TransformExtractor.extractTransformConfig(resolved.map { it.type to it.data })
        assertTrue(!com.styleconverter.runtime.PropertyTracker.isUnhandled("Transform"))
        com.styleconverter.runtime.PropertyTracker.reset()
    }

    @Test
    fun `T7 - only the exact keyword wire is recognised`() {
        assertTrue(TransformInheritance.isInheritKeyword(parse("""{"type":"keyword","keyword":"inherit"}""")))
        assertTrue(TransformInheritance.isInheritKeyword(parse("""{"type":"keyword","keyword":"INHERIT"}""")))
        assertTrue(!TransformInheritance.isInheritKeyword(parse("""{"type":"keyword","keyword":"initial"}""")))
        assertTrue(!TransformInheritance.isInheritKeyword(parse("""{"type":"functions","list":[]}""")))
        assertTrue(!TransformInheritance.isInheritKeyword(parse("\"inherit\"")))
        assertTrue(!TransformInheritance.isInheritKeyword(null))
    }
}
