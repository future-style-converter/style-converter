package com.styleconverter.runtime.effects.shadow

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies box shadow effects to Compose modifiers.
 *
 * ## Implementation Strategy
 *
 * ### All shadows are CSS-style Gaussian glows
 * Every outset shadow goes through `drawBehind` + `BlurMaskFilter`. There
 * is deliberately NO `Modifier.shadow(elevation)` fast path: elevation is
 * a physically-modeled z-light shadow (two fixed-alpha ambient/spot
 * layers whose geometry depends on the device's simulated light source),
 * NOT the colored Gaussian glow css-backgrounds-3 §7.1 specifies — a
 * `box-shadow: 0 0 40px red` routed through elevation rendered as a faint
 * grey-ish rim instead of a wide red halo, which is exactly the class of
 * divergence the campaign diagnosed. The custom draw path handles color,
 * blur, and shape correctly for every parameter combination.
 *
 * ## Limitations
 * - **Spread radius**: Implemented by expanding/contracting the drawn rect
 * - **Inset shadows**: Basic support; complex insets may not render correctly
 * - **Multiple shadows**: All shadows are drawn; may differ from CSS stacking
 * - **Performance**: Custom drawing is less optimized than native elevation
 *
 * ## Platform Comparison
 * | Feature | CSS | Compose (native) | Compose (custom) |
 * |---------|-----|------------------|------------------|
 * | Basic shadow | Yes | Yes (elevation) | Yes |
 * | Offset X/Y | Yes | No | Yes |
 * | Blur radius | Yes | Yes (via elevation) | Yes |
 * | Spread radius | Yes | No | Yes (approximate) |
 * | Inset | Yes | No | Partial |
 * | Custom color | Yes | Limited | Yes |
 * | Multiple shadows | Yes | No | Yes |
 */
object ShadowApplier {

    /**
     * Apply box shadow to a modifier.
     *
     * @param modifier The base modifier to apply shadows to.
     * @param config The shadow configuration.
     * @param radiusConfig The element's border-radius config. css-backgrounds-3
     *   §7.1.1: the shadow's perimeter takes the SAME corner shape as the
     *   border box, so a `border-radius: 50%` element casts an ELLIPTICAL
     *   spread ring. Previously the ring was always a (near-)rectangular
     *   round-rect, so Borders_Decorated's blue 3px ring filled the whole
     *   rectangular box behind the orange ellipse on Android while web/iOS
     *   drew a ring hugging the ellipse (Android-web 0.7685).
     * @return Modified modifier with shadows applied.
     */
    fun applyShadow(
        modifier: Modifier,
        config: ShadowConfig,
        radiusConfig: com.styleconverter.runtime.borders.radius.BorderRadiusConfig =
            com.styleconverter.runtime.borders.radius.BorderRadiusConfig.NONE
    ): Modifier {
        return applyFullShadow(modifier, config, 0.dp, radiusConfig)
    }

    /**
     * Apply box shadow with full support for spread, inset, and multiple shadows.
     *
     * @param modifier The base modifier to apply shadows to.
     * @param config The shadow configuration.
     * @param cornerRadius Legacy single-value corner radius (used when no
     *   [radiusConfig] is supplied by the caller).
     * @param radiusConfig Per-corner border-radius (Dp + paint-time
     *   percentage fractions) driving the shadow perimeter shape.
     * @return Modified modifier with shadows applied.
     */
    fun applyFullShadow(
        modifier: Modifier,
        config: ShadowConfig,
        cornerRadius: Dp = 0.dp,
        radiusConfig: com.styleconverter.runtime.borders.radius.BorderRadiusConfig =
            com.styleconverter.runtime.borders.radius.BorderRadiusConfig.NONE
    ): Modifier {
        if (!config.hasShadow) return modifier

        // Separate inset and outset shadows
        val outsetShadows = config.shadows.filter { !it.inset }
        val insetShadows = config.shadows.filter { it.inset }

        var resultModifier = modifier

        // Apply outset shadows first (drawn behind content)
        if (outsetShadows.isNotEmpty()) {
            resultModifier = applyOutsetShadows(resultModifier, outsetShadows, cornerRadius, radiusConfig)
        }

        // Apply inset shadows (drawn over content, clipped to bounds)
        if (insetShadows.isNotEmpty()) {
            resultModifier = applyInsetShadows(resultModifier, insetShadows, cornerRadius)
        }

        return resultModifier
    }

    /**
     * Apply shadow with rounded corners.
     *
     * @param modifier The base modifier.
     * @param config The shadow configuration.
     * @param cornerRadius The corner radius for rounded rect shadows.
     * @return Modified modifier with rounded shadows applied.
     */
    fun applyShadowWithRadius(
        modifier: Modifier,
        config: ShadowConfig,
        cornerRadius: Dp = 0.dp
    ): Modifier {
        if (!config.hasShadow) return modifier

        // Use full shadow implementation for complete support
        return applyFullShadow(modifier, config, cornerRadius)
    }

    /**
     * Apply outset (normal) shadows - drawn behind the content.
     */
    private fun applyOutsetShadows(
        modifier: Modifier,
        shadows: List<ShadowData>,
        cornerRadius: Dp,
        radiusConfig: com.styleconverter.runtime.borders.radius.BorderRadiusConfig =
            com.styleconverter.runtime.borders.radius.BorderRadiusConfig.NONE
    ): Modifier {
        // NOTE: no elevation fast path. The old code routed a single
        // 0-offset/0-spread shadow through Modifier.shadow(elevation) —
        // but elevation is Android's physically-modeled z-shadow with
        // FIXED ambient/spot alphas and light-source-dependent geometry,
        // not the colored Gaussian css-backgrounds-3 §7.1 asks for, so
        // `box-shadow: 0 0 40px <color>` lost both its color intensity
        // and its 40px reach. Every shadow now takes the BlurMaskFilter
        // path below, which honors color/blur/shape exactly.
        return modifier.drawBehind {
            // CSS spec: "Shadows are rendered in back-to-front order: the
            // FIRST shadow in the list is on top of the stack." We iterate
            // the list in REVERSE so the last-listed shadow is drawn first
            // (bottom of the stack) and the first-listed shadow lands on
            // top — matching how Chrome/Firefox/WebKit composite. The
            // previous forward iteration inverted the order, which also
            // explains the Phase 12 finding that "only the last 2-3 layers
            // are visible" for many-layer shadows: the small tight shadow
            // listed first was the one the author wanted visible, but
            // under forward iteration it got painted over by the larger
            // later-listed layers.
            for (shadowData in shadows.asReversed()) {
                drawIntoCanvas { canvas ->
                    val nativePaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = shadowData.color.toArgb()

                        // Convert the CSS blur radius to Skia's mask-filter
                        // radius (see blurMaskRadius for the derivation) —
                        // passing the raw CSS value made Android's blur
                        // ~2.3× wider/softer than web's for the same
                        // declaration. Skip the filter entirely when the
                        // converted radius rounds to 0: BlurMaskFilter
                        // rejects non-positive radii, and a sub-pixel σ is
                        // visually indistinguishable from a crisp edge.
                        val maskRadius = blurMaskRadius(shadowData.blurRadius.toPx())
                        if (maskRadius > 0f) {
                            maskFilter = android.graphics.BlurMaskFilter(
                                maskRadius,
                                android.graphics.BlurMaskFilter.Blur.NORMAL
                            )
                        }
                    }

                    val offsetX = shadowData.offsetX.toPx()
                    val offsetY = shadowData.offsetY.toPx()
                    val spread = shadowData.spreadRadius.toPx()
                    val spreadGrow = spread.coerceAtLeast(0f)

                    // css-backgrounds-3 §7.1.1: the shadow perimeter is the
                    // border box shape (per-corner radii, percentages
                    // resolved against the laid-out size) expanded by the
                    // spread — each corner radius grows by the spread too,
                    // which is what turns `border-radius: 50%` + a spread
                    // ring into a concentric ELLIPSE ring instead of a
                    // rectangle (Borders_Decorated web/iOS vs old Android).
                    fun corner(
                        dp: Pair<Dp, Dp>,
                        frac: Pair<Float?, Float?>
                    ): CornerRadius {
                        val rx = (frac.first?.times(size.width) ?: dp.first.toPx()) + spreadGrow
                        val ry = (frac.second?.times(size.height) ?: dp.second.toPx()) + spreadGrow
                        return CornerRadius(rx, ry)
                    }
                    // Legacy single-Dp radius keeps working when no
                    // per-corner config was supplied.
                    val legacy = CornerRadius(cornerRadius.toPx() + spreadGrow, cornerRadius.toPx() + spreadGrow)
                    val useConfig = radiusConfig.hasRadius
                    val shadowShape = RoundRect(
                        rect = Rect(
                            left = offsetX - spread,
                            top = offsetY - spread,
                            right = size.width + offsetX + spread,
                            bottom = size.height + offsetY + spread
                        ),
                        topLeft = if (useConfig) corner(radiusConfig.topStart, radiusConfig.topStartFraction) else legacy,
                        topRight = if (useConfig) corner(radiusConfig.topEnd, radiusConfig.topEndFraction) else legacy,
                        bottomRight = if (useConfig) corner(radiusConfig.bottomEnd, radiusConfig.bottomEndFraction) else legacy,
                        bottomLeft = if (useConfig) corner(radiusConfig.bottomStart, radiusConfig.bottomStartFraction) else legacy
                    )
                    // Path-based draw so per-corner (and elliptical) radii
                    // render exactly; Skia clamps overlapping radii for us.
                    val path = Path().apply { addRoundRect(shadowShape) }
                    canvas.nativeCanvas.drawPath(path.asAndroidPath(), nativePaint)
                }
            }
        }
    }

    /**
     * Apply inset shadows - drawn inside the element boundary.
     *
     * Inset shadows are implemented by:
     * 1. Clipping to the element boundary
     * 2. Drawing the shadow from outside inward (inverted)
     */
    private fun applyInsetShadows(
        modifier: Modifier,
        shadows: List<ShadowData>,
        cornerRadius: Dp
    ): Modifier {
        return modifier.drawWithContent {
            // Draw the content first
            drawContent()

            // Create clip path for the element bounds
            val clipPath = Path().apply {
                if (cornerRadius > 0.dp) {
                    addRoundRect(
                        RoundRect(
                            left = 0f,
                            top = 0f,
                            right = size.width,
                            bottom = size.height,
                            cornerRadius = CornerRadius(cornerRadius.toPx())
                        )
                    )
                } else {
                    addRect(Rect(0f, 0f, size.width, size.height))
                }
            }

            // Draw inset shadows clipped to bounds
            clipPath(clipPath, ClipOp.Intersect) {
                for (shadowData in shadows) {
                    drawIntoCanvas { canvas ->
                        // For inset shadows, we draw a large rect with a hole
                        // and apply blur to create the inner shadow effect
                        val offsetX = shadowData.offsetX.toPx()
                        val offsetY = shadowData.offsetY.toPx()
                        val spread = shadowData.spreadRadius.toPx()
                        val blur = shadowData.blurRadius.toPx()
                        val radius = cornerRadius.toPx()

                        val nativePaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            color = shadowData.color.toArgb()

                            // Same CSS→Skia radius conversion as the outset
                            // path (derivation in blurMaskRadius) — the raw
                            // CSS radius over-blurred inset shadows by the
                            // same ~2.3× factor. Guard: BlurMaskFilter
                            // throws on radius ≤ 0.
                            val maskRadius = blurMaskRadius(blur)
                            if (maskRadius > 0f) {
                                maskFilter = android.graphics.BlurMaskFilter(
                                    maskRadius,
                                    android.graphics.BlurMaskFilter.Blur.NORMAL
                                )
                            }
                        }

                        // Create the inset shadow by drawing the negative space
                        // The shadow is drawn at the edges by using a path with a hole
                        val outerPadding = blur + spread.coerceAtLeast(0f) + 50f
                        val path = android.graphics.Path().apply {
                            // Outer rect (large, outside visible area)
                            addRect(
                                -outerPadding,
                                -outerPadding,
                                size.width + outerPadding,
                                size.height + outerPadding,
                                android.graphics.Path.Direction.CW
                            )

                            // Inner rect (hole where no shadow appears)
                            // Offset and contracted by spread
                            val innerLeft = offsetX + spread
                            val innerTop = offsetY + spread
                            val innerRight = size.width + offsetX - spread
                            val innerBottom = size.height + offsetY - spread

                            if (radius > 0f) {
                                addRoundRect(
                                    innerLeft, innerTop, innerRight, innerBottom,
                                    (radius - spread).coerceAtLeast(0f),
                                    (radius - spread).coerceAtLeast(0f),
                                    android.graphics.Path.Direction.CCW
                                )
                            } else {
                                addRect(
                                    innerLeft, innerTop, innerRight, innerBottom,
                                    android.graphics.Path.Direction.CCW
                                )
                            }
                        }

                        canvas.nativeCanvas.drawPath(path, nativePaint)
                    }
                }
            }
        }
    }

    /**
     * Convert a CSS box-shadow blur radius (px) into the radius argument
     * Skia's [android.graphics.BlurMaskFilter] expects.
     *
     * Derivation:
     * - css-backgrounds-3 §7.1: a blur radius `r` means a Gaussian blur
     *   whose standard deviation is `r / 2` — this is what Chromium (and
     *   the web runtime under it) implements, so σ = r/2 is our parity
     *   target.
     * - Skia's BlurMaskFilter maps its radius argument to a sigma via
     *   `sigma ≈ 0.57735 · radius + 0.5` (skia/src/core/SkBlurMask.cpp,
     *   `SkBlurMask::ConvertRadiusToSigma`).
     * - Equate the two and solve for radius:
     *   `radiusForSkia = max(0, (r/2 − 0.5) / 0.57735)`.
     *
     * Passing the raw CSS radius straight through (the old behavior) made
     * the effective sigma ≈ 0.577·r + 0.5 instead of r/2 — roughly 2.3×
     * too soft/wide, a visible cross-platform divergence at 40px blurs.
     * Result 0 (r ≤ 1px) means "skip the filter": BlurMaskFilter throws
     * on non-positive radii and a sub-pixel sigma reads as a crisp edge.
     */
    internal fun blurMaskRadius(cssBlurPx: Float): Float =
        (((cssBlurPx / 2f) - 0.5f) / 0.57735f).coerceAtLeast(0f)

}
