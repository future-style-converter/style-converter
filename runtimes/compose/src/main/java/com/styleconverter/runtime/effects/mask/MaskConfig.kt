package com.styleconverter.runtime.effects.mask

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for CSS mask properties.
 *
 * ## Supported Properties
 * - mask-image: URL, gradient, or none
 * - mask-mode: alpha, luminance, match-source
 * - mask-size: contain, cover, auto, or dimensions
 * - mask-repeat: repeat, no-repeat, repeat-x, repeat-y, space, round
 * - mask-position: position within the element
 * - mask-composite: add, subtract, intersect, exclude
 * - mask-clip: content-box, padding-box, border-box
 * - mask-origin: content-box, padding-box, border-box
 *
 * ## Compose Mapping
 * Mask effects require custom Canvas/shader implementation.
 * Use this config to drive BlendMode and custom drawing logic.
 *
 * ## Gradient Mask Implementation
 * Gradient masks are rendered using graphicsLayer + drawWithContent with BlendMode.DstIn
 * to apply the gradient's alpha channel as a mask to the content.
 *
 * ## URL Mask Implementation
 * url() masks render on the Modifier path when the source is a `data:` URI —
 * decoded synchronously (capture-deterministic) and drawn as a DstIn tile
 * lattice; the image's alpha channel (or luminance in luminance mode) is the
 * mask. Remote http(s) sources are a documented, logged no-op (async fetch
 * would race the first-frame capture) — see MaskApplier.applyUrlMask.
 */
data class MaskConfig(
    /** Whether a mask image is defined */
    val hasImage: Boolean = false,
    /** URL of mask image if applicable */
    val imageUrl: String? = null,
    /** Whether the mask is a gradient */
    val isGradient: Boolean = false,
    /** Gradient configuration if mask is a gradient */
    val gradient: MaskGradientConfig? = null,
    /** How the mask values are interpreted */
    val mode: MaskModeValue = MaskModeValue.MATCH_SOURCE,
    /** Size of the mask */
    val size: MaskSizeValue = MaskSizeValue.Auto,
    /** How the mask repeats */
    val repeat: MaskRepeatValue = MaskRepeatValue.REPEAT,
    /** Position of the mask within the element */
    val position: MaskPositionValue = MaskPositionValue(),
    /** How multiple mask layers are combined */
    val composite: MaskCompositeValue = MaskCompositeValue.ADD,
    /** Box to which mask is clipped */
    val clip: MaskBoxValue = MaskBoxValue.BORDER_BOX,
    /** Box from which mask position is calculated */
    val origin: MaskBoxValue = MaskBoxValue.BORDER_BOX
) {
    /** Returns true if any mask property is set */
    val hasMask: Boolean
        get() = hasImage

    /** Returns true if this is a URL-based mask */
    val isUrlMask: Boolean
        get() = hasImage && imageUrl != null && !isGradient

    companion object {
        val Default = MaskConfig()
        val None = MaskConfig(hasImage = false)
    }
}

/**
 * Gradient configuration for mask-image.
 */
sealed interface MaskGradientConfig {
    /**
     * Linear gradient mask.
     * CSS: mask-image: linear-gradient(...)
     */
    data class Linear(
        val angle: Float,
        val colorStops: List<MaskColorStop>,
        val repeating: Boolean = false
    ) : MaskGradientConfig

    /**
     * Radial gradient mask.
     * CSS: mask-image: radial-gradient(...)
     */
    data class Radial(
        val centerX: Float = 0.5f,
        val centerY: Float = 0.5f,
        val colorStops: List<MaskColorStop>,
        val repeating: Boolean = false,
        /**
         * Ending shape (css-images-3 §3.2 `<ending-shape>`): the IR wire
         * carries `"shape": "circle" | "ellipse"` when the author wrote
         * one (MaskImageProperty.RadialGradient.shape); null when omitted.
         * Null MUST render as ELLIPSE — that is the CSS default — so the
         * applier treats null and ELLIPSE identically.
         */
        val shape: MaskRadialShape? = null
    ) : MaskGradientConfig

    /**
     * Conic (sweep) gradient mask.
     * CSS: mask-image: conic-gradient(...)
     */
    data class Conic(
        val centerX: Float = 0.5f,
        val centerY: Float = 0.5f,
        val angle: Float = 0f,
        val colorStops: List<MaskColorStop>,
        val repeating: Boolean = false
    ) : MaskGradientConfig
}

/**
 * Radial mask ending shape (css-images-3 §3.2). Mirrors the converter's
 * MaskImageValue.GradientShape enum — only these two keywords exist in
 * the `radial-gradient()` grammar.
 */
enum class MaskRadialShape {
    /** `circle` — single radius, both axes equal. */
    CIRCLE,
    /** `ellipse` — independent rx/ry; the CSS default when omitted. */
    ELLIPSE
}

/**
 * Color stop in a mask gradient.
 *
 * For masks, colors are typically black/white to define opacity levels,
 * but any color's alpha channel (or luminance) can be used.
 */
data class MaskColorStop(
    val color: Color,
    val position: Float
)

/**
 * CSS mask-mode values.
 */
enum class MaskModeValue {
    /** Use alpha channel as mask */
    ALPHA,
    /** Use luminance as mask */
    LUMINANCE,
    /** Match the source type (alpha for image, luminance for SVG) */
    MATCH_SOURCE
}

/**
 * CSS mask-size values.
 */
sealed interface MaskSizeValue {
    /** Auto sizing (use intrinsic dimensions) */
    data object Auto : MaskSizeValue
    /** Scale to fit while maintaining aspect ratio */
    data object Contain : MaskSizeValue
    /** Scale to cover while maintaining aspect ratio */
    data object Cover : MaskSizeValue
    /** Explicit dimensions */
    data class Dimensions(val width: Dp?, val height: Dp?) : MaskSizeValue
}

/**
 * CSS mask-repeat values.
 */
enum class MaskRepeatValue {
    /** Repeat both horizontally and vertically */
    REPEAT,
    /** No repeat */
    NO_REPEAT,
    /** Repeat horizontally only */
    REPEAT_X,
    /** Repeat vertically only */
    REPEAT_Y,
    /** Repeat with spacing to fill area evenly */
    SPACE,
    /** Repeat and stretch to fill area evenly */
    ROUND
}

/**
 * CSS mask-position value.
 */
data class MaskPositionValue(
    val x: PositionComponent = PositionComponent.Keyword(HorizontalPosition.LEFT),
    val y: PositionComponent = PositionComponent.Keyword(VerticalPosition.TOP)
)

/**
 * Position component for mask positioning.
 */
sealed interface PositionComponent {
    data class Keyword(val position: Any) : PositionComponent
    data class Length(val value: Dp) : PositionComponent
    data class Percentage(val value: Float) : PositionComponent
}

/**
 * Horizontal position keywords.
 */
enum class HorizontalPosition {
    LEFT, CENTER, RIGHT
}

/**
 * Vertical position keywords.
 */
enum class VerticalPosition {
    TOP, CENTER, BOTTOM
}

/**
 * CSS mask-composite values.
 */
enum class MaskCompositeValue {
    /** Source is placed over destination */
    ADD,
    /** Source minus destination */
    SUBTRACT,
    /** Source intersect with destination */
    INTERSECT,
    /** Source XOR destination */
    EXCLUDE
}

/**
 * CSS mask-clip and mask-origin box values.
 */
enum class MaskBoxValue {
    /** Content box area */
    CONTENT_BOX,
    /** Padding box area */
    PADDING_BOX,
    /** Border box area */
    BORDER_BOX,
    /** Fill box (SVG) */
    FILL_BOX,
    /** Stroke box (SVG) */
    STROKE_BOX,
    /** View box (SVG) */
    VIEW_BOX,
    /** No clipping */
    NO_CLIP
}
