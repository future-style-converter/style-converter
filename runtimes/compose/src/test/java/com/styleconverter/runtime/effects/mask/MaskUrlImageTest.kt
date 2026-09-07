package com.styleconverter.runtime.effects.mask

// Lane UM-ANDROID pins for the url() mask Modifier path.
//
// The diagnosed hole: MaskApplier.applyMask's url branch returned the
// modifier UNCHANGED ("use MaskedBox instead") while EffectsFacade — the
// only caller — called applyMask exclusively, so the Coil-based MaskedBox
// was dead code and every url() mask silently rendered unmasked. The fix
// routes `data:` URIs through a SYNCHRONOUS decode (DataUri bytes →
// BitmapFactory, cached) into a graphicsLayer(Offscreen) + drawWithContent
// DstIn composite; remote http(s) stays a documented no-op, logged once.
//
// What is pinned here (plain JVM — android.graphics is a throwing stub,
// this repo runs no Robolectric, so the BitmapFactory step is swapped
// through MaskApplier.rasterMaskDecoder, an internal test seam):
//  1. decode path: a percent-encoded base64 PNG data URI reaches the
//     raster decoder as the EXACT PNG bytes (%xx-unescape + strict
//     base64 — DataUri's rules) and yields a non-null ImageBitmap.
//  2. caching: repeated lookups decode ONCE and return the same instance
//     (first-frame capture determinism needs a stable bitmap).
//  3. routing: a data-URI url mask is NO LONGER the identity modifier —
//     it builds the same Offscreen + drawWithContent chain as gradient
//     masks; http and malformed payloads STAY identity (+ one log).
//  4. mask-repeat → BackgroundTileMath axis modes (css-masking-1 §7.3
//     defers placement to css-backgrounds-3 §2.4, one shared owner).

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.styleconverter.runtime.color.AxisRepeat
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MaskUrlImageTest {

    // A REAL 2×1 RGBA PNG (one red px, one transparent px), generated and
    // round-trip-verified offline — small enough to read, real enough
    // that the signature assertion below means something.
    private val pngBase64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAIAAAABCAYAAAD0In+KAAAAD0lEQVR4nGP4z8DwnwEIAAz8Af9rOp6TAAAAAElFTkSuQmCC"

    // The same payload with RFC 2397 %xx escapes: the leading 'i' as %69
    // and every '+' as %2B — DataUri must unescape BEFORE base64-decoding
    // (percent escapes are legal inside base64 payloads, RFC 2397 §3).
    private val pngBase64PercentEncoded =
        "%69VBORw0KGgoAAAANSUhEUgAAAAIAAAABCAYAAAD0In%2BKAAAAD0lEQVR4nGP4z8DwnwEIAAz8Af9rOp6TAAAAAElFTkSuQmCC"

    /** The full percent-encoded data URI under test. */
    private val pngDataUri = "data:image/png;base64,$pngBase64PercentEncoded"

    /** JVM stand-in for the platform bitmap — ImageBitmap is an interface,
     *  so the seam can hand back a pure-Kotlin fake off-device. */
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
        savedDecoder = MaskApplier.rasterMaskDecoder
        // Known-empty cache + warn-once guard per test case.
        MaskApplier.resetUrlMaskStateForTest()
    }

    @After
    fun restoreSeam() {
        // Never leak a fake decoder into other suites.
        MaskApplier.rasterMaskDecoder = savedDecoder
        MaskApplier.resetUrlMaskStateForTest()
    }

    /** Fold the modifier chain to element class names — the same
     *  renderer-less strategy ShadowApplierTest pins draw routing with. */
    private fun elementNames(modifier: Modifier): List<String> =
        modifier.foldIn(emptyList<String>()) { acc, element -> acc + element.javaClass.name }

    // ── 1. decode path ─────────────────────────────────────────────────

    @Test
    fun `percent-encoded PNG data URI decodes to a non-null ImageBitmap`() {
        // Capture exactly what the pipeline hands the raster decoder.
        var received: ByteArray? = null
        val fake = FakeImageBitmap()
        MaskApplier.rasterMaskDecoder = { bytes -> received = bytes; fake }

        // The full %xx → base64 → bytes → bitmap pipeline, synchronously.
        val bitmap = MaskApplier.decodeDataUriMask(pngDataUri)
        assertNotNull("data: URI must decode synchronously", bitmap)
        assertSame("the seam's bitmap must surface unwrapped", fake, bitmap)

        // The decoder must see the EXACT bytes of the un-escaped payload —
        // byte-compare against an independent strict base64 decode.
        val expected = java.util.Base64.getDecoder().decode(pngBase64)
        assertArrayEquals("payload bytes corrupted in transit", expected, received)
        // And those bytes are a real PNG: the 8-byte signature
        // (\x89 P N G \r \n \x1a \n) proves the fixture is honest.
        val magic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertArrayEquals("not a PNG payload", magic, received!!.copyOfRange(0, 8))
    }

    @Test
    fun `malformed base64 payload decodes to null`() {
        // '!' is outside the base64 alphabet — DataUri decodes STRICTLY
        // (lenient decoding would render a wrong image instead of failing
        // visibly), so the pipeline must return null before the seam.
        var decoderCalled = false
        MaskApplier.rasterMaskDecoder = { decoderCalled = true; FakeImageBitmap() }
        assertNull(MaskApplier.decodeDataUriMask("data:image/png;base64,!!!not-base64!!!"))
        assertTrue("raster decoder must not see malformed payloads", !decoderCalled)
    }

    // ── 2. caching ─────────────────────────────────────────────────────

    @Test
    fun `decoded mask is cached — one decode, same instance`() {
        // Count decoder invocations across repeated lookups.
        var decodes = 0
        MaskApplier.rasterMaskDecoder = { decodes++; FakeImageBitmap() }
        val first = MaskApplier.decodeDataUriMask(pngDataUri)
        val second = MaskApplier.decodeDataUriMask(pngDataUri)
        // Same instance both times (capture determinism), decoded once.
        assertSame(first, second)
        assertEquals("cache miss on the second lookup", 1, decodes)
    }

    // ── 3. routing ─────────────────────────────────────────────────────

    @Test
    fun `url mask with data payload is no longer the identity modifier`() {
        // The dead-code regression this lane fixes: applyMask used to
        // return the modifier unchanged for EVERY url() mask.
        MaskApplier.rasterMaskDecoder = { FakeImageBitmap() }
        val config = MaskConfig(hasImage = true, imageUrl = pngDataUri)
        val names = elementNames(MaskApplier.applyMask(Modifier, config))
        // Exactly the gradient-mask chain: one graphics layer (Offscreen
        // flatten) + one draw-with-content (DstIn composite).
        assertEquals(names.joinToString(), 2, names.size)
        assertTrue("missing Offscreen flatten layer: $names", names.any { it.contains("GraphicsLayer") })
        assertTrue("missing DstIn draw pass: $names", names.any { it.contains("DrawWithContent") })
    }

    @Test
    fun `http url mask stays the identity modifier`() {
        // Remote fetch is async ⇒ capture-nondeterministic; the DOCUMENTED
        // behavior is unmasked + one warning (warnOnce logs via IRLog,
        // which prints to stdout on the JVM — no android.util.Log crash).
        val config = MaskConfig(hasImage = true, imageUrl = "https://example.com/mask.png")
        val base = Modifier
        // assertSame: identity means the SAME instance, not an empty chain.
        assertSame(base, MaskApplier.applyMask(base, config))
    }

    @Test
    fun `undecodable data URI stays the identity modifier`() {
        // Decodable-looking URI whose bytes the platform rejects (decoder
        // returns null, as BitmapFactory does for garbage): browsers drop
        // the failed mask layer and render unmasked — so identity here.
        MaskApplier.rasterMaskDecoder = { null }
        val config = MaskConfig(hasImage = true, imageUrl = pngDataUri)
        val base = Modifier
        assertSame(base, MaskApplier.applyMask(base, config))
    }

    // ── 4. repeat mapping ──────────────────────────────────────────────

    @Test
    fun `mask-repeat maps onto the shared background axis modes`() {
        // css-masking-1 §7.3 = css-backgrounds-3 §2.4 semantics: the initial
        // `repeat` tiles BOTH axes (what the default-config fixture needs),
        // and every other keyword maps per-axis onto the SAME AxisRepeat
        // owner the background path draws with.
        assertEquals(AxisRepeat.REPEAT to AxisRepeat.REPEAT, MaskApplier.maskRepeatAxes(MaskRepeatValue.REPEAT))
        assertEquals(AxisRepeat.NO_REPEAT to AxisRepeat.NO_REPEAT, MaskApplier.maskRepeatAxes(MaskRepeatValue.NO_REPEAT))
        assertEquals(AxisRepeat.REPEAT to AxisRepeat.NO_REPEAT, MaskApplier.maskRepeatAxes(MaskRepeatValue.REPEAT_X))
        assertEquals(AxisRepeat.NO_REPEAT to AxisRepeat.REPEAT, MaskApplier.maskRepeatAxes(MaskRepeatValue.REPEAT_Y))
        assertEquals(AxisRepeat.SPACE to AxisRepeat.SPACE, MaskApplier.maskRepeatAxes(MaskRepeatValue.SPACE))
        assertEquals(AxisRepeat.ROUND to AxisRepeat.ROUND, MaskApplier.maskRepeatAxes(MaskRepeatValue.ROUND))
    }
}
