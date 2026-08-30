package app.parsing.css.properties

import app.irmodels.IRProperty
import app.parsing.css.CssPropertyValue
import app.parsing.css.properties.longhands.background.BackgroundImagePropertyParser
import app.irmodels.properties.background.BackgroundImageProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Wave-49 lane A1 — "an invalid declaration is DROPPED, not passed through".
 *
 * css-syntax-3 §2.2 ("Error Handling") / CSS 2.2 §4.2 ("Rules for handling
 * parsing errors"): a declaration whose
 * value does not match the property's grammar is invalid and must be ignored,
 * leaving the previously-declared value in force. The converter used to emit
 * such a value as a `{"raw": …}` background-image layer, which no runtime can
 * paint AND which shadows the valid declaration underneath.
 *
 * PROVENANCE, stated exactly because "verbatim payloads" is the standing rule
 * and a blanket claim here would be false. Every declaration in the POSITIVE
 * cases — the ones that must now be dropped, and the "unimplemented ≠ invalid"
 * ones that must not be — is VERBATIM: either from the extracted fixture the
 * converter actually consumed (`fixtures/wpt/css-values/angle-units-001.json`)
 * or from the WPT source it was extracted from
 * (`tools/wpt/css/css-values/angle-units-001.html`), and the four
 * must-not-be-dropped values are drawn from the exact strings a
 * `grep '"raw"'` over
 * `tools/titan/runs/wave48-final/sections/<section>/per-test-ir/` returns —
 * 13 distinct payloads across 8 files: the three this lane adjudicates plus
 * ten `justify-self` / `hyphenate-limit-chars` values.
 *
 * The remaining cases are CONSTRUCTED NEGATIVE CONTROLS, marked as such at
 * each site: a var()-bearing gradient, an unknown function head, and a radial
 * gradient with unmodelled radii. No corpus test exercises those today, which
 * is precisely why they are pinned — they are the shapes a future widening of
 * [InvalidDeclaration] would wrongly start dropping.
 *
 * The IR shapes asserted against are the ones recorded in
 * `tools/titan/runs/wave48-final/sections/css-values/per-test-ir/`.
 */
class InvalidDeclarationDropTest {

    // Run the parser exactly like a selector/media bucket (no cascade
    // resolution) — the same entry point CssParsing uses for every component.
    private fun parse(vararg decls: Pair<String, String>): List<IRProperty> =
        PropertiesParser.parse(decls.toMap().mapValues { CssPropertyValue(it.value) })

    private fun names(props: List<IRProperty>) = props.map { it.propertyName }.toSet()

    // ── WPT css-values/angle-units-001 ────────────────────────────────────
    // Fixture component `angle-units-001__1` (fixtures/wpt/css-values/
    // angle-units-001.json), verbatim. The wave-48 IR for it carried
    //   {"type":"BackgroundImage","data":[{"raw":"linear-gradient(0.25turns, red, red)"}]}
    // `turns` is not in the css-values-4 §7.1 angle-unit table, so the whole
    // declaration is invalid and must vanish.

    @Test
    fun `angle-units-001 component drops the invalid gradient and keeps its box`() {
        val out = parse(
            "height" to "100px",
            "width" to "100px",
            "background-image" to "linear-gradient(0.25turns, red, red)"
        )
        assertTrue(
            out.none { it.propertyName == "background-image" },
            "invalid gradient must be dropped, got $out"
        )
        assertEquals(setOf("height", "width"), names(out))
    }

    @Test
    fun `all four invalid angle units from the WPT source are dropped`() {
        // tools/wpt/css/css-values/angle-units-001.html declares these four in
        // order; each comment in the source says "invalid".
        for (unit in listOf("90degree", "100gradian", "1.57radian", "0.25turns")) {
            val out = parse("background-image" to "linear-gradient($unit, red, red)")
            assertTrue(out.isEmpty(), "linear-gradient($unit, …) must be dropped, got $out")
        }
    }

    @Test
    fun `the valid declaration the invalid ones must not shadow still parses`() {
        // The green gradient that WINS in angle-units-001 (the whole point of
        // dropping the four red ones). Its unit-free two-stop form must keep
        // producing a typed LinearGradient.
        val out = parse("background-image" to "linear-gradient(green, green)")
        assertEquals(1, out.size)
        val prop = assertIs<BackgroundImageProperty>(out[0])
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(prop.images[0])
    }

    @Test
    fun `valid angle units keep rendering — the drop is unit-table-driven, not blanket`() {
        // css-values-4 §7.1's four legal units, plus the legacy unitless zero
        // that GradientPrefixGuard's banner records the capture browser
        // accepting (CSS.supports('…','linear-gradient(0, red, red)') === true).
        for (angle in listOf("90deg", "100grad", "1.57rad", "0.25turn", "0")) {
            val out = parse("background-image" to "linear-gradient($angle, red, red)")
            assertEquals(1, out.size, "linear-gradient($angle, …) must survive, got $out")
            val prop = assertIs<BackgroundImageProperty>(out[0])
            assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(prop.images[0])
        }
    }

    @Test
    fun `conic from-angle with an invalid unit is dropped too`() {
        // css-images-4 §3.3.1 types the prefix slot as `from <angle>`; the
        // parser already refused this, it just used to emit Raw.
        val out = parse("background-image" to "conic-gradient(from 0.25turns, red, blue)")
        assertTrue(out.isEmpty(), "invalid conic from-angle must be dropped, got $out")
    }

    // ── wave-49 REGRESSION PINS for the two false-positive families ───────
    // Skeptic S4 measured that the first cut of this lane deleted nine
    // valid-CSS gradient shapes, and that none of this class's other tests
    // would have caught it: they exercise only shapes the guard gets right.
    // Both families are pinned on the MUST-STILL-PARSE side here; the whole
    // CLASS (every unit × every spelling × every prefix context) is swept in
    // GradientPrefixGuardSoundnessTest.
    //
    // Ground truth for both is the capture browser itself, Chrome/151.0.7922.47:
    //   CSS.supports('background-image','linear-gradient(4.5e1deg, red, blue)')
    //     === true, computing to linear-gradient(45deg, rgb(255,0,0), rgb(0,0,255))
    //   CSS.supports('background-image','linear-gradient(to  right, red, blue)')
    //     === true, computing to linear-gradient(to right, …)

    @Test
    fun `a scientific-notation angle is valid CSS and must not be dropped`() {
        // css-values-4 §5.3 ("Real Numbers: the <number> type") admits the
        // e/E exponent; css-syntax-3 §4.3.13 ("Consume a number") folds it
        // into the <number-token> before §4.3.3 attaches the unit, so
        // `4.5e1deg` is ONE dimension whose unit is the perfectly ordinary
        // `deg`. AngleParser cannot read the exponent yet — a modelling gap
        // that must cost a Raw passthrough, never a deleted declaration.
        for (angle in listOf("4.5e1deg", "1e2deg", "4.5e+1deg", "4.5e-1turn", "45e0deg")) {
            val out = parse("background-image" to "linear-gradient($angle, red, blue)")
            assertEquals(1, out.size, "linear-gradient($angle, …) must survive, got $out")
            val prop = assertIs<BackgroundImageProperty>(out[0])
            assertIs<BackgroundImageProperty.BackgroundImage.Raw>(prop.images[0])
        }
    }

    @Test
    fun `a scientific-notation angle survives beside an interpolation method and in conic`() {
        // The two prefix contexts that route through different guard branches
        // than the bare one above.
        for (value in listOf(
            "linear-gradient(in oklch 4.5e1deg, red, blue)",
            "conic-gradient(from 4.5e1deg, red, blue)"
        )) {
            val out = parse("background-image" to value)
            assertEquals(1, out.size, "$value must survive, got $out")
        }
    }

    @Test
    fun `a direction keyword written with extra whitespace is valid CSS and must not be dropped`() {
        // css-syntax-3 §4.3.1 ("Consume a token") consumes a whole run of
        // whitespace as ONE <whitespace-token>, so the number of spaces
        // between `to` and its side keywords carries no meaning. Before this
        // repair the string-matched lookup missed these, the guard read the
        // segment as "opens with `to` and did not resolve" and DELETED the
        // declaration — strictly worse than the pre-lane Raw passthrough,
        // which the web runtime re-emitted for the browser to render.
        for (direction in listOf("to  right", "to  right top", "to\tright", "to   left  bottom")) {
            val out = parse("background-image" to "linear-gradient($direction, red, blue)")
            assertEquals(1, out.size, "linear-gradient($direction, …) must survive, got $out")
            val prop = assertIs<BackgroundImageProperty>(out[0])
            // Stronger than "not dropped": the direction must actually be
            // READ, otherwise the gradient silently renders `to bottom`.
            val gradient = assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(prop.images[0])
            assertTrue(gradient.angle != null, "direction '$direction' must resolve to an angle, got $gradient")
        }
    }

    @Test
    fun `a conic from-angle the converter cannot evaluate stays Raw, not a drop`() {
        // `from calc(…)` / `from var(…)` are valid CSS whose value is only
        // known at computed-value time. The old conic branch reported them
        // invalid; only an unrelated upstream ExpressionDetector gate kept
        // the declaration alive. Pinned here so the guard's own answer is
        // correct independently of that gate.
        for (value in listOf(
            "conic-gradient(from calc(0.25turn), red, blue)",
            "conic-gradient(from var(--start), red, blue)",
            "repeating-conic-gradient(from calc(45deg), red, blue)"
        )) {
            val out = parse("background-image" to value)
            assertEquals(1, out.size, "$value must survive, got $out")
        }
    }

    // ── The "unimplemented ≠ invalid" side of the contract ────────────────
    // The ONLY other `raw` payloads in the wave-48 corpus IR are ten values
    // (grep for `"raw"` under sections/<section>/per-test-ir): three
    // `justify-self` keywords and seven `hyphenate-limit-chars: auto N M`
    // variants that differ only in their two integers. The four pinned below
    // cover both properties and every distinct justify-self shape. They are
    // VALID CSS the parser does not model yet; dropping them would be a
    // regression, which is why the InvalidDeclaration route is opt-in per
    // parser rather than a blanket "parse failed ⇒ drop".

    @Test
    fun `valid-but-unmodelled values keep their author bytes`() {
        // Verbatim from tools/titan/runs/wave48-final/sections/…/per-test-ir:
        //   {"type":"JustifySelf","data":{"type":"raw","value":"safe anchor-center"}}
        //   {"type":"HyphenateLimitChars","data":{"type":"raw","value":"auto 2 2"}}
        for ((name, value) in listOf(
            "justify-self" to "safe anchor-center",
            "justify-self" to "last baseline",
            "justify-self" to "safe end",
            "hyphenate-limit-chars" to "auto 2 2"
        )) {
            val out = parse(name to value)
            assertEquals(1, out.size, "$name: $value must survive, got $out")
        }
    }

    @Test
    fun `a var-bearing gradient stays a Raw passthrough for the web runtime`() {
        // CONSTRUCTED NEGATIVE CONTROL (no corpus test carries this shape).
        // VarCalcPreservation contract: var() is valid CSS whose value the
        // converter cannot resolve — it must reach the wire byte-for-byte.
        val out = parse("background-image" to "linear-gradient(var(--angle), red, blue)")
        assertEquals(1, out.size)
        val prop = assertIs<BackgroundImageProperty>(out[0])
        assertIs<BackgroundImageProperty.BackgroundImage.Raw>(prop.images[0])
    }

    @Test
    fun `an unknown function head stays a Raw passthrough, not a drop`() {
        // CONSTRUCTED NEGATIVE CONTROL (no corpus test carries this shape).
        // Unknown to US is not the same as invalid: a prefixed/never-modelled
        // image function must keep its bytes so the browser decides.
        val out = parse("background-image" to "-webkit-gradient(linear, left top, right top, from(red))")
        assertEquals(1, out.size)
        val prop = assertIs<BackgroundImageProperty>(out[0])
        assertIs<BackgroundImageProperty.BackgroundImage.Raw>(prop.images[0])
    }

    @Test
    fun `a radial gradient the parser cannot model stays Raw — radii are unmodelled, not invalid`() {
        // CONSTRUCTED NEGATIVE CONTROL (no corpus test carries this shape).
        // `looksLikeRadialPrefix` deliberately ignores <length-percentage>
        // radii, so a refusal in radial-gradient can mean "unmodelled".
        // isInvalidGradientLayer must therefore never claim a radial layer.
        val out = parse("background-image" to "radial-gradient(50px 20px at 10px 10px, red, blue)")
        assertEquals(1, out.size)
        assertIs<BackgroundImageProperty>(out[0])
    }

    // ── The sentinel must never reach the wire ───────────────────────────

    @Test
    fun `the InvalidDeclaration sentinel is never emitted as a property`() {
        val out = parse("background-image" to "linear-gradient(0.25turns, red, red)")
        assertTrue(out.none { it === InvalidDeclaration }, "sentinel leaked into $out")
        assertTrue(out.none { it.propertyName == InvalidDeclaration.propertyName })
    }

    @Test
    fun `the parser itself returns the sentinel for a provably invalid gradient`() {
        // Direct pin on the parser contract, so a future caller that forgets to
        // filter the sentinel fails here rather than in a wire diff.
        assertTrue(
            BackgroundImagePropertyParser.parse("linear-gradient(0.25turns, red, red)") === InvalidDeclaration
        )
    }
}
