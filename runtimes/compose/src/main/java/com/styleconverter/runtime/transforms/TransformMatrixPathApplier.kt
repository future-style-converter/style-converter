package com.styleconverter.runtime.transforms

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.DpOffset

/**
 * The exact 4x4 canvas route (retro R1, finding A11#6).
 *
 * ## Why a canvas, not graphicsLayer
 * graphicsLayer exposes rotationX/rotationY through a CAMERA (cameraDistance)
 * that is not CSS's projection: it has no way to place a `perspective()`
 * at a given slot of the product, cannot express matrix3d, and on the
 * wave-49 captures rendered `perspective(500px) rotateY(30deg)` as a flat
 * rectangle where web and iOS draw the keystoned trapezoid. A flat
 * element's content is the z = 0 plane, and the full css-transforms-2 §6
 * matrix restricted to that plane IS a 3x3 homography ([Mat4.planeHomography])
 * — which android.graphics.Matrix carries natively (MPERSP_0..2) and Skia
 * rasterises exactly. Compose's Canvas.concat hands its 4x4 to
 * android.graphics.Matrix.setFrom, which copies exactly the x/y/w rows and
 * x/y/translate columns this file fills — so the perspective survives the
 * conversion. Same drawWithContent mechanism the skew route has always
 * used, so painted overflow behaves identically (no clip: the node paints
 * wherever the matrix sends it).
 *
 * ## What it does NOT do
 * It projects the element's OWN transform only. A perspective inherited
 * from an ancestor's `perspective` property (css-transforms-2 §8, the
 * spec's actual use of the property) is invisible to a Modifier — that
 * needs a renderer-threaded channel this folder does not own (the
 * OrthographicFlatten limit, unchanged). `transform-style: preserve-3d`
 * hierarchies (§4.1.2) flatten per element as before.
 */
object TransformMatrixPathApplier {

    /**
     * Draw the node's content under the element's §6 matrix.
     *
     * @param pivotShift the position offset the layout step applies INSIDE
     *   this node (PositionApplier.resolvedOffset) — see TransformPivot.
     */
    fun apply(modifier: Modifier, config: TransformConfig, pivotShift: DpOffset): Modifier =
        modifier.drawWithContent {
            // Pivot per axis: length origin or own-size fraction
            // (css-transforms-1 §4), conjugated by the abspos shift (A11#0).
            val pivX = TransformPivot.axisPx(config.originXDp?.value, density, config.originX, size.width, pivotShift.x.toPx())
            val pivY = TransformPivot.axisPx(config.originYDp?.value, density, config.originY, size.height, pivotShift.y.toPx())
            // The full 4x4 in device px, then its exact action on the plane.
            val h = TransformMatrixComposer.ctm(config, density, size.width, size.height, pivX, pivY).planeHomography()
            // Compose Matrix is column-major; these named slots are the nine
            // android.graphics.Matrix.setFrom reads (ScaleX/SkewX/TranslateX
            // = row x; SkewY/ScaleY/TranslateY = row y; Perspective0/1/2 =
            // row w). Everything else stays identity.
            val cm = Matrix()
            cm.values[Matrix.ScaleX] = h[0]; cm.values[Matrix.SkewX] = h[1]; cm.values[Matrix.TranslateX] = h[2]
            cm.values[Matrix.SkewY] = h[3]; cm.values[Matrix.ScaleY] = h[4]; cm.values[Matrix.TranslateY] = h[5]
            cm.values[Matrix.Perspective0] = h[6]; cm.values[Matrix.Perspective1] = h[7]; cm.values[Matrix.Perspective2] = h[8]
            val canvas = drawContext.canvas
            canvas.save()                                  // scope the CTM to this node's paint
            canvas.concat(cm)                              // Skia composes the homography into the CTM
            this@drawWithContent.drawContent()             // background, borders, children — all under it
            canvas.restore()
        }
}
