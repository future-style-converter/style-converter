package app.irmodels.properties.images

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ObjectViewBoxValue {
    @SerialName("none")
    @Serializable
    data object None : ObjectViewBoxValue

    @SerialName("inset")
    @Serializable
    data class Inset(
        val top: IRLength,
        val right: IRLength,
        val bottom: IRLength,
        val left: IRLength
    ) : ObjectViewBoxValue

    /**
     * `rect(<top> <right> <bottom> <left>)` — css-shapes-1 §3.2's
     * <basic-shape-rect>, which css-images-5 §3.1 admits for object-view-box
     * alongside `inset()` and `xywh()`.
     *
     * wave-37 lane W2: the parser recognised ONLY `inset()`, so
     * `object-view-box: rect(…)` / `xywh(…)` returned null and the whole
     * declaration was dropped — the image rendered un-cropped (measured:
     * object-view-box-rect/-xywh and their percentage twins painted the full
     * four-quadrant source where the ref shows one quadrant).
     *
     * Edges are <length-percentage>, not <length>: half of the corpus's
     * spellings are percentages of the natural size, which an IRLength cannot
     * carry at all. (Inset above keeps its IRLength edges — moving them would
     * be a byte-shape change to a frozen wire for no measured gain.)
     */
    @SerialName("rect")
    @Serializable
    data class Rect(
        val top: IRLengthPercentage,
        val right: IRLengthPercentage,
        val bottom: IRLengthPercentage,
        val left: IRLengthPercentage
    ) : ObjectViewBoxValue

    /** `xywh(<x> <y> <width> <height>)` — the same rectangle, origin+size. */
    @SerialName("xywh")
    @Serializable
    data class Xywh(
        val x: IRLengthPercentage,
        val y: IRLengthPercentage,
        val width: IRLengthPercentage,
        val height: IRLengthPercentage
    ) : ObjectViewBoxValue
}

/**
 * Represents the CSS `object-view-box` property.
 * Specifies the view box over an element's contents.
 */
@Serializable
data class ObjectViewBoxProperty(
    val value: ObjectViewBoxValue
) : IRProperty {
    override val propertyName = "object-view-box"
}
