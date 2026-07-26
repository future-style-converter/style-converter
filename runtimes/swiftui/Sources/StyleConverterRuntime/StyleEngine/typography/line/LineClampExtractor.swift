//
//  LineClampExtractor.swift
//  StyleEngine/typography/line — Phase 6.
//
//  RC-B6a (wave 19): the LIVE wire ships `line-clamp: 2` as the SEALED
//  object `{"type":"lines","count":2}` (wave18 css-overflow
//  block-ellipsis-001 per-test IR) — NOT as a bare integer. The old
//  extractor fed that object to ValueExtractors.extractInt, which only
//  reads the `value`/`numeric` keys, so `count` was never read,
//  cfg.lines stayed nil and the clamp NEVER engaged (block-ellipsis-001
//  rendered all 3 wrapped lines / 62px where the ref clamps to 2 lines
//  / 41px). Mirrors Compose's TypographyExtractor.extractLineClamp,
//  which decodes the same {type,count} envelope explicitly.
//

import Foundation

enum LineClampProperty { static let name = "LineClamp" }

enum LineClampExtractor {
    static func extract(from properties: [IRProperty]) -> LineClampConfig? {
        var cfg = LineClampConfig()
        var touched = false
        for prop in properties where prop.type == LineClampProperty.name {
            touched = true
            // Keyword `none` → clamp off. Covers the bare string "none"
            // AND the sealed `{"type":"none"}` envelope (extractKeyword
            // reads the `type` discriminator too).
            if ValueExtractors.extractKeyword(prop.data)?.lowercased() == "none" {
                cfg.lines = nil
                continue
            }
            // Sealed `{"type":"lines","count":N}` — the live wire shape
            // (see the header). IRValue.intValue folds JSON ints AND
            // doubles (2.0 → 2), matching Compose's floatOrNull read;
            // a missing/sub-1 count clamps OFF, per the CSS grammar
            // (css-overflow-4 §5: <integer [1,∞]>).
            if case .object(let o) = prop.data,
               o["type"]?.stringValue == "lines" {
                if let n = o["count"]?.intValue, n >= 1 {
                    cfg.lines = n
                } else {
                    cfg.lines = nil
                }
                continue
            }
            // Integer grammar (legacy bare int / {value:N} envelopes).
            // Clamp < 1 → nil (no clamp), matching CSS.
            if let n = ValueExtractors.extractInt(prop.data), n >= 1 {
                cfg.lines = n
            }
        }
        return touched ? cfg : nil
    }
}
