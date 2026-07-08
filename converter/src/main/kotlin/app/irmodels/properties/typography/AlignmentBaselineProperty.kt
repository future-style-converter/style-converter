package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class AlignmentBaselineValue {
    AUTO,
    BASELINE,
    BEFORE_EDGE,
    TEXT_BEFORE_EDGE,
    MIDDLE,
    CENTRAL,
    AFTER_EDGE,
    TEXT_AFTER_EDGE,
    IDEOGRAPHIC,
    ALPHABETIC,
    HANGING,
    MATHEMATICAL
}

/**
 * Represents the CSS `alignment-baseline` property.
 * Specifies how element aligns to parent's baseline.
 */
@Serializable
data class AlignmentBaselineProperty(
    val value: AlignmentBaselineValue
) : IRProperty {
    override val propertyName = "alignment-baseline"
}
