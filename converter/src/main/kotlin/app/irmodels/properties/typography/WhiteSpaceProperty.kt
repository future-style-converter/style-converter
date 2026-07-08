package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class WhiteSpaceProperty(
    val whiteSpace: WhiteSpace
) : IRProperty {
    override val propertyName = "white-space"

    enum class WhiteSpace {
        NORMAL, NOWRAP, PRE, PRE_WRAP, PRE_LINE, BREAK_SPACES
    }
}
