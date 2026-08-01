package com.styleconverter.runtime.widgets

// Lane W2 (wave 20) — the Compose EXECUTION half of the UA-widget
// replicas: replays the pure UAWidgetsGeometry paint plan on a Canvas.
// All geometry/palette decisions live in UAWidgetsGeometry/-Resolve (the
// byte-parallel pin table); this file only maps ops onto DrawScope calls,
// so nothing here can shift a widget without the pure pins moving first.
// Reached ONLY through the WPT-capture mount hook in ComponentRenderer
// (LocalWptCaptureMode) — the dark-stage 327 baseline never composes it.
//
// Wave 22 lane INK (A-RC2): label WIDTHS no longer come from a device
// face. They come from UAControlFontMetrics, the shared Arial-metric
// advance table, because that is what Chromium's UA control font is —
// see that file's header for the ref evidence. Compose therefore only
// supplies GLYPH SHAPES and the face's ascent; every x position in a
// label is pinned and byte-identical to the iOS twin.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object UAWidgets {

    /** Packed-ARGB → Compose color (the plan speaks Long for pin parity). */
    private fun argb(v: Long): Color = Color(v.toInt())

    /**
     * A-RC2 — the two UA control faces, chosen for METRIC proximity to
     * the ref's Chromium faces, not for brand:
     *  • SansSerif (Roboto on every Android build) stands in for the
     *    Arial-metric control face. Roboto is NOT Arial-metric, which is
     *    exactly why label advances come from the pinned table instead:
     *    each glyph is drawn at its Arial x, so only the glyph OUTLINES
     *    differ (Roboto's are ~2% off per glyph — a stated, sub-glyph
     *    delta, not an accumulating run-length error). Bundling Arimo or
     *    Liberation Sans in the harness asset pack would close even that;
     *    it is deliberately NOT done here because the box geometry (what
     *    the SSIM band actually pays for) is already exact without it.
     *  • Monospace (Droid/Noto Sans Mono) is a true 0.6em-advance face,
     *    matching the ref's textarea font advance for advance.
     * The iOS twin picks Helvetica (which IS Arial-metric) and Courier;
     * both natives place glyphs on the SAME pinned grid, which is what
     * keeps the iOS↔Android lockstep.
     */
    private val CONTROL_FAMILY = FontFamily.SansSerif
    private val MONO_FAMILY = FontFamily.Monospace

    /** Paint one resolved widget replica at its intrinsic size. */
    @Composable
    fun Render(spec: UAWidgetsGeometry.Spec) {
        // Text measurer for glyph rasterisation + the face ascent probe.
        val measurer = rememberTextMeasurer()
        // A-RC2: the plan's width probe is the PURE Arial-metric table —
        // no device face participates in box sizing any more.
        val measure: (String) -> Float = { s ->
            UAControlFontMetrics.advance(s, UAWidgetsGeometry.FONT)
        }
        // Pure geometry: intrinsic border-box + the ordered op list.
        val (w, h) = UAWidgetsGeometry.intrinsicSize(spec, measure)
        val ops = UAWidgetsGeometry.plan(spec, measure)
        // Nothing to paint (input type=hidden) → no box at all.
        if (w <= 0f || h <= 0f) return
        Canvas(Modifier.size(w.dp, h.dp)) {
            // Replay in plan order — ops are authored back-to-front.
            ops.forEach { op -> draw(op, measurer) }
        }
    }

    /** One-op executor — DrawScope calls only, no decisions. */
    private fun androidx.compose.ui.graphics.drawscope.DrawScope.draw(
        op: UAWidgetOp,
        measurer: TextMeasurer,
    ) {
        when (op) {
            // Plain fill — field interiors, color swatch, chrome slabs.
            is UAWidgetOp.FillRect ->
                drawRect(argb(op.color), Offset(op.x, op.y), Size(op.w, op.h))
            // 1px hairline: Stroke(1f) centered on the half-pixel inset
            // rectangle the plan pre-computed (x.5 coords → crisp 1px).
            is UAWidgetOp.StrokeRect ->
                drawRect(argb(op.color), Offset(op.x, op.y), Size(op.w, op.h), style = Stroke(1f))
            is UAWidgetOp.FillRRect ->
                drawRoundRect(argb(op.color), Offset(op.x, op.y), Size(op.w, op.h), CornerRadius(op.r, op.r))
            is UAWidgetOp.StrokeRRect ->
                drawRoundRect(argb(op.color), Offset(op.x, op.y), Size(op.w, op.h), CornerRadius(op.r, op.r), style = Stroke(1f))
            is UAWidgetOp.FillCircle ->
                drawCircle(argb(op.color), op.r, Offset(op.cx, op.cy))
            // A-RC4: circle strokes (the radio ring) draw at the shared
            // RING_SW (1.0px) so the ring stays INSIDE the 13px box —
            // see the constant's ref probe in UAWidgetsGeometry.
            is UAWidgetOp.StrokeCircle ->
                drawCircle(argb(op.color), op.r, Offset(op.cx, op.cy), style = Stroke(UAWidgetsGeometry.RING_SW))
            // Check mark / resize grip / menulist chevron arms — round
            // caps soften the joints the same way Chromium's vectors do.
            is UAWidgetOp.Line ->
                drawLine(argb(op.color), Offset(op.x1, op.y1), Offset(op.x2, op.y2), op.sw, StrokeCap.Round)
            // Label — see drawLabel for the baseline + pinned-advance
            // contract (A-RC2/A-RC4).
            is UAWidgetOp.Label -> drawLabel(op, measurer)
        }
    }

    /**
     * A-RC2/A-RC4 label execution. Two rules, both pinned:
     *  1. BASELINE anchoring — the op carries the ref's alphabetic
     *     baseline, so the layout top is `baseline − firstBaseline` of
     *     THIS face. That is why swapping the face cannot move the ink:
     *     the ascent is measured, never assumed.
     *  2. PINNED ADVANCES — each character is drawn at the x the shared
     *     Arial-metric table gives, so a label's glyph grid is identical
     *     on both natives even though the outlines are not.
     */
    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(
        op: UAWidgetOp.Label,
        measurer: TextMeasurer,
    ) {
        // The face this op asks for (mono = textarea content).
        val family = if (op.mono) MONO_FAMILY else CONTROL_FAMILY
        // Style shared by the ascent probe and every glyph draw.
        val style = TextStyle(fontFamily = family, fontSize = op.size.sp, color = argb(op.color))
        // Ascent probe: any glyph run in this style has the same first
        // baseline, so one measurement converts baseline → layout top.
        val top = op.baseline - measurer.measure(AnnotatedString("Hg"), style).firstBaseline
        // Walk the string, advancing by the PINNED table, not the face.
        var dx = op.x
        op.text.forEach { ch ->
            drawText(measurer, ch.toString(), Offset(dx, top), style)
            dx += if (op.mono) UAControlFontMetrics.MONO_ADVANCE_EM * op.size
                  else UAControlFontMetrics.charAdvance(ch, op.size)
        }
    }
}
