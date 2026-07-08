package app.irmodels.properties.counters

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class CounterIncrement(
    val name: String,
    val value: Int = 1
)

/**
 * Represents the CSS `counter-increment` property.
 * Increments one or more CSS counters.
 */
@Serializable
data class CounterIncrementProperty(
    val counters: List<CounterIncrement>
) : IRProperty {
    override val propertyName = "counter-increment"
}
