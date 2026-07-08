package app.irmodels.properties.images

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class ObjectFitProperty(
    val fit: ObjectFit
) : IRProperty {
    override val propertyName = "object-fit"

    enum class ObjectFit {
        FILL,
        CONTAIN,
        COVER,
        NONE,
        SCALE_DOWN
    }
}
