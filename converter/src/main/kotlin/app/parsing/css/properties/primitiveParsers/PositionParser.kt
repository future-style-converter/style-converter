package app.parsing.css.properties.primitiveParsers

import app.irmodels.IRLength

/**
 * The css-values-4 `<position>` grammar, as a reusable primitive.
 *
 * https://drafts.csswg.org/css-values-4/#position — the same production
 * `background-position`, `object-position`, `mask-position`, the
 * `radial-gradient()` `at` clause and every css-shapes `<basic-shape>`
 * `at` clause share:
 *
 *     [ left | center | right | top | bottom | <length-percentage> ]
 *   | [ left | center | right | <length-percentage> ]
 *     [ top | center | bottom | <length-percentage> ]
 *   | [ center | [ left | right ] <length-percentage>? ] &&
 *     [ center | [ top | bottom ] <length-percentage>? ]
 *
 * WHY THIS FILE EXISTS. Wave 36 lane M1 rewrote
 * `longhands/images/ObjectPositionPropertyParser.kt` to implement this
 * grammar correctly after finding that the previous positional reading
 * (`parts[0] -> x, parts[1] -> y`) swapped the axes for every value using
 * the unordered third arm (`top right` means x=right, y=top — NOT
 * x=top). Wave 37 lane W3 found the same class of defect in
 * `longhands/effects/ClipPathPropertyParser.kt`, whose `parsePosition`
 * accepted only bare lengths plus a hardcoded `center`, so every
 * `circle(50% at left bottom)` / `circle(50% at right 40px bottom 40px)`
 * in the corpus silently lost its whole `at` clause. Rather than mirror
 * the axis logic a second time, the grammar lives here once.
 *
 * ObjectPositionPropertyParser has NOT yet been migrated onto this file
 * (it belongs to another lane this wave); the two implementations are
 * deliberately kept in agreement — see the PARITY NOTE on [parse].
 *
 * OUTPUT SHAPE. [ResolvedPosition] expresses each axis as an offset
 * measured inward from a named edge, because that is the only
 * representation that survives the `[left|right] <length-percentage>`
 * arm: `right 40px` is 40px in from the RIGHT edge and cannot be written
 * as a single left-origin length without knowing the box width (which the
 * converter, by contract, does not know). Forms that ARE expressible from
 * the default origin are normalised to it ([Edge.LEFT] / [Edge.TOP] with
 * a percentage), so a consumer that ignores the edge still reads every
 * keyword-only and left/top-anchored value correctly, and only the
 * genuinely right/bottom-anchored ones need edge awareness.
 */
object PositionParser {

    /** The four `<position>` edges an axis can be measured from. */
    enum class Edge { LEFT, RIGHT, TOP, BOTTOM }

    /** One resolved axis: `offset` measured inward from `edge`. */
    data class Axis(val edge: Edge, val offset: IRLength)

    /** A fully resolved `<position>`: one [Axis] per dimension. */
    data class ResolvedPosition(val x: Axis, val y: Axis)

    /** The five `<position>` keywords. Kept separate from [Edge] because
     *  `center` is not an edge — it normalises to 50% from whichever
     *  default edge the axis it lands on uses. */
    private enum class Keyword { LEFT, RIGHT, TOP, BOTTOM, CENTER }

    /** Which axis a component can name. `center` and bare lengths are
     *  free to serve either. */
    private enum class Which { HORIZONTAL, VERTICAL, EITHER }

    /**
     * One parsed component: an edge-anchored offset, or a bare
     * `<length-percentage>` (edge == null → the axis' default edge).
     *
     * `keyword` distinguishes the two because the unordered third arm
     * admits ONLY keyword-derived components — a bare length there is a
     * syntax error even though it is legal in the ordered second arm.
     */
    private data class Part(
        val edge: Edge?,
        val offset: IRLength,
        val which: Which,
        val keyword: Boolean,
    )

    /**
     * Parse a `<position>` value. Returns null for anything the grammar
     * rejects (`top 10px`, `left right`, five components, an unparseable
     * unit) so callers fall back to their own "value not understood"
     * behaviour instead of emitting a plausible-looking guess
     * (CLAUDE.md: no silent fallthroughs).
     *
     * PARITY NOTE: the arm dispatch, the swap predicate and the final
     * axis validation below are the same three rules
     * `ObjectPositionPropertyParser.resolve / twoComponents /
     * unorderedArm / assign` implement. If one changes, change both.
     */
    fun parse(value: String): ResolvedPosition? {
        val tokens = value.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        // The grammar admits one to four components; anything else is a
        // syntax error, and WHICH ARM APPLIES IS DECIDED BY TOKEN COUNT.
        // That is the whole subtlety: `left 30%` is TWO tokens, so it is
        // the ORDERED second arm (x=left, y=30%) — NOT `left` carrying a
        // 30% offset, which would centre the vertical axis and put the
        // point somewhere else entirely.
        return when (tokens.size) {
            1 -> oneComponent(tokens[0])
            2 -> twoComponents(tokens[0], tokens[1])
            3, 4 -> unorderedArm(tokens)
            else -> null
        }
    }

    /** Arm 1: a single component. The named axis takes the value and the
     *  other centres, which is why a lone `top` is `center top` and not
     *  `top center`. */
    private fun oneComponent(token: String): ResolvedPosition? {
        val part = simple(token) ?: return null
        return if (part.which == Which.VERTICAL) {
            ResolvedPosition(centerAxis(Edge.LEFT), axis(part, Edge.TOP))
        } else {
            // A bare <length-percentage>, `center`, or an explicit
            // left/right is the HORIZONTAL position.
            ResolvedPosition(axis(part, Edge.LEFT), centerAxis(Edge.TOP))
        }
    }

    /**
     * Arms 2 and 3 at two tokens.
     *
     * Two KEYWORDS are unordered (arm 3 matches with both offsets
     * absent), so `top right` is x=right, y=top. Anything involving a
     * bare `<length-percentage>` can only be the ORDERED arm 2, so
     * `10px top` is valid while `top 10px` is not a position at all.
     */
    private fun twoComponents(first: String, second: String): ResolvedPosition? {
        val a = simple(first) ?: return null
        val b = simple(second) ?: return null
        // Reorder ONLY when the unordered arm can match, i.e. both sides
        // are keywords. `a.which == VERTICAL` is the trigger;
        // `b.which == HORIZONTAL` catches `center left`, whose first
        // component is axis-free.
        val swap = a.keyword && b.keyword && (a.which == Which.VERTICAL || b.which == Which.HORIZONTAL)
        return assign(if (swap) b else a, if (swap) a else b)
    }

    /**
     * Arm 3 at three or four tokens:
     * `[center | [left|right] <lp>?] && [center | [top|bottom] <lp>?]`.
     *
     * Each keyword absorbs a following `<length-percentage>` as ITS
     * offset (`right 40px` = 40px in from the right edge), which is what
     * makes `bottom 40px right 40px` mean y=(bottom,40px), x=(right,40px)
     * rather than a positional reading that swaps both axes.
     */
    private fun unorderedArm(tokens: List<String>): ResolvedPosition? {
        val parts = mutableListOf<Part>()
        var i = 0
        while (i < tokens.size) {
            val keyword = keywordOf(tokens[i]) ?: return null   // no bare <lp> in this arm
            val offset = tokens.getOrNull(i + 1)?.let { lengthPercentage(it) }
            if (offset == null) {
                parts.add(bareKeyword(keyword))
                i++
                continue
            }
            // `center` takes no offset, so a length after it is a syntax
            // error — not an offset to quietly attach to the wrong side.
            if (keyword == Keyword.CENTER) return null
            parts.add(Part(edgeOf(keyword), offset, whichOf(keyword), true))
            i += 2
        }
        if (parts.size != 2) return null
        val (a, b) = parts
        // Same unordered assignment as the two-keyword case above.
        val swap = a.which == Which.VERTICAL || b.which == Which.HORIZONTAL
        return assign(if (swap) b else a, if (swap) a else b)
    }

    /**
     * Final validation of an axis assignment: a component that can only
     * name the vertical axis may not be the horizontal position, and vice
     * versa. This rejects `top 10px`, `left right` and `top bottom`
     * instead of emitting a swapped guess.
     */
    private fun assign(x: Part, y: Part): ResolvedPosition? {
        if (x.which == Which.VERTICAL || y.which == Which.HORIZONTAL) return null
        return ResolvedPosition(axis(x, Edge.LEFT), axis(y, Edge.TOP))
    }

    /** Materialise a [Part] on the axis whose default edge is [fallback]
     *  (LEFT for x, TOP for y). Bare lengths and `center` carry no edge
     *  of their own and are always measured from that default. */
    private fun axis(part: Part, fallback: Edge): Axis = Axis(part.edge ?: fallback, part.offset)

    /** `center` on the axis whose default edge is [edge] — 50% in. */
    private fun centerAxis(edge: Edge): Axis = Axis(edge, percent(50.0))

    /** One un-grouped component: a position keyword or a bare
     *  `<length-percentage>`. Null when the token is neither. */
    private fun simple(token: String): Part? {
        val keyword = keywordOf(token)
        if (keyword != null) return bareKeyword(keyword)
        val lp = lengthPercentage(token) ?: return null
        // A bare length is axis-free and always measured from the axis'
        // default edge.
        return Part(null, lp, Which.EITHER, false)
    }

    /**
     * A keyword with NO offset. Normalised to the DEFAULT origin wherever
     * that is lossless, so downstream consumers that ignore the edge
     * field still land in the right place: `right` becomes 100% from the
     * LEFT edge, `bottom` 100% from the TOP edge, `center` 50%. Only the
     * keyword-plus-offset forms (handled in [unorderedArm]) genuinely
     * need a RIGHT / BOTTOM edge.
     */
    private fun bareKeyword(keyword: Keyword): Part = when (keyword) {
        Keyword.LEFT -> Part(Edge.LEFT, percent(0.0), Which.HORIZONTAL, true)
        Keyword.RIGHT -> Part(Edge.LEFT, percent(100.0), Which.HORIZONTAL, true)
        Keyword.TOP -> Part(Edge.TOP, percent(0.0), Which.VERTICAL, true)
        Keyword.BOTTOM -> Part(Edge.TOP, percent(100.0), Which.VERTICAL, true)
        Keyword.CENTER -> Part(null, percent(50.0), Which.EITHER, true)
    }

    /** Token → keyword, or null when the token is not a position keyword
     *  (which is a different answer from `center`, hence the enum). */
    private fun keywordOf(token: String): Keyword? = when (token) {
        "left" -> Keyword.LEFT
        "right" -> Keyword.RIGHT
        "top" -> Keyword.TOP
        "bottom" -> Keyword.BOTTOM
        "center" -> Keyword.CENTER
        else -> null
    }

    /** The edge a keyword anchors to; `center` anchors to neither and
     *  inherits the axis default via [axis]. */
    private fun edgeOf(keyword: Keyword): Edge? = when (keyword) {
        Keyword.LEFT -> Edge.LEFT
        Keyword.RIGHT -> Edge.RIGHT
        Keyword.TOP -> Edge.TOP
        Keyword.BOTTOM -> Edge.BOTTOM
        Keyword.CENTER -> null
    }

    /** Which axis a keyword constrains. */
    private fun whichOf(keyword: Keyword): Which = when (keyword) {
        Keyword.LEFT, Keyword.RIGHT -> Which.HORIZONTAL
        Keyword.TOP, Keyword.BOTTOM -> Which.VERTICAL
        Keyword.CENTER -> Which.EITHER
    }

    /** `<length-percentage>` component. Percentages keep the PERCENT unit
     *  because they resolve against the reference box, which only the
     *  runtime knows (CLAUDE.md: null / unresolved means runtime-dependent). */
    private fun lengthPercentage(token: String): IRLength? = LengthParser.parse(token.trim())

    /** Shorthand for a percentage IRLength. */
    private fun percent(v: Double) = IRLength.fromRelative(v, IRLength.LengthUnit.PERCENT)
}
