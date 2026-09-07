// typography/inline — wave 47 (lane Z6): the STYLED-SPAN RING's Compose
// mount. InlineSpanRing admits a folded member's styling and the fold
// records its range over the MERGED string; this module carries those
// ranges through the leaf pipeline's string surgery (soft-hyphen strip +
// rule-B pre-break — InlineSpanRing.alignment) and overlays one
// SpanStyle per span on the final AnnotatedString the single Text
// renders. The Compose twin of the iOS PlaceholderLabel segment build.
package com.styleconverter.runtime.typography.inline

// The final-string span overlay is ordinary AnnotatedString machinery —
// the exact vehicle the small-caps / word-spacing / script-fallback
// passes already use, so member styles compose with (and, added last,
// override) those paragraph-level spans where they overlap.
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
// Wave 48 (lane W4): the registered face's ascent ratio — the sup/sub
// baseline shift converts px → BaselineShift through it (see spanStyle).
import com.styleconverter.runtime.typography.HalfLeadingBaseline

/**
 * InlineSpanContent — overlay the fold's member [InlineRunFold.Span]s
 * onto the paragraph's final AnnotatedString.
 *
 * Called at the very end of PlaceholderContent's string pipeline (after
 * small caps / word-spacing / script fallback / atom annotation), so:
 *   • the ranges are remapped ONCE, from the fold's merged string into
 *     the final render string, via [InlineSpanRing.alignment] — the two
 *     differ only by the soft-hyphen strip and the rule-B pre-break's
 *     newline surgery (both platforms' op set, proven there);
 *   • member spans are ADDED LAST, so where they overlap a paragraph
 *     span (word-spacing kerns, script-fallback fonts) the member's
 *     attribute wins — the CSS nesting order (the member is the inner
 *     box).
 * Returns null when the alignment cannot explain the surgery — the
 * caller logs and renders the un-spanned base: degraded STYLE, never
 * wrong glyphs (and never silent — the log is the breadcrumb).
 */
object InlineSpanContent {

    /**
     * @param base the paragraph's fully-transformed AnnotatedString (the
     *   exact instance the Text would render without spans).
     * @param original the fold's merged string ([InlineRunFold.Outcome.
     *   Folded.text]) — the coordinate space the spans were recorded in.
     * @param spans the fold's member ranges, wire order.
     * @param paragraphFontSizePx the paragraph's RESOLVED font size — the
     *   base a member's em/% font-size factor multiplies (css-values-4
     *   §6.1.1: the fold host is the member's CSS parent).
     */
    fun overlay(
        base: AnnotatedString,
        original: String,
        spans: List<InlineRunFold.Span>,
        paragraphFontSizePx: Float,
    ): AnnotatedString? {
        // One alignment for all spans — null the moment the final string
        // cannot be explained by the modeled op set (caller falls back).
        val map = InlineSpanRing.alignment(original, base.text) ?: return null
        return buildAnnotatedString {
            // The base keeps every existing span style AND annotation
            // (atom InlineTextContent bindings included) — Builder.append
            // copies them all verbatim.
            append(base)
            for (span in spans) {
                // Guard impossible ranges defensively (the fold only
                // emits 0 ≤ start ≤ end ≤ original.length).
                if (span.start < 0 || span.end > original.length || span.start > span.end) continue
                val start = map[span.start]
                val end = map[span.end]
                // A range fully consumed by the surgery (all its chars
                // deleted) styles nothing — skip the empty overlay.
                if (start >= end) continue
                addStyle(spanStyle(span.style, paragraphFontSizePx), start, end)
            }
        }
    }

    /** One member [InlineSpanRing.Style] → the Compose SpanStyle the
     *  range renders with. Unset fields stay null/unspecified so the
     *  paragraph's own style shows through — the CSS inheritance model. */
    private fun spanStyle(style: InlineSpanRing.Style, paragraphFontSizePx: Float): SpanStyle {
        // Member font-size: absolute px, or the em/% factor against the
        // paragraph's resolved size (px == sp in the density-1 harness).
        val sizePx = style.fontSizePx ?: style.fontSizeEm?.let { it * paragraphFontSizePx }
        // Wave 48 (lane W4) — the sup/sub UA shift. Blink resolves the
        // super/sub keywords as PIXELS off the PARENT's computed size
        // (LayoutBoxModelObject::VerticalPosition: super raises size/3+1,
        // sub lowers size/5+1 — ref-verified in VerticalShift's banner).
        // Compose's BaselineShiftSpan applies `ceil(ascent × multiplier)`
        // to TextPaint.baselineShift, ascent being the SPAN face's signed
        // ascent (−ASCENT_EM × span size for the registered Inter —
        // HalfLeadingBaseline's pinned 1984/2048), so the px shift maps to
        // multiplier = shiftPx / (ASCENT_EM × spanSizePx); the span's ceil
        // rounds within 1px, the same granularity Blink's LayoutUnit snap
        // leaves. Positive multiplier raises (BaselineShift.Superscript
        // is +0.5), so `up` keeps the sign and sub negates it.
        val baselineShift = style.shift?.let { s ->
            // The pixel rule lives in ONE pinned helper (fix lane F5 —
            // InlineSpanRing.shiftPx: parentPx ?? parentEm × paragraph,
            // then super parent/3+1 / sub −(parent/5+1)), shared with the
            // iOS twin so the two seams cannot drift.
            val shiftPx = InlineSpanRing.shiftPx(s, paragraphFontSizePx)
            // NAMED LIMITATION (fix lane F5): the px → multiplier
            // conversion below hard-codes the registered Inter face's
            // ascent ratio (HalfLeadingBaseline.ASCENT_EM = 1984/2048 —
            // the same repo-pinned constant DecorationOps and
            // TextStyleApplier's decoration math already assume). If the
            // paragraph resolves to a DIFFERENT face — a host
            // `font-family: monospace/serif` through CssFontFamilyResolver,
            // a document @font-face via DocumentFontRegistry, or a
            // script-fallback face over these glyphs — the realized px is
            // off by the ratio of that face's real ascent to Inter's.
            // Why no PropertyTracker breadcrumb here: this seam receives
            // only the resolved paragraph SIZE — the face is picked in the
            // renderer's TextStyle (ComponentRenderer/TextStyleApplier),
            // outside overlay's inputs, so the divergence is not
            // detectable from this module without a cross-module signature
            // change (and Compose's PropertyTracker has no logOnce; this
            // file also runs under plain-JVM tests where android.util.Log
            // throws). Stated here instead — not silent.
            // The span renders at ITS resolved size — that face's ascent
            // is the multiplier's base.
            BaselineShift(shiftPx / (HalfLeadingBaseline.ASCENT_EM * (sizePx ?: paragraphFontSizePx)))
        }
        // underline / line-through per css-text-decor-3 §2.1; Compose
        // paints them in the span's text color (the ring's admission gate
        // only let an equal decoration color through, so this is exact).
        val decoration = when {
            style.underline && style.lineThrough ->
                TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
            style.underline -> TextDecoration.Underline
            style.lineThrough -> TextDecoration.LineThrough
            else -> null
        }
        return SpanStyle(
            // The member's own `color`, or unspecified = inherit.
            color = style.ink?.let { Color(it.r, it.g, it.b, it.a) } ?: Color.Unspecified,
            // TextUnit.Unspecified inherits the paragraph size.
            fontSize = sizePx?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
            fontWeight = style.fontWeight?.let { FontWeight(it) },
            fontStyle = if (style.italic) FontStyle.Italic else null,
            // Wave 48 (lane W4): null for every shift-less span keeps the
            // pre-wave-48 SpanStyle byte-identical.
            baselineShift = baselineShift,
            textDecoration = decoration,
        )
    }
}
