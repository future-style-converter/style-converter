package app.irmodels.properties.counters

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class CounterReset(
    val name: String,
    val value: Int = 0
)

/**
 * Represents the CSS `counter-reset` property.
 * Resets one or more CSS counters.
 */
@Serializable
data class CounterResetProperty(
    val counters: List<CounterReset>
) : IRProperty {
    override val propertyName = "counter-reset"
}
