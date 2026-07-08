package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface FontLanguageOverrideValue {
    @Serializable
    @SerialName("normal")
    data object Normal : FontLanguageOverrideValue

    @Serializable
    @SerialName("language-tag")
    data class LanguageTag(val tag: String) : FontLanguageOverrideValue
}

/**
 * Represents the CSS `font-language-override` property.
 * Controls language-specific glyph substitutions.
 */
@Serializable
data class FontLanguageOverrideProperty(
    val value: FontLanguageOverrideValue
) : IRProperty {
    override val propertyName = "font-language-override"
}
