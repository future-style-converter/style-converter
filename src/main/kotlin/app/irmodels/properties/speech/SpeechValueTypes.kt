package app.irmodels.properties.speech

import app.irmodels.IRNumber
import app.irmodels.IRPercentage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Shared value types for CSS Speech Module properties (deprecated).
 */

@Serializable
sealed interface VolumeValue {
    @Serializable @SerialName("silent") data object Silent : VolumeValue
    @Serializable @SerialName("x-soft") data object XSoft : VolumeValue
    @Serializable @SerialName("soft") data object Soft : VolumeValue
    @Serializable @SerialName("medium") data object Medium : VolumeValue
    @Serializable @SerialName("loud") data object Loud : VolumeValue
    @Serializable @SerialName("x-loud") data object XLoud : VolumeValue
    @Serializable @SerialName("number") data class Number(val value: IRNumber) : VolumeValue
    @Serializable @SerialName("percentage") data class Percentage(val value: IRPercentage) : VolumeValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : VolumeValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : VolumeValue
}

@Serializable
sealed interface CueValue {
    @Serializable @SerialName("none") data object None : CueValue
    @Serializable @SerialName("url") data class Url(val url: String) : CueValue
}

enum class SpeakValue { NORMAL, NONE, SPELL_OUT }
