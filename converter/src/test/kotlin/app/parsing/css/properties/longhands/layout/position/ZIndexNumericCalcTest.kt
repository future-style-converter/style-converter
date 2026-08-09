package app.parsing.css.properties.longhands.layout.position

// Regression suite for the wave-39 lane A5 unitless-calc() fold on z-index.
//
// ROOT CAUSE PINNED HERE: css-values-4 §10 makes calc() legal wherever an
// <integer> is expected and §10.11 resolves a calculation of consistent type
// at computed-value time. A UNITLESS arithmetic expression has no context to
// wait for — no font size, no percentage basis, no viewport — so leaving it
// unresolved violated the IR contract's rule that `null` means genuinely
// runtime-dependent (schema/spec/02-values.md).
//
// MEASURED: WPT css/css-values/calc-positive-fraction-001.html paints a green
// square with `z-index: calc(3 / 2)` over a red one with `z-index: 2` (the
// halfway value rounds toward +∞, and the green box is later in tree order).
// With the calc unresolved the green box fell back to `auto` and the capture
// was entirely RED against an all-green reference.
//
// Pinned invariants:
//   1. the fold happens and rounds per §10.11 (halfway → +∞, NOT truncation);
//   2. it is the WHOLE grammar — nesting, precedence, unary signs;
//   3. everything context-dependent is REFUSED and keeps the pre-existing
//      unresolved expression envelope byte-for-byte (units, %, var(), nested
//      functions, division by zero, malformed input);
//   4. the non-calc branches (auto / plain integer / global keyword) are
//      untouched.

import app.irmodels.properties.layout.position.ZIndex
import app.irmodels.properties.layout.position.ZIndexProperty
import app.parsing.css.properties.primitiveParsers.NumericCalcEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ZIndexNumericCalcTest {

    private fun z(css: String): ZIndex =
        assertIs<ZIndexProperty>(ZIndexPropertyParser.parse(css)).value

    @Test
    fun `the measured case folds to the spec integer`() {
        // calc(3 / 2) = 1.5 → 2 (§10.11 rounds halfway values toward +∞).
        val v = z("calc(3 / 2)")
        assertEquals(2, v.intValue)
        // The `original` channel reports it as a resolved integer, not as an
        // expression — that is what tells a runtime it may use the value.
        assertIs<ZIndex.ZIndexOriginal.Integer>(v.original)
    }

    @Test
    fun `rounding is halfway-toward-positive-infinity, never truncation`() {
        assertEquals(2, z("calc(1.5)").intValue)      // +0.5 → up
        assertEquals(0, z("calc(-0.5)").intValue)     // −0.5 → up (toward +∞)
        assertEquals(-1, z("calc(-1.4)").intValue)    // nearest
        assertEquals(1, z("calc(1.4)").intValue)      // truncation would agree
        assertEquals(2, z("calc(1.6)").intValue)      // truncation would say 1
    }

    @Test
    fun `the arithmetic grammar is honoured, not approximated`() {
        assertEquals(7, NumericCalcEvaluator.evaluate("calc(1 + 2 * 3)")?.toInt())
        assertEquals(9, NumericCalcEvaluator.evaluate("calc((1 + 2) * 3)")?.toInt())
        assertEquals(-2, NumericCalcEvaluator.evaluate("calc(-4 / 2)")?.toInt())
        assertEquals(1, NumericCalcEvaluator.evaluate("calc(10 - 3 * 3)")?.toInt())
        // The sibling-index() substitution the extractor leaves behind when its
        // product folder has nothing to fold: `calc(sibling-index())` → calc(2).
        assertEquals(2, z("calc(2)").intValue)
    }

    @Test
    fun `context-dependent values keep the unresolved expression envelope`() {
        // Invariant 3 — each of these must land on the pre-existing
        // ZIndex.fromExpression path with a null numeric value.
        for (css in listOf(
            "calc(2px)",              // a unit is not a <number>
            "calc(50%)",              // percentage needs a basis
            "calc(var(--z) + 1)",     // custom property
            "calc(sign(-1))",         // nested math function
            "calc(1 / 0)",            // §10.11: invalid at computed-value time
            "calc(1 2)",              // malformed — trailing garbage
            "calc((1 + 2)",           // unbalanced
            "calc()",                 // empty
        )) {
            val v = z(css)
            assertNull(v.intValue, "`$css` must stay unresolved")
            assertIs<ZIndex.ZIndexOriginal.Expression>(v.original, "`$css`")
        }
    }

    @Test
    fun `the evaluator refuses anything that is not a whole-value calc`() {
        assertNull(NumericCalcEvaluator.evaluate("3"))
        assertNull(NumericCalcEvaluator.evaluate("calc(1) calc(2)"))
        assertNull(NumericCalcEvaluator.evaluate("translate(calc(1))"))
        assertNull(NumericCalcEvaluator.evaluate("min(1, 2)"))
    }

    @Test
    fun `the non-calc branches are unchanged`() {
        // Invariant 4 — the fold runs before the `when`, so prove the `when`
        // still answers everything it used to.
        assertEquals(0, z("auto").intValue)
        assertIs<ZIndex.ZIndexOriginal.Auto>(z("auto").original)
        assertEquals(-1, z("-1").intValue)
        assertIs<ZIndex.ZIndexOriginal.Integer>(z("-1").original)
        assertIs<ZIndex.ZIndexOriginal.GlobalKeyword>(z("inherit").original)
        assertNull(ZIndexPropertyParser.parse("nonsense"))
    }
}
