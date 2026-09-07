package app.parsing.css.properties.longhands.layout.grid

import app.irmodels.IRProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.irmodels.properties.layout.grid.*

/**
 * Parser for `grid-row-end` property.
 */
// A6#14: the private `parseGridLine` this file used to carry was one of five
// copies; it now calls the shared GridLineParsing (same package). caseFold =
// true preserves this longhand's historical lowercase step exactly — see the
// case-sensitivity note in GridLineParsing.kt.
object GridRowEndPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val gridLine = GridLineParsing.parse(value, caseFold = true) ?: return null
        return GridRowEndProperty(gridLine)
    }
}
