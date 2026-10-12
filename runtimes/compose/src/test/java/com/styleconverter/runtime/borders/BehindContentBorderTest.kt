package com.styleconverter.runtime.borders

// Wave 55 (lane L6, unit U1 PO-selfpaint-android) — pins P1 and P2 for
// BehindContentBorder.kt, the border band painted BEHIND the content.
//
// P1 — the ORDER: drawInOrder runs the band, then the content (CSS 2.1
//      Appendix E §E.2: a box's border before its descendants); the node
//      draws through it; and the self-paint route installs our element, never
//      androidx `BorderModifierNodeElement` (whose three outline branches
//      call drawContent() first — javap of foundation 1.11.4).
// P2 — the GEOMETRY: the band arithmetic copied from that bytecode
//      (strokeWidthPx = min(ceil(w), ceil(minDimension / 2)); topLeft at half
//      the stroke; fillArea = 2·stroke > minDimension; inner corners shrunk
//      by the stroke and floored at 0) — raster parity with the androidx band.
// JVM only (the plain unit suite cannot draw: android.graphics is stubbed),
// so the draw ORDER is pinned on the pure drawInOrder plus a source pin.
// P5 (fix-r1, skeptic D2) — the DRAW CALLS: rectBand / roundedBand run on a
//      recording Proxy DrawScope, call by call against BorderKt's bytecode;
//      the node's re-cache on update; the generic branch's opaque tint (source).

// Modifier chain + the values the pins compare.
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
// The applier whose self-paint route U1 changes, and its config types.
import com.styleconverter.runtime.borders.radius.BorderRadiusApplier
import com.styleconverter.runtime.borders.radius.BorderRadiusConfig
import com.styleconverter.runtime.borders.sides.AllBordersConfig
import com.styleconverter.runtime.borders.sides.BorderSideConfig
import com.styleconverter.runtime.core.types.ValueExtractors.LineStyle
// Source pin: read BehindContentBorder.kt from the repo.
import java.io.File
// P5's recording DrawScope: one dynamic proxy per Compose draw interface.
import java.lang.reflect.Proxy
// JUnit4 (this module's runner).
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BehindContentBorderTest {

    // ── P1: the order ────────────────────────────────────────────────────

    @Test fun `P1a - drawInOrder paints the band, then the content`() {
        // Recorders stand in for the band painter and drawContent().
        val log = mutableListOf<String>()
        drawInOrder(band = { log += "band" }, content = { log += "content" })
        // §E.2: the border (step 2) before the descendants (steps 4-8).
        assertEquals(listOf("band", "content"), log)
    }

    // The clip-rect navbar: border-radius 10/20/30/40, 2px solid blue (the
    // same configs BorderRadiusSelfPaintTest pins through the real extractors).
    private val navbarRadius = BorderRadiusConfig(
        topStart = 10.dp to 10.dp, topEnd = 20.dp to 20.dp,
        bottomEnd = 30.dp to 30.dp, bottomStart = 40.dp to 40.dp,
    )
    private val blue2 = BorderSideConfig(width = 2.dp, color = Color.Blue, style = LineStyle.SOLID)
    private val uniformSolidBlue = AllBordersConfig(top = blue2, end = blue2, bottom = blue2, start = blue2)

    @Test fun `P1b - self-paint installs BehindContentBorderElement and no androidx border node`() {
        // Fold the navbar's self-paint chain into its elements.
        val chain = BorderRadiusApplier.applySelfPaint(Modifier, navbarRadius, uniformSolidBlue, backgroundColor = null)
            .foldIn(listOf<Modifier.Element>()) { acc, e -> acc + e }
        // Exactly one band element, ours …
        val bands = chain.filterIsInstance<BehindContentBorderElement>()
        assertEquals("one behind-content band in $chain", 1, bands.size)
        // … carrying the declared width, the SolidColor brush and the radius Shape.
        assertEquals(2.dp, bands[0].width)
        assertEquals(SolidColor(Color.Blue), bands[0].brush)
        assertEquals(BorderRadiusApplier.shapeFor(navbarRadius), bands[0].shape)
        // … and never the androidx node that draws content first.
        assertFalse("androidx border must not install: $chain",
            chain.any { it.javaClass.simpleName == "BorderModifierNodeElement" })
    }

    @Test fun `P1c - the node draws only through drawInOrder, band first`() {
        // Source of the node (repo-root walk, the BackfaceCullChainTest idiom).
        val src = source("runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt")
        // Code lines only: comments may mention drawContent() freely.
        val code = src.lines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString("\n")
        // The banded draw goes through drawInOrder with the band first …
        assertTrue("band-first drawInOrder call",
            code.contains("onDrawWithContent { drawInOrder(band = { band() }, content = { drawContent() }) }"))
        // … and the only other drawContent() is the no-band fallback.
        assertEquals("drawContent() call sites", 2, Regex("""drawContent\(\)""").findAll(code).count())
        assertTrue("no-band fallback", code.contains("?: return onDrawWithContent { drawContent() }"))
    }

    // ── P2: the band geometry (androidx BorderModifierNode arithmetic) ───

    @Test fun `P2a - a 2px band on the 304x54 navbar border box`() {
        // stroke 2 → topLeft (1,1), borderSize (302,52), a Stroke (not a fill).
        assertEquals(BandGeometry(2f, Offset(1f, 1f), Size(302f, 52f), false),
            bandGeometry(2f, hairline = false, size = Size(304f, 54f)))
    }

    @Test fun `P2b - fractional widths round UP, as androidx ceil does`() {
        // 1.5px → ceil 2: the same 2px stroke androidx rasterises.
        assertEquals(2f, bandGeometry(1.5f, hairline = false, size = Size(100f, 100f))!!.strokePx)
        // A hairline is exactly 1px whatever the width says.
        assertEquals(1f, bandGeometry(0f, hairline = true, size = Size(100f, 100f))!!.strokePx)
    }

    @Test fun `P2c - the stroke caps at half the box and fills when the bands overlap`() {
        // 30px on 51x51: min(30, ceil(25.5)) = 26, 2·26 > 51 → fillArea.
        val odd = bandGeometry(30f, hairline = false, size = Size(51f, 51f))!!
        assertEquals(26f, odd.strokePx)
        assertTrue(odd.fillArea)
        // 30px on 50x50: min(30, 25) = 25, 2·25 = 50 is NOT > 50 → still a Stroke
        // (androidx's strict `>`; the brief's "30px on 50x50 → fill" example was off by one).
        val even = bandGeometry(30f, hairline = false, size = Size(50f, 50f))!!
        assertEquals(25f, even.strokePx)
        assertFalse(even.fillArea)
    }

    @Test fun `P2d - invalid width or an empty box paints no band`() {
        // hasValidBorderParams: width >= 0 and minDimension > 0.
        assertNull(bandGeometry(-1f, hairline = false, size = Size(10f, 10f)))
        assertNull(bandGeometry(2f, hairline = false, size = Size(0f, 10f)))
    }

    @Test fun `P2e - inner corners shrink by the stroke and floor at 0`() {
        // The navbar outline (circular 10/20/30/40) and its 2px band's inner edge.
        val outer = RoundRect(0f, 0f, 304f, 54f, CornerRadius(10f), CornerRadius(20f), CornerRadius(30f), CornerRadius(40f))
        val inner = insetRoundRect(2f, outer)
        // Inner rect (2,2)-(302,52) …
        assertEquals(listOf(2f, 2f, 302f, 52f), listOf(inner.left, inner.top, inner.right, inner.bottom))
        // … with corners 8 / 18 / 28 / 38 (each max(0, r − 2)).
        assertEquals(listOf(CornerRadius(8f), CornerRadius(18f), CornerRadius(28f), CornerRadius(38f)),
            listOf(inner.topLeftCornerRadius, inner.topRightCornerRadius, inner.bottomRightCornerRadius, inner.bottomLeftCornerRadius))
        // A 1px radius under a 2px band shrinks to 0, never negative.
        assertEquals(CornerRadius(0f, 0f), shrinkCorner(CornerRadius(1f, 1f), 2f))
        // The navbar Shape takes androidx's ROUNDED outline (circular corners, no fraction).
        assertTrue(BorderRadiusApplier.shapeFor(navbarRadius) is RoundedCornerShape)
    }

    // ── P5: what the band painters DRAW (fix-r1, skeptic D2) ──────────────
    // Expected calls are BorderKt 1.11.4's (javap -c): drawRectBorder's one
    // drawRect; drawRoundRectBorder's simple branch (fill / Difference-clipped
    // fill / shrunk stroke). The path branches need android.graphics.Path and
    // genericBand an Android bitmap, so they stay device-proven ([ID] keys).

    // The brush every P5 band paints with (its toString lands in the log).
    private val blue = SolidColor(Color.Blue)

    /** A packed Offset / Size / CornerRadius long as "(x,y)" (high 32 bits x, low y). */
    private fun xy(v: Any?) = (v as Long).let { "(${Float.fromBits((it ushr 32).toInt())},${Float.fromBits(it.toInt())})" }

    /** A recording proxy of [type]: [h] answers each call by its base name (the value-class mangling `-xxxx` cut). */
    private fun <T> spy(type: Class<T>, h: (String, Array<Any?>) -> Any?): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, m, a -> h(m.name.substringBefore('-'), a ?: emptyArray()) })

    /** A call no band painter makes: fail loudly rather than answer it. */
    private fun unexpected(n: String): Nothing = throw AssertionError("unexpected draw-scope call $n")

    /** Runs [paint] on a [box]-sized recording DrawScope; returns its draw / clip calls in order. */
    private fun record(box: Size, paint: DrawScope.() -> Unit): List<String> {
        // One line per draw / clip call, in call order.
        val log = mutableListOf<String>()
        // DrawContext / DrawTransform hand the size back packed, as the inline-class ABI does.
        val packed = (box.width.toRawBits().toLong() shl 32) or (box.height.toRawBits().toLong() and 0xFFFFFFFFL)
        // The canvas sees clipRect's save / restore bracket; anything else is logged.
        val canvas = spy(Canvas::class.java) { n, _ -> if (n == "save" || n == "restore") null else { log += "canvas.$n"; null } }
        // clipRect(left, top, right, bottom, op) — ClipOp.Difference is 0.
        val transform = spy(DrawTransform::class.java) { n, a -> when (n) {
            "getSize" -> packed
            "clipRect" -> { log += "clipRect ${a[0]} ${a[1]} ${a[2]} ${a[3]} op=${a[4]}"; null }
            else -> { log += "transform.$n"; null } } }
        // withTransform reads / restores the size and reaches the canvas and the transform.
        val ctx = spy(DrawContext::class.java) { n, _ -> when (n) {
            "getSize" -> packed; "setSize" -> null; "getTransform" -> transform; "getCanvas" -> canvas; else -> unexpected(n) } }
        // drawRect(brush, topLeft, size, alpha, style, filter, blend) / drawRoundRect(…, cornerRadius, alpha, style, …).
        val style = { v: Any? -> if (v is Stroke) "Stroke(${v.width})" else "Fill" }
        // The scope: its size (a `default` member on the real interface) and the draw calls.
        val scope = spy(DrawScope::class.java) { n, a -> when {
            n == "getDrawContext" -> ctx
            n == "getSize" -> packed
            n == "getLayoutDirection" -> LayoutDirection.Ltr
            n == "drawRect" -> { log += "drawRect ${xy(a[1])} ${xy(a[2])} ${style(a[4])} ${a[0]} a=${a[3]} f=${a[5]} b=${a[6]}"; null }
            n == "drawRoundRect" -> { log += "drawRoundRect ${xy(a[1])} ${xy(a[2])} r=${xy(a[3])} ${style(a[5])} ${a[0]} a=${a[4]}"; null }
            n.startsWith("draw") -> { log += n; null }
            else -> unexpected(n) } }
        // Run the painter's lambda once, as the node's onDrawWithContent does.
        scope.paint()
        return log
    }

    @Test fun `P5a - rectBand strokes the half-inset rect with the ceil'd width`() {
        // 2px on the 304x54 navbar: one Stroke(2) at (1,1) sized (302,52), default alpha / no filter / SrcOver (3).
        assertEquals(listOf("drawRect (1.0,1.0) (302.0,52.0) Stroke(2.0) $blue a=1.0 f=null b=3"),
            record(Size(304f, 54f), rectBand(bandGeometry(2f, false, Size(304f, 54f))!!, blue, Size(304f, 54f))))
    }

    @Test fun `P5b - rectBand fills the whole box from the origin when the bands meet`() {
        // 30px on 51x51 → stroke 26, fillArea: a Fill of (0,0)+(51,51), never at topLeft.
        assertEquals(listOf("drawRect (0.0,0.0) (51.0,51.0) Fill $blue a=1.0 f=null b=3"),
            record(Size(51f, 51f), rectBand(bandGeometry(30f, false, Size(51f, 51f))!!, blue, Size(51f, 51f))))
    }

    @Test fun `P5c - a simple radius at exactly half the stroke takes the shrunk-stroke branch`() {
        // r = 1 = half of 2: androidx's strict `<` keeps the stroke, radius shrunk to 0.
        val rr = RoundRect(0f, 0f, 100f, 50f, CornerRadius(1f))
        assertEquals(listOf("drawRoundRect (1.0,1.0) (98.0,48.0) r=(0.0,0.0) Stroke(2.0) $blue a=1.0"),
            record(Size(100f, 50f), roundedBand(bandGeometry(2f, false, Size(100f, 50f))!!, blue, rr)))
    }

    @Test fun `P5d - a simple radius under half the stroke fills with the inner rect clipped out`() {
        // r = 0.5 < 1: clipRect(2, 2, 98, 48, Difference), then the full rounded fill.
        val rr = RoundRect(0f, 0f, 100f, 50f, CornerRadius(0.5f))
        assertEquals(listOf("clipRect 2.0 2.0 98.0 48.0 op=0", "drawRoundRect (0.0,0.0) (100.0,50.0) r=(0.5,0.5) Fill $blue a=1.0"),
            record(Size(100f, 50f), roundedBand(bandGeometry(2f, false, Size(100f, 50f))!!, blue, rr)))
        // The bands meet (30px on 51x51): one filled rounded rect, the top-left radius kept.
        assertEquals(listOf("drawRoundRect (0.0,0.0) (51.0,51.0) r=(10.0,10.0) Fill $blue a=1.0"),
            record(Size(51f, 51f), roundedBand(bandGeometry(30f, false, Size(51f, 51f))!!, blue, RoundRect(0f, 0f, 51f, 51f, CornerRadius(10f)))))
    }

    @Test fun `P5e - the node drops its cached band on an input change, and only then`() {
        // A detached node (no layout, no draw): update() is the whole surface under test.
        val node = BehindContentBorderNode(2.dp, blue, RectangleShape)
        // The delegated CacheDrawModifierNodeImpl's validity flag (androidx 1.11.4 field `isCacheValid`).
        val cache = node.javaClass.getDeclaredField("cacheNode").apply { isAccessible = true }.get(node)
        val valid = cache.javaClass.getDeclaredField("isCacheValid").apply { isAccessible = true }
        // Same inputs: the built cache survives (androidx's setters skip equal values).
        valid.setBoolean(cache, true); node.update(2.dp, blue, RectangleShape)
        assertTrue("equal inputs keep the cached band", valid.getBoolean(cache))
        // A new width, brush or shape each drops it (the next draw rebuilds the band).
        for (change in listOf({ node.update(3.dp, blue, RectangleShape) }, { node.update(3.dp, SolidColor(Color.Red), RectangleShape) },
                { node.update(3.dp, SolidColor(Color.Red), CircleShape) })) {
            valid.setBoolean(cache, true); change()
            assertFalse("a changed input re-caches the band", valid.getBoolean(cache))
        }
    }

    @Test fun `P5f - source - the generic branch tints its alpha mask with the OPAQUE colour`() {
        // androidx drawGenericBorder: Alpha8 + ColorFilter.tint(value.copy(alpha = 1f)) for a SolidColor.
        val code = source("runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt")
        assertTrue("opaque tint", code.contains("solid?.let { ColorFilter.tint(it.value.copy(alpha = 1f)) }"))
        assertTrue("mask config", code.contains("if (solid != null) ImageBitmapConfig.Alpha8 else ImageBitmapConfig.Argb8888"))
    }

    /** A repo file, located by walking up from the test working dir. */
    private fun source(rel: String): String {
        // Gradle runs tests from the module dir; walk up to the repo root.
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        return File(requireNotNull(dir) { "repo root ($rel) not found" }, rel).readText()
    }
}
