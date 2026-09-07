package app.irmodels.properties.background

// IR model for `background-image` (css-backgrounds-3 §2.3, css-images-3/4).
// The wire serializer lives in BackgroundImageSerializer.kt (same package) —
// split out per the ≤200-line file rule when cross-fade() joined the union.
import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class BackgroundImageProperty(
    // Ordered layer list — CSS source order, index 0 paints on top.
    val images: List<BackgroundImage>
) : IRProperty {
    override val propertyName = "background-image"

    @Serializable(with = BackgroundImageSerializer::class)
    sealed interface BackgroundImage {
        // `none` — a layer that paints nothing (css-backgrounds-3 §3.3).
        @Serializable data class None(val unit: Unit = Unit) : BackgroundImage
        // url() / image() reference — payload bytes preserved case-exactly.
        @Serializable data class Url(val url: IRUrl) : BackgroundImage
        // linear-gradient() / repeating-linear-gradient() (css-images-3 §3.1).
        // `interp` is the authored <color-interpolation-method> in canonical
        // CSS spelling (`in oklch`, `in hsl longer hue`) or null when absent —
        // see GradientValueParsers.interpolationMethodOf for why it is carried
        // rather than dropped. Serializes as the OPTIONAL "interp" key, so a
        // gradient written without one is byte-identical to the pre-wave-37 wire.
        @Serializable data class LinearGradient(val angle: IRAngle?, val colorStops: List<ColorStop>, val repeating: Boolean = false, val interp: String? = null) : BackgroundImage
        // radial-gradient() — shape/size prefix per css-images-3 §3.2.
        @Serializable data class RadialGradient(val shape: GradientShape?, val size: GradientSize?, val position: Position?, val colorStops: List<ColorStop>, val repeating: Boolean = false, val interp: String? = null) : BackgroundImage
        // conic-gradient() — `from <angle> at <position>` per css-images-4 §3.3.
        @Serializable data class ConicGradient(val angle: IRAngle?, val position: Position?, val colorStops: List<ColorStop>, val repeating: Boolean = false, val interp: String? = null) : BackgroundImage
        // A bare `<color>` used *as an image* — only valid inside
        // cross-fade() per css-images-4 §2.6.2 (`<cf-image> = <percentage>?
        // && [ <image> | <color> ]`); modelled as a layer so CrossFade args
        // stay a homogeneous list of BackgroundImage values.
        @Serializable data class ColorLayer(val color: IRColor) : BackgroundImage
        // image() notation with sources and/or a fallback colour
        // (css-images-4 §2.5 `image( <image-tags>? [<image-src># ]? [, <color>]? )`;
        // the multi-src form is the legacy css-images-3 grammar WPT
        // css-image-fallbacks-and-annotations003/004 still exercise).
        // `srcs` are the candidate sources IN AUTHOR ORDER — the first that
        // loads paints; `color` paints when no source can be displayed
        // (which for a src-less `image(<color>)` makes a plain solid-colour
        // image). Kept distinct from ColorLayer so the runtimes can tell
        // "author wrote image()" from a cross-fade colour argument. Added
        // wave-48 lane W5: all five WPT css-image-fallbacks-and-annotations
        // tests fell to Raw before — Chromium does not implement image(),
        // so even the web dropped the declaration and every platform painted
        // the red background-color the tests forbid (all three F, wave48-cal).
        @Serializable data class ImageNotation(val srcs: List<IRUrl>, val color: IRColor?) : BackgroundImage
        // cross-fade() — css-images-4 §2.6.2. `args` keeps the AUTHORED
        // per-image weights (null = omitted); the spec's normalization
        // (fill omitted from the 100% remainder, scale down if the sum
        // exceeds 100%) is executed by each runtime's CrossFadeMath twin so
        // all three platforms share one pinned formula. `legacy` records
        // that the value used the older two-argument trailing-percentage
        // syntax (`cross-fade(<image>, <image>, <percentage>)`) so the web
        // runtime can re-serialize to `-webkit-cross-fade()` for engines
        // that only ship the legacy form.
        @Serializable data class CrossFade(val args: List<CrossFadeArg>, val legacy: Boolean = false) : BackgroundImage
        // Global keyword (inherit/initial/unset/revert/revert-layer).
        @Serializable data class Keyword(val keyword: String) : BackgroundImage
        // Anything unparseable — original author bytes for runtime resolution.
        @Serializable data class Raw(val value: String) : BackgroundImage
    }

    // One cross-fade argument: optional authored weight (a CSS
    // <percentage>, 0–100 domain) plus the image (may be a ColorLayer).
    @Serializable data class CrossFadeArg(val weight: IRPercentage?, val image: BackgroundImage)

    // One gradient color stop. Double-position stops
    // (`<color> <pos1> <pos2>`, css-images-4 §3.5.3) are expanded by the
    // parser into TWO ColorStop entries sharing the color, so this model
    // stays a single-position pair and every runtime renders the implied
    // hard stop without new wire shapes.
    //
    // `positionLength` (wave-40 lane T6) carries the OTHER half of the
    // spec's <length-percentage> stop position — the `<length>` arm, which
    // had no home at all before and was silently discarded, costing the
    // repeat period of every `repeating-*-gradient(…, <color> <length>)`
    // (WPT css-images gradient-border-box / gradient-content-box, all three
    // platforms ≈0.645 against a striped reference: one full-size ramp
    // instead of 30px stripes).
    //
    // ADDITIVE BY CONSTRUCTION, on purpose. It is a NEW optional key with a
    // null default, and the converter's Json instance runs with
    // `encodeDefaults` off, so a stop whose position is a percentage (or
    // absent) serializes byte-for-byte as before — `schema/spec/05-
    // versioning.md`'s freeze is on emitted byte SHAPES, and no existing
    // shape moves. Re-typing `position` itself to IRLengthPercentage (the
    // move the gradient CENTRE made) was rejected precisely because it is
    // NOT additive: the Compose reader decodes it as
    // `obj["position"]?.jsonPrimitive?.floatOrNull`, and `jsonPrimitive`
    // THROWS on a JsonObject, so an object-shaped position would break
    // Android decoding of every length stop. Both native readers ignored
    // the key when it was introduced (wave 40); Compose
    // (ColorExtractor.extractColorStops, wave 47) and SwiftUI
    // (BackgroundImageExtractor, wave 46) now decode `positionLength: {px}`
    // as the stop's absolute position, and runtime-dependent units
    // ({original} without px) stay unpositioned with a logged breadcrumb.
    // (Retrospective A5#3 replaced the stale "both readers still ignore
    // it" sentence that stood here through two waves of reader changes.)
    @Serializable data class ColorStop(
        val color: IRColor,
        val position: IRPercentage?,
        val positionLength: IRLength? = null
    )

    // Gradient center (`at <position>`). Each axis is a
    // <length-percentage> (css-images-3 §3.2 / css-values-4 §5.4):
    // percentages keep their legacy raw-number wire form; lengths ride the
    // IRLengthPercentage object form ({"px":100} absolute, or
    // {"original":{"v":1,"u":"LH"}} for runtime-dependent units the
    // engines resolve where font metrics live). Wire back-compat: every
    // pre-existing {"x":25,"y":25} payload decodes identically.
    @Serializable data class Position(val x: IRLengthPercentage, val y: IRLengthPercentage) {
        companion object {
            // Convenience for the historical percent-only call sites.
            fun percent(x: Double, y: Double) = Position(
                IRLengthPercentage.Percentage(IRPercentage(x)),
                IRLengthPercentage.Percentage(IRPercentage(y))
            )
        }
    }

    // Radial ending shape (css-images-3 §3.2).
    enum class GradientShape { CIRCLE, ELLIPSE }
    // Radial size keyword (css-images-3 §3.2).
    enum class GradientSize { CLOSEST_SIDE, CLOSEST_CORNER, FARTHEST_SIDE, FARTHEST_CORNER }
}
