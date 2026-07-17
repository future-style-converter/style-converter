package com.styleconverter.runtime.effects.mask

// Lane-M wire-scale tests for MaskExtractor's gradient parsing.
//
// Pixel evidence behind the /100 fix: mask color-stop positions travel
// as IRPercentage raw percents (converter ValueTypes.kt
// IRPercentageSerializer emits `10%` as the bare number 10.0). The old
// extractor fed that straight into coerceIn(0f, 1f), so EVERY stop past
// 1% collapsed to position 1.0 — `mask-image: repeating-linear-gradient(
// 45deg, black 0%, transparent 10%)` rendered as one full-box fade
// instead of a 10%-period stripe pattern (fixtures/properties/effects/
// mask-image.json, MaskImage_RepeatingLinear). These tests pin the
// percent→fraction conversion and the radial shape/pos wire reading.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskGradientWireTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)

    // Canonical MaskImageProperty wire: {"values":[<layer>...]} — the
    // extractor unwraps values[0]. Stops carry {"color":{"srgb":…},
    // "position":<raw percent>} exactly as MaskImageValueSerializer emits.
    private fun maskImage(layerJson: String) = listOf(
        "MaskImage" to parse("""{"values":[$layerJson]}""")
    )

    @Test
    fun `linear stop positions are percent-scaled to fractions`() {
        // black 0%, transparent 10% — the RepeatingLinear fixture's stops.
        val cfg = MaskExtractor.extractMaskConfig(maskImage(
            """{"type":"linear-gradient","angle":{"deg":45.0},"stops":[
                 {"color":{"srgb":{"r":0.0,"g":0.0,"b":0.0,"a":1.0}},"position":0.0},
                 {"color":{"srgb":{"r":0.0,"g":0.0,"b":0.0,"a":0.0}},"position":10.0}
               ]}"""
        ))
        val linear = cfg.gradient as MaskGradientConfig.Linear
        // 0% → 0.0 fraction; 10% → 0.1 fraction (NOT clamped 1.0).
        assertEquals(0.0f, linear.colorStops[0].position, 1e-6f)
        assertEquals(0.1f, linear.colorStops[1].position, 1e-6f)
    }

    @Test
    fun `sub-one percent positions stay sub-percent, not legacy fractions`() {
        // IRPercentageSerializer has no 0–1-normalized producer: a wire
        // 0.5 means 0.5% (fraction 0.005), never 50%. Pins the no-
        // heuristic decision documented in extractSingleColorStop.
        val cfg = MaskExtractor.extractMaskConfig(maskImage(
            """{"type":"linear-gradient","stops":[
                 {"color":"black","position":0.5},
                 {"color":"transparent","position":100.0}
               ]}"""
        ))
        val linear = cfg.gradient as MaskGradientConfig.Linear
        assertEquals(0.005f, linear.colorStops[0].position, 1e-6f)
        assertEquals(1.0f, linear.colorStops[1].position, 1e-6f)
    }

    @Test
    fun `missing positions fall back to the even spread unscaled`() {
        // §3.4.2 auto-placement produces fractions directly — the /100
        // must not touch them (3 stops → 0, 0.5, 1).
        val cfg = MaskExtractor.extractMaskConfig(maskImage(
            """{"type":"linear-gradient","stops":[
                 {"color":"black"},{"color":"white"},{"color":"transparent"}
               ]}"""
        ))
        val linear = cfg.gradient as MaskGradientConfig.Linear
        assertEquals(0.0f, linear.colorStops[0].position, 1e-6f)
        assertEquals(0.5f, linear.colorStops[1].position, 1e-6f)
        assertEquals(1.0f, linear.colorStops[2].position, 1e-6f)
    }

    @Test
    fun `radial shape keyword circle is honoured`() {
        // radial-gradient(circle, …) → wire "shape":"circle".
        val cfg = MaskExtractor.extractMaskConfig(maskImage(
            """{"type":"radial-gradient","shape":"circle","stops":[
                 {"color":"black","position":0.0},
                 {"color":"transparent","position":100.0}
               ]}"""
        ))
        val radial = cfg.gradient as MaskGradientConfig.Radial
        assertEquals(MaskRadialShape.CIRCLE, radial.shape)
    }

    @Test
    fun `radial without shape keyword extracts null for the ellipse default`() {
        // Omitted keyword → null; the applier renders the CSS default
        // ELLIPSE (css-images-3 §3.2) for null and ELLIPSE alike.
        val cfg = MaskExtractor.extractMaskConfig(maskImage(
            """{"type":"radial-gradient","stops":[
                 {"color":"black","position":0.0},
                 {"color":"transparent","position":100.0}
               ]}"""
        ))
        val radial = cfg.gradient as MaskGradientConfig.Radial
        assertNull("omitted shape must stay null (ellipse default)", radial.shape)
    }

    @Test
    fun `radial pos object is percent-scaled to centre fractions`() {
        // "pos":{"x":25,"y":75} is IRPercentage — 0–100 like the stops.
        val cfg = MaskExtractor.extractMaskConfig(maskImage(
            """{"type":"radial-gradient","pos":{"x":25.0,"y":75.0},"stops":[
                 {"color":"black","position":0.0},
                 {"color":"transparent","position":100.0}
               ]}"""
        ))
        val radial = cfg.gradient as MaskGradientConfig.Radial
        assertEquals(0.25f, radial.centerX, 1e-6f)
        assertEquals(0.75f, radial.centerY, 1e-6f)
    }

    @Test
    fun `repeating-linear keeps the repeating flag through extraction`() {
        // The applier's period-tiling branch keys off this flag.
        val cfg = MaskExtractor.extractMaskConfig(maskImage(
            """{"type":"repeating-linear-gradient","angle":{"deg":45.0},"stops":[
                 {"color":"black","position":0.0},
                 {"color":"transparent","position":10.0}
               ]}"""
        ))
        val linear = cfg.gradient as MaskGradientConfig.Linear
        assertTrue("repeating flag must survive extraction", linear.repeating)
    }
}
