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
//  css-values-4 §6.1.1: `em` resolves against the element's OWN computed
//  font-size (except on `font-size` itself, where it is the inherited one).
//  For a margin the base is therefore the element's own font-size — and
//  FontSize rides the SAME property list this classifier already reads, so
//  the base is available without any layout or inheritance channel.
//    • `%` needs the containing block's inline size (css-box-4 §3) — not on
//      the property list. Still bails.
//    • `vw`/`vh`/`cq*`/`calc()`/`rem`/`ex`/`ch` need the viewport, container
//      query, root element or font metrics. All still bail.
//    • An em margin on a component with NO own FontSize (wave 45, H0): the
//      base is the INHERITED size — and for a composed WPT ROOT that
//      inherited size is statically known, because roots are body-level
//      children and the harness canvas never re-declares a body font-size
//      (a fixture that pins one has it folded into the root's own list by
//      the converter, hitting the declared lane above). So the base is the
//      UA `medium` default: 16px, or Chromium's 13px `defaultFixedFontSize`
//      when the root's FIRST declared family is the monospace generic —
//      consulted through MonospaceUAFontSize (the single quirk owner, the
//      same ladder UAElementFontRule.emBasePx already runs), so one table
//      decides the fixed default for the whole runtime. Pre-wave-45 this
//      bailed instead, and the composed stack DOUBLE-spaced: the fold
//      emitted the UA inter-component gap (R4/R5, no strip) AND
//      MarginApplier rendered the full declared margin — measured +16px on
//      floats-clear-multicol-002 / discard-multicol-001's boxes.
//    • A DECLARED but non-absolute FontSize (em/%/`var()`/`calc()`) still
//      bails — that base is genuinely unresolvable here, and guessing
//      would be a silent fallthrough.
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

    /// The UA `defaultFontSize` — the `medium` keyword's value in every
    /// engine's standard (non-monospace) bucket, and the root font the UA
    /// margin table this lane feeds is calibrated to ("at a 16px root",
    /// UABlockMargin). Same number UAElementFontRule.emBasePx falls back
    /// to; the Kotlin twin mirrors it as `UA_DEFAULT_FONT_SIZE_PX`.
    static let uaDefaultPx: CGFloat = 16

    /// The component's OWN font-size in px — the em base of css-values-4
    /// §6.1.1 — or nil when a DECLARED size is not statically resolvable.
    ///
    /// Resolution ladder (wave 45, H0 — the UAElementFontRule.emBasePx
    /// ladder, applied to the composed-root lane):
    ///  F1 declared absolute px > 0 → that px. `last(where:)` mirrors the
    ///     extractors' last-declaration-wins fold (MarginExtractor's two
    ///     passes, the Kotlin AbsposInsetStretch.strictSidePx read) so a
    ///     duplicated FontSize resolves to the value the renderer paints
    ///     with; `> 0` because a zero/negative size cannot scale a margin
    ///     into anything meaningful.
    ///  F2 declared but non-absolute (em/%/`var()`/`calc()`/unparseable) →
    ///     nil — the E4 bail, kept: the base is genuinely unresolvable
    ///     without the inheritance channel, so the root falls back to the
    ///     pre-fix R4/R5 render path instead of guessing.
    ///  F3 no declaration, first declared family is the monospace generic →
    ///     13px, via MonospaceUAFontSize.resolvePx (Chromium's
    ///     `defaultFixedFontSize`, the value the frozen refs rasterised).
    ///  F4 no declaration otherwise → `uaDefaultPx`: a composed root is a
    ///     body-level child, so its inherited size IS the UA `medium`
    ///     default (see the file header for why this is statically sound
    ///     on this canvas and only this canvas).
    static func ownFontSizePx(_ properties: [IRProperty]) -> CGFloat? {
        // F3/F4 — no FontSize longhand at all ⇒ the UA-default ladder.
        // resolvePx's own "any declared FontSize disarms the quirk" gate is
        // trivially satisfied on this branch, so the two owners can never
        // disagree about when the quirk fires.
        guard let data = properties.last(where: { $0.type == "FontSize" })?.data
        else { return MonospaceUAFontSize.resolvePx(from: properties).map { CGFloat($0) } ?? uaDefaultPx }
        // F1/F2 — only an absolute px font-size is an honest em base.
        // `font-size: 2em`/`120%` decode to `.relative` and bail here.
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
    ///  E4 `em` without a fallback and an UNRESOLVABLE own font-size →
    ///     nil. Wave 45 (H0) narrowed this bail: an ABSENT FontSize now
    ///     resolves through ownFontSizePx's UA-default ladder (F3/F4 —
    ///     13px monospace quirk or the 16px default), so E4 fires only for
    ///     a DECLARED-but-relative/`var()`/`calc()` size (F2), where the
    ///     base is genuinely unknowable without the inheritance channel.
    ///  E5 anything else (auto, negative, %, vw, calc, unknown) → nil.
    static func edgePx(_ v: LengthValue, emBasePx: CGFloat?) -> CGFloat? {
        // E1 — the existing absolute-px classifier, unchanged and reused.
        if let px = MarginCollapse.staticEdge(v) { return px }
        // E5 (part) — only the `em` relative flavor continues; every other
        // shape (auto/%/vw/calc/unknown/negative px) already failed E1 and
        // is deliberately left unresolved.
        guard case .relative(let value, let unit, let pxFallback) = v,
              unit == .em else { return nil }
        // E2 — a converter-resolved px wins (css-values-4 §6.1.1 already
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
        // The em base for THIS component — declared px, or (wave 45, H0)
        // the UA-default ladder when nothing is declared; nil only for a
        // declared-but-unresolvable size (⇒ em edges bail, E4).
        let base = ownFontSizePx(properties)
        // Each vertical edge must classify; one out-of-scope edge bails both
        // (collapsing only one side would produce geometry no engine renders).
        guard let t = edgePx(cfg.top, emBasePx: base),
              let b = edgePx(cfg.bottom, emBasePx: base) else { return nil }
        return (t, b)
    }
}
