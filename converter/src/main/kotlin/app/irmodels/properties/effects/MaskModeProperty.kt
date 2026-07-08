package app.irmodels.properties.effects

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
sealed interface MaskModeValue {
    @Serializable
    data object Alpha : MaskModeValue

    @Serializable
    data object Luminance : MaskModeValue

    @Serializable
    data object MatchSource : MaskModeValue

    @Serializable
    data class Keyword(val value: String) : MaskModeValue

    @Serializable
    data class Raw(val value: String) : MaskModeValue
}

@Serializable
data class MaskModeProperty(
    val value: MaskModeValue
) : IRProperty {
    override val propertyName = "mask-mode"
}
