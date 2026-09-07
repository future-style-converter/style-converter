package app.parsing.css.properties.longhands.effects

import app.irmodels.properties.background.BackgroundImageProperty
import app.irmodels.properties.effects.MaskImageProperty
import app.irmodels.properties.effects.MaskImageValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.longhands.background.BackgroundImagePropertyParser

/**
 * Parser for the `mask-image` CSS property (css-masking-1 §7.1).
 *
 * `mask-image` accepts the SAME `<image>` grammar as `background-image`
 * (none | url() | the six gradient flavours), so this parser DELEGATES the
 * per-layer grammar to [BackgroundImagePropertyParser.parseImage] and maps
 * the result onto [MaskImageValue] — one grammar, two wrappers (A-RC9).
 *
 * WHY delegation: the previous copy re-implemented the grammar and had
 * already drifted — its conic branch had NO `from <angle>` / `at <pos>`
 * prefix handling (`from 45deg` was fed to the color-stop parser and
 * silently dropped), its radial branch ignored size keywords + positions,
 * and its stop parser predated double-position stops. Every future gradient
 * fix now lands in both properties automatically.
 */
object MaskImagePropertyParser : PropertyParser {

    override fun parse(value: String): MaskImageProperty? {
        // CASE-PRESERVATION CONTRACT (shared with BackgroundImageParser):
        // keywords/function names are ASCII case-insensitive (CSS Syntax L3
        // §4.3) but url() payloads are case-sensitive author bytes. The
        // delegate handles lowering per-layer; we only split here.
        val trimmed = value.trim()

        // Split the ORIGINAL bytes; the splitter is paren-aware and
        // case-agnostic, so layers keep the author's casing.
        val imageStrings = app.parsing.css.properties.primitiveParsers.TokenizationUtils.splitByComma(trimmed)
        if (imageStrings.isEmpty()) return null

        // Delegate each layer to the background grammar, then map to the
        // mask IR union. mask-image keeps the historical all-or-nothing
        // contract: ANY unmappable layer fails the whole property (the
        // caller records it as unparsed rather than emitting partial wire).
        val images = imageStrings.mapNotNull { layer ->
            BackgroundImagePropertyParser.parseImage(layer.trim())?.let { mapToMask(it) }
        }
        if (images.size != imageStrings.size) return null

        return MaskImageProperty(images)
    }

    // BackgroundImage → MaskImageValue. The two unions are wire-parallel
    // twins (same JSON shapes, see MaskImageValueSerializer), so mapping is
    // structural. Returns null for layers the mask union cannot represent —
    // cross-fade()/color layers (css-images-4 features not yet modelled on
    // the mask side) — which fails the whole property above, keeping the
    // loss VISIBLE (unparsed property) instead of silent.
    private fun mapToMask(image: BackgroundImageProperty.BackgroundImage): MaskImageValue? = when (image) {
        is BackgroundImageProperty.BackgroundImage.None -> MaskImageValue.None
        is BackgroundImageProperty.BackgroundImage.Url -> MaskImageValue.Image(image.url)
        is BackgroundImageProperty.BackgroundImage.LinearGradient ->
            MaskImageValue.LinearGradient(image.angle, image.colorStops.map { mapStop(it) }, image.repeating)
        is BackgroundImageProperty.BackgroundImage.RadialGradient ->
            MaskImageValue.RadialGradient(
                // Enum twins map by name (both mirror css-images-3 §3.2).
                image.shape?.let { MaskImageValue.GradientShape.valueOf(it.name) },
                image.size?.let { MaskImageValue.GradientSize.valueOf(it.name) },
                image.position?.let { MaskImageValue.Position(it.x, it.y) },
                image.colorStops.map { mapStop(it) },
                image.repeating
            )
        is BackgroundImageProperty.BackgroundImage.ConicGradient ->
            MaskImageValue.ConicGradient(
                image.angle,
                image.position?.let { MaskImageValue.Position(it.x, it.y) },
                image.colorStops.map { mapStop(it) },
                image.repeating
            )
        // Not representable in the mask union (yet): global keywords,
        // raw fallbacks, cross-fade(), bare colors. Null → whole-property
        // failure above (the pre-delegation parser rejected these too).
        else -> null
    }

    // ColorStop twin mapping — identical field shapes on both sides.
    private fun mapStop(stop: BackgroundImageProperty.ColorStop): MaskImageValue.ColorStop =
        MaskImageValue.ColorStop(stop.color, stop.position)
}
