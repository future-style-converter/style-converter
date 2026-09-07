package com.styleconverter.runtime.sizing

// Retro R2 (A11#10) — CSS 2.1 §10.4 / §10.7 min/max precedence on the Compose
// sizing lane, in the two places Compose's stock modifiers got it wrong:
//
//  1. MIN OVER MAX. `widthIn(min, max)` / `heightIn(min, max)` are SizeNodes,
//     which coerce `min` DOWN to `max` when min > max — max wins. CSS says the
//     opposite ("if the computed value of min-width is greater than the value
//     of max-width, max-width is set to the value of min-width"). Measured:
//     fixtures/fidelity/pairwise/pairs-06 PW_Sizing_Transforms_04
//     (`min-block-size: 80px; max-block-size: 50px; block-size: auto`) —
//     Android canvas 82 tall (50 + 2×16 padding, max won) vs iOS/web 112.
//  2. PERCENT + MIN/MAX. `fillMaxWidth(f)` OUTER hands the child a tight
//     band, so the INNER `widthIn(min)` can never grow it. Measured:
//     pairs-01 PW_Background_Sizing_04 (`inline-size: 50%; min-inline-size:
//     300px`) — Android 179 wide (358 × 0.5) vs iOS/web 300.
//
// Both decisions are pure and pinned here on the JVM (no Compose runtime); the
// wiring is locked by a source scan, the SizingOverflowWidthTest pattern.
// Wire shapes are the converter's own output for the two fixtures (retro R2
// scratch run of `convert --from css --to ir`).

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.spacing.SpacingContext
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SizingMinMaxClampTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(type: String, json: String) = type to parse(json)

    // ── 1. minMaxBand: the widthIn/heightIn band with CSS precedence ───────

    @Test fun `min above max raises max to min - pairs-06 027 numbers`() {
        // min-block-size 80 / max-block-size 50 → the band is 80..80, so the
        // auto-height box measures 80 tall (+ padding = the 112 canvas).
        assertEquals(80.dp to 80.dp, SizingClamps.minMaxBand(80.dp, 50.dp))
    }

    @Test fun `ordinary bands are unchanged`() {
        // min ≤ max: identical to the former `min ?: 0.dp, max ?: Infinity`.
        assertEquals(50.dp to 80.dp, SizingClamps.minMaxBand(50.dp, 80.dp))
        assertEquals(0.dp to 50.dp, SizingClamps.minMaxBand(null, 50.dp))
        assertEquals(80.dp to Dp.Infinity, SizingClamps.minMaxBand(80.dp, null))
        assertEquals(60.dp to 60.dp, SizingClamps.minMaxBand(60.dp, 60.dp))
    }

    @Test fun `no bounds means no modifier`() {
        assertNull(SizingClamps.minMaxBand(null, null))
    }

    // ── 2. percentClampSpec: which axes take the new lane ──────────────────

    @Test fun `pairs-01 031 verbatim IR routes its inline axis to the percent clamp`() {
        // Converter output for PW_Background_Sizing_04: `InlineSize: 50.0`
        // (bare-number percent), `MinInlineSize: {"px":300.0}`.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("BackgroundClip", """["CONTENT_BOX"]"""),
            pair("BackgroundPositionInline", """{"type":"percentage","percentage":75.0}"""),
            pair("MinBlockSize", """{"px":80.0}"""),
            pair("MinInlineSize", """{"px":300.0}"""),
            pair("InlineSize", "50.0"),
            pair("Height", """{"type":"length","px":64.0}"""),
        ))
        assertEquals(LengthValue.Relative(50.0, LengthUnit.PERCENT, null), cfg.width)
        val spec = SizingClamps.percentClampSpec(cfg.width, cfg.minWidth, cfg.maxWidth, SpacingContext())
        assertNotNull(spec)
        assertEquals(0.5f, spec!!.fraction, 0f)
        assertEquals(300.dp, spec.minDp)
        assertNull(spec.maxDp)
        // The height axis is an Exact 64 → not a percent → the old chain.
        assertNull(SizingClamps.percentClampSpec(cfg.height, cfg.minHeight, cfg.maxHeight, SpacingContext()))
    }

    @Test fun `a percent without any bound keeps the fillMax chain`() {
        val pct = LengthValue.Relative(50.0, LengthUnit.PERCENT, null)
        assertNull(SizingClamps.percentClampSpec(pct, null, null, SpacingContext()))
        // Non-percent shapes never route here, bounds or not.
        assertNull(SizingClamps.percentClampSpec(LengthValue.Exact(100.0), LengthValue.Exact(300.0), null, SpacingContext()))
        assertNull(SizingClamps.percentClampSpec(LengthValue.Auto, LengthValue.Exact(300.0), null, SpacingContext()))
        assertNull(SizingClamps.percentClampSpec(null, LengthValue.Exact(300.0), null, SpacingContext()))
    }

    @Test fun `pairs-06 027 verbatim IR - auto block size with min over max`() {
        // Converter output for PW_Sizing_Transforms_04.
        val cfg = SizingExtractor.extractSizingConfig(listOf(
            pair("MinBlockSize", """{"px":80.0}"""),
            pair("BlockSize", "\"auto\""),
            pair("MaxBlockSize", """{"px":50.0}"""),
            pair("Width", """{"type":"length","px":180.0}"""),
        ))
        assertEquals(LengthValue.Auto, cfg.height)
        // Not a percent axis → heightIn carries it, with the min-wins band.
        assertNull(SizingClamps.percentClampSpec(cfg.height, cfg.minHeight, cfg.maxHeight, SpacingContext()))
        assertEquals(80.dp to 80.dp, SizingClamps.minMaxBand(80.dp, 50.dp))
    }

    // ── 3. PercentSizeClamp.band: the layout-time arithmetic ───────────────

    @Test fun `bounded - min wins over the percent-resolved size`() {
        // pairs-01 031 on the 358px content envelope: 358 × 0.5 = 179 →
        // max(179, 300) = 300, tight.
        assertEquals(300 to 300, PercentSizeClamp.band(0, 358, 0.5f, 300, null))
    }

    @Test fun `bounded - max clamps the percent-resolved size`() {
        // min(179, 100) = 100 — the case a reordered widthIn/fill gets wrong (50).
        assertEquals(100 to 100, PercentSizeClamp.band(0, 358, 0.5f, null, 100))
    }

    @Test fun `bounded - min above max wins`() {
        // §10.4: max is raised to min, then max(179, 300) = 300.
        assertEquals(300 to 300, PercentSizeClamp.band(0, 358, 0.5f, 300, 100))
    }

    @Test fun `bounded - no bounds is FillNode parity`() {
        // Not a route SizingApplier takes (spec is null then), pinned so the
        // arithmetic provably degenerates to fillMaxWidth's round(max × f).
        assertEquals(179 to 179, PercentSizeClamp.band(0, 358, 0.5f, null, null))
    }

    @Test fun `bounded - the incoming band still coerces the result`() {
        // FillNode coerces into (minWidth, maxWidth); a min above the incoming
        // max cannot violate the parent's envelope any more than fill could.
        assertEquals(358 to 358, PercentSizeClamp.band(0, 358, 0.5f, 400, null))
    }

    @Test fun `unbounded - percent is unresolvable, only the min-wins band applies`() {
        // Infinite incoming max (a column measure): FillNode was an identity
        // here; the band reproduces SizeNode's heightIn(min = 100) result…
        assertEquals(100 to Constraints.Infinity, PercentSizeClamp.band(0, Constraints.Infinity, 1f, 100, null))
        // …except that min > max raises max (CSS) instead of coercing min.
        assertEquals(80 to 80, PercentSizeClamp.band(0, Constraints.Infinity, 1f, 80, 50))
        // Constraints.constrain semantics: both ends floored at incomingMin.
        assertEquals(120 to 120, PercentSizeClamp.band(120, Constraints.Infinity, 1f, 80, 50))
    }

    // ── 4. Wiring lock (source scan) ───────────────────────────────────────

    private fun applierSource(): String {
        // Test cwd is the :runtime module root (apps/android-harness runs it);
        // fall back to the repo-relative path for other launchers.
        val candidates = listOf(
            File("src/main/java/com/styleconverter/runtime/sizing/SizingApplier.kt"),
            File("runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/SizingApplier.kt"),
            File("../../runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/SizingApplier.kt"),
        )
        return candidates.first { it.exists() }.readText()
    }

    @Test fun `applier routes percent-with-bounds through the clamp and widthIn through the band`() {
        val src = applierSource()
        // Both axes of applySizing and the flex-item variants take the lane.
        assertTrue(src.contains("r.percentWidthClamped(pctW.fraction, pctW.minDp, pctW.maxDp)"))
        assertTrue(src.contains("r.percentHeightClamped(pctH.fraction, pctH.minDp, pctH.maxDp)"))
        // The axis the clamp owns skips the redundant widthIn/heightIn.
        assertTrue(src.contains("if (pctW == null) r = applyWidthIn(r, minWv, maxWv, ctx)"))
        assertTrue(src.contains("if (pctH == null) r = applyHeightIn(r, minHv, maxHv, ctx)"))
        // widthIn/heightIn bands come from minMaxBand, never raw min/max.
        assertTrue(src.contains("SizingClamps.minMaxBand(toDpOrNull(min, ctx), toDpOrNull(max, ctx)) ?: return m"))
        assertTrue(!src.contains("max = mx ?: Dp.Infinity"))
    }
}
