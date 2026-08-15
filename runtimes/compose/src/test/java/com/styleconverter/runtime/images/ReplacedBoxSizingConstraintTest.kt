package com.styleconverter.runtime.images

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wave-42 lane W6 — unit pins for CSS 2.1 §10.4 constraint-violation sizing of
 * replaced elements ([ReplacedBoxSizing.constrainAutoSize] and its wire-facing
 * caller [ReplacedImageContent.constrainedAutoContentSize]).
 *
 * The corpus rows use the EXACT geometry the css-ui box-sizing-007..025 family
 * delivers once the wave-40 SVG pre-raster hop stands in for the vectors
 * (tools/titan/svg-preraster.mjs's probed natural sizes):
 *
 *   w100_h100 / w100_r1-1 / h100_r1-1 → 100×100   r1-1 → 150×150
 *   w100 → 100×150   h100 → 300×100
 *
 * and the CONTENT-BOX bounds each test's declared border-box min/max resolves
 * to after its 30px padding band (e.g. `max-height: 100px` + `padding-bottom:
 * 30px` under `box-sizing: border-box` → content max-height 70). The Swift
 * twin (ReplacedBoxSizingConstraintTests) pins the identical numbers — the
 * two natives must agree on this table to the pixel.
 */
class ReplacedBoxSizingConstraintTest {

    /** Shorthand: run the table and assert both axes to 1e-3 px. */
    private fun assertUsed(
        expectedW: Float, expectedH: Float,
        w: Float, h: Float, ratio: Float?,
        minW: Float? = null, maxW: Float? = null,
        minH: Float? = null, maxH: Float? = null,
    ) {
        val used = ReplacedBoxSizing.constrainAutoSize(
            intrinsicWidthPx = w, intrinsicHeightPx = h, aspectRatio = ratio,
            minWidthPx = minW, maxWidthPx = maxW, minHeightPx = minH, maxHeightPx = maxH,
        )
        assertEquals("width", expectedW, used.widthPx, 1e-3f)
        assertEquals("height", expectedH, used.heightPx, 1e-3f)
    }

    // ── Row 1: no violation — the identity every pre-wave-42 caller needs ──

    @Test
    fun `no declared bounds is the identity on the intrinsic size`() {
        // The whole dark-stage corpus (no min/max on any replaced element)
        // must render byte-identical through the new path.
        assertUsed(100f, 100f, w = 100f, h = 100f, ratio = 1f)
        assertUsed(300f, 100f, w = 300f, h = 100f, ratio = 3f)
    }

    @Test
    fun `a bound met exactly is not a violation — only the strict inequality fires`() {
        // box-sizing-018's h100_r1-1 under max-height 100: h == maxH is NOT a
        // violation (§10.4 uses strict inequalities), so only maxW 70 fires
        // and the single-violation row (not row 6/7) resolves to 70×70.
        assertUsed(70f, 70f, w = 100f, h = 100f, ratio = 1f, maxW = 70f, maxH = 100f)
    }

    // ── Rows 2/3: max on one axis re-solves the other via the ratio ────────

    @Test
    fun `max-height violation re-solves width through the ratio`() {
        // box-sizing-010/014 (100×100, content maxH 70) → the ref's 70×70.
        assertUsed(70f, 70f, w = 100f, h = 100f, ratio = 1f, maxH = 70f)
        // box-sizing-016 (r1-1 pre-rastered at 150×150) — the wave-41 T2
        // blocker: this used to paint 150 wide and wrap; §10.4 gives 70×70.
        assertUsed(70f, 70f, w = 150f, h = 150f, ratio = 1f, maxH = 70f)
        // box-sizing-019 (maxW 100 satisfied, maxH 70 violated) → 70×70.
        assertUsed(70f, 70f, w = 100f, h = 100f, ratio = 1f, maxW = 100f, maxH = 70f)
        // box-sizing-012 (w100 pre-rastered at 100×150): the raster ASSERTS a
        // 2:3 ratio the real vector never had (svg-preraster.mjs limitation
        // 2), so the honest §10.4 answer is 46.667×70 — NOT the browser ref's
        // 100×70. Pinned so the divergence is a documented number, not drift.
        assertUsed(70f * 100f / 150f, 70f, w = 100f, h = 150f, ratio = 100f / 150f, maxH = 70f)
    }

    @Test
    fun `max-width violation re-solves height through the ratio`() {
        // box-sizing-011/015/017 (content maxW 70) → 70×70 on every 1:1 raster.
        assertUsed(70f, 70f, w = 100f, h = 100f, ratio = 1f, maxW = 70f)
        assertUsed(70f, 70f, w = 150f, h = 150f, ratio = 1f, maxW = 70f)
        // box-sizing-013 (h100 pre-rastered at 300×100, ratio 3): 70×23.333 —
        // again the raster's asserted ratio, not the vector's absent one.
        assertUsed(70f, 70f * 100f / 300f, w = 300f, h = 100f, ratio = 3f, maxW = 70f)
    }

    @Test
    fun `the re-solved axis is floored at its own min`() {
        // Row 2's max(…, min-height) arm: 200×100 capped to maxW 100 would
        // derive h 50, but minH 80 floors it — the spec's cross-clamp.
        assertUsed(100f, 80f, w = 200f, h = 100f, ratio = 2f, maxW = 100f, minH = 80f)
    }

    // ── Rows 4/5: min on one axis re-solves the other via the ratio ────────

    @Test
    fun `min-height violation re-solves width through the ratio`() {
        // box-sizing-020/024 (100×100, content minH 130) → the ref's 130×130.
        assertUsed(130f, 130f, w = 100f, h = 100f, ratio = 1f, minH = 130f)
        // box-sizing-023 (h100 pre-rastered at 300×100): 390×130 — the honest
        // §10.4 answer for the raster's asserted 3:1 ratio (ref: 300×130).
        assertUsed(130f * 300f / 100f, 130f, w = 300f, h = 100f, ratio = 3f, minH = 130f)
    }

    @Test
    fun `min-width violation re-solves height through the ratio`() {
        // box-sizing-021/025 (100×100, content minW 130) → the ref's 130×130.
        assertUsed(130f, 130f, w = 100f, h = 100f, ratio = 1f, minW = 130f)
        // box-sizing-022 (w100 pre-rastered at 100×150): 130×195 (ref 130×150).
        assertUsed(130f, 130f * 150f / 100f, w = 100f, h = 150f, ratio = 100f / 150f, minW = 130f)
    }

    @Test
    fun `the re-solved axis is capped at its own max`() {
        // Row 4's min(…, max-height) arm: 100×200 grown to minW 200 would
        // derive h 400, but maxH 300 caps it.
        assertUsed(200f, 300f, w = 100f, h = 200f, ratio = 0.5f, minW = 200f, maxH = 300f)
    }

    // ── Rows 6/7: both too large — the MOST violated axis drives ───────────

    @Test
    fun `both maxes violated shrink by the smaller scale factor`() {
        // maxW/w = 0.25 ≤ maxH/h = 0.9 → width drives: (50, 25).
        assertUsed(50f, 25f, w = 200f, h = 100f, ratio = 2f, maxW = 50f, maxH = 90f)
        // maxW/w = 0.95 > maxH/h = 0.25 → height drives: (50, 25).
        assertUsed(50f, 25f, w = 200f, h = 100f, ratio = 2f, maxW = 190f, maxH = 25f)
    }

    // ── Rows 8/9: both too small — the MOST violated axis drives ───────────

    @Test
    fun `both mins violated grow by the larger scale factor`() {
        // minW/w = 1.1 ≤ minH/h = 1.4 → height drives: (min(∞, 280), 140).
        assertUsed(280f, 140f, w = 200f, h = 100f, ratio = 2f, minW = 220f, minH = 140f)
        // minW/w = 1.5 > minH/h = 1.1 → width drives: (300, min(∞, 150)) = (300, 150).
        assertUsed(300f, 150f, w = 200f, h = 100f, ratio = 2f, minW = 300f, minH = 110f)
    }

    @Test
    fun `the row-9 derived height is capped by max-height, not floored at min`() {
        // The wave-42 skeptic vector: both mins violated AND the derived axis
        // over-shoots a declared max. 50×50 raster, minW 100 / minH 70 /
        // maxH 70 → minW/w = 2.0 > minH/h = 1.4, so row 9 fires and derives
        // h = 100·50/50 = 100 — which max-height 70 must CAP. §10.4 row 9:
        // "min-width, min(max-height, min-width·h/w)" → 100×70. The
        // pre-fix `max(min-height, …)` transcription returned 100×100, i.e.
        // 30px of declared max-height silently ignored.
        assertUsed(100f, 70f, w = 50f, h = 50f, ratio = 1f, minW = 100f, minH = 70f, maxH = 70f)
    }

    // ── Row 10: opposite squeezes abandon the ratio ────────────────────────

    @Test
    fun `opposite-direction violations take both bounds and drop the ratio`() {
        // (w < minW) and (h > maxH) → (minW, maxH) verbatim.
        assertUsed(130f, 70f, w = 100f, h = 100f, ratio = 1f, minW = 130f, maxH = 70f)
        // (w > maxW) and (h < minH) → (maxW, minH) verbatim.
        assertUsed(70f, 130f, w = 100f, h = 100f, ratio = 1f, maxW = 70f, minH = 130f)
    }

    // ── §10.4 preamble: max(min, max), and the no-ratio fallback ───────────

    @Test
    fun `a max below its min is raised to the min before the table runs`() {
        // §10.4: "use max(min, max)": maxW 50 under minW 80 becomes 80.
        assertUsed(80f, 80f, w = 100f, h = 100f, ratio = 1f, minW = 80f, maxW = 50f)
    }

    @Test
    fun `a degenerate ratio clamps each axis independently`() {
        // No ratio ⇒ nothing links the axes: minW grows width, maxH shrinks
        // height, neither propagates (§10.4's non-ratio algorithm).
        assertUsed(120f, 70f, w = 100f, h = 100f, ratio = null, minW = 120f, maxH = 70f)
        assertUsed(120f, 70f, w = 100f, h = 100f, ratio = 0f, minW = 120f, maxH = 70f)
    }

    // ── the wire half: border-box band subtraction in the caller ───────────

    /** {"type":"length","px":N} — the Min/Max* wire shape the converter emits. */
    private fun lengthPx(px: Int): JsonElement =
        buildJsonObject { put("type", "length"); put("px", px) }

    /** {"px":N} — the Padding* wire shape the converter emits. */
    private fun px(px: Int): JsonElement = buildJsonObject { put("px", px) }

    @Test
    fun `explicit border-box subtracts the padding band from the bound`() {
        // box-sizing-010's exact IR: border-box, padding-bottom 30, max-height
        // 100 → content maxH 70 → the ref's 70×70 square.
        val used = ReplacedImageContent.constrainedAutoContentSize(
            properties = listOf(
                "BoxSizing" to JsonPrimitive("BORDER_BOX"),
                "PaddingBottom" to px(30),
                "MaxHeight" to lengthPx(100),
            ),
            wptCaptureMode = false,
            intrinsicWidthPx = 100f, intrinsicHeightPx = 100f, aspectRatio = 1f,
        )
        assertEquals(70f, used.widthPx, 1e-3f)
        assertEquals(70f, used.heightPx, 1e-3f)
    }

    @Test
    fun `the width-axis band comes from horizontal padding only`() {
        // box-sizing-021's exact IR: border-box, padding-RIGHT 30, min-width
        // 160 → content minW 130 → the ref's 130×130 square. The vertical
        // axis keeps its full (absent) bounds — no cross-axis leakage.
        val used = ReplacedImageContent.constrainedAutoContentSize(
            properties = listOf(
                "BoxSizing" to JsonPrimitive("BORDER_BOX"),
                "PaddingRight" to px(30),
                "MinWidth" to lengthPx(160),
            ),
            wptCaptureMode = false,
            intrinsicWidthPx = 100f, intrinsicHeightPx = 100f, aspectRatio = 1f,
        )
        assertEquals(130f, used.widthPx, 1e-3f)
        assertEquals(130f, used.heightPx, 1e-3f)
    }

    @Test
    fun `unset box-sizing outside WPT mode keeps the border-box status quo`() {
        // No BoxSizing on the wire, wptCaptureMode false → the chain treats
        // declared sizes as border-box (SizingApplier.effectiveBoxSizing's
        // pinned tri-state), so the band still subtracts.
        val used = ReplacedImageContent.constrainedAutoContentSize(
            properties = listOf(
                "PaddingBottom" to px(30),
                "MaxHeight" to lengthPx(100),
            ),
            wptCaptureMode = false,
            intrinsicWidthPx = 100f, intrinsicHeightPx = 100f, aspectRatio = 1f,
        )
        assertEquals(70f, used.widthPx, 1e-3f)
        assertEquals(70f, used.heightPx, 1e-3f)
    }

    @Test
    fun `unset box-sizing in WPT mode is content-box and keeps the full bound`() {
        // css-sizing-3 §3's initial value IS content-box, and the WPT refs are
        // authored against it — so the declared maxH 100 is already a content
        // bound and 100×100 does not violate it: identity.
        val used = ReplacedImageContent.constrainedAutoContentSize(
            properties = listOf(
                "PaddingBottom" to px(30),
                "MaxHeight" to lengthPx(100),
            ),
            wptCaptureMode = true,
            intrinsicWidthPx = 100f, intrinsicHeightPx = 100f, aspectRatio = 1f,
        )
        assertEquals(100f, used.widthPx, 1e-3f)
        assertEquals(100f, used.heightPx, 1e-3f)
    }

    @Test
    fun `an explicit none bound is unbounded, not zero`() {
        // `max-height: none` serialises as {"type":"none"} → not an Exact px
        // → no bound. Collapsing it to 0 would shrink every unconstrained box.
        val used = ReplacedImageContent.constrainedAutoContentSize(
            properties = listOf(
                "MaxHeight" to buildJsonObject { put("type", "none") },
            ),
            wptCaptureMode = false,
            intrinsicWidthPx = 100f, intrinsicHeightPx = 100f, aspectRatio = 1f,
        )
        assertEquals(100f, used.widthPx, 1e-3f)
        assertEquals(100f, used.heightPx, 1e-3f)
    }

    @Test
    fun `a bound smaller than its own band floors at a zero content box`() {
        // css-ui-3 §5 (the BorderBoxFloor rule): max-height 20 inside a 30px
        // padding band leaves a 0 content bound — the content vanishes, the
        // padding does not. Width follows through the ratio to 0 as well.
        val used = ReplacedImageContent.constrainedAutoContentSize(
            properties = listOf(
                "BoxSizing" to JsonPrimitive("BORDER_BOX"),
                "PaddingBottom" to px(30),
                "MaxHeight" to lengthPx(20),
            ),
            wptCaptureMode = false,
            intrinsicWidthPx = 100f, intrinsicHeightPx = 100f, aspectRatio = 1f,
        )
        assertEquals(0f, used.widthPx, 1e-3f)
        assertEquals(0f, used.heightPx, 1e-3f)
    }
}
