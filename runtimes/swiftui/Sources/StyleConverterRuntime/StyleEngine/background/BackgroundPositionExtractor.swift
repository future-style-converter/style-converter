//
//  BackgroundPositionExtractor.swift
//  StyleEngine/background — Phase 4.
//
//  Reads BackgroundPositionX and BackgroundPositionY into a single
//  BackgroundPositionConfig. IR axis shapes (longhand X/Y):
//    {type:"keyword", value:"LEFT"|"RIGHT"|"TOP"|"BOTTOM"|"CENTER"}
//    {type:"length",  px: N}
//    {type:"percentage", percentage: N}     // N is 0..100
//
//  Wave 9 — the `background` SHORTHAND emits a DIFFERENT wire: a tagged
//  PositionValue LIST (converter irmodels/properties/background/
//  BackgroundPositionProperty.kt), which no runtime consumed until now
//  (`background: red url(…) right bottom` silently lost its position).
//  Pinned against live converter output (JDK 21, wave-9):
//    [{"type":"two-value","x":{"type":"right"},"y":{"type":"bottom"}}]
//    [{"type":"center"}]
//    [{"type":"two-value","x":{"type":"length","px":10.0},
//                         "y":{"type":"percentage","percentage":25.0}}]
//  EdgeValue is keyword-AS-TYPE ({"type":"left"} etc.), and single
//  keywords are pre-normalized by the parser to two-value with the other
//  axis center (`left` → x:left, y:center). Entry 0 only for now —
//  per-layer position lists ride with the multi-layer follow-up.
//

import Foundation

enum BackgroundPositionProperty {
    // BackgroundPosition shorthand is pre-expanded upstream. We consume
    // the two physical longhands plus the two logical-axis variants
    // (block / inline) so the registry marks all four as migrated and
    // they participate in the dispatch loop below.
    static let names: [String] = [
        "BackgroundPosition",
        "BackgroundPositionX",
        "BackgroundPositionY",
        "BackgroundPositionBlock",
        "BackgroundPositionInline",
    ]
}

enum BackgroundPositionExtractor {

    // Extract both axes in one pass. Nil return when neither is present.
    static func extract(from properties: [IRProperty]) -> BackgroundPositionConfig? {
        var cfg = BackgroundPositionConfig()
        var touched = false
        for prop in properties {
            switch prop.type {
            case "BackgroundPositionX":
                cfg.x = parseAxis(prop.data); touched = true
            case "BackgroundPositionY":
                cfg.y = parseAxis(prop.data); touched = true
            // Logical-axis variants. In LTR horizontal writing-mode (the
            // only mode wired here today) `block` maps onto Y and
            // `inline` onto X. RTL / vertical writing modes will need a
            // swapped fold once the writing-mode applier hooks in.
            case "BackgroundPositionBlock":
                cfg.y = parseAxis(prop.data); touched = true
            case "BackgroundPositionInline":
                cfg.x = parseAxis(prop.data); touched = true
            case "BackgroundPosition":
                // Wave 9 — the `background` shorthand's tagged
                // PositionValue LIST (see file header for the pinned
                // wire). Entry 0 only: per-layer lists arrive with the
                // multi-layer background follow-up (the geometry engine
                // takes one position pair today, BackgroundImageApplier
                // "Position is one pair" note).
                if case .array(let layers) = prop.data {
                    // Empty list → nothing to place; unparseable entry
                    // (e.g. the parser's "raw" passthrough) → skip
                    // honestly rather than fake a position.
                    if let first = layers.first,
                       let axes = parseShorthandEntry(first) {
                        cfg.x = axes.x; cfg.y = axes.y; touched = true
                    }
                } else if let parsed = parseAxis(prop.data) {
                    // Legacy defensive path: an un-expanded axis-shaped
                    // object — treat as X-only, exactly as before wave 9.
                    cfg.x = parsed; touched = true
                }
            default:
                break
            }
        }
        return touched ? cfg : nil
    }

    // Wave 9 — one tagged PositionValue entry from the SHORTHAND list.
    // Returns both axes (nil axis = keep whatever a longhand set), or nil
    // for shapes we must not guess at ("raw", unknown tags). Internal
    // (not private) so the XCTest pins each wire variant directly.
    static func parseShorthandEntry(_ v: IRValue)
        -> (x: BackgroundAxisPosition?, y: BackgroundAxisPosition?)? {
        // Every PositionValue serializes as a type-tagged object.
        guard case .object(let o) = v else { return nil }
        switch o["type"]?.stringValue?.lowercased() {
        case "center":
            // Bare `center` → 50% on both axes (css-backgrounds-3 §3.6);
            // reuse the keyword lane so the applier's UnitPoint math is
            // shared with the longhand path.
            return (.keyword("CENTER"), .keyword("CENTER"))
        case "two-value":
            // The normalized pair — each axis is an EdgeValue. Both must
            // parse: a half-understood position would misplace the tile.
            guard let xv = o["x"], let yv = o["y"],
                  let x = parseEdge(xv), let y = parseEdge(yv)
            else { return nil }
            return (x, y)
        case "keyword":
            // Model variant for a lone keyword ({"keyword": "left"}) —
            // the live parser pre-normalizes to two-value, but the model
            // declares it so we map it: the named edge takes its axis,
            // the other axis defaults to center (§3.6 one-value syntax).
            switch (o["keyword"]?.stringValue ?? "").lowercased() {
            case "left":   return (.keyword("LEFT"),   .keyword("CENTER"))
            case "right":  return (.keyword("RIGHT"),  .keyword("CENTER"))
            case "top":    return (.keyword("CENTER"), .keyword("TOP"))
            case "bottom": return (.keyword("CENTER"), .keyword("BOTTOM"))
            case "center": return (.keyword("CENTER"), .keyword("CENTER"))
            // Unknown keyword: refuse rather than misplace (no silent
            // fake — the geometry engine logs unknown keywords too).
            default:       return nil
            }
        default:
            // "raw" (unparsed author text) and future tags: no guessing.
            return nil
        }
    }

    // Wave 9 — one EdgeValue from a two-value shorthand entry. The wire
    // is keyword-AS-TYPE: {"type":"left"}… plus the length ({px}) and
    // percentage ({percentage}) carriers, all pinned in the file header.
    // Internal so the XCTest pins each variant directly.
    static func parseEdge(_ v: IRValue) -> BackgroundAxisPosition? {
        // EdgeValue serializes as a type-tagged object too.
        guard case .object(let o) = v else { return nil }
        switch o["type"]?.stringValue?.lowercased() {
        // Keyword-as-type edges → the same normalized keyword strings the
        // longhand lane produces, so BackgroundImageGeometry's keyword →
        // fraction map (LEFT/ RIGHT / TOP / BOTTOM / CENTER) is shared.
        case "left":   return .keyword("LEFT")
        case "right":  return .keyword("RIGHT")
        case "top":    return .keyword("TOP")
        case "bottom": return .keyword("BOTTOM")
        case "center": return .keyword("CENTER")
        // Absolute length: converter pre-resolves to px ({"px": N}).
        case "length":
            guard let px = o["px"]?.doubleValue else { return nil }
            return .px(px)
        // Percentage 0..100 — applier divides by 100 for UnitPoint math.
        case "percentage":
            guard let pct = o["percentage"]?.doubleValue else { return nil }
            return .percent(pct)
        default:
            // Unknown edge shape → refuse the whole entry upstream.
            return nil
        }
    }

    // Parse a single axis value. Unknown shapes return nil so we don't
    // misrender a "fake" position.
    private static func parseAxis(_ v: IRValue) -> BackgroundAxisPosition? {
        guard case .object(let o) = v else { return nil }
        let type = o["type"]?.stringValue?.lowercased()
        switch type {
        case "keyword":
            // Normalise to upper-case for switch friendliness at apply time.
            let raw = o["value"]?.stringValue ?? ""
            return .keyword(raw.uppercased())
        case "length":
            guard let px = o["px"]?.doubleValue else { return nil }
            return .px(px)
        case "percentage":
            guard let pct = o["percentage"]?.doubleValue else { return nil }
            return .percent(pct)
        default:
            return nil
        }
    }
}
