package com.styleconverter.test.style.performance

/**
 * Configuration for CSS performance-related properties.
 *
 * ## Supported Properties
 * - contain: layout, paint, size, content, strict, none
 * - will-change: properties that will animate
 * - zoom: scaling factor
 * - content-visibility: rendering optimization
 *
 * ## Compose Mapping
 * These properties are hints for rendering optimization.
 * - contain: Can use LazyColumn, CompositingStrategy
 * - will-change: Pre-allocate graphics layers
 * - zoom: Scale modifier
 */
data class PerformanceConfig(
    val contain: ContainConfig = ContainConfig(),
    val willChange: WillChangeConfig = WillChangeConfig(),
    val zoom: ZoomConfig = ZoomConfig.Default
) {
    val hasPerformanceConfig: Boolean
        get() = contain.hasContainment || willChange.hasWillChange || !zoom.isNormal
}

/**
 * CSS contain property configuration.
 * Provides layout containment hints for performance.
 */
data class ContainConfig(
    val layout: Boolean = false,
    val paint: Boolean = false,
    val size: Boolean = false,
    val style: Boolean = false,
    val inlineSize: Boolean = false,
    val blockSize: Boolean = false
) {
    val hasContainment: Boolean
        get() = layout || paint || size || style || inlineSize || blockSize

    /** CSS "content" = layout + paint + style */
    val isContent: Boolean
        get() = layout && paint && style && !size

    /** CSS "strict" = size + layout + paint + style */
    val isStrict: Boolean
        get() = layout && paint && style && size

    companion object {
        val None = ContainConfig()
        val Layout = ContainConfig(layout = true)
        val Paint = ContainConfig(paint = true)
        val Size = ContainConfig(size = true)
        val Content = ContainConfig(layout = true, paint = true, style = true)
        val Strict = ContainConfig(layout = true, paint = true, style = true, size = true)
    }
}

/**
 * CSS will-change property configuration.
 * Hints browser about upcoming changes for optimization.
 */
data class WillChangeConfig(
    val properties: List<WillChangeValue> = emptyList(),
    val isAuto: Boolean = true
) {
    val hasWillChange: Boolean
        get() = !isAuto && properties.isNotEmpty()

    val willTransform: Boolean
        get() = properties.any { it == WillChangeValue.TRANSFORM }

    val willChangeOpacity: Boolean
        get() = properties.any { it == WillChangeValue.OPACITY }

    val willScroll: Boolean
        get() = properties.any { it == WillChangeValue.SCROLL_POSITION }

    companion object {
        val Auto = WillChangeConfig(isAuto = true)
    }
}

/**
 * CSS will-change values.
 */
enum class WillChangeValue {
    AUTO,
    SCROLL_POSITION,
    CONTENTS,
    TRANSFORM,
    OPACITY,
    TOP,
    LEFT,
    BOTTOM,
    RIGHT,
    WIDTH,
    HEIGHT,
    BACKGROUND,
    FILTER,
    CUSTOM
}

/**
 * CSS zoom property configuration.
 */
data class ZoomConfig(
    val factor: Float = 1f,
    val type: ZoomValue = ZoomValue.NORMAL
) {
    val isNormal: Boolean
        get() = type == ZoomValue.NORMAL || factor in 0.99f..1.01f

    val isZoomedIn: Boolean
        get() = factor > 1.01f

    val isZoomedOut: Boolean
        get() = factor < 0.99f

    companion object {
        val Default = ZoomConfig(1f, ZoomValue.NORMAL)

        fun fromFactor(factor: Float): ZoomConfig {
            return if (factor in 0.99f..1.01f) {
                ZoomConfig(1f, ZoomValue.NORMAL)
            } else {
                ZoomConfig(factor, ZoomValue.CUSTOM)
            }
        }

        fun fromPercentage(percentage: Float): ZoomConfig {
            return fromFactor(percentage / 100f)
        }
    }
}

enum class ZoomValue {
    NORMAL,
    CUSTOM
}

/**
 * CSS box-sizing property values.
 */
enum class BoxSizingValue {
    /** Width/height includes only content */
    CONTENT_BOX,
    /** Width/height includes padding and border */
    BORDER_BOX
}

/**
 * CSS box-decoration-break property values.
 */
enum class BoxDecorationBreakValue {
    /** Decorations slice across fragments */
    SLICE,
    /** Each fragment has full decorations */
    CLONE
}

/**
 * CSS image-rendering property values.
 */
enum class ImageRenderingValue {
    AUTO,
    SMOOTH,
    HIGH_QUALITY,
    CRISP_EDGES,
    PIXELATED
}

/**
 * Configuration for box model properties.
 */
data class BoxModelConfig(
    val boxSizing: BoxSizingValue = BoxSizingValue.CONTENT_BOX,
    val boxDecorationBreak: BoxDecorationBreakValue = BoxDecorationBreakValue.SLICE,
    val imageRendering: ImageRenderingValue = ImageRenderingValue.AUTO
) {
    val hasBoxModelConfig: Boolean
        get() = boxSizing != BoxSizingValue.CONTENT_BOX ||
                boxDecorationBreak != BoxDecorationBreakValue.SLICE ||
                imageRendering != ImageRenderingValue.AUTO

    val isBorderBox: Boolean
        get() = boxSizing == BoxSizingValue.BORDER_BOX
}

/**
 * CSS resize property values.
 */
enum class ResizeValue {
    NONE,
    BOTH,
    HORIZONTAL,
    VERTICAL,
    BLOCK,
    INLINE
}
