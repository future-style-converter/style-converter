package app.parsing.css.properties.longhands.columns

// IR model + the PropertyParser SAM contract + the shared length primitive.
import app.irmodels.IRProperty
import app.irmodels.properties.columns.RowRuleWidthProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

/**
 * `row-rule-width` — CSS Gap Decorations Level 1 §4.3.
 *
 * Byte-for-byte mirror of ColumnRuleWidthPropertyParser: the three
 * `<line-width>` keywords first, everything else delegated to LengthParser
 * (which normalises absolute units to px and leaves relative units at
 * pixels=null).
 */
object RowRuleWidthPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        // Normalise once — keywords are case-insensitive, units are too.
        val trimmed = value.trim().lowercase()

        val width = when (trimmed) {
            "thin" -> RowRuleWidthProperty.RuleWidth.Keyword(RowRuleWidthProperty.RuleWidthKeyword.THIN)
            "medium" -> RowRuleWidthProperty.RuleWidth.Keyword(RowRuleWidthProperty.RuleWidthKeyword.MEDIUM)
            "thick" -> RowRuleWidthProperty.RuleWidth.Keyword(RowRuleWidthProperty.RuleWidthKeyword.THICK)
            else -> {
                // Not a keyword → must be a <length>; null propagates so an
                // unparseable value stays unmapped instead of defaulting.
                val length = LengthParser.parse(trimmed) ?: return null
                RowRuleWidthProperty.RuleWidth.LengthValue(length)
            }
        }

        return RowRuleWidthProperty(width)
    }
}
