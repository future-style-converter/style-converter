//
//  GapDecorationsExtractor.swift
//  StyleEngine/columns — wave 24, lane GAPS-I.
//
//  IRProperty[] → GapDecorationsConfig, on the wave-24 WIRE CONTRACT
//  (spelled out in GapDecorationsConfig.swift's header). Value shapes
//  are pinned against a LIVE conversion of the css-gaps corpus
//  (tools/titan/runs/wave23-final/sections/css-gaps/per-test-ir/):
//
//    ColumnRuleColor  {"srgb":{"r":1,"g":0,"b":0},"original":"red"}
//    ColumnRuleStyle  "SOLID"                    (bare keyword string)
//    ColumnRuleWidth  {"type":"length","px":10}  (typed length object)
//
//  The Row* twins are byte-identical by contract, and the three new
//  keyword/length properties follow the same two shapes:
//    *RuleBreak   "NORMAL" | "SPANNING_ITEM" | "INTERSECTION"
//    *RuleInset   {"type":"length","px":-2}      (negatives allowed)
//    RuleOverlap  "ROW_OVER_COLUMN" | "COLUMN_OVER_ROW"
//
//  NOTE on ColumnRuleWidth: ValueExtractors.extractKeyword would answer
//  "length" for the typed-length object (it falls back to the `type`
//  discriminator), so widths and insets MUST route through extractPx —
//  which reads the object's `px` member directly. That asymmetry is the
//  reason this file dispatches per property name instead of bulk-
//  keywording everything like ColumnsExtractor does.
//

import SwiftUI

/// The IR type names this family owns. ColumnRuleColor/Style/Width are
/// deliberately ABSENT: they are already claimed by ColumnsProperty for
/// the multicol rule and re-claiming them in PropertyRegistry would
/// double-register the same string. This extractor still READS them —
/// extraction is not exclusive, registration is.
enum GapDecorationsProperty {
    /// The 6 names wave 24 adds to the catalogue.
    static let names: [String] = [
        "RowRuleColor", "RowRuleStyle", "RowRuleWidth",
        "ColumnRuleBreak", "RowRuleBreak",
        "ColumnRuleInset", "RowRuleInset",
        "RuleOverlap",
    ]
    /// Set form for the registry union.
    static var set: Set<String> { Set(names) }
}

enum GapDecorationsExtractor {

    /// Every type name this extractor consumes, including the three
    /// multicol-owned column-rule longhands it shares.
    private static let consumed: Set<String> = GapDecorationsProperty.set
        .union(["ColumnRuleColor", "ColumnRuleStyle", "ColumnRuleWidth"])

    /// Build the config, or nil when the component declares nothing from
    /// this family (the overwhelmingly common case — a nil return keeps
    /// the flex draw hook completely inert).
    static func extract(from properties: [IRProperty]) -> GapDecorationsConfig? {
        var cfg = GapDecorationsConfig()
        // Single pass over the property list; only our names are read.
        for p in properties where consumed.contains(p.type) {
            // Any hit means the family appeared on the wire.
            cfg.touched = true
            switch p.type {
            // ── colours ────────────────────────────────────────────────
            // Same IRColor object both families; extractColor answers nil
            // for currentColor/var(), which the painter treats as
            // "inherit the text colour" (the CSS initial).
            case "ColumnRuleColor": cfg.column.color = ValueExtractors.extractColor(p.data)
            case "RowRuleColor":    cfg.row.color = ValueExtractors.extractColor(p.data)
            // ── line styles ────────────────────────────────────────────
            // Shared BorderStyleValue decoder (the CSS grammar is the
            // border <line-style> production verbatim).
            case "ColumnRuleStyle": cfg.column.style = extractBorderStyle(p.data)
            case "RowRuleStyle":    cfg.row.style = extractBorderStyle(p.data)
            // ── widths ─────────────────────────────────────────────────
            // Typed length object → px. Negative widths are meaningless
            // per css-multicol-1 §4.4 (<length [0,∞]>) so we clamp at 0,
            // which `paints` then reads as "no ink".
            case "ColumnRuleWidth": cfg.column.widthPx = nonNegative(p.data)
            case "RowRuleWidth":    cfg.row.widthPx = nonNegative(p.data)
            // ── breaks ─────────────────────────────────────────────────
            // Unknown keyword = a converter/runtime version skew; keep
            // the CSS initial rather than silently painting something
            // else, and say so in the tracker (no silent fallthrough).
            case "ColumnRuleBreak": cfg.column.breakMode = breakMode(p.data, on: p.type)
            case "RowRuleBreak":    cfg.row.breakMode = breakMode(p.data, on: p.type)
            // ── insets ─────────────────────────────────────────────────
            // Signed px — NEGATIVE IS LEGAL and extends the rule past the
            // line edge (flex-gap-decorations-011). No clamping here.
            case "ColumnRuleInset": cfg.column.insetPx = inset(p.data, on: p.type)
            case "RowRuleInset":    cfg.row.insetPx = inset(p.data, on: p.type)
            // ── crossing order ─────────────────────────────────────────
            case "RuleOverlap":
                // Same unknown-keyword policy as the breaks.
                if let raw = ValueExtractors.extractKeyword(p.data),
                   let v = GapRuleOverlap(rawValue: ValueExtractors.normalize(raw)) {
                    cfg.overlap = v
                } else {
                    _ = PropertyTracker.logOnce(
                        key: "gapdec.overlap.unknown",
                        message: "RuleOverlap: unknown keyword — kept ROW_OVER_COLUMN")
                }
            // Unreachable: `consumed` is exactly the case list above.
            // Recorded rather than ignored so a future name added to the
            // set without a case here surfaces instead of vanishing.
            default:
                _ = PropertyTracker.logOnce(
                    key: "gapdec.nodecoder." + p.type,
                    message: "gap-decorations name \(p.type) has no decoder")
            }
        }
        // Nothing from the family on this component → no config at all.
        return cfg.touched ? cfg : nil
    }

    /// `<line-width>` reader: the css-backgrounds-3 §4.3 keyword ladder
    /// OR a length, refusing negatives (CSS `<length [0,∞]>`).
    ///
    /// The keyword arm is NOT theoretical — the converter really emits
    /// it. Running `row-rule-width: thin` through
    /// `:converter convert --from css --to ir` yields
    /// `RowRuleWidth = {"type":"keyword","value":"THIN"}` (see
    /// RowRuleWidthProperty.RuleWidth.Keyword), and
    /// `ValueExtractors.extractPx` answers nil for that object because it
    /// has no `px` member — which used to make the whole family go dark.
    /// Chromium's used values (verified: `getComputedStyle` on a bare
    /// element reports `row-rule-width: 3px` for the `medium` initial)
    /// are 1 / 3 / 5, the same ladder the Compose twin's
    /// GapDecorationExtractor.width uses.
    private static func nonNegative(_ v: IRValue?) -> CGFloat? {
        // Keyword arm first: extractPx cannot see through the object.
        if let kw = ValueExtractors.extractKeyword(v) {
            switch ValueExtractors.normalize(kw) {
            case "THIN":   return 1
            case "MEDIUM": return GapRuleSpec.mediumWidthPx
            case "THICK":  return 5
            // `LENGTH` is the discriminator of the typed-length object —
            // fall through to extractPx, which reads its `px` member.
            default:       break
            }
        }
        // Nil stays nil — "unset" resolves to the `medium` initial via
        // GapRuleSpec.effectiveWidthPx, which is NOT the same as 0.
        guard let px = ValueExtractors.extractPx(v) else { return nil }
        // A negative width is out of range; 0 is the spec's clamp target.
        return max(0, px)
    }

    /// `<gap-rule-inset>` as a LENGTH, negatives allowed. Percentages
    /// (`-100%` / `-25%` / `50%` all appear in wpt/css/css-gaps) and the
    /// `overlap-join` ident are outside the wave-24 wire contract, so the
    /// reader leaves them unresolved and extractPx answers nil. Report
    /// them rather than silently treating them as 0 — CLAUDE.md's
    /// no-silent-fallthrough rule, and the Compose twin logs the same
    /// case (GapDecorationExtractor.inset → markUnhandled).
    private static func inset(_ v: IRValue?, on name: String) -> CGFloat {
        // A resolved length is the whole of the supported contract.
        if let px = ValueExtractors.extractPx(v) { return px }
        // Anything else: 0, but on the record.
        _ = PropertyTracker.logOnce(
            key: "gapdec.inset.nonlength." + name,
            message: "\(name): non-length inset (percentage/overlap-join) — used 0")
        return 0
    }

    /// Keyword → GapRuleBreak with the CSS initial as the safe default.
    private static func breakMode(_ v: IRValue?, on name: String) -> GapRuleBreak {
        // normalize() uppercases and maps "-" → "_", so a converter that
        // ever emitted `spanning-item` verbatim still lands correctly.
        if let raw = ValueExtractors.extractKeyword(v),
           let m = GapRuleBreak(rawValue: ValueExtractors.normalize(raw)) {
            return m
        }
        // Unknown/absent keyword: keep the CSS initial `normal` and log
        // it. NOTE the CSS keyword `none` reaches here as nothing at all
        // — the converter rejects it into GenericProperty — so this
        // branch is also the current (wrong, but visible) home of
        // `rule-break: none`.
        _ = PropertyTracker.logOnce(
            key: "gapdec.break.unknown." + name,
            message: "\(name): unknown keyword — kept NORMAL (CSS initial)")
        return .normal
    }
}
