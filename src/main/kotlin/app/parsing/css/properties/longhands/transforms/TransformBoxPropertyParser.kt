package app.parsing.css.properties.longhands.transforms

import app.irmodels.IRProperty
import app.irmodels.properties.transforms.TransformBoxProperty
import app.irmodels.properties.transforms.TransformBoxProperty.TransformBox
import app.parsing.css.properties.longhands.PropertyParser

object TransformBoxPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val box = when (trimmed) {
            "content-box" -> TransformBox.CONTENT_BOX
            "border-box" -> TransformBox.BORDER_BOX
            "fill-box" -> TransformBox.FILL_BOX
            "stroke-box" -> TransformBox.STROKE_BOX
            "view-box" -> TransformBox.VIEW_BOX
            else -> return null
        }
        return TransformBoxProperty(box)
    }
}
