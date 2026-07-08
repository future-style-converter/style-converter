package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.RubyOverhangProperty
import app.irmodels.properties.typography.RubyOverhangValue
import app.parsing.css.properties.longhands.PropertyParser

object RubyOverhangPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase()) {
            "auto" -> RubyOverhangValue.AUTO
            "start" -> RubyOverhangValue.START
            "end" -> RubyOverhangValue.END
            "none" -> RubyOverhangValue.NONE
            else -> return null
        }
        return RubyOverhangProperty(v)
    }
}
