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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

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
     *   §5.1.1: the fold host is the member's CSS parent).
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
            textDecoration = decoration,
        )
    }
}
