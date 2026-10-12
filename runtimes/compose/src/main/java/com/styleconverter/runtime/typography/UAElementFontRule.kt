// typography — wave 54 (lane L5, unit U1-android): the Compose twin of
// SwiftUI StyleEngine/typography/UAElementFontRule.swift (wave 40, T7) —
// BACKLOG 0(g), queued as "STILL DEFERRED" since the retro.
//
// ## The defect this closes (measured on wave53-final)
// Chromium's html.css supplies `h1 { font-size: 2em; font-weight: bold }`;
// Compose had NO tag-keyed font step (ComponentRenderer :1141 ran
// ListStyleUaRule only), so block-in-inline-015-print android (f 0.9489)
// paints 9–12px regular bands where the ref paints 18–25px bold ones; iOS,
// which has the rule, passes the same IR at P 0.9894.
//
// ## Cascade position (css-cascade-4 §4.3 / §6.1)
// A UA declaration is a declaration ON the element: it BEATS inheritance and
// LOSES to the author's own declaration — so the rule fires only when the
// OWN list declares nothing in the guarded family, and it SUBSTITUTES the
// inherited entry in place, leaving exactly one entry for the first-wins
// (DynamicValueResolver.fontSizePxOf) and last-wins (TextStyleApplier) reads.
// The seam runs it BEFORE DynamicValueResolver, so `em` / `ch` on the
// heading resolve against the UA face.
//
// ## Scope, stated rather than discovered
// Keyed on `_tag` (`meta.sourceTag`), which the 327-pair product corpus never
// carries, so every committed baseline is byte-identical by construction. The
// heading half stands down for a heading that hosts child boxes
// ([headingStandsDown], the iOS twin's wave-40 gate); sup/sub are never
// gated. UA heading MARGINS stay with spacing/UaBlockChildMargins.kt + the
// harness UaBlockMargins. KNOWN GAPS (as in the Swift twin): `smaller` is
// parent ÷ 1.2, where Blink uses its keyword table for a keyword-sized
// parent (13 vs 13.33); `all: initial|unset|inherit` on a heading still gets
// the UA face (0 corpus carriers).
package com.styleconverter.runtime.typography

// The decoded component whose composed children decide the heading gate.
import com.styleconverter.runtime.core.ir.IRComponent
// The typed IR property (type name + raw JSON payload) this rule rewrites.
import com.styleconverter.runtime.core.ir.IRProperty
// The resolver's own first-FontSize px read — reused for the em base so the
// UA rule and the em/ch resolution can never disagree about the parent size.
import com.styleconverter.runtime.core.variables.DynamicValueResolver
// JSON builders for the two payloads this rule emits.
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The UA sheet's ELEMENT-KEYED font declarations as a cascade step at the
 *  merge seam ([com.styleconverter.runtime.lists.ListStyleUaRule]'s site).
 *  Pure Kotlin (no Compose) so the JVM suite pins it on verbatim wire. */
object UAElementFontRule {

    /** The IR types the size and weight halves write (no magic literals). */
    private const val SIZE_TYPE = "FontSize"
    private const val WEIGHT_TYPE = "FontWeight"

    /** The UA `medium` keyword size — CSS Fonts 4 §2.5 / Chromium 16px. */
    private const val MEDIUM_PX = 16.0

    /** `smaller` — one ×1.2 ladder step down (css-fonts-4 §2.5, Blink
     *  FontSizeFunctions for a non-keyword parent; see the KNOWN GAP). */
    private const val SMALLER = 1.0 / 1.2

    /** The `bold` keyword's computed weight (css-fonts-4 §2.2), emitted as
     *  an INTEGER: ValueExtractors.extractFontWeight reads `intOrNull`, so a
     *  700.0 double would silently read as null. */
    private const val BOLD = 700

    /** Own-declared types meaning the author sized the element, so the UA
     *  size loses (css-cascade-4 §6.1); `Font` (unexpanded) carries a size. */
    val SIZE_DECLARING: Set<String> = setOf(SIZE_TYPE, "Font")

    /** The weight twin of [SIZE_DECLARING] (same shorthand argument). */
    val WEIGHT_DECLARING: Set<String> = setOf(WEIGHT_TYPE, "Font")

    /**
     * The UA `font-size` multiplier for [tag], as a factor on the element's
     * own `medium` (CSS 2.1 Appendix D / Chromium html.css: h1 2em, h2 1.5em,
     * h3 1.17em, h4 1em, h5 .83em, h6 .67em; sub/sup `smaller`). Null for
     * every other tag — the overwhelming majority, and the fast path.
     */
    fun sizeMultiplier(tag: String?): Double? = when (tag?.lowercase()) {
        // The six sectioning headings (HTML §15.3.7).
        "h1" -> 2.0
        "h2" -> 1.5
        "h3" -> 1.17
        "h4" -> 1.0
        "h5" -> 0.83
        "h6" -> 0.67
        // HTML §15.3.4 `sub, sup { font-size: smaller }`.
        "sub", "sup" -> SMALLER
        // Every tag the UA sheet gives no font-size.
        else -> null
    }

    /** Does the UA sheet declare `font-weight: bold` for [tag]? The six
     *  headings only — `b` / `strong` are left out as in the Swift twin (bare
     *  `<b>` cells pass today; widening would risk them unmeasured). */
    fun isBold(tag: String?): Boolean = when (tag?.lowercase()) {
        // The heading family; everything else carries no UA weight here.
        "h1", "h2", "h3", "h4", "h5", "h6" -> true
        else -> false
    }

    /**
     * The `em` base the UA multiplier resolves against, in px: 1. an
     * INHERITED size — the parent publishes RESOLVED px, read with the
     * resolver's own first-entry read (css-values-4 §6.1.1); 2. else the UA
     * `medium`: 16px, or Chromium's 13px fixed default for a monospace first
     * family via [MonospaceUAFontSize] (one table for the whole runtime —
     * why inset-014's monospace `<h1>` measures 2 × 13 = 26px in the ref).
     */
    fun emBasePx(merged: List<IRProperty>): Double =
        // Rung 1: an inherited resolved px size; rung 2: the medium keyword.
        DynamicValueResolver.fontSizePxOf(merged)?.toDouble()
            ?: MonospaceUAFontSize.resolveSp(merged)?.toDouble()
            ?: MEDIUM_PX

    /**
     * The seam's `standsDown` verdict: true for an element hosting composed
     * child boxes — the Swift twin's wave-40 `hasElementChildren` gate,
     * verbatim (wave 54 GO-SMALL, PLAN §2 L5). A FOLDED heading host stands
     * down too: its abspos `<h1>` gets no UA `.67em` margin on either native,
     * so the 2em face alone lands ~21px high and the replay scores
     * inset-005/-006/-014 LOWER (tools/titan/results/wave54-ua-heading-face/
     * skeptic.md repro 14); the fold-aware gate waits there, in `held/`.
     */
    fun headingStandsDown(component: IRComponent): Boolean =
        // Any composed child box ⇒ the heading half is withheld.
        !component.children.isNullOrEmpty()

    /**
     * Apply the UA element-font declarations to one element's merged list.
     *
     * @param sourceTag the element's `_tag`; null / untabled ⇒ [merged] is
     *   returned as the SAME instance (the byte-stability fast path every
     *   Compose `remember` key downstream relies on).
     * @param own the element's OWN post-reset declarations — a guarded type
     *   here means the author declared it and the UA value loses.
     * @param merged the inheritance-merged list to correct.
     * @param standsDown [headingStandsDown]'s verdict: true withholds the
     *   HEADING half (a host of child boxes); sup/sub ignore it.
     * @return [merged] itself when nothing fires, else a substituted copy.
     */
    fun apply(
        sourceTag: String?,
        own: List<IRProperty>,
        merged: List<IRProperty>,
        standsDown: Boolean = false,
    ): List<IRProperty> {
        // Heading tags are the only gated ones; sup/sub always apply.
        val heading = isBold(sourceTag)
        // The heading half applies unless [headingStandsDown] withheld it.
        val applies = !heading || !standsDown
        // The size factor (null = no UA size for this tag or gated out).
        val multiplier = if (applies) sizeMultiplier(sourceTag) else null
        // The weight half: headings only, same gate.
        val bold = heading && applies
        // Fast path: nothing to declare — hand back the very same list.
        if (multiplier == null && !bold) return merged
        // Start from the input; each half below replaces it only if it fires.
        var out = merged
        // SIZE half — the author's own size (or `font`) wins (§6.1).
        if (multiplier != null && own.none { it.type in SIZE_DECLARING }) {
            // The wire's plain px length shape, as the Swift twin emits.
            val px = multiplier * emBasePx(merged)
            // Substitute the inherited entry in place (or append).
            out = substituting(out, SIZE_TYPE, JsonObject(mapOf("px" to JsonPrimitive(px))))
        }
        // WEIGHT half — same cascade rule, same substitution.
        if (bold && own.none { it.type in WEIGHT_DECLARING }) {
            // `{"weight": 700}` — the converter's own shape for `bold`.
            out = substituting(out, WEIGHT_TYPE, JsonObject(mapOf("weight" to JsonPrimitive(BOLD))))
        }
        // Either `merged` untouched (both halves guarded) or a new list.
        return out
    }

    /** Replace the FIRST [type] entry with [data], drop later ones, append
     *  when none exists — one entry left, so first-wins and last-wins readers
     *  agree (the Swift twin's `substituting`, line for line). */
    private fun substituting(
        properties: List<IRProperty>,
        type: String,
        data: JsonObject,
    ): List<IRProperty> {
        // Whether the first matching entry has already been replaced.
        var replaced = false
        // Walk once: keep others, replace the first match, drop the rest.
        val out = properties.mapNotNull { p ->
            when {
                // Not the guarded type — kept verbatim, in place.
                p.type != type -> p
                // A later duplicate — dropped so only one entry survives.
                replaced -> null
                // The first match — the UA value takes its position.
                else -> { replaced = true; IRProperty(type, data) }
            }
        }
        // No entry to replace: the UA declaration is appended.
        return if (replaced) out else out + IRProperty(type, data)
    }
}
