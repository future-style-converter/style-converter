package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface AnimationTimelineValue {
    @Serializable
    @SerialName("auto")
    data object Auto : AnimationTimelineValue

    @Serializable
    @SerialName("none")
    data object None : AnimationTimelineValue

    @Serializable
    @SerialName("named")
    data class Named(val name: String) : AnimationTimelineValue

    /**
     * scroll() function - creates a scroll timeline.
     * @property scroller The scroll container (nearest, root, self)
     * @property axis The scroll axis (block, inline, x, y)
     */
    @Serializable
    @SerialName("scroll")
    data class Scroll(
        val scroller: ScrollScroller = ScrollScroller.NEAREST,
        val axis: ScrollAxis = ScrollAxis.BLOCK
    ) : AnimationTimelineValue

    /**
     * view() function - creates a view timeline based on element visibility.
     * @property axis The scroll axis (block, inline, x, y)
     * @property inset Optional inset values for the view
     */
    @Serializable
    @SerialName("view")
    data class View(
        val axis: ScrollAxis = ScrollAxis.BLOCK,
        val insetStart: String? = null,
        val insetEnd: String? = null
    ) : AnimationTimelineValue

    /**
     * Multiple timelines (comma-separated list)
     */
    @Serializable
    @SerialName("multiple")
    data class Multiple(val timelines: List<AnimationTimelineValue>) : AnimationTimelineValue
}

@Serializable
enum class ScrollScroller {
    @SerialName("nearest") NEAREST,
    @SerialName("root") ROOT,
    @SerialName("self") SELF
}

@Serializable
enum class ScrollAxis {
    @SerialName("block") BLOCK,
    @SerialName("inline") INLINE,
    @SerialName("x") X,
    @SerialName("y") Y
}

/**
 * Represents the CSS `animation-timeline` property.
 * Specifies the timeline for an animation (scroll-driven animations).
 */
@Serializable
data class AnimationTimelineProperty(
    val value: AnimationTimelineValue
) : IRProperty {
    override val propertyName = "animation-timeline"
}
