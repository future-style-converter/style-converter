package app.irmodels.properties.columns

// Only the IRProperty marker is needed — the value domain is a local enum.
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * CSS Gap Decorations Level 1 §7 `rule-overlap`.
 *
 * Declares the PAINT ORDER where a row-axis decoration and a column-axis
 * decoration cross:
 *   - `row-over-column` — row rules paint on top (initial value).
 *   - `column-over-row` — column rules paint on top.
 *
 * Only meaningful when both axes carry decorations AND neither is broken at
 * the intersection; the WPT flex corpus exercises it in
 * flex-gap-decorations-012 (`rule-overlap: column-over-row`).
 *
 * Note this is a LONGHAND despite the un-prefixed name — it has no
 * `column-`/`row-` twins, so it must never be treated as a shorthand.
 */
@Serializable
data class RuleOverlapProperty(
    // Single field → flattened to the bare UPPERCASE enum name on the wire.
    val value: Overlap
) : IRProperty {
    // Canonical kebab-case CSS name.
    override val propertyName = "rule-overlap"

    /** The two `<gap-rule-overlap>` keywords; ROW_OVER_COLUMN is the initial. */
    enum class Overlap {
        // SHOUTY_SNAKE forms of `row-over-column` / `column-over-row`.
        ROW_OVER_COLUMN, COLUMN_OVER_ROW
    }
}
