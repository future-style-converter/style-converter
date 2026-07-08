package app.irmodels.properties.scrolling

import kotlinx.serialization.Serializable

/**
 * Represents overscroll behavior values shared across overscroll-behavior properties.
 */
@Serializable
enum class OverscrollBehavior {
    /**
     * Default scroll overflow behavior occurs as normal.
     */
    AUTO,

    /**
     * Prevents scroll chaining - default scroll overflow behavior observed inside the element,
     * but no scroll chaining to neighboring scrolling areas.
     */
    CONTAIN,

    /**
     * No scroll chaining and default scroll overflow behavior is prevented.
     */
    NONE
}
