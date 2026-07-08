package app.irmodels.properties.svg

import app.irmodels.IRUrl
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MarkerValue {
    @Serializable @SerialName("none") data object None : MarkerValue
    @Serializable @SerialName("url") data class Url(val url: IRUrl) : MarkerValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : MarkerValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : MarkerValue
}

@Serializable
data class MarkerProperty(
    val value: MarkerValue
) : IRProperty {
    override val propertyName = "marker"
}
