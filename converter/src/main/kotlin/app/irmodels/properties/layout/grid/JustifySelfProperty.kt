package app.irmodels.properties.layout.grid

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface JustifySelfValue {
    @Serializable @SerialName("auto") data object Auto : JustifySelfValue
    @Serializable @SerialName("normal") data object Normal : JustifySelfValue
    @Serializable @SerialName("stretch") data object Stretch : JustifySelfValue
    @Serializable @SerialName("center") data object Center : JustifySelfValue
    @Serializable @SerialName("start") data object Start : JustifySelfValue
    @Serializable @SerialName("end") data object End : JustifySelfValue
    @Serializable @SerialName("flex-start") data object FlexStart : JustifySelfValue
    @Serializable @SerialName("flex-end") data object FlexEnd : JustifySelfValue
    @Serializable @SerialName("self-start") data object SelfStart : JustifySelfValue
    @Serializable @SerialName("self-end") data object SelfEnd : JustifySelfValue
    @Serializable @SerialName("left") data object Left : JustifySelfValue
    @Serializable @SerialName("right") data object Right : JustifySelfValue
    @Serializable @SerialName("baseline") data object Baseline : JustifySelfValue
    @Serializable @SerialName("anchor-center") data object AnchorCenter : JustifySelfValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : JustifySelfValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : JustifySelfValue
}

@Serializable
data class JustifySelfProperty(
    val value: JustifySelfValue
) : IRProperty {
    override val propertyName = "justify-self"
}
