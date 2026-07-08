package app.irmodels.properties.experimental

import app.irmodels.IRNumber
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PresentationLevelValue {
    @Serializable @SerialName("same") data object Same : PresentationLevelValue
    @Serializable @SerialName("increment") data class Increment(val value: IRNumber) : PresentationLevelValue
}

@Serializable
sealed interface RunningValue {
    @Serializable @SerialName("named") data class Named(val name: String) : RunningValue
}
