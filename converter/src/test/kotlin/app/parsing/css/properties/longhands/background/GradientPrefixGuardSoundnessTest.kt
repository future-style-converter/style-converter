package app.parsing.css.properties.longhands.background

import app.irmodels.IRProperty
import app.parsing.css.CssPropertyValue
import app.parsing.css.properties.PropertiesParser
import app.parsing.css.properties.primitiveParsers.TokenizationUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Wave-49 FIX lane F3 — the SOUNDNESS contract of [GradientPrefixGuard],
 * pinned as a class rather than as a handful of samples.
 *
 * WHY A WHOLE-CLASS SWEEP
 * -----------------------
 * Lane A1 gave the converter the power to DELETE a declaration it proves
 * invalid, and defended it with 24 tests. Skeptic S4 then found nine
 * valid-CSS gradient shapes that the guard deleted anyway, and noted the real
 * problem: "A1's 24 new tests exercise only shapes the guard gets right, so
 * neither false positive would be caught by the suite as it stands." A
 * soundness bug is not a bug in one value, it is a bug in a WHOLE CLASS of
 * values — so this file sweeps the classes instead of sampling them:
 *
 *  1. every angle unit in the css-values-4 §7.1 table × every legal number
 *     spelling (sign, leading dot, exponent) × every prefix context the guard
 *     has a branch for;
 *  2. every `<side-or-corner>` (css-images-4 §3.1) × every whitespace
 *     spelling between its tokens;
 *  3. every distinct gradient PREFIX SHAPE the WPT corpus actually carries.
 *
 * PROVENANCE of (3), stated exactly. The 55 declarations in [CORPUS_GRADIENTS]
 * are VERBATIM author bytes from the `fixtures/wpt/` tree — the extractor output the
 * converter consumed to produce
 * `tools/titan/runs/wave48-final/sections/<section>/per-test-ir/`. They were
 * harvested by scanning every fixture's `properties` bag for a value
 * containing `gradient(`, which yields 138 declarations, then deduplicating
 * on the SHAPE of each value's first comma-segment — the only thing this
 * guard ever inspects — which yields 56. The 56th is
 * `linear-gradient(0.25turns, red, red)` from WPT css-values/angle-units-001,
 * the single corpus gradient that IS invalid; it is asserted separately, in
 * the opposite direction, so this sweep cannot pass by the guard simply
 * answering `false` to everything.
 */
class GradientPrefixGuardSoundnessTest {

    private fun parse(vararg decls: Pair<String, String>): List<IRProperty> =
        PropertiesParser.parse(decls.toMap().mapValues { CssPropertyValue(it.value) })

    // Split a declaration into background-image LAYERS the way the parser
    // does (paren-aware, so a comma inside `rgba(…)` never splits a layer) —
    // the guard's unit of judgement is one layer, not one declaration.
    private fun layersOf(value: String): List<String> =
        TokenizationUtils.splitByComma(value).map { it.trim() }.filter { it.isNotEmpty() }

    // ── (1) angles ────────────────────────────────────────────────────────

    // css-values-4 §7.1 "Angle Units": the CLOSED table of angle units. Any
    // dimension carrying one of these can be an <angle>, so the guard must
    // never claim it — whether or not AngleParser can currently read it.
    private val ANGLE_UNITS = listOf("deg", "grad", "rad", "turn")

    // Number spellings from css-values-4 §5.3 "Real Numbers: the <number>
    // type" — integer, decimal, leading dot, explicit sign, and the e/E
    // exponent that css-syntax-3 §4.3.13 ("Consume a number") folds into the
    // <number-token>. The exponent forms are exactly the family skeptic S4
    // measured the first cut deleting, confirmed VALID by the capture browser
    // (Chrome/151.0.7922.47, CSS.supports === true, computing
    // `linear-gradient(4.5e1deg, red, blue)` to `linear-gradient(45deg, …)`).
    private val NUMBER_SPELLINGS = listOf(
        "45", "45.0", "0.25", ".25", "+45", "-45", "045",
        "4.5e1", "1e2", "4.5e+1", "4.5e-1", "45e0", "4.5E1"
    )

    // The three prefix contexts isInvalidGradientLayer branches on: a bare
    // linear prefix, a linear prefix sharing its segment with a
    // <color-interpolation-method> (§3.1's `||` combinator), and a conic
    // `from` slot (§3.3.1).
    private fun contexts(angle: String) = listOf(
        "linear-gradient($angle, red, blue)",
        "repeating-linear-gradient($angle, red, blue)",
        "linear-gradient(in oklch $angle, red, blue)",
        "linear-gradient($angle in oklch, red, blue)",
        "conic-gradient(from $angle, red, blue)",
        "repeating-conic-gradient(from $angle at 50% 50%, red, blue)"
    )

    @Test
    fun `no dimension carrying a real angle unit is ever called invalid`() {
        for (unit in ANGLE_UNITS) {
            for (number in NUMBER_SPELLINGS) {
                for (value in contexts("$number$unit")) {
                    assertFalse(
                        GradientPrefixGuard.isInvalidGradientLayer(value),
                        "'$value' carries the angle unit '$unit' and must never be called invalid"
                    )
                }
            }
        }
    }

    @Test
    fun `angle units are matched case-insensitively`() {
        // WPT css-values/angle-units-001 declares its VALID half in mixed
        // case (`90DeG`, `100gRaD`, `1.57rAd`, `0.25tUrN`) precisely to test
        // this; CSS 2.2 §4.1.3 ("Characters and case") makes units
        // ASCII case-insensitive.
        for (angle in listOf("90DeG", "100gRaD", "1.57rAd", "0.25tUrN", "45DEG", "4.5E1DEG")) {
            for (value in contexts(angle)) {
                assertFalse(
                    GradientPrefixGuard.isInvalidGradientLayer(value),
                    "'$value' must never be called invalid"
                )
            }
        }
    }

    @Test
    fun `a dimension whose unit is not in the angle table is invalid in every context`() {
        // The other direction, so the sweep above cannot pass degenerately.
        // These four are the invalid half of angle-units-001, verbatim from
        // tools/wpt/css/css-values/angle-units-001.html, plus the plural
        // trap for each real unit.
        for (angle in listOf(
            "90degree", "100gradian", "1.57radian", "0.25turns",
            "45degs", "45rads", "45grads", "45px", "45%"
        )) {
            for (value in contexts(angle)) {
                assertTrue(
                    GradientPrefixGuard.isInvalidGradientLayer(value),
                    "'$value' carries no angle unit and must be called invalid"
                )
            }
        }
    }

    @Test
    fun `a from-argument the converter cannot evaluate is never called invalid`() {
        // calc()/var() are valid CSS whose value is only known at
        // computed-value time. The conic branch used to report them invalid;
        // only an unrelated upstream gate hid the drop.
        for (arg in listOf("calc(0.25turn)", "calc(1turn / 4)", "var(--start)", "calc(45deg + 45deg)")) {
            for (value in listOf(
                "conic-gradient(from $arg, red, blue)",
                "conic-gradient(from $arg at 50% 50%, red, blue)",
                "repeating-conic-gradient(from $arg, red, blue)"
            )) {
                assertFalse(
                    GradientPrefixGuard.isInvalidGradientLayer(value),
                    "'$value' is valid CSS this converter cannot evaluate — Raw, not a drop"
                )
            }
        }
    }

    // ── (2) directions ────────────────────────────────────────────────────

    // Every string the css-images-4 §3.1 production
    // `<side-or-corner> = [left | right] || [top | bottom]` generates.
    private val SIDE_OR_CORNER = listOf(
        "left", "right", "top", "bottom",
        "left top", "top left", "left bottom", "bottom left",
        "right top", "top right", "right bottom", "bottom right"
    )

    // Every whitespace run css-syntax-3 §4.3.1 ("Consume a token") folds into
    // a single <whitespace-token>. `to  right` is the two-space spelling S4
    // measured the first cut deleting — Chrome computes it to `to right`.
    private val WHITESPACE_RUNS = listOf(" ", "  ", "   ", "\t", "\n", " \t ")

    @Test
    fun `every side-or-corner survives every whitespace spelling`() {
        for (direction in SIDE_OR_CORNER) {
            for (ws in WHITESPACE_RUNS) {
                val spelled = ("to " + direction).replace(" ", ws)
                val value = "linear-gradient($spelled, red, blue)"
                assertFalse(
                    GradientPrefixGuard.isInvalidGradientLayer(value),
                    "'$value' is a legal <side-or-corner> and must never be called invalid"
                )
                // And it must actually be READ, not merely spared: losing the
                // direction silently renders the gradient `to bottom`.
                val out = parse("background-image" to value)
                assertEquals(1, out.size, "'$value' must survive, got $out")
            }
        }
    }

    @Test
    fun `a to-segment that is not a side-or-corner is invalid whatever the spacing`() {
        // The `||` combinator allows each operand at most once and requires
        // at least one, so all of these are outside the production.
        for (direction in listOf(
            "to", "to left right", "to top bottom", "to bottom left top",
            "to centre", "to 45deg", "to left left"
        )) {
            for (ws in listOf(" ", "  ")) {
                val spelled = direction.replace(" ", ws)
                assertTrue(
                    GradientPrefixGuard.isInvalidGradientLayer("linear-gradient($spelled, red, blue)"),
                    "'$spelled' is not a <side-or-corner> and must be called invalid"
                )
            }
        }
    }

    // ── (3) the corpus ────────────────────────────────────────────────────

    private val CORPUS_GRADIENTS = listOf(
        "conic-gradient(at 25% 25%, red 0 25%, green 25% 50%, blue 50% 75%, black 75% 100%)",
        "conic-gradient(at 80% 80% in hsl, green)",
        "conic-gradient(from -90deg, blue 0 25%, black 25% 50%, red 50% 75%, green 75% 100%)",
        "conic-gradient(from 0deg at center, green)",
        "conic-gradient(from 45deg at 100px 50px, green 25%, transparent 0)",
        "conic-gradient(from 45deg at 1lh 50px, red 25%, green 0)",
        "conic-gradient(from 45deg at 50px 1lh, red 25%, green 0)",
        "conic-gradient(green)",
        "conic-gradient(hsl(100deg 100% 50%), green)",
        "cross-fade(\n          10% linear-gradient(#e66465, #9198e5),\n          10% linear-gradient(#e66465, #9198e5),\n          10% linear-gradient(#e66465, #9198e5),\n          10% linear-gradient(#e66465, #9198e5),\n          10% linear-gradient(#e66465, #9198e5),\n          10% linear-gradient(#e66465, #9198e5)\n        )",
        "linear-gradient(#e66465, #9198e5)",
        "linear-gradient(0.25tUrN, green, green)",
        "linear-gradient(1.57rAd, green, green)",
        "linear-gradient(100gRaD, green, green)",
        "linear-gradient(90DeG, green, green)",
        "linear-gradient(90deg in hsl, red, color(srgb 0 1 0 / 0) )",
        "linear-gradient(90deg in hwb, red, color(srgb 0 1 0 / 0) )",
        "linear-gradient(90deg in lch, red, color(srgb 0 1 0 / 0) )",
        "linear-gradient(90deg in oklch, red, color(srgb 0 1 0 / 0) )",
        "linear-gradient(90deg in srgb, hsl(0deg 0% 50%), yellow)",
        "linear-gradient(90deg, red, green)",
        "linear-gradient(black 0,white)",
        "linear-gradient(blue, green)",
        "linear-gradient(currentcolor, currentcolor)",
        "linear-gradient(green 80px, red 140px)",
        "linear-gradient(green)",
        "linear-gradient(in hsl longer hue 0deg, hsl(0, 100%, 50%) 0 0)",
        "linear-gradient(red 50%, green 50%)",
        "linear-gradient(rgba(0,255,0,0.5), rgba(0,0,255,0.5)), linear-gradient(rgba(0,0,0,1), rgba(0,0,0,1))",
        "linear-gradient(to bottom in srgb, lime 100px, red calc(Infinity * 1px))",
        "linear-gradient(to bottom, red 50%, green 50%)",
        "linear-gradient(to left in srgb, lime 100px, red calc(Infinity * 1px))",
        "linear-gradient(to right in display-p3-linear, rgb(255, 0, 0), rgb(0, 255, 0))",
        "linear-gradient(to right in hsl decreasing hue,\n                                              hsl(0deg, 100%, 50%),\n                                              hsl(270deg, 100%, 50%))",
        "linear-gradient(to right in hsl increasing hue,\n                                              hsl(0deg, 100%, 50%),\n                                              hsl(10deg, 100%, 50%),\n                                              hsl(20deg, 100%, 50%),\n                                              hsl(30deg, 100%, 50%),\n                                              hsl(40deg, 100%, 50%))",
        "linear-gradient(to right in hsl longer hue, hsl(0 100% 50%), hsl(0 0% 0% / 0%))",
        "linear-gradient(to right in hsl shorter hue, red, orange)",
        "linear-gradient(to right in hsl, red, hsl(120deg 100% 50% / 0%) )",
        "linear-gradient(to right in hwb, red, hsl(120deg 100% 50% / 0%) )",
        "linear-gradient(to right in lch decreasing hue,\n                                              lch(50% 100% 0deg),\n                                              lch(50% 100% 270deg))",
        "linear-gradient(to right in lch increasing hue,\n                                              lch(50% 100% 0deg),\n                                              lch(50% 100% 20deg),\n                                              lch(50% 100% 40deg),\n                                              lch(50% 100% 60deg),\n                                              lch(50% 100% 80deg))",
        "linear-gradient(to right in lch longer hue, red, orange)",
        "linear-gradient(to right in lch shorter hue, red, orange)",
        "linear-gradient(to right in lch, red, hsl(120deg 100% 50% / 0%) )",
        "linear-gradient(to right in oklab, rgb(0 255 0), rgb(255 0 0) )",
        "linear-gradient(to right in oklch longer hue, oklch(0.2 0.1 90), oklch(0.5 0.3 90) 50%, oklch(0.5 0.3 180) 50%, oklch(0.8 0.4 180))",
        "linear-gradient(to right in oklch, oklch(0.2 0.1 90), oklch(0.8 0.4 90) 50%, oklch(0.8 0.4 180) 50%, oklch(0.3 0.2 180))",
        "linear-gradient(to right in srgb, color(srgb 0 0 0), color(srgb 0 1 1))",
        "linear-gradient(to right, rgb(255, 0, 0), color(srgb 0 1 0))",
        "linear-gradient(to top in srgb, lime 100px, red calc(1px / 0))",
        "radial-gradient(farthest-corner at center, blue, black)",
        "radial-gradient(green)",
        "repeating-linear-gradient(green 50px)",
        "repeating-linear-gradient(to bottom right, white, black, white 30px)",
        "repeating-radial-gradient(10px circle at top left, green)",
    )

    @Test
    fun `no gradient prefix shape the corpus carries is called invalid`() {
        for (declaration in CORPUS_GRADIENTS) {
            for (layer in layersOf(declaration)) {
                assertFalse(
                    GradientPrefixGuard.isInvalidGradientLayer(layer),
                    "corpus layer '$layer' must never be called invalid"
                )
            }
        }
    }

    @Test
    fun `every corpus gradient declaration survives the parser`() {
        // End-to-end companion to the guard-level sweep: a false verdict is
        // only useful if the declaration actually reaches the wire.
        for (declaration in CORPUS_GRADIENTS) {
            val out = parse("background-image" to declaration)
            assertEquals(1, out.size, "corpus declaration '$declaration' must survive, got $out")
        }
    }

    @Test
    fun `the one corpus gradient that IS invalid is still called invalid`() {
        // WPT css-values/angle-units-001, verbatim. Without this the sweep
        // above would pass just as well against a guard that always answers
        // `false`, which is exactly the degenerate green this campaign keeps
        // finding.
        val invalid = "linear-gradient(0.25turns, red, red)"
        assertTrue(GradientPrefixGuard.isInvalidGradientLayer(invalid))
        assertTrue(
            parse("background-image" to invalid).isEmpty(),
            "the invalid corpus gradient must still be dropped"
        )
    }
}
