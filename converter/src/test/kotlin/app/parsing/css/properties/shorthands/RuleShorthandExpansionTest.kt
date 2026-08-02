package app.parsing.css.properties.shorthands

// Wave-24 skeptic pins for the `column-rule` / `row-rule` shorthands.
//
// Three defects, all reproduced live through `:converter:run` before the fix
// and all silent (no Generic, no log line):
//   1. `<line-width>` KEYWORDS (thin | medium | thick) were plain alpha
//      idents, so the "any remaining ident is a named colour" branch claimed
//      them. `row-rule: thick` emitted RowRuleColor{original:"thick"} which
//      the web engine rendered as `row-rule-color: transparent`, and
//      `row-rule: thin solid red` lost its width entirely.
//   2. CSS Color 4/5 functions were dropped — the colour branch only tested
//      the `#`/`rgb`/`hsl` prefixes, so `row-rule: 2px solid oklch(...)`
//      silently produced no colour longhand.
//   3. A `<gap-rule-list>` ("2px solid hotpink, 1px dashed grey" — present
//      twice in the wpt css-gaps corpus) collapsed to whichever rule came
//      last instead of staying unmapped.
//
// Both axes are asserted together: the expanders share one classifier
// precisely so they cannot drift.

import kotlin.test.Test
import kotlin.test.assertEquals

class RuleShorthandExpansionTest {

    // The two expanders paired with their longhand prefix + shorthand name.
    private val axes = listOf(
        Triple(RowRuleExpander as ShorthandExpander, "row-rule", "row-rule"),
        Triple(ColumnRuleExpander as ShorthandExpander, "column-rule", "column-rule")
    )

    @Test
    fun `line-width keywords land on the width longhand, not colour`() {
        axes.forEach { (expander, p, _) ->
            listOf("thin", "medium", "thick").forEach { kw ->
                assertEquals(
                    mapOf("$p-width" to kw),
                    expander.expand(kw),
                    "$p: $kw"
                )
            }
        }
    }

    @Test
    fun `keyword width survives alongside style and colour`() {
        axes.forEach { (expander, p, _) ->
            assertEquals(
                mapOf("$p-width" to "thin", "$p-style" to "solid", "$p-color" to "red"),
                expander.expand("thin solid red"),
                "$p: thin solid red"
            )
            // The exact declaration the wpt css-variables corpus carries.
            assertEquals(
                mapOf("$p-width" to "medium", "$p-style" to "solid", "$p-color" to "green"),
                expander.expand("medium solid green"),
                "$p: medium solid green"
            )
        }
    }

    @Test
    fun `CSS Color 4-5 functions reach the colour longhand`() {
        val fns = listOf(
            "oklch(0.7 0.15 200)", "oklab(0.5 0.1 0.1)", "lab(50% 40 59)",
            "lch(50% 40 60)", "hwb(200 30% 20%)",
            "color(display-p3 1 0 0)", "color-mix(in srgb, red, blue)"
        )
        axes.forEach { (expander, p, _) ->
            fns.forEach { fn ->
                assertEquals(
                    mapOf("$p-width" to "2px", "$p-style" to "solid", "$p-color" to fn),
                    expander.expand("2px solid $fn"),
                    "$p: 2px solid $fn"
                )
            }
        }
    }

    @Test
    fun `a gap-rule-list stays unexpanded so it records as unmapped`() {
        axes.forEach { (expander, _, self) ->
            // Handing the shorthand name back means PropertiesParser finds no
            // longhand parser and emits GenericProperty(_unmapped: true).
            val list = "2px solid hotpink, 1px dashed grey"
            assertEquals(mapOf(self to list), expander.expand(list), "$self: $list")
            val repeated = "repeat(5, 100px)"
            assertEquals(mapOf(self to repeated), expander.expand(repeated), "$self: $repeated")
        }
    }

    @Test
    fun `rgba commas are not mistaken for a rule-list separator`() {
        axes.forEach { (expander, p, _) ->
            // The comma guard counts paren depth, so functional-notation
            // argument commas keep the single-rule fast path.
            assertEquals(
                mapOf("$p-width" to "5px", "$p-style" to "solid",
                      "$p-color" to "rgba(255, 0, 0, 0.5)"),
                expander.expand("5px solid rgba(255, 0, 0, 0.5)"),
                "$p rgba"
            )
        }
    }

    @Test
    fun `pre-existing orderings and line-styles are unchanged`() {
        axes.forEach { (expander, p, _) ->
            val expected = mapOf(
                "$p-width" to "5px", "$p-style" to "solid", "$p-color" to "gold"
            )
            // Order-independent per the `||` grammar.
            assertEquals(expected, expander.expand("5px solid gold"), "$p w-s-c")
            assertEquals(expected, expander.expand("gold solid 5px"), "$p c-s-w")
            assertEquals(expected, expander.expand("solid 5px gold"), "$p s-w-c")
            // `inset`/`outset` stay line-styles, never colours.
            assertEquals(mapOf("$p-style" to "inset"), expander.expand("inset"), "$p inset")
            assertEquals(mapOf("$p-style" to "outset"), expander.expand("outset"), "$p outset")
            assertEquals(mapOf("$p-style" to "none"), expander.expand("none"), "$p none")
            // Hex + the classic functional notations still work.
            assertEquals(
                mapOf("$p-color" to "#ff8800", "$p-style" to "dotted", "$p-width" to "3px"),
                expander.expand("#ff8800 dotted 3px"), "$p hex"
            )
        }
    }

    @Test
    fun `fractional and signed lengths are recognised as widths`() {
        axes.forEach { (expander, p, _) ->
            // `.5px` is legal CSS; the old regex required a leading digit and
            // dropped it silently.
            assertEquals(
                mapOf("$p-width" to ".5px", "$p-style" to "solid"),
                expander.expand(".5px solid"), "$p .5px"
            )
            assertEquals(
                mapOf("$p-width" to "0", "$p-style" to "solid"),
                expander.expand("0 solid"), "$p 0"
            )
        }
    }

    @Test
    fun `the two axes classify every probe token identically`() {
        // The gap-decorations family is specified as row/column twins, so the
        // ONLY difference between the two outputs may be the prefix.
        val probes = listOf(
            "5px solid gold", "thin solid red", "medium", "thick dashed",
            "2px solid oklch(0.7 0.15 200)", "#abc dotted",
            "5px solid rgba(255, 0, 0, 0.5)", "solid", "none",
            "2px solid hotpink, 1px dashed grey"
        )
        probes.forEach { probe ->
            val row = RowRuleExpander.expand(probe)
                .mapKeys { (k, _) -> k.removePrefix("row-") }
            val col = ColumnRuleExpander.expand(probe)
                .mapKeys { (k, _) -> k.removePrefix("column-") }
            assertEquals(col, row, "axis drift on: $probe")
        }
    }
}
