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
    /**
     * PER-LAYER background sizes in SOURCE order (css-backgrounds-3 §2.3:
     * each comma-separated background-size entry pairs with the same-index
     * background-image layer; a shorter list repeats cyclically — the
     * applier's layerValue() implements the cycling). Empty = fall back to
     * the single [backgroundSize] (legacy single-layer callers).
     */
    val backgroundSizes: List<BackgroundSizeConfig> = emptyList(),
    /**
     * PER-LAYER, PER-AXIS repeat modes in source order. Unlike the single
     * [backgroundRepeat] enum this shape can express two-axis values like
     * `background-repeat: space round` (css-backgrounds-3 §3.7 two-value
     * syntax). Empty = fall back to the single field. Cycles like sizes.
     */
    val backgroundRepeats: List<BackgroundRepeatAxes> = emptyList(),
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
    /**
     * Paint-area insets for CSS `background-clip: padding-box | content-box`
     * (css-backgrounds-3 §3.11: the background painting area shrinks to the
     * padding box / content box). Null = border-box (the initial value) —
     * paint the whole box as before. When non-null, ColorApplier insets the
     * background-color / gradient fill by these edge amounts instead of
     * calling the full-box `Modifier.background`. Computed at extract time
     * from the element's border widths (padding-box) plus padding
     * (content-box), because the modifier chain applies padding LAST
     * (border-box sizing) so the paint pass can't discover them on its own.
     */
    val backgroundClipInsets: BackgroundClipInsets? = null,
) {
    /** Returns true if any color-related property is set */
    val hasColor: Boolean get() = backgroundColor != null || opacity != null || backgroundImages.isNotEmpty() ||
        backgroundPosition.hasPosition || backgroundSize != BackgroundSizeConfig.Auto ||
        backgroundRepeat != BackgroundRepeatConfig.REPEAT

    /** Returns true if a gradient background is present */
    val hasGradient: Boolean get() = backgroundImages.any {
        it is BackgroundImageConfig.LinearGradient ||
        it is BackgroundImageConfig.RadialGradient ||
        it is BackgroundImageConfig.ConicGradient ||
        // cross-fade() layers composite gradient/color sub-images — they
        // ride the same brush pipeline, so they count as gradient content.
        it is BackgroundImageConfig.CrossFade
    }
}

/**
 * One gradient-center axis (`at <position>`, css-images-3 §3.5 /
 * css-images-4 §3.4.4). CSS allows a full <length-percentage> per axis;
 * the IR wire carries percents as raw numbers and lengths as objects
 * (IRLengthPercentageSerializer). The extractor resolves runtime-dependent
 * units (lh/em/rem) to PX at extract time — font metrics live in the
 * component's own FontSize/LineHeight properties there — so the applier
 * only ever sees FRACTION or PX. Byte-parallel twin of the SwiftUI
 * `GradientCoord` (BackgroundImageConfig.swift): identical kinds and
 * identical resolve math.
 */
data class GradientCoord(val kind: Kind, val value: Float) {
    enum class Kind {
        /** 0..1 fraction of the gradient box axis (percent / keyword). */
        FRACTION,
        /** Absolute CSS pixels from the box origin (length centers). */
        PX
    }

    /**
     * Resolve to a 0..1 fraction of the given axis size in px. PX on a
     * degenerate (≤0) axis falls back to center — the CSS default — so a
     * zero-sized box can never divide by zero.
     */
    fun fraction(axisPx: Float): Float = when (kind) {
        Kind.FRACTION -> value
        Kind.PX -> if (axisPx > 0f) value / axisPx else 0.5f
    }

    companion object {
        /** CSS default center (50%). */
        val CENTER = GradientCoord(Kind.FRACTION, 0.5f)
        /** Fraction constructor (percent ÷ 100 done by the caller). */
        fun fraction(f: Float) = GradientCoord(Kind.FRACTION, f)
        /** Absolute-pixel constructor. */
        fun px(v: Float) = GradientCoord(Kind.PX, v)
    }
}

/**
 * Edge insets (border-box → painting-area) for CSS `background-clip:
 * padding-box | content-box` (css-backgrounds-3 §3.11). For padding-box the
 * insets are the computed border widths; for content-box they additionally
 * include the padding. All four zero ≡ border-box (no clip).
 */
data class BackgroundClipInsets(
    val top: Dp = 0.dp,
    val right: Dp = 0.dp,
    val bottom: Dp = 0.dp,
    val left: Dp = 0.dp
) {
    /** True when at least one edge actually shrinks the paint area. */
    val hasInsets: Boolean get() = top > 0.dp || right > 0.dp || bottom > 0.dp || left > 0.dp
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
        // `at <position>` per axis — FRACTION (percent/keyword) or PX
        // (length center, A-RC8); resolved against the draw size in the
        // applier's createShader (the only place the box size exists).
        val centerX: GradientCoord,
        val centerY: GradientCoord,
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
        // `at <position>` per axis — same FRACTION|PX contract as radial.
        val centerX: GradientCoord,
        val centerY: GradientCoord,
        val angle: Float,
        val colorStops: List<ColorStop>,
        val repeating: Boolean = false
    ) : BackgroundImageConfig

    /**
     * A bare `<color>` used AS an image — only produced as a cross-fade()
     * argument (css-images-4 §2.6.2 `<cf-image>` allows `<color>`).
     * Renders as a solid fill of the gradient box.
     */
    data class SolidColor(val color: Color) : BackgroundImageConfig

    /**
     * cross-fade() (css-images-4 §2.6.2). Entries carry EFFECTIVE weights
     * as 0..1 fractions — the extractor runs CrossFadeMath.normalizeWeights
     * (the shared spec normalization, byte-parallel across all three
     * platforms) on the authored wire weights. The applier composites
     * `Σ wᵢ × premultiplied(imageᵢ)` via additive blending; weights summing
     * below 1 leave the remainder TRANSPARENT (the target-alpha WPT).
     */
    data class CrossFade(val entries: List<CrossFadeEntry>) : BackgroundImageConfig

    /** One weighted cross-fade image: effective fraction + sub-image. */
    data class CrossFadeEntry(val weight: Float, val image: BackgroundImageConfig)

    /**
     * URL reference to an image.
     *
     * CSS: `url(path/to/image.png)`
     * `data:` URIs render (ColorApplier decodes them synchronously via
     * SyncImageDecode and tiles them through BackgroundTileMath — wave 9);
     * remote schemes are a documented, once-logged no-op because an async
     * fetch cannot be capture-deterministic.
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

    /**
     * Explicit dimensions. Exactly one of (width, widthPercent) is set per
     * axis; both null = that axis is `auto` (resolves to the container axis
     * for gradients, which have no intrinsic size — css-backgrounds-3 §3.9).
     * The *Percent fields are 0..1 FRACTIONS of the positioning area (the
     * extractor divides the CSS 0..100 percentage by 100) — pinned by
     * ColorExtractorTest and consumed as fractions by ColorApplier's tile
     * math (both the gradient and the url()-image lattice paths).
     */
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
 * One AXIS of a background-repeat value (css-backgrounds-3 §3.7
 * <repeat-style>). The single-keyword forms expand per spec: `repeat-x` ≡
 * `repeat no-repeat`, `space` ≡ `space space`, etc. — see
 * [BackgroundRepeatAxes.from].
 */
enum class AxisRepeat {
    /** Tile edge-to-edge, clipping the last tile (`repeat`). */
    REPEAT,
    /** One tile only, at the background-position anchor (`no-repeat`). */
    NO_REPEAT,
    /** Whole tiles only, leftover distributed as equal gaps (`space`). */
    SPACE,
    /** Tile size rescaled so a whole number fits exactly (`round`). */
    ROUND
}

/**
 * A full two-axis background-repeat value for ONE layer. Unlike the flat
 * [BackgroundRepeatConfig] enum this can express mixed pairs like
 * `space round` (Background_C03) which have no single-keyword equivalent.
 */
data class BackgroundRepeatAxes(
    val x: AxisRepeat = AxisRepeat.REPEAT,
    val y: AxisRepeat = AxisRepeat.REPEAT
) {
    companion object {
        /** Expand a single-keyword [BackgroundRepeatConfig] per §3.7. */
        fun from(config: BackgroundRepeatConfig): BackgroundRepeatAxes = when (config) {
            BackgroundRepeatConfig.REPEAT -> BackgroundRepeatAxes(AxisRepeat.REPEAT, AxisRepeat.REPEAT)
            BackgroundRepeatConfig.REPEAT_X -> BackgroundRepeatAxes(AxisRepeat.REPEAT, AxisRepeat.NO_REPEAT)
            BackgroundRepeatConfig.REPEAT_Y -> BackgroundRepeatAxes(AxisRepeat.NO_REPEAT, AxisRepeat.REPEAT)
            BackgroundRepeatConfig.NO_REPEAT -> BackgroundRepeatAxes(AxisRepeat.NO_REPEAT, AxisRepeat.NO_REPEAT)
            BackgroundRepeatConfig.SPACE -> BackgroundRepeatAxes(AxisRepeat.SPACE, AxisRepeat.SPACE)
            BackgroundRepeatConfig.ROUND -> BackgroundRepeatAxes(AxisRepeat.ROUND, AxisRepeat.ROUND)
        }

        /** Parse one axis keyword (`repeat` / `no-repeat` / `space` / `round`). */
        fun axisOf(keyword: String?): AxisRepeat = when (keyword?.lowercase()?.replace("_", "-")) {
            "no-repeat" -> AxisRepeat.NO_REPEAT
            "space" -> AxisRepeat.SPACE
            "round" -> AxisRepeat.ROUND
            else -> AxisRepeat.REPEAT
        }
    }
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
