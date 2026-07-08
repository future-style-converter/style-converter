package app.parsing.css.properties.longhands.effects

import app.irmodels.IRProperty
import app.irmodels.properties.effects.ClipPathGeometryBoxProperty
import app.irmodels.properties.effects.ClipPathGeometryBoxValue
import app.parsing.css.properties.longhands.PropertyParser

object ClipPathGeometryBoxPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "fill-box" -> ClipPathGeometryBoxValue.FILL_BOX
            "stroke-box" -> ClipPathGeometryBoxValue.STROKE_BOX
            "view-box" -> ClipPathGeometryBoxValue.VIEW_BOX
            "margin-box" -> ClipPathGeometryBoxValue.MARGIN_BOX
            "border-box" -> ClipPathGeometryBoxValue.BORDER_BOX
            "padding-box" -> ClipPathGeometryBoxValue.PADDING_BOX
            "content-box" -> ClipPathGeometryBoxValue.CONTENT_BOX
            else -> return null
        }
        return ClipPathGeometryBoxProperty(v)
    }
}
