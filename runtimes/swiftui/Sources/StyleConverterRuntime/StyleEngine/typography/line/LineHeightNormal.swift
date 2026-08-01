//
//  LineHeightNormal.swift
//  StyleEngine/typography/line — wave 22, lane FONT.
//
//  Byte-parallel twin of Compose
//  runtimes/compose/src/main/java/com/styleconverter/runtime/typography/LineHeightNormal.kt.
//

import Foundation

/// The DECLARED-`normal` line-height discriminator.
///
/// ## Why a third state exists at all
/// The WPT capture path calibrates a line box for text whose IR declares NO
/// line-height: `ComponentRenderer.wptRefLineBoxPx` (20 — the browser-ref
/// injection's pinned `line-height: 1.25` at the 16px root,
/// capture-browser-ref.mjs REF_LINE_HEIGHT) so stacked bars stop drifting
/// against the ref. That calibration is correct precisely BECAUSE the ref's own
/// zero-specificity `:where(body)` rule is what supplies the ref's line box
/// when the test declares none.
///
/// `font: 92px Arial` is the case where it is WRONG. css-fonts-4 §4.3 makes the
/// `font` shorthand RESET `line-height` to `normal`, and a directly-matching
/// declaration beats an inherited one no matter how specific the source rule
/// is — so Chromium lays that div out on Arial's natural metrics (hhea asc 1854
/// + desc 434 + gap 67 over a 2048 upem = 1.1499em ≈ 105.8px at 92px), NOT on
/// the injected 1.25 (115px). Until wave 22 the converter never emitted the
/// reset (FontExpander.kt), so the runtime could not tell "author said
/// `normal`" from "author said nothing" and applied the calibration to both.
///
/// The converter now emits `{"multiplier":1.2,"original":"normal"}` for the
/// reset. `multiplier` 1.2 is a legacy COMPATIBILITY fallback on the wire (see
/// irmodels/properties/typography/LineHeightProperty.kt — it predates this lane
/// and is deliberately left byte-identical so no schema golden moves); it is
/// NOT the CSS-correct value, because `normal` is a font-metric lookup and no
/// single ratio can stand in for it across faces. This predicate is what lets a
/// consumer tell the keyword apart from a real authored number that happens to
/// share the same `multiplier` field.
///
/// ## The contract the three states must satisfy
/// | IR                                | WPT capture      | elsewhere         |
/// |-----------------------------------|------------------|-------------------|
/// | LineHeight absent                 | calibration      | calibration       |
/// | LineHeight `original == "normal"` | natural metrics  | 1.2× (UNCHANGED)  |
/// | LineHeight number / % / length    | that value       | that value        |
///
/// The middle row under WPT capture is the ONLY cell this lane moves; see
/// `lineBoxSource` for why the same cell outside WPT capture is deliberately
/// left on its historical numeric approximation. No committed fixture under
/// fixtures/properties|components uses the `font` shorthand (checked), so the
/// converter-side reset cannot reach the dark stage at all.
///
/// Web needs no twin — its `LineHeightExtractor.ts` already tests
/// `o.original === 'normal'` FIRST and emits the CSS keyword verbatim, which
/// the browser's own cascade resolves against the real font for free.
enum LineHeightNormal {

    /// The IR type name this discriminator belongs to (no magic literals).
    static let propertyType = "LineHeight"

    /// True iff `data` is a `LineHeight` payload whose `original`
    /// discriminator is the bare string `"normal"`.
    ///
    /// The Kotlin serializer (LineHeightSerializer.serialize) encodes ONLY the
    /// `normal` keyword as a JSON *string* at `original`; every other flavour
    /// encodes an object carrying a `type` field (`number` / `percentage` /
    /// `length` / `expression` / `keyword`). So the string test is exact and
    /// cannot collide — in particular `line-height: inherit` rides
    /// `{"type":"keyword","keyword":"inherit"}`, an object, and correctly
    /// returns false here.
    static func isDeclaredNormal(_ data: IRValue?) -> Bool {
        // Anything that is not the `{multiplier?, original}` envelope (a bare
        // number from a legacy wire, an array, null) cannot be the keyword.
        guard let data, case .object(let o) = data else { return false }
        // `.stringValue` is nil for every non-`.string` case, so a numeric
        // `original` can never be mistaken for the keyword.
        return o["original"]?.stringValue == "normal"
    }

    /// Cascade-aware form for the renderer: true iff the LAST `LineHeight`
    /// declaration in `properties` is the `normal` keyword.
    ///
    /// Last-write-wins mirrors what LineHeightExtractor does when it folds the
    /// same list into a config (each later declaration overwrites the
    /// accumulator), so the gate and the value can never disagree about WHICH
    /// declaration won.
    static func isDeclaredNormal(_ properties: [IRProperty]) -> Bool {
        isDeclaredNormal(properties.last { $0.type == propertyType }?.data)
    }

    /// Where a text run's line box comes from — the three states above.
    enum LineBoxSource {
        /// A number/length was declared: use it verbatim.
        case declared
        /// `normal` was declared: use the rendered face's own metrics.
        case natural
        /// Nothing was declared: use the WPT calibration, UNCHANGED.
        case calibrated
    }

    /// The pure three-state decision, shared by both natives so their tables
    /// can never drift (Compose twin: `LineHeightNormal.lineBoxSource`,
    /// consumed by the placeholder's `effectiveLineHeight` `when`).
    ///
    /// ## Why `wptCapture` gates the `.natural` row
    /// Font-natural metrics are the CSS-correct reading of `normal` on EVERY
    /// surface, but both natives have shipped a numeric approximation for it
    /// for the whole life of the committed 327-pair dark-stage corpus: the
    /// wire's legacy 1.2 multiplier flows through the typography extractors and
    /// lands as a real declared value
    /// (fixtures/properties/typography/line-height.json `LineHeight_Normal` →
    /// tools/visual/baseline/{iOS,Android}__063_Typography_LineHeight.png).
    /// Switching that path to natural metrics would move those baselines, and
    /// this lane cannot re-capture them (no device runs). So the correction is
    /// scoped to WPT capture — the only surface diffed against a Chromium
    /// reference and the only one where the divergence was ever measured.
    /// Outside WPT capture `hasDeclaredValue` is true for `normal` (the 1.2
    /// value survived extraction) and `.declared` wins, exactly as before.
    /// Stated risk: the product/dark-stage path stays CSS-imprecise for
    /// `line-height: normal` on both natives, where web has always been exact
    /// (its extractor emits the CSS keyword and the browser resolves it).
    ///
    /// Order matters and is asserted by the pins: `.natural` is tested before
    /// `.declared` because under WPT capture the keyword must beat the numeric
    /// stand-in the extractors derived FROM that same keyword.
    static func lineBoxSource(hasDeclaredValue: Bool,
                              declaredNormal: Bool,
                              wptCapture: Bool) -> LineBoxSource {
        if declaredNormal && wptCapture { return .natural }
        if hasDeclaredValue { return .declared }
        return .calibrated
    }
}
