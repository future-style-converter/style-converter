package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class TextTransformProperty(
    val transform: TextTransform
) : IRProperty {
    override val propertyName = "text-transform"

    enum class TextTransform {
        NONE, UPPERCASE, LOWERCASE, CAPITALIZE,
        FULL_WIDTH, FULL_SIZE_KANA
    }
}
