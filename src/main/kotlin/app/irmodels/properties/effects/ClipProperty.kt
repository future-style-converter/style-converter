package app.irmodels.properties.effects

import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `clip` property (legacy).
 *
 * Deprecated in favor of clip-path, but still used in some contexts.
 *
 * Syntax: auto | rect(top, right, bottom, left)
 */
@Serializable
data class ClipProperty(val value: ClipValue) : IRProperty {
    override val propertyName: String = "clip"
}

@Serializable
sealed interface ClipValue {
    @Serializable
    @SerialName("auto")
    data object Auto : ClipValue

    @Serializable
    @SerialName("rect")
    data class Rect(
        // Each side can be a length OR the keyword `auto`. CSS spec
        // (CSS 2.1 §11.1.2): `auto` on top/left → 0, on bottom/right →
        // the element's full computed extent. We carry it as `null` so
        // the per-platform applier can resolve against the actual draw
        // bounds at render time. Without this nullability the parser
        // dropped `rect(auto, 100px, 200px, 0)` entirely (LengthParser
        // returned null for `auto` → whole parse fell to GenericProperty).
        val top: IRLength? = null,
        val right: IRLength? = null,
        val bottom: IRLength? = null,
        val left: IRLength? = null,
    ) : ClipValue
}
