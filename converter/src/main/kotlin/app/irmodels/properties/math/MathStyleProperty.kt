package app.irmodels.properties.math

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class MathStyleValue {
    NORMAL,
    COMPACT
}

/**
 * Represents the CSS `math-style` property.
 * Specifies whether MathML equations should render in normal or compact style.
 */
@Serializable
data class MathStyleProperty(
    val value: MathStyleValue
) : IRProperty {
    override val propertyName = "math-style"
}
