package com.styleconverter.runtime.layout

// FloatAvoidLayout — the Compose adapter over the pure FloatAvoidPlan (wave-53
// lane L4; iOS twin Renderer/FloatAvoidLayout.swift). Lays out ONE proven
// float-then-BFC sibling list (FloatAvoidPlan.shape) inside the parent's block
// Column: floats by CSS 2.1 §9.5.1, the BFC root beside them per §9.5, the box
// reporting the BFC bottom (§10.6.3). Composed-WPT capture only — the caller
// (ComponentRenderer's block child loop) gates on LocalWptCaptureMode, so the
// dark-stage 327 corpus never reaches this layout. JVM tests cannot execute a
// Layout: the pure pins live in FloatAvoidPlanTest, the call-site pin in
// FloatAvoidSeamWiringTest, and the geometry is read on device by
// tools/titan/results/wave53-plan/float-avoid.geometry.py.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * Children MUST be emitted in wire order — the [shape]'s k floats, then the
 * BFC — one layout node each (the FloatRowLayout contract). Floats should be
 * composed under `LocalSelfAlignmentHandled provides true` so the right-float
 * TopEnd wrapper (floatEndAlignment) never places a box this layout places.
 */
@Composable
internal fun FloatAvoidLayout(
    shape: FloatAvoidShape,
    componentId: String,
    content: @Composable () -> Unit,
) {
    // One breadcrumb per mismatching host (no silent fallthrough), not per pass.
    val logged = remember(componentId) { BooleanArray(1) }
    Layout(content = content, modifier = Modifier) { measurables, constraints ->
        val k = shape.sides.size
        // A node-count drift would be a renderer bug: stack every node like the
        // Column it replaced (the frozen geometry) and say so once.
        if (measurables.size != k + 1) {
            if (!logged[0]) {
                logged[0] = true
                runCatching {
                    android.util.Log.w("FloatAvoidLayout", "node count ${measurables.size} != ${k + 1} " +
                        "— stacked fallback for component $componentId")
                }
            }
            // Column semantics: width-bounded, height-free, top-to-bottom.
            val ps = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
            val stackW = if (constraints.hasBoundedWidth) constraints.maxWidth else (ps.maxOfOrNull { it.width } ?: 0)
            val stackH = ps.sumOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
            return@Layout layout(stackW, stackH) {
                var y = 0
                ps.forEach { p -> p.place(0, y); y += p.height }
            }
        }
        // Floats measure UNBOUNDED (the FloatRowLayout P7 precedent): their own
        // px width/height modifiers decide the margin box, never the Column.
        val floats = measurables.take(k).map { it.measure(Constraints()) }
        // The BFC measures with the Column's own child constraints, so its
        // rendering (content, line boxes, height) is what the frozen path drew.
        val bfc = measurables[k].measure(constraints.copy(minWidth = 0, minHeight = 0))
        // Incoming width: the Column's content width (bounded in block flow);
        // the unbounded fallback is the widest child, like a shrink-to-fit box.
        val incoming = if (constraints.hasBoundedWidth) constraints.maxWidth.toDouble()
            else (floats + bfc).maxOf { it.width }.toDouble()
        // The pure plan in device px: CSS px == dp at the capture density, so
        // the shape's px values scale by `density` (MeasureScope is a Density).
        val plan = FloatAvoidPlan.place(
            shape = shape,
            floatWidths = floats.map { it.width.toDouble() },
            floatHeights = floats.map { it.height.toDouble() },
            incomingWidth = incoming,
            bfcHeight = bfc.height.toDouble(),
            scale = density.toDouble(),
        )
        // Report the content width the Column offered (a block box fills its
        // containing block) and the §10.6.3 height, inside the envelope.
        val reportedW = if (constraints.hasBoundedWidth) constraints.maxWidth
            else plan.width.roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        val reportedH = plan.height.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(reportedW, reportedH) {
            // Wire order, so the BFC (and its inline content) paints over the
            // floats — CSS 2.1 Appendix E step 7 above step 5 (001's orange).
            (floats + bfc).forEachIndexed { i, p ->
                // Whole-px placement, ties half-up (the FloatRowLayout P9 rounding);
                // place() beyond the box draws unclipped (overflow: visible).
                p.place(plan.x[i].roundToInt(), plan.y[i].roundToInt())
            }
        }
    }
}
