package com.styleconverter.runtime.spacing

// ChUnitMetrics — wave-18 lane 2 (pin P1): the CSS `ch` unit basis.
//
// css-values-4 §6.1.3 defines 1ch as the advance width of the glyph '0' in
// the element's font. This helper measures that advance with the SAME
// platform text engine Compose ultimately shells out to (android.graphics
// Paint/Typeface), so a `width: 63.1ch` box fits exactly 63 characters of
// the text it will actually render — character-count parity with the
// browser reference even when the two platforms' fonts differ slightly.
//
// JVM unit tests have no Android graphics runtime: android.graphics.Paint
// is an unmocked stub that throws on construction. measure() catches every
// throwable and returns null, which the resolver maps to the spec's own
// 0.5em fallback (§6.1.3: "assumed to be 0.5em wide") — so the pure test
// suite exercises the fallback lane deterministically.

import androidx.compose.ui.text.font.FontFamily

object ChUnitMetrics {

    // Measured advances keyed by (generic family, font size). StyleApplier
    // calls measure() once per applyConfig pass; a component tree re-renders
    // often, so we memoize to avoid a Paint allocation + text measurement on
    // every recomposition. Bounded in practice: the corpus uses a handful of
    // generic families × font sizes.
    private val cache = HashMap<Pair<String, Float>, Float?>()

    /**
     * The advance width of '0' in px for [fontFamily] at [fontSizePx], or
     * null when platform metrics are unavailable (pure-JVM tests, or a
     * defensive failure inside the graphics stack). Callers must treat null
     * as "use the 0.5em spec fallback" — never as zero.
     */
    fun measure(fontFamily: FontFamily?, fontSizePx: Float): Float? {
        // Cache key: the generic-family name (Compose FontFamily instances
        // for the generic families are singletons with stable toString) +
        // the size. Custom font-file families all map to DEFAULT below, so
        // collapsing them into one key is consistent with the measurement.
        val key = genericName(fontFamily) to fontSizePx
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val advance = measureUncached(fontFamily, fontSizePx)
        synchronized(cache) { cache[key] = advance }
        return advance
    }

    /** Stable name for the cache key — see [measure]. */
    private fun genericName(fontFamily: FontFamily?): String = when (fontFamily) {
        // The five CSS generic families Compose models as singletons.
        FontFamily.Monospace -> "monospace"
        FontFamily.Serif -> "serif"
        FontFamily.SansSerif -> "sans-serif"
        FontFamily.Cursive -> "cursive"
        // null (no font-family declared) and custom families both render
        // through the platform default in this measurement.
        else -> "default"
    }

    /** The actual platform measurement — isolated so measure() can memoize. */
    private fun measureUncached(fontFamily: FontFamily?, fontSizePx: Float): Float? = try {
        // Map the Compose generic family onto the android.graphics Typeface
        // the text engine resolves it to (TextStyleApplier maps CSS
        // "monospace"→FontFamily.Monospace etc.; Typeface mirrors that).
        val typeface = when (fontFamily) {
            FontFamily.Monospace -> android.graphics.Typeface.MONOSPACE
            FontFamily.Serif -> android.graphics.Typeface.SERIF
            FontFamily.SansSerif -> android.graphics.Typeface.SANS_SERIF
            // "cursive" has no Typeface constant; create() resolves the
            // system family of that name (falls back to default safely).
            FontFamily.Cursive ->
                android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL)
            else -> android.graphics.Typeface.DEFAULT
        }
        // Paint.measureText returns the x-advance of the run — exactly the
        // css-values-4 "advance measure" of the glyph, in px because
        // textSize is set in px (the runtime's px==dp space).
        val paint = android.graphics.Paint().apply {
            this.typeface = typeface
            textSize = fontSizePx
        }
        // Non-positive/NaN results mean the stub or a broken font — treat
        // as unavailable so the caller uses the 0.5em spec fallback.
        paint.measureText("0").takeIf { it.isFinite() && it > 0f }
    } catch (_: Throwable) {
        // Pure-JVM tests (unmocked android.graphics) land here → null →
        // spec fallback. Never crash a render path over a metric.
        null
    }
}
