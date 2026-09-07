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
        /**
         * css-shapes-1 §3.1 `path()` = `path( <fill-rule>? , <string> )`.
         *
         * `fillRule` is the optional leading `nonzero` / `evenodd` token.
         * It defaults to null (= `nonzero`, the CSS initial) and is omitted
         * from the wire in that case, so every existing `path('M…')`
         * fixture serialises byte-for-byte as before. Wave 37 lane W3 added
         * it because the parser used to swallow the fill rule INTO the path
         * string — `path(nonzero, 'M0,0 …')` produced
         * `d = "nonzero, 'M0,0 …'"`, which the web runtime re-emitted as
         * `path("nonzero, 'M0,0 …'")`: not a path at all, so the browser
         * dropped the declaration and nothing was clipped.
         */
        @Serializable data class Path(val d: String, val fillRule: String? = null) : Shape
        /**
         * css-shapes-2 §4 `shape()` — the segment-list basic shape
         * (`shape(evenodd from 10px 10px, hline by 80px, close)`).
         *
         * Stored VERBATIM, exactly as `Path` stores its SVG `d` string,
         * because nothing inside a `shape()` can be pre-computed: its
         * segments carry `<position>`s, percentages and relative
         * (`by`) offsets that all resolve against the reference box, a
         * runtime-only quantity (CLAUDE.md: `null` / unresolved means
         * runtime-dependent). Before wave 37 the parser had no `shape(`
         * branch at all, so the whole declaration fell out of the IR as
         * a `Generic` `_unmapped` property and nothing was clipped —
         * eleven css-masking corpus cells (`clip-path-shape-002…010`,
         * `-shape-winding`, `-shape-hline-vline-keywords`).
         *
         * `text` is the complete function call including the `shape(`
         * prefix and closing paren, with runs of whitespace collapsed to
         * single spaces (the WPT sources wrap these values over several
         * lines). Web re-emits it unchanged and the browser parses it;
         * the native runtimes have no `shape()` analogue and log a TODO
         * rather than guessing.
         */
        @Serializable data class ShapeFunction(val text: String) : Shape
        @Serializable data class Rect(val top: IRLength?, val right: IRLength?, val bottom: IRLength?, val left: IRLength?, val round: IRLength?) : Shape // null means 'auto'
        @Serializable data class Xywh(val x: IRLength, val y: IRLength, val width: IRLength, val height: IRLength, val round: IRLength?) : Shape
    }

    /**
     * The `at <position>` clause of `circle()` / `ellipse()`
     * (css-shapes-1 §3.1, whose `<position>` is css-values-4's).
     *
     * `x` / `y` are the offsets measured INWARD FROM `xEdge` / `yEdge`.
     * The edges default to left / top — the CSS default origin — and are
     * therefore omitted from the wire whenever they hold that default
     * (`encodeDefaults` is off on the converter's Json instance), so every
     * value that parsed before wave 37 still serialises byte-for-byte as
     * `{"x": <IRLength>, "y": <IRLength>}`.
     *
     * WHY THE EDGES EXIST. Wave 37 lane W3 replaced
     * `ClipPathPropertyParser.parsePosition` — which accepted only bare
     * lengths plus a hardcoded `center`, silently dropping the entire `at`
     * clause of `circle(50% at left bottom)` and friends — with the full
     * `<position>` grammar via [app.parsing.css.properties.primitiveParsers.PositionParser].
     * That grammar's `[left|right] <length-percentage>` arm produces
     * values like `right 40px` (40px in from the RIGHT edge) which cannot
     * be rewritten as one left-origin length without knowing the
     * reference-box width — a runtime-only quantity. Keyword-only forms
     * are still normalised to the default origin (`right` → x = 100%
     * from left), so only the genuinely right/bottom-anchored offsets
     * carry an edge and consumers that ignore the field stay correct for
     * everything else.
     */
    @Serializable data class Position(
        val x: IRLength,
        val y: IRLength,
        // "left" | "right"; null (omitted) means the default left origin.
        val xEdge: String? = null,
        // "top" | "bottom"; null (omitted) means the default top origin.
        val yEdge: String? = null,
    )
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
