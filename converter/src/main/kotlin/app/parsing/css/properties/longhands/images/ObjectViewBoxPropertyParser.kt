package app.parsing.css.properties.longhands.images

import app.irmodels.IRLengthPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.images.ObjectViewBoxProperty
import app.irmodels.properties.images.ObjectViewBoxValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

/**
 * `object-view-box: none | <basic-shape-rect>` (css-images-4 §5.3).
 *
 * `<basic-shape-rect>` (css-shapes-1 §3.2) has THREE spellings —
 * `inset()`, `rect()` and `xywh()`. Only `inset()` was implemented, so the
 * other two returned null and the declaration vanished (wave-37 lane W2 —
 * four bucket-A tests rendered the un-cropped source image).
 */
object ObjectViewBoxPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "none") {
            return ObjectViewBoxProperty(ObjectViewBoxValue.None)
        }

        // inset() — <length> edges only, unchanged from the original parser
        // so its frozen wire shape does not move.
        if (trimmed.startsWith("inset(") && trimmed.endsWith(")")) {
            val inner = trimmed.removePrefix("inset(").removeSuffix(")").trim()
            val parts = inner.split("\\s+".toRegex())

            val lengths = parts.mapNotNull { LengthParser.parse(it) }
            if (lengths.isEmpty()) return null

            val (top, right, bottom, left) = when (lengths.size) {
                1 -> listOf(lengths[0], lengths[0], lengths[0], lengths[0])
                2 -> listOf(lengths[0], lengths[1], lengths[0], lengths[1])
                3 -> listOf(lengths[0], lengths[1], lengths[2], lengths[1])
                4 -> lengths
                else -> return null
            }

            return ObjectViewBoxProperty(ObjectViewBoxValue.Inset(top, right, bottom, left))
        }

        // rect() / xywh() — both take EXACTLY four <length-percentage> args
        // (css-shapes-1 §3.2; neither has the 1/2/3-value shorthand inset()
        // inherits from the margin grammar). A round-<radius> tail is legal
        // in the shape grammar but has no meaning for a view box and no
        // corpus use, so a value carrying one is rejected outright rather
        // than silently cropped to its rectangle.
        for ((fn, build) in FOUR_ARG_SHAPES) {
            if (!trimmed.startsWith("$fn(") || !trimmed.endsWith(")")) continue
            val inner = trimmed.removePrefix("$fn(").removeSuffix(")").trim()
            val parts = inner.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (parts.size != 4) return null
            val args = parts.map { lengthPercentage(it) ?: return null }
            return ObjectViewBoxProperty(build(args))
        }

        return null
    }

    /** `rect`/`xywh` → the constructor taking their four parsed arguments. */
    private val FOUR_ARG_SHAPES: List<Pair<String, (List<IRLengthPercentage>) -> ObjectViewBoxValue>> = listOf(
        "rect" to { a -> ObjectViewBoxValue.Rect(a[0], a[1], a[2], a[3]) },
        "xywh" to { a -> ObjectViewBoxValue.Xywh(a[0], a[1], a[2], a[3]) },
    )

    /**
     * One `<length-percentage>` argument. Percentages keep the legacy raw-number
     * wire form (IRLengthPercentage.Percentage); `0` is a valid unitless
     * <length> per css-values-4 §5.1 and LengthParser handles it.
     */
    private fun lengthPercentage(token: String): IRLengthPercentage? {
        PercentageParser.parse(token)?.let { return IRLengthPercentage.Percentage(it) }
        LengthParser.parse(token)?.let { return IRLengthPercentage.Length(it) }
        return null
    }
}
