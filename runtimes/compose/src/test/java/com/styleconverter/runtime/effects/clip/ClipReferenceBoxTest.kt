package com.styleconverter.runtime.effects.clip

// Wave 46 (lane Y4) — JVM pins for css-masking-1 §5.1 reference-box
// resolution on Android (ClipReferenceBox + the extractor's geometry-box
// forms). Every expected number is taken from a wave45-final css-masking
// WPT test whose PNG showed the defect:
//   - contentBox-1a: `position:absolute; left/top:10px; width/height:100px;
//     padding:40px; clip-path: circle(farthest-side) content-box` rendered
//     the whole 180px square on Android (the geometry-box wire never
//     reached the applier) and a 180px circle on iOS (box ignored).
//   - marginBox-1b/1c/1d: `clip-path: margin-box` + a 200px outline flooded
//     291px of green on both natives; the margin box must cut it, with the
//     css-shapes-1 §4 corner rule (1d: 10 + 50·(1 + (0.2 − 1)³) = 34.4).
//   - ellipse-006: `margin:50px` — the node is the MARGIN box on Android
//     (step 3 clips outside step 4's absolutePadding), so `ellipse()`
//     resolved rx against 250px instead of 150px.
//   - inset-round-percent: `inset(80% 0 0 round 8%)` clipped nothing (a
//     percent side read as 0 px).
// Pure geometry — no Robolectric (the suite's standing constraint):
// Rect / CornerRadius / Density are plain Kotlin.

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.spacing.CollapsedMargin
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipReferenceBoxTest {

    private val density = Density(1f)
    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun px(v: Int) = parse("""{"px":$v}""")

    // The exact contentBox-1a wire minus colour (per-test-ir, wave45-final).
    private val contentBox1a = listOf(
        "Position" to parse("\"ABSOLUTE\""),
        "Left" to px(10), "Top" to px(10),
        "Width" to parse("""{"type":"length","px":100}"""),
        "Height" to parse("""{"type":"length","px":100}"""),
        "PaddingTop" to px(40), "PaddingRight" to px(40), "PaddingBottom" to px(40), "PaddingLeft" to px(40),
        "ClipPath" to parse("""{"geometry-box":"content-box","shape":{"type":"circle","r":"farthest-side"}}"""),
    )

    @Test
    fun `geometry-box plus shape wire unwraps the shape and keeps the box`() {
        val cfg = ClipPathExtractor.extractClipPathConfig(contentBox1a)
        val circle = cfg.shape as ClipShape.Circle
        assertEquals(ClipRadius.FarthestSide, circle.radius)
        assertEquals(ClipGeometryBox.CONTENT_BOX, cfg.geometryBox)
        // Box metrics came along: padding 40 each side, position offset 10/10.
        assertEquals(40f, cfg.box.paddings.top.value, 0.001f)
        assertEquals(10f, cfg.box.positionOffset.x.value, 0.001f)
        assertNull(cfg.box.margin)
    }

    @Test
    fun `content box of contentBox-1a is the 100px square at 50,50 inside the node`() {
        // Under WPT content-box sizing the node is the 180px border box at
        // the canvas origin with the paint slid by the (10,10) inset.
        val cfg = ClipPathExtractor.extractClipPathConfig(contentBox1a)
        val frame = ClipReferenceBox.resolve(
            cfg.box, cfg.geometryBox, ClipReferenceBox.nodeInsets(cfg.box, null),
            Size(180f, 180f), density,
        )
        assertEquals(50f, frame.rect.left, 0.001f)
        assertEquals(50f, frame.rect.top, 0.001f)
        assertEquals(100f, frame.rect.width, 0.001f)
        assertEquals(100f, frame.rect.height, 0.001f)
    }

    @Test
    fun `bare geometry-box wire yields the ReferenceBox shape`() {
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf("ClipPath" to parse("""{"geometry-box":"margin-box"}"""))
        )
        assertEquals(ClipShape.ReferenceBox, cfg.shape)
        assertEquals(ClipGeometryBox.MARGIN_BOX, cfg.geometryBox)
        assertTrue(cfg.hasClipPath)
    }

    @Test
    fun `svg-only keywords take the spec used value for a CSS box`() {
        fun box(kw: String) = ClipPathExtractor.extractClipPathConfig(
            listOf("ClipPath" to parse("""{"geometry-box":"$kw"}"""))
        ).geometryBox
        // css-masking-1 §5.1: fill-box → content-box; stroke/view → border.
        assertEquals(ClipGeometryBox.CONTENT_BOX, box("fill-box"))
        assertEquals(ClipGeometryBox.BORDER_BOX, box("stroke-box"))
        assertEquals(ClipGeometryBox.BORDER_BOX, box("view-box"))
        assertEquals(ClipGeometryBox.PADDING_BOX, box("padding-box"))
    }

    // marginBox-1b: 50×50, margin 25, `clip-path: margin-box`.
    private val marginBox1b = listOf(
        "Width" to parse("""{"type":"length","px":50}"""),
        "Height" to parse("""{"type":"length","px":50}"""),
        "ClipPath" to parse("""{"geometry-box":"margin-box"}"""),
        "OutlineWidth" to parse("""{"type":"length","px":200}"""),
        "MarginTop" to px(25), "MarginRight" to px(25), "MarginBottom" to px(25), "MarginLeft" to px(25),
    )

    @Test
    fun `margin box spans the whole node when the bands are padded in`() {
        // No collapse override: all four 25px bands are node padding, so
        // the node is 100×100 and the margin box IS the node.
        val cfg = ClipPathExtractor.extractClipPathConfig(marginBox1b)
        val frame = ClipReferenceBox.resolve(
            cfg.box, cfg.geometryBox, ClipReferenceBox.nodeInsets(cfg.box, null),
            Size(100f, 100f), density,
        )
        assertEquals(0f, frame.rect.left, 0.001f)
        assertEquals(0f, frame.rect.top, 0.001f)
        assertEquals(100f, frame.rect.right, 0.001f)
        assertEquals(100f, frame.rect.bottom, 0.001f)
        // Square corners stay square: css-shapes-1 §4 gives 0 + 25·(1 + (−1)³) = 0.
        assertEquals(0f, frame.topLeft.x, 0.001f)
    }

    @Test
    fun `root block-margin strip moves the border box but not the margin box`() {
        // The harness hands a root CollapsedMargin(0,0): its vertical bands
        // live in gap spacers, so the node is 100×50 with the border box at
        // y=0 — and the element's OWN margin box still reaches 25px above.
        val cfg = ClipPathExtractor.extractClipPathConfig(marginBox1b)
        val insets = ClipReferenceBox.nodeInsets(cfg.box, CollapsedMargin(0f, 0f))
        assertEquals(0f, insets.bands.top.value, 0.001f)
        assertEquals(25f, insets.bands.left.value, 0.001f)
        val border = ClipReferenceBox.resolve(cfg.box, ClipGeometryBox.BORDER_BOX, insets, Size(100f, 50f), density)
        assertEquals(25f, border.rect.left, 0.001f)
        assertEquals(0f, border.rect.top, 0.001f)
        assertEquals(50f, border.rect.height, 0.001f)
        val margin = ClipReferenceBox.resolve(cfg.box, ClipGeometryBox.MARGIN_BOX, insets, Size(100f, 50f), density)
        assertEquals(-25f, margin.rect.top, 0.001f)
        assertEquals(75f, margin.rect.bottom, 0.001f)
    }

    @Test
    fun `margin box corner rule - marginBox-1d gives 34 point 4 and 1c a full circle`() {
        // 1d: border-radius 10, margin 50 → 10 + 50·(1 + (0.2 − 1)³) = 34.4.
        assertEquals(34.4f, ClipReferenceBox.marginOutsetRadius(10f, 50f), 0.01f)
        // 1c: border-radius 50 on a 50px box is overlap-scaled to 25
        // (css-backgrounds-3 §4.1), then ratio 25/25 = 1 → 25 + 25 = 50: the
        // 100px margin box becomes a full circle.
        assertEquals(50f, ClipReferenceBox.marginOutsetRadius(25f, 25f), 0.001f)
        // Negative margin: plain r + m, floored at 0.
        assertEquals(5f, ClipReferenceBox.marginOutsetRadius(10f, -5f), 0.001f)
        assertEquals(0f, ClipReferenceBox.marginOutsetRadius(2f, -5f), 0.001f)
    }

    @Test
    fun `overlap scaling then outset reproduces marginBox-1c end to end`() {
        val cfg = ClipPathExtractor.extractClipPathConfig(
            marginBox1b + listOf(
                "BorderTopLeftRadius" to px(50), "BorderTopRightRadius" to px(50),
                "BorderBottomRightRadius" to px(50), "BorderBottomLeftRadius" to px(50),
            )
        )
        val frame = ClipReferenceBox.resolve(
            cfg.box, cfg.geometryBox, ClipReferenceBox.nodeInsets(cfg.box, null),
            Size(100f, 100f), density,
        )
        assertEquals(50f, frame.topLeft.x, 0.001f)
        assertEquals(50f, frame.bottomRight.y, 0.001f)
    }

    @Test
    fun `padding and content boxes inset the radii by border then padding`() {
        // contentBox-1e: padding 4, border 4, border-radius 58 → content
        // box radius 58 − 4 − 4 = 50 on the 100px content box: a circle.
        val props = listOf(
            "ClipPath" to parse("""{"geometry-box":"content-box"}"""),
            "PaddingTop" to px(4), "PaddingRight" to px(4), "PaddingBottom" to px(4), "PaddingLeft" to px(4),
            "BorderTopWidth" to px(4), "BorderRightWidth" to px(4), "BorderBottomWidth" to px(4), "BorderLeftWidth" to px(4),
            "BorderTopStyle" to parse("\"SOLID\""), "BorderRightStyle" to parse("\"SOLID\""),
            "BorderBottomStyle" to parse("\"SOLID\""), "BorderLeftStyle" to parse("\"SOLID\""),
            "BorderTopLeftRadius" to px(58), "BorderTopRightRadius" to px(58),
            "BorderBottomRightRadius" to px(58), "BorderBottomLeftRadius" to px(58),
        )
        val cfg = ClipPathExtractor.extractClipPathConfig(props)
        val insets = ClipReferenceBox.nodeInsets(cfg.box, null)
        val padding = ClipReferenceBox.resolve(cfg.box, ClipGeometryBox.PADDING_BOX, insets, Size(116f, 116f), density)
        assertEquals(4f, padding.rect.left, 0.001f)
        assertEquals(108f, padding.rect.width, 0.001f)
        assertEquals(54f, padding.topLeft.x, 0.001f)
        val content = ClipReferenceBox.resolve(cfg.box, ClipGeometryBox.CONTENT_BOX, insets, Size(116f, 116f), density)
        assertEquals(8f, content.rect.left, 0.001f)
        assertEquals(100f, content.rect.width, 0.001f)
        assertEquals(50f, content.topLeft.y, 0.001f)
    }

    @Test
    fun `a none style border has zero used width`() {
        // CSS2 §8.5.3: border-style none → used width 0, so the padding box
        // equals the border box even with a declared width.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(
                "ClipPath" to parse("""{"geometry-box":"padding-box"}"""),
                "BorderTopWidth" to px(8), "BorderTopStyle" to parse("\"NONE\""),
            )
        )
        assertEquals(0f, cfg.box.borderWidths.top.value, 0.001f)
    }

    @Test
    fun `ellipse-006 - margin bands shrink the border box to 150x100`() {
        // `width:150; height:100; margin:50` — horizontal bands padded into
        // the node (250 wide), the root strip removes the vertical ones.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(
                "Width" to parse("""{"type":"length","px":150}"""),
                "Height" to parse("""{"type":"length","px":100}"""),
                "Position" to parse("\"ABSOLUTE\""),
                "MarginTop" to px(50), "MarginRight" to px(50), "MarginBottom" to px(50), "MarginLeft" to px(50),
                "ClipPath" to parse("""{"type":"ellipse"}"""),
            )
        )
        val frame = ClipReferenceBox.resolve(
            cfg.box, cfg.geometryBox, ClipReferenceBox.nodeInsets(cfg.box, CollapsedMargin(0f, 0f)),
            Size(250f, 100f), density,
        )
        assertEquals(50f, frame.rect.left, 0.001f)
        assertEquals(150f, frame.rect.width, 0.001f)
        assertEquals(100f, frame.rect.height, 0.001f)
    }

    @Test
    fun `no metrics keeps every box equal to the node rect`() {
        // The byte-stability guarantee for the committed 047-050 baselines.
        for (box in ClipGeometryBox.entries) {
            val f = ClipReferenceBox.resolve(
                ClipBoxGeometry.NONE, box, ClipNodeInsets.NONE, Size(120f, 80f), density,
            )
            assertEquals(0f, f.rect.left, 0f)
            assertEquals(0f, f.rect.top, 0f)
            assertEquals(120f, f.rect.right, 0f)
            assertEquals(80f, f.rect.bottom, 0f)
            assertEquals(0f, f.topLeft.x, 0f)
        }
    }

    @Test
    fun `negative margin remainder shifts the border box like Modifier offset`() {
        // margin-left: -10px: no band, a −10 px translation of the paint.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(
                "MarginLeft" to px(-10),
                "ClipPath" to parse("""{"type":"circle"}"""),
            )
        )
        val insets = ClipReferenceBox.nodeInsets(cfg.box, null)
        assertEquals(0f, insets.bands.left.value, 0.001f)
        assertEquals(-10f, insets.shiftX.value, 0.001f)
    }

    @Test
    fun `inset percent sides surface as fractions`() {
        // inset-round-percent: `inset(80% 0 0 round 8%)`.
        val shape = ClipPathExtractor.extractClipPathConfig(
            listOf(
                "ClipPath" to parse(
                    """{"type":"inset","t":{"original":{"v":80,"u":"PERCENT"}},"r":{"px":0},"b":{"px":0},"l":{"px":0},"round":{"original":{"v":8,"u":"PERCENT"}}}"""
                )
            )
        ).shape as ClipShape.Inset
        assertEquals(0.8f, shape.topFraction!!, 0.0001f)
        assertNull(shape.rightFraction)
        assertEquals(0f, shape.top.value, 0.001f)
        assertEquals(0.08f, shape.borderRadiusFraction!!, 0.0001f)
    }
}
