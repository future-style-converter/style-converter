package com.styleconverter.runtime.effects.filter

import androidx.compose.ui.Modifier
// `filter: blur()` records the subtree into a clip-sized GraphicsLayer (retro
// R6, A12#2) — drawWithCache owns the layer; see [BlurLayerNode.apply] for
// why Modifier.blur's node-bounds RenderNode was retired.
import androidx.compose.ui.draw.drawWithCache
// The canvas' current clip, read back as a node-local Rect for the layer size.
import androidx.compose.ui.geometry.Rect
// The same RenderEffect Modifier.blur(Unbounded) installed (BlurKt: Decal edge).
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
// Translates the recording so the node origin lands inside the layer, and the
// layer back to the node's space when drawn.
import androidx.compose.ui.graphics.drawscope.translate
// Composites a recorded GraphicsLayer into the current canvas.
import androidx.compose.ui.graphics.layer.drawLayer
// android.graphics.Canvas.getClipBounds lives on the framework canvas only.
import androidx.compose.ui.graphics.nativeCanvas
// GraphicsLayer.record takes its size in whole pixels.
import androidx.compose.ui.unit.IntSize

/**
 * The `filter: blur()` draw node — carved out of [FilterApplier] (file-size
 * rule) in retro R6. One instance per blur() function in the chain; the
 * geometry half is [BlurLayerGeometry] (JVM-pinned), the chain shape is
 * pinned by FilterBlurUnboundedLayerTest. Deliberately NOT named
 * `*Applier`: coverage-audit.mjs counts every `<Name>Applier.kt` as a
 * dedicated property applier and doc-staleness-check pins that count, and
 * this is a helper of the Filter applier, not a property of its own.
 */
internal object BlurLayerNode {

    /**
     * `filter: blur()` as an UNBOUNDED offscreen group (retro R6, audit
     * finding A12#2).
     *
     * ## Why not `Modifier.blur(…, BlurredEdgeTreatment.Unbounded)`
     * Modifier.blur is `graphicsLayer { renderEffect = BlurEffect(r, r,
     * tileMode); clip = false }` (BlurKt, ui-android 1.11.4) — a RenderNode
     * sized to the NODE'S OWN layout bounds. HWUI renders a RenderNode
     * carrying a RenderEffect into an offscreen buffer of exactly those
     * bounds, so every descendant pixel outside them is gone before the
     * blur runs, clip=false notwithstanding — the same node-bounds buffer
     * physics [FilterApplier.applyGroupColorFilters]' KDoc measured for the retired
     * RenderEffect matrix route. Measured on the committed Filter_Blur
     * baselines: the label overflowing the 80px box is present blurred on
     * iOS (368 px of ink right of x=80) and web (391 px) and ABSENT on
     * Android (0 px) — the ledgered 2.3–2.4 % Android pixel diff was the
     * missing label, not a Gaussian quantisation band. filter-effects-1
     * §6.1 defines blur() as a Gaussian over the element's rendering
     * (descendants included) and gives filter functions no border-box
     * clip, so overflowing ink must survive here exactly as it does under
     * the `opacity` property (color/OpacityApplier).
     *
     * ## The fix — record into a layer sized to the CURRENT CLIP
     * The contract OpacityApplier's `saveLayerAlpha(null, α)` already has
     * ("the layer covers the current clip"), built by hand because a
     * RenderEffect can only hang off a RenderNode, never a saveLayer paint:
     * a drawWithCache-owned GraphicsLayer (Compose 1.7+ API, RenderNode-
     * backed on API 29+) is RECORDED at the size of the canvas' current
     * clip bounds ∪ the node box, inflated by 3σ
     * ([BlurLayerGeometry.layerBounds]), with the content translated so the
     * node's origin lands where it belongs inside the layer, and drawn back
     * translated by the inverse. `DrawScope.record` swaps THIS scope's
     * canvas for the layer's for the duration of the block (DrawScope.kt —
     * its lambda re-enters the outer scope's `draw(...)` with the layer's
     * canvas), so `drawContent()` — every inner draw modifier AND the
     * children, however far they overflow the node — lands in the layer.
     * Ancestor overflow clips are already applied to the canvas, so they
     * keep cropping exactly as CSS's overflow path demands. TileMode.Decal
     * keeps Unbounded's edge model (samples beyond the layer read
     * transparent; the 3σ inflation puts that edge past anything visible).
     *
     * API < 31 has no RenderEffect (Modifier.blur was a silent identity
     * there too — degraded to a plain draw here); the JVM unit suite folds
     * this chain without drawing (FilterBlurUnboundedLayerTest), so every
     * android.graphics touch stays inside the draw lambda.
     */
    fun apply(modifier: Modifier, blur: FilterFunction.Blur): Modifier =
        modifier.drawWithCache {
            // One layer per blur() function, owned by this draw node —
            // Compose releases it with the node.
            val layer = obtainGraphicsLayer()
            onDrawWithContent {
                // filter-effects-1 §6.1: the CSS length IS σ ("the value of
                // the standard deviation to the Gaussian function"); Skia
                // wants its affine radius (see FilterApplier.blurSigmaToSkiaRadius).
                val sigmaPx = blur.radius.toPx()
                val skiaRadius = FilterApplier.blurSigmaToSkiaRadius(sigmaPx)
                // σ < 0.5px is unreachable through Skia (helper KDoc) and
                // RenderEffect needs API 31: both degrade to a plain draw —
                // the identity Modifier.blur also produced there.
                if (skiaRadius <= 0f ||
                    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S
                ) {
                    drawContent()
                    return@onDrawWithContent
                }
                // The canvas' current clip in THIS node's local coordinates
                // (android.graphics.Canvas.getClipBounds contract) — the
                // region any of our ink can land in. false ⇒ empty clip.
                val clipRect = android.graphics.Rect()
                val clip = if (drawContext.canvas.nativeCanvas.getClipBounds(clipRect)) {
                    Rect(
                        clipRect.left.toFloat(), clipRect.top.toFloat(),
                        clipRect.right.toFloat(), clipRect.bottom.toFloat(),
                    )
                } else null
                val bounds = BlurLayerGeometry.layerBounds(size.width, size.height, clip, sigmaPx)
                if (!bounds.coversClip) {
                    // Node-box fallback (no clip to read, or a clip past the
                    // texture-size guard): overflow ink beyond 3σ of the box
                    // is cropped here — disclosed, never silent.
                    android.util.Log.w(
                        "FilterApplier",
                        "filter: blur() layer fell back to the node box (clip=$clip) — overflow ink beyond 3σ is cropped",
                    )
                }
                // Same radius/edge model Modifier.blur(Unbounded) installed
                // (BlurKt: Unbounded → clip=false, TileMode.Decal).
                layer.renderEffect = BlurEffect(skiaRadius, skiaRadius, TileMode.Decal)
                layer.clip = false
                // Record with node-local (0,0) placed at (-left,-top) inside
                // the layer — the layer rect's top-left is its origin.
                // `GraphicsLayer.record` is DrawScope's member extension
                // (DrawScope.kt): its block re-enters THIS scope with the
                // layer's canvas swapped in, so the outer scope's
                // `drawContent()` — every inner draw modifier and the
                // children, however far they overflow — lands in the layer
                // (the pattern Compose's GraphicsLayer docs show:
                // `this@drawWithContent.drawContent()` inside `record`).
                layer.record(size = IntSize(bounds.width, bounds.height)) {
                    translate(-bounds.left, -bounds.top) { this@onDrawWithContent.drawContent() }
                }
                // Composite the blurred layer back at the layer rect's origin.
                translate(bounds.left, bounds.top) { drawLayer(layer) }
            }
        }
}
