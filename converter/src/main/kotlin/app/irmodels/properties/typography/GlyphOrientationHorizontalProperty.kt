package app.irmodels.properties.typography

import app.irmodels.IRAngle
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface GlyphOrientationHorizontalValue {
    @Serializable
    @SerialName("angle")
    data class Angle(val value: IRAngle) : GlyphOrientationHorizontalValue
}

/**
 * Represents the CSS `glyph-orientation-horizontal` property.
 * Controls orientation of glyphs in horizontal text (SVG).
 */
@Serializable
data class GlyphOrientationHorizontalProperty(
    val value: GlyphOrientationHorizontalValue
) : IRProperty {
    override val propertyName = "glyph-orientation-horizontal"
}
