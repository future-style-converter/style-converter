package com.styleconverter.runtime.borders

// Modifier chain, element / node machinery, cached draw node, geometry, paint, units,
// and the float math spelled exactly as the androidx bytecode calls it.
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawModifierNode
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSimple
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * A uniform border band painted BEHIND the element's content (wave 55, L6).
 * CSS 2.1 Appendix E §E.2 paints a box's border (step 2) BEFORE its in-flow
 * and positioned descendants (steps 4-8). androidx foundation 1.11.4
 * `BorderModifierNode` starts all three outline branches with `drawContent()`
 * (javap of the AAR), so the band overpainted overflowing children (WPT
 * backdrop-filter-clip-rect: the navbar's blue band across the red menus).
 * Raster parity is the constraint: every function below copies the 1.11.4
 * `BorderModifierNode` / `BorderKt` bytecode — same calls, same coordinates —
 * and only the position of `drawContent()` moves.
 */
fun Modifier.behindContentBorder(width: Dp, color: Color, shape: Shape): Modifier =
    // `border(width, color, shape)` wraps the colour in a SolidColor brush too.
    this then BehindContentBorderElement(width, SolidColor(color), shape)

/** The element JVM chain pins look for in place of `BorderModifierNodeElement`. */
internal data class BehindContentBorderElement(val width: Dp, val brush: Brush, val shape: Shape) :
    ModifierNodeElement<BehindContentBorderNode>() {
    // One node per element instance, holding the three band inputs.
    override fun create() = BehindContentBorderNode(width, brush, shape)
    // Recomposition hands new inputs; the node re-caches only on a change.
    override fun update(node: BehindContentBorderNode) = node.update(width, brush, shape)
}

/** Caches the band per size, like `BorderModifierNode`'s CacheDrawModifierNode. */
internal class BehindContentBorderNode(private var width: Dp, private var brush: Brush, private var shape: Shape) :
    DelegatingNode() {
    // The cached draw node: rebuilt when the size or an input changes.
    private val cacheNode = delegate(CacheDrawModifierNode { buildBand() })

    fun update(width: Dp, brush: Brush, shape: Shape) {
        // androidx's setters invalidate only on an actual change; so do we.
        if (this.width == width && this.brush == brush && this.shape == shape) return
        // Take the new inputs and drop the cached band: the next draw rebuilds it.
        this.width = width; this.brush = brush; this.shape = shape; cacheNode.invalidateDrawCache()
    }

    private fun CacheDrawScope.buildBand(): DrawResult {
        // androidx `drawContentWithoutBorder`: an invalid width/size paints content only.
        val g = bandGeometry(width.toPx(), width == Dp.Hairline, size)
            ?: return onDrawWithContent { drawContent() }
        // The same Outline androidx strokes: shape, size, direction, density.
        val band = bandPainter(g, brush, shape.createOutline(size, layoutDirection, this))
        // The ONE behavioural change: band first, then the children (§E.2).
        return onDrawWithContent { drawInOrder(band = { band() }, content = { drawContent() }) }
    }
}

/** Paint order of the node, isolated so a JVM pin can record it (P1). */
internal inline fun drawInOrder(band: () -> Unit, content: () -> Unit) {
    // The border band (CSS 2.1 E.2 step 2) …
    band()
    // … then everything inner of the node: padding, text, children.
    content()
}
/** `BorderModifierNode`'s stroke arithmetic, pure (pinned by P2). */
internal data class BandGeometry(val strokePx: Float, val topLeft: Offset, val borderSize: Size, val fillArea: Boolean)
/** hasValidBorderParams + the stroke arithmetic for one band; null = paint the content only (P2). */
internal fun bandGeometry(widthPx: Float, hairline: Boolean, size: Size): BandGeometry? {
    // hasValidBorderParams: width >= 0 and a non-empty box.
    if (!(widthPx >= 0f && size.minDimension > 0f)) return null
    // strokeWidthPx = min(Hairline ? 1 : ceil(width), ceil(minDimension / 2)).
    val stroke = min(if (hairline) 1f else ceil(widthPx), ceil(size.minDimension / 2f))
    // A centred stroke: inset by half the stroke so it lands inside the box.
    val half = stroke / 2f
    // fillArea: the two bands meet, so the band is the whole box (a Fill).
    return BandGeometry(stroke, Offset(half, half), Size(size.width - stroke, size.height - stroke),
        stroke * 2f > size.minDimension)
}

/** `BorderKt.shrink`: a corner radius reduced by [v], floored at 0 per axis. */
internal fun shrinkCorner(r: CornerRadius, v: Float) = CornerRadius(max(0f, r.x - v), max(0f, r.y - v))

/** `BorderKt.createInsetRoundedRect`: the inner edge of a [w]-wide band. */
internal fun insetRoundRect(w: Float, rr: RoundRect) = RoundRect(
    // left / top at w; right / bottom at the outer width / height minus w.
    w, w, rr.width - w, rr.height - w,
    // Each inner corner (TL, TR, BR, BL) is its outer corner shrunk by the band width.
    shrinkCorner(rr.topLeftCornerRadius, w), shrinkCorner(rr.topRightCornerRadius, w),
    shrinkCorner(rr.bottomRightCornerRadius, w), shrinkCorner(rr.bottomLeftCornerRadius, w),
)

/** Picks the androidx branch for the outline kind; returns the band's draw call. */
private fun CacheDrawScope.bandPainter(g: BandGeometry, brush: Brush, outline: Outline): DrawScope.() -> Unit =
    when (outline) {
        // `drawRectBorder`: a Fill of the (cache-time) box, or a Stroke on the inset rect.
        is Outline.Rectangle -> rectBand(g, brush, size)
        // `drawRoundRectBorder`: the simple (uniform-corner) and path branches.
        is Outline.Rounded -> roundedBand(g, brush, outline.roundRect)
        // `drawGenericBorder`: a path fill, or the cached alpha mask.
        is Outline.Generic -> genericBand(g, brush, outline)
    }
/** `drawRectBorder`'s one draw call for a rectangle outline (internal: P5a / P5b record it). */
internal fun rectBand(g: BandGeometry, brush: Brush, box: Size): DrawScope.() -> Unit {
    // `drawRectBorder`: fillArea → a Fill of (0,0)+box; else a Stroke(strokePx) on (topLeft, borderSize).
    val style = if (g.fillArea) Fill else Stroke(g.strokePx)
    // Default alpha / colour filter / blend mode, exactly as androidx draws it.
    return { drawRect(brush, if (g.fillArea) Offset.Zero else g.topLeft, if (g.fillArea) box else g.borderSize, style = style) }
}
/** `drawRoundRectBorder`'s draw call(s) for a rounded outline (internal: P5c / P5d record the simple branch). */
internal fun roundedBand(g: BandGeometry, brush: Brush, rr: RoundRect): DrawScope.() -> Unit {
    // Non-simple corners: outer RoundRect minus the inset one, drawn as a path.
    if (!rr.isSimple) {
        // `createRoundRectPath`: the outer rounded rect minus the inner one (path.op(path, inset,
        // Difference)), unless the bands meet.
        val path = Path().apply { addRoundRect(rr) }
        if (!g.fillArea) path.op(path, Path().apply { addRoundRect(insetRoundRect(g.strokePx, rr)) }, PathOperation.Difference)
        // A plain fill of that ring (no style: Fill).
        return { drawPath(path, brush) }
    }
    // Simple corners: androidx keeps the top-left radius and a half stroke.
    val cr = rr.topLeftCornerRadius
    val half = g.strokePx / 2f
    val stroke = Stroke(g.strokePx)
    return {
        when {
            // The bands meet: one filled rounded rect.
            g.fillArea -> drawRoundRect(brush, cornerRadius = cr)
            // Radius under the half stroke: fill, with the inner rect clipped out.
            cr.x < half -> clipRect(g.strokePx, g.strokePx, size.width - g.strokePx, size.height - g.strokePx, ClipOp.Difference) {
                drawRoundRect(brush, cornerRadius = cr)
            }
            // Otherwise a centred stroke on the half-inset rect, radius shrunk by half.
            else -> drawRoundRect(brush, g.topLeft, g.borderSize, shrinkCorner(cr, half), style = stroke)
        }
    }
}
/** `drawGenericBorder` for a path outline: a fill, or the cached alpha mask (its opaque tint: P5f). */
private fun CacheDrawScope.genericBand(g: BandGeometry, brush: Brush, outline: Outline.Generic): DrawScope.() -> Unit {
    // The bands meet: the outline path, filled.
    if (g.fillArea) return { drawPath(outline.path, brush) }
    // A solid colour needs only an alpha mask, tinted at draw time (opaque tint).
    val solid = brush as? SolidColor
    val config = if (solid != null) ImageBitmapConfig.Alpha8 else ImageBitmapConfig.Argb8888
    val filter = solid?.let { ColorFilter.tint(it.value.copy(alpha = 1f)) }
    // The mask path: the outline's bounds with the outline itself cut out.
    val bounds = outline.path.getBounds()
    val mask = Path().apply { addRect(bounds); op(this, outline.path, PathOperation.Difference) }
    // The offscreen bitmap is the path bounds, rounded up to whole pixels.
    val px = IntSize(ceil(bounds.right - bounds.left).toInt(), ceil(bounds.bottom - bounds.top).toInt())
    val bitmap = ImageBitmap(px.width, px.height, config)
    // `BorderCache.drawBorderCache`, drawn once per cache build.
    CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(bitmap), px.toSize()) {
        // Clear the bitmap first (androidx re-uses it; a fresh one is clear anyway).
        drawRect(Color.Black, size = size, blendMode = BlendMode.Clear)
        // Paths may sit off the origin: draw them relative to their bounds.
        translate(-bounds.left, -bounds.top) {
            // A 2× centred stroke paints the wanted inner band plus an outer one …
            drawPath(outline.path, brush, style = Stroke(g.strokePx * 2f))
            // … and the outer one is cleared through the (1 px over-scaled) mask.
            scale((size.width + 1f) / size.width, (size.height + 1f) / size.height) {
                drawPath(mask, brush, blendMode = BlendMode.Clear)
            }
        }
    }
    // Upload once, as androidx does after building the cache; at draw time the mask goes back at the bounds.
    bitmap.prepareToDraw()
    return { translate(bounds.left, bounds.top) { drawImage(bitmap, srcSize = px, colorFilter = filter) } }
}
