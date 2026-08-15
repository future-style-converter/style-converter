package com.styleconverter.runtime.borders

// Wave 42 (lane W8) — pins for the border-radius CHILDREN-CLIP fix.
//
// The defect (wave-41 T5, WPT filter-effects/backdrop-filter-clip-rect.html,
// android-ref 0.8651): BorderRadiusApplier used `Modifier.clip`, which clips
// EVERYTHING inner to it — child composables included — while
// css-backgrounds-3 §4.3 only rounds the element's OWN background and
// border; descendants are clipped solely under overflow != visible
// (css-overflow-3 §3). Measured: the `.navbar`'s two red-bordered abspos
// `.menu` children carry 1552 red pixels in the Chromium ref and the iOS
// capture, and ZERO on Android.
//
// The fix is a MODE SPLIT decided by the pure truth table pinned here
// (selfPaintsWithoutClip): eligible elements shape their own paint with
// `background(color, shape)` + `border(w, c, shape)` and clip NOTHING;
// everything else (overflow clips, layered backgrounds, mixed borders)
// keeps the legacy clip byte-identically. JVM-only chain introspection, no
// Robolectric — the same foldIn technique as FilterGroupApplierTest.

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.borders.radius.BorderRadiusApplier
import com.styleconverter.runtime.borders.radius.BorderRadiusConfig
import com.styleconverter.runtime.borders.sides.AllBordersConfig
import com.styleconverter.runtime.borders.sides.BorderSideConfig
import com.styleconverter.runtime.core.types.ValueExtractors.LineStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BorderRadiusSelfPaintTest {

    // Chain-introspection helper: fold the modifier into concrete element
    // class names so install/skip decisions are assertable without drawing.
    private fun elementNames(modifier: Modifier): List<String> =
        modifier.foldIn(emptyList<String>()) { acc, element -> acc + element.javaClass.name }

    // The clip-rect navbar's radius: border-radius: 10px 20px 30px 40px —
    // four circular corners, no fractions.
    private val navbarRadius = BorderRadiusConfig(
        topStart = 10.dp to 10.dp, topEnd = 20.dp to 20.dp,
        bottomEnd = 30.dp to 30.dp, bottomStart = 40.dp to 40.dp,
    )

    // The clip-rect navbar's border: 2px solid blue on all four sides.
    private val uniformSolidBlue = run {
        val side = BorderSideConfig(width = 2.dp, color = Color.Blue, style = LineStyle.SOLID)
        AllBordersConfig(top = side, end = side, bottom = side, start = side)
    }

    // ── The truth table ──────────────────────────────────────────────────

    @Test
    fun `clip-rect navbar shape self-paints - radius plus uniform solid border`() {
        // The measured WPT case: radius + uniform solid border, overflow
        // visible, no background layers → must take the non-clipping mode.
        assertTrue(
            BorderRadiusApplier.selfPaintsWithoutClip(
                radius = navbarRadius, sides = uniformSolidBlue,
                overflowClips = false, hasBackgroundLayers = false, hasClipInsets = false, hasPartialOpacity = false,
            )
        )
    }

    @Test
    fun `borderless radius self-paints too`() {
        // Radius with no border at all (plain rounded card) — nothing the
        // legacy clip contributed except the child cut; self-paint wins.
        assertTrue(
            BorderRadiusApplier.selfPaintsWithoutClip(
                radius = navbarRadius, sides = AllBordersConfig(),
                overflowClips = false, hasBackgroundLayers = false, hasClipInsets = false, hasPartialOpacity = false,
            )
        )
    }

    @Test
    fun `no radius never self-paints`() {
        // Zero radius → neither mode installs; the gate must say false so
        // the caller stays on the (no-op) legacy path.
        assertFalse(
            BorderRadiusApplier.selfPaintsWithoutClip(
                radius = BorderRadiusConfig.NONE, sides = uniformSolidBlue,
                overflowClips = false, hasBackgroundLayers = false, hasClipInsets = false, hasPartialOpacity = false,
            )
        )
    }

    @Test
    fun `overflow clip keeps the legacy children-clipping mode`() {
        // overflow: hidden + radius — css-overflow-3 §3 really does clip
        // the children at the rounded box; the legacy clip IS the spec.
        assertFalse(
            BorderRadiusApplier.selfPaintsWithoutClip(
                radius = navbarRadius, sides = uniformSolidBlue,
                overflowClips = true, hasBackgroundLayers = false, hasClipInsets = false, hasPartialOpacity = false,
            )
        )
    }

    @Test
    fun `background layers and clip insets keep the legacy mode`() {
        // Gradient/url layers paint as rectangles in ColorApplier — only
        // the clip rounds them today, so self-paint must decline.
        assertFalse(
            BorderRadiusApplier.selfPaintsWithoutClip(
                radius = navbarRadius, sides = AllBordersConfig(),
                overflowClips = false, hasBackgroundLayers = true, hasClipInsets = false, hasPartialOpacity = false,
            )
        )
        // Same for background-clip padding/content-box insets.
        assertFalse(
            BorderRadiusApplier.selfPaintsWithoutClip(
                radius = navbarRadius, sides = AllBordersConfig(),
                overflowClips = false, hasBackgroundLayers = false, hasClipInsets = true, hasPartialOpacity = false,
            )
        )
    }

    @Test
    fun `mixed border styles keep the legacy mode`() {
        // A dashed border (or any non-uniform combination) is not
        // expressible by Modifier.border — must keep the legacy clip.
        val dashed = BorderSideConfig(width = 2.dp, color = Color.Blue, style = LineStyle.DASHED)
        val sides = AllBordersConfig(top = dashed, end = dashed, bottom = dashed, start = dashed)
        assertFalse(
            BorderRadiusApplier.selfPaintsWithoutClip(
                radius = navbarRadius, sides = sides,
                overflowClips = false, hasBackgroundLayers = false, hasClipInsets = false, hasPartialOpacity = false,
            )
        )
        // Per-side width mismatch breaks uniformity the same way.
        val thick = BorderSideConfig(width = 4.dp, color = Color.Blue, style = LineStyle.SOLID)
        val mixed = uniformSolidBlue.copy(top = thick)
        assertFalse(BorderRadiusApplier.isUniformSolid(mixed))
    }

    @Test
    fun `partial opacity keeps the legacy mode`() {
        // opacity < 1: ColorApplier's alpha layer (its step 1) must wrap
        // the background fill (its step 2). Self-paint hoists the fill
        // OUTER of ColorApplier, so the gate must decline — otherwise a
        // radius+bg+opacity element paints its fill at full strength.
        assertFalse(
            BorderRadiusApplier.selfPaintsWithoutClip(
                radius = navbarRadius, sides = uniformSolidBlue,
                overflowClips = false, hasBackgroundLayers = false, hasClipInsets = false,
                hasPartialOpacity = true,
            )
        )
    }

    // ── The installed chains ─────────────────────────────────────────────

    @Test
    fun `self-paint installs shaped background and border and NO clip layer`() {
        // The non-clipping mode's whole point: a Background element and a
        // Border element (both Shape-aware), and no graphicsLayer clip that
        // would cut children.
        val names = elementNames(
            BorderRadiusApplier.applySelfPaint(
                Modifier, navbarRadius, uniformSolidBlue, backgroundColor = Color.Green,
            )
        )
        assertTrue("expected a background element in $names", names.any { it.contains("Background") })
        assertTrue("expected a border element in $names", names.any { it.contains("Border") })
        assertFalse("no clip layer may install in $names", names.any { it.contains("GraphicsLayer") })
    }

    @Test
    fun `self-paint without background installs only the border band`() {
        // The clip-rect navbar exactly: border + radius, background: none.
        val names = elementNames(
            BorderRadiusApplier.applySelfPaint(
                Modifier, navbarRadius, uniformSolidBlue, backgroundColor = null,
            )
        )
        assertFalse("no background may install in $names", names.any { it.contains("Background") })
        assertTrue("expected a border element in $names", names.any { it.contains("Border") })
    }

    // ── End-to-end reachability on the measured WPT wire ─────────────────

    @Test
    fun `clip-rect navbar wire reaches self-paint through the real extractors`() {
        // The EXACT declaration list of `.navbar` (wave41-final
        // per-test-ir/wpt__filter-effects__backdrop-filter-clip-rect.json),
        // decoded by the SAME extractors StyleApplier's gate reads — so
        // this pins that the fix actually engages for the measured test,
        // not just for hand-built configs.
        val pairs = listOf(
            "Position" to "\"ABSOLUTE\"",
            "Width" to """{"type":"length","px":300}""",
            "Height" to """{"type":"length","px":50}""",
            "BorderTopWidth" to """{"px":2}""",
            "BorderRightWidth" to """{"px":2}""",
            "BorderBottomWidth" to """{"px":2}""",
            "BorderLeftWidth" to """{"px":2}""",
            "BorderTopStyle" to "\"SOLID\"",
            "BorderRightStyle" to "\"SOLID\"",
            "BorderBottomStyle" to "\"SOLID\"",
            "BorderLeftStyle" to "\"SOLID\"",
            "BorderTopColor" to """{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}""",
            "BorderRightColor" to """{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}""",
            "BorderBottomColor" to """{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}""",
            "BorderLeftColor" to """{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}""",
            "BorderTopLeftRadius" to """{"px":10}""",
            "BorderTopRightRadius" to """{"px":20}""",
            "BorderBottomRightRadius" to """{"px":30}""",
            "BorderBottomLeftRadius" to """{"px":40}""",
        ).map { (t, j) -> t to kotlinx.serialization.json.Json.parseToJsonElement(j) as kotlinx.serialization.json.JsonElement? }
        // The same three config lanes StyleApplier's branch reads.
        val borders = BordersFacade.extractConfig(pairs)
        val overflow = com.styleconverter.runtime.scrolling.OverflowExtractor
            .extractOverflowConfig(pairs)
        val colors = com.styleconverter.runtime.color.ColorExtractor
            .extractColorConfig(pairs)
        // Radius decoded, border uniform-solid — the gate must fire.
        assertTrue(borders.radius.hasRadius)
        assertTrue(BorderRadiusApplier.isUniformSolid(borders.sides))
        assertTrue(
            BorderRadiusApplier.selfPaintsWithoutClip(
                radius = borders.radius,
                sides = borders.sides,
                overflowClips = overflow.shouldClip,
                hasBackgroundLayers = colors.backgroundImages.isNotEmpty(),
                hasClipInsets = colors.backgroundClipInsets != null,
                // The navbar wire declares no Opacity — the same resolve
                // StyleApplier's gate performs.
                hasPartialOpacity = (colors.opacity ?: 1f) < 1f,
            )
        )
    }

    @Test
    fun `legacy applyRadius still installs the clipping layer`() {
        // The fallback path must stay byte-compatible: Modifier.clip is a
        // graphicsLayer(shape, clip = true) element.
        val names = elementNames(BorderRadiusApplier.applyRadius(Modifier, navbarRadius))
        assertTrue("expected the clip graphicsLayer in $names", names.any { it.contains("GraphicsLayer") })
        // And the zero-radius fast path installs nothing at all.
        assertEquals(emptyList<String>(), elementNames(
            BorderRadiusApplier.applyRadius(Modifier, BorderRadiusConfig.NONE)
        ))
    }
}
