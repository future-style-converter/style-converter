package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.ViewTransitionGroupProperty
import app.irmodels.properties.animations.ViewTransitionGroupProperty.ViewTransitionGroupValue
import app.parsing.css.properties.longhands.PropertyParser

object ViewTransitionGroupPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val v = when (value.trim().lowercase()) {
            "normal" -> ViewTransitionGroupValue.Normal
            "nearest" -> ViewTransitionGroupValue.Nearest
            "contain" -> ViewTransitionGroupValue.Contain
            "root" -> ViewTransitionGroupValue.Root
            else -> ViewTransitionGroupValue.Raw(value.trim())
        }
        return ViewTransitionGroupProperty(v)
    }
}
