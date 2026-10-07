package com.styleconverter.runtime.background

// wave-53 lane L3 (item A) — the ROOT's background-IMAGE layers propagate to
// the CANVAS (css-backgrounds-3 §2.11.2). The Compose twin of the web runtime's
// engine/background/RootBackgroundPropagation.ts and SwiftUI's
// StyleEngine/background/RootBackgroundPropagation.swift: one rule, three
// canvases. The composed canvas (ScreenshotCaptureScreen.ComposedCaptureCanvas)
// propagated the body-root COLOUR only; a root image was painted by nobody
// (wave52-ship: display-contents-root-background android f 0.5355,
// background-attachment-margin-root-001/-002 android f 0.451 / 0.3389).
//
// The rule: §2.11.2 the canvas paints the root's image layers and the root
// does not paint them again (withCanvasOwnedRootBackground); §3.4 a `fixed`
// layer's tile origin is the viewport (ICB) corner, a `scroll`/`local` one the
// root box (ICB + the canvas-owned root margin); F2 capture-frame chrome —
// capture-browser-ref.mjs padColorFor (:728) fills the 16-px frame with the
// ring colour only for a uniform ring, so only a stack uniform BY
// CONSTRUCTION (every layer one sRGBA, repeating on both axes) may cover the
// frame; any other stack leaves the frame at the colour-layer value.

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.color.BackgroundPositionConfig
import com.styleconverter.runtime.color.ColorApplier
import com.styleconverter.runtime.color.ColorConfig
import com.styleconverter.runtime.color.ColorExtractor
import com.styleconverter.runtime.core.images.DataUri
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

object RootBackgroundPropagation {

    /** One image layer's §3.4 attachment keyword. */
    enum class Attachment { SCROLL, FIXED, LOCAL }                         // the three §3.4 keywords

    /** A layer's tile origin in px (== dp in the capture), relative to the ICB corner. */
    data class LayerOrigin(val x: Float, val y: Float)                    // px right of / below the ICB corner

    /** The propagation decision: per-layer attachments + origins, and the F2 uniformity. */
    data class Plan(
        val attachments: List<Attachment>,                                 // per image layer, source order
        val origins: List<LayerOrigin>,                                    // per image layer, ICB-relative (§3.4)
        val uniform: Boolean,                                              // F2: one colour by construction
    )

    /** The image-layer longhands the canvas takes over (the root never repaints them). */
    val IMAGE_TYPES: Set<String> = setOf(
        "BackgroundImage", "BackgroundSize", "BackgroundRepeat", "BackgroundAttachment",
        "BackgroundPosition", "BackgroundPositionX", "BackgroundPositionY",
        "BackgroundPositionInline", "BackgroundPositionBlock",
        "BackgroundOrigin", "BackgroundClip", "BackgroundBlendMode",
    )

    /** Knobs the origin offset does not compose with — breadcrumbed, no corpus carrier. */
    private val UNMODELLED = listOf("BackgroundPosition", "BackgroundPositionX", "BackgroundPositionY",
        "BackgroundPositionInline", "BackgroundPositionBlock", "BackgroundOrigin", "BackgroundClip",
        "BackgroundBlendMode")

    /** The raw IR layer list of the LAST `type` declaration (scalar wrapped; absent → empty). */
    private fun rawList(props: List<IRProperty>, type: String): List<JsonElement> =
        when (val d = props.lastOrNull { it.type == type }?.data) {        // last declaration wins (cascade)
            null, JsonNull -> emptyList()                                  // absent: the initial value applies
            is JsonArray -> d                                              // the normal per-layer list
            else -> listOf(d)                                              // a scalar: one layer
        }

    /** A `none` layer (bare string or `{type:"none"}`) paints nothing. */
    private fun isNone(l: JsonElement): Boolean =
        (l as? JsonPrimitive)?.contentOrNull == "none" || ((l as? JsonObject)?.get("type") as? JsonPrimitive)?.contentOrNull == "none"

    /** The keyword of one entry: a bare string, or the `type` of an object. */
    private fun keyword(e: JsonElement?): String? =
        (e as? JsonPrimitive)?.contentOrNull ?: ((e as? JsonObject)?.get("type") as? JsonPrimitive)?.contentOrNull

    /** Layer i's attachment (§2.3: shorter lists cycle; unknown → initial `scroll`). */
    private fun attachmentAt(props: List<IRProperty>, i: Int): Attachment {
        val list = rawList(props, "BackgroundAttachment")                 // `[{type:"scroll"},…]` on the wire
        if (list.isEmpty()) return Attachment.SCROLL                       // initial value
        return when (keyword(list[i % list.size])?.lowercase()) {          // §2.3 cyclic pairing
            "fixed" -> Attachment.FIXED                                    // anchors to the viewport
            "local" -> Attachment.LOCAL                                    // anchors like scroll here (no scroller)
            else -> Attachment.SCROLL                                      // `scroll` and anything unknown
        }
    }

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

    /** JVM-safe breadcrumb: PropertyTracker marks the type, android.util.Log is stubbed off-device. */
    private fun breadcrumb(type: String, context: String) {
        runCatching { PropertyTracker.logUnhandled(type, context) }       // marks first, then Log.w (stubbed on JVM)
    }

    /**
     * The plan for one body-root's properties, or null when nothing propagates
     * (no image layer, only `none`, containment — the caller's css-contain-2
     * §2 gate — or a layer the colour extractor cannot paint). [marginTop] /
     * [marginLeft] are the canvas-owned root margin in px.
     */
    fun plan(props: List<IRProperty>, marginTop: Float, marginLeft: Float, contained: Boolean): Plan? {
        if (contained) return null                                         // off the propagation path
        val layers = rawList(props, "BackgroundImage")                     // the raw per-layer list
        if (layers.isEmpty() || layers.all(::isNone)) return null          // nothing to paint
        // Index parity with the applier: a dropped layer would mis-pair every attachment.
        if (ColorExtractor.extractBackgroundImages(JsonArray(layers)).size != layers.size) {
            breadcrumb("BackgroundImage", "root canvas background: a layer the extractor cannot paint"); return null
        }
        UNMODELLED.filter { t -> props.any { it.type == t } }              // no corpus carrier pairs these
            .forEach { breadcrumb(it, "root canvas background: positioning area not modelled") }
        val attachments = layers.indices.map { attachmentAt(props, it) }
        // §3.4: fixed → the viewport (ICB) corner; scroll/local → the root box's padding edge.
        val origins = attachments.map { if (it == Attachment.FIXED) LayerOrigin(0f, 0f) else LayerOrigin(marginLeft, marginTop) }
        // F2: every layer one colour AND repeating on both axes (no gaps) ⇒ the ring is uniform.
        val uniform = layers.all(::layerIsUniform) && rawList(props, "BackgroundRepeat").all(::entryRepeatsBoth)
        return Plan(attachments, origins, uniform)
    }

    /** §2.11.2 "not painted again": the forest with the body-root's image layers removed; identity without a plan. */
    fun withCanvasOwnedRootBackground(roots: List<IRComponent>, plan: Plan?): List<IRComponent> {
        if (plan == null) return roots                                     // nothing owned → the same list
        // Only the synthetic html+body bag is rewritten; id/meta/children ride through.
        return roots.map { r -> if (r.role != "body-root") r else r.copy(properties = r.properties.filterNot { it.type in IMAGE_TYPES }) }
    }

    /**
     * One single-layer [ColorConfig] per image layer (source order), each with
     * the §3.4 origin + [base] as its position offset — the existing applier
     * then paints it. [base] = where the ICB corner sits in the painted box.
     */
    fun layerConfigs(props: List<IRProperty>, plan: Plan, base: Float): List<ColorConfig> {
        val full = ColorExtractor.extractColorConfig(props.map { it.type to it.data })  // the engine's own reading
        return full.backgroundImages.mapIndexed { i, image ->
            val o = plan.origins[i]                                        // plan() pinned the index parity
            ColorConfig(                                                   // no colour, no opacity: image only
                backgroundImages = listOf(image),
                // The §3.4 origin alone (an author position is breadcrumbed in plan(),
                // exactly as the web and SwiftUI twins drop it).
                backgroundPosition = BackgroundPositionConfig(xOffset = (o.x + base).dp, yOffset = (o.y + base).dp),
                backgroundSize = full.backgroundSize,                      // the legacy single-value fallback
                backgroundSizes = listOfNotNull(ColorApplier.layerValue(full.backgroundSizes, i)),     // §2.3 pairing
                backgroundRepeat = full.backgroundRepeat,                  // the legacy single-value fallback
                backgroundRepeats = listOfNotNull(ColorApplier.layerValue(full.backgroundRepeats, i)), // §2.3 pairing
            )
        }
    }

    /**
     * The canvas-surface paint: the layers bottom-up over the framed surface
     * (tile origins at the ICB corner = [frame]), and — for a non-uniform stack —
     * the 16-px frame band repainted in [frameColor] above them (capture-frame
     * chrome: padPngBuffer fills the frame with the pad colour). [Modifier] itself
     * (identity under `then`) without a plan.
     */
    fun canvasModifier(props: List<IRProperty>, plan: Plan?, frame: Dp, frameColor: Color): Modifier {
        if (plan == null) return Modifier                                  // identity: `then(Modifier)` is a no-op
        // Compose chains paint later entries on top: the LAST source layer first.
        var m: Modifier = Modifier
        layerConfigs(props, plan, frame.value).asReversed().forEach { m = ColorApplier.applyColors(m, it) }
        if (plan.uniform) return m                                         // F2: the stack may cover the frame
        return m.drawBehind {                                              // drawn above the layers, below content
            val f = frame.toPx(); val w = size.width; val h = size.height  // the frame band, in this box's px
            drawRect(frameColor, Offset.Zero, Size(w, f))                       // top band
            drawRect(frameColor, Offset(0f, h - f), Size(w, f))                 // bottom band
            drawRect(frameColor, Offset(0f, f), Size(f, h - 2 * f))             // left band
            drawRect(frameColor, Offset(w - f, f), Size(f, h - 2 * f))          // right band
        }
    }
}
