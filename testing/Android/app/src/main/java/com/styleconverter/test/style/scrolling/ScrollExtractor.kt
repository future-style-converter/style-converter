package com.styleconverter.test.style.scrolling

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts scroll-related configuration from IR properties.
 */
object ScrollExtractor {

    /**
     * Extract complete scroll configuration from property pairs.
     */
    fun extractScrollConfig(properties: List<Pair<String, JsonElement?>>): ScrollConfig {
        var behavior = ScrollBehaviorMode.AUTO
        var snapType: ScrollSnapType? = null
        var snapAlign = ScrollSnapAlign()
        var snapStop = ScrollSnapStopMode.NORMAL
        var scrollMargin = ScrollMargin()
        var scrollPadding = ScrollPadding()
        var overscroll = OverscrollConfig()
        var scrollbar = ScrollbarConfig()

        // Margin accumulators
        var marginTop: androidx.compose.ui.unit.Dp? = null
        var marginRight: androidx.compose.ui.unit.Dp? = null
        var marginBottom: androidx.compose.ui.unit.Dp? = null
        var marginLeft: androidx.compose.ui.unit.Dp? = null
        var marginBlockStart: androidx.compose.ui.unit.Dp? = null
        var marginBlockEnd: androidx.compose.ui.unit.Dp? = null
        var marginInlineStart: androidx.compose.ui.unit.Dp? = null
        var marginInlineEnd: androidx.compose.ui.unit.Dp? = null

        // Padding accumulators
        var paddingTop: androidx.compose.ui.unit.Dp? = null
        var paddingRight: androidx.compose.ui.unit.Dp? = null
        var paddingBottom: androidx.compose.ui.unit.Dp? = null
        var paddingLeft: androidx.compose.ui.unit.Dp? = null
        var paddingBlockStart: androidx.compose.ui.unit.Dp? = null
        var paddingBlockEnd: androidx.compose.ui.unit.Dp? = null
        var paddingInlineStart: androidx.compose.ui.unit.Dp? = null
        var paddingInlineEnd: androidx.compose.ui.unit.Dp? = null

        // Overscroll accumulators
        var overscrollX = OverscrollBehaviorMode.AUTO
        var overscrollY = OverscrollBehaviorMode.AUTO

        // Scrollbar accumulators
        var scrollbarWidth = ScrollbarWidth.AUTO
        var scrollbarThumbColor: Color? = null
        var scrollbarTrackColor: Color? = null
        var scrollbarGutter = ScrollbarGutter.AUTO

        for ((type, data) in properties) {
            when (type) {
                // Scroll behavior
                "ScrollBehavior" -> behavior = extractScrollBehavior(data)

                // Scroll snap type
                "ScrollSnapType" -> snapType = extractScrollSnapType(data)

                // Scroll snap align
                "ScrollSnapAlign" -> snapAlign = extractScrollSnapAlign(data)

                // Scroll snap stop
                "ScrollSnapStop" -> snapStop = extractScrollSnapStop(data)

                // Scroll margins - physical
                "ScrollMarginTop" -> marginTop = ValueExtractors.extractDp(data)
                "ScrollMarginRight" -> marginRight = ValueExtractors.extractDp(data)
                "ScrollMarginBottom" -> marginBottom = ValueExtractors.extractDp(data)
                "ScrollMarginLeft" -> marginLeft = ValueExtractors.extractDp(data)

                // Scroll margins - logical
                "ScrollMarginBlockStart" -> marginBlockStart = ValueExtractors.extractDp(data)
                "ScrollMarginBlockEnd" -> marginBlockEnd = ValueExtractors.extractDp(data)
                "ScrollMarginInlineStart" -> marginInlineStart = ValueExtractors.extractDp(data)
                "ScrollMarginInlineEnd" -> marginInlineEnd = ValueExtractors.extractDp(data)

                // Scroll padding - physical
                "ScrollPaddingTop" -> paddingTop = ValueExtractors.extractDp(data)
                "ScrollPaddingRight" -> paddingRight = ValueExtractors.extractDp(data)
                "ScrollPaddingBottom" -> paddingBottom = ValueExtractors.extractDp(data)
                "ScrollPaddingLeft" -> paddingLeft = ValueExtractors.extractDp(data)

                // Scroll padding - logical
                "ScrollPaddingBlockStart" -> paddingBlockStart = ValueExtractors.extractDp(data)
                "ScrollPaddingBlockEnd" -> paddingBlockEnd = ValueExtractors.extractDp(data)
                "ScrollPaddingInlineStart" -> paddingInlineStart = ValueExtractors.extractDp(data)
                "ScrollPaddingInlineEnd" -> paddingInlineEnd = ValueExtractors.extractDp(data)

                // Overscroll behavior
                "OverscrollBehavior" -> {
                    val (x, y) = extractOverscrollBehavior(data)
                    overscrollX = x
                    overscrollY = y
                }
                "OverscrollBehaviorX", "OverscrollBehaviorInline" -> {
                    overscrollX = extractOverscrollBehaviorMode(data)
                }
                "OverscrollBehaviorY", "OverscrollBehaviorBlock" -> {
                    overscrollY = extractOverscrollBehaviorMode(data)
                }

                // Scrollbar
                "ScrollbarWidth" -> scrollbarWidth = extractScrollbarWidth(data)
                "ScrollbarColor" -> {
                    val (thumb, track) = extractScrollbarColors(data)
                    scrollbarThumbColor = thumb
                    scrollbarTrackColor = track
                }
                "ScrollbarGutter" -> scrollbarGutter = extractScrollbarGutter(data)
            }
        }

        // Build final configs
        scrollMargin = ScrollMargin(
            top = marginTop,
            right = marginRight,
            bottom = marginBottom,
            left = marginLeft,
            blockStart = marginBlockStart,
            blockEnd = marginBlockEnd,
            inlineStart = marginInlineStart,
            inlineEnd = marginInlineEnd
        )

        scrollPadding = ScrollPadding(
            top = paddingTop,
            right = paddingRight,
            bottom = paddingBottom,
            left = paddingLeft,
            blockStart = paddingBlockStart,
            blockEnd = paddingBlockEnd,
            inlineStart = paddingInlineStart,
            inlineEnd = paddingInlineEnd
        )

        overscroll = OverscrollConfig(x = overscrollX, y = overscrollY)

        scrollbar = ScrollbarConfig(
            width = scrollbarWidth,
            thumbColor = scrollbarThumbColor,
            trackColor = scrollbarTrackColor,
            gutter = scrollbarGutter
        )

        return ScrollConfig(
            behavior = behavior,
            snapType = snapType,
            snapAlign = snapAlign,
            snapStop = snapStop,
            scrollMargin = scrollMargin,
            scrollPadding = scrollPadding,
            overscroll = overscroll,
            scrollbar = scrollbar
        )
    }

    private fun extractScrollBehavior(data: JsonElement?): ScrollBehaviorMode {
        val keyword = ValueExtractors.extractKeyword(data)?.lowercase() ?: return ScrollBehaviorMode.AUTO
        return when (keyword) {
            "smooth" -> ScrollBehaviorMode.SMOOTH
            else -> ScrollBehaviorMode.AUTO
        }
    }

    private fun extractScrollSnapType(data: JsonElement?): ScrollSnapType? {
        if (data == null) return null

        val obj = data as? JsonObject ?: return null
        val snapTypeObj = obj["snapType"] as? JsonObject ?: obj

        val axisStr = snapTypeObj["axis"]?.jsonPrimitive?.contentOrNull
        val strictnessStr = snapTypeObj["strictness"]?.jsonPrimitive?.contentOrNull

        val axis = when (axisStr?.uppercase()) {
            "X" -> ScrollSnapAxis.X
            "Y" -> ScrollSnapAxis.Y
            "BLOCK" -> ScrollSnapAxis.BLOCK
            "INLINE" -> ScrollSnapAxis.INLINE
            "BOTH" -> ScrollSnapAxis.BOTH
            else -> return null
        }

        val strictness = when (strictnessStr?.uppercase()) {
            "MANDATORY" -> ScrollSnapStrictness.MANDATORY
            "PROXIMITY" -> ScrollSnapStrictness.PROXIMITY
            else -> ScrollSnapStrictness.PROXIMITY
        }

        return ScrollSnapType(axis = axis, strictness = strictness)
    }

    private fun extractScrollSnapAlign(data: JsonElement?): ScrollSnapAlign {
        if (data == null) return ScrollSnapAlign()

        val obj = data as? JsonObject
        if (obj != null) {
            val blockStr = obj["block"]?.jsonPrimitive?.contentOrNull
                ?: obj["blockAxis"]?.jsonPrimitive?.contentOrNull
            val inlineStr = obj["inline"]?.jsonPrimitive?.contentOrNull
                ?: obj["inlineAxis"]?.jsonPrimitive?.contentOrNull

            return ScrollSnapAlign(
                blockAxis = parseSnapAlignValue(blockStr),
                inlineAxis = parseSnapAlignValue(inlineStr)
            )
        }

        val keyword = ValueExtractors.extractKeyword(data)
        val value = parseSnapAlignValue(keyword)
        return ScrollSnapAlign(blockAxis = value, inlineAxis = value)
    }

    private fun parseSnapAlignValue(str: String?): ScrollSnapAlignValue {
        return when (str?.lowercase()) {
            "start" -> ScrollSnapAlignValue.START
            "center" -> ScrollSnapAlignValue.CENTER
            "end" -> ScrollSnapAlignValue.END
            else -> ScrollSnapAlignValue.NONE
        }
    }

    private fun extractScrollSnapStop(data: JsonElement?): ScrollSnapStopMode {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase() ?: return ScrollSnapStopMode.NORMAL
        return when (keyword) {
            "ALWAYS" -> ScrollSnapStopMode.ALWAYS
            else -> ScrollSnapStopMode.NORMAL
        }
    }

    private fun extractOverscrollBehavior(data: JsonElement?): Pair<OverscrollBehaviorMode, OverscrollBehaviorMode> {
        if (data == null) return Pair(OverscrollBehaviorMode.AUTO, OverscrollBehaviorMode.AUTO)

        val obj = data as? JsonObject
        if (obj != null) {
            val x = extractOverscrollBehaviorMode(obj["x"])
            val y = extractOverscrollBehaviorMode(obj["y"]) ?: x
            return Pair(x, y)
        }

        val both = extractOverscrollBehaviorMode(data)
        return Pair(both, both)
    }

    private fun extractOverscrollBehaviorMode(data: JsonElement?): OverscrollBehaviorMode {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase() ?: return OverscrollBehaviorMode.AUTO
        return when (keyword) {
            "CONTAIN" -> OverscrollBehaviorMode.CONTAIN
            "NONE" -> OverscrollBehaviorMode.NONE
            else -> OverscrollBehaviorMode.AUTO
        }
    }

    private fun extractScrollbarWidth(data: JsonElement?): ScrollbarWidth {
        val keyword = ValueExtractors.extractKeyword(data)?.lowercase() ?: return ScrollbarWidth.AUTO
        return when (keyword) {
            "thin" -> ScrollbarWidth.THIN
            "none" -> ScrollbarWidth.NONE
            else -> ScrollbarWidth.AUTO
        }
    }

    private fun extractScrollbarColors(data: JsonElement?): Pair<Color?, Color?> {
        if (data == null) return Pair(null, null)

        val obj = data as? JsonObject ?: return Pair(null, null)

        val thumbColor = obj["thumbColor"]?.let { ValueExtractors.extractColor(it) }
            ?: obj["thumb"]?.let { ValueExtractors.extractColor(it) }
        val trackColor = obj["trackColor"]?.let { ValueExtractors.extractColor(it) }
            ?: obj["track"]?.let { ValueExtractors.extractColor(it) }

        return Pair(thumbColor, trackColor)
    }

    private fun extractScrollbarGutter(data: JsonElement?): ScrollbarGutter {
        val keyword = ValueExtractors.extractKeyword(data)?.lowercase() ?: return ScrollbarGutter.AUTO

        return when {
            keyword.contains("stable") && keyword.contains("both") -> ScrollbarGutter.STABLE_BOTH_EDGES
            keyword.contains("stable") -> ScrollbarGutter.STABLE
            else -> ScrollbarGutter.AUTO
        }
    }

    /**
     * Check if a property type is scroll-related.
     */
    fun isScrollProperty(type: String): Boolean {
        return type in SCROLL_PROPERTIES
    }

    private val SCROLL_PROPERTIES = setOf(
        "ScrollBehavior",
        "ScrollSnapType",
        "ScrollSnapAlign",
        "ScrollSnapStop",
        "ScrollMarginTop", "ScrollMarginRight", "ScrollMarginBottom", "ScrollMarginLeft",
        "ScrollMarginBlockStart", "ScrollMarginBlockEnd", "ScrollMarginInlineStart", "ScrollMarginInlineEnd",
        "ScrollPaddingTop", "ScrollPaddingRight", "ScrollPaddingBottom", "ScrollPaddingLeft",
        "ScrollPaddingBlockStart", "ScrollPaddingBlockEnd", "ScrollPaddingInlineStart", "ScrollPaddingInlineEnd",
        "OverscrollBehavior", "OverscrollBehaviorX", "OverscrollBehaviorY",
        "OverscrollBehaviorBlock", "OverscrollBehaviorInline",
        "ScrollbarWidth", "ScrollbarColor", "ScrollbarGutter"
    )
}
