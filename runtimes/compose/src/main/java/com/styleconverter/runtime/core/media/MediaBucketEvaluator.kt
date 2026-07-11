package com.styleconverter.runtime.core.media

// MediaBucketEvaluator — the dynamic-styling runtime v1 evaluation of the IR
// envelope's `media` buckets (schema/spec/06-dynamic-styling.md §4).
//
// This is deliberately STRICTER than the legacy MediaQueryApplier grammar in
// this package: spec 06 pins runtime v1 to exactly two features —
// min-width / max-width in px (compared against the RENDER-SURFACE width,
// never the device screen) and prefers-color-scheme (mapped to the platform
// dark-mode signal, isSystemInDarkTheme() on Android) — joined only by `and`.
// Every other query (not/only prefixes, comma lists, range syntax, any other
// feature, non-px units, malformed strings) is CONSERVATIVELY INACTIVE: a
// query the runtime cannot evaluate never applies (no apply-by-guess),
// logged once per query via PropertyTracker (the no-silent-fallthrough rule).
//
// The legacy MediaQueryApplier (orientation / aspect-ratio / breakpoints…)
// stays for the ResponsiveUtils convenience surface; the ENVELOPE bucket
// path in ComponentRenderer now routes exclusively through this evaluator so
// all three platforms agree on which buckets are contract-active.

import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRMedia

object MediaBucketEvaluator {

    // Logcat tag for the one-time unsupported-query warnings below.
    private const val TAG = "MediaBucketEvaluator"

    /**
     * Render-surface width channel (spec 06 §4): the width in CSS px of the
     * surface the IR document is rendered INTO. The harness capture screen
     * provides the capture-canvas width (390 by default, CAPTURE_WIDTH
     * override — docs/DYNAMIC_CAPTURE.md §2); an SDUI shell would provide
     * its hosting view's width. `null` (no host-provided surface) makes the
     * renderer fall back to the window width from LocalConfiguration — when
     * nobody narrows the surface, the window IS the surface.
     */
    val LocalRenderSurfaceWidthPx = compositionLocalOf<Float?> { null }

    // One term of the runtime-v1 grammar: "(feature: value)" with free
    // whitespace. The value group excludes parens so nested/function syntax
    // (e.g. calc()) fails the full-match and lands in the unsupported path.
    private val TERM = Regex("""^\(\s*([a-z-]+)\s*:\s*([^()\s][^()]*?)\s*\)$""")

    // Width values at runtime v1 are px-only (spec 06 §4: "Only `px` values
    // are evaluated at v1") — em/rem/vw/% widths make the bucket inactive.
    private val PX_VALUE = Regex("""^(\d+(?:\.\d+)?)px$""")

    // Queries already reported unsupported — spec 06 wants the miss logged
    // ONCE (per query string) rather than once per recomposition frame.
    private val loggedUnsupported = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Filter `media` down to the buckets whose query is ACTIVE for the given
     * render surface, PRESERVING ARRAY ORDER — spec 06 §3 layers active
     * media buckets in `media[]` order (last writer wins per type), so the
     * order of this list is contract-bearing.
     */
    fun activeBuckets(
        media: List<IRMedia>,
        surfaceWidthPx: Float,
        isDarkScheme: Boolean
    ): List<IRMedia> {
        // Fast path — the overwhelmingly common bucket-free component.
        if (media.isEmpty()) return emptyList()
        // filter{} keeps encounter order, which is the wire array order.
        return media.filter { evaluate(it.query, surfaceWidthPx, isDarkScheme) }
    }

    /**
     * Evaluate one query string per the runtime-v1 grammar. Pure function
     * (JVM-testable): the caller supplies the two environment signals.
     *
     * @param surfaceWidthPx render-surface width in CSS px (spec 06 §4 —
     *   boundaries are INCLUSIVE per mediaqueries-5 §4.2: `(min-width:
     *   390px)` matches a 390 px surface).
     * @param isDarkScheme the platform dark-mode signal (Android:
     *   configuration uiMode night mask via isSystemInDarkTheme()).
     * @return true only when EVERY term is supported and holds; false for
     *   any unsupported/malformed query (conservatively inactive).
     */
    fun evaluate(query: String, surfaceWidthPx: Float, isDarkScheme: Boolean): Boolean {
        // Normalize case + outer whitespace once; the grammar is ASCII-only.
        val q = query.trim().lowercase()
        // Empty queries carry no evaluatable condition — inactive.
        if (q.isEmpty()) return reportUnsupported(query)
        // Comma = query LIST (mediaqueries-5 §2.1) — not in the v1 grammar.
        if (',' in q) return reportUnsupported(query)
        // Conjunction only: split on the `and` combinator between terms.
        val terms = q.split(Regex("""\s+and\s+"""))
        for (term in terms) {
            // Each fragment must be a complete "(feature: value)" term —
            // `not (…)`, `only screen`, bare `screen`, and range syntax
            // (`(200px <= width)`) all fail this full-match.
            val m = TERM.matchEntire(term) ?: return reportUnsupported(query)
            val feature = m.groupValues[1]
            val value = m.groupValues[2]
            when (feature) {
                // min-width — surface must be at least the px value.
                "min-width" -> {
                    val px = PX_VALUE.matchEntire(value)?.groupValues?.get(1)?.toFloatOrNull()
                        ?: return reportUnsupported(query)
                    if (surfaceWidthPx < px) return false
                }
                // max-width — surface must be at most the px value.
                "max-width" -> {
                    val px = PX_VALUE.matchEntire(value)?.groupValues?.get(1)?.toFloatOrNull()
                        ?: return reportUnsupported(query)
                    if (surfaceWidthPx > px) return false
                }
                // prefers-color-scheme — must agree with the platform signal.
                "prefers-color-scheme" -> when (value) {
                    "dark" -> if (!isDarkScheme) return false
                    "light" -> if (isDarkScheme) return false
                    // Any other keyword ("no-preference" was dropped from the
                    // spec; typos land here too) — not evaluatable.
                    else -> return reportUnsupported(query)
                }
                // Any other feature (orientation, hover, resolution, height,
                // …) is outside runtime v1 — whole bucket inactive.
                else -> return reportUnsupported(query)
            }
        }
        // All terms supported and all held.
        return true
    }

    /**
     * The conservative-inactive path: mark the query unhandled in the
     * PropertyTracker (so coverage reports stay honest) and warn ONCE per
     * query string. Always returns false so call sites can `return
     * reportUnsupported(q)` directly.
     */
    private fun reportUnsupported(query: String): Boolean {
        // Namespaced key so property-type and media-query misses don't mix.
        PropertyTracker.markUnhandled("Media[$query]")
        // Log once per distinct query; add() is atomic on the synced set.
        if (loggedUnsupported.add(query)) {
            try {
                // spec 06 §4: unsupported grammar is never a crash — surface
                // the miss to logcat for the harness/dev loop.
                Log.w(TAG, "media query not evaluatable at runtime v1 (bucket inactive): \"$query\"")
            } catch (_: RuntimeException) {
                // android.util.Log is unmocked in plain-JVM unit tests; the
                // PropertyTracker mark above already recorded the miss.
            }
        }
        return false
    }
}
