package com.styleconverter.runtime.color

// Modifier chain type — the applier is Modifier-in, Modifier-out.
import androidx.compose.ui.Modifier
// drawWithContent hosts the transparency group: an outer draw modifier's
// drawContent() replays every inner draw modifier AND the children, so the
// group covers exactly what CSS says opacity covers — "the element and its
// descendants as a whole".
import androidx.compose.ui.draw.drawWithContent
// drawIntoCanvas exposes the framework canvas: the Compose Canvas API only
// offers saveLayer with EXPLICIT Rect bounds, and this fix exists precisely
// because bounded layers crop — the unbounded form lives on the platform type.
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
// nativeCanvas: the android.graphics.Canvas that owns saveLayerAlpha(null, α).
import androidx.compose.ui.graphics.nativeCanvas
// 0..1 float alpha → the 0..255 int quantisation saveLayerAlpha takes.
import kotlin.math.roundToInt

/**
 * Applies CSS `opacity` to a Compose Modifier — wave-43 lane V2, the
 * Compose twin of iOS `color/OpacityApplier.swift` (whose SwiftUI
 * `.opacity(_:)` never clips — clipping in SwiftUI comes only from
 * `.clipped()`/`clipShape`, and the wave-42 gate shows the un-cropped
 * result: css-color/composited-filters-under-opacity ios-ref 0.9816 PASS).
 *
 * ## The spec
 * css-color-4 §3.3: opacity < 1 makes the element "a group" — the element
 * and its descendants are composited together at full opacity, then the
 * flattened group composites ONCE with the specified alpha (which is why
 * two 50%-opacity siblings under one opacity parent overlap UNIFORMLY).
 * The spec gives that group NO clip: descendants that paint outside the
 * element's border box (abspos children, floats, ink overflow) stay
 * visible; only `overflow`/`clip` properties ever crop.
 *
 * ## Why not `Modifier.alpha` (the previous implementation)
 * androidx.compose.ui:ui-android:1.11.4 bytecode, AlphaKt.alpha: it calls
 * graphicsLayer$default with default-mask 520187 — alpha and clip are the
 * only explicit params, and clip is pushed as iconst_1. `Modifier.alpha`
 * IS `graphicsLayer(alpha = α, clip = TRUE)`: a record-time clip at the
 * node's own bounds. Measured victim (wave42-final): WPT
 * css-color/composited-filters-under-opacity, android-ref 0.9401 — the
 * `left: 50px` abspos child under the `opacity: 0.5` parent is cropped at
 * the parent's right edge (x=116 in the capture) while ref/web/iOS paint
 * it in full.
 *
 * ## Why not `graphicsLayer(alpha = α, clip = false)` either
 * ui-graphics-android:1.11.4 bytecode, GraphicsLayerV29: setAlpha routes
 * to RenderNode.setAlpha only (requiresCompositingLayer() checks Offscreen
 * / blendMode / colorFilter / renderEffect — NOT alpha), and
 * applyCompositingStrategy(Auto) calls setHasOverlappingRendering(TRUE).
 * A RenderNode with alpha < 1 and overlapping rendering takes HWUI's
 * "simulate a layer" branch (AOSP frameworks/base/libs/hwui/pipeline/skia/
 * RenderNodeDrawable.cpp, setViewProperties): canvas->saveLayerAlpha(
 * &bounds, α·255) with bounds = the node's own 0,0,w,h — a composite-time
 * buffer that crops at node bounds no matter what clipToBounds says. The
 * same node-bounds buffer physics wave-42 W8 measured for
 * CompositingStrategy.Offscreen and retired from `isolation: isolate`
 * (see performance/IsolationApplier.kt).
 *
 * ## The fix — the IsolationApplier precedent, with a group alpha
 * An explicit UNBOUNDED framework-canvas layer: saveLayerAlpha(null, α)
 * sizes the layer to the CURRENT CLIP (android.graphics.Canvas contract:
 * null bounds ⇒ the layer covers the clip), so nothing the subtree paints
 * inside the visible canvas is ever cropped — and ancestor overflow clips
 * (already applied to the canvas) keep cropping exactly as CSS's overflow
 * path demands. A Skia layer composited at α IS the css-color-4 §3.3
 * transparency group: descendants flatten at full opacity inside it, the
 * group composites once — uniform overlap, no crop.
 */
object OpacityApplier {

    /**
     * Wrap [modifier]'s subtree in a CSS transparency group of [alpha].
     *
     * @param modifier Base modifier (the group wraps everything chained
     *   AFTER it plus the children — callers install it OUTERMOST of the
     *   paint they want attenuated, see ColorApplier.applyColors step 1).
     * @param alpha CSS opacity value; clamped to 0..1 per css-color-4 §3.3
     *   ("values outside the range are clipped to this range").
     *   DUAL DOMAIN since wave-44 U4: the SAME coerceIn serves css-color-4
     *   §2.2 `opacity` AND Filter Effects 1 §10.2 `filter: opacity()` (§10.2:
     *   amounts over 100% "must be clamped to 1", negatives floor at 0), so
     *   both specs' clamps land here and neither caller re-clamps.
     * @return The modifier unchanged for opacity ≥ 1, else with the group.
     */
    fun applyOpacity(modifier: Modifier, alpha: Float): Modifier {
        // css-color-4 §3.3 computed-value clamp: out-of-range values clip
        // to [0,1] (so 1.5 renders like 1, -0.5 renders like 0).
        val groupAlpha = alpha.coerceIn(0f, 1f)
        // opacity: 1 draws normally — no group, no layer. Parity with the
        // retired Modifier.alpha fast path (`alpha == 1f -> this`), so
        // explicit `opacity: 1` keeps producing a byte-identical chain.
        if (groupAlpha >= 1f) return modifier
        // The group. All android.graphics work stays INSIDE the draw
        // lambda: the plain-JVM unit suite folds this chain without
        // drawing (platform classes are throwing stubs there) — the same
        // discipline as IsolationApplier / FilterApplier.
        return modifier.drawWithContent {
            // Compose's Canvas.saveLayer demands explicit bounds; the
            // unbounded form only exists on the framework canvas.
            drawIntoCanvas { canvas ->
                // Null bounds ⇒ the layer covers the current clip — the
                // no-crop guarantee. α quantises to Skia's 0..255 lattice
                // (the identical quantisation RenderNode alpha lands on).
                canvas.nativeCanvas.saveLayerAlpha(null, (groupAlpha * 255f).roundToInt())
                // Everything the element paints — bg fills, gradient
                // layers, text, children however far they overflow —
                // renders INTO the group at full opacity.
                drawContent()
                // Composite the flattened group once at α: the §2.2
                // "element as a whole" semantic (uniform overlap — the
                // exact thing composited-filters-under-opacity verifies).
                canvas.nativeCanvas.restore()
            }
        }
    }
}
