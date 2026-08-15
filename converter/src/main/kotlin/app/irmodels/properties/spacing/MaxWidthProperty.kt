package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MaxWidthProperty(
    val maxWidth: MaxValue
) : IRProperty {
    override val propertyName = "max-width"

    @Serializable
    sealed interface MaxValue {
        @Serializable
        @SerialName("none")
        data class None(val unit: Unit = Unit) : MaxValue

        @Serializable
        @SerialName("length")
        data class LengthValue(val length: IRLength) : MaxValue

        @Serializable
        @SerialName("percentage")
        data class PercentageValue(val percentage: IRPercentage) : MaxValue

        @Serializable
        @SerialName("min-content")
        data class MinContent(val unit: Unit = Unit) : MaxValue

        @Serializable
        @SerialName("max-content")
        data class MaxContent(val unit: Unit = Unit) : MaxValue

        @Serializable
        @SerialName("fit-content")
        data class FitContent(val maxSize: IRLength?) : MaxValue

        // css-values-5 §10.1 calc-size (wave 42 lane W3) — same wire shape as
        // the MinMaxValue/WidthValue twins (see MinWidthProperty.CalcSize for
        // the polymorphic-encoding note). Typed so max-width/max-height
        // declarations reach the web runtime for verbatim browser replay;
        // the native runtimes currently consume it as "no constraint" (their
        // documented pre-existing behavior for unresolvable max bounds).
        @Serializable
        @SerialName("calc-size")
        data class CalcSize(
            val basis: String,    // auto|min-content|max-content|fit-content|stretch|content
            val factor: Double,   // multiplier on the resolved basis size
            val offsetPx: Double, // absolute px addend (may be negative)
            val original: String, // verbatim declaration for web browser replay
        ) : MaxValue
    }
}
