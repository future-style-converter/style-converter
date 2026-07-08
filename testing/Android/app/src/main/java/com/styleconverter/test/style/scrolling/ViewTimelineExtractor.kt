package com.styleconverter.test.style.scrolling

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts CSS view-timeline configuration from IR properties.
 *
 * ## CSS Properties
 * ```css
 * .element {
 *     view-timeline-name: --reveal;
 *     view-timeline-axis: block;
 *     view-timeline-inset: 0px 100px;
 * }
 *
 * .animated {
 *     animation-timeline: view();
 *     animation-range: entry 0% entry 100%;
 * }
 * ```
 *
 * ## Compose Integration
 * ```kotlin
 * val viewConfig = ViewTimelineExtractor.extractViewTimelineConfig(properties)
 * val viewState = ViewTimelineApplier.rememberViewTimeline(
 *     viewportHeight = LocalConfiguration.current.screenHeightDp.dp,
 *     insetTop = viewConfig.insetTop ?: 0.dp,
 *     insetBottom = viewConfig.insetBottom ?: 0.dp
 * )
 * ```
 */
object ViewTimelineExtractor {

    /**
     * Extract complete view timeline configuration.
     */
    fun extractViewTimelineConfig(properties: List<Pair<String, JsonElement?>>): ViewTimelineConfig {
        var name: String? = null
        var axis = ScrollTimelineAxisValue.BLOCK
        var insetTop: Dp? = null
        var insetBottom: Dp? = null
        var insetStart: Dp? = null
        var insetEnd: Dp? = null
        var animationTimeline: AnimationTimelineValue = AnimationTimelineValue.Auto
        var animationRangeStart: AnimationRangeValue = AnimationRangeValue.Normal
        var animationRangeEnd: AnimationRangeValue = AnimationRangeValue.Normal
        var subject: ViewTimelineSubject = ViewTimelineSubject.AUTO

        for ((type, data) in properties) {
            when (type) {
                "ViewTimelineName" -> name = extractTimelineName(data)
                "ViewTimelineAxis" -> axis = extractTimelineAxis(data)
                "ViewTimelineInset" -> {
                    val insets = extractTimelineInset(data)
                    insetTop = insets.first
                    insetBottom = insets.second
                }
                "AnimationTimeline" -> animationTimeline = extractAnimationTimeline(data)
                "AnimationRangeStart" -> animationRangeStart = extractAnimationRange(data)
                "AnimationRangeEnd" -> animationRangeEnd = extractAnimationRange(data)
                "AnimationRange" -> {
                    val ranges = extractAnimationRangePair(data)
                    animationRangeStart = ranges.first
                    animationRangeEnd = ranges.second
                }
                "ViewTransitionName", "ViewTimelineSubject" -> {
                    subject = extractSubject(data)
                }
            }
        }

        return ViewTimelineConfig(
            name = name,
            axis = axis,
            insetTop = insetTop,
            insetBottom = insetBottom,
            insetStart = insetStart,
            insetEnd = insetEnd,
            animationTimeline = animationTimeline,
            animationRangeStart = animationRangeStart,
            animationRangeEnd = animationRangeEnd,
            subject = subject
        )
    }

    /**
     * Extract view-timeline-name.
     */
    private fun extractTimelineName(data: JsonElement?): String? {
        if (data == null) return null

        return when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull
                if (content?.lowercase() == "none") null else content
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull
                if (type?.lowercase() == "none") return null
                data["name"]?.jsonPrimitive?.contentOrNull
                    ?: data["value"]?.jsonPrimitive?.contentOrNull
            }
            else -> null
        }
    }

    /**
     * Extract view-timeline-axis.
     */
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

    /**
     * Extract view-timeline-inset.
     * Supports: auto, <length>, <length> <length> (top/bottom)
     */
    private fun extractTimelineInset(data: JsonElement?): Pair<Dp?, Dp?> {
        if (data == null) return Pair(null, null)

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull ?: return Pair(null, null)
                if (content.lowercase() == "auto") return Pair(null, null)

                // Single value
                val px = parseLengthToPx(content)
                return Pair(px, px)
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
                if (type == "auto") return Pair(null, null)

                // Check for top/bottom or start/end
                val top = data["top"]?.let { extractDp(it) }
                    ?: data["start"]?.let { extractDp(it) }
                    ?: data["insetTop"]?.let { extractDp(it) }

                val bottom = data["bottom"]?.let { extractDp(it) }
                    ?: data["end"]?.let { extractDp(it) }
                    ?: data["insetBottom"]?.let { extractDp(it) }

                // Single value case
                if (top == null && bottom == null) {
                    val px = data["px"]?.jsonPrimitive?.floatOrNull
                    if (px != null) {
                        val dp = px.dp
                        return Pair(dp, dp)
                    }
                }

                return Pair(top, bottom)
            }
            is JsonArray -> {
                if (data.isEmpty()) return Pair(null, null)

                val first = extractDp(data[0])
                val second = if (data.size > 1) extractDp(data[1]) else first

                return Pair(first, second)
            }
            else -> return Pair(null, null)
        }
    }

    /**
     * Extract animation-timeline value.
     */
    private fun extractAnimationTimeline(data: JsonElement?): AnimationTimelineValue {
        if (data == null) return AnimationTimelineValue.Auto

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase() ?: return AnimationTimelineValue.Auto
                return when (content) {
                    "auto" -> AnimationTimelineValue.Auto
                    "none" -> AnimationTimelineValue.None
                    else -> {
                        // Could be a named timeline reference
                        if (content.startsWith("--")) {
                            AnimationTimelineValue.Named(content)
                        } else {
                            AnimationTimelineValue.Auto
                        }
                    }
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
                        val insetStart = data["insetStart"]?.let { extractDp(it) }
                        val insetEnd = data["insetEnd"]?.let { extractDp(it) }
                        AnimationTimelineValue.View(axis, insetStart, insetEnd)
                    }
                    else -> AnimationTimelineValue.Auto
                }
            }
            else -> return AnimationTimelineValue.Auto
        }
    }

    /**
     * Extract animation-range value.
     */
    private fun extractAnimationRange(data: JsonElement?): AnimationRangeValue {
        if (data == null) return AnimationRangeValue.Normal

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase() ?: return AnimationRangeValue.Normal
                return parseRangeName(content) ?: AnimationRangeValue.Normal
            }
            is JsonObject -> {
                // Check for percentage
                data["percentage"]?.jsonPrimitive?.floatOrNull?.let {
                    return AnimationRangeValue.Percentage(it / 100f)
                }
                // Check for length
                val px = data["px"]?.jsonPrimitive?.floatOrNull
                if (px != null) {
                    return AnimationRangeValue.Length(px.dp)
                }
                // Check for named range
                val name = data["name"]?.jsonPrimitive?.contentOrNull?.lowercase()
                val offset = data["offset"]?.jsonPrimitive?.floatOrNull
                if (name != null) {
                    val rangeName = parseRangeName(name)
                    if (rangeName is AnimationRangeValue.NamedRange) {
                        return AnimationRangeValue.NamedRange(rangeName.name, offset)
                    }
                }
                return AnimationRangeValue.Normal
            }
            else -> return AnimationRangeValue.Normal
        }
    }

    /**
     * Extract animation-range shorthand (start and end).
     */
    private fun extractAnimationRangePair(data: JsonElement?): Pair<AnimationRangeValue, AnimationRangeValue> {
        if (data == null) return Pair(AnimationRangeValue.Normal, AnimationRangeValue.Normal)

        when (data) {
            is JsonObject -> {
                val start = data["start"]?.let { extractAnimationRange(it) }
                    ?: data["rangeStart"]?.let { extractAnimationRange(it) }
                    ?: AnimationRangeValue.Normal
                val end = data["end"]?.let { extractAnimationRange(it) }
                    ?: data["rangeEnd"]?.let { extractAnimationRange(it) }
                    ?: AnimationRangeValue.Normal
                return Pair(start, end)
            }
            is JsonArray -> {
                if (data.isEmpty()) return Pair(AnimationRangeValue.Normal, AnimationRangeValue.Normal)
                val start = extractAnimationRange(data[0])
                val end = if (data.size > 1) extractAnimationRange(data[1]) else start
                return Pair(start, end)
            }
            else -> return Pair(AnimationRangeValue.Normal, AnimationRangeValue.Normal)
        }
    }

    /**
     * Extract view-timeline subject.
     */
    private fun extractSubject(data: JsonElement?): ViewTimelineSubject {
        if (data == null) return ViewTimelineSubject.AUTO

        val keyword = ValueExtractors.extractKeyword(data)?.lowercase()
        return when (keyword) {
            "auto" -> ViewTimelineSubject.AUTO
            "none" -> ViewTimelineSubject.NONE
            else -> ViewTimelineSubject.AUTO
        }
    }

    /**
     * Parse range name keyword to AnimationRangeValue.
     */
    private fun parseRangeName(name: String): AnimationRangeValue? {
        return when (name.lowercase().replace("-", "_")) {
            "normal" -> AnimationRangeValue.Normal
            "cover" -> AnimationRangeValue.NamedRange(TimelineRangeName.COVER)
            "contain" -> AnimationRangeValue.NamedRange(TimelineRangeName.CONTAIN)
            "entry" -> AnimationRangeValue.NamedRange(TimelineRangeName.ENTRY)
            "exit" -> AnimationRangeValue.NamedRange(TimelineRangeName.EXIT)
            "entry_crossing" -> AnimationRangeValue.NamedRange(TimelineRangeName.ENTRY_CROSSING)
            "exit_crossing" -> AnimationRangeValue.NamedRange(TimelineRangeName.EXIT_CROSSING)
            else -> {
                // Try parsing as percentage
                if (name.endsWith("%")) {
                    val value = name.dropLast(1).toFloatOrNull()
                    if (value != null) return AnimationRangeValue.Percentage(value / 100f)
                }
                null
            }
        }
    }

    /**
     * Parse length string to Dp.
     */
    private fun parseLengthToPx(value: String): Dp? {
        val trimmed = value.trim().lowercase()
        return when {
            trimmed.endsWith("px") -> trimmed.dropLast(2).toFloatOrNull()?.dp
            trimmed.endsWith("dp") -> trimmed.dropLast(2).toFloatOrNull()?.dp
            trimmed.endsWith("em") -> (trimmed.dropLast(2).toFloatOrNull()?.times(16))?.dp
            trimmed.endsWith("rem") -> (trimmed.dropLast(3).toFloatOrNull()?.times(16))?.dp
            trimmed.endsWith("%") -> null // Percentages need viewport context
            else -> trimmed.toFloatOrNull()?.dp
        }
    }

    /**
     * Extract Dp from JsonElement.
     */
    private fun extractDp(element: JsonElement): Dp? {
        return when (element) {
            is JsonPrimitive -> element.floatOrNull?.dp
            is JsonObject -> element["px"]?.jsonPrimitive?.floatOrNull?.dp
            else -> null
        }
    }

    /**
     * Check if a property type is a view timeline property.
     */
    fun isViewTimelineProperty(type: String): Boolean {
        return type in VIEW_TIMELINE_PROPERTIES
    }

    private val VIEW_TIMELINE_PROPERTIES = setOf(
        "ViewTimelineName", "ViewTimelineAxis", "ViewTimelineInset",
        "ViewTimeline", // shorthand
        "AnimationTimeline", "AnimationRange", "AnimationRangeStart", "AnimationRangeEnd",
        "ViewTransitionName"
    )
}

/**
 * View timeline configuration.
 */
data class ViewTimelineConfig(
    /** Named timeline identifier */
    val name: String? = null,
    /** Timeline axis (block/inline/x/y) */
    val axis: ScrollTimelineAxisValue = ScrollTimelineAxisValue.BLOCK,
    /** Inset from top of viewport */
    val insetTop: Dp? = null,
    /** Inset from bottom of viewport */
    val insetBottom: Dp? = null,
    /** Logical inset start (maps to top in block axis) */
    val insetStart: Dp? = null,
    /** Logical inset end (maps to bottom in block axis) */
    val insetEnd: Dp? = null,
    /** Animation timeline to use */
    val animationTimeline: AnimationTimelineValue = AnimationTimelineValue.Auto,
    /** Animation range start */
    val animationRangeStart: AnimationRangeValue = AnimationRangeValue.Normal,
    /** Animation range end */
    val animationRangeEnd: AnimationRangeValue = AnimationRangeValue.Normal,
    /** View timeline subject */
    val subject: ViewTimelineSubject = ViewTimelineSubject.AUTO
) {
    /** True if view timeline properties are defined */
    val hasViewTimeline: Boolean
        get() = name != null ||
                axis != ScrollTimelineAxisValue.BLOCK ||
                insetTop != null ||
                insetBottom != null ||
                animationTimeline is AnimationTimelineValue.View

    /** True if using animation-timeline: view() */
    val usesViewTimeline: Boolean
        get() = animationTimeline is AnimationTimelineValue.View

    /** Get effective inset top (resolving logical to physical) */
    val effectiveInsetTop: Dp?
        get() = insetTop ?: insetStart

    /** Get effective inset bottom (resolving logical to physical) */
    val effectiveInsetBottom: Dp?
        get() = insetBottom ?: insetEnd
}

/**
 * View timeline subject type.
 */
enum class ViewTimelineSubject {
    AUTO,
    NONE
}
