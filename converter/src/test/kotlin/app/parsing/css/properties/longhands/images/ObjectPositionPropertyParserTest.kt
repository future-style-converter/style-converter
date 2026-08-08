package app.parsing.css.properties.longhands.images

// Regression suite for the wave-36 lane M1 <position> AXIS fix
// (css-images-3 §5.2 object-position, whose value is the css-values-4
// `<position>` grammar shared with background-position).
//
// ROOT CAUSE PINNED HERE: the old parser read the token stream POSITIONALLY
// — `parts[0] → x, parts[1] → y`, and the four-value `keyword offset keyword
// offset` form as x-first. The grammar's third arm is a `&&` (UNORDERED), so:
//
//   object-position: top right          means x = right, y = top
//   object-position: top 25% left 25%   means y = (top,25%), x = (left,25%)
//   object-position: top                means x = center, y = top
//
// All three are spellings the css-images WPT `object-fit-*` family uses —
// six of the seven boxes in every row — so six of every seven elements in
// 132 corpus tests reached the runtimes with the two axes SWAPPED, on top of
// the missing image the same lane delivers.
//
// Pinned invariants:
//   1. a lone vertical keyword fills the OTHER axis with center;
//   2. a keyword pair is reordered by axis, not by writing order;
//   3. the four-value form assigns each keyword+offset to ITS axis;
//   4. two bare lengths keep written order (x then y — nothing to reorder);
//   5. a value the grammar rejects (`top 10px`) degrades to Raw, which
//      re-emits verbatim and is dropped by the browser exactly as the
//      invalid declaration would be — never a silently swapped guess.

import app.irmodels.properties.images.ObjectPositionProperty
import app.irmodels.properties.images.ObjectPositionProperty.PositionKeyword
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertIs

class ObjectPositionPropertyParserTest {

    private fun parse(css: String): ObjectPositionProperty.Position {
        val p = ObjectPositionPropertyParser.parse(css)
        assertNotNull(p, "parser returned null for `$css` — it must always emit a property")
        return (p as ObjectPositionProperty).position
    }

    /** The keyword an axis carries, or null when it is not a bare keyword. */
    private fun keyword(v: ObjectPositionProperty.ObjectPositionValue): PositionKeyword? =
        (v as? ObjectPositionProperty.ObjectPositionValue.Keyword)?.value

    @Test
    fun `a lone horizontal keyword centres the other axis`() {
        val p = parse("left")
        assertEquals(PositionKeyword.LEFT, keyword(p.x))
        assertEquals(PositionKeyword.CENTER, keyword(p.y))
    }

    @Test
    fun `a lone VERTICAL keyword fills the Y axis, not the X axis`() {
        // §5.2: `top` alone is `center top`. The old parser produced
        // `top center` — a horizontal position named by a vertical keyword.
        val p = parse("top")
        assertEquals(PositionKeyword.CENTER, keyword(p.x))
        assertEquals(PositionKeyword.TOP, keyword(p.y))
    }

    @Test
    fun `a vertical-first keyword pair is reordered by AXIS`() {
        // The exact `.tr` class of object-fit-fill-png-001i and its 40 siblings.
        val tr = parse("top right")
        assertEquals(PositionKeyword.RIGHT, keyword(tr.x))
        assertEquals(PositionKeyword.TOP, keyword(tr.y))

        val bl = parse("bottom left")
        assertEquals(PositionKeyword.LEFT, keyword(bl.x))
        assertEquals(PositionKeyword.BOTTOM, keyword(bl.y))
    }

    @Test
    fun `a horizontal-first keyword pair keeps its order`() {
        val p = parse("right bottom")
        assertEquals(PositionKeyword.RIGHT, keyword(p.x))
        assertEquals(PositionKeyword.BOTTOM, keyword(p.y))
    }

    @Test
    fun `center pairs with a keyword on either side`() {
        val a = parse("center top")
        assertEquals(PositionKeyword.CENTER, keyword(a.x))
        assertEquals(PositionKeyword.TOP, keyword(a.y))
        val b = parse("top center")
        assertEquals(PositionKeyword.CENTER, keyword(b.x))
        assertEquals(PositionKeyword.TOP, keyword(b.y))
    }

    @Test
    fun `the four-value form assigns each keyword-offset to its own axis`() {
        // `.tl` of the object-fit family: vertical component written FIRST.
        val p = parse("top 25% left 25%")
        val x = assertIs<ObjectPositionProperty.ObjectPositionValue.KeywordOffset>(p.x)
        val y = assertIs<ObjectPositionProperty.ObjectPositionValue.KeywordOffset>(p.y)
        assertEquals(PositionKeyword.LEFT, x.keyword)
        assertEquals(PositionKeyword.TOP, y.keyword)
    }

    @Test
    fun `the four-value form keeps each offset with ITS keyword`() {
        // `.br`: `bottom 1px right 2px` — swapping the axes without swapping
        // the offsets would have been a second, quieter bug.
        val p = parse("bottom 1px right 2px")
        val x = assertIs<ObjectPositionProperty.ObjectPositionValue.KeywordOffset>(p.x)
        val y = assertIs<ObjectPositionProperty.ObjectPositionValue.KeywordOffset>(p.y)
        assertEquals(PositionKeyword.RIGHT, x.keyword)
        assertEquals(2.0, x.offset.value)
        assertEquals(PositionKeyword.BOTTOM, y.keyword)
        assertEquals(1.0, y.offset.value)
    }

    @Test
    fun `the three-value form pairs the offset with the keyword it follows`() {
        // `left 10px top` — one component carries an offset, the other does not.
        val p = parse("left 10px top")
        val x = assertIs<ObjectPositionProperty.ObjectPositionValue.KeywordOffset>(p.x)
        assertEquals(PositionKeyword.LEFT, x.keyword)
        assertEquals(10.0, x.offset.value)
        assertEquals(PositionKeyword.TOP, keyword(p.y))
    }

    @Test
    fun `two bare lengths keep written order — x then y`() {
        val p = parse("10px 20px")
        val x = assertIs<ObjectPositionProperty.ObjectPositionValue.LengthValue>(p.x)
        val y = assertIs<ObjectPositionProperty.ObjectPositionValue.LengthValue>(p.y)
        assertEquals(10.0, x.length.value)
        assertEquals(20.0, y.length.value)
    }

    @Test
    fun `a bare percentage pair keeps written order`() {
        val p = parse("25% 75%")
        assertEquals(25.0, assertIs<ObjectPositionProperty.ObjectPositionValue.PercentageValue>(p.x).percentage.value)
        assertEquals(75.0, assertIs<ObjectPositionProperty.ObjectPositionValue.PercentageValue>(p.y).percentage.value)
    }

    @Test
    fun `a keyword and a length pair on their own axes`() {
        // §5.2 second arm: `[left|center|right|<lp>] [top|center|bottom|<lp>]`.
        val p = parse("left 30%")
        assertEquals(PositionKeyword.LEFT, keyword(p.x))
        assertEquals(30.0, assertIs<ObjectPositionProperty.ObjectPositionValue.PercentageValue>(p.y).percentage.value)
    }

    @Test
    fun `an ungrammatical value degrades to Raw, never to a swapped guess`() {
        // `top 10px` in the two-value form is invalid — `top` cannot be the
        // horizontal position. Raw re-emits verbatim, and the browser drops
        // it for the same reason, so the pipeline and the reference agree.
        val p = parse("top 10px")
        assertIs<ObjectPositionProperty.ObjectPositionValue.Raw>(p.x)
        assertIs<ObjectPositionProperty.ObjectPositionValue.Raw>(p.y)
        // `center` takes no offset (§5.2) — same treatment.
        assertIs<ObjectPositionProperty.ObjectPositionValue.Raw>(parse("center 10px top").x)
        // Five components is outside the one-to-four grammar.
        assertIs<ObjectPositionProperty.ObjectPositionValue.Raw>(parse("left 1px top 2px right").x)
        assertIs<ObjectPositionProperty.ObjectPositionValue.Raw>(parse("wat").x)
    }

    @Test
    fun `global keywords and expressions still ride the whole-value arms`() {
        assertEquals(
            "inherit",
            assertIs<ObjectPositionProperty.ObjectPositionValue.GlobalKeyword>(parse("inherit").x).value,
        )
        assertEquals(
            "var(--p)",
            assertIs<ObjectPositionProperty.ObjectPositionValue.Raw>(parse("var(--p)").x).value,
        )
    }
}
