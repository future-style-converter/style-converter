package app.irmodels.properties.color

import app.irmodels.*
import kotlinx.serialization.Serializable

/** Filter functions for CSS filter property */
@Serializable(with = FilterFunctionSerializer::class)
sealed interface FilterFunction {
    @Serializable data class Blur(val radius: IRLength) : FilterFunction
    @Serializable data class Brightness(val amount: IRPercentage) : FilterFunction
    @Serializable data class Contrast(val amount: IRPercentage) : FilterFunction
    @Serializable data class Grayscale(val amount: IRPercentage) : FilterFunction
    @Serializable data class HueRotate(val angle: IRAngle) : FilterFunction
    @Serializable data class Invert(val amount: IRPercentage) : FilterFunction
    @Serializable data class Saturate(val amount: IRPercentage) : FilterFunction
    @Serializable data class Sepia(val amount: IRPercentage) : FilterFunction
    @Serializable data class Opacity(val amount: IRPercentage) : FilterFunction
    @Serializable data class DropShadow(
        val offsetX: IRLength,
        val offsetY: IRLength,
        val blurRadius: IRLength?,
        val color: IRColor?
    ) : FilterFunction

    companion object {
        /** Blur filter (radius in pixels) */
        fun blur(radiusPx: Double) = Blur(IRLength.fromPx(radiusPx))

        /** Brightness filter (percentage, 100 = normal) */
        fun brightness(percent: Double) = Brightness(IRPercentage(percent))

        /** Contrast filter (percentage, 100 = normal) */
        fun contrast(percent: Double) = Contrast(IRPercentage(percent))

        /** Grayscale filter (percentage, 0 = none, 100 = full) */
        fun grayscale(percent: Double) = Grayscale(IRPercentage(percent))

        /** Hue-rotate filter (degrees) */
        fun hueRotate(degrees: Double) = HueRotate(IRAngle.fromDegrees(degrees))

        /** Invert filter (percentage, 0 = none, 100 = full) */
        fun invert(percent: Double) = Invert(IRPercentage(percent))

        /** Saturate filter (percentage, 100 = normal) */
        fun saturate(percent: Double) = Saturate(IRPercentage(percent))

        /** Sepia filter (percentage, 0 = none, 100 = full) */
        fun sepia(percent: Double) = Sepia(IRPercentage(percent))

        /** Opacity filter (percentage, 100 = full opacity) */
        fun opacity(percent: Double) = Opacity(IRPercentage(percent))

        /** Drop shadow filter */
        fun dropShadow(
            offsetXPx: Double,
            offsetYPx: Double,
            blurRadiusPx: Double? = null,
            color: IRColor? = null
        ) = DropShadow(
            IRLength.fromPx(offsetXPx),
            IRLength.fromPx(offsetYPx),
            blurRadiusPx?.let { IRLength.fromPx(it) },
            color
        )

        // Common presets
        fun fullGrayscale() = grayscale(100.0)
        fun fullInvert() = invert(100.0)
        fun fullSepia() = sepia(100.0)
        fun noFilter() = brightness(100.0)
    }
}
