package com.styleconverter.test.style.core.types

import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import kotlinx.serialization.json.JsonElement

/**
 * Base interface for property configurations.
 *
 * Property configs are data classes that hold extracted values from IR JSON.
 * They serve as an intermediate representation between raw JSON and Compose modifiers.
 */
interface PropertyConfig {
    /**
     * Returns true if this config has any meaningful values to apply.
     * Used to skip applying empty/default configurations.
     */
    val hasValues: Boolean get() = true
}

/**
 * Interface for property appliers.
 *
 * Property appliers handle the extraction and application of a specific
 * category of CSS properties (e.g., spacing, borders, typography).
 *
 * @param T The configuration type that holds extracted values.
 */
interface PropertyApplier<T : PropertyConfig> {
    /**
     * Extract configuration values from a list of properties.
     *
     * @param properties List of property type/data pairs from the IR JSON.
     * @return A configuration object with extracted values.
     */
    fun extractConfig(properties: List<JsonPropertyData>): T

    /**
     * Apply the configuration to a modifier.
     *
     * @param modifier The base modifier to extend.
     * @param config The configuration with extracted values.
     * @return The modified Modifier with properties applied.
     */
    fun apply(modifier: Modifier, config: T): Modifier
}

/**
 * Simple wrapper for property type and data.
 *
 * This is a lightweight representation of IR properties for use
 * in the style system without requiring full deserialization.
 */
data class JsonPropertyData(
    /** The IR property type name (e.g., "PaddingTop", "BackgroundColor"). */
    val type: String,
    /** The JSON data for this property, or null if no data. */
    val data: JsonElement?
)

/**
 * Result of applying a property - modifier and optional side effects.
 *
 * Some properties produce effects beyond modifier changes, such as
 * text styles that need to be passed to Text composables.
 */
data class ApplyResult(
    /** The resulting modifier after property application. */
    val modifier: Modifier,
    /** Any side effects produced by the property application. */
    val sideEffects: List<SideEffect> = emptyList()
)

/**
 * Side effects that can be produced when applying properties.
 *
 * Some CSS properties don't map directly to Modifier methods and
 * need to be applied through other mechanisms.
 */
sealed interface SideEffect {
    /**
     * A text style that should be applied to Text composables.
     * Produced by typography properties like font-size, font-weight, etc.
     */
    data class TextStyleEffect(val style: TextStyle) : SideEffect

    /**
     * Layout configuration that affects container behavior.
     * Produced by display, flex-direction, grid properties, etc.
     */
    data class LayoutConfig(val config: Any) : SideEffect

    /**
     * Animation configuration for animated properties.
     */
    data class AnimationConfig(val config: Any) : SideEffect
}

/**
 * Common result type for extracting optional values.
 */
sealed interface ExtractResult<out T> {
    data class Value<T>(val value: T) : ExtractResult<T>
    data object NotPresent : ExtractResult<Nothing>
    data object Invalid : ExtractResult<Nothing>
}

/**
 * Extension to get value or null from ExtractResult.
 */
fun <T> ExtractResult<T>.valueOrNull(): T? = when (this) {
    is ExtractResult.Value -> value
    else -> null
}
