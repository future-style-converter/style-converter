package com.styleconverter.runtime.borders.outline

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.PropertyRegistry
// RC-B1: mode-split default ink (WPT capture → spec black, dark stage →
// #eee) for the outline-color currentColor bottom-out below.
import com.styleconverter.runtime.core.renderer.defaultTextInk
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement

/**
 * Extracts outline configuration from IR property JSON data.
 *
 * Handles the following IR property types:
 * - `OutlineWidth` - CSS `outline-width` property
 * - `OutlineStyle` - CSS `outline-style` property
 * - `OutlineColor` - CSS `outline-color` property
 * - `OutlineOffset` - CSS `outline-offset` property
 *
 * ## IR Format Examples
 * ```json
 * { "type": "OutlineWidth", "data": { "px": 2.0 } }
 * { "type": "OutlineStyle", "data": "solid" }
 * { "type": "OutlineColor", "data": { "srgb": { "r": 0, "g": 0, "b": 1 } } }
 * { "type": "OutlineOffset", "data": { "px": 4.0 } }
 * ```
 */
object OutlineExtractor {

    init {
        // CSS outline is distinct from border: drawn outside the box and
        // doesn't affect layout. Registered so the legacy dispatch skips
        // these — the OutlineApplier is the only code that should paint them.
        PropertyRegistry.migrated(
            "OutlineWidth", "OutlineStyle", "OutlineColor", "OutlineOffset",
            owner = "borders/outline"
        )
    }

    /**
     * Extract outline configuration from a list of property type/data pairs.
     *
     * @param properties List of pairs where first is the property type
     *                   and second is the JSON data for that property.
     * @return OutlineConfig with extracted outline properties.
     */
    fun extractOutlineConfig(
        properties: List<Pair<String, JsonElement?>>,
        // RC-B1 (TITAN WPT lane) — same threading as
        // BorderSideExtractor.extractBorderConfig: default false keeps every
        // legacy call site (and the dark-stage corpus) byte-identical; the
        // paint path passes the live flag via BordersFacade.
        wptCaptureMode: Boolean = false,
    ): OutlineConfig {
        // css-ui-4 §3.4: the INITIAL outline-color is `currentColor` — the
        // element's used `color`. The old Color.Black default painted a
        // solid-black ring on every fixture that omitted outline-color,
        // while the web reference (whose harness body sets `color: #eee`)
        // painted light-gray 3D patterns — PW_Borders_Sizing_04 (groove, no
        // color) sat at A-w 0.657 with all five outline-style variants
        // wrong. Resolution order: explicit OutlineColor → the element's
        // own `Color` property (inheritance already folded in by
        // ComponentRenderer.mergeInherited) → the MODE-SPLIT default ink
        // (defaultTextInk, RC-B1): dark stage keeps the harness #EEEEEE
        // (apps/web-harness/index.html `body { color: #eee }` — the 327
        // baselines pin it); WPT capture bottoms out at spec BLACK, the UA
        // `color: CanvasText` a real WPT page inherits (corpus-v4.1 ink
        // sub-boundary, mirrored by iOS OutlineApplier.fallbackInk).
        val currentColor = properties.firstOrNull { it.first == "Color" }
            ?.second?.let { ValueExtractors.extractColor(it) }
            ?: defaultTextInk(wptCaptureMode, Color(0xFFEEEEEE))
        var config = OutlineConfig(color = currentColor)

        for ((type, data) in properties) {
            config = when (type) {
                "OutlineWidth" -> config.copy(width = extractWidth(data))
                "OutlineStyle" -> config.copy(style = extractStyle(data))
                // Unparseable outline-color (e.g. the {original:"currentColor"}
                // dynamic carrier) falls back to currentColor, not black.
                "OutlineColor" -> config.copy(color = ValueExtractors.extractColor(data) ?: currentColor)
                "OutlineOffset" -> config.copy(offset = ValueExtractors.extractDp(data) ?: 0.dp)
                else -> config
            }
        }

        return config
    }

    /**
     * Extract outline width from JSON.
     * Supports both pixel values and keywords (thin, medium, thick — the
     * typed {"type":"keyword","value":"THICK"} envelope included, which
     * `outline-width: thick` emits; see ValueExtractors.extractBorderWidth).
     * Unparseable data falls back to `medium` (3px), the css-ui-4 §3.2
     * initial value — never 0, which would silently kill the whole outline.
     */
    private fun extractWidth(json: JsonElement?): Dp {
        return ValueExtractors.extractBorderWidth(json) ?: 3.dp
    }

    /**
     * Extract outline style from JSON.
     * Converts CSS style keywords to OutlineStyle enum.
     */
    private fun extractStyle(json: JsonElement?): OutlineStyle {
        val styleKeyword = ValueExtractors.extractKeyword(json)?.uppercase() ?: return OutlineStyle.NONE
        return try {
            OutlineStyle.valueOf(styleKeyword)
        } catch (e: IllegalArgumentException) {
            OutlineStyle.NONE
        }
    }

    /**
     * Check if a property type is an outline-related property.
     *
     * @param type The property type string.
     * @return True if this is an outline property.
     */
    fun isOutlineProperty(type: String): Boolean {
        return type in setOf("OutlineWidth", "OutlineStyle", "OutlineColor", "OutlineOffset")
    }
}
