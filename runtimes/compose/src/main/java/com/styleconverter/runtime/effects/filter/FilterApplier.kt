package com.styleconverter.runtime.effects.filter

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

/**
 * Applies filter effects to Compose modifiers.
 *
 * ## Implementation Notes
 *
 * ### Direct Modifier Support
 * - `blur()` - Uses Modifier.blur() directly
 * - `opacity()` - Uses Modifier.alpha() directly
 *
 * ### ColorMatrix-based Filters
 * ColorMatrix filters (brightness, contrast, grayscale, etc.) are applied via
 * graphicsLayer with RenderEffect on Android 12+ for true element-wide filtering.
 * On older versions, uses drawWithContent with ColorFilter as a fallback.
 *
 * ### Platform Support
 * - Android 12+ (API 31+): Full RenderEffect support for element-wide filtering
 * - Android 10-11: Fallback using drawWithContent + ColorFilter
 * - Below Android 10: Limited support
 */
object FilterApplier {

    /**
     * Apply all filters to a modifier.
     *
     * @param modifier The base modifier
     * @param config The filter configuration
     * @return Modified modifier with filters applied
     */
    fun applyFilters(modifier: Modifier, config: FilterConfig): Modifier {
        var result = modifier

        // Separate filters by type for optimal application
        val blurFilters = config.filters.filterIsInstance<FilterFunction.Blur>()
        val opacityFilters = config.filters.filterIsInstance<FilterFunction.Opacity>()
        val dropShadowFilters = config.filters.filterIsInstance<FilterFunction.DropShadow>()
        val colorMatrixFilters = config.filters.filter { it.isColorMatrixFilter() }

        // Apply blur first
        blurFilters.forEach { blur ->
            result = result.blur(blur.radius)
        }

        // Apply drop shadows
        dropShadowFilters.forEach { shadow ->
            result = applyDropShadow(result, shadow)
        }

        // Apply color matrix filters (combined for efficiency)
        if (colorMatrixFilters.isNotEmpty()) {
            result = applyColorMatrixFilters(result, colorMatrixFilters)
        }

        // Apply opacity last
        opacityFilters.forEach { opacity ->
            result = result.alpha(opacity.amount.coerceIn(0f, 1f))
        }

        // Handle backdrop filters
        if (config.hasBackdropFilters) {
            result = applyBackdropFilters(result, config.backdropFilters)
        }

        return result
    }

    /**
     * Check if a filter function is ColorMatrix-based.
     */
    private fun FilterFunction.isColorMatrixFilter(): Boolean {
        return when (this) {
            is FilterFunction.Brightness,
            is FilterFunction.Contrast,
            is FilterFunction.Grayscale,
            is FilterFunction.HueRotate,
            is FilterFunction.Saturate,
            is FilterFunction.Sepia,
            is FilterFunction.Invert -> true
            else -> false
        }
    }

    /**
     * Apply color matrix filters using the best available method.
     */
    private fun applyColorMatrixFilters(
        modifier: Modifier,
        filters: List<FilterFunction>
    ): Modifier {
        val colorMatrix = buildCombinedColorMatrix(filters) ?: return modifier

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: Use RenderEffect for true element-wide filtering
            applyColorMatrixWithRenderEffect(modifier, colorMatrix)
        } else {
            // Fallback: Use drawWithContent
            applyColorMatrixWithDraw(modifier, colorMatrix)
        }
    }

    /**
     * Apply color matrix using RenderEffect (Android 12+).
     * This provides true element-wide filtering including children.
     */
    private fun applyColorMatrixWithRenderEffect(
        modifier: Modifier,
        colorMatrix: ColorMatrix
    ): Modifier {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modifier.graphicsLayer {
                val androidMatrix = android.graphics.ColorMatrix(colorMatrix.values)
                renderEffect = RenderEffect.createColorFilterEffect(
                    android.graphics.ColorMatrixColorFilter(androidMatrix)
                ).asComposeRenderEffect()
            }
        } else {
            modifier
        }
    }

    /**
     * Apply color matrix using drawWithContent (fallback for older Android).
     * This draws content with the color filter applied.
     */
    private fun applyColorMatrixWithDraw(
        modifier: Modifier,
        colorMatrix: ColorMatrix
    ): Modifier {
        val colorFilter = ColorFilter.colorMatrix(colorMatrix)

        return modifier.drawWithContent {
            // Draw content with color filter applied
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    this.colorFilter = colorFilter
                }

                // Save the layer with the color filter
                canvas.saveLayer(
                    bounds = androidx.compose.ui.geometry.Rect(
                        0f, 0f, size.width, size.height
                    ),
                    paint = paint
                )

                // Draw the content
                drawContent()

                // Restore the layer
                canvas.restore()
            }
        }
    }

    /**
     * Apply backdrop filters.
     *
     * CSS `backdrop-filter` is supposed to filter what is BEHIND the
     * element (the parent's already-painted pixels), not the element
     * itself. Compose's `Modifier.blur()` and `RenderEffect` applied via
     * `graphicsLayer` blur the element's own rendering — including its
     * children. Wiring `backdrop-filter: blur(10px)` to `Modifier.blur()`
     * therefore obliterates foreground content: `Glass_Effect`
     * (rgba(255,255,255,0.2) box with placeholder text) was rendering
     * with the "Glass Effect" label literally blurred into invisibility,
     * dragging iOS-Android SSIM and producing a clearly broken capture.
     *
     * There is no clean Compose API to blur "what's drawn behind me"
     * without restructuring the render tree (you'd need to capture the
     * parent layer, blur the snapshot, and composite). Until that
     * restructuring lands, the right behaviour is to NOT apply the blur
     * to the foreground at all. The translucent background still tints
     * the underlying canvas (matching iOS's `.thinMaterial` visual at
     * least in mid-grey-ness), and the placeholder text is now visible.
     * That's a perceptual win even though the soft-edge cosmetic of a
     * real frosted blur is lost.
     *
     * Color-matrix backdrop filters (grayscale/contrast/etc) have the
     * same correctness problem and are dropped on the same grounds.
     */
    private fun applyBackdropFilters(
        modifier: Modifier,
        filters: List<FilterFunction>
    ): Modifier {
        // Intentional no-op: see kdoc above. Applying these filters via
        // graphicsLayer/Modifier.blur destroys foreground content, which
        // is worse than the loss of the cosmetic blur effect.
        return modifier
    }

    /**
     * Apply drop shadow effect using drawBehind.
     */
    private fun applyDropShadow(modifier: Modifier, shadow: FilterFunction.DropShadow): Modifier {
        return modifier.drawBehind {
            val offsetX = shadow.offsetX.toPx()
            val offsetY = shadow.offsetY.toPx()
            val blur = shadow.blurRadius.toPx()

            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    color = shadow.color
                    asFrameworkPaint().apply {
                        setShadowLayer(blur, offsetX, offsetY, shadow.color.toArgb())
                    }
                }

                canvas.drawRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    paint = paint
                )
            }
        }
    }

    /**
     * Build a combined ColorMatrix from multiple filter functions.
     */
    private fun buildCombinedColorMatrix(filters: List<FilterFunction>): ColorMatrix? {
        if (filters.isEmpty()) return null

        var matrix: ColorMatrix? = null

        filters.forEach { filter ->
            matrix = when (filter) {
                is FilterFunction.Brightness -> applyBrightness(matrix, filter.amount)
                is FilterFunction.Contrast -> applyContrast(matrix, filter.amount)
                is FilterFunction.Grayscale -> applyGrayscale(matrix, filter.amount)
                is FilterFunction.HueRotate -> applyHueRotate(matrix, filter.degrees)
                is FilterFunction.Saturate -> applySaturate(matrix, filter.amount)
                is FilterFunction.Sepia -> applySepia(matrix, filter.amount)
                is FilterFunction.Invert -> applyInvert(matrix, filter.amount)
                else -> matrix
            }
        }

        return matrix
    }

    // ==================== PUBLIC API FOR EXTERNAL USE ====================

    /**
     * Generate a ColorFilter from a list of filter functions.
     * Useful for applying to images or custom drawing.
     */
    fun generateColorFilter(filters: List<FilterFunction>): ColorFilter? {
        val matrix = buildCombinedColorMatrix(filters)
        return matrix?.let { ColorFilter.colorMatrix(it) }
    }

    /**
     * Generate a combined ColorMatrix from filter config.
     */
    fun generateColorMatrix(config: FilterConfig): ColorMatrix? {
        val colorMatrixFilters = config.filters.filter { it.isColorMatrixFilter() }
        return buildCombinedColorMatrix(colorMatrixFilters)
    }

    // ==================== COLOR MATRIX GENERATORS ====================

    /**
     * Apply brightness adjustment to a color matrix.
     * @param brightness 1.0 = normal, <1.0 = darker, >1.0 = brighter
     */
    private fun applyBrightness(existing: ColorMatrix?, brightness: Float): ColorMatrix {
        val matrix = existing ?: ColorMatrix()
        val scale = brightness

        val brightnessMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, 0f,
            0f, scale, 0f, 0f, 0f,
            0f, 0f, scale, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        matrix.timesAssign(brightnessMatrix)
        return matrix
    }

    /**
     * Apply contrast adjustment to a color matrix.
     * @param contrast 1.0 = normal, <1.0 = less contrast, >1.0 = more contrast
     */
    private fun applyContrast(existing: ColorMatrix?, contrast: Float): ColorMatrix {
        val matrix = existing ?: ColorMatrix()
        val translate = (1f - contrast) / 2f * 255f

        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))

        matrix.timesAssign(contrastMatrix)
        return matrix
    }

    /**
     * Apply grayscale effect to a color matrix.
     * @param amount 0.0 = no effect, 1.0 = fully grayscale
     */
    private fun applyGrayscale(existing: ColorMatrix?, amount: Float): ColorMatrix {
        val matrix = existing ?: ColorMatrix()
        val invAmount = 1f - amount.coerceIn(0f, 1f)

        // Luminance-preserving grayscale matrix
        val grayscaleMatrix = ColorMatrix(floatArrayOf(
            0.2126f + 0.7874f * invAmount, 0.7152f - 0.7152f * invAmount, 0.0722f - 0.0722f * invAmount, 0f, 0f,
            0.2126f - 0.2126f * invAmount, 0.7152f + 0.2848f * invAmount, 0.0722f - 0.0722f * invAmount, 0f, 0f,
            0.2126f - 0.2126f * invAmount, 0.7152f - 0.7152f * invAmount, 0.0722f + 0.9278f * invAmount, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        matrix.timesAssign(grayscaleMatrix)
        return matrix
    }

    /**
     * Apply hue rotation to a color matrix.
     * @param degrees Rotation angle in degrees
     */
    private fun applyHueRotate(existing: ColorMatrix?, degrees: Float): ColorMatrix {
        val matrix = existing ?: ColorMatrix()

        val radians = Math.toRadians(degrees.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()

        // Luminance-preserving hue rotation
        val lumR = 0.213f
        val lumG = 0.715f
        val lumB = 0.072f

        val hueRotateMatrix = ColorMatrix(floatArrayOf(
            lumR + cos * (1 - lumR) + sin * (-lumR),
            lumG + cos * (-lumG) + sin * (-lumG),
            lumB + cos * (-lumB) + sin * (1 - lumB),
            0f, 0f,

            lumR + cos * (-lumR) + sin * 0.143f,
            lumG + cos * (1 - lumG) + sin * 0.140f,
            lumB + cos * (-lumB) + sin * (-0.283f),
            0f, 0f,

            lumR + cos * (-lumR) + sin * (-(1 - lumR)),
            lumG + cos * (-lumG) + sin * lumG,
            lumB + cos * (1 - lumB) + sin * lumB,
            0f, 0f,

            0f, 0f, 0f, 1f, 0f
        ))

        matrix.timesAssign(hueRotateMatrix)
        return matrix
    }

    /**
     * Apply saturation adjustment to a color matrix.
     * @param saturation 0.0 = grayscale, 1.0 = normal, >1.0 = oversaturated
     */
    private fun applySaturate(existing: ColorMatrix?, saturation: Float): ColorMatrix {
        val matrix = existing ?: ColorMatrix()

        val invAmount = 1f - saturation
        val r = 0.213f * invAmount
        val g = 0.715f * invAmount
        val b = 0.072f * invAmount

        val saturateMatrix = ColorMatrix(floatArrayOf(
            r + saturation, g, b, 0f, 0f,
            r, g + saturation, b, 0f, 0f,
            r, g, b + saturation, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        matrix.timesAssign(saturateMatrix)
        return matrix
    }

    /**
     * Apply sepia tone effect to a color matrix.
     * @param amount 0.0 = no effect, 1.0 = fully sepia
     */
    private fun applySepia(existing: ColorMatrix?, amount: Float): ColorMatrix {
        val matrix = existing ?: ColorMatrix()
        val amt = amount.coerceIn(0f, 1f)
        val invAmt = 1f - amt

        val sepiaMatrix = ColorMatrix(floatArrayOf(
            invAmt + amt * 0.393f, amt * 0.769f, amt * 0.189f, 0f, 0f,
            amt * 0.349f, invAmt + amt * 0.686f, amt * 0.168f, 0f, 0f,
            amt * 0.272f, amt * 0.534f, invAmt + amt * 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        matrix.timesAssign(sepiaMatrix)
        return matrix
    }

    /**
     * Apply color inversion to a color matrix.
     * @param amount 0.0 = no effect, 1.0 = fully inverted
     */
    private fun applyInvert(existing: ColorMatrix?, amount: Float): ColorMatrix {
        val matrix = existing ?: ColorMatrix()
        val amt = amount.coerceIn(0f, 1f)

        // newColor = amount * (1 - color) + (1 - amount) * color
        val scale = 1f - 2f * amt
        val translate = amt * 255f

        val invertMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))

        matrix.timesAssign(invertMatrix)
        return matrix
    }
}
