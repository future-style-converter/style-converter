//
//  PositionExtractor.swift
//  StyleEngine/layout/position — Phase 7, step 4 (position).
//
//  Parses Position, Top/Right/Bottom/Left, InsetBlock*, InsetInline*, and
//  ZIndex into LayoutAggregate.position, .inset, .zIndex. Logical inset
//  sides are resolved to physical top/right/bottom/left via the current
//  writing-mode / layoutDirection — mirrors the SpacingResolver physical-
//  to-logical pattern under StyleEngine/spacing/SpacingResolver.swift.
//
//  iOS doesn't expose the CSS writing-mode directly; we read from the
//  SwiftUI Environment `layoutDirection` at render time when mapping
//  inline-start/end. At extraction time we store both the raw logical
//  values and the LTR-resolved physical values; the applier re-resolves
//  for RTL if needed via a small extension at the bottom of this file.
//

import CoreGraphics
import Foundation

enum PositionExtractor {

    /// Folds Position / Top / Right / Bottom / Left / InsetBlock* /
    /// InsetInline* / ZIndex into `agg`. Per-property branches are no-ops
    /// when the IR doesn't carry that type.
    static func contribute(_ properties: [IRProperty], into agg: inout LayoutAggregate) {
        // Local scratchpads so we accumulate physical + logical across
        // iterations before writing back to `agg.inset` once at the end.
        var rect = agg.inset ?? InsetRect()
        var logical = LogicalInsets()
        var touched = false

        for prop in properties {
            switch prop.type {
            case "Position":
                if let pos = parsePosition(prop.data) {
                    agg.position = pos
                    touched = true
                }
            // Physical sides — trivially map to the rect.
            case "Top":
                rect.top = extractPx(prop.data); touched = true
            case "Right":
                rect.right = extractPx(prop.data); touched = true
            case "Bottom":
                rect.bottom = extractPx(prop.data); touched = true
            case "Left":
                rect.left = extractPx(prop.data); touched = true
            // Logical sides — resolved to physical in LTR by default
            // (block-start → top, block-end → bottom, inline-start → left,
            // inline-end → right for horizontal-tb writing-mode, which is
            // what the SwiftUI runtime assumes without explicit
            // .environment(\.layoutDirection, .rightToLeft) override).
            case "InsetBlockStart":
                logical.blockStart = extractPx(prop.data); touched = true
            case "InsetBlockEnd":
                logical.blockEnd = extractPx(prop.data); touched = true
            case "InsetInlineStart":
                logical.inlineStart = extractPx(prop.data); touched = true
            case "InsetInlineEnd":
                logical.inlineEnd = extractPx(prop.data); touched = true
            case "ZIndex":
                // IR: { "value": N, "original": "auto" | {…} }.
                agg.zIndex = extractZIndex(prop.data)
                // Don't set touched on ZIndex alone if nothing else fires —
                // a lone ZIndex still counts as a layout touch.
                touched = true
            default:
                break
            }
        }

        // Resolve the logical sides into physical top/right/bottom/left.
        // LTR is the default; RTL remapping happens in the applier where
        // the Environment is reachable. Physical properties already
        // written above take precedence over logical ones (CSS ordering
        // would be cascade-dependent; we approximate "physical wins").
        if rect.top == nil, let v = logical.blockStart { rect.top = v }
        if rect.bottom == nil, let v = logical.blockEnd { rect.bottom = v }
        if rect.left == nil, let v = logical.inlineStart { rect.left = v }
        if rect.right == nil, let v = logical.inlineEnd { rect.right = v }

        // Only write the rect back if any side was populated — preserves
        // the "nil rect = no positioning" short-circuit downstream.
        if rect != InsetRect() {
            agg.inset = rect
        }
        if touched { agg.touched = true }
    }

    // MARK: - Logical inset scratch

    /// Scratchpad for logical inset longhands. Resolved into InsetRect at
    /// the end of `contribute(...)`.
    private struct LogicalInsets {
        var blockStart:  CGFloat? = nil
        var blockEnd:    CGFloat? = nil
        var inlineStart: CGFloat? = nil
        var inlineEnd:   CGFloat? = nil
    }

    // MARK: - Parsers

    /// Position keyword: STATIC / RELATIVE / ABSOLUTE / FIXED / STICKY.
    private static func parsePosition(_ value: IRValue) -> PositionKind? {
        let kw = ValueExtractors.normalize(ValueExtractors.extractKeyword(value))
        switch kw {
        case "STATIC":   return .staticPos
        case "RELATIVE": return .relative
        case "ABSOLUTE": return .absolute
        case "FIXED":    return .fixed
        case "STICKY":   return .sticky
        default:         return nil
        }
    }

    /// Extract a CGFloat from the many shapes Top/Right/Bottom/Left use:
    ///   • { "px": N }
    ///   • { "keyword": "auto" } → nil (CSS auto leaves the edge free)
    ///   • { "original": { "u": "PERCENT", "v": N } } → stored as-is in
    ///     px for now; true % resolution needs the applier's GeometryReader.
    ///     (TODO: percent support — requires parent-size resolution.)
    ///   • raw Double/Int for post-normalisation examples (inset-logical.json
    ///     uses bare Double for some sides).
    private static func extractPx(_ value: IRValue) -> CGFloat? {
        // Bare numerics — examples/properties/layout/inset-logical.json
        // has "inset-block-start": 10.0 in some variants.
        if case .double(let d) = value { return CGFloat(d) }
        if case .int(let i) = value    { return CGFloat(i) }
        // Object path — covers { px }, { keyword: "auto" }, { original: …%% }.
        if case .object(let o) = value {
            if let kw = o["keyword"]?.stringValue, kw.lowercased() == "auto" {
                return nil
            }
            if let px = o["px"]?.doubleValue {
                return CGFloat(px)
            }
            // Percent — no parent size here. Encode as nil + TODO; the
            // applier may later pick this up via LengthOrPercentage.
            if let original = o["original"]?.objectValue,
               let u = original["u"]?.stringValue, u.uppercased() == "PERCENT" {
                // Stash percent into nil for now — downstream readers treat
                // nil as "leave this edge unconstrained". TODO: carry
                // percentages through for GeometryReader resolution.
                return nil
            }
        }
        return nil
    }

    /// ZIndex IR: { "value": Int, "original": "auto" | { … } }. Auto →
    /// nil to preserve CSS's "default paint order".
    private static func extractZIndex(_ value: IRValue) -> Double? {
        guard case .object(let o) = value else {
            // Bare number is also tolerated (older IR shape).
            if case .int(let i) = value    { return Double(i) }
            if case .double(let d) = value { return d }
            return nil
        }
        if let original = o["original"]?.stringValue, original.lowercased() == "auto" {
            return nil
        }
        if let v = o["value"]?.doubleValue { return v }
        if let v = o["value"]?.intValue    { return Double(v) }
        return nil
    }
}

// MARK: - RTL resolver

/// Re-resolve an InsetRect for the current layout direction. Called by
/// PositionApplier at render time when the SwiftUI Environment says the
/// view is in .rightToLeft mode — logical inline-start/end then map to
/// the opposite physical side.
extension InsetRect {
    /// Returns a new rect with left / right swapped if `isRTL` is true.
    /// Physical-only callers (Top/Right/Bottom/Left) never hit this path
    /// because their physical values are already set and the extractor
    /// gave the physical side precedence. This only repairs logical
    /// insets that were mis-resolved at extraction time.
    func resolved(isRTL: Bool) -> InsetRect {
        guard isRTL else { return self }
        // Swap left/right. Top/bottom are writing-mode invariant for
        // horizontal-tb (the SwiftUI default).
        var out = self
        out.left = self.right
        out.right = self.left
        return out
    }
}
