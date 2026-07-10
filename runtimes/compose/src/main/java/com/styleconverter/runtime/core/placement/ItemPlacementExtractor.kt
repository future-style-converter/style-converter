package com.styleconverter.runtime.core.placement

// ItemPlacementExtractor — THE single owner of ITEM-scoped property
// parsing on Android (design §1.3: "Extractors for ITEM-scoped
// properties … populate a per-component ItemPlacement config").
//
// Every value mapping below is byte-for-byte the logic that previously
// lived inline in ComponentRenderer (extractAlignSelf / extractJustifySelf
// / extractOrder / extractFlexGrow / extractFlexShrink / flexBasisPx) and
// GridRenderer (extractPlacementSpec) — moved here so each ITEM property
// has exactly ONE parsing site. The old entry points remain as thin
// delegates (the campaign pinning tests exercise them), so this
// relocation cannot shift a pixel: same inputs, same outputs, one owner.

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object ItemPlacementExtractor {

    /**
     * Build the full placement union for one component's IR properties.
     * Absent properties resolve to CSS initial values (design §2.2:
     * "CSS-initial defaults for absent fields").
     */
    fun extract(properties: List<IRProperty>): ItemPlacement = ItemPlacement(
        grid = GridClaims(
            colStart = gridLine(properties, "GridColumnStart"),
            colEnd = gridLine(properties, "GridColumnEnd"),
            rowStart = gridLine(properties, "GridRowStart"),
            rowEnd = gridLine(properties, "GridRowEnd"),
            // Wave 5: the previously-dropped span / named-line wire flavors
            // (css-grid-1 §8.3). Each wire is one of number|span|name, so at
            // most one of the three fields per line is non-null.
            colStartSpan = gridSpan(properties, "GridColumnStart"),
            colEndSpan = gridSpan(properties, "GridColumnEnd"),
            rowStartSpan = gridSpan(properties, "GridRowStart"),
            rowEndSpan = gridSpan(properties, "GridRowEnd"),
            colStartName = gridName(properties, "GridColumnStart"),
            colEndName = gridName(properties, "GridColumnEnd"),
            rowStartName = gridName(properties, "GridRowStart"),
            rowEndName = gridName(properties, "GridRowEnd")
        ),
        flex = FlexClaims(
            grow = flexGrow(properties),
            shrink = flexShrink(properties),
            basisPx = flexBasisPx(properties)
        ),
        alignSelf = alignSelf(properties),
        justifySelf = justifySelf(properties),
        order = order(properties),
        zIndex = zIndex(properties)
    )

    /**
     * align-self keyword → renderer enum. css-align-3 §6.4 value space;
     * `anchor-center` folds to CENTER unconditionally (CSS Anchor
     * Positioning §6: no default anchor in scope in this runtime) —
     * the mapping the wave-4 ledger pinned into the LIVE path.
     */
    fun alignSelf(properties: List<IRProperty>): ComponentRenderer.AlignSelf {
        properties.forEach { prop ->
            if (prop.type == "AlignSelf") {
                val keyword = ValueExtractors.extractKeyword(prop.data)?.uppercase()
                return when (keyword) {
                    "FLEX_START", "FLEX-START", "START" -> ComponentRenderer.AlignSelf.FLEX_START
                    "FLEX_END", "FLEX-END", "END" -> ComponentRenderer.AlignSelf.FLEX_END
                    "CENTER" -> ComponentRenderer.AlignSelf.CENTER
                    "ANCHOR_CENTER", "ANCHOR-CENTER" -> ComponentRenderer.AlignSelf.CENTER
                    "STRETCH" -> ComponentRenderer.AlignSelf.STRETCH
                    "BASELINE" -> ComponentRenderer.AlignSelf.BASELINE
                    else -> ComponentRenderer.AlignSelf.AUTO
                }
            }
        }
        return ComponentRenderer.AlignSelf.AUTO
    }

    /**
     * justify-self keyword → renderer enum (css-align-3 §6.2 value space,
     * incl. the physical left/right keywords and self-start/self-end).
     */
    fun justifySelf(properties: List<IRProperty>): ComponentRenderer.JustifySelf {
        properties.forEach { prop ->
            if (prop.type == "JustifySelf") {
                // Dual carrier shape: bare keyword primitive OR {value: kw}
                // object, depending on the longhand parser flavor.
                val keyword = ValueExtractors.extractKeyword(prop.data)?.uppercase()
                    ?: ValueExtractors.extractKeywordFromObject(prop.data)?.uppercase()
                return when (keyword) {
                    "AUTO" -> ComponentRenderer.JustifySelf.AUTO
                    "NORMAL" -> ComponentRenderer.JustifySelf.NORMAL
                    "START", "SELF_START", "SELF-START" -> ComponentRenderer.JustifySelf.START
                    "END", "SELF_END", "SELF-END" -> ComponentRenderer.JustifySelf.END
                    "CENTER" -> ComponentRenderer.JustifySelf.CENTER
                    "STRETCH" -> ComponentRenderer.JustifySelf.STRETCH
                    "FLEX_START", "FLEX-START" -> ComponentRenderer.JustifySelf.FLEX_START
                    "FLEX_END", "FLEX-END" -> ComponentRenderer.JustifySelf.FLEX_END
                    "LEFT" -> ComponentRenderer.JustifySelf.LEFT
                    "RIGHT" -> ComponentRenderer.JustifySelf.RIGHT
                    "BASELINE" -> ComponentRenderer.JustifySelf.BASELINE
                    else -> ComponentRenderer.JustifySelf.AUTO
                }
            }
        }
        return ComponentRenderer.JustifySelf.AUTO
    }

    /** order: integer, CSS initial 0, lower values lay out first. */
    fun order(properties: List<IRProperty>): Int {
        properties.forEach { prop ->
            if (prop.type == "Order") {
                return ValueExtractors.extractInt(prop.data) ?: 0
            }
        }
        return 0
    }

    /** flex-grow: number, CSS initial 0 (css-flexbox-1 §7.2). */
    fun flexGrow(properties: List<IRProperty>): Float {
        properties.forEach { prop ->
            if (prop.type == "FlexGrow") {
                return ValueExtractors.extractFloat(prop.data) ?: 0f
            }
        }
        return 0f
    }

    /** flex-shrink: number, CSS initial 1 (css-flexbox-1 §7.2). */
    fun flexShrink(properties: List<IRProperty>): Float {
        properties.forEach { prop ->
            if (prop.type == "FlexShrink") {
                return ValueExtractors.extractFloat(prop.data) ?: 1f
            }
        }
        return 1f
    }

    /**
     * flex-basis in px, or null when auto / percentage / content — the
     * IR shape is `{"value":{"px":N},"normalizedPixels":N}`; percentage
     * and keyword bases carry no px and stay unresolved (the §9.7 static
     * resolver then declares the line unresolvable).
     */
    fun flexBasisPx(properties: List<IRProperty>): Double? {
        val data = properties.firstOrNull { it.type == "FlexBasis" }?.data ?: return null
        val obj = data as? JsonObject ?: return null
        obj["normalizedPixels"]?.jsonPrimitive?.doubleOrNull?.let { return it }
        return (obj["value"] as? JsonObject)?.get("px")?.jsonPrimitive?.doubleOrNull
    }

    /**
     * One grid line longhand → 1-based line number, or null for auto.
     * Wire shapes (GridColumnStartPropertyParser et al.): line numbers as
     * `{"type":"number","number":N}` or a bare integer primitive; `span N`
     * / named lines serialize differently → auto (documented fallback).
     */
    private fun gridLine(properties: List<IRProperty>, type: String): Int? =
        properties.firstOrNull { it.type == type }?.data?.let { d ->
            ((d as? JsonObject)?.get("number") as? JsonPrimitive)?.intOrNull
                ?: (d as? JsonPrimitive)?.intOrNull
        }

    /**
     * `span N` flavor of a grid line longhand → N, or null. Wire shape
     * (GridLine.Span in the converter): `{"type":"span","count":N}`.
     * Also accepts the bare-string "span N" primitive for legacy fixtures.
     */
    private fun gridSpan(properties: List<IRProperty>, type: String): Int? =
        properties.firstOrNull { it.type == type }?.data?.let { d ->
            when (d) {
                // Typed wire: the "span" discriminator guards the count so a
                // future {type:"...", count:N} shape can't be misread.
                is JsonObject ->
                    if ((d["type"] as? JsonPrimitive)?.contentOrNull == "span")
                        (d["count"] as? JsonPrimitive)?.intOrNull
                    else null
                // Legacy bare-string carrier: "span 2".
                is JsonPrimitive -> d.contentOrNull?.trim()?.lowercase()
                    ?.takeIf { it.startsWith("span ") }
                    ?.removePrefix("span ")?.trim()?.toIntOrNull()
                else -> null
            }
        }

    /**
     * Named-line flavor of a grid line longhand → the ident, or null. Wire
     * shape (GridLine.LineName): `{"type":"name","name":"media"}`. This is
     * how `grid-area: media` reaches the runtime — the shorthand expander
     * emits ONLY `grid-row-start: media` (see converter GridAreaExpander),
     * so the row-start name doubles as the area-name claim.
     */
    private fun gridName(properties: List<IRProperty>, type: String): String? =
        properties.firstOrNull { it.type == type }?.data?.let { d ->
            val obj = d as? JsonObject ?: return@let null
            if ((obj["type"] as? JsonPrimitive)?.contentOrNull != "name") return@let null
            (obj["name"] as? JsonPrimitive)?.contentOrNull
        }

    /**
     * z-index: integer or null for auto (CSS 2.1 §9.9.1). Carried for
     * measure-time consumers; the SELF-path applier chain keeps owning
     * the actual Modifier.zIndex application today.
     */
    fun zIndex(properties: List<IRProperty>): Int? {
        val data = properties.firstOrNull { it.type == "ZIndex" }?.data ?: return null
        return ValueExtractors.extractInt(data)
    }
}
