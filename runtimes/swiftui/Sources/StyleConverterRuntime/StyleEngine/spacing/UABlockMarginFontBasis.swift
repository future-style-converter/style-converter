//
//  UABlockMarginFontBasis.swift
//  StyleEngine/spacing — wave 46, lane H1 (Y8's iOS twin): the FONT BASIS
//  of the UA default block margins.
//
//  ## The measured defect
//  CSS2/cascade/inherit-computed-001 is a single composed ROOT `<p>` that
//  declares `font-size: larger` (wire: {"original":{"type":"relative",
//  "keyword":"larger"}}, no px). The Chromium UA sheet gives a `<p>`
//  `margin-block: 1em` (HTML §15.3.3 "Flow content"), and css-values-4
//  §6.1.1 resolves that em against the element's OWN computed font-size —
//  `larger` of the inherited 16px = 19.2px — so the browser-ref's border
//  box starts at y = 16 (image pad) + 19.2 = 35.2 → row 35. Both natives
//  started it at row 32: 16 + 16, the table's fixed "16px at a 16px root"
//  value. Row-by-row the two boxes are otherwise IDENTICAL (same glyph
//  rows, the em's atom bar at the same x) — translating the wave45-final
//  iOS capture down 3px re-scores it from 0.8863 to 0.9767 through the
//  scorer's own diffWebVsRef (Android: 0.8959 → 0.9984). The whole
//  residual is this basis.
//
//  ## What this module is
//  Two PURE tables the UA margin fold consults, kept out of
//  UABlockChildMargin.swift / UABlockMargin.swift so those files stay
//  under their 200-line target:
//   1. `uaBlockMarginEm(forTag:)` — the UA sheet's block-margin em FACTOR
//      per tag (1em for p/ul/ol/blockquote/pre/figure/h3, the .67/.83/
//      1.33/1.67/2.33em ladder for the other headings; Chromium html.css).
//   2. `ownFontSizePx(_:inheritedPx:sourceTag:)` — the element's own
//      computed font-size in px from its property list, against a
//      caller-supplied inherited base (css-values-4 §6.1.1 em rule for
//      font-size itself + CSS 2.1 §15.7 for the relative keywords). It
//      returns NIL when the element carries NO own font signal (no
//      FontSize, no monospace-quirk family — and, for an em-sized UA
//      heading, not even that: see `uaFontSizeIsKeywordSized`), which is
//      the contract that keeps every existing caller byte-identical: a nil basis makes the UA table fall back to its
//      Round-4 ref-calibrated 16px-root values, so the hundreds of corpus
//      `<p>`/`<ul>`/`<hN>` roots and children without a font-size
//      declaration cannot move by a pixel.
//
//  ## Why the keyword ladder is duplicated rather than shared
//  DynamicValueResolver.resolveFontSizeWire owns the same relative ladder
//  for the PAINTED size (em/rem/%/larger/smaller against the inherited
//  size), but it is internal to the variables lane, returns a Double and
//  bails on the absolute-keyword and top-level-px shapes by design (those
//  ride other lanes). UABlockMarginFontBasisTests' F-pins assert the two
//  agree on every shape both resolve, so a drift between "the size the
//  glyphs paint at" and "the size the UA margin resolves against" fails a
//  unit test instead of a capture.
//
//  ## Known limits (stated, not hidden)
//   - A DECLARED but unresolvable size (var()/calc()/vw/ex/ch) yields nil
//     → the table's 16px-root value, exactly the pre-wave-46 render.
//   - The CHILD fold (MarginCollapse.containerPlan's per-child merge) has
//     no inheritance channel into this module yet and keeps the table —
//     same deferral as the Compose twin, same corpus simulation (only
//     inherit-computed-002 and counter-style-at-rule/disclosure-styles
//     carry a block child whose basis differs from 16px, both ~0.60 for
//     unrelated reasons — a renderer seam, NOT ComponentRenderer's lane).
//
//  Byte-parallel twin: Compose
//  `runtime/spacing/UaBlockMarginFontBasis.kt` (B1-B8 / F pins shared).
//

// CoreGraphics for CGFloat — the currency type of the collapse lane.
import CoreGraphics

// public: ComposedCaptureCanvas (apps/ios-harness) resolves each root's
// basis and threads it into UABlockMargin.rootStackMargin; the runtime
// test target pins the ladder.
public enum UABlockMarginFontBasis {

    /// The UA `medium` default — 16px: the inherited size of every composed
    /// WPT root (body-level children of a canvas that never re-declares a
    /// body font-size) and the root the UA margin table is calibrated to.
    /// Same number as StaticEmMargin.uaDefaultPx; the Kotlin twin names it
    /// `UA_DEFAULT_FONT_SIZE_PX`.
    public static let uaDefaultFontSizePx: CGFloat = 16

    /// CSS 2.1 §15.7's recommended ratio between adjacent absolute-size
    /// ladder entries — the ±1-step multiplier for `larger` / `smaller`.
    /// Blink's FontSizeFunctions uses exactly ×1.2 / ÷1.2 for a non-keyword
    /// inherited size, which is what the frozen refs rasterised (19.2px on
    /// inherit-computed-001). Same constant DynamicValueResolver paints with.
    public static let relativeSizeStep: CGFloat = 1.2

    /// The UA stylesheet's block-margin em factor for `tag` — the
    /// `margin-block: <n>em` of Chromium's html.css (HTML §15.3.3 sectioning
    /// headings, §15.3.8 flow content: p / ul / ol / pre / blockquote /
    /// figure). Nil for every tag the UA sheet gives no block margin
    /// (div, section, li, span, unknown, absent).
    ///
    /// NOTE the factor is over the element's COMPUTED size: for the headings
    /// that size is itself a UA em (h1 2em, h2 1.5em, h3 1.17em, h4 1em, h5
    /// .83em, h6 .67em of the inherited size) unless the author overrides
    /// it, which is exactly the case where a caller has an own basis.
    public static func uaBlockMarginEm(forTag tag: String?) -> CGFloat? {
        switch (tag ?? "").lowercased() {
        // `margin-block: 1em` tags — their UA font-size is the inherited one.
        case "p", "ul", "ol", "blockquote", "pre", "figure": return 1
        // Headings: html.css `h1 { margin-block: 0.67em }` … `h6 { 2.33em }`.
        case "h1": return 0.67
        case "h2": return 0.83
        case "h3": return 1
        case "h4": return 1.33
        case "h5": return 1.67
        case "h6": return 2.33
        // No UA block margin for flow containers / unknown tags.
        default:   return nil
        }
    }

    /// The tags whose UA font-size is itself an EM of the inherited size —
    /// Chromium html.css: `h1 { font-size: 2em }`, h2 1.5em, h3 1.17em,
    /// h5 .83em, h6 .67em. (`h4` declares NO font-size there, so it stays
    /// keyword-sized exactly like the flow tags and is deliberately absent.)
    ///
    /// Why this set exists at all: it is the B1 gate. Blink applies the
    /// `defaultFixedFontSize` (13px) only where the computed size came from
    /// a KEYWORD — `FontBuilder::UpdateComputedSize` re-resolves the keyword
    /// against the fixed table. For an element whose specified size is an em
    /// (KeywordSize == 0) `FontBuilder::CheckForGenericFamilyChange` instead
    /// SCALES the specified size by fixed/default = 13/16, so a monospace
    /// `<h1>` computes 2em × 16 × 13/16 = 26px and its UA margin is
    /// .67 × 26 ≈ 17.4px — nowhere near the .67 × 13 = 8.71px an unguarded
    /// B1 would hand back. Rather than model that scaling (it needs the UA
    /// font-size ladder, an inheritance channel this module does not have,
    /// and a Blink rule this lane cannot verify against a capture), B1
    /// returns nil for these tags: the caller keeps the Round-4
    /// ref-calibrated table (h1 → 21px), which is the pre-wave-46 render and
    /// the CORRECT value for the overwhelmingly common non-monospace heading.
    /// Latent either way — the frozen corpus has zero B1 roots today.
    private static let emSizedUAFontTags: Set<String> = ["h1", "h2", "h3", "h5", "h6"]

    /// Is `tag`'s UA font-size the inherited KEYWORD size — i.e. may the
    /// monospace fixed-default quirk (B1) resolve it?
    ///
    /// True for the 1em keyword-sized block tags (p / ul / ol / blockquote /
    /// figure / pre / h4) and, deliberately, for every tag OUTSIDE the UA
    /// block-margin table (div, li, span, unknown, absent): those get no UA
    /// block margin at all (`uaBlockMarginEm(forTag:)` is nil → 0px), so the
    /// basis they resolve cannot move a pixel, and keeping the quirk there
    /// leaves every pre-wave-46 caller that passes no tag byte-identical.
    /// Byte-parallel with the Kotlin twin's `uaFontSizeIsKeywordSized`.
    static func uaFontSizeIsKeywordSized(_ tag: String?) -> Bool {
        !emSizedUAFontTags.contains((tag ?? "").lowercased())
    }

    /// The absolute-size keyword ladder (css-fonts-4 §2.5.1 table, Chromium's
    /// 16px-medium row) — the px the converter pre-resolves the keyword to
    /// (FontSizeProperty.kt's AbsoluteSize table), pinned by the B4 test so
    /// the margin basis and the painted size cannot disagree.
    private static func absoluteKeywordPx(_ keyword: String?) -> CGFloat? {
        switch (keyword ?? "").lowercased() {
        case "xx-small":  return 9
        case "x-small":   return 10
        case "small":     return 13
        case "medium":    return 16
        case "large":     return 18
        case "x-large":   return 24
        case "xx-large":  return 32
        case "xxx-large": return 48
        // Unknown keyword: not resolvable, never guessed.
        default:          return nil
        }
    }

    /// The element's OWN computed font-size in px — the em base of the UA
    /// block margin — or NIL when the element carries no own font signal or
    /// its declared size is not statically resolvable.
    ///
    /// Resolution ladder (`last(where:)` mirrors the extractors'
    /// last-declaration-wins fold — FontSizeExtractor's scan and
    /// DynamicValueResolver's pass 0 — so a duplicated FontSize resolves to
    /// the painted value):
    ///  B1 no FontSize, first declared family is the monospace generic, and
    ///     the tag's UA font-size is keyword-derived
    ///     (`uaFontSizeIsKeywordSized`) → 13px via MonospaceUAFontSize
    ///     (Chromium's defaultFixedFontSize). An em-sized heading
    ///     (h1/h2/h3/h5/h6) takes Blink's 13/16 SCALING instead, which this
    ///     module does not model, so it returns nil and keeps the table.
    ///  B2 no FontSize otherwise → nil: NO own signal, the caller keeps the
    ///     16px-root table (the identity contract in the file header).
    ///  B3 a resolved top-level `px` (absolute length, or a
    ///     DynamicValueResolver prebake) > 0 → that px.
    ///  B4 absolute keyword (`{"type":"absolute","keyword":…}`) → the ladder.
    ///  B5 relative keyword (`{"type":"relative","keyword":"larger"|"smaller"}`)
    ///     → `inheritedPx` × / ÷ `relativeSizeStep` (CSS 2.1 §15.7).
    ///  B6 nested length (`{"type":"length","original":{v,u}}`): EM → v ×
    ///     inherited (css-values-4 §6.1.1: em on font-size itself resolves
    ///     against the INHERITED size), REM → v × the 16px root.
    ///  B7 percentage (`{"type":"percentage","value":N}`) → N% × inherited
    ///     (css-fonts-4 §2.5: same base as em).
    ///  B8 anything else (var()/calc()/vw/ex/ch/malformed) → nil (the
    ///     documented honest fallback: the caller keeps the table).
    ///
    /// - Parameter inheritedPx: the parent's computed size —
    ///   `uaDefaultFontSizePx` for a composed root; the renderer's
    ///   inheritance channel for a child.
    /// - Parameter sourceTag: the element's `meta.sourceTag` (`_tag` on the
    ///   wire), read by the B1 gate ONLY (`uaFontSizeIsKeywordSized`). Nil
    ///   keeps the pre-wave-46 behaviour for callers with no tag in hand;
    ///   every rung B3-B8 is tag-independent, because a DECLARED size
    ///   overrides the UA one and there is nothing left for the UA sheet to
    ///   scale.
    public static func ownFontSizePx(_ properties: [IRProperty],
                                     inheritedPx: CGFloat = uaDefaultFontSizePx,
                                     sourceTag: String? = nil) -> CGFloat? {
        // B1 / B2 — no declaration: the quirk (only where the UA size is the
        // keyword one) or no signal at all. An em-sized heading gets nil
        // rather than a wrong 13px basis — see the gate's doc comment.
        guard let data = properties.last(where: {
            $0.type == MonospaceUAFontSize.sizePropertyType
        })?.data else {
            guard uaFontSizeIsKeywordSized(sourceTag) else { return nil }
            return MonospaceUAFontSize.resolvePx(from: properties).map { CGFloat($0) }
        }
        // A non-object payload (e.g. a bare "var(--x)" string) is B8.
        guard case .object(let obj) = data else { return nil }
        // B3 — a resolved px rides at the top level on every absolute or
        // prebaked wire shape; a zero/negative size cannot scale a margin
        // (and falls through to the `original` discriminator, as on Compose).
        if let px = obj["px"]?.doubleValue, px > 0 { return CGFloat(px) }
        guard case .object(let original)? = obj["original"] else { return nil }
        // The ladder's candidate, validated `> 0` once at the end.
        let resolved: CGFloat?
        switch original["type"]?.stringValue {
        // B4 — the absolute keyword ladder.
        case "absolute", "absoluteKeyword":
            resolved = absoluteKeywordPx(original["keyword"]?.stringValue)
        // B5 — one ladder step from the inherited size.
        case "relative":
            switch original["keyword"]?.stringValue?.lowercased() {
            case "larger":  resolved = inheritedPx * relativeSizeStep
            case "smaller": resolved = inheritedPx / relativeSizeStep
            default:        resolved = nil
            }
        // B6 — the nested {v,u} wrapper the converter emits for em/rem.
        case "length":
            guard case .object(let inner)? = original["original"],
                  let v = inner["v"]?.doubleValue else { resolved = nil; break }
            switch inner["u"]?.stringValue?.uppercased() {
            case "EM":  resolved = CGFloat(v) * inheritedPx
            case "REM": resolved = CGFloat(v) * uaDefaultFontSizePx
            // Other relative units have no static base here (B8).
            default:    resolved = nil
            }
        // B7 — percentage of the inherited size.
        case "percentage":
            resolved = original["value"]?.doubleValue.map { CGFloat($0) / 100 * inheritedPx }
        // B8 — expression / unknown shapes: not statically resolvable.
        default:
            resolved = nil
        }
        // A zero/negative product cannot scale a margin either.
        guard let px = resolved, px > 0 else { return nil }
        return px
    }
}
