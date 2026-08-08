package com.styleconverter.runtime.effects.clip

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for CSS clip-path property.
 *
 * Clip-path creates a clipping region that determines which parts
 * of an element are visible. Parts outside the clipping region are hidden.
 *
 * ## Supported Shapes
 * - [ClipShape.Circle] - Circular clipping region
 * - [ClipShape.Ellipse] - Elliptical clipping region
 * - [ClipShape.Inset] - Rectangular inset with optional border-radius
 * - [ClipShape.Polygon] - Arbitrary polygon defined by points
 * - [ClipShape.Path] - SVG path data (limited support)
 *
 * ## Example
 * ```kotlin
 * val config = ClipPathConfig(
 *     shape = ClipShape.Circle(
 *         radius = ClipRadius.Percentage(50f),
 *         centerX = 50f,
 *         centerY = 50f
 *     )
 * )
 * ```
 */
data class ClipPathConfig(
    val shape: ClipShape? = null
) {
    /** True if there is a clip path to apply. */
    val hasClipPath: Boolean get() = shape != null
}

/**
 * Sealed interface representing CSS clip-path shape functions.
 *
 * Each shape maps to a CSS basic-shape function:
 * - `circle()` -> [Circle]
 * - `ellipse()` -> [Ellipse]
 * - `inset()` -> [Inset]
 * - `polygon()` -> [Polygon]
 * - `path()` -> [Path]
 */
sealed interface ClipShape {

    /**
     * Circular clipping region.
     *
     * CSS: `clip-path: circle(radius at centerX centerY)`
     *
     * @param radius The radius of the circle (fixed, percentage, or keyword)
     * @param centerX Horizontal center position as percentage (0-100)
     * @param centerY Vertical center position as percentage (0-100)
     * @param centerXDp Horizontal center as an absolute length. CSS Shapes 1
     *   §3.1 allows `circle(r at 20px 30px)`; the IR then carries
     *   `pos.x = {px: 20}` instead of a percent. Non-null wins over
     *   [centerX] at draw time. (Previously the extractor only understood
     *   the percent form, so px-positioned circles silently recentred to
     *   50%/50% — clip-path-basic-shapes 006_ClipPath_Circle_AtCorner sat
     *   at SSIM 0.86 vs web.)
     * @param centerYDp Vertical counterpart of [centerXDp].
     * @param centerFromRight True when the `at` clause anchored the
     *   horizontal offset to the RIGHT edge (`circle(50% at right 40px …)`).
     *   The css-values-4 `<position>` grammar's `[left|right]
     *   <length-percentage>` arm cannot be normalised to a left-origin
     *   length by the converter, which never sees the box, so the IR ships
     *   `pos.xEdge = "right"` and the applier subtracts at draw time.
     *   (Wave 37 lane W3 — before it, the converter dropped every keyword
     *   `at` clause outright, so this case never reached the runtimes.)
     * @param centerFromBottom Vertical counterpart of [centerFromRight].
     */
    data class Circle(
        val radius: ClipRadius = ClipRadius.ClosestSide,
        val centerX: Float = 50f,
        val centerY: Float = 50f,
        val centerXDp: Dp? = null,
        val centerYDp: Dp? = null,
        val centerFromRight: Boolean = false,
        val centerFromBottom: Boolean = false,
    ) : ClipShape

    /**
     * Elliptical clipping region.
     *
     * CSS: `clip-path: ellipse(radiusX radiusY at centerX centerY)`
     *
     * @param radiusX Horizontal radius
     * @param radiusY Vertical radius
     * @param centerX Horizontal center position as percentage (0-100)
     * @param centerY Vertical center position as percentage (0-100)
     * @param centerXDp Horizontal center as an absolute length, mirroring
     *   [Circle.centerXDp] — `ellipse(40px 60px at 20px 30px)` puts
     *   `pos.x = {px: 20}` on the wire and a percent read would silently
     *   recentre it.
     * @param centerYDp Vertical counterpart of [centerXDp].
     * @param centerFromRight See [Circle.centerFromRight].
     * @param centerFromBottom See [Circle.centerFromBottom].
     */
    data class Ellipse(
        val radiusX: ClipRadius = ClipRadius.ClosestSide,
        val radiusY: ClipRadius = ClipRadius.ClosestSide,
        val centerX: Float = 50f,
        val centerY: Float = 50f,
        val centerXDp: Dp? = null,
        val centerYDp: Dp? = null,
        val centerFromRight: Boolean = false,
        val centerFromBottom: Boolean = false,
    ) : ClipShape

    /**
     * Rectangular inset clipping region with optional border-radius.
     *
     * CSS: `clip-path: inset(top right bottom left round radius)`
     *
     * @param top Inset from top edge
     * @param right Inset from right edge
     * @param bottom Inset from bottom edge
     * @param left Inset from left edge
     * @param borderRadius Corner radius for rounded rectangle
     */
    data class Inset(
        val top: Dp = 0.dp,
        val right: Dp = 0.dp,
        val bottom: Dp = 0.dp,
        val left: Dp = 0.dp,
        val borderRadius: Dp = 0.dp,
        // CSS allows percentage radii on `inset(... round ...)`. Per CSS
        // Backgrounds 3 §5.2 a percentage radius resolves to that
        // percentage of the box's *width* (rx) and *height* (ry)
        // respectively, producing an ellipse on non-square boxes. We can't
        // resolve at extract time (no size available), so we surface a
        // 0..1 fraction the applier multiplies inside createOutline where
        // `size` is in scope.
        val borderRadiusFraction: Float? = null,
    ) : ClipShape

    /**
     * CSS Shapes 1 §2.1 `xywh(x y w h [round r])` — a rectangle anchored
     * at (x, y) from the box's top-left with explicit width/height and an
     * optional uniform corner radius. IR wire shape (ClipPathSerializers):
     *   { type: "xywh", x: IRLength, y: IRLength, w: IRLength, h: IRLength,
     *     round?: IRLength }
     * Before this variant existed the extractor returned null for xywh and
     * the element rendered UNCLIPPED on Android (clip-path-basic-shapes
     * 012/013: 16% mismatched pixels vs web, SSIM 0.79-0.81).
     */
    data class Xywh(
        val x: Dp = 0.dp,
        val y: Dp = 0.dp,
        val w: Dp = 0.dp,
        val h: Dp = 0.dp,
        val round: Dp = 0.dp,
    ) : ClipShape

    /**
     * Legacy CSS 2.1 `clip: rect(t, r, b, l)` — values are absolute
     * coordinates from the box's top-left, NOT inset distances. `null`
     * on any axis means CSS `auto` and resolves at draw time to 0
     * (top/left) or full extent (bottom/right).
     */
    data class LegacyRect(
        val top: Dp? = null,
        val right: Dp? = null,
        val bottom: Dp? = null,
        val left: Dp? = null,
    ) : ClipShape

    /**
     * Polygon clipping region defined by a series of points.
     *
     * CSS: `clip-path: polygon(x1 y1, x2 y2, ...)`
     *
     * Each [PolygonPoint] carries an x and y axis value that can be
     * either a percentage of the box (legacy form) or a fixed Dp length
     * (CSS Shapes 2 §3.1.4 — `<length-percentage>`). The applier picks
     * the right resolution path per axis at draw time.
     */
    data class Polygon(
        val points: List<PolygonPoint>
    ) : ClipShape

    /**
     * Per-axis polygon vertex value. One of:
     *  - [PolygonAxis.Percent] — value × box dimension at draw time
     *  - [PolygonAxis.Length] — absolute Dp, resolved to px via density
     */
    data class PolygonPoint(val x: PolygonAxis, val y: PolygonAxis)

    sealed interface PolygonAxis {
        /** 0..100 percentage of the box width (x) or height (y). */
        data class Percent(val value: Float) : PolygonAxis
        /** Absolute density-independent pixels. */
        data class Length(val dp: Dp) : PolygonAxis
    }

    /**
     * SVG path clipping region.
     *
     * CSS: `clip-path: path('M...')`
     *
     * Note: Full SVG path parsing is complex. This is a simplified
     * implementation that may not support all path commands.
     *
     * @param svgPath SVG path data string
     */
    data class Path(
        val svgPath: String
    ) : ClipShape
}

/**
 * Represents a radius value for circle/ellipse clip shapes.
 *
 * CSS clip-path radius can be:
 * - Fixed length (e.g., `50px`)
 * - Percentage of reference box (e.g., `50%`)
 * - Keyword (`closest-side` or `farthest-side`)
 */
sealed interface ClipRadius {

    /**
     * Fixed length radius.
     * @param dp The radius in density-independent pixels
     */
    data class Fixed(val dp: Dp) : ClipRadius

    /**
     * Percentage radius relative to the reference box.
     * @param percent Percentage value (0-100)
     */
    data class Percentage(val percent: Float) : ClipRadius

    /**
     * Radius extends to the closest side of the reference box.
     * For circle: minimum of distances to all sides from center.
     * For ellipse: minimum horizontal/vertical distance separately.
     */
    data object ClosestSide : ClipRadius

    /**
     * Radius extends to the farthest side of the reference box.
     * For circle: maximum of distances to all sides from center.
     * For ellipse: maximum horizontal/vertical distance separately.
     */
    data object FarthestSide : ClipRadius
}
