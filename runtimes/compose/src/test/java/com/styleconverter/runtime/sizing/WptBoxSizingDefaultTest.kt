package com.styleconverter.runtime.sizing

// TITAN WPT lane — the WPT-capture-mode box-sizing DEFAULT (wave 11).
//
// WPT reftests are authored against the UA default `box-sizing: content-box`
// (css-sizing-3 §3's initial value; the browser-ref render applies no reset),
// while the SDUI runtimes' status quo is border-box (the web harness's
// `* { box-sizing: border-box }` reset that the whole 327-pair dark-stage
// baseline corpus is captured against). The web harness already splits the two
// worlds via the `body.wpt-mode [data-component-id] { box-sizing: content-box }`
// override in apps/web-harness/index.html; SizingApplier.effectiveBoxSizing is
// the Compose twin. This suite PINS THE MODE SPLIT — the dark-stage side must
// never move, the WPT side must default undeclared to CONTENT_BOX, and a
// declared keyword must win in BOTH modes.
//
// Wire shapes copy-pasted from BoxSizingContentBoxTest so parser drift fails
// in one obvious place first.

import com.styleconverter.runtime.core.types.LengthValue
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WptBoxSizingDefaultTest {

    // Helpers — same style as BoxSizingContentBoxTest.
    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(type: String, json: String) = type to parse(json)

    // ── 1. The pure tri-state decision (the mode split itself) ──────────────

    @Test fun `undeclared defaults to content-box ONLY in wpt capture mode`() {
        // WPT side: null → the css-sizing-3 §3 initial value.
        assertEquals(
            BoxSizingKeyword.CONTENT_BOX,
            SizingApplier.effectiveBoxSizing(null, wptCaptureMode = true))
        // Dark-stage side: null stays null — the border-box status quo the
        // 327-pair baselines depend on (byte-stability contract).
        assertNull(SizingApplier.effectiveBoxSizing(null, wptCaptureMode = false))
    }

    @Test fun `a declared keyword wins verbatim in both modes`() {
        // A WPT test that WRITES `box-sizing: border-box` must keep it —
        // the default only fills the UNSET slot.
        assertEquals(
            BoxSizingKeyword.BORDER_BOX,
            SizingApplier.effectiveBoxSizing(BoxSizingKeyword.BORDER_BOX, wptCaptureMode = true))
        // And an explicit content-box on the dark stage keeps working
        // (the lane-BX fixture sentinel).
        assertEquals(
            BoxSizingKeyword.CONTENT_BOX,
            SizingApplier.effectiveBoxSizing(BoxSizingKeyword.CONTENT_BOX, wptCaptureMode = false))
    }

    // ── 2. Extraction end-to-end: bands materialize for the WPT default ─────

    // The grid *-large-border-padding arithmetic that motivated the fix:
    // width:100px + padding:20px + border:16px solid → content-box frame
    // 100 + 2·(20+16) = 172 (the ref's 172-wide border box; Android rendered
    // 100 because undeclared box-sizing kept the border-box status quo).
    private val largeBorderPaddingProps = listOf(
        pair("Width", """{"type":"length","px":100.0}"""),
        pair("PaddingTop", """{"px":20.0}"""),
        pair("PaddingRight", """{"px":20.0}"""),
        pair("PaddingBottom", """{"px":20.0}"""),
        pair("PaddingLeft", """{"px":20.0}"""),
        // Width AND style — a style-less border has USED width 0 (CSS 2.1
        // §8.5.3), so both longhands ship like the WPT sources do.
        pair("BorderWidth", """{"px":16.0}"""),
        pair("BorderStyle", """{"keyword":"SOLID"}"""),
    )

    @Test fun `wpt mode computes inflation bands for the undeclared default`() {
        // No BoxSizing property at all + wptCaptureMode → CONTENT_BOX with
        // REAL bands (2·(20+16) = 72 per axis) — without the band hand-off
        // the default would be a no-op and the frame would stay 100.
        val cfg = SizingExtractor.extractSizingConfig(largeBorderPaddingProps, wptCaptureMode = true)
        assertEquals(BoxSizingKeyword.CONTENT_BOX, cfg.boxSizing)
        assertEquals(72f, cfg.contentBoxInflateX, 1e-4f)
        assertEquals(72f, cfg.contentBoxInflateY, 1e-4f)
        // And the applier arithmetic lands on the ref's 172px border box.
        assertEquals(
            LengthValue.Exact(172.0),
            SizingApplier.inflateForContentBox(
                LengthValue.Exact(100.0), cfg.boxSizing, cfg.contentBoxInflateX))
    }

    @Test fun `dark stage keeps the tri-state null and zero bands`() {
        // The SAME properties WITHOUT wpt mode (the default parameter — i.e.
        // every existing call site): null slot, 0f bands, 100px frame —
        // byte-identical to the pre-wave-11 extraction.
        val cfg = SizingExtractor.extractSizingConfig(largeBorderPaddingProps)
        assertNull(cfg.boxSizing)
        assertEquals(0f, cfg.contentBoxInflateX, 0f)
        assertEquals(0f, cfg.contentBoxInflateY, 0f)
        assertEquals(
            LengthValue.Exact(100.0),
            SizingApplier.inflateForContentBox(
                LengthValue.Exact(100.0), cfg.boxSizing, cfg.contentBoxInflateX))
    }

    @Test fun `explicit border-box in wpt mode stays border-box with no bands`() {
        // Declared keyword beats the WPT default — a border-box-declaring
        // WPT test must not get inflated.
        val cfg = SizingExtractor.extractSizingConfig(
            largeBorderPaddingProps + pair("BoxSizing", """"BORDER_BOX""""),
            wptCaptureMode = true)
        assertEquals(BoxSizingKeyword.BORDER_BOX, cfg.boxSizing)
        assertEquals(0f, cfg.contentBoxInflateX, 0f)
        assertEquals(0f, cfg.contentBoxInflateY, 0f)
    }

    // ── 3. Threading source scan (the render-path wiring pin) ───────────────
    //
    // The mode split is only live if ComponentRenderer actually reads
    // LocalWptCaptureMode and threads it into the static style chain — the
    // same feasible-without-Robolectric wiring pin FragmentGeometryTest uses.

    /** The renderer source, located by walking up from the test working dir. */
    private val rendererSource: String by lazy {
        val rel = "runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        File(requireNotNull(dir) { "repo root ($rel) not found above ${System.getProperty("user.dir")}" }, rel)
            .readText()
    }

    @Test fun `renderer threads LocalWptCaptureMode into the style chain`() {
        // The composable must read the ambient flag…
        assertTrue(
            "renderer must read LocalWptCaptureMode for the sizing lane",
            rendererSource.contains("val wptCaptureModeForSizing = LocalWptCaptureMode.current"))
        // …and pass it into applyProperties (the static chain can't read
        // CompositionLocals itself — the collapsedMargin precedent).
        assertTrue(
            "applyProperties must receive the threaded wpt flag",
            rendererSource.contains(
                "StyleApplier.applyProperties(effectiveProperties, collapsedMargin, wptCaptureModeForSizing)"))
    }
}
