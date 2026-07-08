package app.irmodels.properties.typography

import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface KerningValue {
    @Serializable
    @SerialName("auto")
    data object Auto : KerningValue

    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : KerningValue
}

/**
 * Represents the CSS `kerning` property (SVG).
 * Controls spacing between glyphs.
 */
@Serializable
data class KerningProperty(
    val value: KerningValue
) : IRProperty {
    override val propertyName = "kerning"
}
