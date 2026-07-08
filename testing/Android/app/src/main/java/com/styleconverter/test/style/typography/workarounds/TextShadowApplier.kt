package com.styleconverter.test.style.typography.workarounds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Applies CSS text-shadow using Compose workarounds.
 *
 * ## CSS Property
 * ```css
 * .shadowed-text {
 *     text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.5);
 * }
 *
 * /* Multiple shadows */
 * .multi-shadow {
 *     text-shadow:
 *         1px 1px 2px black,
 *         0 0 10px blue,
 *         0 0 20px cyan;
 * }
 * ```
 *
 * ## Compose Support
 *
 * ### TextStyle.shadow
 * Compose TextStyle supports a single shadow via the `shadow` parameter.
 * This works well for simple shadows but has limitations:
 * - Only ONE shadow supported
 * - Blur is simulated, not true Gaussian blur
 * - No spread radius
 *
 * ### Multi-shadow Workaround
 * For multiple shadows, we render the text multiple times:
 * 1. Render shadow copies (back to front, blurred and offset)
 * 2. Render the main text on top
 *
 * ## Usage
 * ```kotlin
 * // Simple shadow via TextStyle
 * Text(
 *     text = "Hello",
 *     style = TextShadowApplier.textStyleWithShadow(
 *         baseStyle = MaterialTheme.typography.headlineLarge,
 *         shadow = TextShadowConfig(2.dp, 2.dp, 4.dp, Color.Black.copy(alpha = 0.5f))
 *     )
 * )
 *
 * // Multiple shadows via composable
 * TextShadowApplier.TextWithShadows(
 *     text = "Glowing",
 *     shadows = listOf(
 *         TextShadowConfig(0.dp, 0.dp, 10.dp, Color.Blue),
 *         TextShadowConfig(0.dp, 0.dp, 20.dp, Color.Cyan)
 *     ),
 *     style = TextStyle(fontSize = 32.sp, color = Color.White)
 * )
 * ```
 */
object TextShadowApplier {

    /**
     * Create a TextStyle with shadow applied.
     *
     * @param baseStyle Base text style
     * @param shadow Shadow configuration
     * @return TextStyle with shadow
     */
    fun textStyleWithShadow(
        baseStyle: TextStyle = TextStyle.Default,
        shadow: TextShadowConfig
    ): TextStyle {
        return baseStyle.copy(
            shadow = Shadow(
                color = shadow.color,
                offset = Offset(shadow.offsetX.value, shadow.offsetY.value),
                blurRadius = shadow.blurRadius.value
            )
        )
    }

    /**
     * Create a Shadow from TextShadowConfig.
     *
     * @param config Shadow configuration
     * @return Compose Shadow
     */
    fun toComposeShadow(config: TextShadowConfig): Shadow {
        return Shadow(
            color = config.color,
            offset = Offset(config.offsetX.value, config.offsetY.value),
            blurRadius = config.blurRadius.value
        )
    }

    /**
     * Composable that renders text with multiple shadows.
     *
     * Renders shadow layers first (back to front), then the main text.
     *
     * @param text Text content
     * @param shadows List of shadow configurations
     * @param style Text style (color will be used for main text)
     * @param modifier Modifier for the container
     * @param textAlign Text alignment
     * @param overflow Text overflow behavior
     * @param maxLines Maximum lines
     */
    @Composable
    fun TextWithShadows(
        text: String,
        shadows: List<TextShadowConfig>,
        style: TextStyle = TextStyle.Default,
        modifier: Modifier = Modifier,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Clip,
        maxLines: Int = Int.MAX_VALUE
    ) {
        Box(modifier = modifier) {
            // Render shadows from back to front
            shadows.reversed().forEach { shadow ->
                ShadowTextLayer(
                    text = text,
                    shadow = shadow,
                    style = style,
                    textAlign = textAlign,
                    overflow = overflow,
                    maxLines = maxLines
                )
            }

            // Render main text on top
            Text(
                text = text,
                style = style,
                textAlign = textAlign,
                overflow = overflow,
                maxLines = maxLines
            )
        }
    }

    /**
     * Render a single shadow layer.
     */
    @Composable
    private fun ShadowTextLayer(
        text: String,
        shadow: TextShadowConfig,
        style: TextStyle,
        textAlign: TextAlign?,
        overflow: TextOverflow,
        maxLines: Int
    ) {
        val shadowModifier = Modifier
            .offset(x = shadow.offsetX, y = shadow.offsetY)
            .then(
                if (shadow.blurRadius > 0.dp) {
                    Modifier.blur(shadow.blurRadius)
                } else {
                    Modifier
                }
            )

        Text(
            text = text,
            style = style.copy(color = shadow.color),
            textAlign = textAlign,
            overflow = overflow,
            maxLines = maxLines,
            modifier = shadowModifier
        )
    }

    /**
     * Composable for neon/glow text effect.
     *
     * Creates multiple blur layers to simulate a glow effect.
     *
     * @param text Text content
     * @param glowColor Color of the glow
     * @param textColor Color of the main text
     * @param style Base text style
     * @param glowIntensity Number of glow layers (more = more intense)
     * @param glowRadius Base blur radius for glow
     * @param modifier Modifier for container
     */
    @Composable
    fun NeonText(
        text: String,
        glowColor: Color,
        textColor: Color = Color.White,
        style: TextStyle = TextStyle.Default,
        glowIntensity: Int = 3,
        glowRadius: Dp = 10.dp,
        modifier: Modifier = Modifier
    ) {
        val shadows = (1..glowIntensity).map { layer ->
            TextShadowConfig(
                offsetX = 0.dp,
                offsetY = 0.dp,
                blurRadius = glowRadius * layer,
                color = glowColor.copy(alpha = glowColor.alpha / layer)
            )
        }

        TextWithShadows(
            text = text,
            shadows = shadows,
            style = style.copy(color = textColor),
            modifier = modifier
        )
    }

    /**
     * Composable for 3D/embossed text effect.
     *
     * Creates highlight and shadow layers for a raised appearance.
     *
     * @param text Text content
     * @param textColor Main text color
     * @param style Base text style
     * @param depth Depth of the 3D effect
     * @param lightAngle Angle of light source (degrees, 0 = right, 90 = top)
     * @param modifier Modifier for container
     */
    @Composable
    fun EmbossedText(
        text: String,
        textColor: Color = Color.Gray,
        style: TextStyle = TextStyle.Default,
        depth: Dp = 2.dp,
        lightAngle: Float = 135f,
        modifier: Modifier = Modifier
    ) {
        val radians = Math.toRadians(lightAngle.toDouble())
        val highlightX = (-kotlin.math.cos(radians) * depth.value).dp
        val highlightY = (-kotlin.math.sin(radians) * depth.value).dp
        val shadowX = (kotlin.math.cos(radians) * depth.value).dp
        val shadowY = (kotlin.math.sin(radians) * depth.value).dp

        val shadows = listOf(
            // Highlight (light side)
            TextShadowConfig(
                offsetX = highlightX,
                offsetY = highlightY,
                blurRadius = 0.dp,
                color = Color.White.copy(alpha = 0.7f)
            ),
            // Shadow (dark side)
            TextShadowConfig(
                offsetX = shadowX,
                offsetY = shadowY,
                blurRadius = 0.dp,
                color = Color.Black.copy(alpha = 0.5f)
            )
        )

        TextWithShadows(
            text = text,
            shadows = shadows,
            style = style.copy(color = textColor),
            modifier = modifier
        )
    }

    /**
     * Composable for long shadow effect (flat design style).
     *
     * Creates a stretched shadow at an angle.
     *
     * @param text Text content
     * @param textColor Main text color
     * @param shadowColor Shadow color
     * @param style Base text style
     * @param shadowLength Number of shadow layers
     * @param angle Angle of shadow (degrees)
     * @param modifier Modifier for container
     */
    @Composable
    fun LongShadowText(
        text: String,
        textColor: Color,
        shadowColor: Color = Color.Black.copy(alpha = 0.2f),
        style: TextStyle = TextStyle.Default,
        shadowLength: Int = 20,
        angle: Float = 45f,
        modifier: Modifier = Modifier
    ) {
        val radians = Math.toRadians(angle.toDouble())
        val stepX = kotlin.math.cos(radians).toFloat()
        val stepY = kotlin.math.sin(radians).toFloat()

        val shadows = (1..shadowLength).map { i ->
            TextShadowConfig(
                offsetX = (stepX * i).dp,
                offsetY = (stepY * i).dp,
                blurRadius = 0.dp,
                color = shadowColor
            )
        }

        TextWithShadows(
            text = text,
            shadows = shadows,
            style = style.copy(color = textColor),
            modifier = modifier
        )
    }

    /**
     * Composable for outlined/stroke text effect.
     *
     * Creates shadows in all directions for an outline appearance.
     *
     * @param text Text content
     * @param textColor Main text color (usually same as background)
     * @param outlineColor Outline color
     * @param outlineWidth Width of outline
     * @param style Base text style
     * @param modifier Modifier for container
     */
    @Composable
    fun OutlinedText(
        text: String,
        textColor: Color,
        outlineColor: Color = Color.Black,
        outlineWidth: Dp = 1.dp,
        style: TextStyle = TextStyle.Default,
        modifier: Modifier = Modifier
    ) {
        val w = outlineWidth.value

        // Create shadows in 8 directions for outline effect
        val shadows = listOf(
            TextShadowConfig((-w).dp, (-w).dp, 0.dp, outlineColor),
            TextShadowConfig(0.dp, (-w).dp, 0.dp, outlineColor),
            TextShadowConfig(w.dp, (-w).dp, 0.dp, outlineColor),
            TextShadowConfig((-w).dp, 0.dp, 0.dp, outlineColor),
            TextShadowConfig(w.dp, 0.dp, 0.dp, outlineColor),
            TextShadowConfig((-w).dp, w.dp, 0.dp, outlineColor),
            TextShadowConfig(0.dp, w.dp, 0.dp, outlineColor),
            TextShadowConfig(w.dp, w.dp, 0.dp, outlineColor)
        )

        TextWithShadows(
            text = text,
            shadows = shadows,
            style = style.copy(color = textColor),
            modifier = modifier
        )
    }
}

/**
 * Configuration for a single text shadow.
 */
data class TextShadowConfig(
    val offsetX: Dp = 0.dp,
    val offsetY: Dp = 0.dp,
    val blurRadius: Dp = 0.dp,
    val color: Color = Color.Black
) {
    companion object {
        val None = TextShadowConfig()

        /** Subtle drop shadow */
        val Subtle = TextShadowConfig(1.dp, 1.dp, 2.dp, Color.Black.copy(alpha = 0.3f))

        /** Standard drop shadow */
        val Standard = TextShadowConfig(2.dp, 2.dp, 4.dp, Color.Black.copy(alpha = 0.5f))

        /** Heavy drop shadow */
        val Heavy = TextShadowConfig(3.dp, 3.dp, 6.dp, Color.Black.copy(alpha = 0.7f))

        /** Glow effect (no offset, just blur) */
        fun glow(color: Color, radius: Dp = 8.dp) = TextShadowConfig(
            offsetX = 0.dp,
            offsetY = 0.dp,
            blurRadius = radius,
            color = color
        )
    }
}
