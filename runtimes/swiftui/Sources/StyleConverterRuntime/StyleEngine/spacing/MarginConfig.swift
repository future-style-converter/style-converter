//
//  MarginConfig.swift
//  StyleEngine/spacing — Phase 2.
//
//  Mirror of PaddingConfig with margin semantics: values may be negative,
//  and any side may be `auto` (which triggers alignment rather than a
//  concrete pixel number). All four physical edges are stored; logical
//  edges resolve to physical at extract time under LTR-TB writing mode.
//

// Foundation for consistency with sibling files.
import Foundation

// Per-edge margin. Uses LengthValue directly so the applier can still
// see `.auto`, negatives, percents, and calc.
struct MarginConfig: Equatable {
    // Zero defaults — a component with no margin props skips the applier.
    var top: LengthValue = .exact(px: 0)
    var right: LengthValue = .exact(px: 0)
    var bottom: LengthValue = .exact(px: 0)
    var left: LengthValue = .exact(px: 0)

    // Convenience check for whether at least one side is non-zero-or-auto.
    // Used by the applier to short-circuit the modifier chain.
    var hasAny: Bool {
        !isNopSide(top) || !isNopSide(right) ||
        !isNopSide(bottom) || !isNopSide(left)
    }

    // True when the side resolves to exact 0 (no-op). `.auto` counts as
    // non-nop since it affects layout even without pixels.
    private func isNopSide(_ v: LengthValue) -> Bool {
        if case .exact(0) = v { return true }
        return false
    }

    // Horizontal centering / push helpers. The CSS rule: both l+r auto →
    // center; only l auto → push-right (trailing); only r auto → push-left
    // (leading). When neither is auto the flag returns nil and the applier
    // skips the frame-based centering.
    var horizontalAutoAlignment: HorizontalAutoAlignment {
        let lAuto = isAuto(left), rAuto = isAuto(right)
        if lAuto && rAuto { return .center }
        if lAuto          { return .pushRight }
        if rAuto          { return .pushLeft }
        return .none
    }

    // Same pattern for vertical `auto` — rare but emitted by the
    // margin-basic fixture (Margin_Auto_Vertical_Block).
    var verticalAutoAlignment: VerticalAutoAlignment {
        let tAuto = isAuto(top), bAuto = isAuto(bottom)
        if tAuto && bAuto { return .center }
        if tAuto          { return .pushBottom }
        if bAuto          { return .pushTop }
        return .none
    }

    // Shared helper.
    private func isAuto(_ v: LengthValue) -> Bool {
        if case .auto = v { return true }
        return false
    }
}

// Horizontal-axis alignment triggered by `margin: auto`.
enum HorizontalAutoAlignment { case none, center, pushLeft, pushRight }
// Vertical-axis counterpart. `pushTop` means "pin content to top of frame".
enum VerticalAutoAlignment   { case none, center, pushTop, pushBottom }
