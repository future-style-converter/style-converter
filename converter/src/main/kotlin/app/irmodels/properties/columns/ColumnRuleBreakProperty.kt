package app.irmodels.properties.columns

// Only the IRProperty marker is needed — the value domain is a local enum.
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * CSS Gap Decorations Level 1 §5.1 `column-rule-break`.
 *
 * Controls where a column-axis gap decoration is CUT into segments as it
 * crosses the grid/flex gap intersections:
 *   - `normal`        — one continuous rule spanning the whole gap (initial).
 *   - `spanning-item` — break only where an item spans across the gap.
 *   - `intersection`  — break at every intersection with a cross-axis gap.
 *
 * Value domain verified against Chromium's computed style for the
 * css-gaps WPT flex corpus (`getComputedStyle(el).columnRuleBreak` returns
 * exactly one of those three idents).
 */
@Serializable
data class ColumnRuleBreakProperty(
    // Single field → IRPropertySerializer flattens it away, so `data` is the
    // bare UPPERCASE enum name (e.g. "INTERSECTION"), per the wire contract.
    val value: RuleBreak
) : IRProperty {
    // Canonical kebab-case CSS name.
    override val propertyName = "column-rule-break"

    /** The three `<gap-rule-break>` keywords; NORMAL is the CSS initial value. */
    enum class RuleBreak {
        // SPANNING_ITEM is the SHOUTY_SNAKE form of the `spanning-item` ident —
        // the runtimes kebab it back on the way out.
        NORMAL, SPANNING_ITEM, INTERSECTION
    }
}
