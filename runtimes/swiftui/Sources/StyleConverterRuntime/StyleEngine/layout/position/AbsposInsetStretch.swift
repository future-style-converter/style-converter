//
//  AbsposInsetStretch.swift
//  StyleEngine/layout/position — wave 18 (RC2, lane 1).
//
//  Abspos inset-stretch sizing, css-position-3 §3.5: an absolutely
//  positioned box whose axis has BOTH opposing insets non-auto and NO
//  explicit size on that axis is sized to the inset-modified containing
//  block (cb − inset1 − inset2). Both natives measured the abspos child
//  at its own ideal size (the wave-8 overlay contract), under which an
//  empty inset-stretched div collapsed to 0×0 — css-sizing
//  abspos-003/004's green square was entirely absent on iOS. After the
//  stretch, a declared aspect-ratio resolves with the INLINE axis owning
//  the ratio (css-sizing-4 §5 interaction with §3.5: the ref renders
//  abspos-003's all-0-inset 1:1 box as 100×100 — inline stretch 100,
//  block DERIVED from the ratio — never 100×500 from the block stretch).
//
//  The resolver is PURE MATH with a signature mirrored verbatim by the
//  Compose twin (layout/position/AbsposInsetStretch.kt) so cross-native
//  probes can diff the two rule tables directly; the same pin table lives
//  in AbsposInsetStretchTest.kt / AbsposInsetStretchTests.swift.
//

import Foundation

enum AbsposInsetStretch {

    /// The resolved injection for one box: px sizes to force onto the
    /// still-auto axes (nil = leave the axis alone). Never overrides an
    /// author-declared size.
    struct Resolved: Equatable {
        let widthPx: Double?
        let heightPx: Double?
    }

    /// The no-op result — identity guard for every non-stretch box.
    private static let none = Resolved(widthPx: nil, heightPx: nil)

    /// The pin table (mirrored on Compose byte-for-byte):
    ///  - S1 (stretch, css-position-3 §3.5.3): an axis is stretchable iff
    ///    it has NO author size, BOTH opposing insets are known px, and
    ///    the containing-block axis is known → candidate =
    ///    max(0, cb − start − end). Percent/auto/unknown insets and
    ///    unknown cb axes conservatively disable the stretch (nothing
    ///    honest to resolve against).
    ///  - S2 (no ratio): each stretchable axis is injected as-is.
    ///  - S3 (ratio present, css-sizing-4 §5): the inline (width) axis
    ///    wins — if width is determined (explicit px or stretch) and
    ///    height is NOT explicit, height := width / ratio (this is what
    ///    overrides abspos-003's 500px block stretch to 100). Otherwise,
    ///    if height is determined and width is fully auto, width :=
    ///    height × ratio (abspos-004: block stretch 50, ratio 2 → 100).
    ///    An explicit author size on an axis is never overridden.
    ///  - S4 (identity guard): if NEITHER axis stretched, return `none` —
    ///    explicit-size + ratio boxes (abspos-001/002) keep the existing
    ///    SizeApplier aspect-ratio path untouched, and every committed
    ///    baseline render is byte-identical.
    ///  - S5 (wave 31, css-tables-3 §"Abspos tables"): a TABLE box's
    ///    available space "can never exceed the available space of the
    ///    containing block" (the assert text of css-tables/
    ///    absolute-tables-008…011). The inset-modified containing block is
    ///    NOT that bound: a NEGATIVE start inset makes cb − start − end
    ///    LARGER than cb, and a table — unlike a block — does not stretch
    ///    into it. Measured on absolute-tables-009 (cb 100×100, `left:
    ///    -100; right: 0`): S1 hands 100 − (−100) − 0 = 200, and both
    ///    natives painted a 200×100 green band where the Chromium ref
    ///    paints 100×100 (frozen captures, wave30-final/sections/
    ///    css-tables — iOS 0.9503, Android 0.9398 FAIL). Clamping the
    ///    candidate to the containing-block extent reproduces the ref.
    ///    Applies to TABLE boxes ONLY (`isTable`) so every non-table
    ///    abspos stretch — the whole css-position / css-sizing baseline —
    ///    stays byte-identical; a block box legitimately stretches past
    ///    its containing block under a negative inset (css-position-3
    ///    §3.5.3 has no such clamp).
    ///
    /// All parameters are px; `ratio` is CSS width/height (> 0), nil when
    /// absent or auto-only. `isTable` is the css-display-3 table-ish
    /// classification of THIS box (see [isTableBox]) and defaults to
    /// false so every pre-wave-31 call site keeps S1 verbatim.
    static func resolve(
        cbW: Double?, cbH: Double?,
        left: Double?, right: Double?, top: Double?, bottom: Double?,
        explicitW: Double?, explicitH: Double?,
        hasExplicitW: Bool, hasExplicitH: Bool,
        ratio: Double?,
        isTable: Bool = false
    ) -> Resolved {
        // S1 — per-axis stretch candidates. max(0, …): a box whose insets
        // exceed the containing block clamps to zero, never negative
        // (css-position-3 §3.5.3's over-constrained floor).
        // S5 folds in as a per-axis ceiling: for a table the candidate may
        // never exceed the containing block's own extent (css-tables-3).
        func clampToCb(_ candidate: Double, _ cb: Double) -> Double {
            isTable ? min(candidate, cb) : candidate
        }
        let stretchW: Double? = (!hasExplicitW && left != nil && right != nil && cbW != nil)
            ? clampToCb(max(0, cbW! - left! - right!), cbW!) : nil
        let stretchH: Double? = (!hasExplicitH && top != nil && bottom != nil && cbH != nil)
            ? clampToCb(max(0, cbH! - top! - bottom!), cbH!) : nil
        // S4 — identity guard: no stretch anywhere → nothing to inject.
        if stretchW == nil && stretchH == nil { return none }
        // S2 — start from the plain stretch result.
        var outW = stretchW
        var outH = stretchH
        // S3 — aspect-ratio resolution with inline-axis priority.
        if let ratio, ratio > 0 {
            // The determined value per axis: author px first, else stretch.
            let wKnown = explicitW ?? stretchW
            let hKnown = explicitH ?? stretchH
            if let wKnown, !hasExplicitH {
                // Inline determined → block derives from the ratio, even
                // over a block stretch (the abspos-003 100×100 pin).
                outH = wKnown / ratio
            } else if let hKnown, !hasExplicitW, wKnown == nil {
                // Block determined, inline fully auto → inline derives
                // (the abspos-004 50×2 → 100 pin).
                outW = hKnown * ratio
            }
        }
        return Resolved(widthPx: outW, heightPx: outH)
    }

    /// Strict px read of the four inset sides for the STRETCH math only
    /// (pin S1's honesty rule, wave-18 skeptic fix — twin of Compose
    /// AbsposInsetStretch.strictSidePx): the typed {"px":N} wire is the
    /// only shape whose meaning is unambiguously absolute px. The LIVE
    /// converter emits a PERCENT inset as a BARE number (`left: 10%` →
    /// `"data": 10.0`), which the permissive LayoutExtractor lane reads
    /// as px for the offset applier (a pre-existing, documented
    /// approximation). Sizing against that mis-read would bake the wrong
    /// basis into the box frame, so a present-but-non-{px} side resolves
    /// to nil here — the axis conservatively never stretches (exactly
    /// what S1 promises for percent insets). Keyword `auto` (bare or
    /// {"keyword":"auto"}) counts as ABSENT and falls through to the
    /// logical longhand, mirroring the extractor's physical-over-logical
    /// precedence (last declaration wins per type, like the fold).
    static func strictInsets(from properties: [IRProperty]) -> InsetRect? {
        // One side: .none = absent/auto; .some(nil) = present but not
        // honest px (percent bare number, calc, …) → disables the axis.
        func read(_ type: String) -> Double?? {
            guard let data = properties.last(where: { $0.type == type })?.data
            else { return .none }
            // Keyword auto — bare string or wrapped — IS the initial
            // value (css-position-3 §3.5: the side does not anchor).
            if let s = data.stringValue, s.lowercased() == "auto" { return .none }
            if case .object(let o) = data,
               let kw = o["keyword"]?.stringValue, kw.lowercased() == "auto" {
                return .none
            }
            // Only the frozen typed-length px shape is trusted for math.
            if case .object(let o) = data, let px = o["px"]?.doubleValue {
                return .some(px)
            }
            return .some(nil)
        }
        func side(_ physical: String, _ logical: String) -> CGFloat? {
            let v = read(physical) ?? read(logical) ?? nil
            return v.map { CGFloat($0) }
        }
        var rect = InsetRect()
        rect.left = side("Left", "InsetInlineStart")
        rect.right = side("Right", "InsetInlineEnd")
        rect.top = side("Top", "InsetBlockStart")
        rect.bottom = side("Bottom", "InsetBlockEnd")
        // nil when no side survived — resolveFor treats it like no insets.
        return (rect.left != nil || rect.right != nil
            || rect.top != nil || rect.bottom != nil) ? rect : nil
    }

    /// Is this box a TABLE box for S5's purposes (css-display-3 §2:
    /// `table` and `inline-table` both generate a table wrapper box, and
    /// css-tables-3's abspos available-space rule is written against the
    /// table box, not the caption/row/cell internals)?
    ///
    /// Two channels, in css-cascade order:
    ///  1. A DECLARED `Display` always wins. Read from the raw wire
    ///     because the runtime's DisplayKeyword enum predates the table
    ///     lane and has no table member; the serialized keyword is the
    ///     honest source (the converter emits SCREAMING_SNAKE —
    ///     `"TABLE"` / `"INLINE_TABLE"`). Last declaration wins, matching
    ///     every other reader in this file. A declared non-table display
    ///     makes this NOT a table even on a `<table>` element
    ///     (css-display-3 §2).
    ///  2. With NO declared display, the originating tag's UA default
    ///     decides — `<table>` is `display: table` per the HTML UA
    ///     stylesheet, and the converter does NOT serialize UA defaults.
    ///     This channel is load-bearing, not belt-and-braces: the live
    ///     absolute-tables-008…011 IRs carry the table ONLY as
    ///     `meta.sourceTag: "table"` with no Display property at all
    ///     (absolute-tables-016 is the one that declares `display:
    ///     table` in author CSS), so S5 would never fire on the very
    ///     tests it was measured against. `tag` is IRComponent's decoded
    ///     `meta.sourceTag`.
    ///
    /// Anything else is NOT a table — S5 then never fires and S1 is
    /// byte-identical.
    ///
    /// Twin: AbsposInsetStretch.isTableBox(properties, tag) on Compose.
    static func isTableBox(from properties: [IRProperty], tag: String? = nil) -> Bool {
        if let kw = properties.last(where: { $0.type == "Display" })?.data.stringValue {
            let upper = kw.uppercased()
            return upper == "TABLE" || upper == "INLINE_TABLE"
        }
        // UA default. Only `<table>` maps to `display: table`; no HTML
        // element defaults to `inline-table`.
        return tag?.lowercased() == "table"
    }

    /// iOS-side style fold: apply the resolver to a built ComponentStyle's
    /// size config, reading the insets from [strictInsets] (px-honest
    /// sides only — see its doc; FixedHoist's hasAnyInset keeps the
    /// permissive decoder because ANY inset anchors, even a percent one)
    /// and the containing block from the
    /// environment channels the caller passes (positioned-children set
    /// the §3.1 padding-box basis; the hoist overlay sets the canvas).
    /// Returns the injected axes; the caller writes them onto
    /// `size.width/height` ONLY where nil (never overriding the author).
    /// Pure over the config — pinned in AbsposInsetStretchTests.
    static func resolveFor(size: SizeConfig,
                           inset: InsetRect?,
                           cbW: Double?, cbH: Double?,
                           isTable: Bool = false) -> Resolved {
        // Author-size classification per axis: any non-auto LengthValue
        // counts as explicit; exact px carries a value for the ratio step;
        // non-px explicit (min-content/…) blocks both stretch and
        // derivation on that axis (conservative — matches Compose).
        func classify(_ v: LengthValue?) -> (has: Bool, px: Double?) {
            switch v {
            // Absent, `auto`, or unparsed wire = the axis is auto
            // (css-sizing-3 §5: auto IS the initial value).
            case nil, .auto, .unknown: return (false, nil)
            // Exact px: explicit AND usable by the ratio step.
            case .exact(let px): return (true, px)
            // Anything else (intrinsic keywords, unresolved calc/%…):
            // explicit but value-less here.
            default: return (true, nil)
            }
        }
        let w = classify(size.width)
        let h = classify(size.height)
        // Ratio via the shared aspect-ratio primitive (0 = auto-only).
        let ratio = (size.aspectRatio?.ratio).flatMap { $0 > 0 ? Double($0) : nil }
        return resolve(
            cbW: cbW, cbH: cbH,
            left: (inset?.left).map(Double.init),
            right: (inset?.right).map(Double.init),
            top: (inset?.top).map(Double.init),
            bottom: (inset?.bottom).map(Double.init),
            explicitW: w.px, explicitH: h.px,
            hasExplicitW: w.has, hasExplicitH: h.has,
            ratio: ratio,
            // S5 — the css-tables-3 available-space ceiling applies to
            // table boxes only; every other box keeps S1 verbatim.
            isTable: isTable)
    }
}
