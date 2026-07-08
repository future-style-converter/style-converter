package app.irmodels.properties.print

import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface BleedValue {
    @Serializable
    @SerialName("auto")
    data object Auto : BleedValue

    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : BleedValue
}

/**
 * Represents the CSS `bleed` property.
 * Specifies the extent of bleed area outside page box.
 */
@Serializable
data class BleedProperty(
    val value: BleedValue
) : IRProperty {
    override val propertyName = "bleed"
}
