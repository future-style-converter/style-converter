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

        // Linear scan. LTR-TB writing-mode: block=top/bottom, inline=left/right.
        for prop in properties {
            switch prop.type {
            case "MarginTop", "MarginBlockStart":
                cfg.top = extractLengthPercentDefault(prop.data); touched = true
            case "MarginRight", "MarginInlineEnd":
                cfg.right = extractLengthPercentDefault(prop.data); touched = true
            case "MarginBottom", "MarginBlockEnd":
                cfg.bottom = extractLengthPercentDefault(prop.data); touched = true
            case "MarginLeft", "MarginInlineStart":
                cfg.left = extractLengthPercentDefault(prop.data); touched = true
            default:
                break
            }
        }

        return touched ? cfg : nil
    }
}
