package app.irmodels.properties.typography

import app.irmodels.IRAngle
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface GlyphOrientationVerticalValue {
    @Serializable
    @SerialName("auto")
    data object Auto : GlyphOrientationVerticalValue

    @Serializable
    @SerialName("angle")
    data class Angle(val value: IRAngle) : GlyphOrientationVerticalValue
}

/**
 * Represents the CSS `glyph-orientation-vertical` property.
 * Controls orientation of glyphs in vertical text (SVG).
 */
@Serializable
data class GlyphOrientationVerticalProperty(
    val value: GlyphOrientationVerticalValue
) : IRProperty {
    override val propertyName = "glyph-orientation-vertical"
}
