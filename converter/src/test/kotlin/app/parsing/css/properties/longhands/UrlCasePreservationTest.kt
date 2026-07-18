package app.parsing.css.properties.longhands

// Regression suite for the url()-payload case-preservation fix (applier
// campaign wave 8, converter lane).
//
// Root cause: BackgroundImagePropertyParser lowercased the ENTIRE
// declaration value before splitting layers, and BorderImageSource /
// ListStyleImage / MaskImage parsers did the same to the whole value —
// so url() payloads (base64 data URIs, case-sensitive server paths)
// reached the IR with their case destroyed. The iOS runtime documented
// this as a known wire quirk and the repo standardized on percent-encoded
// data URIs to dodge it.
//
// The fix: keyword / function-name matching runs against a lowered COPY
// (they are ASCII case-insensitive per CSS Syntax L3 §4.3) while url()
// payloads are extracted from the ORIGINAL bytes. Gradient bodies still
// parse from the lowered copy — every gradient token is itself
// case-insensitive per CSS Images L3/L4, so no author bytes are lost.
//
// Wire note: restoring true bytes is a bug fix, not a wire-shape change —
// schema/spec/05-versioning.md freezes byte SHAPES and the v2 schema is
// permissive at property-data leaves; the leaf is still a JSON string.
//
// Pinned invariants:
//   1. base64 data URIs round-trip byte-exact (background-image,
//      mask-image, border-image-source, list-style-image).
//   2. Uppercase URL paths are preserved.
//   3. Keywords / function names stay case-insensitive: URL(...),
//      LINEAR-GRADIENT(...), NONE all still parse.
//   4. Multi-layer background-image preserves each layer's url bytes.
//   5. Raw fallback layers carry original (non-lowercased) bytes.

import app.irmodels.properties.background.BackgroundImageProperty
import app.irmodels.properties.borders.BorderImageSource
import app.irmodels.properties.borders.BorderImageSourceProperty
import app.irmodels.properties.effects.MaskImageProperty
import app.irmodels.properties.effects.MaskImageValue
import app.irmodels.properties.lists.ListStyleImageProperty
import app.parsing.css.properties.longhands.background.BackgroundImagePropertyParser
import app.parsing.css.properties.longhands.borders.BorderImageSourcePropertyParser
import app.parsing.css.properties.longhands.effects.MaskImagePropertyParser
import app.parsing.css.properties.longhands.lists.ListStyleImagePropertyParser
import app.parsing.css.properties.primitiveParsers.UrlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UrlCasePreservationTest {

    // A base64 payload with mixed case — any lowercasing corrupts it.
    // (Real 1x1 PNG prefix: "iVBORw0KGgo…" is famously mixed-case.)
    private val dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="

    // A URL whose path segment is case-sensitive on real servers.
    private val casedUrl = "https://CDN.Example.com/Assets/Logo.PNG"

    // --- 1. base64 data URI round-trips byte-exact -----------------------

    @Test
    fun backgroundImageDataUriRoundTripsByteExact() {
        val prop = BackgroundImagePropertyParser.parse("url($dataUri)") as BackgroundImageProperty
        val url = assertIs<BackgroundImageProperty.BackgroundImage.Url>(prop.images.single())
        assertEquals(dataUri, url.url.url) // byte-exact, incl. base64 case
        assertTrue(url.url.isDataUrl)      // data: scheme still detected
    }

    @Test
    fun maskImageDataUriRoundTripsByteExact() {
        val prop = MaskImagePropertyParser.parse("url($dataUri)") as MaskImageProperty
        val img = assertIs<MaskImageValue.Image>(prop.values.single())
        assertEquals(dataUri, img.url.url)
    }

    @Test
    fun borderImageSourceDataUriRoundTripsByteExact() {
        val prop = BorderImageSourcePropertyParser.parse("url($dataUri)") as BorderImageSourceProperty
        val src = assertIs<BorderImageSource.Url>(prop.source)
        assertEquals(dataUri, src.url)
    }

    @Test
    fun listStyleImageDataUriRoundTripsByteExact() {
        val prop = ListStyleImagePropertyParser.parse("url('$dataUri')") as ListStyleImageProperty
        val img = assertIs<ListStyleImageProperty.ListImage.Url>(prop.image)
        assertEquals(dataUri, img.url)
    }

    // --- 2. uppercase URL paths preserved --------------------------------

    @Test
    fun backgroundImageUppercasePathPreserved() {
        val prop = BackgroundImagePropertyParser.parse("url(\"$casedUrl\")") as BackgroundImageProperty
        val url = assertIs<BackgroundImageProperty.BackgroundImage.Url>(prop.images.single())
        assertEquals(casedUrl, url.url.url)
    }

    // --- 3. keywords / function names stay case-insensitive --------------

    @Test
    fun uppercaseUrlFunctionNameStillParses() {
        // "URL(...)" is valid CSS — function names are ASCII
        // case-insensitive (CSS Syntax L3 §4.3.4) — and the payload
        // must still come back untouched.
        val prop = BackgroundImagePropertyParser.parse("URL($dataUri)") as BackgroundImageProperty
        val url = assertIs<BackgroundImageProperty.BackgroundImage.Url>(prop.images.single())
        assertEquals(dataUri, url.url.url)
    }

    @Test
    fun uppercaseLinearGradientStillParses() {
        val prop = BackgroundImagePropertyParser.parse("LINEAR-GRADIENT(TO RIGHT, RED, BLUE)") as BackgroundImageProperty
        val grad = assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(prop.images.single())
        assertEquals(90.0, assertNotNull(grad.angle).degrees, 1e-9) // "to right" = 90deg
        assertEquals(2, grad.colorStops.size)                       // red + blue survived
    }

    @Test
    fun uppercaseNoneAndKeywordsStillParse() {
        // 'NONE' keyword — case-insensitive per spec.
        val bg = BackgroundImagePropertyParser.parse("NONE") as BackgroundImageProperty
        assertIs<BackgroundImageProperty.BackgroundImage.None>(bg.images.single())
        val border = BorderImageSourcePropertyParser.parse("None") as BorderImageSourceProperty
        assertIs<BorderImageSource.None>(border.source)
        val list = ListStyleImagePropertyParser.parse("NONE") as ListStyleImageProperty
        assertIs<ListStyleImageProperty.ListImage.None>(list.image)
        val mask = MaskImagePropertyParser.parse("None") as MaskImageProperty
        assertIs<MaskImageValue.None>(mask.values.single())
    }

    @Test
    fun uppercaseUrlPrimitiveParserStillMatches() {
        // The shared primitive itself must accept URL( / Url( spellings
        // and detect DATA: case-insensitively (RFC 3986 §3.1 schemes).
        val ir = assertNotNull(UrlParser.parse("Url('DATA:image/png;base64,AbC=')"))
        assertEquals("DATA:image/png;base64,AbC=", ir.url) // payload untouched
        assertTrue(ir.isDataUrl)                            // scheme matched case-insensitively
    }

    // --- 4. multi-layer background-image preserves each layer ------------

    @Test
    fun multiLayerBackgroundPreservesUrlBytesPerLayer() {
        // Layer 1: cased URL; layer 2: gradient (case-insensitive body).
        val prop = BackgroundImagePropertyParser.parse(
            "url($casedUrl), linear-gradient(90deg, red, blue)"
        ) as BackgroundImageProperty
        assertEquals(2, prop.images.size)
        val url = assertIs<BackgroundImageProperty.BackgroundImage.Url>(prop.images[0])
        assertEquals(casedUrl, url.url.url) // layer split must not lowercase
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(prop.images[1])
    }

    // --- 5. Raw fallback carries original bytes --------------------------

    @Test
    fun rawFallbackLayerKeepsOriginalBytes() {
        // "image-set(...)" isn't a recognized image function → Raw layer;
        // the Raw payload must be the author's original bytes.
        val original = "image-set(\"Cat.PNG\" 1x)"
        val prop = BackgroundImagePropertyParser.parse(original) as BackgroundImageProperty
        val raw = assertIs<BackgroundImageProperty.BackgroundImage.Raw>(prop.images.single())
        assertEquals(original, raw.value)
    }
}
