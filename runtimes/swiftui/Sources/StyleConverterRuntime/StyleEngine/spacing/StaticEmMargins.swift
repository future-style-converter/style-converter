//
//  StaticEmMargins.swift
//  StyleEngine/spacing — Wave 22 (B-RC2): STATICALLY resolvable `em` block
//  margins for the composed WPT root stack.
//
//  ## The measured defect
//  css-text-decor/text-decoration-dotted-001 declares `margin: .5em` on
//  three sibling divs that also declare `font-size: 92px` (live IR at
//  tools/titan/runs/wave21-final/sections/css-text-decor/per-test-ir/
//  wpt__css-text-decor__text-decoration-dotted-001.json — MarginTop ships
//  as `{"original":{"v":0.5,"u":"EM"}}`, FontSize as `{"px":92,…}`).
//  0.5em × 92px = 46px per edge, and the browser-ref COLLAPSES the abutting
//  46/46 pair to ONE 46px gap (CSS 2.1 §8.3.1). The natives instead bailed:
//  MarginCollapse.staticVerticalEdges classifies ONLY `.exact` px, so any
//  relative flavor returned nil, rootStackMargin took its R4/R5 branch, no
//  strip happened, and both roots painted their own 46px margin — 92px of
//  inter-div space against the ref's 46 (measured Android capture).
//
//  ## Why em — and ONLY em — is statically resolvable here
//  css-values-4 §5.1.1: `em` resolves against the element's OWN computed
//  font-size (except on `font-size` itself, where it is the inherited one).
//  For a margin the base is therefore the element's own font-size — and
//  FontSize rides the SAME property list this classifier already reads, so
//  the base is available without any layout or inheritance channel.
//    • `%` needs the containing block's inline size (css-box-4 §3) — not on
//      the property list. Still bails.
//    • `vw`/`vh`/`cq*`/`calc()`/`rem`/`ex`/`ch` need the viewport, container
//      query, root element or font metrics. All still bail.
//    • An em margin on a component with NO own FontSize declaration would
//      need the INHERITED size, which the flattened composed-root IR does
//      not carry. Bails too — guessing 16px would be a silent fallthrough.
//
//  Byte-parallel with the Kotlin twin (apps/android-harness
//  StaticEmMargins.kt): the shared E1–E8 pin table asserts IDENTICAL
//  expected values on both platforms. ONLY the composed WPT canvas reaches
//  this file (through UABlockMargin.staticDeclaredEdges); the runtime's own
//  §8.3.1 machinery (MarginCollapsePlanner / MarginCollapseChildGates) still
//  calls MarginCollapse.staticVerticalEdges directly, so the 327-pair
//  dark-stage baseline is untouched by construction.
//

// CoreGraphics for CGFloat — the currency type of the collapse lane.
import CoreGraphics

// Pure namespace — never instantiated (same shape as MarginCollapse).
enum StaticEmMargin {

    /// The component's OWN font-size in px, or nil when it declares none /
    /// declares a non-absolute one. `last(where:)` mirrors the extractors'
    /// last-declaration-wins fold (MarginExtractor's two passes, the
    /// Kotlin AbsposInsetStretch.strictSidePx read) so a duplicated
    /// FontSize resolves to the same value the renderer paints with.
    /// `> 0` because a zero/negative font-size cannot scale a margin into
    /// anything meaningful — the caller conservatively bails instead.
    static func ownFontSizePx(_ properties: [IRProperty]) -> CGFloat? {
        // No FontSize longhand at all ⇒ the used size is INHERITED, which
        // the flattened composed-root IR does not carry ⇒ not resolvable.
        guard let data = properties.last(where: { $0.type == "FontSize" })?.data
        else { return nil }
        // Only an absolute px font-size is an honest em base. `font-size:
        // 2em`/`120%` decode to `.relative` and correctly bail here.
        guard case .exact(let px) = extractLength(data), px > 0 else { return nil }
        return CGFloat(px)
    }

    /// One margin edge → non-negative px, resolving `em` against `emBasePx`.
    /// Nil = not statically resolvable ⇒ the caller bails the whole root.
    ///
    /// Rule table (shared with the Kotlin twin, pins E1–E5):
    ///  E1 absolute px ≥ 0 → itself (delegated to MarginCollapse.staticEdge
    ///     so the px lane has exactly ONE definition and cannot drift).
    ///  E2 `em` with a resolved pxFallback → that px (the converter emits
    ///     `{"px":N,"original":{"v":…,"u":"EM"}}` when it could pre-resolve;
    ///     the pre-resolved value is authoritative over re-multiplying).
    ///  E3 `em` without a fallback, own font-size known → value × base.
    ///  E4 `em` without a fallback and NO own font-size → nil (inherited
    ///     base unknown — see the file header).
    ///  E5 anything else (auto, negative, %, vw, calc, unknown) → nil.
    static func edgePx(_ v: LengthValue, emBasePx: CGFloat?) -> CGFloat? {
        // E1 — the existing absolute-px classifier, unchanged and reused.
        if let px = MarginCollapse.staticEdge(v) { return px }
        // E5 (part) — only the `em` relative flavor continues; every other
        // shape (auto/%/vw/calc/unknown/negative px) already failed E1 and
        // is deliberately left unresolved.
        guard case .relative(let value, let unit, let pxFallback) = v,
              unit == .em else { return nil }
        // E2 — a converter-resolved px wins (css-values-4 §5.1.1 already
        // applied upstream); negatives stay out of the §8.3.1 scope.
        if let fb = pxFallback { return fb >= 0 ? CGFloat(fb) : nil }
        // E4 — no own font-size ⇒ nothing honest to multiply against.
        guard let base = emBasePx else { return nil }
        // E3 — the em multiplication; negatives stay out of scope (the
        // §8.3.1 negative-margin rules are not emulated by this lane).
        let px = CGFloat(value) * base
        return px >= 0 ? px : nil
    }

    /// The (top, bottom) static block margins of a property list with `em`
    /// resolved, or nil when either vertical edge is out of scope. A strict
    /// SUPERSET of MarginCollapse.staticVerticalEdges: for a list carrying
    /// no em margin the two return byte-identical results (pin E8), which
    /// is what keeps every wave-19 R/S/T pin unchanged.
    ///
    /// Horizontal edges are ignored — only vertical margins collapse in
    /// horizontal writing mode (§8.3.1), so dotted-001's `margin-left: .5em`
    /// keeps rendering through MarginApplier untouched.
    static func verticalEdges(_ properties: [IRProperty])
        -> (top: CGFloat, bottom: CGFloat)? {
        // No margin longhand at all ⇒ both edges are the initial 0
        // (css-box-3 §3) — fully eligible, same as staticVerticalEdges.
        guard let cfg = MarginExtractor.extract(from: properties) else { return (0, 0) }
        // The em base for THIS component (nil ⇒ em edges will bail, E4).
        let base = ownFontSizePx(properties)
        // Each vertical edge must classify; one out-of-scope edge bails both
        // (collapsing only one side would produce geometry no engine renders).
        guard let t = edgePx(cfg.top, emBasePx: base),
              let b = edgePx(cfg.bottom, emBasePx: base) else { return nil }
        return (t, b)
    }
}
