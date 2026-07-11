//
//  MediaQueryEvaluator.swift
//  StyleEngine/core/dynamic — wave 7 (dynamic styling, issue #34).
//
//  Runtime-v1 media-query evaluation (schema/spec/06-dynamic-styling.md
//  §4): a single `(feature: value)` term or an `and`-joined conjunction,
//  with exactly two feature families —
//
//    min-width / max-width   compared against the RENDER-SURFACE width
//                            (the host-published styleViewport, i.e. the
//                            capture canvas — NEVER the device screen),
//                            px values only, inclusive boundaries
//                            (mediaqueries-5 §4.2).
//    prefers-color-scheme    light | dark, mapped to the platform
//                            dark-mode signal (mediaqueries-5 §11.5 —
//                            SwiftUI's colorScheme environment on iOS).
//
//  EVERYTHING else — `not`/`only` prefixes, comma query lists, range
//  syntax, any other feature, malformed input — is .unsupported: the
//  caller keeps the bucket conservatively INACTIVE and logs once via
//  PropertyTracker (a query the runtime cannot evaluate never applies).
//

import Foundation

/// Pure query-string → verdict evaluator. Stateless; fully XCTest-pinned.
// enum: namespace only (mirrors StateResolver / DynamicValueResolver).
enum MediaQueryEvaluator {

    /// The rendering environment one document resolves against —
    /// spec 06 §4: one surface, one scheme answer.
    struct Environment: Equatable {
        /// Render-surface width in CSS px (styleViewport.width — the
        /// 390 px capture canvas by default, a real app's own surface
        /// otherwise). Deliberately NOT UIScreen.
        var surfaceWidthPx: Double = 390.0
        /// Platform dark-mode signal (colorScheme == .dark). Shared with
        /// light-dark() value resolution so both mechanisms agree.
        var prefersDark: Bool = false
    }

    /// Three-way verdict: active/inactive are evaluated answers;
    /// unsupported means "outside the runtime-v1 grammar" — the caller
    /// must treat the bucket as inactive AND log the miss.
    enum Verdict: Equatable { case active, inactive, unsupported }

    /// Evaluate one wire query string (verbatim converter output, e.g.
    /// "(min-width: 300px)" or "(min-width: 200px) and (max-width: 500px)").
    static func evaluate(_ query: String, in env: Environment) -> Verdict {
        // Queries are ASCII-case-insensitive (mediaqueries-5 §2.4);
        // author whitespace at the edges is meaningless.
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        // Empty string is malformed → unsupported (never active).
        guard !q.isEmpty else { return .unsupported }
        // Comma = a query LIST (OR semantics) — not in the v1 grammar.
        guard !q.contains(",") else { return .unsupported }
        // Negation / legacy gating prefixes — not in the v1 grammar.
        guard !q.hasPrefix("not "), !q.hasPrefix("only ") else { return .unsupported }
        // mediaqueries-4 range syntax ("(200px <= width)") — v1 evaluates
        // only the classic min-/max- prefixed forms.
        guard !q.contains("<"), !q.contains(">") else { return .unsupported }
        // Conjunction split. The v1 grammar's terms are parenthesised, so
        // the literal " and " separator can't appear inside a term.
        let terms = q.components(separatedBy: " and ")
        // A conjunction is active iff EVERY term is; one unsupported term
        // poisons the whole query (we can't know the AND's answer).
        var allActive = true
        for term in terms {
            switch evaluateTerm(term, env) {
            case .unsupported: return .unsupported
            case .inactive:    allActive = false
            case .active:      break
            }
        }
        return allActive ? .active : .inactive
    }

    // MARK: - Single term

    /// Evaluate one parenthesised `(feature: value)` term.
    private static func evaluateTerm(_ raw: String, _ env: Environment) -> Verdict {
        // Terms may carry author whitespace between the "and" joins.
        let t = raw.trimmingCharacters(in: .whitespaces)
        // v1 terms are always parenthesised — a bare token is malformed.
        guard t.hasPrefix("("), t.hasSuffix(")") else { return .unsupported }
        // Strip the parentheses, then split into feature : value. A
        // boolean-context term ("(color)") has no colon → unsupported.
        let inner = t.dropFirst().dropLast()
        let parts = inner.split(separator: ":", maxSplits: 1)
        guard parts.count == 2 else { return .unsupported }
        let feature = parts[0].trimmingCharacters(in: .whitespaces)
        let value = parts[1].trimmingCharacters(in: .whitespaces)
        switch feature {
        // Width bounds — inclusive per mediaqueries-5 §4.2:
        // (min-width: 390px) MATCHES a 390 px surface.
        case "min-width":
            guard let px = pxValue(value) else { return .unsupported }
            return env.surfaceWidthPx >= px ? .active : .inactive
        case "max-width":
            guard let px = pxValue(value) else { return .unsupported }
            return env.surfaceWidthPx <= px ? .active : .inactive
        // Scheme preference — the platform dark-mode signal.
        case "prefers-color-scheme":
            switch value {
            case "dark":  return env.prefersDark ? .active : .inactive
            case "light": return env.prefersDark ? .inactive : .active
            // no-preference / garbage — outside the v1 vocabulary.
            default:      return .unsupported
            }
        // Any other feature (orientation, hover, resolution, …) — inert.
        default:
            return .unsupported
        }
    }

    /// Parse a `<number>px` dimension. v1 evaluates px ONLY (spec 06 §4)
    /// — em/rem/vw width bounds are unsupported, not approximated.
    private static func pxValue(_ s: String) -> Double? {
        // Require the px suffix, then parse the numeric prefix.
        guard s.hasSuffix("px") else { return nil }
        return Double(s.dropLast(2).trimmingCharacters(in: .whitespaces))
    }
}
