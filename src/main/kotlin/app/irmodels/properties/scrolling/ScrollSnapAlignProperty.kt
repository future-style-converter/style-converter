package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class ScrollSnapAlignProperty(
    val block: Alignment,
    val inline: Alignment?
) : IRProperty {
    override val propertyName = "scroll-snap-align"

    enum class Alignment {
        NONE,
        START,
        END,
        CENTER
    }
}
