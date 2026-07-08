package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface InitialLetterAlignValue {
    @Serializable @SerialName("auto") data object Auto : InitialLetterAlignValue
    @Serializable @SerialName("alphabetic") data object Alphabetic : InitialLetterAlignValue
    @Serializable @SerialName("hanging") data object Hanging : InitialLetterAlignValue
    @Serializable @SerialName("ideographic") data object Ideographic : InitialLetterAlignValue
    @Serializable @SerialName("border-box") data object BorderBox : InitialLetterAlignValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : InitialLetterAlignValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : InitialLetterAlignValue
}

/**
 * Represents the CSS `initial-letter-align` property.
 * Controls alignment of initial letter.
 */
@Serializable
data class InitialLetterAlignProperty(
    val value: InitialLetterAlignValue
) : IRProperty {
    override val propertyName = "initial-letter-align"
}
