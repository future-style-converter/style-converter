package app.parsing.css.properties.shorthands

import app.parsing.css.CssPropertyValue
import app.parsing.css.properties.GenericProperty
import app.parsing.css.properties.PropertiesParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Retrospective finding A8#0 (converter half) — nothing leaving the `font`
 * expander may be a SHORTHAND name.
 *
 * Measured defect (scratchpad probe `{"font":"inherit"}`, converted with the
 * wave-49 converter): the only IR property emitted was
 * `Generic{propertyName:"font-variant", rawValue:"inherit", _unmapped:true}`.
 * `font-variant` is a shorthand (css-fonts-4 §6.11), no longhand parser is
 * registered for it, and every runtime ignores a static Generic — so the
 * keyword reached nothing. Two roots, both fixed and both pinned here:
 *
 *  • FontExpander's CSS-wide branch forwarded the keyword under the nested
 *    shorthand's NAME. css-cascade-4 §3: a shorthand set to a CSS-wide
 *    keyword "sets all of its sub-properties to that keyword" — the
 *    sub-properties of `font-variant` are the seven §6.11 longhands.
 *  • FontVariantExpander had no CSS-wide branch at all: `font-variant:
 *    inherit` expanded to an EMPTY map (silently dropped).
 *
 * The non-keyword path had the same class of leak on a verbatim corpus
 * payload — css-cascade/all-prop-001's `font: bold italic small-caps 20px
 * monospace` emitted `Generic{propertyName:"font-variant",
 * rawValue:"small-caps"}` (tools/titan/runs/wave49-final/sections/css-cascade/
 * per-test-ir). css-fonts-4 §2.7 says that slot sets `font-variant-caps`.
 *
 * Proven able to fail: reverting FontExpander's branch to `"font-variant" to
 * trimmed` fails every test below that asserts the shorthand name is absent;
 * removing FontVariantExpander's CSS-wide branch fails the empty-map pin.
 */
class FontShorthandGlobalKeywordTest {

    /** css-fonts-4 §6.11: the seven sub-properties of `font-variant`, spec order. */
    private val variantLonghands = listOf(
        "font-variant-ligatures", "font-variant-position", "font-variant-caps",
        "font-variant-numeric", "font-variant-alternates", "font-variant-east-asian",
        "font-variant-emoji"
    )

    @Test
    fun `font inherit forwards to every longhand and never to the font-variant shorthand`() {
        val out = FontExpander.expand("inherit")
        assertNull(out["font-variant"], "the nested shorthand's NAME must not leave the expander")
        for (l in variantLonghands) assertEquals("inherit", out[l], "css-cascade-4 §3 forwards the keyword to $l")
        for (l in listOf("font-style", "font-weight", "font-stretch", "font-size", "line-height", "font-family")) {
            assertEquals("inherit", out[l], "css-fonts-4 §2.7 longhand $l keeps the keyword")
        }
        // Every key is a longhand: none is registered as a shorthand.
        for (k in out.keys) assertFalse(ShorthandRegistry.isShorthand(k), "$k is a shorthand name")
    }

    @Test
    fun `initial unset revert and revert-layer take the same path`() {
        // The five CSS-wide keywords this converter accepts: css-values-4
        // §4.1.1 (initial | inherit | unset) plus css-cascade-4 §7.3.4
        // `revert` and css-cascade-5 §7.3.5 `revert-layer`. The branch keys on
        // the set, so one keyword slipping to the token loop would emit a
        // half-expansion with no font-size (the loop treats it as noise).
        for (kw in listOf("initial", "unset", "revert", "revert-layer")) {
            val out = FontExpander.expand(kw)
            assertNull(out["font-variant"], "$kw leaked the shorthand name")
            assertEquals(13, out.size, "$kw must reach 6 font longhands + 7 font-variant longhands: ${out.keys}")
            assertTrue(out.values.all { it == kw }, "$kw forwarded verbatim to every longhand")
        }
    }

    @Test
    fun `keyword is forwarded as authored not lowercased`() {
        // Keywords are ASCII case-insensitive (css-values-4 §4.1);
        // the longhand parsers own the folding, so the expander must not
        // rewrite the author's bytes (FontExpanderTest pins the same for
        // line-height).
        val out = FontExpander.expand("Inherit")
        assertEquals("Inherit", out["font-variant-caps"])
        assertEquals("Inherit", out["font-size"])
    }

    @Test
    fun `font-variant shorthand with a CSS-wide keyword expands to its seven longhands`() {
        // Before the fix this returned an EMPTY map — the declaration vanished
        // without a log line, the silent-fallthrough class the house rules ban.
        val out = FontVariantExpander.expand("inherit")
        assertEquals(variantLonghands, out.keys.toList(), "css-fonts-4 §6.11 sub-properties, spec order")
        assertTrue(out.values.all { it == "inherit" })
    }

    @Test
    fun `non-keyword font shorthand sets font-variant-caps not font-variant`() {
        // VERBATIM corpus payload: fixtures/wpt/css-cascade/all-prop-001.json,
        // component all-prop-001__1 (`font: bold italic small-caps 20px monospace`).
        val out = FontExpander.expand("bold italic small-caps 20px monospace")
        assertEquals("small-caps", out["font-variant-caps"], "css-fonts-4 §2.7: <font-variant-css2> sets font-variant-caps")
        assertNull(out["font-variant"], "shorthand name must not leak")
        assertEquals("bold", out["font-weight"])
        assertEquals("italic", out["font-style"])
        assertEquals("20px", out["font-size"])
        assertEquals("monospace", out["font-family"])
    }

    // ── end to end through PropertiesParser ────────────────────────────────

    private fun parseBase(vararg decls: Pair<String, String>) =
        PropertiesParser.parse(decls.toMap().mapValues { CssPropertyValue(it.value) }, resolveInheritedDefaults = true, sourceTag = null)

    private fun parseBucket(vararg decls: Pair<String, String>) =
        PropertiesParser.parse(decls.toMap().mapValues { CssPropertyValue(it.value) })

    @Test
    fun `base bucket font inherit emits no property named font-variant`() {
        // The measured wire from the finding: exactly one Generic named
        // `font-variant`. Now: the six inherited-by-default font longhands
        // resolve to natural inheritance (InheritedDefaultResolution) and the
        // seven font-variant longhands ride as longhand-named Generics — their
        // parsers have no CSS-wide arm yet, the same contract a bare
        // `font-variant-caps: inherit` has today.
        val out = parseBase("font" to "inherit")
        assertTrue(out.none { it.propertyName == "font-variant" }, "shorthand name on the wire: $out")
        assertEquals(variantLonghands.toSet(), out.map { it.propertyName }.toSet())
        assertTrue(out.all { it is GenericProperty && it.rawValue == "inherit" })
    }

    @Test
    fun `selector bucket font initial emits only longhand names`() {
        // Buckets never drop `inherit`/`initial`, so every forwarded longhand
        // surfaces; none of them may be a shorthand name.
        val out = parseBucket("font" to "initial")
        for (p in out) assertFalse(ShorthandRegistry.isShorthand(p.propertyName), "${p.propertyName} is a shorthand name")
        assertTrue(out.any { it.propertyName == "font-variant-caps" })
        assertTrue(out.any { it.propertyName == "font-size" })
    }

    @Test
    fun `all-prop-001 shorthand yields a typed FontVariantCaps instead of a Generic`() {
        val out = parseBucket("font" to "bold italic small-caps 20px monospace")
        val caps = out.single { it.propertyName == "font-variant-caps" }
        assertFalse(caps is GenericProperty, "font-variant-caps has a registered parser; got $caps")
        assertTrue(out.none { it.propertyName == "font-variant" })
    }
}
