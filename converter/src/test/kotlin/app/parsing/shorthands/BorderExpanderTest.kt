package app.parsing.shorthands

// Pins the `border` shorthand's component classification.
//
// The expander had TWO silent drops, both because parseBorderValue's `when`
// had no else and an unmatched token simply vanished:
//
//  1. The colour test was `startsWith("#") || startsWith("rgb") ||
//     startsWith("hsl") || ^[a-zA-Z]+$`, so every CSS Color 4/5 function —
//     oklch(), lab(), lch(), oklab(), hwb(), color(), color-mix() — matched
//     nothing and the border rendered with no colour at all.
//  2. The width pattern was
//     ^\d+\.?\d*(px|em|rem|%|pt|cm|mm|in|pc|ex|ch|vw|vh|vmin|vmax|fr)?$
//     which has no IGNORE_CASE, requires a leading digit, and omits several
//     css-values-4 units — so `2PX`, `.5px` and `3Q` all disappeared. Worse,
//     the <line-width> KEYWORDS (thin/medium/thick) are bare idents, so they
//     matched the colour test and were filed onto border-*-color: the width
//     was lost AND a colour was invented.
//
// Every case below was verified end-to-end through the converter before the
// fix (the colour ones produced no border-*-color; the width ones produced
// no border-*-width).

import app.parsing.css.properties.shorthands.BorderExpander
import app.parsing.css.properties.shorthands.BorderBottomExpander
import app.parsing.css.properties.shorthands.BorderLeftExpander
import app.parsing.css.properties.shorthands.BorderRightExpander
import app.parsing.css.properties.shorthands.BorderTopExpander
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BorderExpanderTest {

    private fun expand(v: String) = BorderExpander.expand(v)

    // ── Colour functions ────────────────────────────────────────────────

    @Test
    fun `css color 4 and 5 functions survive the shorthand`() {
        // One assertion per function family, because they were dropped as a
        // family — a single case could pass on a lucky prefix match.
        val cases = listOf(
            "oklch(0.7 0.15 200)",
            "lab(50% 40 30)",
            "lch(50% 40 30)",
            "oklab(0.7 0.1 0.1)",
            "hwb(200 20% 10%)",
            "color(display-p3 1 0 0)",
            "color-mix(in srgb, red, blue)"
        )
        for (c in cases) {
            val out = expand("2px solid $c")
            assertEquals(c, out["border-top-color"], "colour dropped for $c")
            // and the other two components must be unharmed
            assertEquals("2px", out["border-top-width"], "width lost alongside $c")
            assertEquals("solid", out["border-top-style"], "style lost alongside $c")
        }
    }

    @Test
    fun `commas inside a colour function do not split the token`() {
        // The tokenizer is paren-aware; this pins that the colour branch sees
        // the WHOLE function and not a fragment.
        val out = expand("1px solid rgba(255, 255, 255, 0.2)")
        assertEquals("rgba(255, 255, 255, 0.2)", out["border-top-color"])
    }

    // ── <line-width> ────────────────────────────────────────────────────

    @Test
    fun `line-width keywords are widths, not colours`() {
        // THE mis-filing bug: these are bare idents, so they used to match the
        // colour test. `border: thin solid` produced a border with no width
        // and a colour of "thin".
        for (kw in listOf("thin", "medium", "thick")) {
            val out = expand("$kw solid")
            assertEquals(kw, out["border-top-width"], "$kw must be the width")
            assertNull(out["border-top-color"], "$kw must not become a colour")
            assertEquals("solid", out["border-top-style"])
        }
    }

    @Test
    fun `lengths the old pattern dropped now survive`() {
        // Each of these produced NO border-*-width before the fix.
        val cases = mapOf(
            ".5px" to "leading dot — the old pattern required a leading digit",
            "2PX" to "uppercase unit — the old pattern had no IGNORE_CASE",
            "3Q" to "quarter-millimetre — absent from the old unit list",
            "1.5rem" to "decimal with a unit (regression guard)",
            "0" to "unitless zero"
        )
        for ((v, why) in cases) {
            val out = expand("$v solid red")
            assertEquals(v, out["border-top-width"], "width dropped: $why")
            assertEquals("red", out["border-top-color"], "colour disturbed by $v")
        }
    }

    @Test
    fun `calc is a width, not a colour function`() {
        // Both are `name(...)`; the branch order has to distinguish them.
        val out = expand("calc(1px + 2px) solid red")
        assertEquals("calc(1px + 2px)", out["border-top-width"])
        assertEquals("red", out["border-top-color"])
    }

    // ── Classification boundaries ───────────────────────────────────────

    @Test
    fun `none and hidden stay styles rather than named colours`() {
        // Both are bare idents and would be swallowed by the colour branch if
        // the style test did not come first.
        for (st in listOf("none", "hidden")) {
            val out = expand("2px $st")
            assertEquals(st, out["border-top-style"])
            assertNull(out["border-top-color"], "$st must not become a colour")
        }
    }

    @Test
    fun `order within the shorthand does not matter`() {
        // css-backgrounds-3 §4.4: the three components may appear in any order.
        val a = expand("2px solid red")
        val b = expand("red 2px solid")
        val c = expand("solid red 2px")
        for (out in listOf(a, b, c)) {
            assertEquals("2px", out["border-top-width"])
            assertEquals("solid", out["border-top-style"])
            assertEquals("red", out["border-top-color"])
        }
    }

    @Test
    fun `every component expands to all four sides`() {
        val out = expand("2px solid red")
        for (side in listOf("top", "right", "bottom", "left")) {
            assertEquals("2px", out["border-$side-width"])
            assertEquals("solid", out["border-$side-style"])
            assertEquals("red", out["border-$side-color"])
        }
    }

    @Test
    fun `an omitted component is simply absent`() {
        // The expander must not invent initial values — the cascade layer
        // owns that. `border: solid` sets style only.
        val out = expand("solid")
        assertEquals("solid", out["border-top-style"])
        assertNull(out["border-top-width"])
        assertNull(out["border-top-color"])
        assertTrue(out.keys.all { it.endsWith("-style") })
    }

    // ── The four per-side shorthands ────────────────────────────────────

    @Test
    fun `every side shorthand shares the fixed classification`() {
        // THE test that would have caught the duplication. `parseBorderValue`
        // was private to BorderExpander, and a file-level EXTENSION of the
        // same name carried a second, older copy; the four side objects bound
        // to the extension, so `border` was fixed and `border-top`/-right/
        // -bottom/-left silently were not. Testing only the `border` object
        // could never see that.
        val sides = listOf(
            Triple("top", BorderTopExpander as Any, "border-top"),
            Triple("right", BorderRightExpander as Any, "border-right"),
            Triple("bottom", BorderBottomExpander as Any, "border-bottom"),
            Triple("left", BorderLeftExpander as Any, "border-left")
        )
        for ((side, expander, _) in sides) {
            @Suppress("UNCHECKED_CAST")
            val out = (expander as app.parsing.css.properties.shorthands.ShorthandExpander)
                .expand("thick solid oklch(0.7 0.15 200)")
            assertEquals("thick", out["border-$side-width"],
                "line-width keyword lost on border-$side")
            assertEquals("solid", out["border-$side-style"])
            assertEquals("oklch(0.7 0.15 200)", out["border-$side-color"],
                "css-color-4 function lost on border-$side")
        }
    }

    @Test
    fun `side shorthands agree with the all-sides shorthand`() {
        // Any future divergence between the two code paths shows up here
        // rather than as a silent per-side regression.
        val all = BorderExpander.expand("2px dashed hwb(200 20% 10%)")
        val top = BorderTopExpander.expand("2px dashed hwb(200 20% 10%)")
        assertEquals(all["border-top-width"], top["border-top-width"])
        assertEquals(all["border-top-style"], top["border-top-style"])
        assertEquals(all["border-top-color"], top["border-top-color"])
    }
}
