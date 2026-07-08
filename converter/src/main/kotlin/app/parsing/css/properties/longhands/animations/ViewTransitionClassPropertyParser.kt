package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.ViewTransitionClassProperty
import app.irmodels.properties.animations.ViewTransitionClassValue
import app.parsing.css.properties.longhands.PropertyParser

object ViewTransitionClassPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val v = when (trimmed.lowercase()) {
            "none" -> ViewTransitionClassValue.None
            else -> {
                val classes = trimmed.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                if (classes.isEmpty()) return null
                ViewTransitionClassValue.Classes(classes)
            }
        }
        return ViewTransitionClassProperty(v)
    }
}
