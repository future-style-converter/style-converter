package com.styleconverter.runtime.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for color-related styling properties.
 *
 * This config aggregates background colors, opacity, and background images (gradients)
 * into a single structure that can be applied to a Modifier.
 *
 * ## Supported Properties
 * - BackgroundColor: Solid background color
 * - Opacity: Alpha transparency (0.0-1.0)
 * - BackgroundImage: Gradients (linear, radial, conic) and URLs
 * - BackgroundPosition: Position of background image
 * - BackgroundSize: Size of background image
 * - BackgroundRepeat: How background image repeats
 * - BackgroundAttachment: Scroll behavior (limited in Compose)
 *
 * ## Usage
 * ```kotlin
 * val config = ColorExtractor.extractColorConfig(properties)
 * val modifier = ColorApplier.applyColors(Modifier, config)
 * ```
 */
data class ColorConfig(
    /** Solid background color, if specified */
    val backgroundColor: Color? = null,
    /** Opacity/alpha value (0.0-1.0), if specified */
    val opacity: Float? = null,
    /** List of background images (gradients, URLs) in layer order */
    val backgroundImages: List<BackgroundImageConfig> = emptyList(),
    /** Background position configuration */
    val backgroundPosition: BackgroundPositionConfig = BackgroundPositionConfig(),
    /** Background size configuration */
    val backgroundSize: BackgroundSizeConfig = BackgroundSizeConfig.Auto,
    /** Background repeat configuration */
    val backgroundRepeat: BackgroundRepeatConfig = BackgroundRepeatConfig.REPEAT,
    /** Background attachment (scroll/fixed) */
    val backgroundAttachment: BackgroundAttachment = BackgroundAttachment.SCROLL,
    /**
     * Set when CSS `background-clip: text` was declared. The
     * placeholder text renderer (ComponentRenderer.PlaceholderContent)
     * detects the same flag and paints the gradient as `TextStyle.brush`
     * instead of letting it fill the rectangular box; ColorApplier reads
     * this flag to skip the regular `Modifier.background(brush)` paint
     * so we don't end up with both a clipped-text fill AND a rectangular
     * gradient behind it. ColorExtractor sets this from the
     * BackgroundClip property at extract time.
     */
    val suppressBackgroundImage: Boolean = false,
    /**
     * Per-layer blend modes from CSS `background-blend-mode`. Parallel
     * to `backgroundImages`: index N describes how layer N composites
     * against the union of layers 0..N-1 (and the bg-color underneath).
     * Empty list = no per-layer blending (every layer uses SrcOver).
     * Threaded through here from BlendModeExtractor so ColorApplier
     * can wrap each `applyBackgroundImage` call in a saveLayer with
     * the matching paint.blendMode.
     */
    val backgroundBlendModes: List<androidx.compose.ui.graphics.BlendMode> = emptyList(),
) {
    /** Returns true if any color-related property is set */
    val hasColor: Boolean get() = backgroundColor != null || opacity != null || backgroundImages.isNotEmpty() ||
        backgroundPosition.hasPosition || backgroundSize != BackgroundSizeConfig.Auto ||
        backgroundRepeat != BackgroundRepeatConfig.REPEAT

    /** Returns true if a gradient background is present */
    val hasGradient: Boolean get() = backgroundImages.any {
        it is BackgroundImageConfig.LinearGradient ||
        it is BackgroundImageConfig.RadialGradient ||
        it is BackgroundImageConfig.ConicGradient
    }
}

/**
 * Sealed interface representing different types of CSS background-image values.
 *
 * ## Supported Types
 * - LinearGradient: CSS linear-gradient() and repeating-linear-gradient()
 * - RadialGradient: CSS radial-gradient() and repeating-radial-gradient()
 * - ConicGradient: CSS conic-gradient() (mapped to Compose sweepGradient)
 * - Url: CSS url() for image references (not rendered, placeholder only)
 * - None: CSS none value
 */
sealed interface BackgroundImageConfig {

    /**
     * Linear gradient configuration.
     *
     * CSS: `linear-gradient(angle, color-stop1, color-stop2, ...)`
     * Compose: `Brush.linearGradient()`
     *
     * @property angle Direction in degrees (CSS: 0deg = to top, 90deg = to right)
     * @property colorStops List of color stops with positions
     * @property repeating Whether this is a repeating gradient
     */
    data class LinearGradient(
        val angle: Float,
        val colorStops: List<ColorStop>,
        val repeating: Boolean = false
    ) : BackgroundImageConfig

    /**
     * Radial gradient configuration.
     *
     * CSS: `radial-gradient(shape size at position, color-stop1, ...)`
     * Compose: `Brush.radialGradient()`
     *
     * @property centerX Horizontal center position (0.0-1.0 fraction)
     * @property centerY Vertical center position (0.0-1.0 fraction)
     * @property colorStops List of color stops with positions
     * @property repeating Whether this is a repeating gradient
     */
    /**
     * Ending shape of a CSS radial gradient (Images Module 3 §3.5):
     *   ellipse: distinct horizontal / vertical radii (default)
     *   circle:  single radius — both axes match
     */
    enum class RadialShape { ELLIPSE, CIRCLE }

    /**
     * Sizing keyword that determines how far the gradient line extends.
     * Each combines with the shape to set the actual radii at draw time:
     *   closest-side    → radius reaches the closest side
     *   closest-corner  → radius reaches the closest corner
     *   farthest-side   → radius reaches the farthest side
     *   farthest-corner → radius reaches the farthest corner (default)
     */
    enum class RadialSize { CLOSEST_SIDE, CLOSEST_CORNER, FARTHEST_SIDE, FARTHEST_CORNER }

    data class RadialGradient(
        val centerX: Float,
        val centerY: Float,
        val colorStops: List<ColorStop>,
        val repeating: Boolean = false,
        // Shape/size are nullable so that fixtures emitting bare
        // `radial-gradient(red, blue)` (no shape/size prefix) keep their
        // pre-Phase-12 default behaviour (ellipse / farthest-corner — the
        // CSS defaults).
        val shape: RadialShape? = null,
        val size: RadialSize? = null,
    ) : BackgroundImageConfig

    /**
     * Conic (sweep) gradient configuration.
     *
     * CSS: `conic-gradient(from angle at position, color-stop1, ...)`
     * Compose: `Brush.sweepGradient()` (note: no TileMode support)
     *
     * @property centerX Horizontal center position (0.0-1.0 fraction)
     * @property centerY Vertical center position (0.0-1.0 fraction)
     * @property angle Starting angle in degrees
     * @property colorStops List of color stops with positions
     * @property repeating Whether this is a repeating gradient (limited support in Compose)
     */
    data class ConicGradient(
        val centerX: Float,
        val centerY: Float,
        val angle: Float,
        val colorStops: List<ColorStop>,
        val repeating: Boolean = false
    ) : BackgroundImageConfig

    /**
     * URL reference to an image.
     *
     * CSS: `url(path/to/image.png)`
     * Note: Image loading is not supported in this context; use Coil or similar.
     *
     * @property url The URL or path to the image
     */
    data class Url(val url: String) : BackgroundImageConfig

    /**
     * No background image.
     *
     * CSS: `background-image: none`
     */
    data object None : BackgroundImageConfig
}

/**
 * A color stop in a gradient.
 *
 * CSS: `red 25%` or `#ff0000 0.25`
 * Compose: Pair<Float, Color> for colorStops parameter
 *
 * @property color The color at this stop
 * @property position Position in the gradient (0.0-1.0)
 */
data class ColorStop(
    val color: Color,
    val position: Float
)

/**
 * Background position configuration.
 *
 * CSS: `background-position: center center` or `background-position: 50% 50%`
 */
data class BackgroundPositionConfig(
    /** Horizontal position (0.0 = left, 0.5 = center, 1.0 = right) */
    val x: Float = 0f,
    /** Vertical position (0.0 = top, 0.5 = center, 1.0 = bottom) */
    val y: Float = 0f,
    /** Horizontal offset in Dp (added to percentage position) */
    val xOffset: Dp = 0.dp,
    /** Vertical offset in Dp (added to percentage position) */
    val yOffset: Dp = 0.dp
) {
    val hasPosition: Boolean get() = x != 0f || y != 0f || xOffset != 0.dp || yOffset != 0.dp

    companion object {
        val CENTER = BackgroundPositionConfig(0.5f, 0.5f)
        val TOP_LEFT = BackgroundPositionConfig(0f, 0f)
        val TOP_CENTER = BackgroundPositionConfig(0.5f, 0f)
        val TOP_RIGHT = BackgroundPositionConfig(1f, 0f)
        val CENTER_LEFT = BackgroundPositionConfig(0f, 0.5f)
        val CENTER_RIGHT = BackgroundPositionConfig(1f, 0.5f)
        val BOTTOM_LEFT = BackgroundPositionConfig(0f, 1f)
        val BOTTOM_CENTER = BackgroundPositionConfig(0.5f, 1f)
        val BOTTOM_RIGHT = BackgroundPositionConfig(1f, 1f)
    }
}

/**
 * Background size configuration.
 *
 * CSS: `background-size: cover` or `background-size: 100px 50px`
 */
sealed interface BackgroundSizeConfig {
    /** Use intrinsic image size */
    data object Auto : BackgroundSizeConfig

    /** Scale to cover entire element (may crop) */
    data object Cover : BackgroundSizeConfig

    /** Scale to fit within element (may have gaps) */
    data object Contain : BackgroundSizeConfig

    /** Explicit dimensions */
    data class Dimensions(
        val width: Dp? = null,
        val height: Dp? = null,
        val widthPercent: Float? = null,
        val heightPercent: Float? = null
    ) : BackgroundSizeConfig
}

/**
 * Background repeat configuration.
 *
 * CSS: `background-repeat: repeat` or `background-repeat: no-repeat`
 */
enum class BackgroundRepeatConfig {
    /** Repeat both horizontally and vertically */
    REPEAT,
    /** Repeat horizontally only */
    REPEAT_X,
    /** Repeat vertically only */
    REPEAT_Y,
    /** No repeat */
    NO_REPEAT,
    /** Repeat with even spacing */
    SPACE,
    /** Repeat and stretch to fill */
    ROUND
}

/**
 * Background attachment configuration.
 *
 * CSS: `background-attachment: scroll` or `background-attachment: fixed`
 * Note: Fixed attachment has limited support in Compose.
 */
enum class BackgroundAttachment {
    /** Background scrolls with content (default) */
    SCROLL,
    /** Background fixed relative to viewport (limited support) */
    FIXED,
    /** Background fixed relative to element's content */
    LOCAL
}
