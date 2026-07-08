package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class WritingModeProperty(
    val mode: WritingMode
) : IRProperty {
    override val propertyName = "writing-mode"

    enum class WritingMode {
        HORIZONTAL_TB, VERTICAL_RL, VERTICAL_LR,
        SIDEWAYS_RL, SIDEWAYS_LR
    }
}
