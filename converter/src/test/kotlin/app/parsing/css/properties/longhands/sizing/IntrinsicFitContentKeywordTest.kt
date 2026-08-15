package app.parsing.css.properties.longhands.sizing

// Regression suite for the wave-39 lane A5 bare `fit-content` keyword.
//
// ROOT CAUSE PINNED HERE: css-sizing-3 §5.1 defines the sizing value space as
//   auto | min-content | max-content | fit-content(<length-percentage>)
// with `fit-content` ALSO admitted as a bare keyword (equivalent to
// `fit-content(stretch)`). Every physical-axis parser recognised only the
// FUNCTIONAL form, so the single commonest intrinsic declaration in the WPT
// corpus — `width: fit-content`, 148 occurrences, plus 87 on height and 110
// across the four min/max longhands — fell through to LengthParser, returned
// null, and reached the runtimes inside the Generic degradation envelope,
// where all three dropped it. Their LOGICAL twins (inline-size, max-inline-
// size, min-block-size) had carried the branch all along, which is what made
// the hole invisible.
//
// Pinned invariants:
//   1. every one of the seven repaired longhands accepts the bare keyword and
//      types it as the SAME @SerialName("fit-content") variant the functional
//      form produces, with a null bound;
//   2. the value is case-insensitive (CSS keywords are ASCII case-insensitive);
//   3. the FUNCTIONAL form is untouched — it still carries its bounding length;
//   4. the neighbouring keywords the same `when` chain answers (auto /
//      min-content / max-content / none) are unchanged, so this is an
//      addition, not a re-ordering.

import app.irmodels.SizeValue
import app.irmodels.properties.spacing.HeightProperty
import app.irmodels.properties.spacing.MaxHeightProperty
import app.irmodels.properties.spacing.MaxWidthProperty
import app.irmodels.properties.spacing.MinHeightProperty
import app.irmodels.properties.spacing.MinWidthProperty
import app.irmodels.properties.spacing.WidthProperty
import app.irmodels.properties.sizing.BlockSizeProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IntrinsicFitContentKeywordTest {

    @Test
    fun `width accepts the bare fit-content keyword`() {
        val p = assertIs<WidthProperty>(WidthPropertyParser.parse("fit-content"))
        val v = assertIs<WidthProperty.WidthValue.FitContent>(p.width)
        // Bare keyword ⇒ no bounding length; the serializer emits the plain
        // "fit-content" primitive every runtime's decoder already reads.
        assertNull(v.maxSize)
    }

    @Test
    fun `width keeps the functional fit-content form intact`() {
        val p = assertIs<WidthProperty>(WidthPropertyParser.parse("fit-content(200px)"))
        val v = assertIs<WidthProperty.WidthValue.FitContent>(p.width)
        assertNotNull(v.maxSize, "the bound must survive — invariant 3")
        assertEquals(200.0, v.maxSize!!.value)
    }

    @Test
    fun `height accepts the bare fit-content keyword`() {
        val p = assertIs<HeightProperty>(HeightPropertyParser.parse("fit-content"))
        val v = assertIs<WidthProperty.WidthValue.FitContent>(p.height)
        assertNull(v.maxSize)
    }

    @Test
    fun `min-width and min-height accept the bare fit-content keyword`() {
        val w = assertIs<MinWidthProperty>(MinWidthPropertyParser.parse("fit-content"))
        assertNull(assertIs<MinWidthProperty.MinMaxValue.FitContent>(w.minWidth).maxSize)
        val h = assertIs<MinHeightProperty>(MinHeightPropertyParser.parse("fit-content"))
        assertNull(assertIs<MinWidthProperty.MinMaxValue.FitContent>(h.minHeight).maxSize)
    }

    @Test
    fun `max-width and max-height accept the bare fit-content keyword`() {
        val w = assertIs<MaxWidthProperty>(MaxWidthPropertyParser.parse("fit-content"))
        assertNull(assertIs<MaxWidthProperty.MaxValue.FitContent>(w.maxWidth).maxSize)
        val h = assertIs<MaxHeightProperty>(MaxHeightPropertyParser.parse("fit-content"))
        assertNull(assertIs<MaxWidthProperty.MaxValue.FitContent>(h.maxHeight).maxSize)
    }

    @Test
    fun `block-size accepts the bare fit-content keyword`() {
        val p = assertIs<BlockSizeProperty>(BlockSizePropertyParser.parse("fit-content"))
        assertNull(assertIs<SizeValue.FitContent>(p.size).length)
    }

    @Test
    fun `the keyword is ASCII case-insensitive`() {
        // CSS keywords are case-insensitive (css-values-4 §3.1); the parsers
        // lowercase before matching, so this must not depend on source casing.
        val p = assertIs<WidthProperty>(WidthPropertyParser.parse("Fit-Content"))
        assertIs<WidthProperty.WidthValue.FitContent>(p.width)
    }

    @Test
    fun `the neighbouring intrinsic keywords are unchanged`() {
        // Invariant 4 — the addition must not have displaced a sibling branch.
        assertIs<WidthProperty.WidthValue.Auto>(
            assertIs<WidthProperty>(WidthPropertyParser.parse("auto")).width)
        assertIs<WidthProperty.WidthValue.MinContent>(
            assertIs<WidthProperty>(WidthPropertyParser.parse("min-content")).width)
        assertIs<WidthProperty.WidthValue.MaxContent>(
            assertIs<WidthProperty>(WidthPropertyParser.parse("max-content")).width)
        assertIs<MaxWidthProperty.MaxValue.None>(
            assertIs<MaxWidthProperty>(MaxWidthPropertyParser.parse("none")).maxWidth)
    }

    @Test
    fun `calc-size routing — affine is typed, unreducible stays Generic`() {
        // SUPERSEDED PIN (wave 42, lane W3): this test froze the wave-39
        // state where EVERY calc-size() declined to the Generic envelope.
        // CalcSizeParser now types the affine `size` family so the NATIVE
        // runtimes can resolve it (Android dropped these outright —
        // calc-size-flex-001..006 painted no green at all); the typed value
        // carries the verbatim `original` so the web runtime still replays
        // the exact declaration into the browser (the fidelity this pin
        // was protecting). What must STILL decline is the unreducible
        // family — nested math functions over `size` — which keeps riding
        // the Generic envelope to the browser untyped.
        val typed = assertIs<WidthProperty>(
            WidthPropertyParser.parse("calc-size(auto, size + 80px)"))
        assertEquals("calc-size(auto, size + 80px)",
            assertIs<WidthProperty.WidthValue.CalcSize>(typed.width).original)
        // min(size, 100px) is not affine in `size` → Generic, as before.
        assertNull(WidthPropertyParser.parse("calc-size(auto, min(size, 100px))"))
    }
}
