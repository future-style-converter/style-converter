package com.styleconverter.runtime.color

// ColorApplierImageNotationTest — wave 49, lane A3: the css-images-4 §2.5
// CANDIDATE WALK, pinned on the Compose side. Byte-parallel twin:
// BackgroundImageNotationResolutionTests.swift.
//
// WHY THESE PINS EXIST. The wave-48 seam collapsed an image() value at
// EXTRACT time (colour if present, else srcs[0]) and therefore threw
// candidates 2..n away. WPT css-image-fallbacks-and-annotations003/004 open
// with `1x1-green.svg`, a path that does not exist beside those tests, so the
// only paintable candidate was one of the discarded ones — measured at the
// wave-48 gate as Android 0.9981 `colorFailed`, 17% of the canvas in pure red
// (255,0,0) where the reference is `green` (0,128,0). These tests pin the walk
// that fixes it AND the two outcomes that must NOT move.
//
// THE PAYLOADS ARE CORPUS BYTES, NOT INVENTIONS. GREEN_PNG_DATA_URI is the
// verbatim data: URI the converter already emits for
// css-images/support/1x1-green.png (it appears on the 005 wire — see
// tools/titan/runs/wave48-final/sections/css-images/per-test-ir/
// wpt__css-images__css-image-fallbacks-and-annotations005.json — and is
// byte-identical to percent-encoding that file). GREEN_GIF_DATA_URI is the
// same encoding of support/1x1-green.gif, 004's only resolvable candidate.
//
// PLAIN JVM: android.graphics is a throwing stub here, so the BitmapFactory
// step swaps through SyncImageDecode.rasterDecoder exactly as
// ColorApplierUrlBackgroundTest does. DataUri's %xx decode is REAL — only the
// bytes → ImageBitmap step is faked, so a malformed payload still declines
// through the production path.

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.styleconverter.runtime.core.images.SyncImageDecode
import com.styleconverter.runtime.images.ImageCandidateChain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ColorApplierImageNotationTest {

    private companion object {
        /** support/1x1-green.png as the percent-encoded data: URI the corpus
         *  wire already carries (verbatim from the 005 per-test IR). */
        const val GREEN_PNG_DATA_URI = "data:image/png,%89%50%4e%47%0d%0a%1a%0a%00%00%00%0d%49%48%44%52%00%00%00%01%00%00%00%01%01%03%00%00%00%25%db%56%ca%00%00%00%04%67%41%4d%41%00%00%af%c8%37%05%8a%e9%00%00%00%03%50%4c%54%45%00%80%00%9c%f9%a5%91%00%00%00%0a%49%44%41%54%78%da%63%60%00%00%00%02%00%01%e5%27%de%fc%00%00%00%19%74%45%58%74%53%6f%66%74%77%61%72%65%00%41%64%6f%62%65%20%49%6d%61%67%65%52%65%61%64%79%71%c9%65%3c%00%00%00%00%49%45%4e%44%ae%42%60%82"

        /** support/1x1-green.gif, same encoding — 004's last candidate. */
        const val GREEN_GIF_DATA_URI = "data:image/gif,%47%49%46%38%39%61%01%00%01%00%80%00%00%00%7f%00%00%00%00%21%f9%04%00%07%00%ff%00%2c%00%00%00%00%01%00%01%00%00%02%02%44%01%00%3b"
    }

    /** JVM stand-in for the platform bitmap (ImageBitmap is an interface, so
     *  the seam hands back a pure-Kotlin fake off-device) — the same shape
     *  ColorApplierUrlBackgroundTest uses. */
    private class FakeImageBitmap(
        override val width: Int = 1,
        override val height: Int = 1
    ) : ImageBitmap {
        override val colorSpace: ColorSpace = ColorSpaces.Srgb
        override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
        override val hasAlpha: Boolean = true
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

    private lateinit var savedDecoder: (ByteArray) -> ImageBitmap?

    @Before
    fun saveSeamAndReset() {
        savedDecoder = SyncImageDecode.rasterDecoder
        ColorApplier.resetUrlBackgroundStateForTest()
        // Any real byte payload decodes; DataUri still gates the scheme and
        // the %xx decode, so an undecodable/relative candidate still declines.
        SyncImageDecode.rasterDecoder = { FakeImageBitmap() }
    }

    @After
    fun restoreSeam() {
        SyncImageDecode.rasterDecoder = savedDecoder
        ColorApplier.resetUrlBackgroundStateForTest()
    }

    private fun resolve(srcs: List<String>, fallback: Color?) =
        ColorApplier.resolveImageNotation(
            BackgroundImageConfig.ImageNotation(srcs, fallback))

    // ── The three §2.1 outcomes ────────────────────────────────────────

    @Test
    fun `first decodable candidate wins even when it is not the first listed`() {
        // The 003 shape: candidate 1 is the missing `1x1-green.svg`, and the
        // paintable one is BEHIND it. Before the candidate walk this value
        // resolved to Url("1x1-green.svg") and painted nothing, leaving the
        // forbidden red background-color showing.
        assertEquals(
            BackgroundImageConfig.Url(GREEN_PNG_DATA_URI),
            resolve(listOf("1x1-green.svg", GREEN_PNG_DATA_URI, "support/1x1-green.gif"), null)
        )
    }

    @Test
    fun `the walk stops at the winner and never prefers a later candidate`() {
        // §2.1 is "the FIRST one that can be displayed" — a decodable
        // candidate must not be overridden by a decodable one behind it.
        assertEquals(
            BackgroundImageConfig.Url(GREEN_PNG_DATA_URI),
            resolve(listOf(GREEN_PNG_DATA_URI, GREEN_GIF_DATA_URI), null)
        )
    }

    @Test
    fun `the last candidate still wins - the 004 shape`() {
        // The 004 shape: BOTH leading candidates are missing files and only
        // the third resolves. A first-src-only read can never reach it.
        assertEquals(
            BackgroundImageConfig.Url(GREEN_GIF_DATA_URI),
            resolve(listOf("1x1-green.svg", "1x1-green.png", GREEN_GIF_DATA_URI), null)
        )
    }

    @Test
    fun `all candidates declined falls back to the colour`() {
        // The 001 shape: `image("green.png", green)`. Every candidate is
        // undecodable, so §2.1 paints the colour — the SAME config the
        // pre-wave-49 extractor produced, which is why 001 keeps passing.
        val green = Color(0.0f, 0.5019607843137255.toFloat(), 0.0f, 1.0f)
        assertEquals(
            BackgroundImageConfig.SolidColor(green),
            resolve(listOf("green.png"), green)
        )
    }

    @Test
    fun `empty candidate list paints the colour alone`() {
        // The 005 shape: `image(rgba(0,0,255,0.5))` — no walk to run, the
        // colour IS the image (§2.1's src-less form).
        val blue = Color(0.0f, 0.0f, 1.0f, 0.5f)
        assertEquals(BackgroundImageConfig.SolidColor(blue), resolve(emptyList(), blue))
    }

    @Test
    fun `all candidates declined with no colour paints nothing`() {
        // The 002/003/004 shape as the wire stands TODAY (no host asset
        // delivery): nothing is decodable and there is no fallback colour, so
        // the layer paints nothing and the element's own background-color
        // shows through — the browser's failed-load visual, and the honest
        // capture this lane does not fake.
        assertEquals(
            BackgroundImageConfig.None,
            resolve(listOf("support/1x1-green.png"), null)
        )
    }

    // ── The precedence change this lane makes, stated as a test ─────────

    @Test
    fun `a loadable source now beats a fallback colour`() {
        // THE ONE BEHAVIOUR THIS LANE DELIBERATELY CHANGES. The wave-48 seam
        // painted the colour whenever one was present, because the extractor
        // could not know whether a source would load. The paint path CAN
        // know, and §2.1 says a displayable source wins — so a decodable
        // candidate must now beat the fallback colour. No corpus test carries
        // this combination today; the pin is what stops the old precedence
        // creeping back in unnoticed.
        assertEquals(
            BackgroundImageConfig.Url(GREEN_PNG_DATA_URI),
            resolve(listOf(GREEN_PNG_DATA_URI), Color(1.0f, 0.0f, 0.0f, 1.0f))
        )
    }

    // ── The pure walk, independent of any decoder ───────────────────────

    @Test
    fun `chain reports every declined candidate in author order`() {
        // `declined` is what keeps the fallthrough loud (the resolver logs
        // it): an investigator must be able to see WHICH candidates were
        // refused, in order, not just that something failed.
        val outcome = ImageCandidateChain.firstPaintable(listOf("a", "b", "c")) { src ->
            if (src == "c") 42 else null
        }
        assertEquals("c", outcome.src)
        assertEquals(42, outcome.image)
        assertEquals(listOf("a", "b"), outcome.declined)
    }

    @Test
    fun `chain trims candidates and skips blank wire slots without declining them`() {
        // A blank entry is an empty wire slot, not a source the author wrote
        // — skipping it silently is correct; counting it as a decline would
        // put noise in the log the real declines live in.
        val probed = mutableListOf<String>()
        val outcome = ImageCandidateChain.firstPaintable(listOf("  ", " a.png ", "b")) { src ->
            probed.add(src)
            if (src == "b") 1 else null
        }
        assertEquals(listOf("a.png", "b"), probed)
        assertEquals(listOf("a.png"), outcome.declined)
        assertEquals("b", outcome.src)
    }

    @Test
    fun `chain on an empty list declines nothing and wins nothing`() {
        // The `image(<color>)` path: no walk, no declines, no winner.
        val outcome = ImageCandidateChain.firstPaintable(emptyList()) { _: String ->
            throw AssertionError("no candidate should ever be probed")
        }
        assertNull(outcome.src)
        assertTrue(outcome.declined.isEmpty())
    }

    // ── The dense-lattice shading route (wave-49 lane A3) ───────────────
    //
    // WHY IT IS IN THIS FILE. Landing the candidate walk is not enough on
    // Android by itself: the corpus's only paintable candidate for
    // fallbacks-002/003/004 is a 1×1 PNG, and a 1×1 tile over the tests'
    // 200×200 box is 40,000 lattice origins — past the 4096 cap, where
    // applyUrlBackground used to degrade to ONE anchored tile (a single green
    // pixel on a red box) while iOS has tiled it correctly through a
    // `.tiledImage` shading since wave 8. These pin the ROUTING half of the
    // Compose twin of that branch; the shader call itself needs a real draw
    // scope (android.graphics is a throwing stub here), which is precisely
    // why the decision was extracted as a pure predicate.

    @Test
    fun `dense plain-repeat at uniform scale routes to the repeating shader`() {
        // The 1x1-green.png case: repeat/repeat (the CSS initial) and
        // background-size auto, so the tile IS the image — scale 1:1.
        assertTrue(
            ColorApplier.denseRepeatIsShadeable(
                BackgroundRepeatAxes(AxisRepeat.REPEAT, AxisRepeat.REPEAT), 1.0f, 1.0f)
        )
    }

    @Test
    fun `space round and no-repeat are never shaded - they are not infinite grids`() {
        // §3.7: `space` inserts gaps, `round` rescales per lattice, and
        // `no-repeat` is one tile. A TileMode.Repeated shader paints an
        // unbroken uniform grid and would be wrong for all three.
        for (mode in listOf(AxisRepeat.SPACE, AxisRepeat.ROUND, AxisRepeat.NO_REPEAT)) {
            assertTrue(
                "x=$mode must not shade",
                !ColorApplier.denseRepeatIsShadeable(
                    BackgroundRepeatAxes(mode, AxisRepeat.REPEAT), 1.0f, 1.0f)
            )
            assertTrue(
                "y=$mode must not shade",
                !ColorApplier.denseRepeatIsShadeable(
                    BackgroundRepeatAxes(AxisRepeat.REPEAT, mode), 1.0f, 1.0f)
            )
        }
    }

    @Test
    fun `a non-uniform explicit size keeps the enumerated degrade`() {
        // One shader local matrix carries a single scale pair; the tolerance
        // is byte-identical to the iOS twin's abs(sx - sy) < 0.0001.
        assertTrue(
            !ColorApplier.denseRepeatIsShadeable(
                BackgroundRepeatAxes(AxisRepeat.REPEAT, AxisRepeat.REPEAT), 2.0f, 3.0f)
        )
        // Just inside the tolerance — still one uniform scale.
        assertTrue(
            ColorApplier.denseRepeatIsShadeable(
                BackgroundRepeatAxes(AxisRepeat.REPEAT, AxisRepeat.REPEAT), 2.0f, 2.00005f)
        )
    }

    @Test
    fun `a 1x1 tile over the WPT 200x200 box really does blow past the 4096 cap`() {
        // The load-bearing arithmetic, executed rather than asserted in prose:
        // the shading branch only matters if the cap is genuinely reached, and
        // the corpus's paintable candidate for fallbacks-002/003/004 is
        // css-images/support/1x1-green.png over a `width:200px; height:200px`
        // square. 200 origins per axis, 40,000 in the cartesian product the
        // draw loop would enumerate — an order of magnitude past 4096.
        val x = BackgroundTileMath.axisPlan(200f, 1f, 0f, AxisRepeat.REPEAT)
        val y = BackgroundTileMath.axisPlan(200f, 1f, 0f, AxisRepeat.REPEAT)
        assertEquals(200, x.origins.size)
        assertEquals(200, y.origins.size)
        assertTrue(
            "1x1 over 200x200 must exceed the cap, or the shading branch is dead code",
            x.origins.size * y.origins.size > 4096)
    }
}
