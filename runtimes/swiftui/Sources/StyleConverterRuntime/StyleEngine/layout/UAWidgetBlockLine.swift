//
//  UAWidgetBlockLine.swift
//  StyleEngine/layout — wave-38 lane N1.
//
//  The BLOCK-CONTEXT line box of a UA form-control replica. Byte-parallel
//  twin of Compose layout/UAWidgetBlockLine.kt (same table, same solve,
//  same unit expectations) — the two pin suites assert identical numbers
//  so the natives can never drift.
//
//  WHY THIS FILE EXISTS
//  --------------------
//  `UAWidgetIntrinsics` + `InlineAtomFlow` already model a RUN of ≥2
//  consecutive inline atoms: the run's adapter builds the §10.8 line box,
//  floors it at the strut, and places each atom against the row baseline.
//  A widget that is the ONLY inline-level box in its block never reaches
//  that path — the renderer block-stacks it — so on both natives the
//  wrapper block advanced by exactly the widget's BORDER-BOX height, with
//  no line box at all.
//
//  Measured on the wave37-final composed captures of css-ui
//  appearance-revert-001.tentative (14 widgets, one per <div>, so each is
//  a lone inline-level box in a block):
//
//    band            ref box top   native box top   drift
//    input-text          17             16           −1
//    textarea            60             58           −2
//    input-button       101             94           −7
//    checkbox           191            178          −13
//    select             257            231          −26
//    progress (ink)     373            340          −33
//
//  i.e. the natives' row pitch equals the widget height on EVERY row
//  while the ref's is the line-box height. Cumulative drift 33px → SSIM
//  0.8222 (iOS) / 0.821 (Android) against a web that scores exactly 1.0.
//
//  THE TABLE
//  ---------
//  Per kind: the line box HEIGHT a lone widget's block advances by, and
//  the TOP LEAD — everything that must appear above the painted border
//  box (line-box leading plus the UA margin-top). Both are ref-probed:
//  solved so the whole appearance-revert-001 chain reproduces the ref
//  band table above EXACTLY (content origin y16; each row top = previous
//  row top + lineHeightPx; each box top = row top + topLeadPx). The
//  per-row verification lives in UAWidgetBlockLineTests / the Compose
//  UAWidgetBlockLineTest twin.
//
//  Most entries fall straight out of the §10.8.1 strut solve the run path
//  already uses (row ascent = max(strutAscent 16, H − descent), row
//  descent = max(strutDescent 4, descent)) with `UAWidgetIntrinsics.spec`
//  descents — textField/buttonLike (1/22), textarea (0/40), color (0/27),
//  listbox (0/70), meter/progress (3/20) are exactly that. Three kinds
//  carry a ref-probed BLOCK value that differs from their run descent,
//  and each divergence is stated rather than smoothed over:
//    • checkbox/radio — Blink's html.css gives them `margin: 3px …`,
//      which the run table models only horizontally (marginStart/End). In
//      block context the 3px margin-TOP is what puts the ref box at
//      191/211, so it rides in topLeadPx (and is cancelled again when the
//      wire already carries the computed margin — see `leadFor`).
//    • range — block topLead 0, not the run descent's 1. The UA
//      `margin: 2px` and Blink's margin-box baseline for the slider
//      cancel to a flush box top; ref ink 168 = box 166 + the plan's 2px
//      thumb inset.
//    • select — block topLead 2 / line box 21, i.e. an absolute ascent of
//      14 where appearance-auto-001 solved 11. That ref pins the select's
//      ascent only RELATIVE to the colour swatch sharing its row, so the
//      two solves are not in contradiction; they are, however, not yet
//      reconciled into one descent. Deliberately kept as a separate block
//      constant so this lane cannot move a passing appearance-auto-001.
//

enum UAWidgetBlockLine {

    /// The extra space this lane inserts around a block-context widget
    /// replica, in CSS px: `topPx` above the painted border box, `bottomPx`
    /// below it. The renderer adds both INSIDE the widget component's box,
    /// so the block advance becomes wireMarginTop + top + H + bottom +
    /// wireMarginBottom = the line box height.
    struct Lead: Equatable {
        let topPx: Double
        let bottomPx: Double
    }

    /// `(lineHeightPx, topLeadPx)` for a lone block-context widget, or nil
    /// for kinds this lane does not own. `.textAnchor` is nil because it is
    /// not a widget at all — a text-only `<a>` already renders through the
    /// normal text path with the block's own line metrics.
    static func lineBox(_ kind: UAWidgetIntrinsics.Kind) -> (lineHeightPx: Double, topLeadPx: Double)? {
        switch kind {
        // Not a widget replica — no UA box to lead (see doc above).
        case .textAnchor: return nil
        // 21px control box, descent 6 → ascent 15 < strut 16 → lead 1,
        // line box 16 + 6 = 22. Ref rows 17/39 (fields) and 101/123/145
        // (buttons) chain at pitch 22.
        case .textField, .buttonLike: return (22.0, 1.0)
        // 36px box, descent 0 → ascent 36 > strut → lead 0, line box
        // 36 + strutDescent 4 = 40. Ref textarea top 60, next row top 100.
        case .textarea: return (40.0, 0.0)
        // Ref ink 168 with the plan's 2px thumb inset → box top 166 = the
        // row top → lead 0; the next row (checkbox) starts at 188 → 22.
        case .range: return (22.0, 0.0)
        // 13px glyph + Blink's 3px UA margin-top → box tops 191 (row 188)
        // and 211 (row 208) → lead 3, pitch 20.
        case .checkbox, .radio: return (20.0, 3.0)
        // 27px box, descent 9 → ascent 18 > strut → lead 0; line box
        // 18 + 9 = 27. Ref colour box top 228 = its row top; select row
        // then opens at 255.
        case .color: return (27.0, 0.0)
        // Ref select box 257 against row top 255 → lead 2; listbox row
        // opens at 276 → line box 21.
        case .select: return (21.0, 2.0)
        // 70px box, descent 7 → ascent 63 > strut → lead 0, line box 70.
        // Ref listbox top 276 = row top; meter row opens at 346.
        case .listbox: return (70.0, 0.0)
        // 16px gauge, descent 3 → ascent 13 < strut 16 → lead 3, line box
        // 20. Ref meter box 349 (ink 353 = box + the plan's 4px inset)
        // against row top 346; progress box 369 against row top 366.
        case .meter, .progress: return (20.0, 3.0)
        }
    }

    /// The full block-context gate + solve for ONE widget component.
    ///
    /// `tag` / `typeAttr` / `multiple` are the wave-20 widget-identity
    /// channel (meta.sourceTag + meta.attrs), resolved through the SAME
    /// `UAWidgetIntrinsics.kind` the run path uses so a widget can never be
    /// one kind for placement and another for leading.
    ///
    /// `hasWireHeight` is the deliberate CONSERVATIVE gate: when the wire
    /// carries a computed `Height` for this element (post-load extraction),
    /// the renderer already pins the component box to it, and padding the
    /// content would be measured against that fixed box — the replica would
    /// be squeezed or clipped rather than led. Those elements keep the
    /// frozen path; the residual (their line-box lead is still missing) is
    /// reported, not silently absorbed.
    ///
    /// `wireMarginTopPx` / `wireMarginBottomPx` are the element's own used
    /// margins as they arrive on the wire (0 when absent — which is what
    /// the renderer then applies). They are SUBTRACTED from the table's
    /// lead because a post-load-extracted checkbox already carries Blink's
    /// `margin: 3px` on the wire: without this the 3px would be counted
    /// twice and css-ui accent-color-visited (currently a byte-exact 1.0 on
    /// both natives) would move 3px down.
    ///
    /// `horizontalWritingMode` is the AXIS gate — false for any inherited
    /// `writing-mode` other than `horizontal-tb`. See the guard below for
    /// the device measurement that put it here.
    ///
    /// Returns nil when no lead applies (not a widget kind, a non-widget
    /// input type, a wire-pinned height, a vertical writing mode, or a
    /// degenerate table entry).
    static func leadFor(tag: String?,
                        typeAttr: String?,
                        multiple: Bool,
                        hasWireHeight: Bool,
                        wireMarginTopPx: Double,
                        wireMarginBottomPx: Double,
                        horizontalWritingMode: Bool = true) -> Lead? {
        // Conservative gate — a wire-pinned box owns its own geometry.
        if hasWireHeight { return nil }
        // AXIS gate (wave-38 lane N1, device-measured). Every constant in
        // `lineBox` is a §10.8 solve read off a HORIZONTAL-TB ref: the line
        // box grows along the block axis, which is the y axis only when
        // `writing-mode` is horizontal-tb. Under vertical-lr/rl (and the
        // sideways-* pair) the block axis is x, so a top/bottom pad is the
        // WRONG AXIS — it displaces the replica instead of leading it.
        //
        // Measured on the iOS w38-n1 device run of the lane's blast radius:
        // without this gate css-writing-modes forms/checkbox-appearance-
        // native-vertical-{lr,rl}-baseline went 0.7795 → 0.7665 and the two
        // radio twins 0.7796 → 0.7630. Both were already failing, so no
        // pass flipped — but "byte-stable-or-better on the blast radius" is
        // the lane's bar, and moving a failing row further from the ref is
        // still a regression. Vertical widgets keep the frozen composition;
        // their own line-box lead is REPORTED as unsolved, not guessed at
        // with a horizontal number.
        if !horizontalWritingMode { return nil }
        // The three input types whose "widget" is deliberately none
        // (appearance-auto-input-non-widget-001): they paint special
        // content, not a UA control box, so the control line box does not
        // describe them. UAWidgetIntrinsics.kind folds them into
        // .textField (the HTML invalid-type default), which would be the
        // wrong 21px box here — veto explicitly instead of inheriting it.
        switch typeAttr?.lowercased() {
        case "hidden", "image", "file": return nil
        default: break
        }
        // Identity → kind → the ref-probed line box for that kind.
        let kind = UAWidgetIntrinsics.kind(tag: tag, typeAttr: typeAttr, multiple: multiple)
        guard let box = lineBox(kind) else { return nil }
        // The painted border-box height is the run table's fixed axis (the
        // replica paints inside exactly that box). A kind without one has
        // no block line box to solve — nil rather than a guessed height.
        guard let heightPx = UAWidgetIntrinsics.spec(kind).fixedHpx else { return nil }
        // Top: the table's lead minus whatever margin-top the renderer is
        // already applying from the wire (never negative — a wire margin
        // larger than the lead means the element's own ascent already
        // exceeds the strut, so no extra space is owed).
        let top = max(0.0, box.topLeadPx - wireMarginTopPx)
        // Bottom: whatever is left of the line box after the wire margins,
        // the top lead and the painted box. Clamped for the same reason.
        let bottom = max(0.0, box.lineHeightPx - wireMarginTopPx - top - heightPx - wireMarginBottomPx)
        // A zero lead is a no-op for the renderer — report it as nil so the
        // frozen composition stays byte-identical on those rows.
        if top == 0.0 && bottom == 0.0 { return nil }
        return Lead(topPx: top, bottomPx: bottom)
    }
}
