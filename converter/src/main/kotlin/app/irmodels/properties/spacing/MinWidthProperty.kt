package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MinWidthProperty(
    val minWidth: MinMaxValue
) : IRProperty {
    override val propertyName = "min-width"

    @Serializable
    sealed interface MinMaxValue {
        @Serializable
        @SerialName("auto")
        data class Auto(val unit: Unit = Unit) : MinMaxValue

        @Serializable
        @SerialName("length")
        data class LengthValue(val length: IRLength) : MinMaxValue

        @Serializable
        @SerialName("percentage")
        data class PercentageValue(val percentage: IRPercentage) : MinMaxValue

        @Serializable
        @SerialName("min-content")
        data class MinContent(val unit: Unit = Unit) : MinMaxValue

        @Serializable
        @SerialName("max-content")
        data class MaxContent(val unit: Unit = Unit) : MinMaxValue

        @Serializable
        @SerialName("fit-content")
        data class FitContent(val maxSize: IRLength?) : MinMaxValue

        // css-values-5 §10.1 calc-size(<keyword basis>, affine `size` expr)
        // (wave 42 lane W3). The kotlinx polymorphic encoding writes the
        // @SerialName as the `type` discriminator and the constructor
        // parameter names as payload keys, so the wire shape is
        // {"type":"calc-size","basis":…,"factor":…,"offsetPx":…,"original":…}
        // — byte-identical to the hand-written WidthValueSerializer branch,
        // one decode path for every runtime. Parse-time-evaluable calls
        // (pure-length basis / size-free expr) emit LengthValue instead.
        @Serializable
        @SerialName("calc-size")
        data class CalcSize(
            val basis: String,    // auto|min-content|max-content|fit-content|stretch|content
            val factor: Double,   // multiplier on the resolved basis size
            val offsetPx: Double, // absolute px addend (may be negative)
            val original: String, // verbatim declaration for web browser replay
        ) : MinMaxValue
    }
}
