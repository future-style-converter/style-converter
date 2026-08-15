package com.styleconverter.runtime

// Wave 42 (lane W8, task d) — the verification the skeptic asked for:
// WHY did wave-41's S3 fix (threading positionOffset into
// FilterApplier.applyGroupColorFilters via the StyleApplier step-3 gate)
// leave css-color/composited-filters-under-opacity.html at EXACTLY 0.9401
// on Android — identical to 4 decimals to wave-40?
//
// Measured first: the wave-40 and wave-41 Android captures are BYTE
// IDENTICAL (sha256 c99be083bf6210c2…, 8718 bytes, both gates). So the S3
// fix changed nothing for this test — but not because it failed to install.
// These pins prove the S3 pipeline IS live for the test's `.content`
// elements (Position ABSOLUTE + Left 50px + filter: invert(100%)): the
// gate's hasFilters arm fires, resolvedOffset carries (50, 0), and the
// group saveLayer bounds cover the offset box. The unchanged pixels come
// from TWO defects OUTER of the filter group, both outside this lane's
// trees (deferred with evidence in the lane report):
//
//  1. PARENT OPACITY CLIP — the test wraps both children in
//     `<div style="opacity: 0.5">`. ColorApplier applies opacity via
//     androidx `Modifier.alpha`, and the shipped bytecode of
//     androidx.compose.ui:ui-android:1.11.4 (AlphaKt.alpha) compiles to
//     `graphicsLayer(alpha = a, clip = TRUE)` — alpha and clip are the
//     only two non-defaulted parameters (default-mask 520187 decoded).
//     CSS opacity creates a transparency group but never clips
//     (css-color-3 §3.2), yet on Android the offset child is cut at the
//     parent's bounds — measured in the capture: the second `.content`
//     (left: 50px) spans x 66..115, cropped at the parent's right edge
//     x=116, which is the SAME pixel column where the child's own
//     pre-S3 saveLayer crop sat (slot origin + 100px box). Identical
//     crop boundary ⇒ identical pixels ⇒ the 4-decimal tie.
//  2. SIBLING STACKING — the second `.content` renders BELOW the first
//     (y 206..355 vs the ref's overlap at one top edge) because
//     ComponentRenderer's `isPositionedContainer` recognises only
//     RELATIVE (and transform-CB) parents; the test's ABSOLUTE parent
//     drops its abspos children into the block Column, where the first
//     child's 150px reserve becomes the second's static top — CSS 2.1
//     §10.1 makes ANY positioned box the containing block, and §10.6.4's
//     static position ignores out-of-flow siblings (they reserve no
//     space).

import com.styleconverter.runtime.effects.filter.FilterExtractor
import com.styleconverter.runtime.effects.filter.FilterGroupGeometry
import com.styleconverter.runtime.layout.position.PositionApplier
import com.styleconverter.runtime.layout.position.PositionExtractor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleApplierFilterOffsetGateTest {

    // The EXACT wire of the second `.content` element (wave41-final
    // per-test-ir/wpt__css-color__composited-filters-under-opacity.json):
    // Left arrives as the bare {"px":50} spelling, Filter as [{fn,v}].
    private fun contentChildPairs(): List<Pair<String, JsonElement?>> = listOf(
        "Position" to Json.parseToJsonElement("\"ABSOLUTE\""),
        "Width" to Json.parseToJsonElement("""{"type":"length","px":100}"""),
        "Height" to Json.parseToJsonElement("""{"type":"length","px":150}"""),
        "Filter" to Json.parseToJsonElement("""[{"fn":"invert","v":100}]"""),
        "Left" to Json.parseToJsonElement("""{"px":50}"""),
    )

    @Test
    fun `the gate's hasFilters arm fires for filter invert`() {
        // StyleApplier step 3 resolves positionOffset only under
        // `hasBackdropFilters || hasFilters || hasShadow`; the element has
        // no backdrop-filter and no shadow, so hasFilters is the live arm —
        // if this decodes empty the whole S3 plumbing is skipped.
        val filters = FilterExtractor.extractFilterConfig(contentChildPairs())
        assertTrue("filter: invert(100%) must extract as an element filter", filters.hasFilters)
    }

    @Test
    fun `resolvedOffset carries the abspos Left inset`() {
        // The value the gate hands to applyGroupColorFilters: (50, 0) — the
        // same absoluteOffset step 4 will chain, read through the same
        // extractor, so the two cannot disagree.
        val position = PositionExtractor.extractPositionConfig(contentChildPairs())
        val offset = PositionApplier.resolvedOffset(position)
        assertEquals(50f, offset.x.value, 0f)
        assertEquals(0f, offset.y.value, 0f)
    }

    @Test
    fun `the S3 saveLayer bounds cover the offset box`() {
        // 100×150 box offset by (50, 0): the group layer must span the
        // union 0..150 × 0..150 — i.e. the child's own filter group no
        // longer crops at x=100. With this proven, the byte-identical
        // capture can only come from a crop OUTER of the group (the parent
        // alpha layer's clip=true — see the class banner).
        val rect = FilterGroupGeometry.groupLayerBounds(
            widthPx = 100f, heightPx = 150f,
            positionOffsetXPx = 50f, positionOffsetYPx = 0f,
        )
        assertEquals(0f, rect.left, 0f)
        assertEquals(0f, rect.top, 0f)
        assertEquals(150f, rect.right, 0f)
        assertEquals(150f, rect.bottom, 0f)
    }
}
