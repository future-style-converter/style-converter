package app.parsing.css.properties.longhands.columns

// IR model + the PropertyParser SAM contract + the shared length primitive.
import app.irmodels.IRProperty
import app.irmodels.properties.columns.RowRuleInsetProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

/**
 * `row-rule-inset` — CSS Gap Decorations Level 1 §6.
 *
 * Row-axis twin of ColumnRuleInsetPropertyParser: `<length>` incl. negatives
 * (`-25%`, `-210px` appear in the WPT flex corpus), initial `0px`, and the
 * same "`overlap-join` and other idents stay unmapped" contract.
 */
object RowRuleInsetPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        // One shared length primitive → identical normalisation everywhere.
        val length = LengthParser.parse(value.trim()) ?: return null
        return RowRuleInsetProperty(length)
    }
}
