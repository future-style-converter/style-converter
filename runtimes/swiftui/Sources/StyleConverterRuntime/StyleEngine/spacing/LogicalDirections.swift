//
//  LogicalDirections.swift
//  StyleEngine/spacing — wave 47 (lane Z2).
//
//  The css-writing-modes-4 §6.4 abstract-to-physical side mapping, extracted
//  PURE so Margin/PaddingExtractor resolve logical sides correctly under
//  VERTICAL writing modes and XCTest can pin the whole table (the Compose
//  twin is spacing/LogicalDirections.kt — same table, same names).
//
//  Before this file both extractors hard-coded the horizontal-tb mapping
//  (blockStart→top, blockEnd→bottom, inline→left/right), which transposed
//  every logical margin/padding under `writing-mode: vertical-rl/lr` — the
//  css-break background-image-001/002 wall: `margin-block-end: 20px` on a
//  vertical-rl box is a LEFT margin (block-end points left), not a bottom one.
//

// Foundation only — the table is pure data over the shared enums.
import Foundation

/// The four physical sides a logical side can land on.
enum PhysicalSide: Equatable {
    case top, right, bottom, left
}

/// One writing-mode+direction's complete logical→physical assignment
/// (css-writing-modes-4 §6.4 "Abstract-to-Physical Mappings" table).
struct LogicalSides: Equatable {
    let blockStart: PhysicalSide
    let blockEnd: PhysicalSide
    let inlineStart: PhysicalSide
    let inlineEnd: PhysicalSide

    /// The §6.4 table. `vertical-rl`: block flows right→left so block-start
    /// is the RIGHT edge; with ltr the inline axis runs top→bottom so
    /// inline-start is TOP. `vertical-lr` mirrors the block axis only.
    /// `sideways-rl` lays boxes exactly like vertical-rl (§4 — only glyph
    /// orientation differs); `sideways-lr` runs its inline axis bottom→top
    /// under ltr, inverting the inline sides relative to vertical-lr. rtl
    /// flips the inline sides of whichever row applies.
    static func of(_ mode: WritingModeValue, rtl: Bool) -> LogicalSides {
        switch mode {
        case .horizontalTb:
            return LogicalSides(
                blockStart: .top, blockEnd: .bottom,
                inlineStart: rtl ? .right : .left,
                inlineEnd: rtl ? .left : .right)
        case .verticalRl, .sidewaysRl:
            return LogicalSides(
                blockStart: .right, blockEnd: .left,
                inlineStart: rtl ? .bottom : .top,
                inlineEnd: rtl ? .top : .bottom)
        case .verticalLr:
            return LogicalSides(
                blockStart: .left, blockEnd: .right,
                inlineStart: rtl ? .bottom : .top,
                inlineEnd: rtl ? .top : .bottom)
        case .sidewaysLr:
            // The one ltr mode whose inline-start is the BOTTOM edge (§4.1).
            return LogicalSides(
                blockStart: .left, blockEnd: .right,
                inlineStart: rtl ? .top : .bottom,
                inlineEnd: rtl ? .bottom : .top)
        }
    }

    /// The mapping for a component's merged property list, or nil for every
    /// HORIZONTAL writing mode.
    ///
    /// Nil (not the horizontal row) on horizontal modes is deliberate
    /// blast-radius control: the extractors' legacy two-pass fold — which
    /// ignores the `Direction` property entirely — stays the authority for
    /// horizontal content, so every existing fixture and WPT capture
    /// (including rtl ones the legacy fold renders "ltr-blind") is
    /// byte-identical by construction. Only VERTICAL/SIDEWAYS modes, where
    /// the legacy mapping is simply transposed, take the table.
    /// `writing-mode` and `direction` both inherit (css-writing-modes-4
    /// §3.1, CSS2 §9.10) and ride InheritedText's channel, so an ancestor's
    /// declaration reaches this read.
    static func verticalOrNil(_ properties: [IRProperty]) -> LogicalSides? {
        // The one shared decoder — the same read ColumnsApplier's vertical
        // gate uses, so spacing and multicol can never disagree on the mode.
        guard let wm = WritingModeExtractor.extract(from: properties),
              wm.isVertical else { return nil }
        // `direction: rtl` flips the inline sides (§6.4's rtl column). The
        // wire keyword is the SHOUTY enum ("RTL"), inherited like the mode.
        let rtl = properties.last(where: { $0.type == "Direction" })
            .flatMap { ValueExtractors.extractKeyword($0.data) }?
            .uppercased() == "RTL"
        return of(wm.mode, rtl: rtl)
    }
}
