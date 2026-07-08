package app.irmodels.properties.global

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `all` property.
 *
 * ## CSS Property
 * **Syntax**: `all: initial | inherit | unset | revert | revert-layer`
 *
 * ## Description
 * A shorthand property that resets all CSS properties (except unicode-bidi and direction)
 * to their initial, inherited, or specified values. Useful for resetting styles.
 *
 * ## Examples
 * ```kotlin
 * AllProperty(value = AllValue.INITIAL)  // Reset all properties to initial values
 * AllProperty(value = AllValue.INHERIT)  // Inherit all properties from parent
 * AllProperty(value = AllValue.UNSET)    // Reset to inherited or initial values
 * ```
 *
 * ## Platform Support
 * - **CSS**: Full support
 * - **Compose**: No direct equivalent (would require manual reset)
 * - **SwiftUI**: No direct equivalent (would require manual reset)
 *
 * @property value The reset behavior
 * @see [MDN all](https://developer.mozilla.org/en-US/docs/Web/CSS/all)
 */
@Serializable
data class AllProperty(
    val value: AllValue
) : IRProperty {
    override val propertyName = "all"
}

/**
 * Represents values for the `all` property.
 */
@Serializable
enum class AllValue {
    /**
     * Resets all properties to their initial values (as defined in CSS spec).
     */
    INITIAL,

    /**
     * Sets all properties to inherit from their parent element.
     */
    INHERIT,

    /**
     * Resets properties to inherited value if inheritable, otherwise to initial value.
     */
    UNSET,

    /**
     * Resets properties to the value specified by the user agent stylesheet
     * (or user stylesheet if one exists).
     */
    REVERT,

    /**
     * Resets properties to the value established by the previous cascade layer.
     */
    REVERT_LAYER
}
