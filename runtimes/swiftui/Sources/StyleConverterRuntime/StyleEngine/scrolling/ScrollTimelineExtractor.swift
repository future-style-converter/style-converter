//
//  ScrollTimelineExtractor.swift
//  StyleEngine/scrolling — Phase 9.
//
//  Owns the 3 scroll-timeline longhands:
//      ScrollTimeline, ScrollTimelineName, ScrollTimelineAxis.
//  Registered via `ScrollTimelineProperty.set` into PropertyRegistry.
//

import Foundation

/// Registry ownership for the 3 scroll-timeline properties.
enum ScrollTimelineProperty {
    /// Explicit name list — diff-auditable against the parser's
    /// `ScrollTimeline*PropertyParser.kt` counterparts.
    static let names: [String] = [
        "ScrollTimeline",
        "ScrollTimelineName",
        "ScrollTimelineAxis",
    ]
    /// Set form for the PropertyRegistry union.
    static var set: Set<String> { Set(names) }
}

enum ScrollTimelineExtractor {

    /// Walk every property once. Matches the shape of other grouped
    /// Phase 9 extractors so the renderer can call it uniformly.
    static func extract(from properties: [IRProperty]) -> ScrollTimelineConfig? {
        var cfg = ScrollTimelineConfig()
        let owned = ScrollTimelineProperty.set
        for p in properties where owned.contains(p.type) {
            applyOne(p, into: &cfg)
        }
        return cfg.touched ? cfg : nil
    }

    private static func applyOne(_ p: IRProperty, into cfg: inout ScrollTimelineConfig) {
        switch p.type {

        // IR: { name: { name: "--my-scroll" }, axis: "BLOCK" }
        // Note the nested `name.name` — a quirk of the Kotlin IR where
        // the name side is itself a typed object (`ScrollTimelineNameValue`)
        // rather than a bare string.
        case "ScrollTimeline":
            cfg.timeline = parseTimeline(p.data); cfg.touched = true

        // IR: { name: "--page-scroll" } or { name: "none" }
        // README note 15 — "none" is stored verbatim, no sentinel.
        case "ScrollTimelineName":
            cfg.name = extractName(p.data); cfg.touched = true

        // IR: "BLOCK"|"INLINE"|"X"|"Y"
        case "ScrollTimelineAxis":
            cfg.axis = parseAxis(p.data.stringValue); cfg.touched = true

        default:
            break
        }
    }

    /// IR: {name: {name:"…"}, axis: "…"}.
    private static func parseTimeline(_ data: IRValue) -> ScrollTimelineDeclaration? {
        guard case .object(let o) = data else { return nil }
        return ScrollTimelineDeclaration(
            name: extractName(o["name"] ?? .null),
            axis: parseAxis(o["axis"]?.stringValue))
    }

    /// Unwrap the typed-object form `{ name: "…" }` or accept a bare
    /// string if the fixture happens to provide one.
    private static func extractName(_ data: IRValue) -> String? {
        if let s = data.stringValue { return s }
        if case .object(let o) = data {
            return o["name"]?.stringValue
        }
        return nil
    }

    /// Axis keyword → enum — parser upper-cases.
    private static func parseAxis(_ s: String?) -> TimelineAxisKind? {
        switch s?.uppercased() {
        case "BLOCK":  return .block
        case "INLINE": return .inline
        case "X":      return .x
        case "Y":      return .y
        default:       return nil
        }
    }
}
