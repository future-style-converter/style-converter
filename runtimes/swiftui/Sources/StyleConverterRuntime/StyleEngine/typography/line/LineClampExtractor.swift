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
//  Wave 46 (lane Y1): the `lines` object may ALSO carry the wave-42
//  marker component — `"ellipsis":{"type":"no-ellipsis"}` or
//  `{"type":"string","value":"…"}` (converter LineClampProperty.
//  BlockEllipsis, strictly additive: absent for `4`, `4 ellipsis`).
//  The extractor folds it into `markerSuppressed`; the cap/clip route
//  in StyleEngine/scrolling/LineClampCap consumes it.
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
                // A clamp-less declaration carries no marker either.
                cfg.markerSuppressed = false
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
                // Wave 46 (lane Y1): the marker component rides on the
                // SAME declaration, so it is folded here — last
                // declaration wins for both fields together.
                cfg.markerSuppressed = Self.markerSuppressed(o["ellipsis"])
                continue
            }
            // Integer grammar (legacy bare int / {value:N} envelopes).
            // Clamp < 1 → nil (no clamp), matching CSS.
            if let n = ValueExtractors.extractInt(prop.data), n >= 1 {
                cfg.lines = n
                // The legacy integer grammar has no marker component.
                cfg.markerSuppressed = false
            }
        }
        return touched ? cfg : nil
    }

    /// The `<'block-ellipsis'>` read (css-overflow-4 §5.1): true ONLY for
    /// the two values whose effect is "clamp without a marker":
    ///   • `{"type":"no-ellipsis"}` — the keyword;
    ///   • `{"type":"string","value":""}` — the empty string, which the
    ///     converter keeps verbatim (computed values retain the string)
    ///     but whose rendered marker is nothing (block-ellipsis-024).
    /// nil (the initial `ellipsis`), `auto` and a non-empty string all
    /// draw a marker → false. A malformed payload also reads false so a
    /// wire this reader does not understand never silently removes a
    /// marker the author did not forbid.
    static func markerSuppressed(_ ellipsis: IRValue?) -> Bool {
        // Absent component → the initial value `ellipsis` → marker drawn.
        guard let e = ellipsis, case .object(let o) = e else { return false }
        switch o["type"]?.stringValue {
        // The explicit "never draw one" keyword.
        case "no-ellipsis": return true
        // An author string: only the EMPTY string renders nothing.
        case "string": return (o["value"]?.stringValue ?? "").isEmpty
        // `auto`, unknown discriminators → a marker is drawn.
        default: return false
        }
    }
}
