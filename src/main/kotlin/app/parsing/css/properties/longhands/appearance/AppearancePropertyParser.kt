package app.parsing.css.properties.longhands.appearance

import app.irmodels.IRProperty
import app.irmodels.properties.appearance.AppearanceProperty
import app.irmodels.properties.appearance.AppearanceValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object AppearancePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val normalized = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(normalized)) {
            return AppearanceProperty(AppearanceValue.Keyword(normalized))
        }

        // Handle var() and env() expressions
        if (ExpressionDetector.startsWithExpression(normalized)) {
            return AppearanceProperty(AppearanceValue.Raw(trimmed))
        }

        // Handle standard appearance values
        val appearance: AppearanceValue = when (normalized) {
            "none" -> AppearanceValue.None
            "auto" -> AppearanceValue.Auto
            "button" -> AppearanceValue.Button
            "checkbox" -> AppearanceValue.Checkbox
            "listbox" -> AppearanceValue.Listbox
            "menulist" -> AppearanceValue.Menulist
            "menulist-button" -> AppearanceValue.MenulistButton
            "meter" -> AppearanceValue.Meter
            "progress-bar" -> AppearanceValue.ProgressBar
            "push-button" -> AppearanceValue.PushButton
            "radio" -> AppearanceValue.Radio
            "searchfield" -> AppearanceValue.Searchfield
            "slider-horizontal" -> AppearanceValue.SliderHorizontal
            "square-button" -> AppearanceValue.SquareButton
            "textarea" -> AppearanceValue.Textarea
            "textfield" -> AppearanceValue.Textfield
            else -> AppearanceValue.Raw(trimmed)
        }
        return AppearanceProperty(appearance)
    }
}
