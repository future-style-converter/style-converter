package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface FontPaletteValue {
    @Serializable
    @SerialName("normal")
    data object Normal : FontPaletteValue

    @Serializable
    @SerialName("light")
    data object Light : FontPaletteValue

    @Serializable
    @SerialName("dark")
    data object Dark : FontPaletteValue

    @Serializable
    @SerialName("custom")
    data class Custom(val name: String) : FontPaletteValue
}

/**
 * Represents the CSS `font-palette` property.
 * Specifies color palette for color fonts.
 */
@Serializable
data class FontPaletteProperty(
    val value: FontPaletteValue
) : IRProperty {
    override val propertyName = "font-palette"
}
