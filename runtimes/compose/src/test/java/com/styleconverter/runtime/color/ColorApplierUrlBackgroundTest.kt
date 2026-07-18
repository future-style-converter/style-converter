package com.styleconverter.runtime.color

// Wave-9 pins for the url() BACKGROUND layer path — the LIVE-path mirror
// of the wave-3 url() mask fix.
//
// The diagnosed hole: ColorApplier.applyBackgroundImage mapped
// BackgroundImageConfig.Url → null brush and silently returned the
// modifier unchanged (no log — a house-rule violation), while the
// Coil-based background/BackgroundImageRenderer was dead code with zero
// call sites (deleted with this fix). The repair routes `data:` URIs
// through the SHARED synchronous decode (SyncImageDecode — the same
// DataUri → BitmapFactory → LRU pipeline MaskApplier pinned) into a
// drawBehind tile lattice built from the EXISTING planTilePass /
// BackgroundTileMath plans.
//
// What is pinned here (plain JVM — android.graphics is a throwing stub,
// so the BitmapFactory step swaps through SyncImageDecode.rasterDecoder):
//  1. routing: a data-URI url background is NO LONGER the identity
//     modifier (a DrawBehind element appears); remote http and
//     undecodable payloads STAY identity (+ one log, not silent).
//  2. decode seam sharing: the mask-facing seam (rasterMaskDecoder) and
//     the background path read the SAME SyncImageDecode pipeline — one
//     decode, one cache, by construction.
//  3. tile geometry: css-backgrounds-3 §3.9 size resolution (auto =
//     natural px, cover/contain scales, one-auto-axis keeps the intrinsic
//     ratio) and the §3.6 anchor (free-space × fraction + px offset,
//     unclamped) — the pure halves the drawBehind lambda consumes.

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.images.SyncImageDecode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ColorApplierUrlBackgroundTest {

    // The same real 2×1 RGBA PNG data URI MaskUrlImageTest uses (one red
    // px, one transparent px) — a genuinely decodable payload.
    private val pngDataUri = "data:image/png;base64," +
        "iVBORw0KGgoAAAANSUhEUgAAAAIAAAABCAYAAAD0In+KAAAAD0lEQVR4nGP4z8DwnwEIAAz8Af9rOp6TAAAAAElFTkSuQmCC"

    /** JVM stand-in for the platform bitmap (ImageBitmap is an interface,
     *  so the seam hands back a pure-Kotlin fake off-device). */
    private class FakeImageBitmap(
        override val width: Int = 2,
        override val height: Int = 1
    ) : ImageBitmap {
        // sRGB + ARGB8888: the values BitmapFactory would report.
        override val colorSpace: ColorSpace = ColorSpaces.Srgb
        override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
        override val hasAlpha: Boolean = true
        // Draw-side methods are never called on the JVM.
        override fun prepareToDraw() = Unit
        override fun readPixels(
            buffer: IntArray,
            startX: Int,
            startY: Int,
            width: Int,
            height: Int,
            bufferOffset: Int,
            stride: Int
        ) = Unit
    }

    /** The production decoder, saved so every test restores it. */
    private lateinit var savedDecoder: (ByteArray) -> ImageBitmap?

    @Before
    fun saveSeamAndReset() {
        // Snapshot the BitmapFactory-backed default before any swap.
        savedDecoder = SyncImageDecode.rasterDecoder
        // Known-empty cache + warn-once guard per test case.
        ColorApplier.resetUrlBackgroundStateForTest()
    }

    @After
    fun restoreSeam() {
        // Never leak a fake decoder into other suites.
        SyncImageDecode.rasterDecoder = savedDecoder
        ColorApplier.resetUrlBackgroundStateForTest()
    }

    /** Fold the modifier chain to element class names — the renderer-less
     *  routing strategy MaskUrlImageTest pins with. */
    private fun elementNames(modifier: Modifier): List<String> =
        modifier.foldIn(emptyList<String>()) { acc, element -> acc + element.javaClass.name }

    // ── 1. routing ─────────────────────────────────────────────────────

    @Test
    fun `data-URI url background is no longer the identity modifier`() {
        // The silent-no-op regression this lane fixes: applyColors used to
        // return the modifier unchanged for EVERY url() layer.
        SyncImageDecode.rasterDecoder = { FakeImageBitmap() }
        val config = ColorConfig(backgroundImages = listOf(BackgroundImageConfig.Url(pngDataUri)))
        val names = elementNames(ColorApplier.applyColors(Modifier, config))
        // Exactly one draw pass: the drawBehind tile lattice.
        assertEquals(names.joinToString(), 1, names.size)
        assertTrue("missing drawBehind tile pass: $names",
            names.any { it.contains("DrawBehind") || it.contains("DrawBackgroundModifier") })
    }

    @Test
    fun `http url background stays the identity modifier`() {
        // Remote fetch is async ⇒ capture-nondeterministic; the DOCUMENTED
        // behavior is layer-skipped + one warning (IRLog prints to stdout
        // on the JVM — no android.util.Log crash).
        val config = ColorConfig(backgroundImages = listOf(
            BackgroundImageConfig.Url("https://example.com/bg.png")))
        val base = Modifier
        // assertSame: identity means the SAME instance, not an empty chain.
        assertSame(base, ColorApplier.applyColors(base, config))
    }

    @Test
    fun `undecodable data URI stays the identity modifier`() {
        // Decodable-looking URI whose bytes the platform rejects (decoder
        // returns null, as BitmapFactory does for garbage): browsers render
        // a failed image layer as transparent — so identity here.
        SyncImageDecode.rasterDecoder = { null }
        val config = ColorConfig(backgroundImages = listOf(BackgroundImageConfig.Url(pngDataUri)))
        val base = Modifier
        assertSame(base, ColorApplier.applyColors(base, config))
    }

    // ── 2. decode seam sharing ─────────────────────────────────────────

    @Test
    fun `background and mask read the same decode pipeline`() {
        // One decode, one cache: the bitmap the background path gets IS
        // the instance the mask-facing entry point returns for the same
        // URL — SyncImageDecode is the single owner by construction.
        var decodes = 0
        SyncImageDecode.rasterDecoder = { decodes++; FakeImageBitmap() }
        val viaShared = SyncImageDecode.decodeDataUri(pngDataUri)
        val viaMaskName = com.styleconverter.runtime.effects.mask.MaskApplier
            .decodeDataUriMask(pngDataUri)
        assertSame("two caches would fork capture determinism", viaShared, viaMaskName)
        assertEquals("payload must decode exactly once across both paths", 1, decodes)
    }

    // ── 3. tile geometry (pure halves of the drawBehind pass) ──────────

    /** px==dp density-1 convention of the capture harness. */
    private val dpToPx: (androidx.compose.ui.unit.Dp) -> Float = { it.value }

    @Test
    fun `auto size keeps the natural tile`() {
        // §3.9 auto auto → intrinsic dimensions, unscaled.
        val tile = ColorApplier.imageTileSize(
            box = Size(200f, 100f), imageW = 16f, imageH = 8f,
            layerSize = BackgroundSizeConfig.Auto, dpToPx = dpToPx)
        assertEquals(Size(16f, 8f), tile)
    }

    @Test
    fun `cover and contain scale uniformly by the extreme ratio`() {
        // 16×8 image in a 200×100 box: both ratios are 12.5 — but in a
        // 200×50 box cover takes the max (12.5) and contain the min (6.25).
        val cover = ColorApplier.imageTileSize(
            Size(200f, 50f), 16f, 8f, BackgroundSizeConfig.Cover, dpToPx)
        assertEquals(Size(200f, 100f), cover)
        val contain = ColorApplier.imageTileSize(
            Size(200f, 50f), 16f, 8f, BackgroundSizeConfig.Contain, dpToPx)
        assertEquals(Size(100f, 50f), contain)
    }

    @Test
    fun `one explicit axis preserves the intrinsic ratio`() {
        // §3.9: `background-size: 80px auto` on a 2:1 image is 80×40 —
        // NOT 80 × natural height.
        val tile = ColorApplier.imageTileSize(
            Size(200f, 100f), 16f, 8f,
            BackgroundSizeConfig.Dimensions(width = 80.dp), dpToPx)
        assertEquals(Size(80f, 40f), tile)
        // Mirror on the height axis: `auto 50px` on 2:1 → 100×50.
        val tileH = ColorApplier.imageTileSize(
            Size(200f, 100f), 16f, 8f,
            BackgroundSizeConfig.Dimensions(height = 50.dp), dpToPx)
        assertEquals(Size(100f, 50f), tileH)
    }

    @Test
    fun `percent dimensions resolve against the box`() {
        // Dimensions contract: *Percent fields are 0..1 fractions of the
        // positioning area (both axes explicit → ratio may distort).
        val tile = ColorApplier.imageTileSize(
            Size(200f, 100f), 16f, 8f,
            BackgroundSizeConfig.Dimensions(widthPercent = 0.5f, heightPercent = 0.25f),
            dpToPx)
        assertEquals(Size(100f, 25f), tile)
    }

    @Test
    fun `right bottom anchors the tile at the free space`() {
        // The shorthand fixture (`background: … right bottom` → x=1, y=1):
        // §3.6 free-space × 1 end-aligns a 16×8 tile in 200×100 at (184, 92).
        val anchor = ColorApplier.tileAnchor(
            box = Size(200f, 100f), tile = Size(16f, 8f),
            pos = BackgroundPositionConfig(x = 1f, y = 1f), dpToPx = dpToPx)
        assertEquals(Offset(184f, 92f), anchor)
    }

    @Test
    fun `px offset adds to the fraction anchor and stays unclamped`() {
        // 20px raw edge offset (fraction 0) → anchor x = 20; and an
        // OVERSIZED tile (300 wide in a 200 box) at x=1 anchors at −100 so
        // its END edge aligns — the documented no-clamp rule.
        val offset = ColorApplier.tileAnchor(
            Size(200f, 100f), Size(16f, 8f),
            BackgroundPositionConfig(x = 0f, y = 0f, xOffset = 20.dp), dpToPx)
        assertEquals(Offset(20f, 0f), offset)
        val oversized = ColorApplier.tileAnchor(
            Size(200f, 100f), Size(300f, 8f),
            BackgroundPositionConfig(x = 1f, y = 0f), dpToPx)
        assertEquals(-100f, oversized.x)
    }

    @Test
    fun `natural tile plus default repeat plans a full lattice`() {
        // Tile-plan invocation end-to-end (pure): a 16×8 natural tile at
        // origin with the default repeat/repeat covers a 40×16 box with a
        // ceil(40/16)+overhang × ceil(16/8) grid — the SAME planTilePass
        // owner the gradient path draws with, no image-specific fork.
        val pass = ColorApplier.planTilePass(
            box = Size(40f, 16f), tileW = 16f, tileH = 8f,
            anchorX = 0f, anchorY = 0f,
            repeat = BackgroundRepeatAxes(AxisRepeat.REPEAT, AxisRepeat.REPEAT))
        // 3 columns (0, 16, 32) × 2 rows (0, 8) = 6 tiles, clipped later
        // to the border box by the draw pass.
        assertEquals(6, pass.origins.size)
        assertTrue("first tile anchors at the origin", pass.origins.contains(Offset(0f, 0f)))
    }
}
