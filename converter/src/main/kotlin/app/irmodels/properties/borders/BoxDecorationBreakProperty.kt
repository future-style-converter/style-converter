package app.irmodels.properties.borders

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class BoxDecorationBreak {
    SLICE,  // Default - decorations are applied as if element were one piece
    CLONE   // Each fragment is rendered independently
}

@Serializable
data class BoxDecorationBreakProperty(
    val value: BoxDecorationBreak
) : IRProperty {
    override val propertyName = "box-decoration-break"
}
