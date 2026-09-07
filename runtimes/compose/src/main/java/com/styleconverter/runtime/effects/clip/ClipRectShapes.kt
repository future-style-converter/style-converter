package com.styleconverter.runtime.effects.clip

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * The rectangular / vertex / path `<basic-shape>` factories — the bare
 * `<geometry-box>` rounded rect, `xywh()`, `inset()`, legacy `clip:
 * rect()`, `polygon()` and `path()` — split out of [ClipPathApplier]
 * (wave 46, lane Y4) to keep each file under the house size target.
 * All of them resolve against the css-masking-1 §5.1 reference box
 * through [ClipPathApplier.ClipRefBox.frame] at draw time.
 */
internal object ClipRectShapes {
    /**
     * css-masking-1 §5.1 bare `<geometry-box>`: the clip region is the
     * reference box itself WITH its corner curves (border-box follows
     * border-radius; padding/content boxes the §5.1 inner radii; the
     * margin box the css-shapes-1 §4 outset rule — all resolved by
     * ClipReferenceBox). `Modifier.clip` with a non-rect Shape clips to
     * the outline only (no extra clip-to-bounds), so an outline / box
     * shadow drawn past the node is cut exactly at the reference box —
     * the assertion of WPT clip-path-marginBox-1b/1c/1d + contentBox-1d/1e.
     */
    fun createReferenceBoxShape(ref: ClipPathApplier.ClipRefBox): Shape = object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
            val f = ref.frame(size, density)
            val path = Path().apply {
                addRoundRect(RoundRect(f.rect, f.topLeft, f.topRight, f.bottomRight, f.bottomLeft))
            }
            return Outline.Generic(path)
        }
    }

    /**
     * Create a Compose Shape for `xywh(x y w h [round r])` (CSS Shapes 1
     * §2.1). The rect is anchored at (x, y) from the box's top-left with
     * a fixed w×h extent; `round` applies a uniform corner radius (the
     * single-radius form is all the IR serializes today). Rendered as an
     * [Outline.Rounded] so Skia handles the corner arcs natively — the
     * same primitive the browser uses for its border-radius fast path.
     */
    fun createXywhShape(xywh: ClipShape.Xywh, ref: ClipPathApplier.ClipRefBox): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                // Anchored at the reference box's top-left (css-shapes-1 §3.1).
                val box = ref.frame(size, density).rect
                val x = box.left + with(density) { xywh.x.toPx() }
                val y = box.top + with(density) { xywh.y.toPx() }
                val w = with(density) { xywh.w.toPx() }.coerceAtLeast(0f)
                val h = with(density) { xywh.h.toPx() }.coerceAtLeast(0f)
                val r = with(density) { xywh.round.toPx() }
                    // CSS Backgrounds 3 §5.5 corner-overlap rule: radii
                    // never exceed half the rect's shorter dimension.
                    .coerceIn(0f, min(w, h) / 2f)
                return Outline.Rounded(
                    RoundRect(
                        left = x, top = y, right = x + w, bottom = y + h,
                        cornerRadius = CornerRadius(r, r)
                    )
                )
            }
        }
    }

    /**
     * Create a Compose Shape for inset clip-path.
     */
    fun createInsetShape(inset: ClipShape.Inset, ref: ClipPathApplier.ClipRefBox): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                // Insets measure from the REFERENCE BOX's edges; a percent
                // side is that fraction of its height (top/bottom) or width
                // (left/right) — css-shapes-1 §3.1, pinned by WPT
                // clip-path-inset-round-percent (`inset(80% 0 0 round 8%)`).
                val box = ref.frame(size, density).rect
                val top = with(density) { inset.top.toPx() } + (inset.topFraction?.let { box.height * it } ?: 0f)
                val right = with(density) { inset.right.toPx() } + (inset.rightFraction?.let { box.width * it } ?: 0f)
                val bottom = with(density) { inset.bottom.toPx() } + (inset.bottomFraction?.let { box.height * it } ?: 0f)
                val left = with(density) { inset.left.toPx() } + (inset.leftFraction?.let { box.width * it } ?: 0f)
                val absRadius = with(density) { inset.borderRadius.toPx() }
                // CSS percentage radius resolves to width × fraction on
                // the X axis and height × fraction on the Y axis (per
                // CSS Backgrounds 3 §5.2). For non-square boxes that
                // produces an ellipse on each corner, which is what
                // `clip-path: inset(0 round 50%)` looks like in browsers.
                val rx = absRadius + (inset.borderRadiusFraction?.let { box.width * it } ?: 0f)
                val ry = absRadius + (inset.borderRadiusFraction?.let { box.height * it } ?: 0f)

                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(
                                left = box.left + left,
                                top = box.top + top,
                                right = box.right - right,
                                bottom = box.bottom - bottom
                            ),
                            radiusX = rx,
                            radiusY = ry
                        )
                    )
                }

                return Outline.Generic(path)
            }
        }
    }

    /**
     * Legacy CSS 2.1 `clip: rect(t,r,b,l)` — values are absolute
     * coordinates from the box top-left, NOT inset distances. `null`
     * on any axis means CSS `auto`: 0 for top/left, full extent for
     * bottom/right (CSS 2.1 §11.1.2).
     */
    fun createLegacyRectShape(rect: ClipShape.LegacyRect, ref: ClipPathApplier.ClipRefBox): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                // CSS 2.1 §11.1.2: offsets from the BORDER box's top-left —
                // which, for the absolutely positioned box `clip` applies
                // to, sits at the position offset inside this node.
                val box = ref.frame(size, density).rect
                val t = rect.top?.let { with(density) { it.toPx() } } ?: 0f
                val l = rect.left?.let { with(density) { it.toPx() } } ?: 0f
                val r = rect.right?.let { with(density) { it.toPx() } } ?: box.width
                val b = rect.bottom?.let { with(density) { it.toPx() } } ?: box.height
                return Outline.Rectangle(Rect(left = box.left + l, top = box.top + t,
                                              right = box.left + r.coerceAtLeast(l),
                                              bottom = box.top + b.coerceAtLeast(t)))
            }
        }
    }

    /**
     * Create a Compose Shape for polygon clip-path.
     *
     * Per axis the vertex value is either a [ClipShape.PolygonAxis.Percent]
     * (legacy form, % of box dimension) or a [ClipShape.PolygonAxis.Length]
     * (CSS Shapes 2 `<length-percentage>` length form, absolute Dp).
     * `resolveAxis` picks the right path for each axis independently — a
     * vertex like `(100px, 50%)` is fully supported.
     */
    fun createPolygonShape(polygon: ClipShape.Polygon, ref: ClipPathApplier.ClipRefBox): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                // Vertices are offsets from the reference box's top-left;
                // percents scale by its extents (css-shapes-1 §3.1 — WPT
                // clip-path-geometryBox-2: `polygon(…) margin-box`).
                val box = ref.frame(size, density).rect
                // Resolve a PolygonAxis to a concrete pixel coordinate
                // within the reference box. `extent` is the relevant
                // dimension (width for x, height for y).
                fun resolveAxis(axis: ClipShape.PolygonAxis, extent: Float): Float =
                    when (axis) {
                        is ClipShape.PolygonAxis.Percent -> extent * (axis.value / 100f)
                        is ClipShape.PolygonAxis.Length -> with(density) { axis.dp.toPx() }
                    }

                val path = Path().apply {
                    if (polygon.points.isNotEmpty()) {
                        val first = polygon.points.first()
                        moveTo(
                            box.left + resolveAxis(first.x, box.width),
                            box.top + resolveAxis(first.y, box.height)
                        )
                        polygon.points.drop(1).forEach { pt ->
                            lineTo(
                                box.left + resolveAxis(pt.x, box.width),
                                box.top + resolveAxis(pt.y, box.height)
                            )
                        }
                        close()
                    }
                }

                return Outline.Generic(path)
            }
        }
    }

    /**
     * Create a Compose Shape for SVG path clip-path.
     *
     * Parses SVG path data string (d attribute) into a Compose Path.
     * Supports all standard SVG path commands: M, L, H, V, C, S, Q, T, A, Z.
     */
    fun createPathShape(pathShape: ClipShape.Path, ref: ClipPathApplier.ClipRefBox): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                // Parse the SVG path data
                val parsedPath = SvgPathParser.parse(pathShape.svgPath)

                if (parsedPath != null) {
                    // css-shapes-1 §3.1 path(): user units are CSS px from
                    // the reference box's top-left. Translate only when the
                    // box is displaced so the frozen no-margin renders keep
                    // the untouched Path object.
                    val box = ref.frame(size, density).rect
                    if (box.left != 0f || box.top != 0f) {
                        parsedPath.translate(androidx.compose.ui.geometry.Offset(box.left, box.top))
                    }
                    return Outline.Generic(parsedPath)
                }

                // Fallback: return full rectangle (no clipping)
                val fallbackPath = Path().apply {
                    addRect(Rect(0f, 0f, size.width, size.height))
                }
                return Outline.Generic(fallbackPath)
            }
        }
    }

}
