package app.irmodels.properties.counters

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class CounterSet(
    val name: String,
    val value: Int
)

/**
 * Represents the CSS `counter-set` property.
 * Sets one or more CSS counters to a specific value.
 */
@Serializable
data class CounterSetProperty(
    val counters: List<CounterSet>
) : IRProperty {
    override val propertyName = "counter-set"
}
