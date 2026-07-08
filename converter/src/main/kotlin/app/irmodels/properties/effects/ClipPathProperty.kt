package app.irmodels.properties.effects

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class ClipPathProperty(
    val value: ClipPath
) : IRProperty {
    override val propertyName = "clip-path"

    @Serializable(with = ClipPathSerializer::class)
    sealed interface ClipPath {
        @Serializable data class None(val unit: Unit = Unit) : ClipPath
        @Serializable data class Url(val url: IRUrl) : ClipPath
        @Serializable data class BasicShape(val shape: Shape) : ClipPath
        @Serializable data class GeometryBox(val box: String) : ClipPath // margin-box, border-box, padding-box, content-box, fill-box, stroke-box, view-box
        @Serializable data class GeometryBoxShape(val box: String, val shape: Shape) : ClipPath
    }

    @Serializable(with = ClipPathShapeSerializer::class)
    sealed interface Shape {
        @Serializable data class Inset(val top: IRLength, val right: IRLength, val bottom: IRLength, val left: IRLength, val round: IRLength?) : Shape
        // CSS Shapes 1 §3.1 defines `<shape-radius>` =
        // `<length-percentage> | closest-side | farthest-side`. Previously
        // `radius` was typed `IRLength?` and the parser silently dropped
        // the keyword forms (`circle(farthest-side)` parsed to a null
        // radius — see swarm-003 clip-path-borderBox-1a investigation).
        // Now uses the [ShapeRadius] sealed union so both forms survive
        // through to the platform extractors. Wire-compat is preserved:
        // a [ShapeRadius.Length] still serializes as an IRLength JSON
        // object (matching the pre-fix shape), and a [ShapeRadius.Keyword]
        // emits a JSON string primitive (a brand-new variant — no prior
        // fixture exercised the keyword path, so there's nothing to
        // round-trip byte-for-byte against).
        @Serializable data class Circle(val radius: ShapeRadius?, val position: Position?) : Shape
        @Serializable data class Ellipse(val radiusX: ShapeRadius?, val radiusY: ShapeRadius?, val position: Position?) : Shape
        @Serializable data class Polygon(val points: List<Point>) : Shape
        @Serializable data class Path(val d: String) : Shape
        @Serializable data class Rect(val top: IRLength?, val right: IRLength?, val bottom: IRLength?, val left: IRLength?, val round: IRLength?) : Shape // null means 'auto'
        @Serializable data class Xywh(val x: IRLength, val y: IRLength, val width: IRLength, val height: IRLength, val round: IRLength?) : Shape
    }

    @Serializable data class Position(val x: IRLength, val y: IRLength)
    // CSS Shapes 2 §3.1.4: polygon points are `<length-percentage>` pairs.
    // Per-axis x/y is therefore an [IRLengthPercentage] union — the previous
    // typing as `IRPercentage` silently dropped any `px`/`em` coordinate at
    // parse time (mapNotNull → null when parsePercentageValue saw "100px"),
    // collapsing 6-point polygons to a single vertex. Wire shape stays
    // backward compatible: a JSON number is a percentage, a JSON object is
    // an IRLength. See [IRLengthPercentageSerializer].
    @Serializable data class Point(val x: IRLengthPercentage, val y: IRLengthPercentage)

    /**
     * CSS Shapes 1 §3.1 `<shape-radius>` grammar:
     *   `<length-percentage> | closest-side | farthest-side`
     *
     * Sealed union that lets `circle()` and `ellipse()` carry both the
     * absolute / percent length form AND the two keyword forms without
     * collapsing one into the other.
     *
     * Wire format (see [ShapeRadiusSerializer]):
     *  - [Length] → the underlying IRLength's JSON object shape
     *    (`{"px": N}` for absolute, `{"original":{v,u}}` for relative /
     *    percent). Matches the pre-fix wire form exactly, so any existing
     *    fixture with a `circle(50%)` or `circle(40px)` deserializes
     *    byte-identically.
     *  - [Keyword] → a JSON string primitive (`"closest-side"` /
     *    `"farthest-side"`). New variant — no prior fixture emitted this,
     *    so there's no legacy shape to honour.
     */
    @Serializable(with = ShapeRadiusSerializer::class)
    sealed interface ShapeRadius {
        @Serializable data class Length(val length: IRLength) : ShapeRadius
        @Serializable data class Keyword(val keyword: String) : ShapeRadius

        companion object {
            /** Canonical keyword values per CSS Shapes 1 §3.1. */
            const val CLOSEST_SIDE = "closest-side"
            const val FARTHEST_SIDE = "farthest-side"
        }
    }
}
