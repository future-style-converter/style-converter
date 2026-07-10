//
//  MarginExtractor.swift
//  StyleEngine/spacing — Phase 2.
//
//  Walks an [IRProperty] list and fills a MarginConfig covering the eight
//  physical + logical margin longhands the fixtures emit. Same
//  `extractLengthPercentDefault` trick as PaddingExtractor so bare-number
//  percent IR values resolve correctly.
//

// Foundation for Array iteration only.
import Foundation

// Canonical list of margin property-types this extractor owns. Keeps
// PropertyRegistry / StyleBuilder in sync via a single source of truth.
enum MarginProperty {
    static let names: [String] = [
        "MarginTop", "MarginRight", "MarginBottom", "MarginLeft",
        "MarginBlockStart", "MarginBlockEnd",
        "MarginInlineStart", "MarginInlineEnd",
    ]
}

enum MarginExtractor {

    // Public entry. Returns nil when no margin longhand appears, letting
    // the applier skip its modifier entirely.
    static func extract(from properties: [IRProperty]) -> MarginConfig? {
        // Build up a fresh config — each side starts at exact 0.
        var cfg = MarginConfig()
        // Tracks whether any margin property was observed.
        var touched = false

        // Fidelity wave 2 — LOGICAL WINS OVER PHYSICAL, regardless of
        // declaration order. The web reference (MarginApplier.ts) emits
        // physical sides first and logical sides last into the React
        // style object, so in the browser's CSSOM the logical longhand
        // always overrides a physical one targeting the same edge
        // (Spacing_C03: margin-inline-start 40px beat margin-left 4px
        // on web while iOS's single-pass last-write-wins picked 4px →
        // 36px x-shift). Two passes reproduce the web precedence:
        // physical first, logical second. LTR-TB writing-mode:
        // block=top/bottom, inline=left/right.
        for prop in properties {
            switch prop.type {
            case "MarginTop":    cfg.top    = extractLengthPercentDefault(prop.data); touched = true
            case "MarginRight":  cfg.right  = extractLengthPercentDefault(prop.data); touched = true
            case "MarginBottom": cfg.bottom = extractLengthPercentDefault(prop.data); touched = true
            case "MarginLeft":   cfg.left   = extractLengthPercentDefault(prop.data); touched = true
            default: break
            }
        }
        // Second pass — logical longhands override the physical edge.
        for prop in properties {
            switch prop.type {
            case "MarginBlockStart":  cfg.top    = extractLengthPercentDefault(prop.data); touched = true
            case "MarginInlineEnd":   cfg.right  = extractLengthPercentDefault(prop.data); touched = true
            case "MarginBlockEnd":    cfg.bottom = extractLengthPercentDefault(prop.data); touched = true
            case "MarginInlineStart": cfg.left   = extractLengthPercentDefault(prop.data); touched = true
            default: break
            }
        }

        return touched ? cfg : nil
    }
}
