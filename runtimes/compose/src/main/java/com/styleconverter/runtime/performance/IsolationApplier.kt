package com.styleconverter.runtime.performance

// Turns IsolationConfig into a Modifier. `isolation: isolate` (css-compositing-1
// §4) makes the element form an ISOLATED GROUP: descendant mix-blend-mode
// composites against this group instead of the ancestor backdrop. Crucially
// the spec gives the group NO clip — an isolated element with overflowing
// children (abspos descendants, filter halos) still paints them in full.

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/**
 * Applies CSS `isolation` to a Compose Modifier.
 *
 * ## Why an unbounded saveLayer and not `CompositingStrategy.Offscreen`
 * The previous implementation wrapped the subtree in
 * `graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)`.
 * HWUI renders an offscreen-composited RenderNode into a buffer SIZED TO
 * THE NODE'S OWN BOUNDS — and this modifier is installed at StyleApplier
 * step 0, OUTERMOST, i.e. OUTER of step 4's `absoluteOffset` and of every
 * child that paints beyond the box. Everything outside the un-offset slot
 * was silently discarded. Measured on WPT
 * `css/filter-effects/backdrop-filter-isolation-isolate.html`
 * (wave41-final, android-ref 0.8811): `.stacking-context` (`isolation:
 * isolate; position: absolute; left: 120px`) holds a 160×160 abspos child
 * at `top: 30px; left: -90px` — the Android capture renders ONLY the ink
 * that intersects the grandparent's un-offset 100×100 slot (the wave-41 T5
 * verification: visible patch == predicted intersection exactly); the
 * offset green box and most of the filtered child are gone.
 *
 * The positionOffset plumbing CANNOT repair this path: a graphicsLayer's
 * buffer geometry is not parameterizable — Compose sizes it to the node,
 * full stop. So the group is built the same way FilterApplier's
 * colour-matrix group is (its saveLayer precedent replaced a RenderEffect
 * buffer with the identical crop physics): an explicit `canvas.saveLayer`.
 * Bounds are passed as NULL — the framework then sizes the layer to the
 * CURRENT CLIP (android.graphics.Canvas#saveLayer: "a null bounds means
 * the layer covers the clip"), so nothing this subtree paints inside the
 * visible canvas is ever cropped, wherever layout offsets or overflowing
 * children put it. A Skia layer IS a transparency group, so the observable
 * CSS effect is preserved: descendant blend modes composite against this
 * layer and stop leaking to ancestors (css-compositing-1 §4, the reason
 * this applier exists).
 */
object IsolationApplier {

    /**
     * Add an isolated (non-clipping) group when the config requests it.
     *
     * @param modifier Base modifier
     * @param config Extracted isolation config
     * @return Modifier unchanged (AUTO) or wrapped in an unbounded
     *   transparency group (ISOLATE).
     */
    fun applyIsolation(modifier: Modifier, config: IsolationConfig): Modifier {
        // AUTO is the default — return the modifier untouched to avoid paying
        // for an extra offscreen layer on every element.
        if (!config.hasIsolation) return modifier
        // ISOLATE — one unbounded transparency group around the subtree.
        // All android.graphics work stays INSIDE the draw lambda (the JVM
        // unit suite introspects this chain without drawing; platform
        // classes are throwing stubs there — same discipline as
        // FilterApplier.applyGroupColorFilters).
        return modifier.drawWithContent {
            // Compose's Canvas exposes saveLayer only with explicit Rect
            // bounds; the UNBOUNDED form lives on the framework canvas.
            drawIntoCanvas { canvas ->
                // Null bounds + null paint: a plain clip-sized transparency
                // group — the isolation boundary with no crop and no
                // colour/alpha side effects.
                canvas.nativeCanvas.saveLayer(null, null)
                // Everything the element paints — background, borders,
                // children, however far they overflow — renders into the
                // group.
                drawContent()
                // Composite the group as ONE surface; descendant blend
                // modes have already resolved against it, so ancestors
                // only ever see the flattened result (css-compositing-1
                // §4's observable contract).
                canvas.nativeCanvas.restore()
            }
        }
    }
}
