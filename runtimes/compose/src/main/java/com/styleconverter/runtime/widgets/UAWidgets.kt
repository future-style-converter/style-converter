package com.styleconverter.runtime.widgets

// Lane W2 (wave 20) — the Compose EXECUTION half of the UA-widget
// replicas: replays the pure UAWidgetsGeometry paint plan on a Canvas.
// All geometry/palette decisions live in UAWidgetsGeometry/-Resolve (the
// byte-parallel pin table); this file only maps ops onto DrawScope calls,
// so nothing here can shift a widget without the pure pins moving first.
// Reached ONLY through the WPT-capture mount hook in ComponentRenderer
// (LocalWptCaptureMode) — the dark-stage 327 baseline never composes it.

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.typography.InterFontFamily

object UAWidgets {

    /** Packed-ARGB → Compose color (the plan speaks Long for pin parity). */
    private fun argb(v: Long): Color = Color(v.toInt())

    /**
     * Paint one resolved widget replica at its intrinsic size. Labels
     * render through the SAME Inter face the runtime's real-text path
     * uses (InterFont.kt) at the UA control font size — the ref pins
     * Inter too (capture-browser-ref.mjs REF_FONT_STACK), so glyph ink
     * matches the browser-ref within AA noise.
     */
    @Composable
    fun Render(spec: UAWidgetsGeometry.Spec) {
        // Label style: Chromium UA control font-size on the harness Inter
        // face, black ink comes per-op from the plan.
        val measurer = rememberTextMeasurer()
        val style = TextStyle(fontFamily = InterFontFamily, fontSize = UAWidgetsGeometry.FONT.sp)
        // The plan's width probe: real Inter advance widths at FONT px —
        // capture density is 1 (px == dp/sp), so IntSize.width IS CSS px.
        val measure: (String) -> Float = { s ->
            measurer.measure(AnnotatedString(s), style).size.width.toFloat()
        }
        // Pure geometry: intrinsic border-box + the ordered op list.
        val (w, h) = UAWidgetsGeometry.intrinsicSize(spec, measure)
        val ops = UAWidgetsGeometry.plan(spec, measure)
        // Nothing to paint (input type=hidden) → no box at all.
        if (w <= 0f || h <= 0f) return
        Canvas(Modifier.size(w.dp, h.dp)) {
            // Replay in plan order — ops are authored back-to-front.
            ops.forEach { op -> draw(op, measurer, style) }
        }
    }

    /** One-op executor — DrawScope calls only, no decisions. */
    private fun androidx.compose.ui.graphics.drawscope.DrawScope.draw(
        op: UAWidgetOp,
        measurer: androidx.compose.ui.text.TextMeasurer,
        style: TextStyle
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
            // fix 4: circle strokes (the radio ring) draw at the shared
            // RING_SW (1.4px) — an AA'd 1px circle sampled ~#CCC where
            // the ref ring samples ~#9A (see the constant's ref probe).
            is UAWidgetOp.StrokeCircle ->
                drawCircle(argb(op.color), op.r, Offset(op.cx, op.cy), style = Stroke(UAWidgetsGeometry.RING_SW))
            // Check mark / resize grip / menulist chevron arms — round
            // caps soften the joints the same way Chromium's vectors do.
            is UAWidgetOp.Line ->
                drawLine(argb(op.color), Offset(op.x1, op.y1), Offset(op.x2, op.y2), op.sw, StrokeCap.Round)
            // Label — Inter at FONT px, top-left anchored per the plan.
            is UAWidgetOp.Label ->
                drawText(measurer, op.text, Offset(op.x, op.y), style.copy(color = argb(op.color)))
        }
    }
}
