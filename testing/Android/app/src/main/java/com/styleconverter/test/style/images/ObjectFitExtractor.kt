package com.styleconverter.test.style.images

import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import com.styleconverter.test.style.core.types.ValueExtractors
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
            val x = obj["x"]?.jsonPrimitive?.contentOrNull?.lowercase()
            val y = obj["y"]?.jsonPrimitive?.contentOrNull?.lowercase()

            val horizontal = when (x) {
                "left" -> -1f
                "center" -> 0f
                "right" -> 1f
                else -> 0f
            }
            val vertical = when (y) {
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

    fun isObjectFitProperty(type: String): Boolean {
        return type in OBJECT_FIT_PROPERTIES
    }

    private val OBJECT_FIT_PROPERTIES = setOf(
        "ObjectFit", "ObjectPosition"
    )
}
