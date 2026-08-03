package com.styleconverter.runtime.effects.backdrop

// The radius values come from the element's OWN border-radius config, the
// same one BordersFacade clips its box with — patch and box must agree on
// the corner shape or the filtered pixels would leak past the border.
import com.styleconverter.runtime.borders.radius.BorderRadiusConfig

/**
 * The eight corner radii of a border box, resolved to pixels, in physical
 * (top-left → bottom-left) order. `x` is the horizontal semi-axis, `y` the
 * vertical one — CSS corners are elliptical.
 */
data class BackdropCornerRadii(
    val topLeftX: Float, val topLeftY: Float,
    val topRightX: Float, val topRightY: Float,
    val bottomRightX: Float, val bottomRightY: Float,
    val bottomLeftX: Float, val bottomLeftY: Float,
) {
    /** True when every axis is zero — the painter can clip with a plain rect. */
    val isSquare: Boolean
        get() = topLeftX == 0f && topLeftY == 0f && topRightX == 0f && topRightY == 0f &&
            bottomRightX == 0f && bottomRightY == 0f && bottomLeftX == 0f && bottomLeftY == 0f
}

/**
 * Resolves a [BorderRadiusConfig] to pixel corner radii for the backdrop clip.
 *
 * filter-effects-2 §2: the filtered backdrop is clipped to the element's
 * border box — including its border-radius. `backdrop-filter-clip-rect.html`
 * is exactly that assertion (`border-radius: 10px 20px 30px 40px` on the
 * inverting navbar; the inversion must follow the rounded corners).
 *
 * ## Why the maths is duplicated here rather than reused
 * BorderRadiusApplier resolves the same values, but only inside a private
 * `Shape` it feeds to `Modifier.clip`. The backdrop patch is drawn INSIDE a
 * DrawScope (outer of that clip in the modifier chain — effects run at
 * StyleApplier step 3, the radius clip at step 5), so it needs the numbers as
 * a Path it can clip with itself. Same rules, restated as pure arithmetic:
 *
 *  - a percentage axis (carried as a 0..1 fraction) wins over the Dp and
 *    resolves against the border box WIDTH for x and HEIGHT for y
 *    (css-backgrounds-3 §4.4);
 *  - no §5.5 overlap down-scaling, deliberately: the element's own clip does
 *    not apply it either, and patch and box must round identically or the
 *    filtered pixels would show past the corner.
 */
object BackdropClipGeometry {

    /**
     * @param config the element's border-radius config.
     * @param widthPx / [heightPx] the border box size in px.
     * @param dpToPx Dp→px conversion (a Density at draw time; injected so this
     *   stays pure and JVM-testable).
     * @param isLtr layout direction. The composed WPT canvas is LTR-framed
     *   (same assumption ComposedCaptureCanvas states for its padding), but
     *   the parameter is explicit so the mapping is visible and testable
     *   rather than assumed.
     * @return null when the element has no radius at all — the caller then
     *   clips with a rectangle and allocates no Path.
     */
    fun resolve(
        config: BorderRadiusConfig,
        widthPx: Float,
        heightPx: Float,
        dpToPx: (androidx.compose.ui.unit.Dp) -> Float,
        isLtr: Boolean = true,
    ): BackdropCornerRadii? {
        // Square-corner fast path: identical short-circuit to
        // BorderRadiusApplier.applyRadius, so a square element costs nothing.
        if (!config.hasRadius) return null

        // One axis of one corner: the percentage fraction (if any) resolves
        // against the given extent, otherwise the fixed Dp converts to px.
        fun axis(dp: androidx.compose.ui.unit.Dp, fraction: Float?, extent: Float): Float =
            fraction?.times(extent) ?: dpToPx(dp)

        // Logical corners first (start/end, as the IR and Compose name them).
        val startTopX = axis(config.topStart.first, config.topStartFraction.first, widthPx)
        val startTopY = axis(config.topStart.second, config.topStartFraction.second, heightPx)
        val endTopX = axis(config.topEnd.first, config.topEndFraction.first, widthPx)
        val endTopY = axis(config.topEnd.second, config.topEndFraction.second, heightPx)
        val endBottomX = axis(config.bottomEnd.first, config.bottomEndFraction.first, widthPx)
        val endBottomY = axis(config.bottomEnd.second, config.bottomEndFraction.second, heightPx)
        val startBottomX = axis(config.bottomStart.first, config.bottomStartFraction.first, widthPx)
        val startBottomY = axis(config.bottomStart.second, config.bottomStartFraction.second, heightPx)

        // Then map logical → physical exactly like EllipticalCornerShape does:
        // in LTR start is left and end is right; in RTL they swap.
        return BackdropCornerRadii(
            topLeftX = if (isLtr) startTopX else endTopX,
            topLeftY = if (isLtr) startTopY else endTopY,
            topRightX = if (isLtr) endTopX else startTopX,
            topRightY = if (isLtr) endTopY else startTopY,
            bottomRightX = if (isLtr) endBottomX else startBottomX,
            bottomRightY = if (isLtr) endBottomY else startBottomY,
            bottomLeftX = if (isLtr) startBottomX else endBottomX,
            bottomLeftY = if (isLtr) startBottomY else endBottomY,
        )
    }
}
