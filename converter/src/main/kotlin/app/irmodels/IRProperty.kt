package app.irmodels

import kotlinx.serialization.Serializable

/**
 * Base interface for all Intermediate Representation (IR) property classes.
 *
 * ## Purpose
 * This interface serves as the foundation for all CSS property representations in the IR model.
 * It provides a pure data structure without any conversion or business logic.
 *
 * ## Architecture Principles
 * - **Pure Data Representation**: Contains only data, no conversion methods (no toCompose(), toSwiftUI(), toCSS())
 * - **One Property Per File**: Each implementing class represents exactly ONE CSS property
 * - **Precise Type Constraints**: Implementations use enums and sealed interfaces to enforce valid values
 * - **Fully Serializable**: All implementations must be annotated with @Serializable
 * - **Maximum 100 Lines**: Each property file should never exceed 100 lines of code
 *
 * ## Usage Example
 * ```kotlin
 * @Serializable
 * data class FontSizeProperty(
 *     val size: FontSize
 * ) : IRProperty {
 *     override val propertyName = "font-size"
 *
 *     @Serializable
 *     sealed interface FontSize {
 *         data class Length(val value: IRLength) : FontSize
 *         data class Keyword(val value: AbsoluteSize) : FontSize
 *     }
 * }
 * ```
 *
 * ## Implementation Guidelines
 * 1. Use sealed interfaces for values with multiple type options (e.g., length | percentage | auto)
 * 2. Use enums for fixed keyword sets (e.g., text-align: left, right, center, justify)
 * 3. Reference base types from ValueTypes.kt (IRLength, IRColor, IRAngle, etc.)
 * 4. Keep property names in kebab-case matching CSS specifications
 * 5. No default values unless they match CSS initial values
 *
 * @see ValueTypes.kt for available base types (IRLength, IRColor, IRAngle, IRTime, etc.)
 */
interface IRProperty {
    /**
     * The CSS property name in kebab-case.
     *
     * This should exactly match the CSS specification property name.
     * Examples: "font-size", "background-color", "border-top-width"
     */
    val propertyName: String
}