package app.parsing.css.properties.shorthands

// Wave-50 lane B6 pins for the `text-decoration` shorthand (BACKLOG queue 9b:
// "text-decoration shorthand colour-function drop").
//
// TWO defects, both silent before this wave, both of the same shape the
// wave-24 RuleShorthandExpansionTest pinned for column-rule/row-rule — a
// token whose role the expander could not name fell through to (or was
// claimed by) the "any remaining ident is a named colour" net:
//
//   1. COLOUR FUNCTIONS WERE DROPPED. The colour arm tested only the
//      `#`/`rgb`/`hsl` prefixes, so `oklch()`, `lab()`, `lch()`, `oklab()`,
//      `hwb()`, `color()`, `color-mix()` and `light-dark()` matched nothing
//      (they carry parens, so the bare-ident net missed them too) and the
//      declaration produced NO text-decoration-color at all. Zero carriers
//      in the current WPT corpus — a correctness hole, not a score move.
//
//   2. `auto` / `from-font` WERE READ AS COLOURS. These are the keyword half
//      of `<'text-decoration-thickness'>` (css-text-decor-4 §2.4). `auto` is
//      a bare alpha ident, so the colour net claimed it and OVERWROTE an
//      already-parsed colour; `from-font`'s hyphen failed that regex, so it
//      was dropped in silence. This one HAS a carrier and costs visible ink:
//      wpt `css-text-decor/text-decoration-shorthands-001`
//      (`text-decoration: green underline auto`). Measured against the frozen
//      ref tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
//      white-black-ink-font-lh-imgpad-htmlpins/css-text-decor/
//      text-decoration-shorthands-001.png — the ref paints 48 px of pure
//      rgb(0,128,0) on row 85; wave49-final web / android / ios captures each
//      paint 0. The sibling `-002` (`green underline 100px`) already worked
//      and paints the ref's 4800 px exactly, which is what isolates the
//      keyword arm as the cause.
//
// Every payload below with a `wpt` comment is VERBATIM from
// tools/wpt/css/**; the rest are the value flavours
// TextDecorationLine/Style/ThicknessPropertyParser accept, so the expander
// and the longhands cannot drift.

import kotlin.test.Test
import kotlin.test.assertEquals

class TextDecorationShorthandExpansionTest {

    // ---- defect 2: the thickness keywords (the carrier) --------------------

    @Test
    fun `auto lands on thickness and leaves the declared colour alone`() {
        // wpt css-text-decor/text-decoration-shorthands-001.html, verbatim.
        // Before wave 50 this produced {…-color=auto, …-line=underline}: the
        // green was gone and the underline rendered currentColor-black.
        assertEquals(
            mapOf(
                "text-decoration-color" to "green",
                "text-decoration-thickness" to "auto",
                "text-decoration-line" to "underline"
            ),
            TextDecorationExpander.expand("green underline auto")
        )
    }

    @Test
    fun `from-font lands on thickness instead of vanishing`() {
        // `from-font` carries a hyphen, so the bare-ident colour net never
        // matched it — it was dropped with no longhand and no log line.
        assertEquals(
            mapOf(
                "text-decoration-color" to "green",
                "text-decoration-thickness" to "from-font",
                "text-decoration-line" to "underline"
            ),
            TextDecorationExpander.expand("green underline from-font")
        )
    }

    @Test
    fun `thickness keywords are matched case-insensitively`() {
        // css-values-4 §4.1: keywords are ASCII case-insensitive, and the
        // VERBATIM token is what reaches the longhand parser (which folds).
        assertEquals(
            mapOf("text-decoration-thickness" to "Auto"),
            TextDecorationExpander.expand("Auto")
        )
        assertEquals(
            mapOf("text-decoration-thickness" to "FROM-FONT"),
            TextDecorationExpander.expand("FROM-FONT")
        )
    }

    @Test
    fun `an explicit length thickness still works and keeps its colour`() {
        // wpt css-text-decor/text-decoration-shorthands-002.html, verbatim —
        // the sibling that already passed. Pinned so the keyword arm added
        // above cannot regress the numeric arm beside it.
        assertEquals(
            mapOf(
                "text-decoration-color" to "green",
                "text-decoration-line" to "underline",
                "text-decoration-thickness" to "100px"
            ),
            TextDecorationExpander.expand("green underline 100px")
        )
    }

    // ---- defect 1: every CSS Color 4/5 function ---------------------------

    @Test
    fun `colour functions reach the colour longhand`() {
        // No corpus carrier today (the WPT corpus's only functional
        // text-decoration colours are hex and `currentColor`); these are the
        // ColorSyntaxClassifier function set, asserted here so the shorthand
        // and the classifier stay one mechanism.
        val functions = listOf(
            "rgb(0, 128, 0)",
            "rgba(0, 128, 0, 0.5)",
            "hsl(120 100% 25%)",
            "hwb(120 0% 50%)",
            "lab(46% -48 39)",
            "lch(46% 62 140)",
            "oklab(0.52 -0.14 0.11)",
            "oklch(0.7 0.15 200)",
            "color(display-p3 0 0.5 0)",
            "color-mix(in oklch, green, blue)",
            "light-dark(green, lime)"
        )
        functions.forEach { fn ->
            assertEquals(
                mapOf("text-decoration-color" to fn, "text-decoration-line" to "underline"),
                TextDecorationExpander.expand("underline $fn"),
                "text-decoration: underline $fn"
            )
        }
    }

    @Test
    fun `an uppercase colour function is still a colour`() {
        // The old prefix test was case-SENSITIVE, so `RGB(...)` fell through
        // the same crack the Color 4/5 functions did.
        assertEquals(
            mapOf("text-decoration-color" to "RGB(0, 128, 0)", "text-decoration-line" to "underline"),
            TextDecorationExpander.expand("underline RGB(0, 128, 0)")
        )
    }

    // ---- the corpus declarations that must NOT move ------------------------

    @Test
    fun `the corpus declarations expand exactly as they did before wave 50`() {
        // Every one of these is verbatim from tools/wpt/css/** (the census
        // behind the "zero unintended change" claim in the wave-50 report).
        // Their expansion is unchanged by this wave; the pins exist so the
        // added branches are proven inert on the traffic that actually runs.
        val corpus = listOf(
            "underline" to mapOf("text-decoration-line" to "underline"),
            "none" to mapOf("text-decoration-line" to "none"),
            "green underline" to mapOf(
                "text-decoration-color" to "green", "text-decoration-line" to "underline"),
            "underline blue" to mapOf(
                "text-decoration-color" to "blue", "text-decoration-line" to "underline"),
            "underline overline line-through" to mapOf(
                "text-decoration-line" to "underline overline line-through"),
            "dotted red underline" to mapOf(
                "text-decoration-style" to "dotted", "text-decoration-color" to "red",
                "text-decoration-line" to "underline"),
            "2px black underline" to mapOf(
                "text-decoration-thickness" to "2px", "text-decoration-color" to "black",
                "text-decoration-line" to "underline"),
            "wavy underline overline green 5px" to mapOf(
                "text-decoration-style" to "wavy", "text-decoration-color" to "green",
                "text-decoration-thickness" to "5px",
                "text-decoration-line" to "underline overline"),
            "#E03838C0 wavy underline" to mapOf(
                "text-decoration-color" to "#E03838C0", "text-decoration-style" to "wavy",
                "text-decoration-line" to "underline"),
            "2px currentColor underline" to mapOf(
                "text-decoration-thickness" to "2px", "text-decoration-color" to "currentColor",
                "text-decoration-line" to "underline"),
            "0.25em currentColor solid underline" to mapOf(
                "text-decoration-thickness" to "0.25em",
                "text-decoration-color" to "currentColor",
                "text-decoration-style" to "solid",
                "text-decoration-line" to "underline"),
            "blink" to mapOf("text-decoration-line" to "blink"),
            "line-through wavy green" to mapOf(
                "text-decoration-line" to "line-through", "text-decoration-style" to "wavy",
                "text-decoration-color" to "green")
        )
        corpus.forEach { (decl, expected) ->
            assertEquals(expected, TextDecorationExpander.expand(decl), "text-decoration: $decl")
        }
    }

    @Test
    fun `a CSS-wide keyword shorthand is RECORDED, not endorsed`() {
        // KNOWN LIMITATION, pinned so a later wave that fixes it has to look
        // here. css-cascade-5 §7.3: a CSS-wide keyword as the WHOLE shorthand
        // value must set EVERY longhand to that keyword — line, style,
        // thickness and colour. This expander has no such guard, so the
        // keyword reaches only the colour arm and the other three longhands
        // are never written.
        //
        // THE HOLE IS WIDER THAN THE `inherit` ROW IT WAS FIRST PINNED WITH
        // (wave-50 skeptic S5, applied by lane F4). The routing is not a
        // property of `inherit`: ColorSyntaxClassifier.contextKeywords carries
        // ALL FIVE CSS-wide keywords — `inherit`, `initial`, `unset`,
        // `revert`, `revert-layer` — so every one of them is classified as
        // colour syntax and lands on text-decoration-color alone. All five are
        // asserted below in their CURRENT, WRONG expansion: this test RECORDS
        // the behaviour, it does not endorse it, and a §7.3 fix must come here
        // and change all five rows at once.
        //
        // `revert-layer` IS A WAVE-50 BEHAVIOUR CHANGE, and the only one in
        // this group. Before this wave the colour arm was the prefix test
        // `#`/`rgb`/`hsl` plus the bare-ident net `^[a-zA-Z]+$`: `inherit`,
        // `initial`, `unset` and `revert` are bare alpha idents and already
        // took the colour arm, but `revert-layer`'s HYPHEN failed that regex,
        // so it matched no branch at all and was dropped in silence. Routing
        // the expander through the shared classifier (lane B6) gave it the
        // colour arm instead. Wrong either way under §7.3 — but it is a NEW
        // wrong, not an inherited one, and a later fix must not read the row
        // below as pre-existing behaviour it may leave alone.
        //
        // CARRIERS, re-censused over tools/wpt/css/** at the pinned corpus
        // commit (every file type, not just .html — the four `inherit`
        // carriers are .xht): `text-decoration: inherit` 4 declarations in
        // CSS2/text/text-decoration-066/067/068/069.xht; `initial`, `unset`,
        // `revert` and `revert-layer` ZERO each. So only the `inherit` row can
        // cost a cell today; the other four are correctness rows.
        //
        // Fixing it belongs with the other shorthands
        // (FontShorthandGlobalKeywordTest is the precedent for the shape that
        // fix takes), not inside lane B6's blast radius.
        for (keyword in listOf("inherit", "initial", "unset", "revert", "revert-layer")) {
            assertEquals(
                mapOf("text-decoration-color" to keyword),
                TextDecorationExpander.expand(keyword),
                "text-decoration: $keyword"
            )
        }
    }
}
