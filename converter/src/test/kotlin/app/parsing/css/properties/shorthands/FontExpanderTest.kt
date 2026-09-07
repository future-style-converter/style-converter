package app.parsing.css.properties.shorthands

// Regression suite for the `font` shorthand's LINE-HEIGHT RESET
// (css-fonts-4 §4.3 — "the `font` shorthand … resets `line-height` to
// `normal`" for every declaration that omits the `/<line-height>` component).
//
// Root cause pinned here: the expander used to emit `line-height` ONLY when the
// author wrote the slash form, so `font: 92px Arial` produced FontSize 92 +
// FontFamily with NO LineHeight on the wire. Every runtime then fell back to
// its WPT line-box calibration (1.25 → a 115px line box at 92px: web
// index.html's `:where`-level `body.wpt-mode{line-height:1.25}`, Compose
// REF_DEFAULT_FONT_LINE_HEIGHT_RATIO, SwiftUI wptRefLineBoxPx) while Chromium's
// render of the SAME test resets to `normal` and lays Arial out on its natural
// metrics (hhea asc 1854 + desc 434 + gap 67 over 2048 upem = 1.1499em ≈
// 105.8px at 92px) — a ~9px per-line divergence that dominated
// css-text-decor/text-decoration-dotted-001 and -002.
//
// Pinned invariants:
//   1. size+family with no slash → `line-height: normal` IS emitted.
//   2. the explicit `/<line-height>` slash form still wins verbatim.
//   3. a value that does NOT parse as the size/family form emits NOTHING
//      (no synthesized longhand out of an invalid declaration — CSS2 §4.2).
//   4. system fonts (`caption`, `menu`, …) stay family-only — the platform
//      supplies their whole font description, including the line box.
//   5. global keywords (`inherit`, …) keep forwarding the keyword verbatim to
//      `line-height`, not the literal string "normal".
//   6. the reset rides along with the full leading-longhand form so it cannot
//      be lost when style/weight/stretch tokens precede the size.

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FontExpanderTest {

    // 1 — the WPT-corpus shape that motivated the fix
    // (css/css-text-decor/text-decoration-dotted-001.html, `div { font: 92px
    // Arial, sans-serif; }`). The shorthand must now RESET the inherited
    // line-height instead of leaving it in force.
    @Test
    fun `size and family reset line-height to normal`() {
        val out = FontExpander.expand("92px Arial, sans-serif")
        assertEquals("92px", out["font-size"], "font-size component unchanged")
        assertEquals("Arial, sans-serif", out["font-family"], "family component unchanged")
        assertEquals("normal", out["line-height"], "css-fonts-4 §4.3 reset")
    }

    // 2 — the explicit slash form is an AUTHORED value; the reset must never
    // clobber it (css-fonts-4 §4.3 grammar: font-size [ / line-height ]?).
    @Test
    fun `explicit slash line-height still wins`() {
        val out = FontExpander.expand("16px/2 Georgia")
        assertEquals("16px", out["font-size"])
        assertEquals("2", out["line-height"], "authored slash value survives")
        assertEquals("Georgia", out["font-family"])
    }

    // 3 — no font-size means the declaration never matched the shorthand
    // grammar. CSS2 §4.2 drops an invalid declaration whole, so the expander
    // must not invent a `line-height` reset from it.
    @Test
    fun `unparseable value emits no line-height`() {
        val out = FontExpander.expand("bold")
        assertNull(out["font-size"], "no size component was found")
        assertFalse("line-height" in out, "no reset synthesized from an invalid value")
    }

    // 4 — system fonts (css-fonts-4 §4.4) resolve their ENTIRE font
    // description — face, size AND line box — from the platform UI settings,
    // so the expander deliberately forwards only the family token.
    @Test
    fun `system font stays family only`() {
        val out = FontExpander.expand("menu")
        assertEquals("menu", out["font-family"])
        assertFalse("line-height" in out, "platform supplies the system-font line box")
    }

    // 5 — CSS-cascade-4 §7.2: a CSS-wide keyword on a shorthand assigns THAT
    // keyword to every longhand. `inherit` must stay `inherit`, not collapse
    // into the `normal` reset.
    @Test
    fun `global keyword forwards verbatim to line-height`() {
        val out = FontExpander.expand("inherit")
        assertEquals("inherit", out["line-height"])
        assertEquals("inherit", out["font-size"])
    }

    // 6 — the leading style/variant/weight/stretch tokens are consumed by a
    // separate loop before the size branch; pin that the reset still lands
    // after that loop runs (the regression would have been silent otherwise).
    @Test
    fun `reset survives the full leading longhand form`() {
        val out = FontExpander.expand("italic small-caps bold condensed 12pt \"Times New Roman\"")
        assertEquals("italic", out["font-style"])
        // css-fonts-4 §2.7: the shorthand's `<font-variant-css2>` slot sets the
        // LONGHAND font-variant-caps; `font-variant` is itself a shorthand and
        // must never leave the expander (retrospective A8#0 leak).
        assertEquals("small-caps", out["font-variant-caps"])
        assertNull(out["font-variant"], "shorthand name must not leak as a longhand")
        assertEquals("bold", out["font-weight"])
        assertEquals("condensed", out["font-stretch"])
        assertEquals("12pt", out["font-size"])
        assertEquals("normal", out["line-height"], "reset applies to the full form too")
    }

    // 7 (SKEPTIC, wave 22 lane font-shorthand-lh) — whitespace around the `/`
    // delimiter. css-syntax-3 §5 makes `16px/2`, `16px/ 2`, `16px /2` and
    // `16px / 2` the SAME declaration, but `tokenize` splits on spaces only, so
    // the last three used to reach the size branch as a bare `16px`: the
    // authored line-height was lost and `/ 2 Georgia` became the font-family.
    // Harmless-ish before this wave (the value merely fell back to
    // inheritance); once the §4.3 reset landed it wrote `line-height: normal`
    // OVER an explicit `2` — a silent omission promoted to a confident wrong
    // answer. `joinSlashRuns` re-joins the run before parsing. All four
    // spellings must now agree, byte for byte.
    @Test
    fun `whitespace around the slash delimiter does not lose the line-height`() {
        val tight = FontExpander.expand("16px/2 Georgia")
        for (spelling in listOf("16px/ 2 Georgia", "16px /2 Georgia", "16px / 2 Georgia")) {
            val out = FontExpander.expand(spelling)
            assertEquals("16px", out["font-size"], "font-size for `$spelling`")
            assertEquals("2", out["line-height"], "authored line-height for `$spelling`")
            assertEquals("Georgia", out["font-family"], "family for `$spelling`")
            assertEquals(tight, out, "`$spelling` must expand identically to `16px/2 Georgia`")
        }
    }

    // 8 (SKEPTIC) — the join pass works on TOKENS, so it can never reach inside
    // a quoted family name: `tokenize` keeps a quoted string in one token. A
    // family containing a slash must survive untouched, and must NOT be
    // mistaken for a line-height component.
    @Test
    fun `a slash inside a quoted family name is not treated as a delimiter`() {
        val out = FontExpander.expand("16px \"Foo/Bar\"")
        assertEquals("16px", out["font-size"])
        assertEquals("\"Foo/Bar\"", out["font-family"])
        assertEquals("normal", out["line-height"], "no slash component → the §4.3 reset")
    }
}
