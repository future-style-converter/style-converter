package app.irmodels.properties.color

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class BackgroundBlendModeProperty(
    val modes: List<MixBlendModeProperty.BlendMode>
) : IRProperty {
    override val propertyName = "background-blend-mode"

    constructor(mode: MixBlendModeProperty.BlendMode) : this(listOf(mode))
}
