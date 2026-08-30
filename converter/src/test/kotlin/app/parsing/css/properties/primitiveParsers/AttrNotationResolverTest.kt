package app.parsing.css.properties.primitiveParsers

import app.irmodels.IRProperty
import app.irmodels.properties.color.BackgroundColorProperty
import app.parsing.css.CssPropertyValue
import app.parsing.css.properties.PropertiesParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Wave-49 lane A1 — `attr()` resolution at the declaration level
 * (css-values-5 §8.7, "Attribute References: the attr() notation"), plus the
 * wave-49 F3 repairs skeptic S4's probe forced: `--`-prefixed idents, the
 * §8.7.1 FAILURE algorithm, and a KNOWN-WRONG pin on the one rule in the
 * resolver that is not pure grammar.
 *
 * Both corpus carriers are pinned end-to-end, using the declaration strings
 * VERBATIM from the extracted fixtures the converter actually consumed
 * (`fixtures/wpt/css-values/attr-namespace-non-existing.json` and
 * `…/attr-namespace-wildcard.json`), and the wave-48 IR those produced
 * (`tools/titan/runs/wave48-final/sections/css-values/per-test-ir/`) as the
 * "before" shape each assertion replaces.
 */
class AttrNotationResolverTest {

    private fun parse(vararg decls: Pair<String, String>): List<IRProperty> =
        PropertiesParser.parse(decls.toMap().mapValues { CssPropertyValue(it.value) })

    // ── WPT css-values/attr-namespace-non-existing ───────────────────────
    // Fixture components `…__1` / `…__2`, verbatim. Wave-48 IR was
    //   {"type":"BackgroundImage","data":[{"raw":"attr(foo|bar type(*), green)"}]}
    // → nothing painted, while the reference is a filled green square.

    @Test
    fun `undeclared namespace prefix substitutes the fallback`() {
        val outcome = AttrNotationResolver.resolve("attr(foo|bar type(*), green)")
        val sub = assertIs<AttrNotationResolver.Outcome.Substituted>(outcome)
        assertEquals("green", sub.value)
    }

    @Test
    fun `the non-existing-namespace component paints green through the background shorthand`() {
        val out = parse(
            "background" to "attr(foo|bar type(*), green)",
            "height" to "50px",
            "width" to "100px"
        )
        // The shorthand must expand the SUBSTITUTED value, so the layer lands
        // on background-color (not background-image) — that is what makes the
        // green square appear on all three runtimes.
        val bg = out.filterIsInstance<BackgroundColorProperty>()
        assertEquals(1, bg.size, "expected one background-color, got $out")
        assertTrue(
            out.none { it.propertyName == "background-image" },
            "no background-image layer may survive, got $out"
        )
        assertTrue(out.any { it.propertyName == "height" } && out.any { it.propertyName == "width" })
    }

    @Test
    fun `a namespaced attr with a syntax and no fallback is invalid at computed-value time`() {
        // css-values-5 §8.7.1's FAILURE step, second branch: "If second arg is
        // null, return the guaranteed-invalid value." That makes the
        // declaration invalid at computed-value time (Appendix A, "Invalid
        // Substitution"), i.e. it computes as if `unset` were specified.
        //
        // The first cut SUBSTITUTED the literal keyword `unset` instead, which
        // ColorParser then modelled as a NAMED COLOUR called "unset"
        // (`IRColor(Named("unset"), srgb=null)`) — skeptic S4 measured the
        // eight longhands `background: attr(ns|x)` expanded into. A typed
        // outcome the caller omits cannot manufacture that.
        val outcome = AttrNotationResolver.resolve("attr(foo|bar type(*))")
        assertIs<AttrNotationResolver.Outcome.InvalidAtComputedValueTime>(outcome)
    }

    @Test
    fun `a namespaced attr with NO syntax and no fallback substitutes the empty string`() {
        // §8.7.1 FAILURE, FIRST branch: "If second arg is null, and syntax was
        // omitted, return an empty CSS <string>." The two no-fallback cases
        // are genuinely different and the resolver must not conflate them.
        val sub = assertIs<AttrNotationResolver.Outcome.Substituted>(
            AttrNotationResolver.resolve("attr(foo|bar)")
        )
        assertEquals("\"\"", sub.value)
    }

    @Test
    fun `a bare comma passes an EMPTY fallback, which substitutes nothing`() {
        // §8.7: "attr(foo) does not pass a fallback value, but attr(foo,) does
        // (the fallback is just empty)." Substituting an empty token sequence
        // leaves the declaration with no value, which matches no property's
        // grammar — and §8.7 syntax-checks only AFTER substitution, so that is
        // IACVT rather than a parse-time drop.
        assertIs<AttrNotationResolver.Outcome.InvalidAtComputedValueTime>(
            AttrNotationResolver.resolve("attr(foo|bar,)")
        )
    }

    @Test
    fun `an IACVT declaration is omitted rather than emitted as a bogus named colour`() {
        // The end-to-end consequence, pinned on the shape S4 actually measured.
        val out = parse(
            "background" to "attr(ns|x type(*))",
            "height" to "50px"
        )
        assertTrue(
            out.none { it.propertyName.startsWith("background") },
            "no background longhand may carry a manufactured `unset`, got $out"
        )
        assertEquals(1, out.size, "height must survive, got $out")
    }

    // ── `--`-prefixed idents (css-syntax-3 §4.3.9) ───────────────────────

    @Test
    fun `a custom-property-shaped ident is an ident and must not be dropped`() {
        // css-syntax-3 §4.3.9 ("Check if three code points would start an
        // ident sequence"): with a leading U+002D, the second code point may
        // be an ident-start code point OR a second U+002D. So `--x` IS an
        // <ident-token> — the canonical custom-property shape. The first cut
        // classified it NO and DROPPED the declaration, violating the
        // resolver's own stated rule that it may never drop a declaration it
        // merely failed to lex.
        val local = assertIs<AttrNotationResolver.Outcome.Substituted>(
            AttrNotationResolver.resolve("attr(ns|--x, green)")
        )
        assertEquals("green", local.value)
        val prefix = assertIs<AttrNotationResolver.Outcome.Substituted>(
            AttrNotationResolver.resolve("attr(--ns|x, green)")
        )
        assertEquals("green", prefix.value)
    }

    @Test
    fun `a lone hyphen is still not an ident`() {
        // §4.3.9 needs a code point AFTER the hyphen, so `-` alone is a
        // <delim-token>. The widened pattern must not have swallowed it.
        assertIs<AttrNotationResolver.Outcome.Invalid>(AttrNotationResolver.resolve("attr(-|x, green)"))
        assertIs<AttrNotationResolver.Outcome.Invalid>(AttrNotationResolver.resolve("attr(ns|-, green)"))
    }

    @Test
    fun `a digit-leading name is still not an ident`() {
        // Guard against the widened pattern going too far the other way.
        assertIs<AttrNotationResolver.Outcome.Invalid>(AttrNotationResolver.resolve("attr(ns|2x, green)"))
        assertIs<AttrNotationResolver.Outcome.Invalid>(AttrNotationResolver.resolve("attr(2ns|x, green)"))
    }

    // ── KNOWN WRONG, pinned deliberately ─────────────────────────────────

    @Test
    fun `a DECLARED namespace prefix gets the wrong answer here — pinned, not endorsed`() {
        // This is the resolver's one non-grammar rule and it has a stated
        // failure condition (see AttrNotationResolver's banner). Declaration
        // taken VERBATIM from tools/wpt/css/css-values/attr-namespace-valid.xhtml,
        // which DOES declare `@namespace color "http://www.example.com/"` and
        // DOES carry `color:myAttr="green"`; its `<link rel=match>` is
        // ref-filled-green-100px-square.xht, so the correct pixel is GREEN and
        // we produce the fallback `red`.
        //
        // Abstaining here would not fix it — the attribute is absent from the
        // wire either way, so a Raw passthrough reaches runtimes that also
        // cannot resolve it — and would additionally lose
        // attr-namespace-non-existing, where the fallback IS the reference's
        // green. The fix is upstream, in the extractor's attr bake
        // (tools/titan/attr-bake.mjs, which bails on any `|`). That test is
        // not in the corpus today; this pin exists so admitting it cannot
        // regress silently, and so the next reader sees the cost stated.
        val sub = assertIs<AttrNotationResolver.Outcome.Substituted>(
            AttrNotationResolver.resolve("attr(color|myAttr type(*), red)")
        )
        assertEquals("red", sub.value, "KNOWN WRONG: attr-namespace-valid.xhtml wants green")
    }

    // ── WPT css-values/attr-namespace-wildcard ───────────────────────────
    // Fixture child component `attr-namespace-wildcard__1__0`, verbatim. The
    // test's own <meta name=assert> reads "Wildcard not supported in attr()
    // function", and its parent `.outer` paints the green the child must not
    // cover. Wave-48 IR carried {"raw":"attr(*|bar type(*))"} — same pixels by
    // accident; this pins the RIGHT reason.

    @Test
    fun `a wildcard namespace prefix makes the declaration invalid`() {
        val outcome = AttrNotationResolver.resolve("attr(*|bar type(*))")
        assertIs<AttrNotationResolver.Outcome.Invalid>(outcome)
    }

    @Test
    fun `the wildcard component keeps its box and declares no background at all`() {
        val out = parse(
            "background" to "attr(*|bar type(*))",
            "height" to "100%",
            "width" to "100%"
        )
        assertTrue(
            out.none { it.propertyName.startsWith("background") },
            "the invalid shorthand must take every background longhand with it, got $out"
        )
        assertEquals(2, out.size, "height and width must survive, got $out")
    }

    // ── Abstentions: everything the converter must NOT decide ────────────

    @Test
    fun `an unprefixed attr is left untouched — its value is a DOM fact`() {
        assertIs<AttrNotationResolver.Outcome.Unresolvable>(
            AttrNotationResolver.resolve("attr(data-x type(*), fallback)")
        )
    }

    @Test
    fun `an explicitly null-namespaced attr is left untouched`() {
        // `|bar` is the production's empty <ident-token>? — the NULL namespace,
        // which WPT css-values/attr-null-namespace shows does resolve against
        // real attributes. Only the DOM can answer it.
        assertIs<AttrNotationResolver.Outcome.Unresolvable>(
            AttrNotationResolver.resolve("attr(|bar type(*), green)")
        )
    }

    @Test
    fun `an escaped ident is abstained on rather than guessed`() {
        // We do not decode CSS escapes (Syntax L3 §4.3.7); abstaining keeps a
        // valid declaration from being dropped by a lexing shortfall.
        assertIs<AttrNotationResolver.Outcome.Unresolvable>(
            AttrNotationResolver.resolve("""attr(f\6f o|bar type(*), green)""")
        )
    }

    @Test
    fun `an attr riding inside a larger value is not a whole-declaration attr`() {
        assertIs<AttrNotationResolver.Outcome.NotAttr>(
            AttrNotationResolver.resolve("attr(foo|bar type(*), green) no-repeat")
        )
    }

    @Test
    fun `a non-attr value is reported as such`() {
        assertIs<AttrNotationResolver.Outcome.NotAttr>(AttrNotationResolver.resolve("linear-gradient(red, blue)"))
        assertIs<AttrNotationResolver.Outcome.NotAttr>(AttrNotationResolver.resolve("green"))
    }

    @Test
    fun `a fallback containing commas survives whole`() {
        // The first TOP-LEVEL comma splits the arguments; the rest is one
        // <declaration-value> (`attr(x, 1px, 2px)` → fallback "1px, 2px").
        val sub = assertIs<AttrNotationResolver.Outcome.Substituted>(
            AttrNotationResolver.resolve("attr(ns|x type(*), 1px, 2px)")
        )
        assertEquals("1px, 2px", sub.value)
    }

    @Test
    fun `a comma inside a quoted fallback does not split the arguments`() {
        val sub = assertIs<AttrNotationResolver.Outcome.Substituted>(
            AttrNotationResolver.resolve("""attr(ns|x type(*), "a,b")""")
        )
        assertEquals(""""a,b"""", sub.value)
    }

    @Test
    fun `the function name is matched case-insensitively`() {
        val sub = assertIs<AttrNotationResolver.Outcome.Substituted>(
            AttrNotationResolver.resolve("ATTR(foo|bar type(*), green)")
        )
        assertEquals("green", sub.value)
    }
}
