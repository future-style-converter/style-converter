//
//  GapDecorationsConfig.swift
//  StyleEngine/columns — wave 24, lane GAPS-I (css-gaps-1 gap decorations).
//
//  Typed value struct for the gap-decorations family. The IR property
//  names are the wave-24 WIRE CONTRACT the converter lane emits:
//    ColumnRuleColor / ColumnRuleStyle / ColumnRuleWidth   (already IR)
//    RowRuleColor    / RowRuleStyle    / RowRuleWidth      (new, byte-
//                                                           identical
//                                                           shapes)
//    ColumnRuleBreak / RowRuleBreak    NORMAL|SPANNING_ITEM|INTERSECTION
//    ColumnRuleInset / RowRuleInset    length px objects, negatives OK
//    RuleOverlap                       ROW_OVER_COLUMN|COLUMN_OVER_ROW
//
//  All of it lives in the columns/ category (NOT a new category) because
//  the row-* longhands are the cross-axis twins of the existing
//  column-rule-* multicol longhands — same value grammar, same folder.
//
//  Nothing here paints on its own: GapDecorationSegments turns a config
//  plus measured item frames into rects, and GapDecorationsApplier
//  strokes them. Keeping the data inert makes the whole family pinnable
//  without constructing a view.
//

import SwiftUI

/// `<gap-rule-break>` — where a gap rule is allowed to stop.
/// css-gaps-1 "Gap Rule Breaks". The wire spells these UPPERCASE.
///
/// GRAMMAR, measured rather than assumed. Chrome answers
/// `CSS.supports("column-rule-break", v)` true for `none` / `normal` /
/// `intersection` and FALSE for `spanning-item`, and reports the initial
/// computed value as `normal`;
/// wpt/css/css-gaps/parsing/rule-break-valid.html asserts the same three
/// keywords. So `none` and `normal` are two DISTINCT values, not two
/// spellings of one (an earlier comment here claimed otherwise), and
/// `normal` — not `spanning-item` — is the initial.
///
/// CONSEQUENCE for this enum: `.normal` and `.spanningItem` must render
/// identically in flex. `normal` breaks only at items that span the gap,
/// and css-flexbox-1 §5.2 has no spanning items, so there is nothing to
/// break at. GapDecorationSegments implements exactly that; the Compose
/// twin states the same equivalence.
///
/// STILL UNMAPPED: CSS `none` (44 occurrences under tools/wpt/css/
/// css-gaps/, mostly grid/multicol). The converter's
/// ColumnRuleBreakPropertyParser accepts only normal/spanning-item/
/// intersection, so `none` lands in GenericProperty `_unmapped` and
/// never reaches this file — reported to the converter lane, not
/// silently absorbed here.
enum GapRuleBreak: String, Equatable {
    /// CSS INITIAL value. Broken only where an item spans the gap —
    /// which never happens in flex, so this is the continuous-run mode
    /// pinned by flex-gap-decorations-001/002/003 (none of which
    /// declares `rule-break`, i.e. all three compute to `normal`).
    case normal = "NORMAL"
    /// Not a real CSS keyword any more (Chrome rejects it); kept only so
    /// a converter that still emits the token decodes instead of
    /// falling back. Behaviourally identical to `.normal`.
    case spanningItem = "SPANNING_ITEM"
    /// Broken at every crossing gap as well (flex-gap-decorations-009:
    /// the row rule is cut out over BOTH neighbouring lines' column
    /// gaps, leaving 2–52 / 62–102 / 122–172 in border-box coordinates).
    case intersection = "INTERSECTION"
}

/// `rule-overlap` — which family paints last at a crossing.
/// css-gaps-1 "Gap Rule Overlap". Initial value is row-over-column
/// (flex-gap-decorations-003 paints the blue row band OVER the red
/// column rules; 012 sets column-over-row and inverts exactly that).
enum GapRuleOverlap: String, Equatable {
    /// INITIAL — row rules paint after (above) column rules.
    case rowOverColumn = "ROW_OVER_COLUMN"
    /// Column rules paint after (above) row rules.
    case columnOverRow = "COLUMN_OVER_ROW"
}

/// One family's resolved rule: the `column-rule-*` set or the
/// `row-rule-*` set. Both families share this shape because their CSS
/// grammars are identical (css-gaps-1 defines row-rule-* as the
/// cross-axis twin of the css-multicol-1 column-rule-* longhands).
struct GapRuleSpec: Equatable {
    /// `*-rule-color`. Nil = unset; the painter falls back to the
    /// inherited text colour, mirroring the CSS `currentColor` initial.
    var color: Color? = nil
    /// `*-rule-style`. Nil = unset, which per CSS behaves as `none`
    /// (a rule with no line style paints nothing at all).
    var style: BorderStyleValue? = nil
    /// `*-rule-width` in resolved px. Nil = unset, which is NOT "no
    /// rule": see `effectiveWidthPx`.
    var widthPx: CGFloat? = nil
    /// `*-rule-break`. Defaults to the CSS initial, which Chrome reports
    /// as `normal` (NOT `spanning-item` — that keyword is not supported).
    var breakMode: GapRuleBreak = .normal
    /// `*-rule-inset` in px, applied to BOTH ends of every rule segment.
    /// Positive shortens, negative EXTENDS (flex-gap-decorations-011:
    /// column-rule-inset:-2px grows each 50px line segment to 54px,
    /// spilling 2px into the row gap at each end).
    var insetPx: CGFloat = 0

    /// CSS `medium` as Chromium resolves `<line-width>` — verified by
    /// reading `getComputedStyle(el).rowRuleWidth` on a bare element:
    /// "3px". Same constant the Compose twin uses
    /// (GapRuleSpec.MEDIUM_WIDTH_PX).
    static let mediumWidthPx: CGFloat = 3

    /// The width actually used to paint. `*-rule-width`'s INITIAL value
    /// is `medium`, so a family that declares a style but no width is a
    /// 3px rule, not an absent one — `row-rule-style: solid;
    /// row-rule-color: blue` paints in Chrome and on Compose
    /// (effectiveWidthPx), and used to paint nothing here.
    var effectiveWidthPx: CGFloat { widthPx ?? Self.mediumWidthPx }

    /// True when this family actually puts ink on the canvas.
    /// css-gaps-1 inherits css-multicol-1's rule: a `none`/`hidden`
    /// style or a zero width paints nothing, exactly like a border side.
    var paints: Bool {
        // No style, or an explicitly invisible one → nothing to paint.
        // NOTE this is still the gate that keeps the committed corpus
        // dark: `*-rule-style` defaults to nil (= CSS `none`), so a
        // container must declare a style before any of this runs.
        guard let s = style, s != .none, s != .hidden else { return false }
        // Zero (or a clamped-negative) declared width has no area.
        return effectiveWidthPx > 0
    }
}

/// Both families plus the crossing order — the whole gap-decorations
/// state of one container.
struct GapDecorationsConfig: Equatable {
    /// The `column-rule-*` family (paints in COLUMN gaps — the gaps
    /// along the inline axis).
    var column = GapRuleSpec()
    /// The `row-rule-*` family (paints in ROW gaps — block axis).
    var row = GapRuleSpec()
    /// `rule-overlap`, CSS initial row-over-column.
    var overlap: GapRuleOverlap = .rowOverColumn
    /// True when ANY gap-decorations property appeared on the wire —
    /// used only for diagnostics; painting is gated on `isActive`.
    var touched: Bool = false

    /// The paint gate. FALSE for every component in the committed
    /// baseline corpus (no fixture carries a *-rule-* property that
    /// resolves to ink), which is what makes wiring this family into
    /// the flex container ungated-safe: the draw hook allocates nothing
    /// and changes no pixel unless a container really declares a rule.
    var isActive: Bool { column.paints || row.paints }
}
