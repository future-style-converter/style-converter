package app.irmodels.properties.paging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BreakValue {
    AUTO, AVOID, ALWAYS, ALL, AVOID_PAGE, PAGE, LEFT, RIGHT, RECTO, VERSO, AVOID_COLUMN, COLUMN, AVOID_REGION, REGION
}

@Serializable
enum class BreakInsideValue {
    AUTO, AVOID, AVOID_PAGE, AVOID_COLUMN, AVOID_REGION
}

@Serializable
sealed interface PageBreakValue {
    @Serializable @SerialName("auto") data object Auto : PageBreakValue
    @Serializable @SerialName("always") data object Always : PageBreakValue
    @Serializable @SerialName("avoid") data object Avoid : PageBreakValue
    @Serializable @SerialName("left") data object Left : PageBreakValue
    @Serializable @SerialName("right") data object Right : PageBreakValue
    @Serializable @SerialName("recto") data object Recto : PageBreakValue
    @Serializable @SerialName("verso") data object Verso : PageBreakValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : PageBreakValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : PageBreakValue
}

@Serializable
sealed interface PageBreakInsideValue {
    @Serializable @SerialName("auto") data object Auto : PageBreakInsideValue
    @Serializable @SerialName("avoid") data object Avoid : PageBreakInsideValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : PageBreakInsideValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : PageBreakInsideValue
}

@Serializable
enum class MarginBreakValue {
    AUTO, KEEP, DISCARD
}
