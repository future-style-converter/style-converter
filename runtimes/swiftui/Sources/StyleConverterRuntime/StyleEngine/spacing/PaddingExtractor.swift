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

        // Fidelity wave 2 — LOGICAL WINS OVER PHYSICAL, regardless of
        // declaration order, mirroring the web reference: the web
        // PaddingApplier emits physical sides first and logical last
        // into the React style object, so in the browser's CSSOM a
        // logical longhand always overrides a physical one targeting
        // the same edge (same rule as MarginExtractor / Spacing_C03).
        // Two passes: physical first, then logical overrides. LTR-TB
        // writing-mode assumed; block=top/bottom, inline=left/right.
        for prop in properties {
            switch prop.type {
            case "PaddingTop":    cfg.top    = extractLengthPercentDefault(prop.data); touched = true
            case "PaddingRight":  cfg.right  = extractLengthPercentDefault(prop.data); touched = true
            case "PaddingBottom": cfg.bottom = extractLengthPercentDefault(prop.data); touched = true
            case "PaddingLeft":   cfg.left   = extractLengthPercentDefault(prop.data); touched = true
            default: break // not a physical padding longhand
            }
        }
        // Second pass — logical longhands override the physical edge they
        // MAP to. Wave 47 (lane Z2): the target side comes from the
        // css-writing-modes-4 §6.4 table under VERTICAL writing modes
        // (`padding-block-start` on a vertical-lr box is a LEFT padding);
        // `sides` is nil for every horizontal mode (LogicalSides
        // .verticalOrNil's blast-radius contract), where the ?? defaults
        // reproduce the legacy LTR-TB fold byte-identically. Twin of
        // MarginExtractor's fold.
        let sides = LogicalSides.verticalOrNil(properties)
        // One side-targeted store, shared by all four logical longhands.
        func assign(_ side: PhysicalSide, _ value: LengthValue) {
            switch side {
            case .top:    cfg.top = value
            case .right:  cfg.right = value
            case .bottom: cfg.bottom = value
            case .left:   cfg.left = value
            }
        }
        for prop in properties {
            switch prop.type {
            case "PaddingBlockStart":
                assign(sides?.blockStart ?? .top, extractLengthPercentDefault(prop.data)); touched = true
            case "PaddingInlineEnd":
                assign(sides?.inlineEnd ?? .right, extractLengthPercentDefault(prop.data)); touched = true
            case "PaddingBlockEnd":
                assign(sides?.blockEnd ?? .bottom, extractLengthPercentDefault(prop.data)); touched = true
            case "PaddingInlineStart":
                assign(sides?.inlineStart ?? .left, extractLengthPercentDefault(prop.data)); touched = true
            default: break // not a logical padding longhand
            }
        }

        return touched ? cfg : nil
    }
}
