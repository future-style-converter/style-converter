//
//  PaddingExtractor.swift
//  StyleEngine/spacing — Phase 2.
//
//  Walks an [IRProperty] list and fills a PaddingConfig with every
//  physical+logical longhand the fixtures emit. Uses `extractLengthPercentDefault`
//  so that bare-number IR payloads (the percentage encoding) resolve
//  correctly, while object/string payloads fall through to the canonical
//  Phase 1 `extractLength`.
//

// Foundation for Array enumeration only.
import Foundation

// Property-type strings we own. Consumed by PropertyRegistry so the
// renderer knows to skip these in the legacy StyleBuilder switch.
enum PaddingProperty {
    // Keep the list in one place so the registry and the extractor never
    // drift. Physical + logical longhands — no shorthand; shorthand is
    // pre-expanded to four longhands by the Kotlin converter.
    static let names: [String] = [
        "PaddingTop", "PaddingRight", "PaddingBottom", "PaddingLeft",
        "PaddingBlockStart", "PaddingBlockEnd",
        "PaddingInlineStart", "PaddingInlineEnd",
    ]
}

// Single-pass extractor. Returns `nil` when no padding property was
// present — the applier treats that as a no-op. Always safe to call.
enum PaddingExtractor {

    // Public entry point — called from StyleBuilder.build.
    static func extract(from properties: [IRProperty]) -> PaddingConfig? {
        // Start from all-zero and flip sides to the LengthValue we find.
        var cfg = PaddingConfig()
        // Tracks whether any relevant property was observed at all. If
        // not we return nil so the caller can skip the PaddingApplier.
        var touched = false

        // Linear scan — property count per component is O(tens), so no
        // need for a dict lookup. Order of appearance is not significant:
        // logical longhands already resolve to physical at convert time.
        for prop in properties {
            // Resolve each property-type to the right side. We assume LTR
            // writing-mode; block=top/bottom, inline=left/right.
            switch prop.type {
            case "PaddingTop", "PaddingBlockStart":
                cfg.top = extractLengthPercentDefault(prop.data); touched = true
            case "PaddingRight", "PaddingInlineEnd":
                cfg.right = extractLengthPercentDefault(prop.data); touched = true
            case "PaddingBottom", "PaddingBlockEnd":
                cfg.bottom = extractLengthPercentDefault(prop.data); touched = true
            case "PaddingLeft", "PaddingInlineStart":
                cfg.left = extractLengthPercentDefault(prop.data); touched = true
            default:
                // Not a padding longhand — skip silently.
                break
            }
        }

        return touched ? cfg : nil
    }
}
