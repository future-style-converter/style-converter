package app.irmodels.properties.borders

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-image-repeat` property.
 *
 * ## CSS Property
 * **Syntax**: `border-image-repeat: [ stretch | repeat | round | space ]{1,2}`
 *
 * ## Description
 * Controls how the border image's edge regions are scaled and tiled.
 * First value applies to horizontal sides, second to vertical.
 *
 * ## Examples
 * ```kotlin
 * BorderImageRepeatProperty(
 *     horizontal = BorderImageRepeat.REPEAT,
 *     vertical = BorderImageRepeat.STRETCH
 * )
 * ```
 *
 * @property horizontal Horizontal repeat behavior
 * @property vertical Vertical repeat behavior (defaults to horizontal if not specified)
 * @see [MDN border-image-repeat](https://developer.mozilla.org/en-US/docs/Web/CSS/border-image-repeat)
 */
@Serializable
data class BorderImageRepeatProperty(
    val horizontal: BorderImageRepeat,
    val vertical: BorderImageRepeat? = null
) : IRProperty {
    override val propertyName = "border-image-repeat"

    companion object {
        fun both(repeat: BorderImageRepeat) = BorderImageRepeatProperty(repeat, repeat)
    }
}

/**
 * Represents border-image-repeat values.
 */
@Serializable
enum class BorderImageRepeat {
    /**
     * The image is stretched to fill the area.
     */
    STRETCH,

    /**
     * The image is tiled (repeated) to fill the area.
     */
    REPEAT,

    /**
     * The image is tiled and rescaled to fill the area with a whole number of tiles.
     */
    ROUND,

    /**
     * The image is tiled, and extra space is distributed around the tiles.
     */
    SPACE
}
