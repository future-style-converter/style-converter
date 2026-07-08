package app.irmodels.properties.typography

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface FontSizeAdjustValue {
    @Serializable
    @SerialName("none")
    data object None : FontSizeAdjustValue

    @Serializable
    @SerialName("number")
    data class Number(val value: IRNumber) : FontSizeAdjustValue

    @Serializable
    @SerialName("from-font")
    data object FromFont : FontSizeAdjustValue

    @Serializable
    @SerialName("metric-value")
    data class MetricValue(val metric: FontMetric, val value: IRNumber) : FontSizeAdjustValue

    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : FontSizeAdjustValue
}

@Serializable
enum class FontMetric {
    @SerialName("ex-height") EX_HEIGHT,
    @SerialName("cap-height") CAP_HEIGHT,
    @SerialName("ch-width") CH_WIDTH,
    @SerialName("ic-width") IC_WIDTH,
    @SerialName("ic-height") IC_HEIGHT
}

/**
 * Represents the CSS `font-size-adjust` property.
 * Adjusts font size to maintain consistent x-height.
 */
@Serializable
data class FontSizeAdjustProperty(
    val value: FontSizeAdjustValue
) : IRProperty {
    override val propertyName = "font-size-adjust"
}
