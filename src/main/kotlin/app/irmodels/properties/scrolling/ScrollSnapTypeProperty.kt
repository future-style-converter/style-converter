package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class ScrollSnapTypeProperty(
    val snapType: SnapType?
) : IRProperty {
    override val propertyName = "scroll-snap-type"

    @Serializable
    data class SnapType(
        val axis: Axis,
        val strictness: Strictness
    )

    enum class Axis {
        X, Y, BLOCK, INLINE, BOTH
    }

    enum class Strictness {
        MANDATORY,
        PROXIMITY
    }
}
