package app.irmodels.properties.regions

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class RegionFragmentProperty(val value: RegionFragmentValue) : IRProperty {
    override val propertyName = "region-fragment"
}
