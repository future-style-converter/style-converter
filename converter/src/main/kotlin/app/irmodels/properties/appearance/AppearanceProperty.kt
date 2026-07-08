package app.irmodels.properties.appearance

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppearanceValue {
    @Serializable @SerialName("none") data object None : AppearanceValue
    @Serializable @SerialName("auto") data object Auto : AppearanceValue
    @Serializable @SerialName("button") data object Button : AppearanceValue
    @Serializable @SerialName("checkbox") data object Checkbox : AppearanceValue
    @Serializable @SerialName("listbox") data object Listbox : AppearanceValue
    @Serializable @SerialName("menulist") data object Menulist : AppearanceValue
    @Serializable @SerialName("menulist-button") data object MenulistButton : AppearanceValue
    @Serializable @SerialName("meter") data object Meter : AppearanceValue
    @Serializable @SerialName("progress-bar") data object ProgressBar : AppearanceValue
    @Serializable @SerialName("push-button") data object PushButton : AppearanceValue
    @Serializable @SerialName("radio") data object Radio : AppearanceValue
    @Serializable @SerialName("searchfield") data object Searchfield : AppearanceValue
    @Serializable @SerialName("slider-horizontal") data object SliderHorizontal : AppearanceValue
    @Serializable @SerialName("square-button") data object SquareButton : AppearanceValue
    @Serializable @SerialName("textarea") data object Textarea : AppearanceValue
    @Serializable @SerialName("textfield") data object Textfield : AppearanceValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : AppearanceValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : AppearanceValue
}

/**
 * Represents the CSS `appearance` property.
 * Controls native widget styling.
 */
@Serializable
data class AppearanceProperty(
    val value: AppearanceValue
) : IRProperty {
    override val propertyName = "appearance"
}
