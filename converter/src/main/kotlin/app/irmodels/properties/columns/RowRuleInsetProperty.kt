package app.irmodels.properties.columns

// IRLength carries the dual px/original storage and serializes to {"px":N}.
import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * CSS Gap Decorations Level 1 §6 `row-rule-inset`.
 *
 * Row-axis twin of [ColumnRuleInsetProperty]: shrinks (positive) or extends
 * (negative) both ends of every row-gap decoration. Initial value `0px`;
 * negatives allowed (the WPT flex corpus uses `-25%`, `-50%`, `-210px`).
 */
@Serializable
data class RowRuleInsetProperty(
    // Single IRLength field → flattened to the bare length object on the wire.
    val inset: IRLength
) : IRProperty {
    // Canonical kebab-case CSS name.
    override val propertyName = "row-rule-inset"
}
