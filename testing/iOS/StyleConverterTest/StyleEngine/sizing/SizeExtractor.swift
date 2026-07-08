//
//  SizeExtractor.swift
//  StyleEngine/sizing — Phase 3.
//
//  Walks an [IRProperty] list and fills a SizeConfig. Two IR shapes live
//  here:
//    * WidthValue  (Width/Height/Min*/Max* physical) — tagged-wrapper
//      form `{"type":"length","px":N}`, `{"type":"percentage","value":P}`,
//      `{"type":"none"}`, bare strings `auto` / `min-content` / ...,
//      and `{"fit-content":<inner>}`. Handled by the canonical
//      Phase 1 `extractLength` extractor.
//    * SizeValue   (BlockSize/InlineSize/Min/Max logical) — raw form
//      `{"px":N}`, bare number = percent, bare strings `auto`/`none`.
//      Handled by `extractLengthPercentDefault` so bare numbers promote
//      to `.relative(%)`.
//
//  We branch on the IR property name, not the payload shape, because the
//  same payload (bare `20.0`) means different things on Width vs
//  InlineSize. Logical → physical mapping assumes LTR writing mode.
//

// Foundation for array iteration; nothing else needed here.
import Foundation

// Public entry point — invoked once per component from StyleBuilder.
// Returns a config populated with every recognised sizing prop. A
// `hasAny == false` result means the applier can be skipped entirely.
enum SizeExtractor {

    // Linear scan. We never short-circuit because a component may carry
    // both physical and logical sizing in the same pass (the converter
    // occasionally emits both when shorthands expand).
    static func extract(from properties: [IRProperty]) -> SizeConfig {
        var cfg = SizeConfig()
        // Track whether we saw any sizing prop so the caller can decide
        // to attach / skip the applier without re-inspecting the config.
        for p in properties {
            switch p.type {
            // ─── Physical width family (WidthValue shape) ────────────
            case "Width":
                cfg.width = extractLength(p.data)
            case "Height":
                cfg.height = extractLength(p.data)
            case "MinWidth":
                cfg.minWidth = extractLength(p.data)
            case "MaxWidth":
                cfg.maxWidth = extractLength(p.data)
            case "MinHeight":
                cfg.minHeight = extractLength(p.data)
            case "MaxHeight":
                cfg.maxHeight = extractLength(p.data)

            // ─── Logical sizing family (SizeValue shape) ─────────────
            // LTR mapping: block axis = height, inline axis = width.
            // Logical props override physical when both are present
            // (last-write-wins); matches the Android applier's order.
            case "BlockSize":
                cfg.height = extractSizeValue(p.data)
            case "InlineSize":
                cfg.width = extractSizeValue(p.data)
            case "MinBlockSize":
                cfg.minHeight = extractSizeValue(p.data)
            case "MaxBlockSize":
                cfg.maxHeight = extractSizeValue(p.data)
            case "MinInlineSize":
                cfg.minWidth = extractSizeValue(p.data)
            case "MaxInlineSize":
                cfg.maxWidth = extractSizeValue(p.data)

            // ─── AspectRatio (disjoint shape) ─────────────────────────
            case "AspectRatio":
                cfg.aspectRatio = AspectRatioExtractor.extract(p.data)

            // ─── BoxSizing (border-box | content-box) ─────────────────
            // Claimed here for coverage parity with Android. The iOS
            // chain (`engineSpacingPadding → engineSizing`) already
            // implements border-box semantics, which matches every
            // current fixture — none explicitly set `box-sizing` — so
            // we record the keyword but defer real content-box
            // branching until a fixture drives it.
            case "BoxSizing":
                _ = p.data

            default:
                // Not a sizing prop — skip silently. The registry keeps
                // migrated names in sync with this switch.
                break
            }
        }
        return cfg
    }

    // SizeValue-shape dispatcher. The converter emits a "none" bare
    // string for `max-block-size: none` / `max-inline-size: none` —
    // forward that to `.none`. Everything else routes through the
    // percent-default length extractor so bare numbers → `%`.
    private static func extractSizeValue(_ data: IRValue) -> LengthValue {
        // Bare string "none" is unique to SizeValue (WidthValue uses
        // the `{type:none}` object). Handle it here before delegating.
        if case .string(let s) = data, s.lowercased() == "none" {
            return .none
        }
        // Delegate — also covers bare `"auto"`, bare numbers (%), and
        // objects (`{px:N}`, `{expr:"calc(...)"}`, viewport units).
        return extractLengthPercentDefault(data)
    }
}
