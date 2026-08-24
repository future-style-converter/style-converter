package com.styleconverter.runtime.sizing

// Wave-47 lane Z2 — pins the css-logical-1 §4.1 axis swap in
// SizingExtractor: under a VERTICAL writing mode InlineSize declares the
// HEIGHT and BlockSize the WIDTH (the css-break background-image wall's
// 122×472 boxes), while horizontal modes keep the legacy mapping
// byte-identically and baked physical longhands still win by order.

import com.styleconverter.runtime.core.types.LengthValue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SizingVerticalAxisSwapTest {

    private fun px(v: Int): JsonElement = buildJsonObject { put("px", v) }

    // The css-break background-image-001 container: vertical-rl +
    // inline-size 472 → a 472px HEIGHT, width unset (content-hugging).
    @Test
    fun `vertical-rl inline-size declares the height`() {
        val cfg = SizingExtractor.extractSizingConfig(listOf<Pair<String, JsonElement?>>(
            "WritingMode" to JsonPrimitive("VERTICAL_RL"),
            "InlineSize" to px(472),
        ))
        assertEquals(LengthValue.Exact(472.0), cfg.height)
        assertNull(cfg.width)
    }

    // The .mc rows: vertical wm inherited + block-size 120 → a 120px WIDTH.
    @Test
    fun `vertical-lr block-size declares the width, min-max twins follow`() {
        val cfg = SizingExtractor.extractSizingConfig(listOf<Pair<String, JsonElement?>>(
            "WritingMode" to JsonPrimitive("VERTICAL_LR"),
            "BlockSize" to px(120),
            "MinInlineSize" to px(40),
            "MaxBlockSize" to px(300),
        ))
        assertEquals(LengthValue.Exact(120.0), cfg.width)
        assertEquals(LengthValue.Exact(40.0), cfg.minHeight)
        assertEquals(LengthValue.Exact(300.0), cfg.maxWidth)
        assertNull(cfg.height)
    }

    // Horizontal modes: the legacy mapping, byte-identical (no WritingMode
    // entry at all is the whole 327-pair corpus).
    @Test
    fun `horizontal mapping unchanged with and without an explicit htb declaration`() {
        for (props in listOf(
            listOf<Pair<String, JsonElement?>>("InlineSize" to px(50), "BlockSize" to px(10)),
            listOf<Pair<String, JsonElement?>>(
                "WritingMode" to JsonPrimitive("HORIZONTAL_TB"),
                "InlineSize" to px(50), "BlockSize" to px(10)),
        )) {
            val cfg = SizingExtractor.extractSizingConfig(props)
            assertEquals(LengthValue.Exact(50.0), cfg.width)
            assertEquals(LengthValue.Exact(10.0), cfg.height)
        }
    }

    // Post-load-extracted wires bake used physical sizes AFTER the logical
    // entries (the anchor-position-multicol family) — last-occurrence-wins
    // must keep the baked values on top under the swap too.
    @Test
    fun `baked physical width-height after logical entries still win under vertical`() {
        val cfg = SizingExtractor.extractSizingConfig(listOf<Pair<String, JsonElement?>>(
            "WritingMode" to JsonPrimitive("VERTICAL_RL"),
            "InlineSize" to px(100),
            "BlockSize" to px(70),
            // The baked pair, tagged-wrapper shape, AFTER the logical ones.
            "Width" to buildJsonObject { put("type", "length"); put("px", 450) },
            "Height" to buildJsonObject { put("type", "length"); put("px", 20) },
        ))
        assertEquals(LengthValue.Exact(450.0), cfg.width)
        assertEquals(LengthValue.Exact(20.0), cfg.height)
    }
}
