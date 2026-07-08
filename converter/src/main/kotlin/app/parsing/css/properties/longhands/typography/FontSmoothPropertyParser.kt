package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontSmoothProperty
import app.irmodels.properties.typography.FontSmoothValue
import app.parsing.css.properties.longhands.PropertyParser

object FontSmoothPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase().replace("-", "")) {
            "auto" -> FontSmoothValue.Auto
            "never" -> FontSmoothValue.Never
            "always" -> FontSmoothValue.Always
            "antialiased" -> FontSmoothValue.Antialiased
            "subpixelantialiased" -> FontSmoothValue.SubpixelAntialiased
            else -> return null
        }
        return FontSmoothProperty(v)
    }
}
