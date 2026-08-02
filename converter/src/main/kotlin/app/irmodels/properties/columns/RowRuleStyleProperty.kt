package app.irmodels.properties.columns

// Only the IRProperty marker is needed — the value domain is a local enum.
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * CSS Gap Decorations Level 1 §4.2 `row-rule-style`.
 *
 * Row-axis twin of `column-rule-style`. The value domain is the CSS Backgrounds
 * `<line-style>` set, exactly as [ColumnRuleStyleProperty] models it; a
 * dedicated nested enum (rather than reusing the column one) keeps the
 * one-property-per-file rule and keeps the two families independently
 * evolvable while emitting byte-identical wire data.
 */
@Serializable
data class RowRuleStyleProperty(
    // Single field → serializer flattens to the bare enum name, e.g. "SOLID",
    // matching the ColumnRuleStyle bytes seen in the wave23 css-gaps IR.
    val style: RuleStyle
) : IRProperty {
    // Canonical kebab-case CSS name.
    override val propertyName = "row-rule-style"

    /** `<line-style>` — CSS Backgrounds & Borders 3 §4.2, same 10 keywords. */
    enum class RuleStyle {
        // Order mirrors ColumnRuleStyleProperty.RuleStyle so a diff is empty.
        NONE, HIDDEN, DOTTED, DASHED, SOLID,
        DOUBLE, GROOVE, RIDGE, INSET, OUTSET
    }
}
