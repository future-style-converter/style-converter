package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class UnicodeBidiProperty(
    val bidi: UnicodeBidi
) : IRProperty {
    override val propertyName = "unicode-bidi"

    enum class UnicodeBidi {
        NORMAL,
        EMBED,
        ISOLATE,
        BIDI_OVERRIDE,
        ISOLATE_OVERRIDE,
        PLAINTEXT
    }
}
