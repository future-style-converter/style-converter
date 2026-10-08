package com.styleconverter.runtime.background

// wave-53 lane L3 (item A) — the F2 "uniform BY CONSTRUCTION" predicates of the
// root canvas background, split out of RootBackgroundPropagation.kt at the
// wave-53 fix pass (PLAN §0 size rule; bodies moved verbatim). A stack may
// cover the 16-px capture frame only when every layer paints one sRGBA AND
// repeats on both axes (capture-browser-ref.mjs padColorFor :728 fills the
// frame with the ring colour only for a uniform ring). Twins: web
// engine/background/RootBackgroundUniformity.ts, SwiftUI
// StyleEngine/background/RootBackgroundUniformity.swift.

import com.styleconverter.runtime.core.images.DataUri
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

internal object RootBackgroundUniformity {

    /** F2 for a whole stack: every layer one colour and every repeat entry `repeat` on both axes. */
    fun stackIsUniform(layers: List<JsonElement>, repeats: List<JsonElement>): Boolean =
        layers.all(::layerIsUniform) && repeats.all(::entryRepeatsBoth)    // no gap, no second colour

    /** A `none` layer (bare string or `{type:"none"}`) paints nothing. */
    fun isNone(l: JsonElement): Boolean =
        (l as? JsonPrimitive)?.contentOrNull == "none" || ((l as? JsonObject)?.get("type") as? JsonPrimitive)?.contentOrNull == "none"

    /** One stop's sRGBA key; null = no colour (hint/shape word), "?" = not static sRGB. */
    private fun stopKey(stop: JsonElement): String? {
        val c = (stop as? JsonObject)?.get("color") ?: return null         // a hint / shape word: no colour
        if (c is JsonNull) return null                                     // an explicit colour-less stop
        val s = (c as? JsonObject)?.get("srgb") as? JsonObject ?: return "?"   // currentColor / var(): unknown
        val ch = listOf("r", "g", "b").map { (s[it] as? JsonPrimitive)?.doubleOrNull ?: return "?" }
        return "${ch[0]},${ch[1]},${ch[2]},${(s["a"] as? JsonPrimitive)?.doubleOrNull ?: 1.0}"  // alpha defaults to 1
    }

    /** css-images-3 §3: every stop shares one sRGBA ⇒ the gradient paints one colour. */
    private fun gradientIsUniform(o: JsonObject): Boolean {
        val keys = (o["stops"] as? JsonArray ?: return false).mapNotNull(::stopKey)  // colour-less entries dropped
        return keys.isNotEmpty() && keys.all { it != "?" && it == keys[0] }          // one static sRGBA
    }

    /** PNG (ISO 15948 §5.2/§11.2.2): signature, then IHDR width/height — true iff 1×1. */
    fun dataPngIs1x1(url: String): Boolean {
        if (!url.lowercase().let { it.startsWith("data:image/png,") || it.startsWith("data:image/png;") }) return false
        val b = DataUri.decode(url) ?: return false                        // the runtime's RFC 2397 decoder
        if (b.size < 24) return false                                      // no full IHDR
        val sig = intArrayOf(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)   // the PNG signature
        if (sig.indices.any { (b[it].toInt() and 0xff) != sig[it] }) return false
        if (String(b, 12, 4, Charsets.US_ASCII) != "IHDR") return false   // the first chunk must be IHDR
        fun be32(o: Int) = (0..3).fold(0L) { acc, k -> (acc shl 8) or (b[o + k].toLong() and 0xff) }  // big-endian
        return be32(16) == 1L && be32(20) == 1L                            // width, height
    }

    /** One layer paints a single colour across its tile (F2's per-layer test). */
    private fun layerIsUniform(l: JsonElement): Boolean {
        if (isNone(l)) return true                                         // transparent everywhere
        val o = l as? JsonObject ?: return false                           // bare URL string: unknown pixels
        (o["url"] as? JsonPrimitive)?.contentOrNull?.let { return dataPngIs1x1(it) }   // a 1×1 raster tile
        val type = (o["type"] as? JsonPrimitive)?.contentOrNull ?: return false
        return type.endsWith("gradient") && gradientIsUniform(o)            // linear/radial/conic, plain or repeating
    }

    /** §3.7: one entry is `repeat` on both axes (string tokens or an {x,y} pair). */
    private fun entryRepeatsBoth(e: JsonElement): Boolean {
        fun rep(v: JsonElement?) = (v as? JsonPrimitive)?.contentOrNull?.lowercase() == "repeat"
        // String shape: `repeat` / `repeat repeat` (any other keyword leaves a gap or a single tile).
        (e as? JsonPrimitive)?.contentOrNull?.let { s -> return s.trim().split(Regex("\\s+")).all { it.lowercase() == "repeat" } }
        val o = e as? JsonObject ?: return false                           // unknown shape: not uniform
        return rep(o["x"]) && rep(o["y"])                                  // axis-pair shape
    }
}
