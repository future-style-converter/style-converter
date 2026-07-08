package app.parsing.css.properties.longhands.layout.grid

import app.irmodels.IRProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.irmodels.properties.layout.grid.*

/**
 * Parser for `grid-auto-columns` property.
 */
object GridAutoColumnsPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val tracks = GridTrackListParser.parseTrackList(value) ?: return null
        return GridAutoColumnsProperty(tracks)
    }
}
