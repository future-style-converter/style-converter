package com.styleconverter.test.screenshot

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.background.RootBackgroundPropagation
import com.styleconverter.runtime.background.RootBackgroundPropagation.Attachment
import com.styleconverter.runtime.background.RootBackgroundPropagation.LayerOrigin
import com.styleconverter.runtime.color.BackgroundImageConfig
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * wave-53 lane L3 (item A) — the composed canvas paints the ROOT's
 * background-IMAGE layers (css-backgrounds-3 §2.11.2), Compose twin of the web
 * harness ComposedCanvasRootBackgroundImage.test.tsx and the SwiftUI
 * WPTCaptureModeTests root-background pins.
 *
 * THE DEFECT: resolveComposedCanvasBackground read only `BackgroundColor`, and
 * the body-root is a SIBLING of the flow content, so nobody painted a root
 * image across the canvas (wave52-ship display-contents-root-background
 * android f 0.5355, background-attachment-margin-root-001/-002 f 0.451 / 0.3389).
 *
 * Pinned here on the VERBATIM wave52-ship per-test IR (decoded + slot-composed
 * exactly as the harness does): the plan (layers, §3.4 origins, F2
 * uniformity), the per-layer configs the existing ColorApplier paints, the
 * forest strip, the containment gate, colour-only identity, and — because the
 * JVM cannot draw a Compose chain — the call-site placement in
 * ScreenshotCaptureScreen.kt (source pin).
 *
 * EXECUTED MUTATIONS (this class run alone, sources restored byte-exact —
 * sha256 in tools/titan/results/wave53-canvas-root/_note.md):
 *   CA1 resolveComposedCanvasRootBackground returns null (colour-only read) → a1_target red.
 *   CA2 withCanvasOwnedRootBackground returns `roots` (strip dropped)       → a1_strip red
 *       (a1_strip reads the real composedCanvasRoots chain the composable stacks).
 *   CA3 plan's `uniform` forced true                                          → a2_marginRoot001 red.
 *   CA4 attachmentAt always SCROLL (attachment ignored)                      → a2_marginRoot002 red.
 *   CA5 dataPngIs1x1 returns true for any PNG url                             → a3_ihdr red.
 *   CA6 the `contained` early return dropped                                  → a4_contained red.
 * wave-53 fix pass (L3 skeptic should-fix 1; sha256 in tools/titan/results/wave53-S1/_note.md "## Fix pass"):
 *   XC1 canvasPaintPlan's overpaint forced false (`!plan.uniform` → `false`)  → a7 red.
 *   XC2 canvasPaintPlan passes 0 as the tile base instead of [frame]         → a7 red.
 *   XC3 canvasPaintPlan drops `.asReversed()` (source order applied)         → a7 red.
 *   XC9 canvasModifier ignores the plan's overpaint flag (always returns m)   → a7 red (source pin).
 */
class ComposedCanvasRootBackgroundTest {

    /** Decode + slot-compose a v2 document exactly as the harness does. */
    private fun rootsOf(componentsJson: String): List<IRComponent> =
        com.styleconverter.runtime.core.renderer.SlotComposer.compose(
            com.styleconverter.runtime.core.ir.IRDocumentDecoder.decode(
                """{"irVersion":2,"minReaderVersion":2,"components":[$componentsJson]}"""))

    /** css-display/display-contents-root-background — both roots, verbatim. */
    private val target = """
        {"id":"wpt__css-display__display-contents-root-background__0-234","name":"wpt__css-display__display-contents-root-background__0","properties":[{"type":"Display","data":"CONTENTS"},{"type":"BackgroundImage","data":[{"url":"data:image/png,%89%50%4e%47%0d%0a%1a%0a%00%00%00%0d%49%48%44%52%00%00%00%01%00%00%00%01%01%03%00%00%00%25%db%56%ca%00%00%00%04%67%41%4d%41%00%00%af%c8%37%05%8a%e9%00%00%00%03%50%4c%54%45%00%80%00%9c%f9%a5%91%00%00%00%0a%49%44%41%54%78%da%63%60%00%00%00%02%00%01%e5%27%de%fc%00%00%00%19%74%45%58%74%53%6f%66%74%77%61%72%65%00%41%64%6f%62%65%20%49%6d%61%67%65%52%65%61%64%79%71%c9%65%3c%00%00%00%00%49%45%4e%44%ae%42%60%82","data":true}]}],"meta":{"role":"body-root"}},
        {"id":"wpt__css-display__display-contents-root-background__1-235","name":"wpt__css-display__display-contents-root-background__1","properties":[],"text":"Pass if the background is green.","meta":{"sourceTag":"p"}}
    """.trimIndent()

    /** css-backgrounds/background-attachment-margin-root-001 — its root, verbatim (`scroll, fixed`). */
    private val marginRoot001 = """
        {"id":"wpt__css-backgrounds__background-attachment-margin-root-001__0-091","name":"wpt__css-backgrounds__background-attachment-margin-root-001__0","properties":[{"type":"BackgroundImage","data":[{"type":"linear-gradient","stops":[{"color":{"srgb":{"r":0,"g":1,"b":0,"a":0.5},"original":{"r":0,"g":255,"b":0,"a":0.5}},"position":null},{"color":{"srgb":{"r":0,"g":0,"b":1,"a":0.5},"original":{"r":0,"g":0,"b":255,"a":0.5}},"position":null}]},{"type":"linear-gradient","stops":[{"color":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}},"position":null},{"color":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}},"position":null}]}]},{"type":"BackgroundAttachment","data":[{"type":"scroll"},{"type":"fixed"}]},{"type":"BackgroundSize","data":[{"w":{"px":100},"h":{"px":100}},{"w":{"px":100},"h":{"px":100}}]},{"type":"Height","data":{"type":"length","px":300}},{"type":"MarginTop","data":{"px":50}},{"type":"MarginRight","data":{"px":50}},{"type":"MarginBottom","data":{"px":50}},{"type":"MarginLeft","data":{"px":50}}],"meta":{"role":"body-root"}}
    """.trimIndent()

    /** css-backgrounds/background-attachment-margin-root-002 — its root, verbatim (`fixed, scroll`). */
    private val marginRoot002 = """
        {"id":"wpt__css-backgrounds__background-attachment-margin-root-002__0-092","name":"wpt__css-backgrounds__background-attachment-margin-root-002__0","properties":[{"type":"BackgroundImage","data":[{"type":"linear-gradient","stops":[{"color":{"srgb":{"r":0,"g":1,"b":0,"a":0.5},"original":{"r":0,"g":255,"b":0,"a":0.5}},"position":null},{"color":{"srgb":{"r":0,"g":0,"b":1,"a":0.5},"original":{"r":0,"g":0,"b":255,"a":0.5}},"position":null}]},{"type":"linear-gradient","stops":[{"color":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}},"position":null},{"color":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}},"position":null}]}]},{"type":"BackgroundAttachment","data":[{"type":"fixed"},{"type":"scroll"}]},{"type":"BackgroundSize","data":[{"w":{"px":100},"h":{"px":100}},{"w":{"px":100},"h":{"px":100}}]},{"type":"Height","data":{"type":"length","px":300}},{"type":"MarginTop","data":{"px":50}},{"type":"MarginRight","data":{"px":50}},{"type":"MarginBottom","data":{"px":50}},{"type":"MarginLeft","data":{"px":50}}],"meta":{"role":"body-root"}}
    """.trimIndent()

    /** css-cascade/initial-background-color — all roots, verbatim (colour-only body). */
    private val initialBg = """
        {"id":"wpt__css-cascade__initial-background-color__0-033","name":"wpt__css-cascade__initial-background-color__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"}],"meta":{"role":"body-root"}},
        {"id":"wpt__css-cascade__initial-background-color__1-034","name":"wpt__css-cascade__initial-background-color__1","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":0}},{"type":"Width","data":{"type":"percentage","value":100}},{"type":"Height","data":{"type":"percentage","value":100}},{"type":"BackgroundColor","data":{"original":"initial"}}]}
    """.trimIndent()

    /** css-color/a98rgb-003 — all roots, verbatim (colour-only body). */
    private val a98 = """
        {"id":"wpt__css-color__a98rgb-003__0-005","name":"wpt__css-color__a98rgb-003__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"grey"}}],"meta":{"role":"body-root"}},
        {"id":"wpt__css-color__a98rgb-003__1-006","name":"wpt__css-color__a98rgb-003__1","properties":[],"text":"Test passes if you see a single square, and not two rectangles of different colors.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-color__a98rgb-003__2-007","name":"wpt__css-color__a98rgb-003__2","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.996078431372549,"g":0.996078431372549,"b":0.996078431372549},"original":{"r":254,"g":254,"b":254}}},{"type":"Width","data":{"type":"length","original":{"v":12,"u":"EM"}}},{"type":"Height","data":{"type":"length","original":{"v":6,"u":"EM"}}},{"type":"MarginBottom","data":{"px":0}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css-color__a98rgb-003__3-008","name":"wpt__css-color__a98rgb-003__3","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.9999300658875595,"g":1,"b":1},"original":{"type":"color","colorSpace":"a98-rgb","values":[1,1,1]}}},{"type":"Width","data":{"type":"length","original":{"v":12,"u":"EM"}}},{"type":"Height","data":{"type":"length","original":{"v":6,"u":"EM"}}},{"type":"MarginTop","data":{"px":0}}]}
    """.trimIndent()

    /** css-contain/contain-body-bg-001 — all roots, verbatim (contained colour body). */
    private val containBg = """
        {"id":"wpt__css-contain__contain-body-bg-001__0-003","name":"wpt__css-contain__contain-body-bg-001__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"Width","data":{"type":"length","px":300}},{"type":"Height","data":{"type":"length","px":200}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Contain","data":["LAYOUT"]}],"meta":{"role":"body-root","lang":"en"}},
        {"id":"contain-body-bg-001__0-004","name":"contain-body-bg-001__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"Width","data":{"type":"length","px":300}},{"type":"Height","data":{"type":"length","px":200}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}}],"slot":{"parent":"wpt__css-contain__contain-body-bg-001__0-003"},"text":"Test passes if there is no red.","meta":{"sourceTag":"p","lang":"en"}}
    """.trimIndent()

    /** The body-root's properties of a composed document. */
    private fun bodyProps(roots: List<IRComponent>) = roots.first { it.role == "body-root" }.properties

    /** The target's data-URI layer URL, read from the verbatim wire. */
    private val targetUrl: String by lazy {
        val layers = bodyProps(rootsOf(target)).first { it.type == "BackgroundImage" }.data
            as kotlinx.serialization.json.JsonArray
        ((layers[0] as kotlinx.serialization.json.JsonObject)["url"] as kotlinx.serialization.json.JsonPrimitive).content
    }

    @Test
    fun a1_target_oneUniformLayer_framedOrigin() {
        val roots = rootsOf(target)
        // One scroll layer at the ICB corner, uniform (1×1 PNG, initial repeat).
        val plan = resolveComposedCanvasRootBackground(roots)
        assertEquals(RootBackgroundPropagation.Plan(listOf(Attachment.SCROLL), listOf(LayerOrigin(0f, 0f)), true), plan)
        // The existing ColorApplier paints exactly that URL, anchored at the frame (16,16).
        val cfg = RootBackgroundPropagation.layerConfigs(bodyProps(roots), plan!!, 16f).single()
        assertEquals(listOf(BackgroundImageConfig.Url(targetUrl)), cfg.backgroundImages)
        assertEquals(16.dp, cfg.backgroundPosition.xOffset)
        assertEquals(16.dp, cfg.backgroundPosition.yOffset)
        // No colour rides the image layer (the propagated colour paints below it).
        assertNull(cfg.backgroundColor)
    }

    @Test
    fun a1_strip_bodyRootLosesTheImage() {
        val roots = rootsOf(target)
        // Through the REAL forest rewrite the composable stacks (composedCanvasRoots).
        val out = composedCanvasRoots(withCanvasOwnedBodyMargin(roots, resolveComposedCanvasMargin(roots))
            .map(::withUaBlockMarginOnHoistedRoot), resolveComposedCanvasRootBackground(roots))
        // §2.11.2 "not painted again": the body-root keeps Display only; the <p> is untouched.
        assertEquals(listOf("Display"), out.first { it.role == "body-root" }.properties.map { it.type })
        assertEquals(roots[1], out[1])
    }

    @Test
    fun a2_marginRoot001_notUniform_scrollAtRootBox_fixedAtIcb() {
        val roots = rootsOf(marginRoot001)
        // Layer 0 is a real green→blue ramp → ICB-only; origins per §3.4.
        val plan = resolveComposedCanvasRootBackground(roots)
        assertEquals(RootBackgroundPropagation.Plan(
            listOf(Attachment.SCROLL, Attachment.FIXED), listOf(LayerOrigin(50f, 50f), LayerOrigin(0f, 0f)), false), plan)
        // Folded into the painted box's coordinates (ICB corner at the 16-px frame).
        val offs = RootBackgroundPropagation.layerConfigs(bodyProps(roots), plan!!, 16f)
            .map { it.backgroundPosition.xOffset to it.backgroundPosition.yOffset }
        assertEquals(listOf(66.dp to 66.dp, 16.dp to 16.dp), offs)
    }

    @Test
    fun a2_marginRoot002_isTheMirror() {
        val plan = resolveComposedCanvasRootBackground(rootsOf(marginRoot002))
        // `fixed, scroll`: the top layer anchors at the viewport (ICB) corner.
        assertEquals(listOf(LayerOrigin(0f, 0f), LayerOrigin(50f, 50f)), plan?.origins)
        assertEquals(listOf(Attachment.FIXED, Attachment.SCROLL), plan?.attachments)
        assertFalse(plan!!.uniform)
    }

    @Test
    fun a3_ihdr1x1_isUniform_ihdr2x2_isNot() {
        assertTrue(RootBackgroundPropagation.dataPngIs1x1(targetUrl))
        // The SAME payload with the IHDR width/height patched to 2.
        val twoByTwo = targetUrl.replace("%49%48%44%52%00%00%00%01%00%00%00%01", "%49%48%44%52%00%00%00%02%00%00%00%02")
        assertTrue(twoByTwo != targetUrl)
        assertFalse(RootBackgroundPropagation.dataPngIs1x1(twoByTwo))
        // A non-PNG data URI is never uniform by construction.
        assertFalse(RootBackgroundPropagation.dataPngIs1x1("data:image/gif,%47%49%46%38%39%61%01%00%01%00"))
    }

    @Test
    fun a4_contained_noPropagation_noStrip() {
        // The target + `contain: layout` → off the propagation path (css-contain-2 §2).
        val roots = rootsOf(target).map { r -> if (r.role != "body-root") r else r.copy(properties =
            r.properties + IRProperty("Contain", Json.parseToJsonElement("""["LAYOUT"]"""))) }
        assertNull(resolveComposedCanvasRootBackground(roots))
        assertSame(roots, RootBackgroundPropagation.withCanvasOwnedRootBackground(roots, null))
    }

    @Test
    fun a5_colourOnlyBodies_identity() {
        for (doc in listOf(initialBg, a98, containBg)) {
            val roots = rootsOf(doc)
            // No image layer → no plan, the same list, and the identity Modifier.
            val plan = resolveComposedCanvasRootBackground(roots)
            assertNull(plan)
            assertSame(roots, RootBackgroundPropagation.withCanvasOwnedRootBackground(roots, plan))
            assertSame(Modifier, RootBackgroundPropagation.canvasModifier(bodyProps(roots), plan, 16.dp,
                androidx.compose.ui.graphics.Color.White))
        }
        // …while a planned stack's modifier is a real chain. (Measured on the
        // gradient pair: on the JVM the url layer's synchronous BitmapFactory
        // decode is stubbed, so ColorApplier skips it there — on device the
        // same plain `{url: data:image/png…}` layer shape decodes through
        // ColorApplier: css-break/background-image-000/-001/-002 android P 1
        // (wave52-ship; the earlier citation, css-image-fallbacks-and-
        // annotations002, is the `image()` notation, a different path).)
        val roots = rootsOf(marginRoot001)
        assertNotSame(Modifier, RootBackgroundPropagation.canvasModifier(bodyProps(roots),
            resolveComposedCanvasRootBackground(roots), 16.dp, androidx.compose.ui.graphics.Color.White))
    }

    @Test
    fun a7_canvasPaintPlan_marginRoot001_blackUnderTheRamp_frameBandRepainted() {
        // The composition canvasModifier spends (the JVM cannot draw it): verbatim 001.
        val roots = rootsOf(marginRoot001)
        val paint = RootBackgroundPropagation.canvasPaintPlan(bodyProps(roots), resolveComposedCanvasRootBackground(roots)!!, 16f)
        // BOTTOM-UP = applied first: source layer 1 (the opaque black, `fixed`, ICB corner = the 16-px frame) under
        // source layer 0 (the translucent green→blue ramp, `scroll`, root box = 50 + 16).
        assertEquals(listOf(16.dp to 16.dp, 66.dp to 66.dp),
            paint.bottomUp.map { it.backgroundPosition.xOffset to it.backgroundPosition.yOffset })
        // A non-uniform stack never covers the frame: the band is repainted in the colour-layer value.
        assertTrue(paint.overpaintFrame)
        // The target's 1×1 green tile IS uniform: it covers the frame, no band repaint.
        val t = rootsOf(target)
        assertFalse(RootBackgroundPropagation.canvasPaintPlan(bodyProps(t), resolveComposedCanvasRootBackground(t)!!, 16f).overpaintFrame)
        // …and canvasModifier spends exactly that plan (source pin: the JVM cannot draw the chain).
        assertTrue(runtimeCode.contains("val paint = canvasPaintPlan(props, plan, frame.value)"))
        assertTrue(runtimeCode.contains("paint.bottomUp.forEach { m = ColorApplier.applyColors(m, it) }"))
        assertTrue(runtimeCode.contains("if (!paint.overpaintFrame) return m"))
    }

    /** The runtime's RootBackgroundPropagation.kt, comment lines dropped (repo-root walk-up). */
    private val runtimeCode: String by lazy {
        val anchor = "runtimes/compose/src/main/java/com/styleconverter/runtime/background/RootBackgroundPropagation.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        File(requireNotNull(dir), anchor).readText().lines().filter { !it.trim().startsWith("//") }.joinToString("\n")
    }

    // ── the call site (the JVM cannot draw the chain, so its PLACEMENT is pinned) ──

    /** The ComposedCaptureCanvas body of ScreenshotCaptureScreen.kt, comment lines dropped. */
    private val composedCode: String by lazy {
        val anchor = "apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        File(requireNotNull(dir), anchor).readText()
            .substringAfter("private fun ComposedCaptureCanvas(").substringBefore("private fun CompleteView(")
            .lines().filter { !it.trim().startsWith("//") }.joinToString("\n")
    }

    @Test
    fun callSite_paintsOnTheColour_underTheIcbClip_andStripsTheForest() {
        val bg = composedCode.indexOf("?: Modifier.background(canvasBackground)")
        val img = composedCode.indexOf(".then(rootImageModifier)")
        val clip = composedCode.indexOf("composedIcbClipBandPx(")
        // Above the colour (later in the chain), below every root (before the clip node's content).
        assertTrue(bg in 0 until img)
        assertTrue(img < clip)
        // The composable stacks the REAL rewrite (whose strip a1_strip pins) …
        assertTrue(composedCode.contains("composedCanvasRoots(\n            withCanvasOwnedBodyMargin(roots, canvasMargin).map(::withUaBlockMarginOnHoistedRoot), rootImagePlan)"))
    }
}
