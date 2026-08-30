package com.styleconverter.runtime.effects.clip

// Wave 49 (lane A4) — the FLATTENED `<position>` arm of circle() / ellipse().
//
// ## The defect, and how it surfaced
// `ClipPathShapeSerializer` hands `IRPropertySerializer.deepFlatten` a shape
// object; when the only non-`type` field is `pos`, deepFlatten inlines that
// one object and the `pos` wrapper DISAPPEARS from the wire. A radius
// KEYWORD (`closest-corner`, `farthest-corner`) is not serialized as a field
// at all, so `circle(closest-corner at 150px 200px)` lands as
//   {"type":"circle","x":{"px":150},"y":{"px":200}}
// The extractor read only `json["pos"]`, so it (a) lost the whole `at`
// clause and re-centred the shape on the box, and (b) then fell through to
// the legacy BARE-NUMBER branch, where `json["x"].jsonPrimitive` THREW
// because `x` is an object.
//
// (b) is the serious half. `extractClipPathConfig` runs for every component
// (EffectsFacade.extractConfig, and since this wave CanvasRootHoist's clip
// ancestry walk), and a throw there takes the whole composition down — it is
// not a dropped property, it is a dead render. MEASURED: the wave-49 skeptic
// walk over all 1435 wave-48 per-test IR documents died with
// `IllegalArgumentException: … JsonObject … is not a JsonPrimitive` at
// ClipPathExtractor.readCenterPercent.
//
// ## The three corpus carriers (every payload below is VERBATIM)
// tools/titan/runs/wave48-final/sections/css-masking/per-test-ir/:
//   • clip-path-circle-closest-corner   {"type":"circle","x":{"px":150},"y":{"px":200}}
//   • clip-path-circle-farthest-corner  {"type":"circle","x":{"px":200},"y":{"px":150}}
//   • clip-path-ellipse-closest-farthest-corner
//                                       {"type":"ellipse","x":{"px":175},"y":{"px":100}}
// All three fail the browser ref on all three platforms at the wave-48 gate
// — tools/titan/runs/wave48-final/sections/css-masking/manifest.json,
// `wpt.results[…].browserRef.diffs`, `wptPass:false` on all nine cells;
// android-ref ssim 0.7107 / 0.6931 / 0.7362 respectively. The WEB runtime
// already reads this wire form correctly (ClipPathExtractor.ts `positionOf`,
// lines 69-73), so this is a parity repair toward the platform that has it
// right, not a new invention.
//
// SCOPE NOTE (wave 49 lane F1): reading the centre correctly is NOT by itself
// enough to flip these three cells. `closest-corner` / `farthest-corner` are
// `<radial-extent>` keywords belonging to css-images-3 §3.2's
// `radial-gradient()` notation, and are NOT part of css-shapes-1 §3.1's
// `<shape-radius>` grammar (`<length-percentage> | closest-side |
// farthest-side`), so the converter's
// `parseShapeRadius` drops them and every runtime falls back to the
// `closest-side` default — web included (web-ref 0.7278 / 0.6544 / 0.8453 in
// the same manifest). That is a separate converter-side gap, deliberately not
// touched here: this lane's job is the composition crash and the centre.

import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipPathFlattenedPositionTest {

    /** One `ClipPath` declaration list, straight from a wire JSON string. */
    private fun clipPath(json: String): List<Pair<String, JsonElement?>> =
        listOf("ClipPath" to Json.parseToJsonElement(json))

    @Test
    fun `flattened px centre is read as the circle centre, not re-centred`() {
        // clip-path-circle-closest-corner's `.test` box, verbatim.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            clipPath("""{"type":"circle","x":{"px":150},"y":{"px":200}}"""),
        )
        val circle = cfg.shape as ClipShape.Circle
        // The definite px coordinates the applier prefers (ClipRadialShapes
        // reads centreXDp before the percentage) — 150px / 200px from the
        // reference box's top-left, exactly what `at 150px 200px` means.
        assertEquals(150f.dp, circle.centerXDp)
        assertEquals(200f.dp, circle.centerYDp)
    }

    @Test
    fun `flattened px centre is read as the ellipse centre`() {
        // clip-path-ellipse-closest-farthest-corner's box, verbatim.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            clipPath("""{"type":"ellipse","x":{"px":175},"y":{"px":100}}"""),
        )
        val ellipse = cfg.shape as ClipShape.Ellipse
        assertEquals(175f.dp, ellipse.centerXDp)
        assertEquals(100f.dp, ellipse.centerYDp)
    }

    @Test
    fun `an object-valued centre never throws — the whole corpus walks it`() {
        // The regression pin for the crash itself: the predicate
        // CanvasRootHoist runs on EVERY component must be total. Asserting
        // "returns something" is the point; a throw fails the test by
        // escaping, which is precisely how the skeptic walk died.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            clipPath("""{"type":"circle","x":{"px":200},"y":{"px":150}}"""),
        )
        assertTrue(cfg.hasClipPath)
        assertNotNull(cfg.shape)
    }

    @Test
    fun `the wrapped pos form is unchanged`() {
        // Byte-compat: a two-field shape never flattens, so `pos` survives
        // and must still be the bag that is read. `circle(50% at 20px 30px)`.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            clipPath(
                """{"type":"circle","r":{"original":{"v":50,"u":"PERCENT"}},""" +
                    """"pos":{"x":{"px":20},"y":{"px":30}}}""",
            ),
        )
        val circle = cfg.shape as ClipShape.Circle
        assertEquals(20f.dp, circle.centerXDp)
        assertEquals(30f.dp, circle.centerYDp)
        assertEquals(ClipRadius.Percentage(50f), circle.radius)
    }

    @Test
    fun `the flattened RADIUS form still reads as a radius, with no centre`() {
        // The OTHER thing deepFlatten inlines: `circle(40px)` puts an
        // IRLength's `px` at the shape root. Its keys are `px` / `original`,
        // never `x` / `y`, so the position helper must stay silent and the
        // shape must keep the spec default centre.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            clipPath("""{"type":"circle","px":40.0}"""),
        )
        val circle = cfg.shape as ClipShape.Circle
        assertEquals(ClipRadius.Fixed(40f.dp), circle.radius)
        assertNull(circle.centerXDp)
        assertNull(circle.centerYDp)
        assertEquals(50f, circle.centerX, 0f)
        assertEquals(50f, circle.centerY, 0f)
    }

    @Test
    fun `legacy bare-number centres still resolve as percentages`() {
        // Pre-canonical hand-written fixtures spell the centre as plain
        // numbers. They are primitives, so the safe cast keeps reading them.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            clipPath("""{"type":"circle","x":30,"y":70}"""),
        )
        val circle = cfg.shape as ClipShape.Circle
        assertEquals(30f, circle.centerX, 0f)
        assertEquals(70f, circle.centerY, 0f)
        assertNull(circle.centerXDp)
    }
}
