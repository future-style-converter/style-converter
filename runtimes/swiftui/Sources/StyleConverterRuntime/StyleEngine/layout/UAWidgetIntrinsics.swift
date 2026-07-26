// UAWidgetIntrinsics — the SHARED per-native UA widget geometry table
// (wave-20, agreed cross-lane name). One source of truth for the
// intrinsic sizes / baselines of form-control atoms so lane W3 (this
// file's placement consumers: InlineAtomFlow + InlineAtomBlockLayout)
// and lane W2 (widget replica painting) agree on the SAME boxes. Byte-
// parallel twin of Compose layout/UAWidgetIntrinsics.kt.
//
// Every number is in CSS px (== pt at the capture scale) and was
// measured off the composed-WPT browser ref for css-ui
// appearance-auto-001 (Chrome/Blink UA stylesheet defaults at 16px root
// font, tools/wpt/refs/.../white-black-ink-font-lh/css-ui/
// appearance-auto-001.png) — the provenance comment on each entry cites
// the measured pixel band. Tuning happens HERE only; both the pure pin
// suites and W2's painters read the same constants.
enum UAWidgetIntrinsics {

    // P16 — inter-atom gap: the collapsed whitespace between the source
    // elements is one 16px-default-font space advance (~4.16px in the
    // ref: input-text ends x239, input-search starts x245 minus the
    // 1px borders ≈ 4.2).
    static let atomGapPx: Double = 4.16

    // P17 — text-atom line metrics: the composed-WPT line box is 20 CSS
    // px (16px ref font × 1.25, the wptRefLineBoxPx pin); the baseline
    // sits ~5px above its bottom (half-leading 2 + 16px-font descent
    // ≈3.4 → 5.4, floored to keep row-1's 21px pitch: 15 ascent + 6
    // field descent = 21, ref y17→y38).
    static let textLineBoxPx: Double = 20.0
    static let textDescentPx: Double = 5.0

    // P17b (wave-20 fix 4) — the LINE-BOX STRUT metrics: CSS 2.1 §10.8.1
    // gives every line box in an inline formatting context a zero-width
    // inline box with the block's font metrics ("the strut"), so a row
    // holding only short atoms is still at least one text line tall and
    // its baseline never rises above the text baseline. Derived from the
    // P17 pins (20px line box, 5px descent → 15px ascent). Ref proof:
    // appearance-auto-001's LAST row holds only the 16px-tall progress
    // bar (ascent 11.5), yet its ink sits at y159.5-166.5 — exactly
    // rowTop 152 + strut ascent 15 − progress ascent 11.5 + ink inset 4;
    // without the strut the row would hug the atom 3.5px higher.
    static let strutAscentPx: Double = textLineBoxPx - textDescentPx
    static let strutDescentPx: Double = textDescentPx

    /// The widget families the table sizes (wave-20 widget-identity set).
    enum Kind: Equatable {
        // <a> with only text — measured as text, placed as an atom.
        case textAnchor
        // <button> and <input type=button|submit|reset> — width follows
        // the label text, height/baseline are UA constants.
        case buttonLike
        // <input> text family (text/search/tel/url/email/password/number
        // and the date-time flavours) — fully fixed intrinsic size.
        case textField
        // <input type=range> — the slider.
        case range
        // <input type=checkbox> / <input type=radio> — 13px glyph boxes
        // with UA margins (Blink: 3px 3px 3px 4px).
        case checkbox, radio
        // <input type=color> — the swatch button.
        case color
        // <textarea> — cols=20 default.
        case textarea
        // <select> (menulist) vs <select multiple> (listbox).
        case select, listbox
        // <meter> / <progress> gauge bars.
        case meter, progress
    }

    /// One atom's placement inputs (P18): fixed intrinsic sizes when the
    /// UA defines them (nil → measure the rendered replica), the
    /// baseline descent (distance from the border-box bottom UP to the
    /// alphabetic baseline — the inline-flow alignment key, P17), and UA
    /// margins (only checkbox/radio carry any in the Blink UA sheet).
    struct AtomSpec: Equatable {
        // Fixed border-box width in CSS px, or nil → measured.
        let fixedWpx: Double?
        // Fixed border-box height in CSS px, or nil → measured.
        let fixedHpx: Double?
        // Baseline descent from the box bottom (P17).
        let descentPx: Double
        // UA margin-left / margin-right (margin-box packing, P16).
        let marginStartPx: Double
        let marginEndPx: Double

        // Explicit init so the zero-margin common case stays terse —
        // mirrors the Kotlin data-class defaults byte-for-byte.
        init(fixedWpx: Double?, fixedHpx: Double?, descentPx: Double,
             marginStartPx: Double = 0.0, marginEndPx: Double = 0.0) {
            self.fixedWpx = fixedWpx
            self.fixedHpx = fixedHpx
            self.descentPx = descentPx
            self.marginStartPx = marginStartPx
            self.marginEndPx = marginEndPx
        }
    }

    /// Resolve a widget kind from the wire identity: the meta.sourceTag
    /// plus (when decoded) the meta.attrs `type` / `multiple` values of
    /// the wave-20 widget-identity contract. Unknown/absent input types
    /// fold to the textField default exactly like the HTML parser's
    /// invalid-type fallback (HTML §4.10.5 state=Text).
    static func kind(tag: String?, typeAttr: String?, multiple: Bool) -> Kind {
        switch tag?.lowercased() {
        // Text-only anchors measure as text (InlineAtomFlow.isAtom
        // already guaranteed the only-text shape).
        case "a": return .textAnchor
        // <button> labels drive the width; height is the UA constant.
        case "button": return .buttonLike
        // <input>: the type attribute selects the family.
        case "input":
            switch typeAttr?.lowercased() {
            case "button", "submit", "reset": return .buttonLike
            case "range": return .range
            case "checkbox": return .checkbox
            case "radio": return .radio
            case "color": return .color
            // text/search/… and every unknown type → the text field
            // (HTML §4.10.5's invalid-value default).
            default: return .textField
            }
        case "textarea": return .textarea
        // multiple selects render the 4-row listbox, others the menulist.
        case "select": return multiple ? .listbox : .select
        case "meter": return .meter
        case "progress": return .progress
        // Non-widget tags never reach here through the atom gate; the
        // text-anchor spec is the harmless measured fallback (no silent
        // drop — the caller logs unknown tags via its own tracker).
        default: return .textAnchor
        }
    }

    /// P18 — the geometry table. Provenance: appearance-auto-001 white
    /// ref bands (container content origin x16/y17-ish; sizes inclusive).
    static func spec(_ kind: Kind) -> AtomSpec {
        switch kind {
        // Text atoms: measured glyph run inside the 20px line box; the
        // baseline sits textDescentPx above the line-box bottom.
        case .textAnchor: return AtomSpec(fixedWpx: nil, fixedHpx: nil, descentPx: textDescentPx)
        // Buttons: ref button box y17-37 (h 21), text baseline ~y31-32
        // → descent 6; width hugs the label (measured).
        case .buttonLike: return AtomSpec(fixedWpx: nil, fixedHpx: 21.0, descentPx: 6.0)
        // Text field: ref input-text box x87-239 × y17-37 → 153×21,
        // baseline shared with the button row → descent 6.
        case .textField: return AtomSpec(fixedWpx: 153.0, fixedHpx: 21.0, descentPx: 6.0)
        // Range: Blink's 129px default track at the 21px control height;
        // baseline follows the control row (descent 6).
        case .range: return AtomSpec(fixedWpx: 129.0, fixedHpx: 21.0, descentPx: 6.0)
        // Checkbox/radio: 13×13 glyph, Blink UA margins 3px 3px 3px 4px
        // (horizontal only here — vertical margins fold into descent);
        // baseline ~3px above the glyph bottom.
        case .checkbox, .radio:
            return AtomSpec(fixedWpx: 13.0, fixedHpx: 13.0, descentPx: 3.0,
                            marginStartPx: 4.0, marginEndPx: 3.0)
        // Color swatch: ref x204-253 × y125-151 → 50×27. fix 4: the ref
        // box sits y125-151 against the row-3 baseline y143 (checkbox
        // top 133 + ascent 10) → descent 152−143 = 9 (was 2 — that
        // pinned the swatch 7px high once the listbox descent landed).
        case .color: return AtomSpec(fixedWpx: 50.0, fixedHpx: 27.0, descentPx: 9.0)
        // Textarea: ref x16-198 × y38-73 → ~184×36 (cols=20 default);
        // its baseline is the box bottom (descent 0) — that offset is
        // what pushes the sibling buttons 21px down in the ref row 2.
        case .textarea: return AtomSpec(fixedWpx: 184.0, fixedHpx: 36.0, descentPx: 0.0)
        // Menulist select: label + arrow drive width (measured). fix 4:
        // the ref slab is 19px tall (borders y132..150, probed at the
        // white-black-ink ref x280 column), not the 25 the first cut
        // assumed, and its top solves against the row-3 baseline 143 as
        // ascent 11 → descent 19−11 = 8.
        case .select: return AtomSpec(fixedWpx: nil, fixedHpx: 19.0, descentPx: 8.0)
        // Listbox select: 4 option rows ≈ 70px tall (ref y79.5-149.5).
        // fix 4: §10.8.1 — an inline-block's baseline is its LAST line
        // box's baseline, i.e. the 4th option row's text baseline, NOT
        // the box bottom. Ref-solved: listbox top 79.5≈80 with checkbox
        // top 133 fixes the shared baseline at 143 → descent 7. The old
        // descent 0 raised the baseline to 150 and pushed every short
        // row-3 atom ~7px BELOW its ref band (the Android y120-159 diff
        // concentration) and the progress row 4px late.
        case .listbox: return AtomSpec(fixedWpx: nil, fixedHpx: 70.0, descentPx: 7.0)
        // Meter: Blink 80×16 gauge. fix 4: descent 4.5 — solved from the
        // progress twin below (same Blink gauge baseline); off-canvas in
        // the ref (x≥390) so the progress measurement is the authority.
        case .meter: return AtomSpec(fixedWpx: 80.0, fixedHpx: 16.0, descentPx: 4.5)
        // Progress: Blink 160×16 bar (ref last band x16-175). fix 4:
        // descent 4.5, ref-solved: row-4 top = 152 (row-3 = 63 ascent +
        // 9 descent below y80), row-4 baseline = 152 + strut ascent 15 =
        // 167, and the ref ink band y159.5-166.5 puts the box at
        // y155.5-171.5 → descent = 171.5 − 167 = 4.5 (was 3).
        case .progress: return AtomSpec(fixedWpx: 160.0, fixedHpx: 16.0, descentPx: 4.5)
        }
    }
}
