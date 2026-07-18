package com.styleconverter.runtime.core.images

// SyncImageDecode — the ONE synchronous data:-URI → ImageBitmap decode
// shared by every draw-time image consumer (MaskApplier's url() masks,
// ColorApplier's url() background layers). Factored out of MaskApplier in
// wave 9 so the background path could not fork a second cache + second
// decoder seam: css-backgrounds-3 url() layers and css-masking-1 url()
// sources have identical loading needs (bitmap available on the FIRST
// draw frame for capture determinism), so they share one owner.

// BitmapFactory is the SYNCHRONOUS raster decode for data:-URI sources —
// the same decode ImageCache's data: branch uses; no Coil here because
// the Modifier draw path must have the bitmap on the FIRST frame
// (capture determinism) and Coil is async by construction.
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object SyncImageDecode {

    /** Cap on cached decoded bitmaps — fixture data-URI sources are tiny
     *  (≤ a few KB each), so a small bound suffices; the LRU below evicts
     *  the least-recently-used entry past it. */
    private const val CACHE_MAX_ENTRIES = 16

    /**
     * url → decoded [ImageBitmap]. Mirrors ImageCache's LRU memory-cache
     * pattern but stays LOCAL and synchronous: ImageCache.loadImage is a
     * suspend API with no synchronous lookup (a draw-time consumer needs
     * the bitmap NOW), and android.util.LruCache is a throwing stub under
     * the plain-JVM unit suite that pins this routing (no Robolectric in
     * this repo). accessOrder=true + removeEldestEntry is the textbook
     * JDK LRU.
     */
    private val bitmapCache =
        object : LinkedHashMap<String, ImageBitmap>(CACHE_MAX_ENTRIES, 0.75f, true) {
            // Evict the least-recently-used entry once past the cap.
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean =
                size > CACHE_MAX_ENTRIES
        }

    /**
     * Raster bytes → [ImageBitmap]. An `internal var` seam: the default
     * is the platform BitmapFactory decode (the same call ImageCache's
     * data:-URI branch performs); the plain-JVM suite swaps in a fake
     * because android.graphics is a throwing stub off-device. Production
     * code must never reassign this.
     */
    internal var rasterDecoder: (ByteArray) -> ImageBitmap? = { bytes ->
        try {
            // BitmapFactory returns null (does not throw) for undecodable
            // bytes on device — null IS the visible-failure contract.
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (t: Throwable) {
            // android.jar's "not mocked" stub throw under plain JVM (and a
            // defensive belt on-device, e.g. OOM on a hostile payload) →
            // the same null contract; the caller logs the failure once.
            null
        }
    }

    /**
     * Decode a `data:` URI source to an [ImageBitmap], cached per URL.
     * Returns null when [url] is not a data URI or the payload is
     * malformed/undecodable — callers must surface that loudly (the
     * no-silent-fallthrough house rule lives at the call sites, which
     * know their property name for the log line).
     * Synchronous by design: data URIs carry their bytes inline (no I/O),
     * which is exactly what first-frame capture determinism needs.
     */
    internal fun decodeDataUri(url: String): ImageBitmap? {
        // Cache hit first — decoding per recomposition would waste work,
        // and returning the SAME bitmap keeps captures deterministic.
        synchronized(bitmapCache) { bitmapCache[url] }?.let { return it }
        // RFC 2397 payload → bytes; DataUri owns the %xx-unescape +
        // strict-base64 rules (pinned by its own JVM suite).
        val bytes = DataUri.decode(url) ?: return null
        // bytes → platform bitmap through the seam above.
        val bitmap = rasterDecoder(bytes) ?: return null
        // Populate the LRU under the same lock the lookup uses.
        synchronized(bitmapCache) { bitmapCache.put(url, bitmap) }
        return bitmap
    }

    /** Test hook: clear the decode cache so JVM test cases start from a
     *  known state. Test-only by contract. */
    internal fun resetForTest() {
        synchronized(bitmapCache) { bitmapCache.clear() }
    }
}
