package com.styleconverter.runtime.sizing

// Wave 55 (lane L2, unit U2) — PercentContentBoxBand pins P-U2-1 … P-U2-6. Payloads are VERBATIM wave54-final
// per-test IR (= wave55-open): …/filter-effects/per-test-ir/wpt__filter-effects__backdrop-filter-nested-border-radius-clip-3.json
// (#middle 0-172, #inner 0-173). Every pin was proven able to fail by an executed mutation (M-U2a … M-U2g),
// bracketed with sha256 in tools/titan/results/wave55-cbb-android/_note.md.

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.StyleApplier
import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.core.variables.DynamicValueResolver
import com.styleconverter.runtime.lists.ListStyleUaRule
import com.styleconverter.runtime.spacing.SpacingContext
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PercentContentBoxBandTest {

    // Parse one wire JSON literal into an IRProperty.
    private fun prop(type: String, json: String) = IRProperty(type, Json.parseToJsonElement(json))

    /** clip-3's #middle (0-172), every property verbatim, in wire order. */
    private val middle = listOf(
        prop("Position", "\"RELATIVE\""), prop("Width", """{"type":"percentage","value":100}"""),
        prop("Height", """{"type":"percentage","value":100}"""), prop("PaddingTop", """{"px":10}"""),
        prop("PaddingRight", """{"px":10}"""), prop("PaddingBottom", """{"px":10}"""), prop("PaddingLeft", """{"px":10}"""),
        prop("BorderTopLeftRadius", """{"px":10}"""), prop("BorderTopRightRadius", """{"px":10}"""),
        prop("BorderBottomRightRadius", """{"px":10}"""), prop("BorderBottomLeftRadius", """{"px":10}"""),
        prop("OverflowX", "\"HIDDEN\""), prop("OverflowY", "\"HIDDEN\""), prop("BackgroundColor", """{"srgb":{"r":0.8,"g":0.8,"b":0.8},"original":"#cccccc"}"""))

    /** clip-3's #inner (0-173), verbatim: #middle's first three entries, radius 25 ×4, overflow hidden, no band. */
    private val inner = middle.take(3) + listOf("TopLeft", "TopRight", "BottomRight", "BottomLeft")
        .map { prop("Border${it}Radius", """{"px":25}""") } + listOf(prop("OverflowX", "\"HIDDEN\""), prop("OverflowY", "\"HIDDEN\""))

    // SizingExtractor over a property list, as LayoutFacade.extractConfig runs it.
    private fun cfg(props: List<IRProperty>, wpt: Boolean) =
        SizingExtractor.extractSizingConfig(props.map { it.type to it.data }, wpt)

    // applySizing's own clamp question per axis (SizingClamps.percentClampSpec != null), width then height.
    private fun clampW(c: SizingConfig) = SizingClamps.percentClampSpec(c.width, c.minWidth, c.maxWidth, SpacingContext()) != null
    private fun clampH(c: SizingConfig) = SizingClamps.percentClampSpec(c.height, c.minHeight, c.maxHeight, SpacingContext()) != null

    // owns() on the width axis exactly as applySizing asks it.
    private fun ownsW(c: SizingConfig) = PercentContentBoxBand.owns(c.width, c, c.contentBoxInflateX, clampW(c))

    @Test
    fun `P-U2-1 middle's band is added to the fill and overflows as the house split`() {
        // The verbatim payload's bands: padding 10 + 10 per axis, no border.
        val c = cfg(middle, wpt = true)
        assertEquals(20f to 20f, c.contentBoxInflateX to c.contentBoxInflateY)
        // fraction exactly as the fill branch: 100% → 1.0.
        val f = PercentContentBoxBand.fraction(c.width).also { assertEquals(1f, it, 0f) }
        // #outer's content box 200×100 → measured 220 / 120, reported 200 / 100 (the parent must not grow).
        assertEquals(220 to 200, PercentContentBoxBand.measureSpec(f, c.contentBoxInflateX, 0, 200))
        assertEquals(120 to 100, PercentContentBoxBand.measureSpec(f, c.contentBoxInflateY, 0, 100))
        // Fitting row: 50% of 400 + 20 = 220 fits → tight 220 / 220.
        assertEquals(220 to 220, PercentContentBoxBand.measureSpec(0.5f, 20f, 0, 400))
    }

    @Test
    fun `P-U2-2 owns exactly the WPT content-box percent axis with a band`() {
        // A clean tracker: only this test's calls can set the breadcrumbs.
        PropertyTracker.reset()
        // #middle under WPT capture: both axes owned.
        val wpt = cfg(middle, wpt = true)
        assertTrue(ownsW(wpt))
        assertTrue(PercentContentBoxBand.owns(wpt.height, wpt, wpt.contentBoxInflateY, clampH(wpt)))
        // A declared border-box keeps the fill (css-ui-3 §3.1: the % IS the border box).
        assertFalse(ownsW(cfg(middle + prop("BoxSizing", "\"BORDER_BOX\""), wpt = true)))
        // #inner (band 0) is untouched: the fill is already right.
        assertFalse(ownsW(cfg(inner, wpt = true)))
        // Dark stage, unset box-sizing: the null tri-state is not a content-box — declined silently.
        assertFalse(ownsW(cfg(middle, wpt = false)))
        // None of those is a breadcrumb case.
        assertFalse(PropertyTracker.isUnhandled(PercentContentBoxBand.DARK_STAGE_BREADCRUMB) || PropertyTracker.isUnhandled(PercentContentBoxBand.CLAMP_BREADCRUMB))
        // Dark stage, DECLARED content-box (band 20): declined AND named.
        val dark = cfg(middle + prop("BoxSizing", "\"CONTENT_BOX\""), wpt = false)
        assertFalse(ownsW(dark.also { assertEquals(20f, it.contentBoxInflateX, 0f) }))
        assertTrue(PropertyTracker.isUnhandled("Sizing[dark-stage-percent-content-box-band]"))
        // A MaxWidth puts the axis in the PercentSizeClamp lane: declined AND named.
        val clamp = cfg(middle + prop("MaxWidth", """{"type":"length","px":300}"""), wpt = true)
        assertTrue(clampW(clamp) && !ownsW(clamp))
        assertTrue(PropertyTracker.isUnhandled("Sizing[percent-clamp-content-box-band]"))
        // Leave the global tracker clean.
        PropertyTracker.reset()
    }

    @Test
    fun `P-U2-3 an unbounded axis is identity - FillNode parity`() {
        // No containing-block size: no spec, the modifier measures as is.
        assertEquals(null, PercentContentBoxBand.measureSpec(1f, 20f, 0, Constraints.Infinity))
    }

    @Test
    fun `P-U2-4 the oversized box is start-anchored, never centred`() {
        // css-overflow-3 §3: #middle's excess spills toward the end edge.
        assertEquals(0 to 0, PercentContentBoxBand.placementOffset(220, 200) to PercentContentBoxBand.placementOffset(120, 100))
        // requiredWidth's centring would put the start edge at −10.
        assertNotEquals((200 - 220) / 2, PercentContentBoxBand.placementOffset(220, 200))
    }

    /** The directory above Gradle's user.dir (the module or harness dir) that holds [rel], or null — the SeamReachabilityTest walk-up. */
    private fun rootHolding(rel: String): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        return dir
    }

    @Test
    fun `P-U2-5 source - applySizing asks the band lane before the clamp and the fill`() {
        // SizingApplier.kt's applySizing body (C13: cited by symbol, never by line); a missing file is a broken checkout.
        val rel = "runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/SizingApplier.kt"
        val body = File(requireNotNull(rootHolding(rel)) { "repo root not found" }, rel).readText()
            .substringAfter("fun applySizing(").substringBefore("private fun isDefiniteAxis(")
        // Per axis: the band lane present, then the clamp lane, then the fill/inflate branch.
        for ((a, x, clamp, fill) in listOf(listOf("W", "X", "pctW", "Width"), listOf("H", "Y", "pctH", "Height"))) {
            val site = body.indexOf("if (PercentContentBoxBand.owns(raw$a, config, config.contentBoxInflate$x, $clamp != null)) " +
                "r = r.percentFillPlusBand(raw$a, config.contentBoxInflate$x, horizontal = ${a == "W"})")
            assertTrue("$a call site missing", site >= 0)
            assertTrue(site < body.indexOf("else if ($clamp != null) r = r.percent${fill}Clamped("))
            assertTrue(site < body.indexOf("r = apply$fill(r, inflateForContentBox("))
        }
    }

    // Outermost → innermost class names of a modifier chain.
    private fun chain(m: Modifier): List<String> = m.foldIn(emptyList<String>()) { acc, e -> acc + e.javaClass.name }

    @Test
    fun `P-U2-5 live chain - middle gets both band nodes outside its clip, background and padding`() {
        // The chain ComponentRenderer builds for #middle under WPT capture.
        val m = StyleApplier.applyProperties(middle, null, wptCaptureMode = true)
        val names = chain(m)
        // The two band nodes, width then height, with the verbatim inputs.
        val bands = m.foldIn(emptyList<Modifier.Element>()) { acc, e -> acc + e }.filterIsInstance<PercentFillPlusBandModifier>()
        assertEquals(listOf(PercentFillPlusBandModifier(1f, 20f, horizontal = true),
            PercentFillPlusBandModifier(1f, 20f, horizontal = false)), bands)
        // No stock fill is left on either axis.
        assertFalse(names.toString(), names.any { it.endsWith("FillElement") })
        // C13 order: sizing (the band) OUTSIDE #middle's paint, clip and padding.
        val first = names.indexOfFirst { it.endsWith("PercentFillPlusBandModifier") }
        assertTrue(names.toString(), first in 0 until names.indexOfLast { it.endsWith("PaddingElement") })
        assertTrue(names.toString(), names.withIndex().any { (i, n) -> i > first && n.endsWith("BackgroundElement") })
    }

    @Test
    fun `P-U2-5 live chain - the dark stage keeps the stock fill`() {
        // wptCaptureMode false: box-sizing unset → no band node, two FillElements.
        val names = chain(StyleApplier.applyProperties(middle, null, wptCaptureMode = false))
        assertFalse(names.any { it.endsWith("PercentFillPlusBandModifier") })
        assertEquals(names.toString(), 2, names.count { it.endsWith("FillElement") })
    }

    @Test
    fun `P-U2-6 census - owns selects exactly middle W and H of clip-3 among the percentage axes`() {
        // Local-only: tools/titan/runs is gitignored (assume, like the CBB census).
        val run = "tools/titan/runs/wave54-final/sections"
        val dir = rootHolding(run).also { assumeTrue("wave54-final corpus not present", it != null) }
        // Every per-test IR document of the run (1435).
        val files = File(dir, run).listFiles().orEmpty().flatMap { File(it, "per-test-ir").listFiles().orEmpty().toList() }
            .filter { it.name.endsWith(".json") }.sortedBy { it.name }.also { assertEquals(1435, it.size) }
        // blockLevelWidthAuto = true for EVERY box (a superset): the resolver rewrites a
        // guaranteed-invalid block width to the wire's 100% shape, which would read as a % axis.
        val ctx = DynamicValueResolver.Context(containingBlockWidthPx = 358f, blockLevelWidthAuto = true)
        val (axes, owned) = mutableListOf<String>() to mutableListOf<String>()
        for (f in files) for (c in IRDocumentDecoder.decode(f.readText()).components) {
            // The runtime's tag-keyed UA padding (list containers), then the dynamic-value pre-pass.
            val ua = ListStyleUaRule.apply(c._tag, c.properties, c.properties)
            val k = cfg(DynamicValueResolver.resolve(ua, emptyMap(), ctx).properties, wpt = true)
            // One axis: count it when it is a percentage, record it when owns() takes it.
            fun axis(name: String, raw: LengthValue?, band: Float, clampOwns: Boolean) {
                if (raw !is LengthValue.Relative || raw.unit != LengthUnit.PERCENT) return
                val line = "${f.name} ${c.id.substringAfterLast("__")} $name ${raw.value}% band $band"
                axes += line
                if (PercentContentBoxBand.owns(raw, k, band, clampOwns)) owned += line
            }
            // Both physical slots (the extractor folds the logical sizes into them).
            axis("W", k.width, k.contentBoxInflateX, clampW(k))
            axis("H", k.height, k.contentBoxInflateY, clampH(k))
        }
        // The census record (read into the lane note by census.sh).
        File(dir, "runtimes/compose/build/percent-content-box-band-census.now.txt")
            .writeText((listOf("axes ${axes.size} owned ${owned.size}") + owned).joinToString("\n", postfix = "\n"))
        // 122 percentage axes in the corpus (census B), exactly two owned.
        assertEquals("percentage axes", 122, axes.size)
        assertEquals("owned axes (${owned.size})", listOf("W", "H").map {
            "wpt__filter-effects__backdrop-filter-nested-border-radius-clip-3.json 0-172 $it 100.0% band 20.0" }, owned)
    }
}
