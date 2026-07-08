package app.parsing.css.properties.longhands.images

import app.irmodels.IRProperty
import app.irmodels.properties.images.ObjectFitProperty
import app.parsing.css.properties.longhands.PropertyParser

object ObjectFitPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val fit = when (value.trim().lowercase()) {
            "fill" -> ObjectFitProperty.ObjectFit.FILL
            "contain" -> ObjectFitProperty.ObjectFit.CONTAIN
            "cover" -> ObjectFitProperty.ObjectFit.COVER
            "none" -> ObjectFitProperty.ObjectFit.NONE
            "scale-down" -> ObjectFitProperty.ObjectFit.SCALE_DOWN
            else -> return null
        }
        return ObjectFitProperty(fit)
    }
}
