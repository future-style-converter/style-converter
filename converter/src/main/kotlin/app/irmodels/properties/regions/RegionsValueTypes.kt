package app.irmodels.properties.regions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface FlowValue {
    @Serializable @SerialName("none") data object None : FlowValue
    @Serializable @SerialName("named") data class Named(val name: String) : FlowValue
}

@Serializable
enum class RegionFragmentValue { AUTO, BREAK }

@Serializable
enum class ContinueValue { AUTO, DISCARD, OVERFLOW }

@Serializable
enum class WrapFlowValue { AUTO, BOTH, START, END, MAXIMUM, CLEAR }

@Serializable
enum class WrapThroughValue { WRAP, NONE }

@Serializable
enum class WrapBreakValue { AUTO, AVOID, AVOID_PAGE, AVOID_COLUMN, AVOID_REGION }
