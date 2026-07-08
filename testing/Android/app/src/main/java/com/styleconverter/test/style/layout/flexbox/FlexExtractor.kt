package com.styleconverter.test.style.layout.flexbox

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement

/**
 * Extracts flex configuration from IR properties.
 */
object FlexExtractor {

    /**
     * Extract flex container configuration from a list of IR properties.
     *
     * @param properties List of property type/data pairs
     * @return FlexContainerConfig with extracted values
     */
    fun extractContainerConfig(properties: List<Pair<String, JsonElement?>>): FlexContainerConfig {
        var config = FlexContainerConfig()

        // Container properties this extractor cares about. Anything else is
        // skipped without ever touching `data` — the previous code called
        // `extractKeyword(data)` on EVERY property, which threw on any IR
        // shape that wasn't a JsonPrimitive (e.g. ImageOrientation's
        // {type:"angle",value:{deg:180},flip:true}). The throw bubbled up
        // through StyleApplier's catch block and silently dropped width /
        // height / bg, collapsing the whole component box.
        val owned = setOf(
            "Display", "FlexDirection", "FlexWrap",
            "JustifyContent", "AlignItems", "AlignContent",
        )
        properties.forEach { (type, data) ->
            if (type !in owned) return@forEach
            val keyword = ValueExtractors.extractKeyword(data)?.lowercase()
            config = when (type) {
                "Display" -> config.copy(display = parseDisplayType(keyword))
                "FlexDirection" -> config.copy(direction = parseFlexDirection(keyword))
                "FlexWrap" -> config.copy(wrap = parseFlexWrap(keyword))
                "JustifyContent" -> config.copy(justifyContent = parseJustifyContent(keyword))
                "AlignItems" -> config.copy(alignItems = parseAlignItems(keyword))
                "AlignContent" -> config.copy(alignContent = parseAlignContent(keyword))
                else -> config
            }
        }

        return config
    }

    /**
     * Extract flex item configuration from a list of IR properties.
     *
     * @param properties List of property type/data pairs
     * @return FlexItemConfig with extracted values
     */
    fun extractItemConfig(properties: List<Pair<String, JsonElement?>>): FlexItemConfig {
        var config = FlexItemConfig()

        properties.forEach { (type, data) ->
            config = when (type) {
                "FlexGrow" -> config.copy(flexGrow = ValueExtractors.extractFloat(data) ?: 0f)
                "FlexShrink" -> config.copy(flexShrink = ValueExtractors.extractFloat(data) ?: 1f)
                "FlexBasis" -> config.copy(flexBasis = parseFlexBasis(data))
                "AlignSelf" -> config.copy(alignSelf = parseAlignSelf(ValueExtractors.extractKeyword(data)))
                "Order" -> config.copy(order = ValueExtractors.extractInt(data) ?: 0)
                else -> config
            }
        }

        return config
    }

    private fun parseDisplayType(keyword: String?): DisplayType = when (keyword) {
        "flex" -> DisplayType.FLEX
        "inline-flex" -> DisplayType.INLINE_FLEX
        "grid" -> DisplayType.GRID
        "inline-grid" -> DisplayType.INLINE_GRID
        "inline" -> DisplayType.INLINE
        "none" -> DisplayType.NONE
        "contents" -> DisplayType.CONTENTS
        else -> DisplayType.BLOCK
    }

    private fun parseFlexDirection(keyword: String?): FlexDirection = when (keyword) {
        "row-reverse" -> FlexDirection.ROW_REVERSE
        "column" -> FlexDirection.COLUMN
        "column-reverse" -> FlexDirection.COLUMN_REVERSE
        else -> FlexDirection.ROW
    }

    private fun parseFlexWrap(keyword: String?): FlexWrap = when (keyword) {
        "wrap" -> FlexWrap.WRAP
        "wrap-reverse" -> FlexWrap.WRAP_REVERSE
        else -> FlexWrap.NO_WRAP
    }

    private fun parseJustifyContent(keyword: String?): JustifyContent = when (keyword) {
        "flex-end", "end" -> JustifyContent.FLEX_END
        "center" -> JustifyContent.CENTER
        "space-between" -> JustifyContent.SPACE_BETWEEN
        "space-around" -> JustifyContent.SPACE_AROUND
        "space-evenly" -> JustifyContent.SPACE_EVENLY
        else -> JustifyContent.FLEX_START
    }

    private fun parseAlignItems(keyword: String?): AlignItems = when (keyword) {
        "flex-end", "end" -> AlignItems.FLEX_END
        "center" -> AlignItems.CENTER
        "baseline" -> AlignItems.BASELINE
        "stretch" -> AlignItems.STRETCH
        else -> AlignItems.FLEX_START
    }

    private fun parseAlignContent(keyword: String?): AlignContent = when (keyword) {
        "flex-end", "end" -> AlignContent.FLEX_END
        "center" -> AlignContent.CENTER
        "space-between" -> AlignContent.SPACE_BETWEEN
        "space-around" -> AlignContent.SPACE_AROUND
        else -> AlignContent.STRETCH
    }

    private fun parseAlignSelf(keyword: String?): AlignSelf = when (keyword?.lowercase()) {
        "flex-start", "start" -> AlignSelf.FLEX_START
        "flex-end", "end" -> AlignSelf.FLEX_END
        "center" -> AlignSelf.CENTER
        "baseline" -> AlignSelf.BASELINE
        "stretch" -> AlignSelf.STRETCH
        // CSS Anchor Positioning Level 1 §6: `anchor-center` collapses to
        // `center` whenever no default anchor is in scope. The Compose flex
        // runtime has no anchor-positioning model, so this fold is always
        // correct here (mirrors the iOS + Web Appliers).
        "anchor-center" -> AlignSelf.CENTER
        else -> AlignSelf.AUTO
    }

    private fun parseFlexBasis(data: JsonElement?): FlexBasis {
        if (data == null) return FlexBasis.Auto

        val keyword = ValueExtractors.extractKeyword(data)?.lowercase()
        if (keyword == "auto") return FlexBasis.Auto
        if (keyword == "content") return FlexBasis.Content

        val lop = ValueExtractors.extractLengthOrPercentage(data)
        return when (lop) {
            is ValueExtractors.LengthOrPercentage.Length -> FlexBasis.Length(lop.dp.value)
            is ValueExtractors.LengthOrPercentage.Percentage -> FlexBasis.Percentage(lop.fraction)
            else -> FlexBasis.Auto
        }
    }
}
