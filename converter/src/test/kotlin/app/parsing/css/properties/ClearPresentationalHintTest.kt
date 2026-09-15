package app.parsing.css.properties

import app.irmodels.IRProperty
import app.irmodels.properties.layout.ClearProperty
import app.parsing.css.CssPropertyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Wave-50 lane B6 — the CONVERTER half of BACKLOG queue 3(a), "the
 * converter/inline bake drops `<br clear="all">`".
 *
 * WHERE THE DROP ACTUALLY IS, measured. The `clear` HTML attribute never
 * reaches the converter at all: `tools/titan/extract-fixture.mjs` models
 * author declarations (matched rules + inline style) plus a named set of
 * presentational-attribute bakes, and `br[clear]` was not among them. The
 * evidence is byte-level — `tools/titan/runs/wave49-final/sections/CSS2/
 * per-test-ir/wpt__CSS2__floats-clear__floats-clear-multicol-000.json`
 * carries the `<br>` component as `[Width, Height]` and nothing else. So
 * nothing in `converter/` is broken here; what this file pins is the
 * CONTRACT the seam fold must meet, so the day the extractor bake lands the
 * wire is guaranteed to survive conversion instead of being dropped a second
 * time, silently, one layer down.
 *
 * The bake is HTML Rendering §15.3's UA rule set:
 *   br[clear=left i] { clear: left } · br[clear=right i] { clear: right }
 *   br[clear=all i], br[clear=both i] { clear: both }
 * — note the `all`→`both` fold, which the two `all` cases below prove is
 * load-bearing rather than cosmetic.
 *
 * Every declaration bag below is VERBATIM the bag the patched extractor
 * emits (the seam patch + its full-corpus differential are committed under
 * `tools/titan/results/wave50-B6/`): four corpus tests change, one component
 * each, `{"width":"0px","height":"20px"}` →
 * `{"width":"0px","height":"0px","clear":"both"}`.
 */
class ClearPresentationalHintTest {

    // The same entry point a selector/media bucket uses — one component's
    // declaration bag in, typed IR properties out (no cascade resolution).
    private fun parse(vararg decls: Pair<String, String>): List<IRProperty> =
        PropertiesParser.parse(decls.toMap().mapValues { CssPropertyValue(it.value) })

    @Test
    fun `the patched br bag converts to Width, Height and Clear BOTH`() {
        // VERBATIM: the `<br clear="all">` component of all four carriers
        // (floats-clear-multicol-000 / -001 / -balancing-000 / -balancing-001)
        // as the patched extractor writes it.
        val out = parse("width" to "0px", "height" to "0px", "clear" to "both")
        assertEquals(
            setOf("width", "height", "clear"),
            out.map { it.propertyName }.toSet(),
            "the whole bag must survive, got $out"
        )
        val clear = out.first { it.propertyName == "clear" }
        assertIs<ClearProperty>(clear)
        assertEquals(ClearProperty.Clear.BOTH, clear.clear)
    }

    @Test
    fun `the raw attribute value all degrades to an untyped Generic - the fold is required`() {
        // THE REASON THE BAKE MUST MAP, NOT PASS THROUGH. `clear` in CSS 2.1
        // §9.5.2 / css-logical-1 §4 is `none | left | right | both |
        // inline-start | inline-end` — `all` is HTML attribute vocabulary only,
        // so ClearPropertyParser returns null for it.
        //
        // MEASURED, and NOT what the first draft of this pin assumed: null is
        // the "valid-but-unmodelled" channel, not the invalid one
        // (PropertiesParser's three-way `when`: InvalidDeclaration drops,
        // non-null adds, null falls back), so a forwarded `clear: all` does
        // not vanish — it becomes `GenericProperty("clear", "all")`, an
        // `_unmapped` passthrough carrying the author bytes under a type no
        // runtime's clearance machinery reads (Compose's
        // MulticolFloatStrip.factsFor matches `p.type == "Clear"`; the
        // Generic wire is a different type entirely). The clearance is
        // therefore still invisible, while the extractor's own
        // `'clear' in props` br-height rule WOULD flip the break from 20px to
        // 0px — ink moved, defect unfixed. That asymmetry is exactly why the
        // bake folds `all` → `both` instead of forwarding.
        //
        // (Adjacent, deliberately NOT changed by this lane: `clear` has four
        // invalid-value carriers in tools/wpt/css/** — `all`, `up`, `object`,
        // `inherit`, one each — that all take this Generic path where
        // css-syntax-3 §2.2 says an invalid declaration is ignored. Teaching
        // ClearPropertyParser the InvalidDeclaration channel would move those
        // four IRs, which is a measured change of its own, not a free rider.)
        val out = parse("width" to "0px", "height" to "0px", "clear" to "all")
        val clear = out.first { it.propertyName == "clear" }
        assertIs<GenericProperty>(clear)
        assertEquals("all", clear.rawValue)
        assertTrue(
            out.none { it is ClearProperty },
            "`clear: all` must NOT produce a typed ClearProperty, got $out"
        )
    }

    @Test
    fun `every value the HTML rule set can produce converts`() {
        // The three CSS keywords the §15.3 rule set maps to, each asserted on
        // the enum the runtimes switch on. `br clear=left` and `clear=both`
        // have corpus carriers outside the wave49-final 1435-test set
        // (tools/wpt/css/**: 1 × clear="left", 2 × clear="both", 194 ×
        // clear="all"), so the bake must not be an `all`-only special case.
        val expected = mapOf(
            "left" to ClearProperty.Clear.LEFT,
            "right" to ClearProperty.Clear.RIGHT,
            "both" to ClearProperty.Clear.BOTH
        )
        expected.forEach { (css, enum) ->
            val clear = parse("clear" to css).single()
            assertIs<ClearProperty>(clear)
            assertEquals(enum, clear.clear, "clear: $css")
        }
    }

    @Test
    fun `the unpatched br bag carries no Clear - the defect, pinned as it stands`() {
        // VERBATIM the bag wave49-final actually converted (see the per-test-ir
        // pointer in the banner). Recorded, not endorsed: this assertion is
        // what a later wave DELETES when the seam fold lands, and its presence
        // is how that wave knows the fold is the thing still missing.
        val out = parse("width" to "0px", "height" to "20px")
        assertTrue(
            out.none { it.propertyName == "clear" },
            "pre-fold bag must have no Clear wire, got $out"
        )
        assertEquals(setOf("width", "height"), out.map { it.propertyName }.toSet())
    }
}
