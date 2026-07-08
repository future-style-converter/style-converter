package app.parsing.css.properties.longhands.appearance

import app.irmodels.IRProperty
import app.irmodels.properties.appearance.AppearanceVariantProperty
import app.irmodels.properties.appearance.AppearanceVariantValue
import app.parsing.css.properties.longhands.PropertyParser

object AppearanceVariantPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "normal" -> AppearanceVariantValue.NORMAL
            "button" -> AppearanceVariantValue.BUTTON
            "checkbox" -> AppearanceVariantValue.CHECKBOX
            "listbox" -> AppearanceVariantValue.LISTBOX
            "menulist" -> AppearanceVariantValue.MENULIST
            "meter" -> AppearanceVariantValue.METER
            "progress-bar" -> AppearanceVariantValue.PROGRESS_BAR
            "push-button" -> AppearanceVariantValue.PUSH_BUTTON
            "radio" -> AppearanceVariantValue.RADIO
            "searchfield" -> AppearanceVariantValue.SEARCHFIELD
            "slider-horizontal" -> AppearanceVariantValue.SLIDER_HORIZONTAL
            "square-button" -> AppearanceVariantValue.SQUARE_BUTTON
            "textarea" -> AppearanceVariantValue.TEXTAREA
            "textfield" -> AppearanceVariantValue.TEXTFIELD
            else -> return null
        }
        return AppearanceVariantProperty(v)
    }
}
