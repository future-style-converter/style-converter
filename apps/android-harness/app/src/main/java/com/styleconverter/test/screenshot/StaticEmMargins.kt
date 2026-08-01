package com.styleconverter.test.screenshot

// StaticEmMargins — Wave 22 (B-RC2): STATICALLY resolvable `em` block
// margins for the composed WPT root stack. Byte-parallel Kotlin twin of
// runtimes/swiftui/…/StyleEngine/spacing/StaticEmMargins.swift; the shared
// E1–E8 pin table (StaticEmMarginsTest / StaticEmMarginsTests.swift)
// asserts IDENTICAL expected values on both platforms.
//
// ## The measured defect
// css-text-decor/text-decoration-dotted-001 declares `margin: .5em` on three
// sibling divs that also declare `font-size: 92px` (live IR at
// tools/titan/runs/wave21-final/sections/css-text-decor/per-test-ir/
// wpt__css-text-decor__text-decoration-dotted-001.json — MarginTop ships as
// `{"original":{"v":0.5,"u":"EM"}}`, FontSize as `{"px":92,…}`). 0.5em × 92px
// = 46px per edge, and the browser-ref COLLAPSES the abutting 46/46 pair to
// ONE 46px gap (CSS 2.1 §8.3.1). The natives instead bailed:
// BlockMarginCollapse.blockMarginsOrNull classifies ONLY LengthValue.Exact,
// so any relative flavor returned null, rootStackMargin took its R4/R5
// branch, no strip happened, and BOTH roots painted their own 46px margin —
// a measured 92px of inter-div space against the ref's 46px.
//
// ## Why em — and ONLY em — is statically resolvable here
// css-values-4 §5.1.1: `em` resolves against the element's OWN computed
// font-size (except on `font-size` itself, where it is the inherited one).
// For a margin the base is therefore the element's own font-size — and
// FontSize rides the SAME property list this classifier already reads, so
// the base needs no layout pass and no inheritance channel.
//   - `%` needs the containing block's inline size (css-box-4 §3) — not on
//     the property list. Still bails.
//   - `vw`/`vh`/`cq*`/`calc()`/`rem`/`ex`/`ch` need the viewport, container
//     query, root element or font metrics. All still bail.
//   - An em margin on a component with NO own FontSize would need the
//     INHERITED size, which the flattened composed-root IR does not carry.
//     Bails too — guessing 16px would be a silent fallthrough.
//
// ONLY the composed WPT canvas reaches this file (ScreenshotCaptureScreen's
// rootPlans fold). The runtime's own §8.3.1 machinery (ComponentRenderer's
// block collapse plan) still calls BlockMarginCollapse.blockMarginsOrNull
// directly, so the 327-pair dark-stage baseline is untouched by construction.

// IR model + the shared length primitives, via the :runtime project dep.
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.core.types.extractLength
import com.styleconverter.runtime.spacing.MarginExtractor
import com.styleconverter.runtime.spacing.MarginValue

object StaticEmMargin {

    /**
     * The component's OWN font-size in px, or null when it declares none /
     * declares a non-absolute one. `lastOrNull` mirrors the extractors'
     * last-declaration-wins fold (MarginExtractor's two passes,
     * AbsposInsetStretch.strictSidePx's read) so a duplicated FontSize
     * resolves to the same value the renderer paints with. `> 0` because a
     * zero/negative font-size cannot scale a margin into anything
     * meaningful — the caller conservatively bails instead.
     */
    fun ownFontSizePx(properties: List<IRProperty>): Float? {
        // No FontSize longhand at all ⇒ the used size is INHERITED, which
        // the flattened composed-root IR does not carry ⇒ not resolvable.
        val data = properties.lastOrNull { it.type == "FontSize" }?.data ?: return null
        // Only an absolute px font-size is an honest em base. `font-size:
        // 2em` / `120%` decode to Relative and correctly bail here.
        val v = extractLength(data)
        return (v as? LengthValue.Exact)?.px?.takeIf { it > 0.0 }?.toFloat()
    }

    /**
     * One margin edge → non-negative px, resolving `em` against [emBasePx].
     * Null = not statically resolvable ⇒ the caller bails the whole root.
     *
     * Rule table (shared with the Swift twin, pins E1–E5):
     *  E1 absolute px ≥ 0 → itself (identical to
     *     BlockMarginCollapse.plainPxOrNull's Exact branch — the same
     *     non-negativity floor, since §8.3.1's negative-margin rules are
     *     not emulated by this lane).
     *  E2 `em` with a resolved pxFallback → that px (the converter emits
     *     `{"px":N,"original":{"v":…,"u":"EM"}}` when it could pre-resolve;
     *     the pre-resolved value is authoritative over re-multiplying).
     *     Kotlin's extractLength collapses any px-carrying shape to Exact
     *     upstream, so this branch is reachable only on the Swift twin —
     *     it is spelled out here so the two rule tables read identically.
     *  E3 `em` without a fallback, own font-size known → value × base.
     *  E4 `em` without a fallback and NO own font-size → null (inherited
     *     base unknown — see the file header).
     *  E5 anything else (auto, negative, %, vw, calc, unknown) → null.
     */
    fun edgePx(v: MarginValue?, emBasePx: Float?): Float? = when (v) {
        // Unset side — the CSS initial value 0 (a zero adjoining margin),
        // exactly as BlockMarginCollapse.plainPxOrNull treats it.
        null -> 0f
        // E5 — `margin: auto` is alignment, not pixels; MarginApplier turns
        // vertical auto pairs into centering, so that path stays untouched.
        MarginValue.Auto -> null
        is MarginValue.Length -> when (val len = v.value) {
            // E1 — the absolute-px lane, unchanged from the narrow classifier.
            is LengthValue.Exact -> if (len.px >= 0.0) len.px.toFloat() else null
            is LengthValue.Relative -> {
                // E5 (part) — only `em` continues; %/vw/cq*/rem/ex/ch need a
                // context this pure classifier cannot see (file header).
                if (len.unit != LengthUnit.EM) {
                    null
                } else {
                    // E2 — a converter-resolved px wins (css-values-4 §5.1.1
                    // already applied upstream); else E3's multiplication
                    // against the component's own font size, or E4's bail.
                    val px = len.pxFallback
                        ?: emBasePx?.let { base -> len.value * base.toDouble() }
                    // Negatives stay out of the §8.3.1 emulation's scope.
                    if (px != null && px >= 0.0) px.toFloat() else null
                }
            }
            // E5 — Auto/Intrinsic/Fraction/Calc/None/Unknown are never a
            // statically collapsible margin length.
            else -> null
        }
    }

    /**
     * The (top, bottom) static block margins of a property list with `em`
     * resolved, or null when either vertical edge is out of scope. A strict
     * SUPERSET of BlockMarginCollapse.blockMarginsOrNull: for a list carrying
     * no em margin the two return byte-identical results (pin E8), which is
     * what keeps every wave-19 R/S/T pin unchanged.
     *
     * Horizontal edges are ignored — only vertical margins collapse in
     * horizontal writing mode (§8.3.1), so dotted-001's `margin-left: .5em`
     * keeps rendering through MarginApplier untouched.
     */
    fun verticalEdges(properties: List<IRProperty>): Pair<Float, Float>? {
        // Resolve logical→physical through the SAME extractor the renderer
        // paints with (margin-block-start feeds top in horizontal-tb;
        // isRtl only affects the inline axis this fold never touches).
        val resolved = MarginExtractor.extract(properties.map { it.type to it.data })
            .resolve(isRtl = false)
        // The em base for THIS component (null ⇒ em edges will bail, E4).
        val base = ownFontSizePx(properties)
        // Each vertical edge must classify; one out-of-scope edge bails both
        // (collapsing only one side would produce geometry no engine renders).
        val top = edgePx(resolved.top, base) ?: return null
        val bottom = edgePx(resolved.bottom, base) ?: return null
        return top to bottom
    }
}
