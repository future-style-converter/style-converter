//
//  StateResolver.swift
//  StyleEngine/core/dynamic — wave 7 (dynamic styling, issues #33/#34).
//
//  The style-resolution fold of schema/spec/06-dynamic-styling.md §3:
//
//    1. start from the base `properties` list
//    2. overlay each ACTIVE media bucket, in media[] array order
//    3. overlay each ACTIVE selector bucket, in selectors[] array order
//
//  Overlay = whole-value replacement per property `type` (never a deep
//  merge of `data`), append when the type is new; later buckets beat
//  earlier ones for the same type (last writer wins — css-cascade-5
//  §6.4 source order). State beats media BY THE FIXED STEP ORDER — the
//  spec forbids reordering by any other heuristic.
//
//  Resolution runs BEFORE extraction (ComponentRenderer feeds the
//  result into the inheritance merge → DynamicValueResolver →
//  StyleBuilder), so extractors and appliers never see buckets. Pure:
//  given (base, activation set) the output is deterministic — the §5
//  re-evaluation contract falls out of SwiftUI recomputing body when a
//  state flag or environment input changes (stable view identity, no
//  tree recomposition).
//

import Foundation

/// Pure bucket-layering resolver. Stateless; fully XCTest-pinned.
// enum: namespace only (mirrors MediaQueryEvaluator / StyleBuilder).
enum StateResolver {

    /// The runtime-v1 selector-condition vocabulary (spec 06 §2). Wire
    /// conditions arrive colon-stripped ("hover", not ":hover") — the
    /// v2 conformance goldens pin that spelling.
    static let runtimeV1Conditions: Set<String> =
        ["hover", "active", "focus", "disabled", "checked"]

    /// Is `condition` active for `state`? nil = outside the runtime-v1
    /// vocabulary (the caller keeps the bucket inert and logs the miss).
    /// Forcing wins over real input (spec 06 §6: "treated as active
    /// regardless of real input state") but NEVER rescues an
    /// unsupported condition — forcing "visited" still resolves nil.
    static func conditionActive(_ condition: String,
                                state: ComponentState) -> Bool? {
        // Vocabulary gate first so forced garbage can't activate.
        guard runtimeV1Conditions.contains(condition) else { return nil }
        // Forced set: the capture hook's deterministic override.
        if state.forced.contains(condition) { return true }
        switch condition {
        // Pointer designation — real .onHover only; on touch surfaces
        // the flag never latches, which IS the §2 defined no-op.
        case "hover":    return state.hovered
        // Press in progress (§2: SwiftUI press gesture state).
        case "active":   return state.pressed
        // Platform focus system (v1 implements `focus` only —
        // focus-visible/-within are reserved and land in the nil arm).
        case "focus":    return state.focused
        // Host-supplied disabled flag (.disabled() propagation).
        case "disabled": return state.disabled
        // Checkable analogue — no cheap generic one in SwiftUI, so the
        // real flag is always false today (forced-only; resolve() logs
        // the defined no-op once).
        case "checked":  return state.checked
        // Unreachable — the vocabulary gate above covers every case —
        // but Swift can't prove it over a Set membership test.
        default:         return nil
        }
    }

    /// The §3 fold: effective property list for one component.
    /// `componentName` only seasons the PropertyTracker breadcrumbs.
    static func resolve(base: [IRProperty],
                        selectors: [IRSelector]?,
                        media: [IRMedia]?,
                        state: ComponentState,
                        environment: MediaQueryEvaluator.Environment,
                        componentName: String = "") -> [IRProperty] {
        // Fast path — no buckets, no work: the entire pre-wave-7 fixture
        // corpus takes this branch and stays allocation-identical.
        let mediaBuckets = media ?? []
        let selectorBuckets = selectors ?? []
        if mediaBuckets.isEmpty && selectorBuckets.isEmpty { return base }

        var out = base
        // ── Step 2: active MEDIA buckets, wire array order ───────────
        for bucket in mediaBuckets {
            switch MediaQueryEvaluator.evaluate(bucket.query, in: environment) {
            // Query holds → layer the bucket's declarations.
            case .active:
                overlay(&out, bucket: bucket.properties)
            // Evaluated false → contributes nothing (silently — this is
            // a correct answer, not a fallthrough).
            case .inactive:
                break
            // Outside the v1 grammar → conservatively inactive + one
            // breadcrumb per query per process (spec 06 §4).
            case .unsupported:
                PropertyTracker.logOnce(
                    key: "media:\(bucket.query)",
                    message: "media query '\(bucket.query)' is outside the "
                        + "runtime-v1 grammar (spec 06 §4) — bucket inactive "
                        + "(component '\(componentName)')")
            }
        }
        // ── Step 3: active SELECTOR buckets, wire array order ────────
        for bucket in selectorBuckets {
            // Normalize defensively: wire conditions are colon-stripped
            // already; tolerate a stray leading ':' and author case.
            let cond = normalize(bucket.condition)
            if let active = conditionActive(cond, state: state) {
                // `checked` with no real analogue: when it resolves
                // inactive WITHOUT being forced, that is the §2 defined
                // no-op — breadcrumb it once so the miss is auditable.
                if cond == "checked", !active {
                    PropertyTracker.logOnce(
                        key: "selector:checked",
                        message: "':checked' has no generic SwiftUI analogue"
                            + " — defined no-op (spec 06 §2); force it via "
                            + "forcedStyleStates for capture")
                }
                // Active → layer; inactive → nothing (correct answer).
                if active { overlay(&out, bucket: bucket.properties) }
            } else {
                // Outside the v1 vocabulary → inert + one breadcrumb per
                // condition per process (spec 06 §2 tolerance rule).
                PropertyTracker.logOnce(
                    key: "selector:\(cond)",
                    message: "selector condition '\(cond)' is outside the "
                        + "runtime-v1 set (spec 06 §2) — bucket inert "
                        + "(component '\(componentName)')")
            }
        }
        return out
    }

    // MARK: - Overlay primitive

    /// §3 overlay: for each bucket entry, REPLACE the entry with the
    /// same `type` in place (whole-value replacement) or APPEND when the
    /// type is new. If the current list carries duplicate entries of a
    /// type (legal on the wire), the replacement lands at the FIRST
    /// occurrence and the duplicates are dropped — leaving exactly one
    /// entry so first-wins and last-wins extractor scans agree on the
    /// bucket's value.
    private static func overlay(_ current: inout [IRProperty],
                                bucket: [IRProperty]) {
        for p in bucket {
            // Track whether we already substituted this bucket entry.
            var replaced = false
            // Single pass: replace first match, drop later duplicates,
            // keep everything else in original order.
            current = current.compactMap { existing in
                guard existing.type == p.type else { return existing }
                // Later duplicate of an already-replaced type → drop.
                if replaced { return nil }
                // First match → whole-value replacement in place.
                replaced = true
                return p
            }
            // No entry of this type yet → append (spec 06 §3).
            if !replaced { current.append(p) }
        }
    }

    /// Condition normalizer: strip one leading ':' (defensive — the
    /// wire is colon-stripped) and lowercase (selector keywords are
    /// ASCII-case-insensitive, css-selectors-4 §3.2).
    static func normalize(_ condition: String) -> String {
        let trimmed = condition.trimmingCharacters(in: .whitespacesAndNewlines)
        let stripped = trimmed.hasPrefix(":") ? String(trimmed.dropFirst()) : trimmed
        return stripped.lowercased()
    }
}
