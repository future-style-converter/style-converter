//
//  ColumnsExtractor.swift
//  StyleEngine/columns — Phase 10.
//

import Foundation

enum ColumnsProperty {
    /// 6 entries — ColumnGap deliberately omitted (owned by GapProperty).
    static let names: [String] = [
        "ColumnCount", "ColumnWidth", "ColumnFill", "ColumnSpan",
        "ColumnRuleStyle", "ColumnRuleWidth", "ColumnRuleColor",
    ]
    static var set: Set<String> { Set(names) }
}

enum ColumnsExtractor {
    static func extract(from properties: [IRProperty]) -> ColumnsConfig? {
        var cfg = ColumnsConfig()
        let owned = ColumnsProperty.set
        for p in properties where owned.contains(p.type) {
            cfg.touched = true
            if let kw = ValueExtractors.extractKeyword(p.data) {
                cfg.rawByType[p.type] = kw
            } else {
                cfg.rawByType[p.type] = String(describing: p.data)
            }
            // Fidelity wave 3 — typed column-count. Wire shape (see
            // converter ColumnCountSerializer): `auto` → the string
            // "auto"/"AUTO"; an integer count → a bare JSON number
            // ({"type":"ColumnCount","data":3.0}). Only counts ≥ 1 are
            // valid per css-multicol-1 §3.1 (<integer [1,∞]>).
            if p.type == "ColumnCount", let n = p.data.doubleValue, n >= 1 {
                cfg.count = Int(n)
            }
            // Wave-9 regression fix — typed `column-width` (css-multicol-1
            // §3.2: `auto | <length [0,∞]>`). Wire shape (converter
            // ColumnWidthSerializer): `auto` → the string "auto"; a length
            // → the IRLength object `{"px": N, "original"?: {...}}`. Route
            // through the shared Phase-1 length extractor so every wrapped
            // form decodes identically to Width; only a strictly positive
            // resolved-px value is a usable used-value basis (0 would fit
            // infinite columns — degenerate per the §3 pseudo-algorithm).
            if p.type == "ColumnWidth" {
                switch extractLength(p.data) {
                // Absolute length pre-resolved to px by the converter.
                case .exact(let px) where px > 0:
                    cfg.widthPx = px
                // Font-relative shipped WITH a resolved px fallback —
                // prefer the concrete pixels (same policy as SizeApplier).
                case .relative(_, _, .some(let px)) where px > 0:
                    cfg.widthPx = px
                // auto / percent-without-basis / calc / unknown: no
                // definite basis — stays nil (= behaves as `auto`); the
                // raw string is already retained in rawByType above.
                default:
                    break
                }
            }
        }
        // Wave-43 lane V6 — css-overflow-4 §3 `continue: discard`. The
        // "Continue" IR property is REGISTERED by the regions no-op tree
        // (css-regions also defines `continue`), so it is deliberately NOT
        // in ColumnsProperty.names — this extractor only READS it, exactly
        // like Compose's MultiColumnExtractor handles "Continue" without
        // listing it in MULTI_COLUMN_PROPERTIES. Wire shape (converter
        // ContinueProperty): the keyword string "DISCARD"/"AUTO"/"OVERFLOW";
        // only DISCARD engages the discard semantics. A separate whole-list
        // scan (not the `owned` loop above) keeps the read out of the
        // `touched` accounting — `continue` alone must not fabricate a
        // columns config (css-multicol-1 §2: only count/width establish one).
        //
        // Wave-43 lane G3 — this scan is LAST-write-wins, not any-wins.
        // `properties` arrives in declaration order, so a later duplicate
        // must override an earlier one (css-cascade-5 §6.4.4: order of
        // appearance is the final tiebreak). The wave-42 shape was
        // `properties.contains { … == "DISCARD" }`, which let ANY discard
        // declaration win: the wire [Continue DISCARD, Continue AUTO]
        // extracted true here but false on Compose, whose `when` branch
        // reassigns `continueDiscard` per declaration (MultiColumnExtractor
        // lines 40-41) and therefore already folds last-write-wins.
        // `properties.last { … }` is this codebase's cascade idiom —
        // LineHeightNormal.isDeclaredNormal([IRProperty]) takes the same
        // shape for exactly this reason — and restores the byte-for-byte
        // parity with Compose that ColumnsConfig.continueDiscard claims.
        cfg.continueDiscard = ValueExtractors.extractKeyword(
            // The LAST declaration of this IR type wins; nil when the
            // property is absent, which decodes to nil and stays false.
            properties.last { $0.type == "Continue" }?.data
        )?.uppercased() == "DISCARD"
        return cfg.touched ? cfg : nil
    }
}
