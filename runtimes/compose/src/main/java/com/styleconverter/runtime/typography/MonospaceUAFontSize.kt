package com.styleconverter.runtime.typography

// The IR arrives as raw JSON (core/ir/IRModels.kt keeps every property `data`
// as a JsonElement), so the family-list read below is a JSON shape read — it
// deliberately reuses CssFontFamilyResolver.families so this gate and the
// family the TextStyle actually installs can never disagree about the list.
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonElement

/**
 * The UA **fixed-default font size** quirk — wave 36, lane M8.
 *
 * ## The defect this closes (measured, not theoretical)
 * `css/css-overflow/line-clamp/block-ellipsis-001` declares only
 * `line-clamp: 2; width: 63.1ch; font-family: monospace; border: 1px solid`.
 * On the browser reference the box lays out 46 characters per line and the
 * two-line border box measures 35px tall; on both natives it laid out 37
 * characters per line and measured 42px tall — a pure metric divergence with
 * the CORRECT clamp, the CORRECT ellipsis and the CORRECT box model.
 *
 * Two independent measurements over the wave-35 depth-48 captures pin the
 * cause to ONE number:
 *  * advance — ref 358px / 46 chars = 7.78px per glyph; a 0.6em monospace
 *    advance puts the ref font size at 12.97px. The natives measured
 *    9.6px per glyph = 0.6 × 16px.
 *  * line pitch — ref (50 − 16 − 2) / 2 = 16.25px per line, which is exactly
 *    `REF_LINE_HEIGHT` 1.25 × 13px (capture-browser-ref.mjs pins that ratio).
 *    The natives measured (57 − 16 − 2) / 2 = 19.5 ≈ 1.25 × 16px.
 *
 * ## The rule
 * Every engine keeps TWO UA default font sizes: `defaultFontSize` (16px) and
 * `defaultFixedFontSize` (13px in Chromium, and the value the frozen refs
 * were captured with). CSS Fonts 4 §3.5 resolves the `font-size` keyword
 * `medium` — the INITIAL value, i.e. what an element with no `font-size`
 * declaration computes to — against whichever of the two matches the used
 * generic family. Blink implements this in `FontBuilder::UpdateComputedSize`
 * gated on `FontDescription::GenericFamily() == kMonospaceFamily`, and that
 * generic is taken from the **first** entry of the declared family list only.
 *
 * So: no `font-size` declaration + first declared family is the `monospace`
 * (or `ui-monospace`) generic ⇒ the element's font size is [FIXED_DEFAULT_SP],
 * not 16. Web needs no twin — it emits `font-family: monospace` verbatim and
 * the browser applies its own quirk, which is precisely why the web column
 * already passes every cell this gate is aimed at.
 *
 * ## Why the FIRST entry, and only the generic
 * `font-family: "Courier New", Courier, monospace` (css-text hyphens) names a
 * concrete face first, so Blink's generic stays `kStandardFamily` and the size
 * stays 16 — even though the list ends in the monospace generic and even
 * though the face this device resolves may well be monospaced. Keying on
 * "the list mentions monospace" instead of "the first entry IS the monospace
 * generic" would move `hyphens-auto-control`, an Android cell that PASSES at
 * 16px today. The narrow rule is also the CSS-correct one.
 *
 * ## Known limit, stated rather than hidden
 * A child that re-declares `font-family: sans-serif` under a monospace parent
 * inherits the parent's COMPUTED 13px in a browser (the keyword is not
 * re-resolved on the child), while this gate hands it 16. No corpus cell
 * exercises that shape; closing it needs the inherited-size channel to carry
 * "this size came from a keyword", which is a wire change, not a runtime one.
 *
 * Byte-parallel twin: SwiftUI
 * `StyleEngine/typography/MonospaceUAFontSize.swift`.
 */
object MonospaceUAFontSize {

    /** The IR type names this gate reads (no magic literals at call sites). */
    const val FAMILY_PROPERTY_TYPE: String = "FontFamily"
    const val SIZE_PROPERTY_TYPE: String = "FontSize"

    /**
     * The UA `defaultFixedFontSize`, in the runtime's px==sp==dp space.
     *
     * 13 is Chromium's shipped default (and Safari's); it is the number the
     * frozen `tools/wpt/refs/<sha>/…` captures were rasterised with, so it is
     * the only value that can make a native capture land on the reference.
     */
    const val FIXED_DEFAULT_SP: Float = 13f

    /**
     * True iff [familyData] declares the `monospace` / `ui-monospace` generic
     * as its FIRST family — the exact condition Blink keys the quirk on.
     *
     * Reuses [CssFontFamilyResolver.families] so a wire shape that resolver
     * understands (bare array, `{ "families": [...] }`, legacy single string)
     * is understood here too, and a shape it rejects is rejected here as well.
     */
    fun appliesToFamily(familyData: JsonElement?): Boolean {
        // No family payload at all ⇒ no declaration ⇒ the standard 16px
        // default; never guess a generic the wire did not name.
        val names = CssFontFamilyResolver.families(familyData) ?: return false
        // Quoting is stripped by the converter, but hand-authored IR and the
        // conformance goldens may still carry it — normalise exactly the way
        // resolveEntry does so the two agree character-for-character.
        val first = names.firstOrNull()?.trim()?.trim('"', '\'')?.lowercase() ?: return false
        return first == "monospace" || first == "ui-monospace"
    }

    /**
     * The element's font size in sp when the quirk fires, else null (meaning
     * "leave the caller's own default alone").
     *
     * Cascade shape mirrors the extractors that fold the same list into a
     * TextStyle: the LAST `FontFamily` declaration wins, and the presence of
     * ANY `FontSize` declaration disarms the gate (a declared size is never
     * the `medium` keyword this quirk resolves).
     */
    fun resolveSpFromPairs(properties: List<Pair<String, JsonElement?>>): Float? {
        // A declared font-size — of any flavour, including one that fails to
        // parse — means the author supplied a specified value, so the keyword
        // resolution this gate models never runs. Bailing loudly-early keeps
        // the gate off every element that states its own size.
        if (properties.any { it.first == SIZE_PROPERTY_TYPE }) return null
        val family = properties.lastOrNull { it.first == FAMILY_PROPERTY_TYPE }?.second
        return if (appliesToFamily(family)) FIXED_DEFAULT_SP else null
    }

    /**
     * [resolveSpFromPairs] over the renderer's `IRProperty` list — same
     * contract. Two names rather than an overload pair because JVM erasure
     * collapses `List<Pair<…>>` and `List<IRProperty>` to the same signature.
     */
    fun resolveSp(properties: List<IRProperty>): Float? =
        resolveSpFromPairs(properties.map { it.type to it.data })
}
