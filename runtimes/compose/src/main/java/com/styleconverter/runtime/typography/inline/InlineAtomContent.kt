// typography/inline — wave 45 (lane X2): the Compose half of the ATOM
// RING. InlineAtomRing resolved WHAT an empty inline member paints (pure);
// this file mounts that answer into the SAME single-Text pipeline the
// wave-44 fold renders through, via the API U1's banner reserved for
// exactly this shape: androidx.compose.foundation.text.InlineTextContent +
// androidx.compose.ui.text.Placeholder. The placeholder reserves the
// ring's ADVANCE in the line (so "size ▮and" spaces like the Chromium
// ref); the ring itself is DRAWN, taller than the reservation, because a
// CSS inline border never grows the line box (CSS 2.1 §10.8) while a
// Compose placeholder taller than the line would.
package com.styleconverter.runtime.typography.inline

// The fold's atom list (marker-ordered) this module turns into content.
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
// InlineTextContent is Compose's ONLY inline-box vehicle: a Placeholder
// (width/height/vertical-align, in TextUnits) plus a composable painted
// inside the reserved box.
import androidx.compose.foundation.text.InlineTextContent
// appendInlineContent is the PUBLIC builder that plants foundation's own
// inline-content annotation tag — used instead of hardcoding the internal
// INLINE_CONTENT_TAG constant, which could drift across Compose versions.
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp

object InlineAtomContent {

    /** The inline-content id of the [index]-th atom — lane-keyed so a
     *  logcat line or a layout inspector dump names its owner. */
    private fun idFor(index: Int): String = "x2atom:$index"

    /**
     * The `inlineContent` map for [atoms] (marker order == map order —
     * LinkedHashMap via associate), each entry one ring.
     *
     * Placeholder geometry: WIDTH is the ring's border-box width (the
     * advance the empty inline occupies in the line — px == sp in the
     * density-1 harness). HEIGHT is deliberately just the font size with
     * [PlaceholderVerticalAlign.TextCenter]: always inside the line's own
     * ascent+descent band, so the placeholder can never grow the line box
     * the way reserving the full ring height would (+5px on
     * inherit-computed-001's 24px line). TextCenter pins the box to the
     * center of the surrounding text band; the ring is drawn about that
     * same center at its TRUE height (RingBox), overflowing the
     * reservation exactly like a CSS inline border overflows the line.
     *
     * Non-solid side styles paint solid here — surfaced via the tracker,
     * never silent (the corpus's empty-member borders are all solid).
     */
    fun contentMap(
        atoms: List<InlineRunFold.Atom>,
        fontSizePx: Float,
        currentColor: Color,
    ): Map<String, InlineTextContent> = atoms.mapIndexed { index, atom ->
        // Ring border-box width = the line advance the placeholder reserves.
        val (ringW, _) = InlineAtomRing.ringSizePx(atom.spec, fontSizePx)
        // Honest limitation, logged per style (PropertyTracker dedupes):
        // dotted/dashed/double/groove/… rings still paint solid bands.
        listOf(atom.spec.top, atom.spec.right, atom.spec.bottom, atom.spec.left)
            .filter { it.paints && it.style != "SOLID" }
            .forEach {
                // runCatching guards android.util.Log for the JVM suite
                // (PropertyTracker logs unguarded — same seam InlineRunPlan
                // and DocumentFontRegistry protect the same way).
                runCatching {
                    com.styleconverter.runtime.PropertyTracker.logUnhandled(
                        "BorderStyle",
                        "inline atom ring: border style ${it.style.lowercase()} painted solid")
                }
            }
        idFor(index) to InlineTextContent(
            Placeholder(
                // px == sp: the runtime's density-1 harness convention.
                width = ringW.sp,
                // ≤ the text band by construction — never grows the line.
                height = fontSizePx.sp,
                // Centered on the surrounding text's font band — for a
                // same-font empty inline the border-box is that band plus
                // symmetric borders, so its center IS the band center.
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            )
        ) {
            // The ring paints inside (and, vertically, OUTSIDE) this box.
            RingBox(atom.spec, fontSizePx, currentColor)
        }
    }.toMap()

    /**
     * Re-annotate [source] so each U+FFFC marker becomes one inline-content
     * slot bound to [ids] in order (the k-th marker ↔ ids[k] — the fold
     * emits atoms in marker order and [contentMap] preserves it).
     *
     * Runs AFTER the whole existing string-transform chain (small caps,
     * word-spacing spans, script fallback, rule-B pre-break): none of those
     * touches U+FFFC, so scanning the FINAL string is offset-proof against
     * transforms that shift positions (e.g. a soft-hyphen strip upstream).
     * Span/paragraph styles inside each segment survive via subSequence;
     * a count mismatch returns [source] untouched (defensive — the fold
     * bails on source-text U+FFFC, so counts can only agree today).
     */
    fun annotate(source: AnnotatedString, ids: List<String>): AnnotatedString {
        // Marker census — must match the atom list exactly to bind safely.
        val markers = source.text.count { it == InlineAtomRing.MARKER }
        if (markers == 0 || markers != ids.size) return source
        return buildAnnotatedString {
            // Walk the string, copying inter-marker segments verbatim and
            // planting one annotated slot per marker.
            var segStart = 0
            var atom = 0
            source.text.forEachIndexed { i, ch ->
                if (ch == InlineAtomRing.MARKER) {
                    // The styled text run before this marker, spans intact.
                    append(source.subSequence(segStart, i))
                    // The slot: foundation's own annotation tag, with the
                    // marker char itself as the alternate text (1 char, so
                    // downstream offsets keep their meaning).
                    appendInlineContent(ids[atom], InlineAtomRing.MARKER.toString())
                    atom++
                    segStart = i + 1
                }
            }
            // The tail after the last marker.
            append(source.subSequence(segStart, source.text.length))
        }
    }

    /**
     * One ring, drawn about the placeholder box's center at its true
     * border-box size. drawBehind ink is NOT clipped to the box, so the
     * vertical overhang (borders + the ascent/descent band beyond the
     * reserved font-size height) rasterises exactly like CSS's
     * line-box-independent inline borders.
     */
    @Composable
    internal fun RingBox(spec: InlineAtomRing.Spec, fontSizePx: Float, currentColor: Color) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    // True ring size (border-box; content = 0 × font band).
                    val (_, ringH) = InlineAtomRing.ringSizePx(spec, fontSizePx)
                    // Center the ring on the box center: TextCenter put the
                    // box mid-band, and the ring is band ± symmetric borders.
                    val y0 = (size.height - ringH) / 2f
                    // Paint the four solid bands (pure math, JVM-pinned).
                    InlineAtomRing.bands(spec, fontSizePx).forEach { band ->
                        drawRect(
                            // currentColor defers to the paragraph ink.
                            color = when (val p = band.paint) {
                                is InlineAtomRing.Paint.Concrete -> Color(p.r, p.g, p.b, p.a)
                                InlineAtomRing.Paint.CurrentColor -> currentColor
                            },
                            topLeft = Offset(band.xPx, y0 + band.yPx),
                            size = Size(band.widthPx, band.heightPx),
                        )
                    }
                }
        )
    }
}
