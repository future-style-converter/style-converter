package com.styleconverter.runtime.core.images

// DataUri — pure RFC 2397 `data:` URI decoding for CSS image values
// (`background-image: url("data:image/png;base64,…")`, mask-image,
// content: url(…)). Wave-8 #36: Coil 3 loads raw ByteArray models
// natively, so a decoded data URI feeds AsyncImage/rememberAsyncImagePainter
// directly — no custom Fetcher, no fake network round-trip. Coil (2 AND 3)
// has no built-in data:-scheme support, which is why every url() path
// routes its model through [toModel] below.
//
// Deliberately JVM-pure (no android.util.Base64) so the decode rules are
// pinned by the plain-JVM unit suite.

object DataUri {

    /** RFC 2397 grammar: `data:[<mediatype>][;base64],<data>`. */
    private const val SCHEME = "data:"

    /** True when [url] is a data URI (scheme match is case-insensitive —
     *  browsers accept `DATA:`; css-values-4 url() passes it verbatim). */
    fun isDataUri(url: String): Boolean =
        url.regionMatches(0, SCHEME, 0, SCHEME.length, ignoreCase = true)

    /**
     * Decode the payload bytes of a data URI, or null when [url] is not a
     * data URI or is malformed (missing comma, broken base64). Malformed
     * URIs return null rather than throwing — the caller falls back to
     * handing Coil the raw string, which fails visibly in its error state
     * (no silent crash in a draw pass).
     */
    fun decode(url: String): ByteArray? {
        if (!isDataUri(url)) return null
        // Everything between "data:" and the first comma is the metadata
        // block (mediatype + optional ";base64" flag); after it, the data.
        val comma = url.indexOf(',')
        if (comma < 0) return null // RFC 2397 requires the comma
        val meta = url.substring(SCHEME.length, comma)
        val payload = url.substring(comma + 1)
        return try {
            if (meta.endsWith(";base64", ignoreCase = true) ||
                meta.equals("base64", ignoreCase = true)
            ) {
                // %xx escapes are legal inside base64 payloads (RFC 2397
                // §3) — unescape first, then strip the whitespace data URIs
                // are occasionally authored with, and decode STRICTLY: the
                // lenient MIME decoder silently skips garbage characters,
                // which would turn a corrupt payload into a wrong image
                // instead of a visible load failure.
                java.util.Base64.getDecoder()
                    .decode(percentDecode(payload).replace(Regex("\\s"), ""))
            } else {
                // Non-base64 form: percent-decoded US-ASCII/UTF-8 text
                // (SVG data URIs are the common CSS case).
                percentDecode(payload).toByteArray(Charsets.UTF_8)
            }
        } catch (_: IllegalArgumentException) {
            null // broken base64 → caller's visible-failure fallback
        }
    }

    /**
     * The Coil model for a CSS url() value: decoded bytes for data URIs
     * (Coil 3 ByteArray support), the string itself for everything else
     * (http/https/file — the network/file fetchers own those schemes).
     */
    fun toModel(url: String): Any = decode(url) ?: url

    /** Minimal %xx decoder (RFC 3986 §2.1). Kept local instead of
     *  java.net.URLDecoder because URLDecoder also rewrites '+' to space —
     *  wrong inside base64 payloads where '+' is a data character. */
    internal fun percentDecode(s: String): String {
        if ('%' !in s) return s // fast path: nothing escaped
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hi = Character.digit(s[i + 1], 16)
                val lo = Character.digit(s[i + 2], 16)
                if (hi >= 0 && lo >= 0) {
                    out.append(((hi shl 4) or lo).toChar())
                    i += 3
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}
