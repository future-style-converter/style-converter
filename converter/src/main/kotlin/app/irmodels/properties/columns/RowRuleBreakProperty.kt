package app.irmodels.properties.columns

// Only the IRProperty marker is needed — the value domain is a local enum.
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * CSS Gap Decorations Level 1 §5.1 `row-rule-break`.
 *
 * Row-axis twin of [ColumnRuleBreakProperty]: same three-keyword domain,
 * applied to the decorations painted in ROW gaps. Kept as its own file (and
 * its own nested enum) so the columns/ tree stays one-property-per-file while
 * the emitted bytes stay identical to the column-axis property.
 */
@Serializable
data class RowRuleBreakProperty(
    // Single field → flattened to the bare UPPERCASE enum name on the wire.
    val value: RuleBreak
) : IRProperty {
    // Canonical kebab-case CSS name.
    override val propertyName = "row-rule-break"

    /** The three `<gap-rule-break>` keywords; NORMAL is the CSS initial value. */
    enum class RuleBreak {
        // Mirrors ColumnRuleBreakProperty.RuleBreak exactly.
        NORMAL, SPANNING_ITEM, INTERSECTION
    }
}
