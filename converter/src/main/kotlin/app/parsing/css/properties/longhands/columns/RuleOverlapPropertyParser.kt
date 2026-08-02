package app.parsing.css.properties.longhands.columns

// IR model + the PropertyParser SAM contract.
import app.irmodels.IRProperty
import app.irmodels.properties.columns.RuleOverlapProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * `rule-overlap` — CSS Gap Decorations Level 1 §7.
 *
 * Two-keyword domain verified against Chromium's computed style:
 * `row-over-column` (initial) | `column-over-row`. Exercised by
 * wpt/css/css-gaps/flex/flex-gap-decorations-012.html.
 */
object RuleOverlapPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        // CSS idents are ASCII case-insensitive.
        val keyword = when (value.trim().lowercase()) {
            "row-over-column" -> RuleOverlapProperty.Overlap.ROW_OVER_COLUMN
            "column-over-row" -> RuleOverlapProperty.Overlap.COLUMN_OVER_ROW
            // No silent fallthrough — unknown idents stay visibly unmapped.
            else -> return null
        }
        return RuleOverlapProperty(keyword)
    }
}
