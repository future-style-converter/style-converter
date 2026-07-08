package app.irmodels.properties.effects

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
sealed interface MaskRepeatValue {
    @Serializable
    data object Repeat : MaskRepeatValue
    @Serializable
    data object RepeatX : MaskRepeatValue
    @Serializable
    data object RepeatY : MaskRepeatValue
    @Serializable
    data object NoRepeat : MaskRepeatValue
    @Serializable
    data object Space : MaskRepeatValue
    @Serializable
    data object Round : MaskRepeatValue

    @Serializable
    data class Keyword(val value: String) : MaskRepeatValue

    @Serializable
    data class Raw(val value: String) : MaskRepeatValue
}

@Serializable
data class MaskRepeatProperty(
    val value: MaskRepeatValue
) : IRProperty {
    override val propertyName = "mask-repeat"
}
