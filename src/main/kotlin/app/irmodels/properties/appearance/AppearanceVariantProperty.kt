package app.irmodels.properties.appearance

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class AppearanceVariantValue {
    NORMAL,
    BUTTON,
    CHECKBOX,
    LISTBOX,
    MENULIST,
    METER,
    PROGRESS_BAR,
    PUSH_BUTTON,
    RADIO,
    SEARCHFIELD,
    SLIDER_HORIZONTAL,
    SQUARE_BUTTON,
    TEXTAREA,
    TEXTFIELD
}

/**
 * Represents the CSS `appearance-variant` property.
 * Specifies variant styling for native UI controls.
 */
@Serializable
data class AppearanceVariantProperty(
    val value: AppearanceVariantValue
) : IRProperty {
    override val propertyName = "appearance-variant"
}
