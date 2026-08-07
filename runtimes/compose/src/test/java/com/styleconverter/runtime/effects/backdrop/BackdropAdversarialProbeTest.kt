package com.styleconverter.runtime.effects.backdrop

// ADVERSARIAL PROBES for wave-26 lane BF-A (Compose backdrop-filter two-pass).
//
// The lane's own suite pins each pure helper in isolation. These probes do the
// thing the lane could NOT do on a device: RASTER the composite pass by
// re-executing, in plain JVM arithmetic, exactly the op list the painter emits
// (sample crop -> colour matrix -> border-box clip) over a synthetic backdrop,
// and assert real pixel values. Plus the boundary cases the brief named:
// partially-offscreen boxes, rounded clips, nested/overlapping backdrops, and
// the dark-stage no-op gate.

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.borders.radius.BorderRadiusConfig
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.effects.filter.FilterFunction
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A software raster of ONE backdrop patch, built from the runtime's own pure
 * helpers plus the two platform rules the painter delegates to Android:
 *
 *  - `android.graphics.ColorMatrix` semantics: for a row-major 4x5 matrix and
 *    a pixel in 0..255, `out_i = clamp(m[i*5+0]*R + m[i*5+1]*G + m[i*5+2]*B +
 *    m[i*5+3]*A + m[i*5+4])`.
 *  - `DrawScope.clipPath(roundRect)` semantics: a pixel is kept iff its centre
 *    lies inside the border box AND inside every corner ellipse it falls under.
 *
 * Only the invert family is rastered here: the blur op is a platform Gaussian
 * (RenderEffect) with no reimplementable closed form, so blur is probed through
 * geometry (which pixels feed it) rather than through output values.
 */
private class PatchRaster(
    val backdrop: IntArray,
    val srcW: Int,
    val srcH: Int,
) {
    /** Transparent = "the painter drew nothing here". */
    val NOTHING = 0

    fun render(
        elemLeft: Int,
        elemTop: Int,
        boxW: Int,
        boxH: Int,
        chain: BackdropChain,
        radii: BackdropCornerRadii?,
    ): IntArray {
        val out = IntArray(boxW * boxH) { NOTHING }
        val s = BackdropSampleGeometry.sample(
            elemLeft = elemLeft, elemTop = elemTop,
            elemWidth = boxW, elemHeight = boxH,
            srcWidth = srcW, srcHeight = srcH,
        ) ?: return out // refusal: nothing drawn, element paints as before
        val matrix = colourMatrixOf(chain)
        for (y in 0 until boxH) for (x in 0 until boxW) {
            // Element-local -> crop-local -> backdrop-image coords, exactly the
            // mapping drawImage(srcOffset, dstOffset, 1:1 size) performs.
            val cropX = x - s.dstLeft
            val cropY = y - s.dstTop
            if (cropX < 0 || cropY < 0 || cropX >= s.width || cropY >= s.height) continue
            // Border-box clip (clipRect for a square box, clipPath otherwise).
            if (radii != null && !radii.isSquare &&
                !insideRoundRect(x + 0.5f, y + 0.5f, boxW.toFloat(), boxH.toFloat(), radii)
            ) continue
            val src = backdrop[(s.srcTop + cropY) * srcW + (s.srcLeft + cropX)]
            out[y * boxW + x] = if (matrix == null) src else applyMatrix(matrix, src)
        }
        return out
    }

    private fun colourMatrixOf(chain: BackdropChain): FloatArray? {
        var m: FloatArray? = null
        for (op in chain.ops) if (op is BackdropOp.Invert) {
            val next = BackdropChain.invertColorMatrix(op.amount)
            m = if (m == null) next else concat(next, m)
        }
        return m
    }

    /** `post` applied after `pre`, i.e. the matrix product post x pre. */
    private fun concat(post: FloatArray, pre: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (r in 0 until 4) for (c in 0 until 5) {
            var acc = 0f
            for (k in 0 until 4) acc += post[r * 5 + k] * pre[k * 5 + c]
            if (c == 4) acc += post[r * 5 + 4]
            out[r * 5 + c] = acc
        }
        return out
    }

    private fun applyMatrix(m: FloatArray, argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        fun ch(i: Int): Int {
            val v = m[i * 5] * r + m[i * 5 + 1] * g + m[i * 5 + 2] * b +
                m[i * 5 + 3] * a + m[i * 5 + 4]
            return Math.round(v).coerceIn(0, 255)
        }
        return (ch(3) shl 24) or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
    }

    private fun insideRoundRect(
        px: Float, py: Float, w: Float, h: Float, r: BackdropCornerRadii,
    ): Boolean {
        if (px < 0f || py < 0f || px > w || py > h) return false
        fun corner(cx: Float, cy: Float, rx: Float, ry: Float, inX: Boolean, inY: Boolean): Boolean {
            if (rx <= 0f || ry <= 0f) return true
            val dx = if (inX) cx - px else px - cx
            val dy = if (inY) cy - py else py - cy
            if (dx <= 0f || dy <= 0f) return true // not in this corner's quadrant
            val nx = dx / rx
            val ny = dy / ry
            return nx * nx + ny * ny <= 1f
        }
        return corner(r.topLeftX, r.topLeftY, r.topLeftX, r.topLeftY, true, true) &&
            corner(w - r.topRightX, r.topRightY, r.topRightX, r.topRightY, false, true) &&
            corner(w - r.bottomRightX, h - r.bottomRightY, r.bottomRightX, r.bottomRightY, false, false) &&
            corner(r.bottomLeftX, h - r.bottomLeftY, r.bottomLeftX, r.bottomLeftY, true, false)
    }
}

class BackdropAdversarialProbeTest {

    private fun solid(w: Int, h: Int, argb: Int) = PatchRaster(IntArray(w * h) { argb }, w, h)

    private val GREEN = 0xFF00FF00.toInt()
    private val MAGENTA = 0xFFFF00FF.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val BLACK = 0xFF000000.toInt()

    private fun invert(amount: Float) = BackdropChain(listOf(BackdropOp.Invert(amount)))

    private fun irComponent(
        id: String,
        backdrop: Boolean = false,
        children: List<IRComponent>? = null,
    ) = IRComponent(
        id = id,
        name = id,
        properties = if (backdrop) listOf(IRProperty("BackdropFilter", JsonArray(emptyList()))) else emptyList(),
        children = children,
    )

    /** Locate a repo file from whatever working dir Gradle hands the test. */
    private fun repoFile(relative: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, relative)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        throw AssertionError("could not locate $relative from ${File(".").absolutePath}")
    }

    // ── PROBE 1 — raster: the brief's own acceptance pixel ─────────────────

    @Test
    fun `green backdrop under invert 100 percent rasters magenta`() {
        val raster = solid(390, 600, GREEN)
        val out = raster.render(60, 150, 100, 100, invert(1f), radii = null)
        // Every pixel of the border box, not just a sampled one.
        for (p in out) assertEquals(MAGENTA.toUInt().toString(16), p.toUInt().toString(16))
    }

    @Test
    fun `white backdrop under invert 100 percent rasters black — the clip-rect-2 assertion`() {
        // backdrop-filter-clip-rect-2.html: `.bf > .box{backdrop-filter:invert(1)}`
        // over the white capture canvas must read as a black rounded box.
        val raster = solid(390, 600, WHITE)
        val out = raster.render(16, 120, 100, 100, invert(1f), radii = null)
        for (p in out) assertEquals(BLACK, p)
    }

    @Test
    fun `invert 50 percent collapses every channel to mid grey and keeps alpha`() {
        // The sharpest slope check available in raster form: 1-2a, not 1-a.
        val raster = solid(64, 64, GREEN)
        val out = raster.render(0, 0, 10, 10, invert(0.5f), radii = null)
        for (p in out) {
            assertEquals(0xFF, (p ushr 24) and 0xFF)
            assertEquals(128, (p ushr 16) and 0xFF)
            assertEquals(128, (p ushr 8) and 0xFF)
            assertEquals(128, p and 0xFF)
        }
    }

    @Test
    fun `two chained inverts raster back to the original backdrop`() {
        // Pins the software path's matrix CONCATENATION order, which the lane's
        // suite only checks one matrix at a time. invert(1) twice is identity.
        val raster = solid(64, 64, GREEN)
        val chain = BackdropChain(listOf(BackdropOp.Invert(1f), BackdropOp.Invert(1f)))
        val out = raster.render(0, 0, 8, 8, chain, radii = null)
        for (p in out) assertEquals(GREEN, p)
    }

    @Test
    fun `a non uniform backdrop is inverted per pixel, not per box`() {
        // Half green / half white, so a whole-box colour swap would pass the
        // uniform probes above but fail here.
        val w = 40; val h = 20
        val px = IntArray(w * h) { i -> if ((i % w) < w / 2) GREEN else WHITE }
        val out = PatchRaster(px, w, h).render(0, 0, w, h, invert(1f), radii = null)
        for (y in 0 until h) for (x in 0 until w) {
            val expected = if (x < w / 2) MAGENTA else BLACK
            assertEquals("($x,$y)", expected, out[y * w + x])
        }
    }

    // ── PROBE 2 — rounded clip (backdrop-filter-clip-rect-2 geometry) ──────

    @Test
    fun `clip-rect-2 radii map corner-for-corner and cut the patch at the corners`() {
        // border-radius: 10px 20px 30px 40px  ->  TL TR BR BL.
        val cfg = BorderRadiusConfig(
            topStart = 10.dp to 10.dp,
            topEnd = 20.dp to 20.dp,
            bottomEnd = 30.dp to 30.dp,
            bottomStart = 40.dp to 40.dp,
        )
        val radii = BackdropClipGeometry.resolve(cfg, 100f, 100f, { d: Dp -> d.value })!!
        assertEquals(10f, radii.topLeftX, 0f)
        assertEquals(20f, radii.topRightX, 0f)
        assertEquals(30f, radii.bottomRightX, 0f)
        assertEquals(40f, radii.bottomLeftX, 0f)

        val out = solid(390, 600, WHITE).render(16, 120, 100, 100, invert(1f), radii)
        // Corner pixels fall OUTSIDE their ellipse -> nothing drawn there.
        assertEquals("top-left", 0, out[0])
        assertEquals("top-right", 0, out[99])
        assertEquals("bottom-right", 0, out[99 * 100 + 99])
        assertEquals("bottom-left", 0, out[99 * 100])
        // The centre is filtered.
        assertEquals(BLACK, out[50 * 100 + 50])
        // The bigger the radius the deeper the bite: BL(40) eats a pixel that
        // TL(10) keeps, at the same distance from its own corner.
        // Same 6px inset from the corner: TL (r=10) keeps the pixel, BL (r=40)
        // has already cut it away.
        assertEquals("(6,6) inside TL(10)", BLACK, out[6 * 100 + 6])
        assertEquals("(6,93) outside BL(40)", 0, out[93 * 100 + 6])
    }

    @Test
    fun `RTL swaps the logical corners — the backdrop clip must agree with BorderRadiusApplier`() {
        val cfg = BorderRadiusConfig(
            topStart = 10.dp to 10.dp,
            topEnd = 20.dp to 20.dp,
            bottomEnd = 30.dp to 30.dp,
            bottomStart = 40.dp to 40.dp,
        )
        val rtl = BackdropClipGeometry.resolve(cfg, 100f, 100f, { d: Dp -> d.value }, isLtr = false)!!
        assertEquals(20f, rtl.topLeftX, 0f)
        assertEquals(10f, rtl.topRightX, 0f)
        assertEquals(40f, rtl.bottomRightX, 0f)
        assertEquals(30f, rtl.bottomLeftX, 0f)
    }

    @Test
    fun `percentage radii resolve x against width and y against height`() {
        val cfg = BorderRadiusConfig(
            topStart = 0.dp to 0.dp,
            topStartFraction = 0.5f to 0.5f,
        )
        val r = BackdropClipGeometry.resolve(cfg, 200f, 80f, { d: Dp -> d.value })!!
        assertEquals(100f, r.topLeftX, 0f)
        assertEquals(40f, r.topLeftY, 0f)
    }

    // ── PROBE 3 — partially offscreen boxes ───────────────────────────────

    @Test
    fun `a box hanging off the LEFT edge draws only the covered strip`() {
        // A box whose ORIGIN is negative is now the only case where the clamp
        // bites at all (the sample rect is the border box), and dst must go
        // POSITIVE so the uncovered strip stays unpainted.
        val s = BackdropSampleGeometry.sample(-20, 10, 100, 40, 390, 600)!!
        assertEquals(0, s.srcLeft)
        assertEquals(80, s.width)
        assertEquals(20, s.dstLeft) // canvas x=0 lands at element-local x=20

        val out = solid(390, 600, GREEN).render(-20, 10, 100, 40, invert(1f), radii = null)
        // Element-local 0..19 has no backdrop root behind it -> untouched.
        for (y in 0 until 40) for (x in 0 until 20) assertEquals("($x,$y)", 0, out[y * 100 + x])
        // 20..99 is filtered.
        for (y in 0 until 40) for (x in 20 until 100) assertEquals("($x,$y)", MAGENTA, out[y * 100 + x])
    }

    @Test
    fun `a box hanging off the BOTTOM edge draws only the covered strip`() {
        val out = solid(390, 600, GREEN).render(10, 580, 40, 40, invert(1f), radii = null)
        for (x in 0 until 40) {
            assertEquals("row 0", MAGENTA, out[0 * 40 + x])
            assertEquals("row 19", MAGENTA, out[19 * 40 + x])
            assertEquals("row 20 (past the canvas)", 0, out[20 * 40 + x])
            assertEquals("row 39", 0, out[39 * 40 + x])
        }
    }

    @Test
    fun `a fully offscreen box paints nothing at all`() {
        val out = solid(390, 600, GREEN).render(700, 10, 40, 40, invert(1f), radii = null)
        for (p in out) assertEquals(0, p)
    }

    // ── PROBE 4 — blur geometry (the value the raster cannot compute) ──────

    @Test
    fun `edge-pixels fixture geometry — blur 30px still samples exactly the border box`() {
        // fixtures/wpt/filter-effects/backdrop-filter-edge-pixels.json — the
        // converter emits blur r.px = 30, FilterExtractor -> Blur(30.dp), and
        // the emulator runs at 160dpi so 1dp == 1px.
        val chain = BackdropChain.of(listOf(FilterFunction.Blur(30.dp)))!!
        val sigma = chain.totalBlurSigmaPx { d -> d.value }
        assertEquals(30f, sigma, 1e-4f)
        // The .box sits at the page origin; with the canvas's 16px frame that
        // is (16,16), 102x102 with its 1px blue border. This fixture was THE
        // reason the wave-26 grow model looked safe — a box at the plate edge.
        // Under the wave-34 edge model (filter-effects-2 §2 clips the backdrop
        // to the border box first) the σ never enters the rect at all, and
        // TileMode.MIRROR supplies what the Gaussian wants beyond it.
        val s = BackdropSampleGeometry.sample(16, 16, 102, 102, 390, 600)!!
        assertEquals(16, s.srcLeft)
        assertEquals(16, s.srcTop)
        assertEquals(102, s.width)
        assertEquals(102, s.height)
        // Nothing was clamped, so the patch lands at the box's own origin.
        assertEquals(0, s.dstLeft)
        assertEquals(0, s.dstTop)
    }

    @Test
    fun `blur composes in quadrature but each op still blurs in CSS order`() {
        // σ composition still matters — it drives the Gaussian itself (and,
        // on iOS, the width of the mirror band). It just no longer touches
        // the sample rect.
        val chain = BackdropChain.of(listOf(FilterFunction.Blur(3.dp), FilterFunction.Blur(4.dp)))!!
        assertEquals(5f, chain.totalBlurSigmaPx { d -> d.value }, 1e-4f)
        assertEquals(2, chain.ops.size)
    }

    // ── PROBE 5 — nested / overlapping backdrop elements ──────────────────

    @Test
    fun `a nested backdrop element is DETECTED but shares the single canvas root`() {
        // Both the outer and inner element declare backdrop-filter. The IR scan
        // must see it (so the two-pass host arms)...
        val inner = irComponent("inner", backdrop = true)
        val outer = irComponent("outer", backdrop = true, children = listOf(inner))
        assertTrue(documentDeclaresBackdropFilter(listOf(outer)))

        // ...and the DECLARED boundary is that pass A suppresses BOTH, so the
        // inner element samples a canvas from which its own ancestor's paint is
        // missing. Rastered: a green canvas with an outer white box on it. The
        // browser gives the inner element the OUTER's painted pixels; this
        // engine gives it the raw canvas. Same rectangle, different colours —
        // documented, not silently claimed correct.
        val w = 200; val h = 200
        val canvasOnly = IntArray(w * h) { GREEN }
        val browserView = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            if (x in 20 until 180 && y in 20 until 180) WHITE else GREEN
        }
        val ours = PatchRaster(canvasOnly, w, h).render(60, 60, 40, 40, invert(1f), null)
        val browser = PatchRaster(browserView, w, h).render(60, 60, 40, 40, invert(1f), null)
        for (p in ours) assertEquals(MAGENTA, p)   // inverted GREEN — the canvas
        for (p in browser) assertEquals(BLACK, p)  // inverted WHITE — the ancestor
        assertFalse("the nested-root approximation is real, not cosmetic", ours[0] == browser[0])
    }

    @Test
    fun `two OVERLAPPING backdrop elements both read the canvas, not each other`() {
        // Same single-root approximation, stated for siblings: A paints, then B
        // overlaps A. The browser's B sees A's pixels; here both see the canvas.
        val w = 100; val h = 100
        val canvas = IntArray(w * h) { GREEN }
        val a = PatchRaster(canvas, w, h).render(10, 10, 40, 40, invert(1f), null)
        val b = PatchRaster(canvas, w, h).render(30, 30, 40, 40, invert(1f), null)
        for (p in a) assertEquals(MAGENTA, p)
        for (p in b) assertEquals(MAGENTA, p) // browser: B's overlap would read A's magenta -> green
    }

    // ── PROBE 6 — the scope refusal, end to end from the extractor's types ─

    @Test
    fun `a mixed chain refuses wholesale and keeps the historical no-op`() {
        assertNull(
            BackdropChain.of(listOf(FilterFunction.Invert(1f), FilterFunction.Grayscale(1f))),
        )
        assertNotNull(BackdropChain.of(listOf(FilterFunction.Invert(1f))))
    }

    // ── PROBE 7 — dark-stage protection, stated precisely ─────────────────

    @Test
    fun `the 327-pair baseline fixture DOES declare backdrop-filter — the gate is composedMode`() {
        // The task's stated protection ("no baseline fixture carries
        // BackdropFilter") is FALSE: fixtures/visual-test.json's Glass_Effect
        // declares `backdrop-filter: blur(10px)`. Pin the real fact so nobody
        // relaxes the composedMode gate believing the fixture is clean.
        val f = repoFile("fixtures/visual-test.json")
        assertTrue("expected the baseline fixture at ${f.absolutePath}", f.exists())
        assertTrue(
            "visual-test.json is expected to declare backdrop-filter (Glass_Effect)",
            f.readText().contains("backdrop-filter"),
        )
    }

    @Test
    fun `an idle coordinator makes every backdrop element a no-op`() {
        val c = BackdropPassCoordinator()
        assertFalse(c.enabled)
        assertEquals(BackdropPass.DISABLED, c.pass)
        assertNull(c.backdrop)
        // Arming with `false` (what the per-component path always does) is
        // indistinguishable from never arming at all.
        c.beginDocument(false)
        assertFalse(c.enabled)
        assertEquals(BackdropPass.DISABLED, c.pass)
    }

    // ── PROBE 8 — element opacity attenuates the filtered backdrop ────────

    @Test
    fun `opacity zero installs no backdrop node at all`() {
        // backdrop-filter-basic-opacity.html: `opacity: 0` on the inverting
        // box, and the ref is the plain UNFILTERED green box. Before the fix
        // the patch drew at full strength (ColorApplier's alpha layer is at
        // StyleApplier step 6, INSIDE the step-3 backdrop node) — so the old
        // no-op PASSED this test and the two-pass render would have FAILED it.
        val c = BackdropPassCoordinator()
        c.beginDocument(true)
        val base = androidx.compose.ui.Modifier
        val out = base.backdropFilterTwoPass(
            chain = invert(1f),
            radiusConfig = BorderRadiusConfig.NONE,
            coordinator = c,
            elementAlpha = 0f,
        )
        assertTrue("opacity:0 must add no draw node", out === base)
    }

    @Test
    fun `a visible element still installs the backdrop node`() {
        val c = BackdropPassCoordinator()
        c.beginDocument(true)
        val base = androidx.compose.ui.Modifier
        val out = base.backdropFilterTwoPass(
            chain = invert(1f),
            radiusConfig = BorderRadiusConfig.NONE,
            coordinator = c,
            elementAlpha = 1f,
        )
        assertFalse("opacity:1 must register the two-pass hooks", out === base)
    }

    // ── PROBE 9 — the margin box is stripped back to the border box ───────

    @Test
    fun `the effects step is applied OUTSIDE the margin step in StyleApplier`() {
        // The backdrop modifier is installed at StyleApplier step 3 (effects).
        // LayoutFacade (step 4) emits MARGIN as Modifier.absolutePadding, which
        // is INSIDE it in chain order, so the draw node is sized and positioned
        // by the MARGIN box. That is still true — and is precisely why the
        // resolved bands are threaded in and subtracted (wave-26 skeptic fix
        // #4). Pinned as source order because the JVM cannot run Compose
        // layout; the arithmetic itself is pinned in
        // BackdropContractParityTest's border-box table.
        val src = repoFile("runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt").readText()
        val effects = src.indexOf("result = EffectsFacade.apply(")
        val layout = src.indexOf("result = LayoutFacade.applyToModifier(result, config.layout")
        assertTrue(effects > 0 && layout > 0)
        assertTrue(
            "effects (step 3) must precede layout/margin (step 4) for this pin to describe reality",
            effects < layout,
        )
        val margin = repoFile("runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/MarginApplier.kt").readText()
        assertTrue(
            "margin is emitted as absolutePadding, which inflates the OUTER (effects-node) box",
            margin.contains("absolutePadding"),
        )
        assertTrue(
            "the effects call must subtract those bands via MarginApplier.resolvedInsets",
            src.contains("MarginApplier.resolvedInsets("),
        )
    }

    @Test
    fun `a margin-carrying element samples its BORDER box, not its margin box`() {
        // End-to-end for the fix, in the one form the JVM can execute: a
        // 60x40 element with margin 10/6 sits inside a 80x52 draw node whose
        // origin is 10px left / 6px up of the border box. Before the fix the
        // patch sampled that 80x52 rect at the node origin — 10px of somebody
        // else's backdrop on each side, and a clip 20px too wide.
        val box = BackdropSampleGeometry.borderBox(
            nodeWidth = 80f, nodeHeight = 52f,
            marginLeftPx = 10f, marginTopPx = 6f,
            marginRightPx = 10f, marginBottomPx = 6f,
        )!!
        assertEquals(60f, box.width, 0f)
        assertEquals(40f, box.height, 0f)
        // Node origin (100,100) → border-box origin (110,106) in canvas space.
        val sample = BackdropSampleGeometry.sample(
            elemLeft = Math.round(100f + box.localLeft),
            elemTop = Math.round(100f + box.localTop),
            elemWidth = Math.round(box.width),
            elemHeight = Math.round(box.height),
            srcWidth = 390, srcHeight = 600,
        )!!
        assertEquals(110, sample.srcLeft)
        assertEquals(106, sample.srcTop)
        assertEquals(60, sample.width)
        assertEquals(40, sample.height)
    }
}
