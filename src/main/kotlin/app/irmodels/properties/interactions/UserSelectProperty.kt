package app.irmodels.properties.interactions

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class UserSelectProperty(
    val value: UserSelect
) : IRProperty {
    override val propertyName = "user-select"

    enum class UserSelect {
        AUTO, TEXT, NONE, CONTAIN, ALL
    }
}
