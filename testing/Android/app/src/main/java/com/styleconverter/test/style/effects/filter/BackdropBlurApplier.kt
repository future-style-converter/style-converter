package com.styleconverter.test.style.effects.filter

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Applies CSS backdrop-filter: blur() using Android platform workarounds.
 *
 * ## CSS Property
 * ```css
 * .frosted-glass {
 *     backdrop-filter: blur(10px);
 *     background: rgba(255, 255, 255, 0.3);
 * }
 * ```
 *
 * ## Platform Support
 *
 * | Android Version | Support Level |
 * |-----------------|---------------|
 * | Android 12+ (API 31+) | Full support via RenderEffect |
 * | Android 10-11 (API 29-30) | Fallback with translucent overlay |
 * | Below Android 10 | No blur, only overlay |
 *
 * ## Implementation Strategy
 *
 * ### Android 12+ (RenderEffect)
 * Uses `RenderEffect.createBlurEffect()` which applies a blur to what's
 * rendered behind the view. This is true backdrop blur.
 *
 * ### Android 10-11 (Fallback)
 * Uses a semi-transparent overlay with `Modifier.blur()`. This blurs the
 * overlay itself, not the content behind it. It's not true backdrop blur
 * but provides a similar visual effect in some cases.
 *
 * ### Older Versions
 * No blur effect; falls back to just the overlay color.
 *
 * ## Usage
 * ```kotlin
 * Box {
 *     // Background content
 *     Image(...)
 *
 *     // Frosted glass overlay
 *     Box(
 *         modifier = BackdropBlurApplier.applyBackdropBlur(
 *             modifier = Modifier.fillMaxSize(),
 *             blurRadius = 16.dp,
 *             overlayColor = Color.White.copy(alpha = 0.3f)
 *         )
 *     ) {
 *         Text("Frosted content")
 *     }
 * }
 * ```
 *
 * ## Limitations
 * - True backdrop blur only works on Android 12+
 * - RenderEffect affects performance on complex backgrounds
 * - Fallback is a visual approximation, not true blur
 * - Does not support other backdrop-filter functions (grayscale, etc.)
 */
object BackdropBlurApplier {

    /**
     * Check if true backdrop blur is supported.
     */
    val isBackdropBlurSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Apply backdrop blur effect to a modifier.
     *
     * On Android 12+, uses RenderEffect for true backdrop blur.
     * On older versions, falls back to overlay approximation.
     *
     * @param modifier Base modifier
     * @param blurRadius Blur radius in dp
     * @param overlayColor Optional semi-transparent overlay color
     * @return Modified Modifier with backdrop blur
     */
    fun applyBackdropBlur(
        modifier: Modifier,
        blurRadius: Dp,
        overlayColor: Color? = null
    ): Modifier {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applyRenderEffectBlur(modifier, blurRadius, overlayColor)
        } else {
            applyFallbackBlur(modifier, blurRadius, overlayColor)
        }
    }

    /**
     * Apply RenderEffect-based backdrop blur (Android 12+).
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyRenderEffectBlur(
        modifier: Modifier,
        blurRadius: Dp,
        overlayColor: Color?
    ): Modifier {
        val radiusPx = blurRadius.value // Approximate conversion

        var result = modifier.graphicsLayer {
            renderEffect = RenderEffect.createBlurEffect(
                radiusPx,
                radiusPx,
                Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }

        if (overlayColor != null) {
            result = result.background(overlayColor)
        }

        return result
    }

    /**
     * Apply fallback blur for older Android versions.
     *
     * This doesn't truly blur the backdrop, but provides a visual
     * approximation using a blurred overlay.
     */
    private fun applyFallbackBlur(
        modifier: Modifier,
        blurRadius: Dp,
        overlayColor: Color?
    ): Modifier {
        var result = modifier

        // On Android 10+, we can blur the overlay itself
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            result = result.blur(blurRadius)
        }

        // Apply overlay color
        if (overlayColor != null) {
            result = result.background(overlayColor)
        } else {
            // Default translucent white overlay for frosted effect
            result = result.background(Color.White.copy(alpha = 0.1f))
        }

        return result
    }

    /**
     * Composable that renders content with backdrop blur.
     *
     * This is an alternative API that wraps content in a blur container.
     *
     * @param blurRadius Blur radius
     * @param overlayColor Overlay color
     * @param modifier Modifier for the container
     * @param content Content to render on top of blur
     */
    @Composable
    fun BackdropBlurBox(
        blurRadius: Dp,
        overlayColor: Color = Color.White.copy(alpha = 0.3f),
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        Box(
            modifier = applyBackdropBlur(modifier, blurRadius, overlayColor)
        ) {
            content()
        }
    }

    /**
     * Create a frosted glass effect modifier.
     *
     * Combines backdrop blur with a translucent overlay for a
     * "frosted glass" or "glassmorphism" effect.
     *
     * @param blurRadius Blur amount
     * @param tintColor Tint color for the glass (usually white or black)
     * @param tintOpacity Opacity of the tint (0.1-0.3 typical)
     * @return Modifier with frosted glass effect
     */
    fun frostedGlass(
        blurRadius: Dp = 16.dp,
        tintColor: Color = Color.White,
        tintOpacity: Float = 0.2f
    ): Modifier {
        return applyBackdropBlur(
            modifier = Modifier,
            blurRadius = blurRadius,
            overlayColor = tintColor.copy(alpha = tintOpacity)
        )
    }

    /**
     * Acrylic/Fluent Design style blur effect.
     *
     * Mimics Windows Fluent Design acrylic material with:
     * - Backdrop blur
     * - Tint color overlay
     * - Noise texture overlay (for that authentic "frosted" look)
     *
     * @param blurRadius Blur amount (30dp typical for acrylic)
     * @param tintColor Tint color
     * @param tintOpacity Tint opacity
     * @param noiseOpacity Noise texture opacity (0.02-0.05 typical)
     * @return Modifier with acrylic effect
     */
    fun acrylicEffect(
        blurRadius: Dp = 30.dp,
        tintColor: Color = Color.White,
        tintOpacity: Float = 0.15f,
        noiseOpacity: Float = 0.02f
    ): Modifier {
        val noiseTexture = NoiseTextureGenerator.generateNoiseTexture()

        return applyBackdropBlur(
            modifier = Modifier,
            blurRadius = blurRadius,
            overlayColor = tintColor.copy(alpha = tintOpacity)
        ).drawWithContent {
            drawContent()
            // Draw noise overlay
            if (noiseOpacity > 0f) {
                val brush = ShaderBrush(
                    ImageShader(
                        noiseTexture,
                        TileMode.Repeated,
                        TileMode.Repeated
                    )
                )
                drawRect(
                    brush = brush,
                    alpha = noiseOpacity,
                    blendMode = BlendMode.Overlay
                )
            }
        }
    }

    /**
     * Composable version of acrylic effect that remembers the noise texture.
     */
    @Composable
    fun rememberAcrylicEffect(
        blurRadius: Dp = 30.dp,
        tintColor: Color = Color.White,
        tintOpacity: Float = 0.15f,
        noiseOpacity: Float = 0.02f
    ): Modifier {
        val noiseTexture = remember { NoiseTextureGenerator.generateNoiseTexture() }

        return applyBackdropBlur(
            modifier = Modifier,
            blurRadius = blurRadius,
            overlayColor = tintColor.copy(alpha = tintOpacity)
        ).drawWithContent {
            drawContent()
            if (noiseOpacity > 0f) {
                val brush = ShaderBrush(
                    ImageShader(
                        noiseTexture,
                        TileMode.Repeated,
                        TileMode.Repeated
                    )
                )
                drawRect(
                    brush = brush,
                    alpha = noiseOpacity,
                    blendMode = BlendMode.Overlay
                )
            }
        }
    }

    /**
     * Check platform capability and return appropriate blur strategy.
     */
    fun getBlurCapability(): BlurCapability {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> BlurCapability.RENDER_EFFECT
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> BlurCapability.MODIFIER_BLUR
            else -> BlurCapability.NONE
        }
    }

    /**
     * Blur capability levels.
     */
    enum class BlurCapability {
        /** Full backdrop blur via RenderEffect (Android 12+) */
        RENDER_EFFECT,
        /** Modifier.blur() available but not true backdrop (Android 10+) */
        MODIFIER_BLUR,
        /** No blur support */
        NONE
    }

    /**
     * Configuration for backdrop blur effect.
     */
    data class BackdropBlurConfig(
        val blurRadius: Dp = 16.dp,
        val overlayColor: Color? = null,
        val saturate: Float = 1f,  // For future: saturate filter
        val brightness: Float = 1f  // For future: brightness filter
    ) {
        companion object {
            val FrostedGlass = BackdropBlurConfig(
                blurRadius = 16.dp,
                overlayColor = Color.White.copy(alpha = 0.3f)
            )

            val DarkGlass = BackdropBlurConfig(
                blurRadius = 20.dp,
                overlayColor = Color.Black.copy(alpha = 0.4f)
            )

            val Subtle = BackdropBlurConfig(
                blurRadius = 8.dp,
                overlayColor = Color.White.copy(alpha = 0.1f)
            )

            val Heavy = BackdropBlurConfig(
                blurRadius = 40.dp,
                overlayColor = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Generates procedural noise textures for acrylic effects.
 *
 * Creates a tileable noise texture that can be overlaid on blur effects
 * to create the characteristic "grain" of Fluent Design acrylic materials.
 */
object NoiseTextureGenerator {
    // Cached noise texture (generated once)
    private var cachedNoiseTexture: ImageBitmap? = null

    // Default noise texture size (small, will be tiled)
    private const val NOISE_SIZE = 128

    /**
     * Generate a grayscale noise texture suitable for overlay effects.
     *
     * The texture is:
     * - Tileable (seamless edges)
     * - Grayscale (works with any tint color)
     * - Small and cached (efficient memory usage)
     *
     * @param size Size of the noise texture in pixels
     * @param seed Random seed for reproducibility
     * @return ImageBitmap containing the noise texture
     */
    fun generateNoiseTexture(
        size: Int = NOISE_SIZE,
        seed: Long = 42L
    ): ImageBitmap {
        cachedNoiseTexture?.let { return it }

        val random = Random(seed)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)

        // Generate random grayscale noise
        for (i in pixels.indices) {
            val gray = random.nextInt(256)
            // ARGB: Full alpha, gray for R, G, B
            pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }

        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)

        val imageBitmap = bitmap.asImageBitmap()
        cachedNoiseTexture = imageBitmap
        return imageBitmap
    }

    /**
     * Generate a Perlin-like smooth noise texture.
     *
     * Provides a more organic look compared to pure random noise.
     * Uses value noise with interpolation for smoother gradients.
     *
     * @param size Size of the noise texture
     * @param frequency Noise frequency (higher = more detail)
     * @param octaves Number of noise layers to combine
     * @return ImageBitmap containing the smooth noise texture
     */
    fun generateSmoothNoiseTexture(
        size: Int = NOISE_SIZE,
        frequency: Float = 8f,
        octaves: Int = 4
    ): ImageBitmap {
        val random = Random(42L)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)

        // Generate base noise grid for interpolation
        val gridSize = (frequency + 1).toInt()
        val baseNoise = Array(gridSize) { FloatArray(gridSize) { random.nextFloat() } }

        for (y in 0 until size) {
            for (x in 0 until size) {
                var amplitude = 1f
                var totalAmplitude = 0f
                var noiseValue = 0f

                // Combine multiple octaves
                for (octave in 0 until octaves) {
                    val freq = frequency * (1 shl octave) / size
                    val sampleX = x * freq
                    val sampleY = y * freq

                    val noise = interpolatedNoise(sampleX, sampleY, baseNoise, gridSize)
                    noiseValue += noise * amplitude

                    totalAmplitude += amplitude
                    amplitude *= 0.5f
                }

                // Normalize to 0-255
                val normalized = ((noiseValue / totalAmplitude) * 255).toInt().coerceIn(0, 255)
                pixels[y * size + x] = (0xFF shl 24) or (normalized shl 16) or (normalized shl 8) or normalized
            }
        }

        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap.asImageBitmap()
    }

    /**
     * Generate fine-grain noise suitable for subtle textures.
     *
     * @param size Texture size
     * @param grainSize Size of each grain (1 = single pixel, larger = blocky)
     * @return ImageBitmap containing fine grain noise
     */
    fun generateFineGrainNoise(
        size: Int = NOISE_SIZE,
        grainSize: Int = 1
    ): ImageBitmap {
        val random = Random(42L)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)

        val effectiveGrain = grainSize.coerceAtLeast(1)
        val gridSize = (size + effectiveGrain - 1) / effectiveGrain

        // Generate grid of random values
        val grid = Array(gridSize) { IntArray(gridSize) { random.nextInt(256) } }

        for (y in 0 until size) {
            for (x in 0 until size) {
                val gray = grid[y / effectiveGrain][x / effectiveGrain]
                pixels[y * size + x] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
        }

        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap.asImageBitmap()
    }

    /**
     * Bilinear interpolation for smooth noise.
     */
    private fun interpolatedNoise(
        x: Float,
        y: Float,
        baseNoise: Array<FloatArray>,
        gridSize: Int
    ): Float {
        val x0 = x.toInt() % gridSize
        val y0 = y.toInt() % gridSize
        val x1 = (x0 + 1) % gridSize
        val y1 = (y0 + 1) % gridSize

        val xFrac = x - x.toInt()
        val yFrac = y - y.toInt()

        // Bilinear interpolation
        val top = lerp(baseNoise[y0][x0], baseNoise[y0][x1], xFrac)
        val bottom = lerp(baseNoise[y1][x0], baseNoise[y1][x1], xFrac)
        return lerp(top, bottom, yFrac)
    }

    /**
     * Linear interpolation between two values.
     */
    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }

    /**
     * Clear the cached noise texture (for memory management).
     */
    fun clearCache() {
        cachedNoiseTexture = null
    }
}
