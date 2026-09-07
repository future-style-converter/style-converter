package app.parsing.css.properties.longhands.background

// image() notation micro-grammar — wave-48 lane W5, split into its own file
// per the ≤200-line rule (BackgroundImagePropertyParser only dispatches here).
//
// css-images-4 §2.5:  image( <image-tags>? [ <image-src>? , <color>? ]! )
// with <image-src> = <url> | <string>.  WPT css-image-fallbacks-and-
// annotations003/004 additionally exercise the LEGACY css-images-3 grammar
// where several <image-src> candidates are listed ("try each in order"), so
// the src slot here is a `#`-list.  Semantics (both levels agree): the first
// source that can be displayed paints; if none can, the <color> paints; a
// src-less image(<color>) is a solid-colour image.
//
// MEASURED CASE: all five fallbacks-and-annotations tests fell to the Raw
// route before (wave48-cal css-images) — Chromium itself does not implement
// image(), so the re-emitted bytes were dropped as invalid even on web, and
// every platform painted the `background-color: red` the tests forbid
// (001–004: colorFailed at ssim 0.998+; 005: 0.914).
//
// VERIFIED on web (wave-48 W5 section-runner re-capture, w48-w5-verify2):
// 001–004 flip to 1.0000 P through the BackgroundImageExtractor lowering +
// the harness /wpt-image/ asset route. 005 now composites its layers
// spec-correctly (rgba(0,0,255,.5) over the tiled green png = rgb(0,64,128),
// confirmed in the capture) but CANNOT pass as scored: WPT points all five
// tests at ONE shared solid-green ref whose prose is 001's, while 005's own
// prose and assert describe a pale square — an upstream authoring artifact,
// stated rather than tuned around.
import app.irmodels.IRUrl
import app.irmodels.properties.background.BackgroundImageProperty
import app.parsing.css.properties.primitiveParsers.ColorParser
import app.parsing.css.properties.primitiveParsers.TokenizationUtils
import app.parsing.css.properties.primitiveParsers.UrlParser

internal object ImageNotationParser {

    /**
     * Parse one whole `image(...)` value (ORIGINAL author bytes — url/string
     * payloads are case-sensitive) into an ImageNotation layer, or null when
     * the content is outside the modelled grammar (caller falls back to Raw,
     * exactly as before this parser existed).
     */
    internal fun parse(value: String): BackgroundImageProperty.BackgroundImage? {
        // Function extraction is case-insensitive on the NAME only.
        val content = TokenizationUtils.extractFunctionContent(value.trim(), "image") ?: return null
        // Top-level comma split (paren-aware) — colours like rgba(0,0,255,.5)
        // stay one item.
        val items = TokenizationUtils.splitByComma(content).map { it.trim() }.filter { it.isNotEmpty() }
        if (items.isEmpty()) return null // `image()` is invalid — no src, no colour
        val srcs = mutableListOf<IRUrl>()
        var color: app.irmodels.IRColor? = null
        for ((i, item) in items.withIndex()) {
            // <image-tags> (`ltr`/`rtl` directionality prefix) is NOT modelled —
            // zero corpus coverage; refusing keeps Raw's author bytes honest.
            src(item)?.let {
                // Sources may only precede the colour (§2.1's ordered grammar).
                if (color != null) return null
                srcs.add(it); continue
            }
            // The one optional <color> must be the LAST item.
            val parsed = ColorParser.parse(item) ?: return null
            // A colour whose sRGB is unresolvable (var()/color-mix chains)
            // can't be painted as a fallback by the natives — refuse to Raw
            // rather than half-model it. (All corpus fallbacks are literals.)
            if (parsed.srgb == null || i != items.lastIndex) return null
            color = parsed
        }
        if (srcs.isEmpty() && color == null) return null // nothing paintable
        return BackgroundImageProperty.BackgroundImage.ImageNotation(srcs, color)
    }

    /** One <image-src>: a url() function or a bare quoted string (both are
     *  equivalent per §2.1 — a string is a URL). Null when neither. */
    private fun src(item: String): IRUrl? {
        // url(...) — delegate to the shared parser (quotes, data URIs).
        if (item.lowercase().startsWith("url(")) return UrlParser.parse(item)
        // "..." / '...' — payload is the verbatim inner bytes.
        if (item.length >= 2 && (item.first() == '"' || item.first() == '\'') && item.last() == item.first()) {
            return IRUrl(item.substring(1, item.length - 1), isDataUrl = false)
        }
        return null
    }
}
