package app.irmodels.properties.effects

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
sealed interface MaskCompositeValue {
    @Serializable
    data object Add : MaskCompositeValue
    @Serializable
    data object Subtract : MaskCompositeValue
    @Serializable
    data object Intersect : MaskCompositeValue
    @Serializable
    data object Exclude : MaskCompositeValue
    @Serializable
    data class Keyword(val keyword: String) : MaskCompositeValue
    @Serializable
    data class Raw(val raw: String) : MaskCompositeValue
}

@Serializable
data class MaskCompositeProperty(
    val value: MaskCompositeValue
) : IRProperty {
    override val propertyName = "mask-composite"
}
