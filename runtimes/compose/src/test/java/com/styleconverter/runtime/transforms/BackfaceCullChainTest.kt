package com.styleconverter.runtime.transforms

// Wave 52 (lane L4, native-near-misses T5, skeptic must-fix 1) — the WIRING
// pins for the Compose backface fix. BackfaceCullTest pins the DECISION; this
// class pins what StyleApplier.applyConfig DOES with it, on the real
// extractConfig → applyConfig chain (the StyleApplierTransformPivotSeamTest
// foldIn idiom: JVM JUnit, no Robolectric, no Compose UI test in this module).
// Target, verbatim from the wave51-fix per-test IR the Android capture used:
//   tools/titan/runs/wave51-fix/sections/css-transforms/per-test-ir/
//     wpt__css-transforms__composited-under-rotateY-180deg-preserve-3d.json
//   element `…__0-091`: rotateY(180deg) · width 100 · hidden · preserve-3d.
//
// MUTATIONS EXECUTED (2026-10-05, this lane, StyleApplier.kt only, each
// restored byte-exact and sha256-verified, class green again after each):
//   M4  gate `== HIDE_SUBTREE` → `!= NONE` AND `val paint = config` (the
//       skeptic's mutation: the pre-wave-52 blank canvas) → W1 W2 W3 W4 red.
//   M4a the gate change alone → W1 + W4 red (W2/W3 green: no paint change).
//   M4b `val paint = config` alone → W2 + W3 + W4 red (W1 green).
//   M4c only the self-paint route reads `config.` again → W3 + W4 red.

// Compose's modifier chain type: applyConfig's return, walked by foldIn.
import androidx.compose.ui.Modifier
// The chain under test (extractConfig → applyConfig).
import com.styleconverter.runtime.StyleApplier
// Parses the verbatim wire payloads below.
import kotlinx.serialization.json.Json
// The (type, data) pair element type extractConfig consumes.
import kotlinx.serialization.json.JsonElement
// Reads StyleApplier.kt for the source pin (W4).
import java.io.File
// JUnit4 assertions and the test annotation (this module's test runner).
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackfaceCullChainTest {

    /** IR property pair, as StyleApplier.extractConfig consumes it. */
    private fun p(type: String, json: String): Pair<String, JsonElement?> =
        type to Json.parseToJsonElement(json)

    /** The culled preserve-3d parent, verbatim (`…__0-091`). */
    private val target = listOf(
        p("Transform", """{"type":"functions","list":[{"fn":"rotateY","a":{"deg":180}}]}"""),
        p("Width", """{"type":"length","px":100}"""),
        p("BackfaceVisibility", "\"HIDDEN\""),
        p("TransformStyle", "\"PRESERVE_3D\""),
    )

    /**
     * An own face to cull: red fill, uniform 2px solid band, 2px outline.
     * Synthetic by necessity — the corpus has 0 culled preserve-3d elements
     * with own decoration (census.json `t5.ownDecorationOnCulledPreserve3d`),
     * so the decoration lanes are only observable on an added face. Shapes
     * are the converter's wire (BorderRadiusSelfPaintTest / corpus Outline*).
     */
    private val face = listOf(p("BackgroundColor", """{"srgb":{"r":1,"g":0,"b":0},"original":"red"}""")) +
        listOf("Top", "Right", "Bottom", "Left").flatMap { s ->
            // One uniform-solid side per edge: self-paintable when rounded.
            listOf(
                p("Border${s}Width", """{"px":2}"""),
                p("Border${s}Style", "\"SOLID\""),
                p("Border${s}Color", """{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}"""),
            )
        } + listOf(
            // The outline rides BordersFacade (legacy) or applyOutline (self-paint).
            p("OutlineWidth", """{"type":"length","px":2}"""),
            p("OutlineStyle", "\"SOLID\""),
            p("OutlineColor", """{"srgb":{"r":0,"g":1,"b":0}}"""),
        )

    /** Four 10px corners: steps 5./6. take BorderRadiusApplier.applySelfPaint. */
    private val rounded = listOf("TopLeft", "TopRight", "BottomRight", "BottomLeft")
        .map { p("Border${it}Radius", """{"px":10}""") }

    /** One property dropped by name — every negative control below. */
    private fun without(props: List<Pair<String, JsonElement?>>, type: String) =
        props.filterNot { it.first == type }

    /** The applyConfig chain for one wire, outermost → innermost. */
    private fun chain(props: List<Pair<String, JsonElement?>>): List<Modifier.Element> =
        StyleApplier.applyConfig(Modifier, StyleApplier.extractConfig(props))
            .foldIn(mutableListOf<Modifier.Element>()) { acc, e -> acc.apply { add(e) } }

    /** Element class names: what the decoration steps installed. */
    private fun names(props: List<Pair<String, JsonElement?>>) = chain(props).map { it.javaClass.simpleName }

    /**
     * `Modifier.alpha(0f)` layers. Modifier.alpha IS graphicsLayer(alpha,
     * clip = true) → androidx's GraphicsLayerElement data class (see
     * OpacityApplierTest's header); its private `alpha` field is read
     * reflectively so a transform's own (alpha 1) layer never counts.
     */
    private fun alphaZeroLayers(props: List<Pair<String, JsonElement?>>) = chain(props).count { e ->
        e.javaClass.simpleName == "GraphicsLayerElement" &&
            e.javaClass.getDeclaredField("alpha").apply { isAccessible = true }.getFloat(e) == 0f
    }

    @Test fun `W1 - the verbatim preserve-3d target installs no alpha-0 layer`() {
        // §4.1.2: the green child is its own plane — the subtree must paint.
        assertEquals("CULL_OWN_FACE must not alpha the subtree", 0, alphaZeroLayers(target))
        // FLAT control: the flattened subtree IS the back face — exactly one
        // alpha-0 layer, so the detector above provably sees the step.
        assertEquals("FLAT control must hide the subtree", 1, alphaZeroLayers(without(target, "TransformStyle")))
        // NONE control: `visible` never culls.
        assertEquals("visible control", 0, alphaZeroLayers(without(target, "BackfaceVisibility")))
    }

    @Test fun `W2 - legacy route - culling the own face equals never declaring it`() {
        // Control: the same face on a `visible` element installs a fill.
        val visible = names(without(target, "BackfaceVisibility") + face)
        assertTrue("control must install a background: $visible", visible.any { it.contains("Background") })
        // §10: the culled own face paints nothing — the chain is the
        // undecorated element's chain, element for element.
        assertEquals(names(target), names(target + face))
    }

    @Test fun `W3 - self-paint route - the rounded fill, band and outline are culled too`() {
        // Control: rounded + `visible` takes applySelfPaint (fill + band).
        val visible = names(without(target, "BackfaceVisibility") + face + rounded)
        assertTrue("control must install a background: $visible", visible.any { it.contains("Background") })
        assertTrue("control must install a border: $visible", visible.any { it.contains("Border") })
        // Culled: the rounded element's chain without any face at all.
        assertEquals(names(target + rounded), names(target + face + rounded))
    }

    /** A runtime source file, located by walking up from the test working dir (the S3 idiom). */
    private fun source(rel: String): String {
        // Gradle runs tests from the module dir; walk up to the repo root.
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        return File(requireNotNull(dir) { "repo root ($rel) not found" }, rel).readText()
    }

    /** applyConfig's body, brace-matched, with whole-line comments removed. */
    private fun applyConfigBody(src: String): String {
        // The signature ends at its return type; the body's brace follows.
        val open = src.indexOf("): Modifier {", src.indexOf("fun applyConfig("))
        assertTrue("StyleApplier.applyConfig must exist", open >= 0)
        var depth = 0
        for (i in (open + "): Modifier ".length) until src.length) {
            when (src[i]) { '{' -> depth++; '}' -> if (--depth == 0) {
                // Comments may quote `alpha(0f)` — only code lines count.
                return src.substring(open, i + 1).lines()
                    .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
                    .joinToString("\n")
            } }
        }
        error("unbalanced applyConfig body")
    }

    @Test fun `W4 - source - the only alpha-0 is gated on HIDE_SUBTREE and decoration reads paint`() {
        val body = applyConfigBody(source("runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt"))
        // Exactly one alpha(0f) in the live chain, directly under the gate.
        val gated = Regex("""if \(backface == [\w.]*Decision\.HIDE_SUBTREE\) \{\s*result = result\.alpha\(0f\)""")
        assertEquals("exactly one alpha(0f) in applyConfig", 1, Regex("""\.alpha\(0f\)""").findAll(body).count())
        assertTrue("the alpha(0f) must be gated on Decision.HIDE_SUBTREE", gated.containsMatchIn(body))
        // `paint` is the stripped face ONLY under CULL_OWN_FACE, else config.
        val paintDef = Regex("""val paint =\s*if \(backface == [\w.]*Decision\.CULL_OWN_FACE\)\s*[\w.]*BackfaceCull\.stripOwnFace\(config\)\s*else config""")
        assertTrue("paint must be stripOwnFace(config) under CULL_OWN_FACE", paintDef.containsMatchIn(body))
        // Every decoration call in steps 5./6. paints `paint`, never `config`.
        val call = """(BordersFacade\.apply|ColorApplier\.applyColors|applySelfPaint|applyOutline)\(\s*result,\s*"""
        assertEquals("no decoration call may read config", 0, Regex(call + """config\.""").findAll(body).count())
        assertEquals("all five decoration calls read paint", 5, Regex(call + """paint\.""").findAll(body).count())
    }
}
