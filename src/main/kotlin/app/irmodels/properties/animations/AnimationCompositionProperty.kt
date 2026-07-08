package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface AnimationCompositionValue {
    @Serializable @SerialName("replace") data object Replace : AnimationCompositionValue
    @Serializable @SerialName("add") data object Add : AnimationCompositionValue
    @Serializable @SerialName("accumulate") data object Accumulate : AnimationCompositionValue
    @Serializable @SerialName("list") data class List(val values: kotlin.collections.List<String>) : AnimationCompositionValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : AnimationCompositionValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : AnimationCompositionValue
}

/**
 * Represents the CSS `animation-composition` property.
 * Specifies how animation values combine with underlying values.
 */
@Serializable
data class AnimationCompositionProperty(
    val value: AnimationCompositionValue
) : IRProperty {
    override val propertyName = "animation-composition"
}
