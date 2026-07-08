package app.parsing.css.properties.longhands.layout.grid

import app.irmodels.IRProperty
import app.irmodels.properties.layout.grid.GridTemplateAreasProperty
import app.parsing.css.properties.longhands.PropertyParser

object GridTemplateAreasPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        if (trimmed == "none") {
            return GridTemplateAreasProperty(GridTemplateAreasProperty.GridTemplateAreas.None())
        }
        val stringRegex = """"([^"]+)"""".toRegex()
        val matches = stringRegex.findAll(value)
        val rows = matches.map { match ->
            match.groupValues[1].trim().split(Regex("\\s+"))
        }.toList()
        if (rows.isEmpty()) {
            return null
        }
        return GridTemplateAreasProperty(GridTemplateAreasProperty.GridTemplateAreas.Areas(rows))
    }
}
