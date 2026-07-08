package app.parsing.css.properties.longhands.container

import app.irmodels.IRProperty
import app.irmodels.properties.container.ContainerTypeProperty
import app.irmodels.properties.container.ContainerTypeValue
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `container-type` property.
 *
 * Syntax: normal | size | inline-size
 */
object ContainerTypePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val type = when (trimmed) {
            "normal" -> ContainerTypeValue.NORMAL
            "size" -> ContainerTypeValue.SIZE
            "inline-size" -> ContainerTypeValue.INLINE_SIZE
            else -> return null
        }

        return ContainerTypeProperty(type)
    }
}
