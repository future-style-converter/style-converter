package com.styleconverter.runtime.images

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * DocumentImageRegistry — runtime resolution of a document's REPLACED-ELEMENT
 * image sources (`meta.attrs.src`; schema/spec/04-metadata-fields.md,
 * wave-39 lane A2).
 *
 * ## Why this exists, and why it is not a property triplet
 *
 * Everything under this runtime's category folders is a per-PROPERTY triplet:
 * one IR property in, one Compose modifier out. A replaced element's CONTENT is
 * neither. It is a document-level RESOURCE reached through a path only the HOST
 * can turn into bytes, so — exactly like its @font-face twin
 * ([com.styleconverter.runtime.typography.font.DocumentFontRegistry]) — the
 * registry is a document-scoped object the host fills in (the harness's inbox
 * path does it right after `IRDocumentDecoder.decode`), and the paint path
 * merely CONSULTS it.
 *
 * ## What it closes
 *
 * wave-36 lane M1 put the source on the wire and taught the WEB harness to
 * resolve it through its `/wpt-image/` route. Both natives got the path and
 * nothing to open — `IRAttrs.src`'s own comment said so ("DECODED, NOT YET
 * PAINTED … Painting it is the named follow-up") — so every replaced element
 * painted an EMPTY box where the browser ref paints an image. MEASURED
 * (wave38-final, css-writing-modes img-intrinsic-size-contribution-001/002):
 * web 0.9623 PASS against Android 0.8654 fail, the ref's 200×100 blue raster
 * simply absent from the native capture.
 *
 * ## The file channel
 *
 * The wire carries a corpus-relative PATH, never a payload. The feeder
 * (tools/titan/feed-android.mjs `pushReplacedImages`) copies each referenced
 * file into the app's own sandbox at `<externalFilesDir>/images/<src>`,
 * preserving the relative path VERBATIM — so resolution here is a plain [File]
 * join with no name mangling and no escaping rule that could drift from the
 * host's. A `data:` source carries its own bytes and needs no hop at all.
 *
 * ## Degradation is loud, never silent
 *
 * A source that is absent, unreadable, or in a container `BitmapFactory` cannot
 * parse is DECLINED: [resolve] answers null, the paint path renders the empty
 * box it rendered before this channel existed, and the decline is logged AND
 * counted in [lastReport]. That matters most for SVG, which is 19 of the 28
 * depth-48 replaced-source tests and which Android cannot rasterise at all —
 * the honest gap is a stamped decline, never a plausible-looking wrong raster.
 */
object DocumentImageRegistry {

    private const val TAG = "DocImageRegistry"

    /** Logging seam. `runCatching` guards `android.util.Log` for JVM callers —
     *  the module's unit suite runs without `returnDefaultValues`, so an
     *  unguarded Log call throws "not mocked" and would make this registry
     *  untestable off-device. Same guard DocumentFontRegistry uses. */
    private fun logw(message: String) {
        runCatching { android.util.Log.w(TAG, message) }
    }

    /**
     * One decoded replaced-element image: the raster plus the INTRINSIC size
     * CSS 2.1 §10.3.2 / css-images-3 §5.2 size the box from.
     *
     * The intrinsic size is in raw image pixels, which equals CSS px at the
     * 160 dpi capture density (1 px == 1 dp) — the same identity every other
     * geometry constant in this runtime assumes. [decodeFile] therefore
     * disables `BitmapFactory`'s density scaling, or a device at any other
     * dpi would report a size the layout has no way to un-scale.
     */
    data class DecodedImage(
        val bitmap: ImageBitmap,
        val intrinsicWidthPx: Int,
        val intrinsicHeightPx: Int,
    ) {
        /** The css-images-3 §5.2 intrinsic ASPECT RATIO (width ÷ height), or
         *  null when either axis is degenerate — the caller then falls back to
         *  the intrinsic size rather than dividing by zero. */
        val aspectRatio: Float?
            get() = if (intrinsicWidthPx > 0 && intrinsicHeightPx > 0)
                intrinsicWidthPx.toFloat() / intrinsicHeightPx.toFloat() else null
    }

    /** Image container formats `android.graphics.BitmapFactory` can actually
     *  parse — and, critically, NOT the ones it cannot.
     *
     *  SVG is the load-bearing absence. Android ships no SVG rasteriser in the
     *  platform (`VectorDrawable` is a different, Android-only format, and
     *  compiling one at runtime is not a thing), so an SVG reaching
     *  `decodeFile` returns null — a decline this table turns into a NAMED one
     *  instead of a mystery. ICO is out for the same factual reason.
     *
     *  Deliberately asymmetric with the FEEDER's table, which admits SVG: the
     *  host channel's job is to deliver what the wire named, and the decision
     *  about what this PLATFORM can render belongs here, where it can be
     *  stamped. Exactly the layering DocumentFontRegistry's
     *  ANDROID_LOADABLE_EXTENSIONS uses for WOFF. */
    private val ANDROID_DECODABLE_EXTENSIONS =
        setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "avif")

    /** The directory the host copied the image FILES under; the wire's `src` is
     *  joined onto it verbatim. Null ⇒ nothing on disk can resolve (a `data:`
     *  source still can — it carries its own bytes). */
    @Volatile
    private var baseDir: File? = null

    /** Decode cache for the CURRENT document, keyed by the wire `src`.
     *
     *  Not an optimisation so much as a correctness convenience: one support
     *  image is routinely painted by many boxes (css-grid's abspos family
     *  paints colors-8x16.png from 41 components), and re-decoding per box
     *  would multiply capture time by the component count. Cleared with the
     *  document so a stale raster can never outlive the run that delivered it.
     *  A null VALUE is a cached DECLINE — it stops the log from repeating the
     *  same decline once per box. */
    private val cache = HashMap<String, DecodedImage?>()

    /** Outcome of the current document's resolutions. Exposed so the harness
     *  can log a per-document line and a test can assert delivery without
     *  reaching into Compose's graphics stack. */
    data class Report(
        val requested: Int = 0,
        val decoded: Int = 0,
        val declined: List<String> = emptyList(),
    )

    @Volatile
    var lastReport: Report = Report()
        private set

    /**
     * Point the registry at this document's image sandbox, clearing the
     * previous document's cache and report.
     *
     * REPLACE, not merge — the inbox harness renders many documents in one
     * process, and a raster left over from document N-1 keyed by a path
     * document N also uses would paint the wrong picture with nothing in any
     * log. The feeders wipe the on-device images directory per run for exactly
     * the same reason.
     */
    fun configure(dir: File?) {
        baseDir = dir
        cache.clear()
        lastReport = Report()
    }

    /** Forget the base directory and every cached raster. Used by tests and by
     *  a host between documents. */
    fun clear() = configure(null)

    /**
     * The decoded image for one wire `src`, or null when this document cannot
     * deliver it.
     *
     * Null is the contract, not a fallback: the caller paints the empty box it
     * painted before this channel existed. Inventing a placeholder raster here
     * would put pixels on screen that no stylesheet asked for and make an
     * undelivered asset look like a renderer bug.
     */
    fun resolve(src: String?): DecodedImage? {
        val key = src?.trim().orEmpty()
        if (key.isEmpty()) return null
        // Cached, including cached declines (a null VALUE) — `containsKey`
        // rather than `get() ?: decode` so a decline is not re-attempted (and
        // re-logged) once per painting box.
        if (cache.containsKey(key)) return cache[key]
        val decoded = decodeSource(key)
        cache[key] = decoded
        val r = lastReport
        lastReport = Report(
            requested = r.requested + 1,
            decoded = r.decoded + (if (decoded != null) 1 else 0),
            declined = if (decoded == null) r.declined + key else r.declined,
        )
        return decoded
    }

    /** Decode one source: a `data:` URI from its own bytes, anything else from
     *  the host-delivered file. Split out so [resolve] owns only the cache and
     *  the report. */
    private fun decodeSource(src: String): DecodedImage? {
        if (src.startsWith("data:", ignoreCase = true)) return decodeDataUri(src)
        val dir = baseDir
        if (dir == null) {
            logw("declined image '$src': no images directory configured — the host " +
                "never pointed this document at a sandbox; painting the empty box")
            return null
        }
        val file = File(dir, src)
        if (!file.isFile || !file.canRead()) {
            // Name the path AND the source so an investigator can tell a
            // missing feeder hop from a bad path.
            logw("declined image '$src': no readable file at ${file.absolutePath} — " +
                "the feeder hop did not deliver it; painting the empty box")
            return null
        }
        // Format gate BEFORE the decode: an SVG does not throw, it returns
        // null, so without this the log would say "did not decode" where the
        // truth is "this platform has no rasteriser for that container".
        val ext = file.name.substringAfterLast('.', "").lowercase()
        if (ext !in ANDROID_DECODABLE_EXTENSIONS) {
            logw("declined image '$src': '$ext' is a container android.graphics." +
                "BitmapFactory cannot parse (Android ships no SVG rasteriser) — " +
                "painting the empty box rather than a wrong raster")
            return null
        }
        return decodeBytes({ opts -> BitmapFactory.decodeFile(file.absolutePath, opts) }, src)
    }

    /** Decode a `data:` URI's own payload. The extractor forwards an authored
     *  data: source VERBATIM (tools/titan/extract-fixture.mjs
     *  resolveReplacedSrc), so these bytes never ride the feeder hop and are
     *  the one source that works with no sandbox at all. */
    private fun decodeDataUri(src: String): DecodedImage? {
        val comma = src.indexOf(',')
        if (comma < 0) {
            logw("declined image: malformed data: URI (no comma)")
            return null
        }
        val meta = src.substring(5, comma)
        val payload = src.substring(comma + 1)
        // Only base64 is decoded: a percent-encoded data: URI is text (the
        // corpus uses it for SVG, which this platform cannot rasterise anyway),
        // so declining says the true thing instead of half-decoding.
        if (!meta.contains("base64", ignoreCase = true)) {
            logw("declined image: non-base64 data: URI — the payload is text " +
                "(typically SVG), which BitmapFactory cannot parse")
            return null
        }
        val bytes = try {
            Base64.decode(payload, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            logw("declined image: data: URI payload is not valid base64 (${e.message})")
            return null
        }
        return decodeBytes({ opts -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }, "data:")
    }

    /** Shared tail of both decode routes: run the supplied BitmapFactory call
     *  with density scaling OFF, then wrap the result.
     *
     *  `inScaled = false` is load-bearing. BitmapFactory otherwise rescales by
     *  the ratio of the file's nominal density to the device's, so a 200×100
     *  PNG comes back 400×200 on an xhdpi device and every intrinsic-size rule
     *  in css-images-3 §5.2 would be computed from a number the CSS box model
     *  never heard of. The capture runs at 160 dpi where the scale factor is 1,
     *  so this pins a behaviour rather than changing today's numbers — but it
     *  pins the one that is CORRECT off the capture density too. */
    private fun decodeBytes(decode: (BitmapFactory.Options) -> android.graphics.Bitmap?, label: String): DecodedImage? {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bmp = try {
            decode(opts)
        } catch (e: Exception) {
            // BitmapFactory returns null rather than throwing for a bad
            // container, but OOM on a pathological image is a real Throwable
            // path — a declined image must never take the whole capture down.
            logw("declined image '$label': decode threw (${e.javaClass.simpleName}: ${e.message})")
            return null
        }
        if (bmp == null || bmp.width <= 0 || bmp.height <= 0) {
            logw("declined image '$label': BitmapFactory returned no raster")
            return null
        }
        return DecodedImage(bmp.asImageBitmap(), bmp.width, bmp.height)
    }
}
