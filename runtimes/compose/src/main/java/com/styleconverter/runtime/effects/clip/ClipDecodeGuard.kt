package com.styleconverter.runtime.effects.clip

// ClipDecodeGuard — wave 49 lane F1. The clip category's TOTALITY seam.
//
// ── WHY THIS FILE EXISTS (measured, not hypothetical) ──────────────────
// Wave 49 lane A4 wired `clip-path` into two NEW callers that run for every
// component of every document:
//   • CanvasRootHoist.establishesUnescapableClip — the clip-ancestry channel,
//     evaluated once per node in `collectCanvasHoisted` / `anyOutOfFlowBox`
//     and once per component in ComponentRenderer.
//   • rootCanvasClipConfig — evaluated once per composed capture canvas.
// Both hop straight into ClipPathExtractor. Before wave 49 the only caller
// was the per-component style chain, whose throw ComponentRenderer already
// swallows (`try { StyleApplier.applyProperties(…) } catch { Modifier }`),
// so a decode failure cost ONE component's styles. After wave 49 the same
// throw escapes from `CanvasRootHoist.Host`'s
// `remember(roots) { collectCanvasHoisted(roots) }` DURING COMPOSITION, and
// apps/android-harness installs no error boundary of any kind (grep for
// ErrorBoundary / uncaughtException over apps/android-harness/app/src/main
// returns nothing) — so the capture Activity dies and the whole section
// captures nothing.
//
// The wave-49 skeptic walk measured exactly that: all 1435 wave-48 per-test
// IR documents walked clean on the pre-A4 tree, and three of them
// (css-masking/clip-path/clip-path-circle-closest-corner, its
// -farthest-corner twin, and clip-path-ellipse-closest-farthest-corner) died
// post-A4 with `IllegalArgumentException: … JsonObject … is not a
// JsonPrimitive` inside ClipPathExtractor.readCenterPercent. That ROOT CAUSE
// is fixed in the extractor (see ClipPathExtractor.positionObject); this file
// is the defence in depth that keeps the next malformed payload from being a
// dead capture instead of a dropped property.
//
// ── WHY `false` / `null` IS THE CORRECT ANSWER, NOT JUST THE SAFE ONE ──
// The predicate and the applier share ONE decoder by design (see
// ClipPathSubtreeScope's doc): a declaration bag this decoder cannot read is
// a bag that emits no `Modifier.clip`. So "no clip is painted" is the ground
// truth, and "no clip to escape" / "no page clip" is what the fallbacks
// assert. Same policy ComponentRenderer already applies one layer up.

import com.styleconverter.runtime.core.ir.IRLog

/** Logcat/stdout tag; matches the category name used by the clip appliers. */
private const val TAG = "ClipPath"

/**
 * Once-per-key breadcrumb for clip decoding failures.
 *
 * Same shape and same reason as [com.styleconverter.runtime.color.GradientLog]:
 * the guarded call sites run per node per recomposition, so an unguarded log
 * would flood logcat, while "no silent fallthroughs" still wants the failure
 * to be LOUD the first time. Keyed on call site + exception class, which is a
 * bounded key space — payload text is deliberately not part of the key.
 */
internal object ClipDecodeLog {
    // Synchronized: composition runs on the UI thread, the pure walks and the
    // JVM tests run on whatever thread called them.
    private val seen = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Log [message] the first time [key] is seen; IRLog prints on the JVM too. */
    fun once(key: String, message: String) {
        if (seen.add(key)) IRLog.warn(TAG, message)
    }

    /** Test hook: clear the once-guard so JVM cases start known-clean. */
    internal fun resetForTest() = seen.clear()
}

/**
 * Run a clip-path decode that MUST NOT be able to take a composition down.
 *
 * @param where the call site, used as the log key so one malformed document
 *   cannot spam a line per node per frame.
 * @param fallback the value that states "no clip is painted here" for this
 *   call site — `false` for the ancestry predicate, `null` for the root
 *   canvas config.
 * @param decode the ClipPathExtractor hop being guarded.
 *
 * Catches [Exception] rather than a narrower type on purpose: the wire is
 * attacker-shaped from this code's point of view (any JSON the converter or a
 * hand-written fixture emits), and the extractor's failure modes are the
 * whole kotlinx-serialization cast family — `IllegalArgumentException` from
 * `JsonElement.jsonPrimitive` / `.jsonObject`, `NumberFormatException` from a
 * malformed numeric literal, `IllegalStateException` from a bad discriminator.
 * [Error] is deliberately NOT caught: an OOM or a StackOverflow is not a
 * decode failure and must keep propagating.
 */
internal fun <T> clipDecodeOrElse(where: String, fallback: T, decode: () -> T): T =
    try {
        decode()
    } catch (e: Exception) {
        // Loud once, then quiet: the breadcrumb names the site and the class
        // so a real wire regression is still findable in a capture log.
        ClipDecodeLog.once(
            "$where:${e::class.simpleName}",
            "clip-path decode failed at $where (${e::class.simpleName}: ${e.message}) — " +
                "treating this box as painting no clip",
        )
        fallback
    }
