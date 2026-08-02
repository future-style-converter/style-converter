package app.parsing.css.properties.longhands.columns

// IR model + the PropertyParser SAM contract + the shared length primitive.
import app.irmodels.IRProperty
import app.irmodels.properties.columns.ColumnRuleInsetProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

/**
 * `column-rule-inset` — CSS Gap Decorations Level 1 §6.
 *
 * Accepts a `<length>`, INCLUDING negatives (the spec allows extending a
 * decoration past the gap ends; the WPT flex corpus uses `-2px`, `-100%`).
 * LengthParser's numeric group is `[+-]?\d*\.?\d+`, so negatives and the
 * unitless `0` both parse without extra handling here.
 *
 * The draft also defines the `overlap-join` ident; it is deliberately NOT
 * mapped in this wave (the wave-24 wire contract pins this leaf to length
 * objects only), so it returns null and the declaration stays visibly
 * unmapped as a GenericProperty rather than silently becoming 0px.
 */
object ColumnRuleInsetPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        // Delegate to the one shared length primitive so unit normalisation
        // (pt/cm/in → px, %/em → pixels=null) matches every other property.
        val length = LengthParser.parse(value.trim()) ?: return null
        return ColumnRuleInsetProperty(length)
    }
}
