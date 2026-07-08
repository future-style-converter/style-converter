package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.RubyMergeProperty
import app.irmodels.properties.typography.RubyMergeValue
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `ruby-merge` property.
 *
 * Values: separate | collapse | auto
 * Note: IR model uses COLLAPSE instead of 'merge' from CSS spec
 */
object RubyMergePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val merge = when (trimmed) {
            "separate" -> RubyMergeValue.SEPARATE
            "collapse", "merge" -> RubyMergeValue.COLLAPSE
            "auto" -> RubyMergeValue.AUTO
            else -> return null
        }

        return RubyMergeProperty(merge)
    }
}
