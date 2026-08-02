package app.irmodels.properties.columns

// IRColor + the shared value types live in app.irmodels; imported with a
// wildcard to mirror ColumnRuleColorProperty.kt byte-for-byte.
import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * CSS Gap Decorations Level 1 §4.1 `row-rule-color`.
 *
 * The row-axis twin of `column-rule-color`: paints the gap decoration drawn
 * in ROW gaps (the gutters that run across the inline axis, created by
 * `row-gap`). Chromium's gap-decorations implementation computes it exactly
 * like `column-rule-color`, so the IR shape is deliberately identical to
 * [ColumnRuleColorProperty] — one `IRColor` field, which the
 * IRPropertySerializer single-field flattening unwraps to
 * `{"srgb":{...},"original":"blue"}` on the wire.
 */
@Serializable
data class RowRuleColorProperty(
    // Single field so the serializer's size==1 flatten produces the same
    // bytes as ColumnRuleColor (verified against a live conversion).
    val color: IRColor
) : IRProperty {
    // Canonical kebab-case CSS name; PropertyScope.of() keys off this string.
    override val propertyName = "row-rule-color"
}
