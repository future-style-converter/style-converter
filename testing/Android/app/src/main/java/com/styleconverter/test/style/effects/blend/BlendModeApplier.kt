package com.styleconverter.test.style.effects.blend

// Applies CSS mix-blend-mode and background-blend-mode using Compose drawWithContent +
// an explicit Paint with the requested BlendMode. graphicsLayer alone cannot express
// "composite this subtree against the parent using BlendMode X" — drawWithContent +
// saveLayer (via drawIntoCanvas) is the canonical workaround on Compose.

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/**
 * Applies CSS mix-blend-mode using Compose drawing primitives.
 *
 * ## CSS Property
 * ```css
 * .blended-element {
 *     mix-blend-mode: multiply;
 * }
 * ```
 *
 * ## Compose Implementation
 * Uses drawWithContent + Paint(blendMode) wrapped in Canvas.saveLayer so the
 * subtree composites against whatever was drawn below with the target blend
 * mode. An offscreen graphicsLayer is used underneath so antialiased content
 * is captured before blending.
 *
 * ## Limitations
 * - CSS mix-blend-mode requires the sibling underneath to already be drawn
 *   in the same compositing group. In Compose this is usually the parent Box.
 * - Some CSS modes (e.g. plus-darker, color-dodge) map imperfectly on older
 *   Android versions — the BlendMode enum is available on API 29+ for every
 *   mode we emit; older APIs fall back to Compose's default SrcOver.
 * - `mix-blend-mode: normal` is a no-op (SrcOver already is Compose default).
 */
object BlendModeApplier {

    /**
     * Apply blend mode to a modifier using the config wrapper.
     *
     * @param modifier Base modifier
     * @param config BlendModeConfig
     * @return Modified Modifier with blend mode applied
     */
    fun applyBlendMode(modifier: Modifier, config: BlendModeConfig): Modifier {
        // No blend mode, or SrcOver (normal) — return as-is. hasBlendMode already
        // filters SrcOver, but we double-check for safety.
        if (!config.hasBlendMode || config.blendMode == null) {
            return modifier
        }
        return applyBlendMode(modifier, config.blendMode)
    }

    /**
     * Apply blend mode directly with BlendMode value.
     *
     * @param modifier Base modifier
     * @param blendMode BlendMode to apply
     * @return Modified Modifier
     */
    fun applyBlendMode(modifier: Modifier, blendMode: BlendMode?): Modifier {
        // SrcOver (which CSS "normal" maps to) is Compose's default — skip.
        if (blendMode == null || blendMode == BlendMode.SrcOver) {
            return modifier
        }
        // CSS mix-blend-mode blends THIS element's painted output with what's
        // already on the parent canvas (its sibling/ancestor stacking-context
        // content). The recipe in Compose:
        //   drawWithContent { saveLayer(paint=blendMode); drawContent(); restore() }
        // saveLayer pushes a fresh offscreen buffer onto the active canvas;
        // drawContent() renders the rest of the modifier chain (bg, borders,
        // children) into that buffer; restore() pops the buffer back onto the
        // underlying canvas using paint.blendMode → producing the multiply /
        // screen / overlay etc. against the parent canvas.
        //
        // We previously prefixed this with `graphicsLayer(compositingStrategy
        // = Offscreen)`. That created an additional isolated offscreen surface
        // that the saveLayer's restore composited against — but that surface
        // starts empty/transparent, so multiplying against it produced the
        // wrong result (Edge case: BlendMode_Multiply rendered as fully-opaque
        // red on Android, SSIM 0.674 vs iOS↔web's 0.945). Removing the
        // graphicsLayer means saveLayer's paint.blendMode now composites
        // against whatever the parent painted (the dark CaptureCanvas bg in
        // our screenshot harness, the page bg in real-world usage), matching
        // CSS / iOS `.blendMode(_:)` / web `mix-blend-mode` semantics.
        //
        // The original "avoid partial alpha artifacts at edges" rationale for
        // the offscreen layer doesn't really apply here — saveLayer already
        // gives us a fresh buffer for the children to draw into; there is no
        // double-composition happening that would multiply alpha twice.
        return modifier.drawWithContent {
            drawIntoCanvas { canvas ->
                // Paint carries the blend mode for the saveLayer call.
                val paint = Paint().apply { this.blendMode = blendMode }
                // saveLayer pushes a buffer covering this element's bounds.
                // A matching restore() is issued below to pop the layer.
                canvas.saveLayer(
                    bounds = androidx.compose.ui.geometry.Rect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height
                    ),
                    paint = paint
                )
                // Draw child content (bg, border, children) into the layer.
                drawContent()
                // Pop the layer — paint.blendMode composites the buffer onto
                // the underlying canvas (the parent's painted content).
                canvas.restore()
            }
        }
    }

    /**
     * Notes about blend mode implementation.
     */
    object Notes {
        const val LIMITATION = """
            CSS mix-blend-mode blends this element with what is painted behind
            it (its stacking-context siblings). In Compose, that means whatever
            was drawn into the parent Box before this modifier runs. The
            drawWithContent+saveLayer approach above is the closest analog.
        """

        const val PERFORMANCE = """
            saveLayer allocates a per-element bitmap buffer at draw time.
            Avoid on hot paths with many blended children — each blended
            element pays an offscreen-buffer allocation + a final blit.
        """
    }
}
