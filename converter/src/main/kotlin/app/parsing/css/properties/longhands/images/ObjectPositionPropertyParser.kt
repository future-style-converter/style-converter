package app.parsing.css.properties.longhands.images

import app.irmodels.*
import app.irmodels.properties.images.ObjectPositionProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords
import app.parsing.css.properties.primitiveParsers.ExpressionDetector

/**
 * `object-position` — css-images-3 §5.2, whose value is the css-values-4
 * `<position>` grammar shared with `background-position` (css-backgrounds-3
 * §3.6):
 *
 *     [ left | center | right | top | bottom | <length-percentage> ]
 *   | [ left | center | right | <length-percentage> ]
 *     [ top | center | bottom | <length-percentage> ]
 *   | [ center | [ left | right ] <length-percentage>? ] &&
 *     [ center | [ top | bottom ] <length-percentage>? ]
 *
 * WAVE-36 LANE M1 — THE AXIS BUG THIS REPLACES. The previous implementation
 * read the token stream POSITIONALLY: `parts[0] → x, parts[1] → y` for the
 * two-value form, and `keyword offset keyword offset` as x-first for the
 * four-value form. The grammar does not work that way. Its third arm is a
 * `&&` (unordered), so `top right` means **x = right, y = top**, and
 * `top 25% left 25%` means **y = (top, 25%), x = (left, 25%)** — both
 * spellings the css-images WPT `object-fit-*` family uses on six of the
 * seven boxes in every row. Six of every seven elements in 132 corpus tests
 * therefore reached the runtimes with the two axes SWAPPED. The one-value
 * form was wrong for the same reason: `top` alone is `center top`, not
 * `top center`.
 *
 * The parser now classifies each token by the AXIS its keyword can name and
 * assigns from that, which is what makes the unordered arm work. Anything
 * the grammar rejects (`top 10px` in the two-value form — `top` cannot be a
 * horizontal position) falls back to `Raw`, which is the honest answer: a
 * browser drops the invalid declaration, and a `Raw` value re-emitted
 * verbatim on the web is dropped by the browser for exactly the same reason.
 */
object ObjectPositionPropertyParser : PropertyParser {

    /** Which axis a `<position>` keyword can name. `center` is either. */
    private enum class Axis { HORIZONTAL, VERTICAL, EITHER }

    /** One parsed `<position>` component: a keyword with an optional offset,
     *  or a bare length/percentage (which is always axis-free). `keyword`
     *  distinguishes the two — the unordered third arm admits ONLY
     *  keyword-derived components, so a bare length there is a syntax error
     *  even though it would be legal in the ordered second arm. */
    private data class Part(
        val value: ObjectPositionProperty.ObjectPositionValue,
        val axis: Axis,
        val keyword: Boolean,
    )

    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle global keywords first (inherit, initial, unset, revert, revert-layer)
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            val globalValue = ObjectPositionProperty.ObjectPositionValue.GlobalKeyword(trimmed)
            return ObjectPositionProperty(ObjectPositionProperty.Position(globalValue, globalValue))
        }

        // Handle CSS functions (var, env, calc, etc.)
        if (ExpressionDetector.containsExpression(trimmed)) {
            val rawValue = ObjectPositionProperty.ObjectPositionValue.Raw(value.trim())
            return ObjectPositionProperty(ObjectPositionProperty.Position(rawValue, rawValue))
        }

        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        // §5.2 admits one to four components; anything else is a syntax error.
        val position = if (tokens.size in 1..4) resolve(tokens) else null
        return ObjectPositionProperty(position ?: raw(value))
    }

    /** The Raw fallback both axes share when the value is unparseable — a
     *  verbatim echo, never a guess (CLAUDE.md: no silent fallthroughs). */
    private fun raw(value: String): ObjectPositionProperty.Position {
        val rawValue = ObjectPositionProperty.ObjectPositionValue.Raw(value.trim())
        return ObjectPositionProperty.Position(rawValue, rawValue)
    }

    /** The initial value of each axis when the shorthand names only the
     *  other one (§5.2: a one-value form fills the missing axis with
     *  `center`). */
    private fun center() = ObjectPositionProperty.ObjectPositionValue
        .Keyword(ObjectPositionProperty.PositionKeyword.CENTER)

    /**
     * Resolve a token list into the two axes, arm by arm. Returns null for
     * anything outside the grammar so the caller can fall back to Raw.
     *
     * WHICH ARM APPLIES IS DECIDED BY TOKEN COUNT, and that is the whole
     * subtlety. `left 30%` is TWO tokens, so it is the ORDERED second arm
     * (x = left, y = 30%) — NOT `left` carrying a 30% offset, which would
     * centre the vertical axis and put the box in a different place.
     * Keyword-plus-offset grouping only exists in the three- and four-token
     * forms, where the unordered third arm is the only arm long enough to
     * match.
     */
    private fun resolve(tokens: List<String>): ObjectPositionProperty.Position? = when (tokens.size) {
        1 -> oneComponent(tokens[0])
        2 -> twoComponents(tokens[0], tokens[1])
        else -> unorderedArm(tokens)
    }

    /** Arm 1: `[left|center|right|top|bottom|<length-percentage>]`. The named
     *  axis takes the value and the other centres (§5.2), which is why a lone
     *  `top` is `center top` and not `top center`. */
    private fun oneComponent(token: String): ObjectPositionProperty.Position? {
        val part = simple(token) ?: return null
        return if (part.axis == Axis.VERTICAL) {
            ObjectPositionProperty.Position(center(), part.value)
        } else {
            // A bare <length-percentage>, `center`, or an explicit left/right
            // is the HORIZONTAL position.
            ObjectPositionProperty.Position(part.value, center())
        }
    }

    /**
     * Arms 2 and 3 at two tokens.
     *
     * Two KEYWORDS are unordered (arm 3 matches with both offsets absent), so
     * `top right` is x = right, y = top. Anything involving a bare
     * `<length-percentage>` can only be the ORDERED arm 2,
     * `[left|center|right|<lp>] [top|center|bottom|<lp>]` — so `10px top` is
     * valid while `top 10px` is not a position at all.
     */
    private fun twoComponents(first: String, second: String): ObjectPositionProperty.Position? {
        val a = simple(first) ?: return null
        val b = simple(second) ?: return null
        // Reorder ONLY when the unordered arm can match, i.e. both sides are
        // keywords. `a.axis == VERTICAL` is the trigger; `b.axis ==
        // HORIZONTAL` catches `center left`, whose first component is free.
        val swap = a.keyword && b.keyword && (a.axis == Axis.VERTICAL || b.axis == Axis.HORIZONTAL)
        val x = if (swap) b else a
        val y = if (swap) a else b
        return assign(x, y)
    }

    /**
     * Arm 3 at three or four tokens:
     * `[center | [left|right] <lp>?] && [center | [top|bottom] <lp>?]`.
     *
     * Each keyword absorbs a following `<length-percentage>` as ITS offset
     * (`right 2px` = 2px in from the right edge), which is what makes
     * `bottom 1px right 2px` mean y = (bottom,1px), x = (right,2px) rather
     * than the positional reading that swapped both axes before this wave.
     * Every component must be keyword-derived — this arm has no bare-length
     * alternative — and exactly two must result.
     */
    private fun unorderedArm(tokens: List<String>): ObjectPositionProperty.Position? {
        val parts = mutableListOf<Part>()
        var i = 0
        while (i < tokens.size) {
            val keyword = parseKeyword(tokens[i]) ?: return null   // no bare <lp> in this arm
            val offset = tokens.getOrNull(i + 1)?.let { LengthParser.parse(it) }
            if (offset == null) {
                parts.add(Part(ObjectPositionProperty.ObjectPositionValue.Keyword(keyword), axisOf(keyword), true))
                i++
                continue
            }
            // `center` takes no offset (§5.2), so a length after it is a
            // syntax error, not an offset to quietly attach to the wrong side.
            if (keyword == ObjectPositionProperty.PositionKeyword.CENTER) return null
            parts.add(Part(
                ObjectPositionProperty.ObjectPositionValue.KeywordOffset(keyword, offset),
                axisOf(keyword),
                true,
            ))
            i += 2
        }
        if (parts.size != 2) return null
        val (a, b) = parts
        // Same unordered assignment as the two-keyword case above.
        val swap = a.axis == Axis.VERTICAL || b.axis == Axis.HORIZONTAL
        return assign(if (swap) b else a, if (swap) a else b)
    }

    /** Final validation of an axis assignment: a component that can only name
     *  the vertical axis may not be the horizontal position, and vice versa.
     *  This is what rejects `top 10px`, `left right` and `top bottom` instead
     *  of emitting a plausible-looking swapped guess. */
    private fun assign(x: Part, y: Part): ObjectPositionProperty.Position? {
        if (x.axis == Axis.VERTICAL || y.axis == Axis.HORIZONTAL) return null
        return ObjectPositionProperty.Position(x.value, y.value)
    }

    /** One un-grouped component: a position keyword or a bare
     *  `<length-percentage>`. Null when the token is neither. */
    private fun simple(token: String): Part? {
        val keyword = parseKeyword(token)
        if (keyword != null) {
            return Part(ObjectPositionProperty.ObjectPositionValue.Keyword(keyword), axisOf(keyword), true)
        }
        val lp = parseLengthPercentage(token) ?: return null
        return Part(lp, Axis.EITHER, false)
    }

    /** The axis a keyword names; `center` is valid on either. */
    private fun axisOf(k: ObjectPositionProperty.PositionKeyword): Axis = when (k) {
        ObjectPositionProperty.PositionKeyword.LEFT,
        ObjectPositionProperty.PositionKeyword.RIGHT -> Axis.HORIZONTAL
        ObjectPositionProperty.PositionKeyword.TOP,
        ObjectPositionProperty.PositionKeyword.BOTTOM -> Axis.VERTICAL
        ObjectPositionProperty.PositionKeyword.CENTER -> Axis.EITHER
    }

    private fun parseKeyword(value: String): ObjectPositionProperty.PositionKeyword? {
        return when (value.lowercase()) {
            "left" -> ObjectPositionProperty.PositionKeyword.LEFT
            "center" -> ObjectPositionProperty.PositionKeyword.CENTER
            "right" -> ObjectPositionProperty.PositionKeyword.RIGHT
            "top" -> ObjectPositionProperty.PositionKeyword.TOP
            "bottom" -> ObjectPositionProperty.PositionKeyword.BOTTOM
            else -> null
        }
    }

    /** A bare `<length-percentage>` component. Percentages keep their own
     *  variant because §5.2 resolves them against the (box − content) size,
     *  which only the runtime knows. */
    private fun parseLengthPercentage(value: String): ObjectPositionProperty.ObjectPositionValue? {
        val length = LengthParser.parse(value.trim()) ?: return null
        return if (length.unit == IRLength.LengthUnit.PERCENT) {
            ObjectPositionProperty.ObjectPositionValue.PercentageValue(IRPercentage(length.value))
        } else {
            ObjectPositionProperty.ObjectPositionValue.LengthValue(length)
        }
    }
}
