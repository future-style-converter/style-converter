package com.styleconverter.runtime.spacing

// UaBlockMarginFontBasis — wave 46, lane Y8: the FONT BASIS of the UA
// default block margins.
//
// ## The measured defect
// CSS2/cascade/inherit-computed-001 is a single composed ROOT `<p>` that
// declares `font-size: larger` (wire: {"original":{"type":"relative",
// "keyword":"larger"}}, no px). The Chromium UA sheet gives a `<p>`
// `margin-block: 1em` (HTML §15.3.3 "Flow content"), and css-values-4
// §6.1.1 resolves that em against the element's OWN computed font-size —
// `larger` of the inherited 16px = 19.2px — so the browser-ref's border
// box starts at y = 16 (image pad) + 19.2 = 35.2 → row 35. Both natives
// started it at row 32: 16 + 16, the table's fixed "16px at a 16px root"
// value. Row-by-row the two boxes are otherwise IDENTICAL (54 rows tall,
// same glyph rows, the em's 6×27 atom bar at the same x) — translating the
// wave45-final Android capture down 3px re-scores it from 0.8959 to 0.9984
// through the scorer's own diffWebVsRef. The whole residual is this basis.
//
// ## What this module is
// Two PURE tables the UA margin fold consults, kept out of
// UaBlockChildMargins.kt so that file stays under its 200-line target:
//  1. [uaBlockMarginEm] — the UA sheet's block-margin em FACTOR per tag
//     (1em for p/ul/ol/blockquote/pre/figure/h3, the .67/.83/1.33/1.67/
//     2.33em ladder for the other headings; Chromium html.css).
//  2. [ownFontSizePxFromPairs] — the element's own computed font-size in px
//     from its property list, against a caller-supplied inherited base
//     (css-values-4 §6.1.1 em rule for font-size itself + CSS 2.1 §15.7 for
//     the relative keywords). It returns NULL when the element carries NO
//     own font signal (no FontSize, no monospace-quirk family — and, for an
//     em-sized UA heading, not even that: see [uaFontSizeIsKeywordSized]),
//     which is the contract that keeps every caller byte-identical: a null basis
//     makes uaVerticalBlockMargins fall back to its Round-4 ref-calibrated
//     16px-root table, so the hundreds of corpus `<p>`/`<ul>`/`<hN>` roots
//     and children without a font-size declaration cannot move by a pixel.
//
// ## Why the keyword ladder is duplicated rather than shared
// TextStyleApplier.extractFontSize owns the same ladder for the painted
// TextStyle, but it is private, returns a TextUnit, and lives in the
// typography tree. UaBlockMarginFontBasisTest's F-pins assert the two agree,
// so a drift between "the size the glyphs paint at" and "the size the UA
// margin resolves against" fails a unit test instead of a capture.
//
// ## Known limits (stated, not hidden)
//  - A DECLARED but unresolvable size (var()/calc()/vw/ex/ch) yields null
//    → the table's 16px-root value, exactly the pre-wave-46 render.
//  - The CHILD fold (ComponentRenderer.blockCollapsePlanFor) has no
//    inheritance channel into this module yet and keeps the table. Corpus
//    simulation (wave45-final per-test-ir): only inherit-computed-002 and
//    counter-style-at-rule/disclosure-styles carry a block child whose basis
//    differs from 16px, both ~0.60 for unrelated reasons — a renderer seam.

import com.styleconverter.runtime.core.ir.IRProperty
// The single owner of the UA fixed-default (monospace-13) quirk: consulted
// on the no-declaration rung, never re-derived (same ladder as the harness
// StaticEmMargin.ownFontSizePx F3 and the Swift UAElementFontRule.emBasePx).
import com.styleconverter.runtime.typography.MonospaceUAFontSize
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

object UaBlockMarginFontBasis {

    /** The UA `medium` default — 16px: the inherited size of every composed
     *  WPT root (body-level children of a canvas that never re-declares a
     *  body font-size) and the root the UA margin table is calibrated to. */
    const val UA_DEFAULT_FONT_SIZE_PX: Float = 16f

    /**
     * CSS 2.1 §15.7's recommended ratio between adjacent absolute-size
     * ladder entries — the ±1-step multiplier for `larger` / `smaller`.
     * Blink's FontSizeFunctions uses exactly ×1.2 / ÷1.2 for a non-keyword
     * inherited size, which is what the frozen refs rasterised (19.2px on
     * inherit-computed-001). Same constant TextStyleApplier paints with.
     */
    const val RELATIVE_SIZE_STEP: Float = 1.2f

    /**
     * The UA stylesheet's block-margin em factor for [sourceTag] — the
     * `margin-block: <n>em` of Chromium's html.css (HTML §15.3.3 sectioning
     * headings, §15.3.8 flow content: p / ul / ol / pre / blockquote /
     * figure). Null for every tag the UA sheet gives no block margin
     * (div, section, li, span, unknown, absent).
     *
     * NOTE the factor is over the element's COMPUTED size: for the headings
     * that size is itself a UA em (h1 2em, h2 1.5em, h3 1.17em, h4 1em, h5
     * .83em, h6 .67em of the inherited size) unless the author overrides
     * it, which is exactly the case where a caller has an own basis.
     */
    fun uaBlockMarginEm(sourceTag: String?): Float? = when (sourceTag?.lowercase()) {
        // `margin-block: 1em` tags — their UA font-size is the inherited one.
        "p", "ul", "ol", "blockquote", "pre", "figure" -> 1f
        // Headings: html.css `h1 { margin-block: 0.67em }` … `h6 { 2.33em }`.
        "h1" -> 0.67f
        "h2" -> 0.83f
        "h3" -> 1f
        "h4" -> 1.33f
        "h5" -> 1.67f
        "h6" -> 2.33f
        // No UA block margin for flow containers / unknown tags.
        else -> null
    }

    /**
     * The tags whose UA font-size is itself an EM of the inherited size —
     * Chromium html.css: `h1 { font-size: 2em }`, h2 1.5em, h3 1.17em,
     * h5 .83em, h6 .67em. (`h4` declares NO font-size there, so it stays
     * keyword-sized exactly like the flow tags and is deliberately absent.)
     *
     * Why this set exists at all: it is the B1 gate. Blink applies the
     * `defaultFixedFontSize` (13px) only where the computed size came from
     * a KEYWORD — `FontBuilder::UpdateComputedSize` re-resolves the keyword
     * against the fixed table. For an element whose specified size is an em
     * (KeywordSize == 0) `FontBuilder::CheckForGenericFamilyChange` instead
     * SCALES the specified size by fixed/default = 13/16, so a monospace
     * `<h1>` computes 2em × 16 × 13/16 = 26px and its UA margin is
     * .67 × 26 ≈ 17.4px — nowhere near the .67 × 13 = 8.71px an unguarded
     * B1 would hand back. Rather than model that scaling (it needs the UA
     * font-size ladder, an inheritance channel this module does not have,
     * and a Blink rule this lane cannot verify against a capture), B1
     * returns null for these tags: the caller keeps the Round-4
     * ref-calibrated table (h1 → 21px), which is the pre-wave-46 render and
     * the CORRECT value for the overwhelmingly common non-monospace heading.
     * Latent either way — the frozen corpus has zero B1 roots today.
     */
    private val EM_SIZED_UA_FONT_TAGS: Set<String> = setOf("h1", "h2", "h3", "h5", "h6")

    /**
     * Is [sourceTag]'s UA font-size the inherited KEYWORD size — i.e. may
     * the monospace fixed-default quirk (B1) resolve it?
     *
     * True for the 1em keyword-sized block tags (p / ul / ol / blockquote /
     * figure / pre / h4) and, deliberately, for every tag OUTSIDE the UA
     * block-margin table (div, li, span, unknown, absent): those get no UA
     * block margin at all ([uaBlockMarginEm] is null → 0px), so the basis
     * they resolve cannot move a pixel, and keeping the quirk there leaves
     * every pre-wave-46 caller that passes no tag byte-identical.
     */
    internal fun uaFontSizeIsKeywordSized(sourceTag: String?): Boolean =
        // An absent tag normalises to "" — not in the set, so it keeps the
        // quirk (the pre-wave-46 identity for every caller with no tag).
        (sourceTag ?: "").lowercase() !in EM_SIZED_UA_FONT_TAGS

    /**
     * The absolute-size keyword ladder (css-fonts-4 §2.5.1 table, Chromium's
     * 16px-medium row) — the same px TextStyleApplier paints the keyword
     * at, pinned against it by the F-pins.
     */
    private fun absoluteKeywordPx(keyword: String?): Float? = when (keyword?.lowercase()) {
        "xx-small" -> 9f
        "x-small" -> 10f
        "small" -> 13f
        "medium" -> 16f
        "large" -> 18f
        "x-large" -> 24f
        "xx-large" -> 32f
        "xxx-large" -> 48f
        // Unknown keyword: not resolvable, never guessed.
        else -> null
    }

    /**
     * The element's OWN computed font-size in px — the em base of the UA
     * block margin — or NULL when the element carries no own font signal or
     * its declared size is not statically resolvable.
     *
     * Resolution ladder (pairs of IR `type` → `data`, the renderer's list
     * shape; `lastOrNull` mirrors the extractors' last-declaration-wins
     * fold so a duplicated FontSize resolves to the painted value):
     *  B1 no FontSize, first declared family is the monospace generic, and
     *     the tag's UA font-size is keyword-derived
     *     ([uaFontSizeIsKeywordSized]) → 13px via [MonospaceUAFontSize]
     *     (Chromium's defaultFixedFontSize). An em-sized heading
     *     (h1/h2/h3/h5/h6) takes Blink's 13/16 SCALING instead, which this
     *     module does not model, so it returns null and keeps the table.
     *  B2 no FontSize otherwise → null: NO own signal, the caller keeps the
     *     16px-root table (the identity contract in the file header).
     *  B3 a resolved top-level `px` (absolute length, or a DynamicValueResolver
     *     prebake) > 0 → that px.
     *  B4 absolute keyword (`{"type":"absolute","keyword":…}`) → the ladder.
     *  B5 relative keyword (`{"type":"relative","keyword":"larger"|"smaller"}`)
     *     → [inheritedPx] × / ÷ [RELATIVE_SIZE_STEP] (CSS 2.1 §15.7).
     *  B6 nested length (`{"type":"length","original":{v,u}}`): EM → v ×
     *     inherited (css-values-4 §6.1.1: em on font-size itself resolves
     *     against the INHERITED size), REM → v × the 16px root.
     *  B7 percentage (`{"type":"percentage","value":N}`) → N% × inherited
     *     (css-fonts-4 §2.5: same base as em).
     *  B8 anything else (var()/calc()/vw/ex/ch/malformed) → null (the
     *     documented honest fallback: the caller keeps the table).
     *
     * @param inheritedPx the parent's computed size — [UA_DEFAULT_FONT_SIZE_PX]
     *   for a composed root; the renderer's inheritance channel for a child.
     * @param sourceTag the element's `meta.sourceTag` (`_tag` on the wire),
     *   read by the B1 gate ONLY ([uaFontSizeIsKeywordSized]). Null keeps the
     *   pre-wave-46 behaviour for callers with no tag in hand; every rung
     *   B3-B8 is tag-independent, because a DECLARED size overrides the UA
     *   one and there is nothing left for the UA sheet to scale.
     */
    fun ownFontSizePxFromPairs(
        properties: List<Pair<String, JsonElement?>>,
        inheritedPx: Float = UA_DEFAULT_FONT_SIZE_PX,
        sourceTag: String? = null,
    ): Float? {
        val data = properties.lastOrNull { it.first == MonospaceUAFontSize.SIZE_PROPERTY_TYPE }?.second
            // B1 / B2 — no declaration: the quirk (only where the UA size is
            // the keyword one) or no signal at all. An em-sized heading gets
            // null rather than a wrong 13px basis — see the gate's kdoc.
            ?: return if (uaFontSizeIsKeywordSized(sourceTag))
                MonospaceUAFontSize.resolveSpFromPairs(properties) else null
        val obj = data as? JsonObject ?: return null
        // B3 — a resolved px rides at the top level on every absolute or
        // prebaked wire shape; a zero/negative size cannot scale a margin.
        obj["px"]?.jsonPrimitive?.floatOrNull?.takeIf { it > 0f }?.let { return it }
        val original = obj["original"] as? JsonObject ?: return null
        return when (original["type"]?.jsonPrimitive?.contentOrNull) {
            // B4 — the absolute keyword ladder.
            "absolute", "absoluteKeyword" ->
                absoluteKeywordPx(original["keyword"]?.jsonPrimitive?.contentOrNull)
            // B5 — one ladder step from the inherited size.
            "relative" -> when (original["keyword"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                "larger" -> inheritedPx * RELATIVE_SIZE_STEP
                "smaller" -> inheritedPx / RELATIVE_SIZE_STEP
                else -> null
            }
            // B6 — the nested {v,u} wrapper the converter emits for em/rem.
            "length" -> {
                val inner = original["original"] as? JsonObject
                val v = inner?.get("v")?.jsonPrimitive?.floatOrNull
                when (inner?.get("u")?.jsonPrimitive?.contentOrNull?.uppercase()) {
                    "EM" -> v?.times(inheritedPx)
                    "REM" -> v?.times(UA_DEFAULT_FONT_SIZE_PX)
                    // Other relative units have no static base here (B8).
                    else -> null
                }
            }
            // B7 — percentage of the inherited size.
            "percentage" -> original["value"]?.jsonPrimitive?.floatOrNull?.let { it / 100f * inheritedPx }
            // B8 — expression / unknown shapes: not statically resolvable.
            else -> null
        }?.takeIf { it > 0f }
    }

    /**
     * [ownFontSizePxFromPairs] over the renderer's `IRProperty` list — same
     * contract. A second name rather than an overload because JVM erasure
     * collapses `List<Pair<…>>` and `List<IRProperty>` to one signature.
     */
    fun ownFontSizePx(
        properties: List<IRProperty>,
        inheritedPx: Float = UA_DEFAULT_FONT_SIZE_PX,
        sourceTag: String? = null,
    ): Float? = ownFontSizePxFromPairs(properties.map { it.type to it.data }, inheritedPx, sourceTag)
}
