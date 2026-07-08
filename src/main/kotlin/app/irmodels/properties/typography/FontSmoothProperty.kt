package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface FontSmoothValue {
    @Serializable
    @SerialName("auto")
    data object Auto : FontSmoothValue

    @Serializable
    @SerialName("never")
    data object Never : FontSmoothValue

    @Serializable
    @SerialName("always")
    data object Always : FontSmoothValue

    @Serializable
    @SerialName("antialiased")
    data object Antialiased : FontSmoothValue

    @Serializable
    @SerialName("subpixel-antialiased")
    data object SubpixelAntialiased : FontSmoothValue
}

/**
 * Represents the CSS `font-smooth` property.
 * Controls font smoothing/anti-aliasing (non-standard but widely used).
 */
@Serializable
data class FontSmoothProperty(
    val value: FontSmoothValue
) : IRProperty {
    override val propertyName = "font-smooth"
}
