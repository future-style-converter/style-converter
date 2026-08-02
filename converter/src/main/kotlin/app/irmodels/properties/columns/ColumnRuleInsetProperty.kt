package app.irmodels.properties.columns

// IRLength carries the dual px/original storage and serializes to {"px":N}
// (plus an {"original":{v,u}} sidecar for non-px / unresolvable units).
import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * CSS Gap Decorations Level 1 §6 `column-rule-inset`.
 *
 * Shrinks (positive) or extends (negative) BOTH ends of every column-axis
 * gap decoration along its own length. The initial value is `0px`; negative
 * lengths are explicitly allowed by the spec (the WPT flex corpus uses
 * `column-rule-inset: -2px` and `-100%`), so no clamping happens here.
 *
 * Wire shape per the wave-24 contract: a plain length object — `{"px":0}` —
 * i.e. the same single-IRLength flatten used by MarginTop et al.
 */
@Serializable
data class ColumnRuleInsetProperty(
    // Single IRLength field → serializer flattens to the bare length object.
    // Percentages/relative units keep pixels=null (runtime-dependent), which
    // is the repo-wide "cannot pre-compute" convention (spec 02-values.md).
    val inset: IRLength
) : IRProperty {
    // Canonical kebab-case CSS name.
    override val propertyName = "column-rule-inset"
}
