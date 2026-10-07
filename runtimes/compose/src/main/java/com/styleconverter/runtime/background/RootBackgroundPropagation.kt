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
// frame; any other stack leaves the frame at the colour-layer value. Those
// uniformity predicates live in RootBackgroundUniformity.kt (fix-pass split).

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
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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

    /** PNG IHDR 1×1 test, public for the harness pins (the rule lives in [RootBackgroundUniformity]). */
    fun dataPngIs1x1(url: String): Boolean = RootBackgroundUniformity.dataPngIs1x1(url)

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
        if (layers.isEmpty() || layers.all(RootBackgroundUniformity::isNone)) return null   // nothing to paint
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
        val uniform = RootBackgroundUniformity.stackIsUniform(layers, rawList(props, "BackgroundRepeat"))
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
        val full = ColorExtractor.extractColorConfig(props.map { it.type to it.data })  // the runtime's own reading
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

    /** The canvas paint as data: per-layer configs BOTTOM-UP (the order the chain applies them) + the F2 overpaint. */
    data class CanvasPaint(val bottomUp: List<ColorConfig>, val overpaintFrame: Boolean)

    /**
     * The canvas-surface composition, pure so the JVM pins it (wave-53 fix pass,
     * L3 skeptic should-fix 1): Compose chains paint later entries on top, so the
     * LAST source layer is applied first; tile origins sit at the ICB corner =
     * [frame]; only a non-uniform stack gets the frame band repainted (F2).
     */
    fun canvasPaintPlan(props: List<IRProperty>, plan: Plan, frame: Float): CanvasPaint =
        CanvasPaint(layerConfigs(props, plan, frame).asReversed(), overpaintFrame = !plan.uniform)

    /**
     * The canvas-surface paint: [canvasPaintPlan]'s layers bottom-up over the
     * framed surface, and — for a non-uniform stack — the 16-px frame band
     * repainted in [frameColor] above them (capture-frame chrome: padPngBuffer
     * fills the frame with the pad colour). [Modifier] itself (identity under
     * `then`) without a plan.
     */
    fun canvasModifier(props: List<IRProperty>, plan: Plan?, frame: Dp, frameColor: Color): Modifier {
        if (plan == null) return Modifier                                  // identity: `then(Modifier)` is a no-op
        val paint = canvasPaintPlan(props, plan, frame.value)              // the pinned composition
        var m: Modifier = Modifier
        paint.bottomUp.forEach { m = ColorApplier.applyColors(m, it) }     // first applied = bottom-most
        if (!paint.overpaintFrame) return m                                // F2: the stack may cover the frame
        return m.drawBehind {                                              // drawn above the layers, below content
            val f = frame.toPx(); val w = size.width; val h = size.height  // the frame band, in this box's px
            drawRect(frameColor, Offset.Zero, Size(w, f))                       // top band
            drawRect(frameColor, Offset(0f, h - f), Size(w, f))                 // bottom band
            drawRect(frameColor, Offset(0f, f), Size(f, h - 2 * f))             // left band
            drawRect(frameColor, Offset(w - f, f), Size(f, h - 2 * f))          // right band
        }
    }
}
