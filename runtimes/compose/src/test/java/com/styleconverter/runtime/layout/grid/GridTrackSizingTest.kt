package com.styleconverter.runtime.layout.grid

// Pins the grid-template-columns track model added after the GTC fixture
// family diverged from web (SSIM 0.76-0.80): the old renderer gave every
// column Modifier.weight(1f), silently flattening `80px 120px 80px`,
// `25% 50% 25%`, `1fr 2fr 1fr`, auto and minmax() templates into equal
// thirds. Two halves are covered here:
//   1. parseColumnTracks — IR wire shapes → TrackSpec list
//   2. computeTrackWidths — css-grid-1 §7.2 track sizing approximation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridTrackSizingTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun widths(
        specs: List<GridRenderer.TrackSpec>,
        intrinsics: List<Float> = List(specs.size) { 0f },
        container: Float = 288f, // 300px grid − 2×6px padding
        gap: Float = 6f,
    ) = GridRenderer.computeTrackWidths(specs, intrinsics, container, gap)

    // ── parseColumnTracks: IR wire shapes ────────────────────────────────

    @Test
    fun `px fr percent auto array parses`() {
        // GTC_Mixed wire shape: [{"px":80},{"fr":1},20.0] (bare number = %).
        val t = GridRenderer.parseColumnTracks(parse("""[{"px":80.0},{"fr":1.0},20.0]"""))!!
        assertEquals(GridRenderer.TrackSpec.Px(80f), t[0])
        assertEquals(GridRenderer.TrackSpec.Fr(1f), t[1])
        assertEquals(GridRenderer.TrackSpec.Percent(20f), t[2])
    }

    @Test
    fun `auto keyword and repeat expand`() {
        val auto = GridRenderer.parseColumnTracks(parse("""["auto","auto"]"""))!!
        assertEquals(listOf(GridRenderer.TrackSpec.Auto, GridRenderer.TrackSpec.Auto), auto)
        // GTC_RepeatN: [{"repeat":4,"tracks":[{"fr":1}]}] → 4 × fr(1).
        val rep = GridRenderer.parseColumnTracks(
            parse("""[{"repeat":4,"tracks":[{"fr":1.0}]}]"""))!!
        assertEquals(4, rep.size)
        assertTrue(rep.all { it == GridRenderer.TrackSpec.Fr(1f) })
    }

    @Test
    fun `minmax expr parses and auto-fill bails to null`() {
        // GTC_Minmax keeps the raw text: {"expr":"minmax(80px, 1fr) minmax(80px, 1fr)"}.
        val mm = GridRenderer.parseTrackExpr("minmax(80px, 1fr) minmax(80px, 1fr)")!!
        assertEquals(listOf(
            GridRenderer.TrackSpec.MinMax(80f, 1f),
            GridRenderer.TrackSpec.MinMax(80f, 1f)), mm)
        // Named lines are stripped: `[start] 1fr [mid] 1fr [end]` → 2×fr.
        assertEquals(2, GridRenderer.parseTrackExpr("[start] 1fr [mid] 1fr [end]")!!.size)
        // auto-fill cannot be sized without full placement — must return
        // null so the renderer keeps the legacy fallback, not a guess.
        assertNull(GridRenderer.parseTrackExpr("repeat(auto-fill, minmax(80px, 1fr))"))
    }

    // ── computeTrackWidths: css-grid-1 §7.2 approximation ────────────────

    @Test
    fun `fixed px tracks resolve literally`() {
        // GTC_Px: 80px 120px 80px in a 288px content box — literal sizes,
        // leftover stays empty (justify-content: start).
        val w = widths(listOf(
            GridRenderer.TrackSpec.Px(80f),
            GridRenderer.TrackSpec.Px(120f),
            GridRenderer.TrackSpec.Px(80f)))
        assertArrayEquals(floatArrayOf(80f, 120f, 80f), w.toFloatArray(), 0.5f)
    }

    @Test
    fun `fr tracks split free space by factor`() {
        // GTC_AsymmetricFr: 1fr 2fr 1fr, 288 − 2×6 gaps = 276 free → 69/138/69.
        val w = widths(listOf(
            GridRenderer.TrackSpec.Fr(1f),
            GridRenderer.TrackSpec.Fr(2f),
            GridRenderer.TrackSpec.Fr(1f)))
        assertArrayEquals(floatArrayOf(69f, 138f, 69f), w.toFloatArray(), 0.5f)
    }

    @Test
    fun `percent tracks resolve against the container content box`() {
        // GTC_Percent: 25% 50% 25% of 288 = 72/144/72 (gaps overflow, as in CSS).
        val w = widths(listOf(
            GridRenderer.TrackSpec.Percent(25f),
            GridRenderer.TrackSpec.Percent(50f),
            GridRenderer.TrackSpec.Percent(25f)))
        assertArrayEquals(floatArrayOf(72f, 144f, 72f), w.toFloatArray(), 0.5f)
    }

    @Test
    fun `auto tracks take max-content plus an equal stretch share`() {
        // GTC_Auto with items 60/80/100px wide: content 240 + 12 gap = 252;
        // 288 − 252 = 36 free → +12 each (Chrome's normal-alignment auto
        // stretch). Tracks: 72/92/112.
        val w = widths(
            listOf(GridRenderer.TrackSpec.Auto, GridRenderer.TrackSpec.Auto, GridRenderer.TrackSpec.Auto),
            intrinsics = listOf(60f, 80f, 100f))
        assertArrayEquals(floatArrayOf(72f, 92f, 112f), w.toFloatArray(), 0.5f)
    }

    @Test
    fun `minmax takes the larger of min and fr share`() {
        // GTC_Minmax: 2 × minmax(80px, 1fr) in 288 − 6 gap = 282 free →
        // shares 141 each; 141 > 80 min → 141/141.
        val w = widths(listOf(
            GridRenderer.TrackSpec.MinMax(80f, 1f),
            GridRenderer.TrackSpec.MinMax(80f, 1f)))
        assertArrayEquals(floatArrayOf(141f, 141f), w.toFloatArray(), 0.5f)
        // Cramped container: shares (60) fall below the 80px min → mins win.
        val cramped = GridRenderer.computeTrackWidths(
            listOf(GridRenderer.TrackSpec.MinMax(80f, 1f), GridRenderer.TrackSpec.MinMax(80f, 1f)),
            listOf(0f, 0f), containerWidth = 126f, gapPx = 6f)
        assertArrayEquals(floatArrayOf(80f, 80f), cramped.toFloatArray(), 0.5f)
    }
}
