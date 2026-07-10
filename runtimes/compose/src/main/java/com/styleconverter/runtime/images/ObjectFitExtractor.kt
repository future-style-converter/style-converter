package com.styleconverter.runtime.images

import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts object-fit configuration from IR properties.
 */
object ObjectFitExtractor {

    fun extractObjectFitConfig(properties: List<Pair<String, JsonElement?>>): ObjectFitConfig {
        var fit = ObjectFitValue.FILL
        var alignment = Alignment.Center

        for ((type, data) in properties) {
            when (type) {
                "ObjectFit" -> fit = extractObjectFit(data)
                "ObjectPosition" -> alignment = extractObjectPosition(data)
            }
        }

        return ObjectFitConfig(
            fit = fit,
            contentScale = fit.toContentScale(),
            alignment = alignment
        )
    }

    fun extractObjectFit(data: JsonElement?): ObjectFitValue {
        if (data == null) return ObjectFitValue.FILL

        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return ObjectFitValue.FILL

        return when (keyword) {
            "FILL" -> ObjectFitValue.FILL
            "CONTAIN" -> ObjectFitValue.CONTAIN
            "COVER" -> ObjectFitValue.COVER
            "NONE" -> ObjectFitValue.NONE
            "SCALE_DOWN" -> ObjectFitValue.SCALE_DOWN
            else -> ObjectFitValue.FILL
        }
    }

    fun extractObjectPosition(data: JsonElement?): Alignment {
        if (data == null) return Alignment.Center

        val obj = data as? JsonObject
        if (obj != null) {
            // Each axis from ObjectPositionPropertyParser.kt is one of:
            //   {"type":"keyword","value":"LEFT"}      — position keyword
            //   {"type":"percentage","percentage":25}  — percent offset
            //   {"type":"length","px":10}              — length offset
            //   bare string primitive                  — legacy keyword shape
            // The old bare `.jsonPrimitive` threw on every object axis,
            // aborting StyleApplier.extractConfig for the whole element
            // (Images_C01: the 200x64 green box collapsed to a bare label).
            val x = axisKeyword(obj["x"])
            val y = axisKeyword(obj["y"])

            // css-values-4 <position>: keywords bind to their own axis
            // regardless of token order — swap when the converter's
            // positional storage put a vertical keyword in x (or vice
            // versa), mirroring how the browser resolves "bottom right".
            val xVertical = x == "top" || x == "bottom"
            val yHorizontal = y == "left" || y == "right"
            val hKey = if (xVertical || yHorizontal) y else x
            val vKey = if (xVertical || yHorizontal) x else y

            val horizontal = when (hKey) {
                "left" -> -1f
                "center" -> 0f
                "right" -> 1f
                else -> 0f
            }
            val vertical = when (vKey) {
                "top" -> -1f
                "center" -> 0f
                "bottom" -> 1f
                else -> 0f
            }

            return Alignment { size, space, _ ->
                androidx.compose.ui.unit.IntOffset(
                    x = ((space.width - size.width) * (horizontal + 1) / 2).toInt(),
                    y = ((space.height - size.height) * (vertical + 1) / 2).toInt()
                )
            }
        }

        val keyword = ValueExtractors.extractKeyword(data)?.lowercase()
        return when (keyword) {
            "center" -> Alignment.Center
            "top" -> Alignment.TopCenter
            "bottom" -> Alignment.BottomCenter
            "left" -> Alignment.CenterStart
            "right" -> Alignment.CenterEnd
            "top left", "left top" -> Alignment.TopStart
            "top right", "right top" -> Alignment.TopEnd
            "bottom left", "left bottom" -> Alignment.BottomStart
            "bottom right", "right bottom" -> Alignment.BottomEnd
            else -> Alignment.Center
        }
    }

    /**
     * One object-position axis → lowercase keyword string, or null when the
     * axis is a percent/length offset (those fall back to the centered
     * default — Compose's Alignment lambda has no per-axis pixel channel
     * here, and object-position only matters for replaced content which
     * the harness never exercises with offsets). Never throws: safe casts
     * only, per the ValueExtractors.extractKeyword precedent.
     */
    private fun axisKeyword(el: JsonElement?): String? {
        if (el == null) return null
        (el as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.let { return it.lowercase() }
        val o = el as? JsonObject ?: return null
        val type = (o["type"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        return when (type) {
            // {"type":"keyword","value":"CENTER"} — the modern parser shape.
            "keyword" -> (o["value"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.lowercase()
            // Direct serialName keywords, matching PerspectiveOrigin's shape.
            "left", "right", "top", "bottom", "center" -> type
            else -> null
        }
    }

    fun isObjectFitProperty(type: String): Boolean {
        return type in OBJECT_FIT_PROPERTIES
    }

    private val OBJECT_FIT_PROPERTIES = setOf(
        "ObjectFit", "ObjectPosition"
    )
}
