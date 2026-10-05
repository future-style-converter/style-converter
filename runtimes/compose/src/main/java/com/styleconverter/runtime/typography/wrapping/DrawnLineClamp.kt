package com.styleconverter.runtime.typography.wrapping

// DrawnLineClamp.kt
// typography/wrapping — wave 52 (lane L9). ONE reader for "is there a
// fixed-count `line-clamp` whose marker is DRAWN, and what is its cap?"
//
// css-overflow-4 §5.1 expands `line-clamp: <n>` to `max-lines: <n>` +
// `block-ellipsis: auto` + `continue: discard`, and §4.2 makes `auto` the
// UA ellipsis string — so a bare `line-clamp: N` MUST paint a marker. Two
// Compose consumers need that answer and must never disagree about it:
//   • ComponentRenderer.placeholderOverflow (F3) — a soft-wrapped run with
//     a drawn clamp answers TextOverflow.Ellipsis instead of Compose's
//     Clip default, which is why no Compose clamp in the corpus ever
//     painted "…" (block-ellipsis-001 android P 0.9836 = `…room uncha`
//     clipped; -032 android f 0.9397: fold landed, no marker in any box);
//   • PreBreakPipeline.preBreak (F4) — a FIRED run (softWrap off, where
//     Ellipsis is the wave-39 finalMaxLines landmine) bakes the marker into
//     its string via GreedyLineBreaker.clampLines instead.
// Null when the author forbade the marker (`no-ellipsis` / `""`,
// LineClampWire.markerSuppressed — WPT block-ellipsis-023/-024), for
// `line-clamp: none`, and for the bare `max-lines` longhand (its
// block-ellipsis is the initial `none`): only the LineClamp declarations
// are consulted, so a `MaxLines` count can never leak in as a drawn clamp.
//
// Pure Kotlin (no Compose, no Android) so the JVM suite pins it without a
// device; it reads through TextStyleApplier's shared count reader and
// LineClampWire's shared marker reader, so no third reading of the wire
// exists (retro finding A5#4's "one shared reader" rule).

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.typography.TextStyleApplier

object DrawnLineClamp {

    /** The IR type the converter emits for `line-clamp`. */
    private const val TYPE: String = "LineClamp"

    /**
     * The cap of a fixed-count `line-clamp` whose marker is drawn, or null.
     *
     * @param properties the component's (inheritance-merged) property list
     *   — the same list the placeholder's `maxLines` is read from.
     */
    @JvmStatic
    fun cap(properties: List<IRProperty>): Int? {
        // Only `line-clamp` declarations — `max-lines` alone draws nothing.
        val clampOnly = properties.filter { it.type == TYPE }
        // `none` reads null through the shared count reader (it consults
        // LineClamp first and MaxLines second — absent here by construction).
        val count = TextStyleApplier.extractMaxLines(clampOnly) ?: return null
        // A non-positive cap is no cap (the wave-19 sub-1 guard's rule).
        if (count <= 0) return null
        // The `<'block-ellipsis'>` component: -023 / -024 forbid the marker.
        if (TextStyleApplier.extractLineClampMarkerSuppressed(clampOnly)) return null
        return count
    }
}
