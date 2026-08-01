package com.styleconverter.runtime.typography

// The IR arrives as raw JSON (core/ir/IRModels.kt keeps every property `data`
// as a JsonElement), so the discriminator test below is a JSON shape test.
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The DECLARED-`normal` line-height discriminator — wave 22, lane FONT.
 *
 * ## Why a third state exists at all
 * The WPT capture path calibrates a line box for text whose IR declares NO
 * line-height: `composedDefaultLineHeightPx` (core/renderer/WptCaptureMode.kt)
 * returns `fontSize × REF_DEFAULT_FONT_LINE_HEIGHT_RATIO` (1.25), the exact box
 * the browser-ref injection pins (capture-browser-ref.mjs REF_LINE_HEIGHT) so
 * stacked bars stop drifting against the ref. That calibration is correct
 * precisely BECAUSE the ref's own zero-specificity `:where(body)` rule is what
 * supplies the ref's line box in that case.
 *
 * `font: 92px Arial` is the case where it is WRONG. css-fonts-4 §4.3 makes the
 * `font` shorthand RESET `line-height` to `normal`, and a directly-matching
 * declaration beats an inherited one no matter how specific the source rule is
 * — so Chromium lays that div out on Arial's natural metrics (hhea asc 1854 +
 * desc 434 + gap 67 over a 2048 upem = 1.1499em ≈ 105.8px at 92px), NOT on the
 * injected 1.25 (115px). Until wave 22 the converter never emitted the reset
 * (FontExpander.kt), so the runtime could not tell "author said `normal`" from
 * "author said nothing" and applied the calibration to both.
 *
 * The converter now emits `{"multiplier":1.2,"original":"normal"}` for the
 * reset. `multiplier` 1.2 is a legacy COMPATIBILITY fallback on the wire (see
 * irmodels/properties/typography/LineHeightProperty.kt — it predates this lane
 * and is deliberately left byte-identical so no schema golden moves); it is NOT
 * the CSS-correct value, because `normal` is a font-metric lookup and no single
 * ratio can stand in for it across faces. This predicate is what lets a
 * consumer tell the keyword apart from a real authored number that happens to
 * share the same `multiplier` field.
 *
 * ## The contract the three states must satisfy
 * | IR                                | WPT capture      | elsewhere         |
 * |-----------------------------------|------------------|-------------------|
 * | LineHeight absent                 | calibration      | calibration       |
 * | LineHeight `original == "normal"` | natural metrics  | 1.2× (UNCHANGED)  |
 * | LineHeight number / % / length    | that value       | that value        |
 *
 * The middle row under WPT capture is the ONLY cell this lane moves; see
 * [lineBoxSource] for why the same cell outside WPT capture is deliberately
 * left on its historical numeric approximation. No committed fixture under
 * fixtures/properties|components uses the `font` shorthand (checked), so the
 * converter-side reset cannot reach the dark stage at all.
 *
 * Byte-parallel twin: SwiftUI
 * StyleEngine/typography/line/LineHeightNormal.swift (`LineHeightNormal.isDeclaredNormal`).
 * Web needs no twin — its `LineHeightExtractor.ts` already tests
 * `o.original === 'normal'` FIRST and emits the CSS keyword verbatim, which the
 * cascade resolves against the real font for free.
 */
object LineHeightNormal {

    /** The IR type name this discriminator belongs to (no magic literals). */
    const val PROPERTY_TYPE: String = "LineHeight"

    /**
     * True iff [data] is a `LineHeight` payload whose `original` discriminator
     * is the bare string `"normal"`.
     *
     * The serializer (LineHeightSerializer.serialize) encodes ONLY the `normal`
     * keyword as a JSON *string* at `original`; every other flavour encodes an
     * object with a `type` field (`number` / `percentage` / `length` /
     * `expression` / `keyword`). So the string test is exact and cannot collide
     * — in particular `line-height: inherit` rides `{"type":"keyword",
     * "keyword":"inherit"}`, an object, and correctly returns false here.
     */
    fun isDeclaredNormal(data: JsonElement?): Boolean {
        // Anything that is not the `{multiplier?, original}` envelope (a bare
        // number from a legacy wire, an array, null) cannot be the keyword.
        val obj = data as? JsonObject ?: return false
        // A JsonPrimitive that `isString` is the keyword encoding; a numeric
        // primitive would report content too, hence the explicit isString gate.
        val original = obj["original"] as? JsonPrimitive ?: return false
        return original.isString && original.content == "normal"
    }

    /**
     * Cascade-aware form for the renderer: true iff the LAST `LineHeight`
     * declaration in [properties] is the `normal` keyword.
     *
     * Last-write-wins mirrors what TextStyleApplier / TypographyExtractor do
     * when they fold the same list into a TextStyle (each later declaration
     * overwrites the accumulator), so the gate and the value can never disagree
     * about WHICH declaration won.
     */
    fun isDeclaredNormal(properties: List<IRProperty>): Boolean =
        isDeclaredNormal(properties.lastOrNull { it.type == PROPERTY_TYPE }?.data)

    /** Where a text run's line box comes from — the three states above. */
    enum class LineBoxSource {
        /** A number/length was declared: use it verbatim. */
        DECLARED,
        /** `normal` was declared: use the rendered face's own metrics. */
        NATURAL,
        /** Nothing was declared: use the WPT / native calibration, UNCHANGED. */
        CALIBRATED
    }

    /**
     * The pure three-state decision, shared by both natives so their tables can
     * never drift (SwiftUI twin: `LineHeightNormal.lineBoxSource`, consumed by
     * `ComponentRenderer.effectiveLineHeight`).
     *
     * ## Why [wptCapture] gates the NATURAL row
     * Font-natural metrics are the CSS-correct reading of `normal` on EVERY
     * surface, but both natives have shipped a numeric approximation for it for
     * the whole life of the committed 327-pair dark-stage corpus: the wire's
     * legacy 1.2 multiplier flows through the typography extractors and lands
     * as a real declared value (fixtures/properties/typography/line-height.json
     * `LineHeight_Normal` → tools/visual/baseline/{Android,iOS}__063_Typography_
     * LineHeight.png). Switching that path to natural metrics would move those
     * baselines, and this lane cannot re-capture them (no device runs). So the
     * correction is scoped to WPT capture — the only surface that is diffed
     * against a Chromium reference and the only one where the divergence was
     * ever measured. Outside WPT capture [hasDeclaredValue] is true for
     * `normal` (the 1.2 value survived extraction) and DECLARED wins, exactly
     * as before. Stated risk: the product/dark-stage path stays CSS-imprecise
     * for `line-height: normal` on both natives, where web has always been
     * exact (its extractor emits the CSS keyword and the browser resolves it).
     *
     * Order matters and is asserted by the pins: NATURAL is tested before
     * DECLARED because under WPT capture the keyword must beat the numeric
     * stand-in the extractors derived FROM that same keyword.
     */
    fun lineBoxSource(
        hasDeclaredValue: Boolean,
        declaredNormal: Boolean,
        wptCapture: Boolean
    ): LineBoxSource =
        when {
            declaredNormal && wptCapture -> LineBoxSource.NATURAL
            hasDeclaredValue -> LineBoxSource.DECLARED
            else -> LineBoxSource.CALIBRATED
        }
}
