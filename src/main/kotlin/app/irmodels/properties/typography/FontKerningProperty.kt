package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `font-kerning` property.
 *
 * ## CSS Property
 * **Syntax**: `font-kerning: auto | normal | none`
 *
 * ## Description
 * Controls the usage of kerning information stored in a font. Kerning defines how letters
 * are spaced to improve visual appearance.
 *
 * ## Examples
 * ```kotlin
 * FontKerningProperty(kerning = FontKerning.Auto)
 * FontKerningProperty(kerning = FontKerning.Normal)
 * FontKerningProperty(kerning = FontKerning.None)
 * ```
 *
 * ## Platform Support
 * - **CSS**: Full support
 * - **Compose**: Default behavior, limited control
 * - **SwiftUI**: `.kerning()` modifier available
 *
 * @property kerning The font kerning value
 * @see [MDN font-kerning](https://developer.mozilla.org/en-US/docs/Web/CSS/font-kerning)
 */
@Serializable
data class FontKerningProperty(
    val kerning: FontKerning
) : IRProperty {
    override val propertyName = "font-kerning"
}

/**
 * Represents font-kerning values.
 */
@Serializable
enum class FontKerning {
    /**
     * Browser determines whether kerning should be used.
     * May disable kerning on small font sizes for readability.
     */
    AUTO,

    /**
     * Kerning information is applied. Fonts with kerning data will use it.
     */
    NORMAL,

    /**
     * Kerning information is not applied. Letters are positioned using only
     * their default spacing.
     */
    NONE
}
