package app.parsing.css.mediaQueries

import app.irmodels.IRMedia
import app.irmodels.IRProperty
import app.parsing.css.CssMedia
import app.parsing.css.properties.PropertiesParser

/**
 * Media query parser for CSS @media rules.
 *
 * Converts CssMedia objects (raw query + properties) into IRMedia objects
 * with fully parsed property types.
 *
 * ## Example
 * ```
 * Input:  CssMedia(query = "(min-width: 768px)", properties = {"padding": "20px"})
 * Output: IRMedia(query = "(min-width: 768px)", properties = [PaddingProperty(...)])
 * ```
 *
 * @param media List of raw media query definitions
 * @return List of IRMedia with parsed properties
 * @see PropertiesParser for property parsing logic
 */
fun parseMedia(media: List<CssMedia>?): List<IRMedia> {
	if (media == null) return emptyList()
    return media.map { m ->
        // Parse directly to specific properties
        val properties = PropertiesParser.parse(m.properties)
        IRMedia(query = m.query, properties = properties)
    }
}


