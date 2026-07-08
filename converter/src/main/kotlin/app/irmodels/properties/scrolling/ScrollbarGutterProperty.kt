package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scrollbar-gutter` property.
 *
 * ## CSS Property
 * **Syntax**: `scrollbar-gutter: auto | stable && both-edges?`
 *
 * ## Description
 * Controls whether space should be reserved for the scrollbar, preventing layout shifts
 * when scrollbars appear or disappear.
 *
 * ## Examples
 * ```kotlin
 * ScrollbarGutterProperty(value = ScrollbarGutter.Auto)
 * ScrollbarGutterProperty(value = ScrollbarGutter.Stable(bothEdges = false))
 * ScrollbarGutterProperty(value = ScrollbarGutter.Stable(bothEdges = true))
 * ```
 *
 * @property value The scrollbar gutter value
 * @see [MDN scrollbar-gutter](https://developer.mozilla.org/en-US/docs/Web/CSS/scrollbar-gutter)
 */
@Serializable
data class ScrollbarGutterProperty(
    val value: ScrollbarGutter
) : IRProperty {
    override val propertyName = "scrollbar-gutter"
}

/**
 * Represents a scrollbar-gutter value.
 */
@Serializable
sealed interface ScrollbarGutter {
    /**
     * Default behavior - scrollbar appears when content overflows.
     */
    @Serializable
    @SerialName("auto")
    data object Auto : ScrollbarGutter

    /**
     * Reserve space for scrollbar even when not needed.
     * @property bothEdges If true, reserves space on both sides of the scrollport
     */
    @Serializable
    @SerialName("stable")
    data class Stable(val bothEdges: Boolean = false) : ScrollbarGutter
}
