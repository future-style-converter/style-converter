package com.styleconverter.runtime.typography

// Retrospective R3 (finding A5#4) — Compose reads the `<'block-ellipsis'>`
// component riding on `line-clamp` (css-overflow-4 §5.1 two-value grammar;
// §4.2 `no-ellipsis`), which Swift (LineClampExtractor, wave 46) and web
// (LineClampExtractor.suppressesMarker, wave 42) already read and Compose's
// three readers ignored — a live twin divergence, not merely an unread field.
//
// Wires are VERBATIM from tools/titan/runs/wave49-final/sections/css-overflow/
// per-test-ir/wpt__css-overflow__line-clamp__block-ellipsis-023.json and
// -024.json (the only two corpus carriers): `line-clamp: 4 no-ellipsis` and
// `line-clamp: 4 ""` on a 32ch monospace box. Both cells PASS on Android at
// wave49-final (Android-web 0.9735) and already paint no marker — the
// renderer's default overflow is Clip when no text-overflow is declared —
// so these pins make that outcome honest and guard the one case that could
// differ: a suppressed marker meeting an explicit `text-overflow: ellipsis`.
//
// Mutation proof (run during the lane): making LineClampWire.markerSuppressed
// return false unconditionally fails every "suppress" assertion below.

import androidx.compose.ui.text.style.TextOverflow
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineClampMarkerSuppressionTest {

    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun prop(type: String, json: String) = IRProperty(type, j(json))

    /** block-ellipsis-023's LineClamp payload, verbatim. */
    private val clamp023 = """{"type":"lines","count":4,"ellipsis":{"type":"no-ellipsis"}}"""

    /** block-ellipsis-024's LineClamp payload, verbatim. */
    private val clamp024 = """{"type":"lines","count":4,"ellipsis":{"type":"string","value":""}}"""

    /** The whole -023 component list, verbatim (Width 32ch, monospace). */
    private fun props023() = listOf(
        prop("LineClamp", clamp023),
        prop("Width", """{"type":"length","original":{"v":32,"u":"CH"}}"""),
        prop("FontFamily", """["monospace"]"""),
    )

    // ── the leaf reader (TextStyleApplier) ────────────────────────────────

    @Test
    fun `no-ellipsis and the empty string suppress the marker`() {
        assertTrue(TextStyleApplier.extractLineClampMarkerSuppressed(props023()))
        assertTrue(TextStyleApplier.extractLineClampMarkerSuppressed(listOf(prop("LineClamp", clamp024))))
        // The clamp itself still engages (Swift's companion assertion).
        assertEquals(4, TextStyleApplier.extractMaxLines(props023()))
        assertEquals(4, TextStyleApplier.extractMaxLines(listOf(prop("LineClamp", clamp024))))
    }

    @Test
    fun `drawn markers are not suppressed`() {
        // Absent (the initial `ellipsis`), `auto`, a non-empty string.
        for (json in listOf(
            """{"type":"lines","count":4}""",
            """{"type":"lines","count":4,"ellipsis":{"type":"auto"}}""",
            """{"type":"lines","count":4,"ellipsis":{"type":"string","value":"…"}}""",
        )) {
            assertFalse(json, TextStyleApplier.extractLineClampMarkerSuppressed(listOf(prop("LineClamp", json))))
            assertEquals(4, TextStyleApplier.extractMaxLines(listOf(prop("LineClamp", json))))
        }
    }

    @Test
    fun `clamp-less declarations carry no marker`() {
        // `none`, `auto`, the legacy bare integer, and no declaration at all.
        assertFalse(TextStyleApplier.extractLineClampMarkerSuppressed(listOf(prop("LineClamp", """{"type":"none"}"""))))
        assertFalse(TextStyleApplier.extractLineClampMarkerSuppressed(listOf(prop("LineClamp", """{"type":"auto"}"""))))
        assertFalse(TextStyleApplier.extractLineClampMarkerSuppressed(listOf(prop("LineClamp", "4"))))
        assertFalse(TextStyleApplier.extractLineClampMarkerSuppressed(listOf(prop("Width", """{"px":100}"""))))
    }

    @Test
    fun `last declaration wins like the cascade`() {
        val plain = prop("LineClamp", """{"type":"lines","count":4}""")
        assertFalse(TextStyleApplier.extractLineClampMarkerSuppressed(listOf(prop("LineClamp", clamp023), plain)))
        assertTrue(TextStyleApplier.extractLineClampMarkerSuppressed(listOf(plain, prop("LineClamp", clamp023))))
    }

    @Test
    fun `the clamp envelope the legacy count reader accepts is honoured`() {
        // extractLineClampValue unwraps {"clamp":{…}}; the marker read must
        // see the same declaration or the two would disagree.
        assertTrue(LineClampWire.markerSuppressed(j("""{"clamp":$clamp023}""")))
    }

    @Test
    fun `a malformed component never removes a marker`() {
        // A wire this reader does not understand keeps the author's marker.
        assertFalse(LineClampWire.markerSuppressed(j("""{"type":"lines","count":4,"ellipsis":"no-ellipsis"}""")))
        assertFalse(LineClampWire.markerSuppressed(j("""{"type":"lines","count":4,"ellipsis":{"type":"garbage"}}""")))
    }

    @Test
    fun `a string component with no value reads as the empty string`() {
        // Swift: `(o["value"]?.stringValue ?? "").isEmpty` — mirrored.
        assertTrue(LineClampWire.markerSuppressed(j("""{"type":"lines","count":4,"ellipsis":{"type":"string"}}""")))
    }

    // ── the config fold (TypographyExtractor) and its consumers ───────────

    @Test
    fun `the typography config carries both halves of the declaration`() {
        val cfg = TypographyExtractor.extractTypographyConfig(props023().map { it.type to it.data })
        assertEquals(4, cfg.lineClamp)
        assertTrue(cfg.lineClampMarkerSuppressed)
        // A plain `line-clamp: 4` keeps the pre-R3 config byte-identically.
        val plain = TypographyExtractor.extractTypographyConfig(listOf("LineClamp" to j("""{"type":"lines","count":4}""")))
        assertEquals(4, plain.lineClamp)
        assertFalse(plain.lineClampMarkerSuppressed)
    }

    @Test
    fun `a suppressed marker Clips even over an explicit text-overflow ellipsis`() {
        // Compose's single overflow knob would paint "…" under Ellipsis; the
        // block-clamped run must Clip (css-overflow-4 §4.2), and the
        // inline-axis `text-overflow` (css-overflow-3 §6.1) cannot override.
        val suppressed = TypographyConfig(lineClamp = 4, lineClampMarkerSuppressed = true, textOverflow = TextOverflow.Ellipsis)
        assertEquals(TextOverflow.Clip, LineClampApplier.getTextOverflow(suppressed))
        assertFalse(TypographyApplier.hasEllipsis(suppressed))
        // A drawn marker keeps today's Ellipsis byte-for-byte.
        val drawn = TypographyConfig(lineClamp = 4)
        assertEquals(TextOverflow.Ellipsis, LineClampApplier.getTextOverflow(drawn))
        assertTrue(TypographyApplier.hasEllipsis(drawn))
        // Without a clamp the flag is inert: explicit ellipsis still wins.
        val noClamp = TypographyConfig(lineClampMarkerSuppressed = true, textOverflow = TextOverflow.Ellipsis)
        assertEquals(TextOverflow.Ellipsis, LineClampApplier.getTextOverflow(noClamp))
    }
}
