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
 * WAVE 38 LANE N4 — THE PARITY NOTE IS RETIRED. Until this wave
 * `ObjectPositionPropertyParser` carried its own copy of the arm
 * dispatch, the swap predicate and the axis validation, kept in
 * agreement with this file by a comment asking the next editor to change
 * both. That is not a contract, it is a hope: the two copies had already
 * been written twice and the whole reason this file exists is that the
 * first duplication shipped a two-axis swap into 132 corpus tests.
 * `object-position` now delegates here, so there is exactly ONE
 * implementation of the grammar in the converter and nothing left to
 * keep in sync.
 *
 * TWO OUTPUT SHAPES, ONE GRAMMAR. Callers want the parse result in one
 * of two forms, so the arm dispatch ([parts]) is shared and only the
 * final projection differs:
 *
 *  - [parse] → [ResolvedPosition]: each axis as an offset measured
 *    inward from a named edge. That is the only representation that
 *    survives the `[left|right] <length-percentage>` arm — `right 40px`
 *    is 40px in from the RIGHT edge and cannot be written as a single
 *    left-origin length without knowing the box width, which the
 *    converter by contract does not know. Forms that ARE expressible
 *    from the default origin are normalised to it ([Edge.LEFT] /
 *    [Edge.TOP] with a percentage), so a consumer that ignores the edge
 *    still reads every keyword-only and left/top-anchored value
 *    correctly. `clip-path` uses this.
 *
 *  - [parseAuthored] → [AuthoredPosition]: each axis as the author
 *    WROTE it — a keyword, a keyword plus offset, or a bare
 *    `<length-percentage>` — with only the axis assignment resolved.
 *    `object-position` uses this because its IR
 *    (`ObjectPositionProperty.ObjectPositionValue`) has a distinct wire
 *    variant per authored form: normalising `left` to "0% from the left
 *    edge" would turn a `{"keyword":"LEFT"}` leaf into a
 *    `{"percentage":0}` one, which is a wire-shape change
 *    (schema/spec/05-versioning.md) and not this lane's business.
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
     *  default edge the axis it lands on uses. Public because
     *  [AuthoredComponent] hands it to callers that re-emit the keyword
     *  itself rather than an offset. */
    enum class Keyword { LEFT, RIGHT, TOP, BOTTOM, CENTER }

    /**
     * One axis in AUTHORED shape. Exactly three inhabitants, matching the
     * three things the grammar lets a component be:
     *
     *   keyword != null, offset == null  — a bare keyword (`left`)
     *   keyword != null, offset != null  — keyword + offset (`right 2px`)
     *   keyword == null, offset != null  — a bare `<length-percentage>`
     *
     * (`keyword == null && offset == null` is unreachable — [simple] and
     * [unorderedArm] only ever build the three above.)
     */
    data class AuthoredComponent(val keyword: Keyword?, val offset: IRLength?)

    /** A `<position>` in authored shape: one [AuthoredComponent] per axis,
     *  already assigned to the right dimension by the grammar. */
    data class AuthoredPosition(val x: AuthoredComponent, val y: AuthoredComponent)

    /** Which axis a component can name. `center` and bare lengths are
     *  free to serve either. */
    private enum class Which { HORIZONTAL, VERTICAL, EITHER }

    /**
     * One parsed component, held in AUTHORED shape ([AuthoredComponent]'s
     * three inhabitants) plus the axis it is allowed to name. Both public
     * projections are computed from this, which is what makes them two
     * views of one parse rather than two parsers.
     *
     * `keyword == null` (a bare `<length-percentage>`) is also what tells
     * the unordered third arm to reject the component: that arm admits
     * ONLY keyword-derived components, even though a bare length is legal
     * in the ordered second arm.
     */
    private data class Part(val keyword: Keyword?, val offset: IRLength?)

    /** The axis a component may occupy — a bare length is free to serve
     *  either. An extension on the object rather than a member of [Part]
     *  so the nested data class stays a plain value with no reach into
     *  the enclosing object's private helpers. */
    private val Part.which: Which get() = keyword?.let { whichOf(it) } ?: Which.EITHER

    /**
     * Parse a `<position>` into edge-anchored axes. Returns null for
     * anything the grammar rejects (`top 10px`, `left right`, five
     * components, an unparseable unit) so callers fall back to their own
     * "value not understood" behaviour instead of emitting a
     * plausible-looking guess (CLAUDE.md: no silent fallthroughs).
     */
    fun parse(value: String): ResolvedPosition? {
        val (x, y) = parts(value) ?: return null
        // LEFT / TOP are the default origins the spec measures each axis
        // from when the component carries no edge of its own.
        return ResolvedPosition(axis(x, Edge.LEFT), axis(y, Edge.TOP))
    }

    /**
     * Parse a `<position>` into authored components. Same grammar, same
     * rejections as [parse] — only the projection differs (see the class
     * doc's TWO OUTPUT SHAPES note).
     */
    fun parseAuthored(value: String): AuthoredPosition? {
        val (x, y) = parts(value) ?: return null
        return AuthoredPosition(AuthoredComponent(x.keyword, x.offset), AuthoredComponent(y.keyword, y.offset))
    }

    /**
     * THE GRAMMAR: tokenize, pick the arm, assign the axes. Returns the
     * (x, y) pair of parts, or null when the value is not a `<position>`.
     *
     * WHICH ARM APPLIES IS DECIDED BY TOKEN COUNT, and that is the whole
     * subtlety: `left 30%` is TWO tokens, so it is the ORDERED second arm
     * (x=left, y=30%) — NOT `left` carrying a 30% offset, which would
     * centre the vertical axis and put the point somewhere else entirely.
     */
    private fun parts(value: String): Pair<Part, Part>? {
        // Keywords and units are ASCII case-insensitive (CSS Syntax L3
        // §4.3); a `<position>` has no case-sensitive payload, so lowering
        // the whole value up front is lossless here.
        val tokens = value.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when (tokens.size) {
            1 -> oneComponent(tokens[0])
            2 -> twoComponents(tokens[0], tokens[1])
            3, 4 -> unorderedArm(tokens)
            else -> null   // outside the one-to-four grammar
        }
    }

    /** Arm 1: a single component. The named axis takes the value and the
     *  other centres, which is why a lone `top` is `center top` and not
     *  `top center`. */
    private fun oneComponent(token: String): Pair<Part, Part>? {
        val part = simple(token) ?: return null
        val center = Part(Keyword.CENTER, null)
        return if (part.which == Which.VERTICAL) {
            center to part
        } else {
            // A bare <length-percentage>, `center`, or an explicit
            // left/right is the HORIZONTAL position.
            part to center
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
    private fun twoComponents(first: String, second: String): Pair<Part, Part>? {
        val a = simple(first) ?: return null
        val b = simple(second) ?: return null
        // Reorder ONLY when the unordered arm can match, i.e. both sides
        // are keywords. `a.which == VERTICAL` is the trigger;
        // `b.which == HORIZONTAL` catches `center left`, whose first
        // component is axis-free.
        val swap = a.keyword != null && b.keyword != null &&
            (a.which == Which.VERTICAL || b.which == Which.HORIZONTAL)
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
    private fun unorderedArm(tokens: List<String>): Pair<Part, Part>? {
        val parsed = mutableListOf<Part>()
        var i = 0
        while (i < tokens.size) {
            val keyword = keywordOf(tokens[i]) ?: return null   // no bare <lp> in this arm
            val offset = tokens.getOrNull(i + 1)?.let { lengthPercentage(it) }
            if (offset == null) {
                parsed.add(Part(keyword, null))
                i++
                continue
            }
            // `center` takes no offset, so a length after it is a syntax
            // error — not an offset to quietly attach to the wrong side.
            if (keyword == Keyword.CENTER) return null
            parsed.add(Part(keyword, offset))
            i += 2
        }
        if (parsed.size != 2) return null
        val (a, b) = parsed
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
    private fun assign(x: Part, y: Part): Pair<Part, Part>? {
        if (x.which == Which.VERTICAL || y.which == Which.HORIZONTAL) return null
        return x to y
    }

    /**
     * Project one [Part] onto the axis whose DEFAULT edge is [fallback]
     * (LEFT for x, TOP for y).
     *
     * Keyword-only forms are normalised to that default origin wherever
     * it is lossless, so downstream consumers that ignore the edge field
     * still land in the right place: `right` becomes 100% from the LEFT
     * edge, `bottom` 100% from the TOP edge, `center` 50%. Only the
     * keyword-PLUS-offset forms genuinely need a RIGHT / BOTTOM edge,
     * because `right 40px` cannot be restated from the left origin
     * without the box width.
     */
    private fun axis(part: Part, fallback: Edge): Axis {
        val keyword = part.keyword
            ?: return Axis(fallback, part.offset!!)          // bare <lp> — default origin
        val offset = part.offset
        // keyword + offset: the keyword's own edge is the anchor.
        // `center` never reaches here — unorderedArm rejects `center <lp>`.
        if (offset != null) return Axis(edgeOf(keyword) ?: fallback, offset)
        return when (keyword) {
            Keyword.LEFT -> Axis(Edge.LEFT, percent(0.0))
            Keyword.RIGHT -> Axis(Edge.LEFT, percent(100.0))
            Keyword.TOP -> Axis(Edge.TOP, percent(0.0))
            Keyword.BOTTOM -> Axis(Edge.TOP, percent(100.0))
            Keyword.CENTER -> Axis(fallback, percent(50.0))
        }
    }

    /** One un-grouped component: a position keyword or a bare
     *  `<length-percentage>`. Null when the token is neither. */
    private fun simple(token: String): Part? {
        val keyword = keywordOf(token)
        if (keyword != null) return Part(keyword, null)
        val lp = lengthPercentage(token) ?: return null
        return Part(null, lp)
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
