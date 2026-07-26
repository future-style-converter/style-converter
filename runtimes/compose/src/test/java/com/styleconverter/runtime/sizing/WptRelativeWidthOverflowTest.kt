package com.styleconverter.runtime.sizing

// RC-B6b (wave 19, lane CLAMP) — a px-RESOLVABLE relative width (ch/em/rem)
// larger than the incoming constraints must OVERFLOW in WPT capture mode,
// exactly like the wave-12 Exact-px branch: css-overflow block-ellipsis-001
// declares `width: 63.1ch` (monospace ≈ 605px at the measured advance) on a
// box whose incoming content envelope is ~358px. Modifier.width COERCES the
// resolved 605 into the 358 max, so Android wrapped the paragraph at 358px
// while the browser-ref wraps at 605px — every wrap point (and therefore the
// 2-line `line-clamp` geometry) shifted. The fix routes the WPT-mode
// non-percent Relative branch through the SAME ExactWidthOverflow layout the
// Exact branch uses (measure at declared / report the clamp / start-anchor),
// gated on SizingConfig.wptCaptureMode so the dark-stage 327-pair corpus
// keeps the historical coercing Modifier.width byte-identically (the wave-1
// "+2px placeholder" lesson: never move non-WPT geometry from sizing).
//
// Pins: the config-threading tri-state + the live 63.1ch wire resolution,
// plus a source scan on the routing (the SizingOverflowWidthTest pattern —
// the branch itself builds Modifiers, so the wiring is locked textually).

import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.spacing.SpacingContext
import com.styleconverter.runtime.spacing.resolveToDp
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WptRelativeWidthOverflowTest {

    // Helper: parse a JSON string into a JsonElement (live wire shapes only).
    private fun parse(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    // ── config threading: the flag rides SizingConfig ────────────────────

    @Test
    fun `extractor stamps the wpt flag onto the config`() {
        // The applier is a pure config→Modifier function, so the mode must
        // travel ON the config (the boxSizing tri-state plumbing pattern).
        val props = listOf(pair("Width", """{"type":"length","original":{"v":63.1,"u":"CH"}}"""))
        assertTrue(SizingExtractor.extractSizingConfig(props, wptCaptureMode = true).wptCaptureMode)
    }

    @Test
    fun `default extraction keeps the flag off for the dark-stage corpus`() {
        // Flag omitted → false: every legacy call site (and the whole
        // 327-pair baseline geometry) stays on the coercing width() path.
        val props = listOf(pair("Width", """{"type":"length","original":{"v":63.1,"u":"CH"}}"""))
        assertFalse(SizingExtractor.extractSizingConfig(props).wptCaptureMode)
        assertFalse(SizingConfig().wptCaptureMode)
    }

    // ── the live wire: 63.1ch stays Relative and resolves past the max ───

    @Test
    fun `block-ellipsis ch width resolves to a definite px beyond the envelope`() {
        // The EXACT block-ellipsis-001 payload → Relative(63.1, CH); at the
        // measured monospace advance (ChUnitMetrics ≈ 9.6px @16px) it
        // resolves ~605px — definite, and larger than the 358px content
        // envelope, which is precisely the overflow case the exactWidth
        // guard (ExactWidthOverflow.measureSpec) splits on.
        val cfg = SizingExtractor.extractSizingConfig(
            listOf(pair("Width", """{"type":"length","original":{"v":63.1,"u":"CH"}}""")),
            wptCaptureMode = true
        )
        assertEquals(LengthValue.Relative(63.1, LengthUnit.CH, null), cfg.width)
        // Resolution basis (same ctx StyleApplier builds): 63.1 × 9.6 ≈ 605.8.
        val px = resolveToDp(cfg.width, SpacingContext(chAdvancePx = 9.6f)).value
        assertEquals(605.76f, px, 1e-2f)
        // …and the wave-12 guard sends that through the overflow side:
        // measure at declared 606, report the 358 envelope (start-anchored).
        val (child, reported) = ExactWidthOverflow.measureSpec(606, 0, 358)
        assertEquals(606, child)
        assertEquals(358, reported)
    }

    // ── source scan: the routing cannot silently regress ─────────────────

    /** Walk up to the repo root to read a runtime source (the
     *  SizingOverflowWidthTest / FragmentGeometryTest pattern). */
    private fun source(rel: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        return File(requireNotNull(dir) { "repo root ($rel) not found" }, rel).readText()
    }

    private val applier by lazy {
        source("runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/SizingApplier.kt")
    }

    @Test
    fun `wpt-gated relative branch routes through exactWidth`() {
        // The WPT side must take the overflow-aware layout on the RESOLVED
        // px value — reverting to Modifier.width reinstates the clamp…
        assertTrue(
            "WPT relative width must route through exactWidth(resolveToDp(...))",
            applier.contains("m.exactWidth(resolveToDp(v, ctx))")
        )
        // …and it must stay GATED (the dark stage keeps width()).
        assertTrue(
            "the exact routing must sit behind the wptCaptureMode gate",
            applier.contains("} else if (wptCaptureMode) {")
        )
        assertTrue(
            "the non-WPT relative branch must keep the coercing width()",
            applier.contains("m.width(resolveToDp(v, ctx))")
        )
    }

    @Test
    fun `percent widths keep the fillMaxWidth path in both modes`() {
        // %-widths resolve against the PARENT, not to a definite px — the
        // WPT gate must sit after the percent split so they never reroute.
        assertTrue(
            applier.contains("m.fillMaxWidth((v.value.toFloat() / 100f).coerceIn(0f, 1f))")
        )
    }
}
