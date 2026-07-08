package app.irmodels.properties.transforms

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * CSS `transform` property - applies 2D/3D transformations.
 *
 * Supports:
 * - `Functions`: List of transform functions (translate, rotate, scale, skew, matrix, etc.)
 * - `Expression`: CSS expressions like calc() or var()
 * - `Keyword`: "none" or global keywords
 *
 * All angles in TransformFunction are normalized to degrees.
 * All lengths are normalized to pixels when possible.
 *
 * @see TransformFunction for individual transform function types
 */
@Serializable
data class TransformProperty(
    val value: TransformValue
) : IRProperty {
    override val propertyName = "transform"

    constructor(functions: List<TransformFunction>) : this(TransformValue.Functions(functions))

    @Serializable
    sealed interface TransformValue {
        @Serializable @SerialName("functions")
        data class Functions(val list: List<TransformFunction>) : TransformValue
        @Serializable @SerialName("expression")
        data class Expression(val expr: String) : TransformValue
        @Serializable @SerialName("keyword")
        data class Keyword(val keyword: String) : TransformValue
    }

    val functions: List<TransformFunction>
        get() = when (value) {
            is TransformValue.Functions -> value.list
            else -> emptyList()
        }
}
