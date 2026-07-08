package app.irmodels.properties.layout.flexbox

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class FlexWrapProperty(
    val wrap: FlexWrap
) : IRProperty {
    override val propertyName = "flex-wrap"

    enum class FlexWrap {
        NOWRAP, WRAP, WRAP_REVERSE
    }
}
