package com.styleconverter.test.style.core.media

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for a parsed CSS media query condition.
 *
 * CSS media queries are evaluated at runtime based on device characteristics
 * like screen width, height, orientation, and color scheme.
 *
 * ## Supported Features
 * - Width constraints: min-width, max-width, width
 * - Height constraints: min-height, max-height, height
 * - Orientation: portrait, landscape
 * - Prefers-color-scheme: light, dark
 * - Aspect ratio comparisons
 *
 * ## Example
 * ```kotlin
 * val config = MediaQueryConfig(
 *     minWidth = 768.dp,
 *     maxWidth = 1024.dp,
 *     orientation = Orientation.PORTRAIT
 * )
 * ```
 */
data class MediaQueryConfig(
    /** Minimum screen width (inclusive) */
    val minWidth: Dp? = null,
    /** Maximum screen width (inclusive) */
    val maxWidth: Dp? = null,
    /** Exact screen width */
    val width: Dp? = null,
    /** Minimum screen height (inclusive) */
    val minHeight: Dp? = null,
    /** Maximum screen height (inclusive) */
    val maxHeight: Dp? = null,
    /** Exact screen height */
    val height: Dp? = null,
    /** Device orientation constraint */
    val orientation: Orientation? = null,
    /** Color scheme preference */
    val colorScheme: ColorScheme? = null,
    /** Minimum aspect ratio (width/height) */
    val minAspectRatio: Float? = null,
    /** Maximum aspect ratio (width/height) */
    val maxAspectRatio: Float? = null,
    /** Display mode (standalone, fullscreen, etc.) */
    val displayMode: DisplayMode? = null,
    /** Reduced motion preference */
    val prefersReducedMotion: ReducedMotion? = null,
    /** Contrast preference */
    val prefersContrast: ContrastPreference? = null,
    /** Hover capability */
    val hoverCapability: HoverCapability? = null,
    /** Pointer type (coarse for touch, fine for mouse) */
    val pointerType: PointerType? = null,
    /** Whether this is a negated query (NOT condition) */
    val negate: Boolean = false,
    /** Logical operator for combining with previous condition */
    val operator: LogicalOperator = LogicalOperator.AND
) {
    companion object {
        val EMPTY = MediaQueryConfig()
    }
}

/**
 * Device orientation for media queries.
 */
enum class Orientation {
    PORTRAIT,
    LANDSCAPE
}

/**
 * Preferred color scheme for media queries.
 */
enum class ColorScheme {
    LIGHT,
    DARK
}

/**
 * Display mode for media queries.
 */
enum class DisplayMode {
    BROWSER,
    STANDALONE,
    FULLSCREEN,
    MINIMAL_UI
}

/**
 * Logical operator for combining media query conditions.
 */
enum class LogicalOperator {
    AND,
    OR,
    NOT
}

/**
 * Reduced motion preference for media queries.
 * Maps to CSS `prefers-reduced-motion`.
 */
enum class ReducedMotion {
    /** User has not requested reduced motion */
    NO_PREFERENCE,
    /** User has requested reduced motion */
    REDUCE
}

/**
 * Contrast preference for media queries.
 * Maps to CSS `prefers-contrast`.
 */
enum class ContrastPreference {
    /** No preference */
    NO_PREFERENCE,
    /** User prefers more contrast */
    MORE,
    /** User prefers less contrast */
    LESS,
    /** User prefers custom contrast */
    CUSTOM
}

/**
 * Hover capability for media queries.
 * Maps to CSS `hover`.
 */
enum class HoverCapability {
    /** Device has no hover capability (touch) */
    NONE,
    /** Device has hover capability (mouse) */
    HOVER
}

/**
 * Pointer type for media queries.
 * Maps to CSS `pointer`.
 */
enum class PointerType {
    /** No pointing device */
    NONE,
    /** Coarse pointing device (touch) */
    COARSE,
    /** Fine pointing device (mouse) */
    FINE
}

/**
 * Represents the current device/screen characteristics for media query evaluation.
 */
data class ScreenInfo(
    val width: Dp,
    val height: Dp,
    val isDarkTheme: Boolean = false,
    val isStandalone: Boolean = false,
    /** Whether reduced motion is enabled in system settings */
    val reducedMotionEnabled: Boolean = false,
    /** Whether high contrast is enabled in system settings */
    val highContrastEnabled: Boolean = false,
    /** Whether the device supports hover (has mouse/trackpad) */
    val hasHoverCapability: Boolean = false,
    /** Whether the device has touch input */
    val hasTouchInput: Boolean = true
) {
    val orientation: Orientation
        get() = if (width > height) Orientation.LANDSCAPE else Orientation.PORTRAIT

    val aspectRatio: Float
        get() = width.value / height.value

    val reducedMotion: ReducedMotion
        get() = if (reducedMotionEnabled) ReducedMotion.REDUCE else ReducedMotion.NO_PREFERENCE

    val contrastPreference: ContrastPreference
        get() = if (highContrastEnabled) ContrastPreference.MORE else ContrastPreference.NO_PREFERENCE

    val hoverCapability: HoverCapability
        get() = if (hasHoverCapability) HoverCapability.HOVER else HoverCapability.NONE

    val pointerType: PointerType
        get() = when {
            !hasTouchInput && !hasHoverCapability -> PointerType.NONE
            hasHoverCapability -> PointerType.FINE
            else -> PointerType.COARSE
        }

    companion object {
        val DEFAULT = ScreenInfo(360.dp, 640.dp)
    }
}

/**
 * Common breakpoint presets matching CSS frameworks.
 */
object Breakpoints {
    /** Extra small devices (phones, <576px) */
    val XS = 0.dp
    /** Small devices (landscape phones, ≥576px) */
    val SM = 576.dp
    /** Medium devices (tablets, ≥768px) */
    val MD = 768.dp
    /** Large devices (desktops, ≥992px) */
    val LG = 992.dp
    /** Extra large devices (large desktops, ≥1200px) */
    val XL = 1200.dp
    /** Extra extra large devices (≥1400px) */
    val XXL = 1400.dp
}
