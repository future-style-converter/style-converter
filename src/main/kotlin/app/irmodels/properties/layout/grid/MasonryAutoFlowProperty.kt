package app.irmodels.properties.layout.grid

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MasonryAutoFlowValue {
    // Single placement values
    @Serializable @SerialName("pack") data object Pack : MasonryAutoFlowValue
    @Serializable @SerialName("next") data object Next : MasonryAutoFlowValue

    // Single ordering values (when used with default placement)
    @Serializable @SerialName("ordered") data object Ordered : MasonryAutoFlowValue
    @Serializable @SerialName("definite-first") data object DefiniteFirst : MasonryAutoFlowValue

    // Combined values (placement + ordering)
    @Serializable @SerialName("pack-definite-first") data object PackDefiniteFirst : MasonryAutoFlowValue
    @Serializable @SerialName("pack-ordered") data object PackOrdered : MasonryAutoFlowValue
    @Serializable @SerialName("next-definite-first") data object NextDefiniteFirst : MasonryAutoFlowValue
    @Serializable @SerialName("next-ordered") data object NextOrdered : MasonryAutoFlowValue

    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : MasonryAutoFlowValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : MasonryAutoFlowValue
}

/**
 * Represents the CSS `masonry-auto-flow` property.
 * Controls how items flow in a masonry layout.
 */
@Serializable
data class MasonryAutoFlowProperty(
    val value: MasonryAutoFlowValue
) : IRProperty {
    override val propertyName = "masonry-auto-flow"
}
