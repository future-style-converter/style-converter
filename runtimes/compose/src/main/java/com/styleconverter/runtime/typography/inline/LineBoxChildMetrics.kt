// typography/inline — wave 50 (lane B9): the per-CHILD wire readers the
// cross-block line-box census needs, split from LineBoxCensusRuns.kt under
// the ≤200-line file rule. Twin of iOS's wave-46
// `StyleEngine/scrolling/LineClampChildMetrics.swift`.
//
// A clamp root's children are rendered LATER, recursively, each with its
// inherited properties merged in at its own render — so at the root's
// render, where the cap must be decided, a child's USED font-size and
// line-height are not on any style yet. These readers resolve them off the
// child's RAW wire (the same `ValueExtractors.extractDp` reads the child's
// own chain will make), inheriting from the root where the child declares
// nothing: font-size, line-height and white-space all inherit (CSS 2.1
// §6.2). Anything a reader cannot resolve reads null, which the census
// turns into [LineBoxCensus.Verdict.Unprovable] and the cap resolver turns
// back into the wave-41 uniform cap. Never a guessed height.
package com.styleconverter.runtime.typography.inline

// The typed IR property bag these readers classify (pure data, no Compose).
import com.styleconverter.runtime.core.ir.IRProperty
// The composed-WPT default line-box ratio, shared with the root's own pick.
import com.styleconverter.runtime.core.renderer.composedDefaultLineHeightPx
// The shared wire-value readers — one parse per shape, never re-derived.
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull

object LineBoxChildMetrics {

    /** The lowercased keyword of the LAST declaration of [type], if any. */
    fun keyword(properties: List<IRProperty>, type: String): String? =
        ValueExtractors.extractKeyword(
            properties.lastOrNull { it.type == type }?.data
        )?.lowercase()

    /**
     * The child's used font size in px, or null when its declaration is a
     * shape this reader cannot resolve here (an unflattened `em` wire — the
     * renderer's DynamicValueResolver flattens those at the CHILD's own
     * render, which is after this decision).
     */
    fun fontSizePx(properties: List<IRProperty>, root: LineBoxRootMetrics): Float? {
        val declared = properties.lastOrNull { it.type == "FontSize" }
        if (declared != null) return ValueExtractors.extractDp(declared.data)?.value
        // No declaration: the UA fixed default for a first-family monospace
        // child (wave-36 M8, 13px), else the INHERITED size.
        return com.styleconverter.runtime.typography.MonospaceUAFontSize.resolveSp(properties)
            ?: root.fontSizePx
    }

    /**
     * The child's one line box in px, or null when it is not provable: a
     * `line-height` wire this reader does not resolve, or a null font size.
     */
    fun lineBoxPx(
        properties: List<IRProperty>,
        fontSizePx: Float?,
        root: LineBoxRootMetrics,
    ): Float? {
        val declared = properties.lastOrNull { it.type == "LineHeight" }
        if (declared != null) {
            // A resolved length (including the typed `original` wrapper the
            // live wire ships) is authoritative.
            val px = ValueExtractors.extractDp(declared.data)?.value
            if (px != null && px > 0f) return px
            // A unitless multiplier multiplies the CHILD's own size.
            val fs = fontSizePx ?: return null
            val multiplier = (declared.data as? JsonObject)
                ?.get("multiplier")?.let { (it as? JsonPrimitive)?.floatOrNull }
            return if (multiplier != null && multiplier > 0f) fs * multiplier else null
        }
        // Inherited: a root UNITLESS multiplier recomputes against the
        // child's own size (CSS 2.1 §10.8 — the computed value is the
        // number); a root px length inherits as that px; otherwise the
        // calibrated ratio × the child's size.
        val fs = fontSizePx ?: return null
        root.declaredMultiplier?.let { return fs * it }
        root.declaredLineHeightPx?.let { return it }
        return composedDefaultLineHeightPx(composedWpt = true, fontSizePx = fs)
    }

    /**
     * The child's vertical bands — padding + border width + margin, top and
     * bottom — in px, or null when any of them is declared in a shape this
     * reader does not resolve. Absent longhands are 0 (the CSS initial).
     */
    fun verticalBands(properties: List<IRProperty>): Pair<Float, Float>? {
        fun band(type: String): Float? {
            val p = properties.lastOrNull { it.type == type } ?: return 0f
            return ValueExtractors.extractDp(p.data)?.value?.coerceAtLeast(0f)
        }
        val top = listOf("PaddingTop", "BorderTopWidth", "MarginTop").map { band(it) }
        val bottom = listOf("PaddingBottom", "BorderBottomWidth", "MarginBottom").map { band(it) }
        if (top.any { it == null } || bottom.any { it == null }) return null
        return top.filterNotNull().sum() to bottom.filterNotNull().sum()
    }

    /**
     * True when the child's used overflow is not `visible` on any axis — a
     * scroll container / clip box, i.e. an independent formatting context
     * whose line boxes are not this container's (css-overflow-3 §3). Reads
     * the same five longhands the overflow extractor does.
     */
    fun isScrollContainer(properties: List<IRProperty>): Boolean {
        val axes = setOf("Overflow", "OverflowX", "OverflowY", "OverflowBlock", "OverflowInline")
        return properties.any { p ->
            p.type in axes &&
                (ValueExtractors.extractKeyword(p.data)?.lowercase() ?: "visible") != "visible"
        }
    }

    /**
     * A definite px `height` on the child (null for absent / `auto` / any
     * shape this reader does not resolve): the box is then exactly that
     * tall whatever its text does (css-sizing-3 §5.1 — the used height
     * wins), so it is a monolithic run rather than a stack of line boxes.
     */
    fun explicitHeightPx(properties: List<IRProperty>): Float? =
        ValueExtractors.extractDp(properties.lastOrNull { it.type == "Height" }?.data)?.value
}
