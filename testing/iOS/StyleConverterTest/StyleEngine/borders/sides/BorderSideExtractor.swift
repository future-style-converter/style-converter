//
//  BorderSideExtractor.swift
//  StyleEngine/borders/sides — Phase 5.
//
//  Walks a property list once and fills an AllBordersConfig. Handles all
//  24 `border-*` side properties: 4 physical sides × {width, color, style}
//  plus 4 logical sides × {width, color, style}. Shorthand longhands
//  (`BorderWidth`, `BorderColor`, `BorderStyle`) fan out to every side
//  that didn't set an explicit value — matches the parser registry in
//  `src/main/kotlin/app/parsing/css/properties/shorthands/borders/sides/`.
//

// Foundation + CoreGraphics (via SwiftUI) for Color/CGFloat.
import SwiftUI

// Single static entry point to keep the call site clean — mirrors
// ColorExtractor / PaddingExtractor.
enum BorderSideExtractor {

    // List of property-type strings this extractor owns. The registry
    // consults this list (via `PropertyRegistry.migrated`) so the legacy
    // StyleBuilder switch skips them.
    static let propertyNames: [String] = [
        // Shorthand longhands — CSS 2.1 §8.5 per-family fallthroughs.
        "BorderWidth", "BorderColor", "BorderStyle",
        // Physical sides.
        "BorderTopWidth", "BorderTopColor", "BorderTopStyle",
        "BorderRightWidth", "BorderRightColor", "BorderRightStyle",
        "BorderBottomWidth", "BorderBottomColor", "BorderBottomStyle",
        "BorderLeftWidth", "BorderLeftColor", "BorderLeftStyle",
        // Logical sides — CSS Logical Properties Level 1.
        "BorderBlockStartWidth", "BorderBlockStartColor", "BorderBlockStartStyle",
        "BorderBlockEndWidth", "BorderBlockEndColor", "BorderBlockEndStyle",
        "BorderInlineStartWidth", "BorderInlineStartColor", "BorderInlineStartStyle",
        "BorderInlineEndWidth", "BorderInlineEndColor", "BorderInlineEndStyle",
    ]

    // Returns nil when no border-* property appears in the list — lets the
    // applier short-circuit without allocating.
    static func extract(from properties: [IRProperty]) -> AllBordersConfig? {
        // Per-side accumulators. Logical names map into the same four
        // buckets since we assume LTR (fixtures confirm this).
        var top = BorderSideConfig()
        var end = BorderSideConfig()
        var bottom = BorderSideConfig()
        var start = BorderSideConfig()
        // Shorthand-derived fallbacks. Applied after the explicit pass so
        // per-side overrides win — mirrors the Android extractor.
        var sharedWidth: CGFloat? = nil
        var sharedColor: Color? = nil
        var sharedStyle: BorderStyleValue? = nil
        // Touch flag — returns nil when no border-* entry was seen so the
        // applier skips dispatch entirely.
        var touched = false

        for prop in properties {
            switch prop.type {
            // ── Shorthand longhands (apply-to-all-sides fallback) ──
            case "BorderWidth":
                // Width keywords (`thin|medium|thick`) are pre-resolved to
                // `{ "px": N }` by the CSS parser before reaching iOS.
                sharedWidth = ValueExtractors.extractPx(prop.data); touched = true
            case "BorderColor":
                sharedColor = ValueExtractors.extractColor(prop.data); touched = true
            case "BorderStyle":
                sharedStyle = extractBorderStyle(prop.data); touched = true

            // ── Top (physical) + Block-start (logical, LTR = top) ──
            case "BorderTopWidth", "BorderBlockStartWidth":
                top.width = ValueExtractors.extractPx(prop.data); touched = true
            case "BorderTopColor", "BorderBlockStartColor":
                top.color = ValueExtractors.extractColor(prop.data); touched = true
            case "BorderTopStyle", "BorderBlockStartStyle":
                top.style = extractBorderStyle(prop.data); touched = true

            // ── Right (physical) + Inline-end (logical, LTR = right) ──
            case "BorderRightWidth", "BorderInlineEndWidth":
                end.width = ValueExtractors.extractPx(prop.data); touched = true
            case "BorderRightColor", "BorderInlineEndColor":
                end.color = ValueExtractors.extractColor(prop.data); touched = true
            case "BorderRightStyle", "BorderInlineEndStyle":
                end.style = extractBorderStyle(prop.data); touched = true

            // ── Bottom (physical) + Block-end (logical, LTR = bottom) ──
            case "BorderBottomWidth", "BorderBlockEndWidth":
                bottom.width = ValueExtractors.extractPx(prop.data); touched = true
            case "BorderBottomColor", "BorderBlockEndColor":
                bottom.color = ValueExtractors.extractColor(prop.data); touched = true
            case "BorderBottomStyle", "BorderBlockEndStyle":
                bottom.style = extractBorderStyle(prop.data); touched = true

            // ── Left (physical) + Inline-start (logical, LTR = left) ──
            case "BorderLeftWidth", "BorderInlineStartWidth":
                start.width = ValueExtractors.extractPx(prop.data); touched = true
            case "BorderLeftColor", "BorderInlineStartColor":
                start.color = ValueExtractors.extractColor(prop.data); touched = true
            case "BorderLeftStyle", "BorderInlineStartStyle":
                start.style = extractBorderStyle(prop.data); touched = true

            default:
                // Not ours.
                break
            }
        }

        if !touched { return nil }

        // Shorthand fan-out — fill in any side that didn't set an explicit
        // value of its own. Mirrors the Android extractor's semantics so
        // the cross-platform coverage comparison is byte-for-byte aligned.
        if sharedWidth != nil || sharedColor != nil || sharedStyle != nil {
            if top.width == nil    { top.width    = sharedWidth }
            if top.color == nil    { top.color    = sharedColor }
            if top.style == nil    { top.style    = sharedStyle }
            if end.width == nil    { end.width    = sharedWidth }
            if end.color == nil    { end.color    = sharedColor }
            if end.style == nil    { end.style    = sharedStyle }
            if bottom.width == nil { bottom.width = sharedWidth }
            if bottom.color == nil { bottom.color = sharedColor }
            if bottom.style == nil { bottom.style = sharedStyle }
            if start.width == nil  { start.width  = sharedWidth }
            if start.color == nil  { start.color  = sharedColor }
            if start.style == nil  { start.style  = sharedStyle }
        }

        return AllBordersConfig(top: top, end: end, bottom: bottom, start: start)
    }
}
