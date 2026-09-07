//
//  ZoomExtractor.swift
//  StyleEngine/rendering — IR `Zoom` → ZoomConfig.
//
//  Mirrors the reader's value flavors one branch per sealed variant of
//  `ZoomValue` in converter/src/main/kotlin/app/parsing/css/properties/
//  longhands/rendering/ZoomPropertyParser.kt, and is the byte-parallel
//  twin of runtimes/compose/.../rendering/ZoomExtractor.kt and
//  runtimes/web/src/engine/rendering/ZoomExtractor.ts.
//
//  Why this is NOT `ValueExtractors.extractKeyword`: that helper folds
//  `{"type":"number","value":1.5}` down to the STRING "number" (it reads
//  keyword ?? value ?? type and `value` isn't a string), which is how the
//  factor got silently dropped while `Zoom` still looked "registered".
//  The percentage variant is the other trap — `zoom: 150%` and
//  `zoom: 150` differ by two orders of magnitude, so the suffix has to be
//  read, not guessed.
//
//  Registry note: "Zoom" stays inside `RenderingProperty.names` (see
//  RenderingExtractor.swift) so PropertyRegistry ownership and the
//  Phase-10 coverage probe are unchanged. The string RenderingExtractor
//  also records for `Zoom` is inert — the rendering family has no applier
//  at all since retro P2b (A6#10) deleted the identity one, so this
//  extractor is the only path whose output reaches a modifier.
//

import Foundation

/// The single IR property this file owns.
enum ZoomPropertyName { static let name = "Zoom" }

enum ZoomExtractor {
    /// Last write wins, matching the cascade every other extractor uses.
    /// Returns nil when the IR carried no `Zoom`, so the applier chains
    /// as identity and every un-zoomed element stays byte-identical.
    static func extract(from properties: [IRProperty]) -> ZoomConfig? {
        var result: ZoomConfig?
        for p in properties where p.type == ZoomPropertyName.name {
            result = config(for: p.data)
        }
        return result
    }

    /// One IR payload → the typed config.
    private static func config(for data: IRValue) -> ZoomConfig {
        switch data {
        // Tagged-object wire — the shape the converter emits today.
        case .object(let o):
            return fromTagged(o)
        // Bare string: a hand-written fixture, or a future wire that
        // flattens tag-only variants. `normal` / `reset` and any keyword
        // outside the grammar are identity; a stringified number is not
        // guessed at as a percentage — the suffix would be missing, and
        // reading "150" as 150× is the corruption this file exists to
        // avoid, so only a bare `<number>` spelling is honoured.
        case .string(let s):
            let k = s.trimmingCharacters(in: .whitespaces).lowercased()
            if k == "normal" || k == "reset" { return ZoomConfig() }
            guard let d = Double(k) else { return ZoomConfig() }
            return factor(CGFloat(d))
        // Bare number.
        case .double(let d):  return factor(CGFloat(d))
        case .int(let i):     return factor(CGFloat(i))
        default:              return ZoomConfig()
        }
    }

    /// `{"type":"number"|"percentage"|"normal"|"reset", "value": …}`.
    private static func fromTagged(_ o: [String: IRValue]) -> ZoomConfig {
        let tag = o["type"]?.stringValue?.lowercased()
        // Both numeric variants carry the magnitude under `value`; the
        // reader wraps them in IRNumber / IRPercentage, which serialize
        // as a bare JSON number.
        let raw = o["value"]?.doubleValue
        switch tag {
        // `<number>`: 1 is unzoomed, 2 doubles the used values.
        case "number":
            guard let raw else { return ZoomConfig() }
            return factor(CGFloat(raw))
        // `<percentage>`: the suffix is load-bearing (see the header).
        case "percentage":
            guard let raw else { return ZoomConfig() }
            return factor(CGFloat(raw) / 100.0)
        // css-viewport-1: `normal` computes to the same used scale as 1.
        case "normal":
            return ZoomConfig()
        // Legacy WebKit `reset` — identity, see ZoomConfig for why that
        // is the CONVERGENT reading rather than a silent fallthrough.
        case "reset":
            return ZoomConfig()
        default:
            // A variant the reader grew without this extractor. iOS has no
            // PropertyTracker twin, so the honest signal is the identity
            // config plus this named branch (never an unlabelled default
            // that hides which shape arrived).
            return ZoomConfig()
        }
    }

    /// Builds a non-normal config, rejecting factors the layout pass
    /// cannot honour. css-viewport-1 requires a positive value; zero,
    /// negative, or non-finite would divide the incoming proposal by
    /// <= 0 (NaN / negative extents) and poison the measure pass, so all
    /// three degrade to identity rather than crash.
    private static func factor(_ f: CGFloat) -> ZoomConfig {
        guard f.isFinite, f > 0 else { return ZoomConfig() }
        return ZoomConfig(factor: f, isNormal: false)
    }
}
