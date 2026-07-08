//
//  GridExtractor.swift
//  StyleEngine/layout/grid — Phase 7, step 3 (grid).
//
//  Parses every grid-* IR property into LayoutAggregate.gridTemplate*,
//  gridAuto*, gridArea/gridColumn/gridRow, gridAutoFlow, justifyItems,
//  justifySelf. Mirrors the Kotlin parser output at
//  src/main/kotlin/app/parsing/css/properties/longhands/layout/grid/ —
//  see the IR shape comments on each helper.
//
//  Everything in this file is defensive: unknown / unresolvable variants
//  are silently skipped (with a TODO marker) rather than raising, because
//  downstream aggregate reads treat nil fields as "unset".
//

import CoreGraphics
import Foundation

enum GridExtractor {

    /// Folds every recognised grid-* property into the shared aggregate.
    /// Called by LayoutExtractor's facade; each property is a no-op when
    /// not present in `properties`.
    static func contribute(_ properties: [IRProperty], into agg: inout LayoutAggregate) {
        // Local touched flag — OR-ed into agg.touched at the end so we
        // don't stomp on flexbox/position extractors that ran first.
        var touched = false

        for prop in properties {
            switch prop.type {
            case "GridTemplateColumns":
                if let tracks = parseTrackList(prop.data) {
                    // Wrap parsed tracks in a GridTrackList struct.
                    agg.gridTemplateColumns = GridTrackList(tracks: tracks)
                    touched = true
                }
            case "GridTemplateRows":
                if let tracks = parseTrackList(prop.data) {
                    agg.gridTemplateRows = GridTrackList(tracks: tracks)
                    touched = true
                }
            case "GridTemplateAreas":
                // IR: { "type": "areas", "rows": [["a","b"], …] } or "none".
                if let rows = parseAreas(prop.data) {
                    agg.gridTemplateAreas = rows
                    touched = true
                }
            case "GridAutoColumns":
                if let tracks = parseTrackList(prop.data) {
                    agg.gridAutoColumns = GridTrackList(tracks: tracks)
                    touched = true
                }
            case "GridAutoRows":
                if let tracks = parseTrackList(prop.data) {
                    agg.gridAutoRows = GridTrackList(tracks: tracks)
                    touched = true
                }
            case "GridAutoFlow":
                // IR: "ROW" / "COLUMN" or { direction, dense } object.
                if let flow = parseAutoFlow(prop.data) {
                    agg.gridAutoFlow = flow
                    touched = true
                }
            case "GridArea":
                if let line = parseGridLine(prop.data) {
                    agg.gridArea = line
                    touched = true
                }
            case "GridColumnStart", "GridColumnEnd":
                // Fold into gridColumn — last-seen wins. A finer split
                // (separate start/end) will land when the applier needs
                // span ranges spanning both sides. TODO: expose both.
                if let line = parseGridLine(prop.data) {
                    agg.gridColumn = line
                    touched = true
                }
            case "GridRowStart", "GridRowEnd":
                if let line = parseGridLine(prop.data) {
                    agg.gridRow = line
                    touched = true
                }
            case "JustifyItems":
                if let kw = parseAlign(prop.data) {
                    agg.justifyItems = kw
                    touched = true
                }
            case "JustifySelf":
                if let kw = parseAlign(prop.data) {
                    agg.justifySelf = kw
                    touched = true
                }
            // GridAutoTrack / GridTemplate / AlignTracks / JustifyTracks
            // / MasonryAutoFlow — registered but no SwiftUI analogue today.
            // TODO: breadcrumb via PropertyTracker once wired.
            default:
                break
            }
        }
        if touched { agg.touched = true }
    }

    // MARK: - Track list parsing

    /// IR shape options (see grid-template-columns parser):
    ///   • Array of entries:   [{ "fr": 1 }, { "px": 80 }, …]
    ///   • Array with repeat:  [{ "repeat": 4, "tracks": [{"fr":1}] }]
    ///   • Object with expr:   { "expr": "repeat(auto-fill, minmax(80px,1fr))" }
    private static func parseTrackList(_ value: IRValue) -> [GridTrack]? {
        if case .array(let entries) = value {
            var out: [GridTrack] = []
            for entry in entries {
                if let expanded = parseTrackEntry(entry) {
                    out.append(contentsOf: expanded)
                }
            }
            return out.isEmpty ? nil : out
        }
        if case .object(let o) = value, let expr = o["expr"]?.stringValue {
            return parseTrackExpr(expr)
        }
        return nil
    }

    /// Expand a single IR track entry. Returns an array because `repeat`
    /// produces multiple tracks inline.
    private static func parseTrackEntry(_ entry: IRValue) -> [GridTrack]? {
        guard case .object(let o) = entry else { return nil }
        // { "fr": N } — flexible weight.
        if let fr = o["fr"]?.doubleValue {
            return [GridTrack(kind: .flexible(weight: CGFloat(fr)))]
        }
        // { "px": N } — fixed physical size.
        if let px = o["px"]?.doubleValue {
            return [GridTrack(kind: .fixed(px: CGFloat(px)))]
        }
        // { "percent": N } — parent-relative.
        if let pct = o["percent"]?.doubleValue ?? o["percentage"]?.doubleValue {
            return [GridTrack(kind: .percent(CGFloat(pct)))]
        }
        // { "keyword": "auto" } — content-sized.
        if let kw = o["keyword"]?.stringValue, kw.lowercased() == "auto" {
            return [GridTrack(kind: .automatic)]
        }
        // { "repeat": N, "tracks": [...] } — expand N copies.
        if let n = o["repeat"]?.intValue, let ts = o["tracks"]?.arrayValue {
            var sub: [GridTrack] = []
            for t in ts {
                if let expanded = parseTrackEntry(t) {
                    sub.append(contentsOf: expanded)
                }
            }
            // Flatten Array(repeating: sub, count: N) down to a single list.
            return Array(repeating: sub, count: max(0, n)).flatMap { $0 }
        }
        // { "minmax": { "min": {px:…}, "max": {px:…|fr:…} } }.
        if let mm = o["minmax"]?.objectValue {
            let minPx = mm["min"]?.objectValue?["px"]?.doubleValue.map { CGFloat($0) }
            let maxPx = mm["max"]?.objectValue?["px"]?.doubleValue.map { CGFloat($0) }
            return [GridTrack(kind: .minmax(min: minPx, max: maxPx))]
        }
        // { "fit": { "px": N } } — fit-content(). SwiftUI has no direct
        // analogue; treat as fixed cap. TODO: revisit with container-
        // query sizing when it lands on iOS.
        if let fit = o["fit"]?.objectValue, let px = fit["px"]?.doubleValue {
            return [GridTrack(kind: .fixed(px: CGFloat(px)))]
        }
        // { "name": "start" } — named-line marker. No emitted track.
        // TODO: real named-line resolution would live in the applier.
        if o["name"]?.stringValue != nil { return [] }
        return nil
    }

    /// Narrow fallback for the string `expr` form (when the Kotlin parser
    /// couldn't fully decompose). Handles two common shapes: repeat with
    /// auto-fill/auto-fit wrapping a minmax, and a bare minmax. Anything
    /// else collapses to a single `.automatic` track + TODO.
    private static func parseTrackExpr(_ expr: String) -> [GridTrack]? {
        let lower = expr.lowercased()
        // repeat(auto-fill | auto-fit, minmax(a, b)) → one adaptive item.
        if lower.contains("auto-fill") || lower.contains("auto-fit") {
            if let range = lower.range(of: "minmax("),
               let end = lower[range.upperBound...].firstIndex(of: ")") {
                let inside = String(lower[range.upperBound..<end])
                let (lo, hi) = splitPxArgs(inside)
                return [GridTrack(kind: .adaptive(min: lo, max: hi))]
            }
        }
        // bare minmax(a, b).
        if lower.hasPrefix("minmax(") {
            if let end = lower.firstIndex(of: ")") {
                // Drop the "minmax(" prefix (7 chars) and everything past ')'.
                let start = lower.index(lower.startIndex, offsetBy: 7)
                let inside = String(lower[start..<end])
                let (lo, hi) = splitPxArgs(inside)
                return [GridTrack(kind: .minmax(min: lo, max: hi))]
            }
        }
        // Fallback — single automatic track. TODO: richer parse.
        return [GridTrack(kind: .automatic)]
    }

    /// Split "80px, 1fr" / "80px, 120px" into a (min, max) pair of optional
    /// CGFloat points. Non-px values (e.g. "1fr") land as nil.
    private static func splitPxArgs(_ s: String) -> (CGFloat?, CGFloat?) {
        let parts = s.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
        func px(_ p: String) -> CGFloat? {
            guard p.hasSuffix("px") else { return nil }
            return Double(p.dropLast(2)).map { CGFloat($0) }
        }
        let lo = parts.first.flatMap(px)
        let hi = parts.dropFirst().first.flatMap(px)
        return (lo, hi)
    }

    // MARK: - Template-areas parsing

    /// Extract a 2D name-grid from { "type": "areas", "rows": [[...]] }.
    /// Returns nil when the IR encodes "none" or is malformed.
    private static func parseAreas(_ value: IRValue) -> [[String]]? {
        if case .string(let s) = value, s.lowercased() == "none" { return nil }
        guard case .object(let o) = value,
              let rowsIR = o["rows"]?.arrayValue else { return nil }
        var rows: [[String]] = []
        for rowIR in rowsIR {
            guard case .array(let cells) = rowIR else { continue }
            // Non-string cells → "." (empty slot) per CSS spec.
            rows.append(cells.map { $0.stringValue ?? "." })
        }
        return rows.isEmpty ? nil : rows
    }

    // MARK: - Auto-flow

    /// IR: "ROW" / "COLUMN" (string) or { direction, dense } (object).
    private static func parseAutoFlow(_ value: IRValue) -> GridAutoFlowKeyword? {
        if case .string(let s) = value {
            switch s.uppercased() {
            case "ROW":    return .row
            case "COLUMN": return .column
            default:       return nil
            }
        }
        if case .object(let o) = value {
            let dir = o["direction"]?.stringValue?.uppercased() ?? "ROW"
            let dense = o["dense"]?.boolValue ?? false
            switch (dir, dense) {
            case ("ROW", true):     return .rowDense
            case ("ROW", false):    return .row
            case ("COLUMN", true):  return .columnDense
            case ("COLUMN", false): return .column
            default:                return nil
            }
        }
        return nil
    }

    // MARK: - GridLine

    /// IR (see grid-column parser): one of
    ///   • { "type": "number", "number": N }   (N may be negative)
    ///   • { "type": "span",   "count":  N }
    ///   • { "type": "name",   "name":   "s" }
    ///   • "auto"
    static func parseGridLine(_ value: IRValue) -> GridLine? {
        if case .string(let s) = value, s.lowercased() == "auto" {
            // All fields nil — encodes CSS auto.
            return GridLine()
        }
        guard case .object(let o) = value else { return nil }
        let kind = o["type"]?.stringValue?.lowercased()
        switch kind {
        case "number":
            return GridLine(line: o["number"]?.intValue)
        case "span":
            return GridLine(span: o["count"]?.intValue)
        case "name":
            return GridLine(name: o["name"]?.stringValue)
        default:
            return nil
        }
    }

    // MARK: - Alignment keyword parse

    /// Parse justify-items / justify-self. Local until the flexbox agent's
    /// alignment parser lands — keeps scopes disjoint.
    private static func parseAlign(_ value: IRValue) -> AlignmentKeyword? {
        let kw = ValueExtractors.normalize(ValueExtractors.extractKeyword(value))
        switch kw {
        case "START", "FLEX_START", "LEFT":       return .start
        case "END", "FLEX_END", "RIGHT":          return .end
        case "CENTER":                            return .center
        case "STRETCH":                           return .stretch
        case "BASELINE":                          return .baseline
        case "SELF_START":                        return .selfStart
        case "SELF_END":                          return .selfEnd
        case "AUTO":                              return .auto
        case "NORMAL":                            return .normal
        default:                                  return nil
        }
    }
}
