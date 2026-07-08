package com.styleconverter.test.style.scrolling

import com.styleconverter.test.style.core.types.ValueExtractors
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts scroll timeline configuration from IR properties.
 */
object ScrollTimelineExtractor {

    fun extractScrollTimelineConfig(properties: List<Pair<String, JsonElement?>>): ScrollTimelineConfig {
        var scrollTimelineName: String? = null
        var scrollTimelineAxis = ScrollTimelineAxisValue.BLOCK
        var viewTimelineName: String? = null
        var viewTimelineAxis = ScrollTimelineAxisValue.BLOCK
        var viewTimelineInset: String? = null
        var animationTimeline: AnimationTimelineValue = AnimationTimelineValue.Auto
        var animationRangeStart: AnimationRangeValue = AnimationRangeValue.Normal
        var animationRangeEnd: AnimationRangeValue = AnimationRangeValue.Normal

        for ((type, data) in properties) {
            when (type) {
                "ScrollTimelineName" -> scrollTimelineName = extractTimelineName(data)
                "ScrollTimelineAxis" -> scrollTimelineAxis = extractTimelineAxis(data)
                "ViewTimelineName" -> viewTimelineName = extractTimelineName(data)
                "ViewTimelineAxis" -> viewTimelineAxis = extractTimelineAxis(data)
                "ViewTimelineInset" -> viewTimelineInset = extractInset(data)
                "AnimationTimeline" -> animationTimeline = extractAnimationTimeline(data)
                "AnimationRangeStart" -> animationRangeStart = extractAnimationRange(data)
                "AnimationRangeEnd" -> animationRangeEnd = extractAnimationRange(data)
            }
        }

        return ScrollTimelineConfig(
            scrollTimelineName = scrollTimelineName,
            scrollTimelineAxis = scrollTimelineAxis,
            viewTimelineName = viewTimelineName,
            viewTimelineAxis = viewTimelineAxis,
            viewTimelineInset = viewTimelineInset,
            animationTimeline = animationTimeline,
            animationRangeStart = animationRangeStart,
            animationRangeEnd = animationRangeEnd
        )
    }

    private fun extractTimelineName(data: JsonElement?): String? {
        if (data == null) return null

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull
                return if (content?.lowercase() == "none") null else content
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull
                if (type?.lowercase() == "none") return null
                return data["name"]?.jsonPrimitive?.contentOrNull
                    ?: data["value"]?.jsonPrimitive?.contentOrNull
            }
            else -> return null
        }
    }

    private fun extractTimelineAxis(data: JsonElement?): ScrollTimelineAxisValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return ScrollTimelineAxisValue.BLOCK

        return when (keyword) {
            "BLOCK" -> ScrollTimelineAxisValue.BLOCK
            "INLINE" -> ScrollTimelineAxisValue.INLINE
            "X" -> ScrollTimelineAxisValue.X
            "Y" -> ScrollTimelineAxisValue.Y
            else -> ScrollTimelineAxisValue.BLOCK
        }
    }

    private fun extractInset(data: JsonElement?): String? {
        if (data == null) return null
        return when (data) {
            is JsonPrimitive -> data.contentOrNull
            is JsonObject -> data["value"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
    }

    /**
     * Extract animation-timeline value from IR data.
     * Handles: auto, none, named references, scroll(), view() functions.
     */
    private fun extractAnimationTimeline(data: JsonElement?): AnimationTimelineValue {
        if (data == null) return AnimationTimelineValue.Auto

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase() ?: return AnimationTimelineValue.Auto
                return when (content) {
                    "auto" -> AnimationTimelineValue.Auto
                    "none" -> AnimationTimelineValue.None
                    else -> AnimationTimelineValue.Named(content)
                }
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
                return when (type) {
                    "auto" -> AnimationTimelineValue.Auto
                    "none" -> AnimationTimelineValue.None
                    "named" -> {
                        val name = data["name"]?.jsonPrimitive?.contentOrNull
                            ?: return AnimationTimelineValue.Auto
                        AnimationTimelineValue.Named(name)
                    }
                    "scroll" -> {
                        val scrollerStr = data["scroller"]?.jsonPrimitive?.contentOrNull?.uppercase()
                        val axisStr = data["axis"]?.jsonPrimitive?.contentOrNull?.uppercase()
                        val scroller = when (scrollerStr) {
                            "ROOT" -> ScrollerValue.ROOT
                            "SELF" -> ScrollerValue.SELF
                            else -> ScrollerValue.NEAREST
                        }
                        val axis = when (axisStr) {
                            "INLINE" -> ScrollTimelineAxisValue.INLINE
                            "X" -> ScrollTimelineAxisValue.X
                            "Y" -> ScrollTimelineAxisValue.Y
                            else -> ScrollTimelineAxisValue.BLOCK
                        }
                        AnimationTimelineValue.Scroll(scroller, axis)
                    }
                    "view" -> {
                        val axisStr = data["axis"]?.jsonPrimitive?.contentOrNull?.uppercase()
                        val axis = when (axisStr) {
                            "INLINE" -> ScrollTimelineAxisValue.INLINE
                            "X" -> ScrollTimelineAxisValue.X
                            "Y" -> ScrollTimelineAxisValue.Y
                            else -> ScrollTimelineAxisValue.BLOCK
                        }
                        val insetStartPx = data["insetStart"]?.jsonObject?.get("px")?.jsonPrimitive?.doubleOrNull
                        val insetEndPx = data["insetEnd"]?.jsonObject?.get("px")?.jsonPrimitive?.doubleOrNull
                        AnimationTimelineValue.View(
                            axis = axis,
                            insetStart = insetStartPx?.dp,
                            insetEnd = insetEndPx?.dp
                        )
                    }
                    else -> AnimationTimelineValue.Auto
                }
            }
            else -> return AnimationTimelineValue.Auto
        }
    }

    /**
     * Extract animation-range value from IR data.
     * Handles: normal, percentage, length, named ranges (cover, contain, entry, exit).
     */
    private fun extractAnimationRange(data: JsonElement?): AnimationRangeValue {
        if (data == null) return AnimationRangeValue.Normal

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase() ?: return AnimationRangeValue.Normal
                return when (content) {
                    "normal" -> AnimationRangeValue.Normal
                    "cover" -> AnimationRangeValue.NamedRange(TimelineRangeName.COVER)
                    "contain" -> AnimationRangeValue.NamedRange(TimelineRangeName.CONTAIN)
                    "entry" -> AnimationRangeValue.NamedRange(TimelineRangeName.ENTRY)
                    "exit" -> AnimationRangeValue.NamedRange(TimelineRangeName.EXIT)
                    "entry-crossing" -> AnimationRangeValue.NamedRange(TimelineRangeName.ENTRY_CROSSING)
                    "exit-crossing" -> AnimationRangeValue.NamedRange(TimelineRangeName.EXIT_CROSSING)
                    else -> {
                        // Try parsing as percentage
                        if (content.endsWith("%")) {
                            val value = content.dropLast(1).toFloatOrNull()
                            if (value != null) return AnimationRangeValue.Percentage(value / 100f)
                        }
                        AnimationRangeValue.Normal
                    }
                }
            }
            is JsonObject -> {
                // Check for percentage
                data["percentage"]?.jsonPrimitive?.doubleOrNull?.let {
                    return AnimationRangeValue.Percentage(it.toFloat() / 100f)
                }
                // Check for length in pixels
                data["px"]?.jsonPrimitive?.doubleOrNull?.let {
                    return AnimationRangeValue.Length(it.dp)
                }
                // Check for named range
                val name = data["name"]?.jsonPrimitive?.contentOrNull?.uppercase()?.replace("-", "_")
                val offset = data["offset"]?.jsonPrimitive?.doubleOrNull?.toFloat()
                if (name != null) {
                    val rangeName = try {
                        TimelineRangeName.valueOf(name)
                    } catch (e: Exception) {
                        null
                    }
                    if (rangeName != null) {
                        return AnimationRangeValue.NamedRange(rangeName, offset)
                    }
                }
                return AnimationRangeValue.Normal
            }
            else -> return AnimationRangeValue.Normal
        }
    }

    fun isScrollTimelineProperty(type: String): Boolean {
        return type in setOf(
            "ScrollTimelineName", "ScrollTimelineAxis",
            "ViewTimelineName", "ViewTimelineAxis", "ViewTimelineInset",
            "AnimationTimeline", "AnimationRangeStart", "AnimationRangeEnd"
        )
    }
}
