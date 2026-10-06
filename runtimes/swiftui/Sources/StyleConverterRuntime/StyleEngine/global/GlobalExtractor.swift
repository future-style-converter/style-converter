//
//  GlobalExtractor.swift
//  StyleEngine/global — Phase 10.
//

import Foundation

enum GlobalProperty {
    // The one IR type this category owns: the `all` shorthand.
    static let names: [String] = ["All"]
    // Set form for O(1) membership in the extractor loop.
    static var set: Set<String> { Set(names) }
}

enum GlobalExtractor {
    /// Inert config fold (no applier reads it — see GlobalConfig.swift).
    static func extract(from properties: [IRProperty]) -> GlobalConfig? {
        // Fresh config; `touched` flips on the first owned entry.
        var cfg = GlobalConfig()
        // The category's owned type names.
        let owned = GlobalProperty.set
        // Record every owned entry by its keyword (or raw description).
        for p in properties where owned.contains(p.type) {
            cfg.touched = true
            if let kw = ValueExtractors.extractKeyword(p.data) {
                cfg.rawByType[p.type] = kw
            } else {
                cfg.rawByType[p.type] = String(describing: p.data)
            }
        }
        // nil when the list carries no `All` at all.
        return cfg.touched ? cfg : nil
    }

    // MARK: - Wave 52 (lane L11) — the ORDER-AWARE `all` reset

    /// css-cascade-4 §3.1: the two longhands `all` never resets.
    static let allResetExemptTypes: Set<String> = ["Direction", "UnicodeBidi"]

    /// Keywords under which the inherited channel survives (§7.3.2–§7.3.5).
    private static let keepsInherited: Set<String> = ["INHERIT", "UNSET", "REVERT", "REVERT_LAYER"]

    /// The normalised keyword of an `All` entry (nil for any other type or a
    /// payload with no keyword) — `-` folds onto the wire enum spelling.
    static func allKeyword(_ p: IRProperty) -> String? {
        // Only the shorthand carries a reset keyword.
        guard p.type == "All", let kw = ValueExtractors.extractKeyword(p.data) else { return nil }
        // One spelling: the converter emits `REVERT_LAYER`, hand IR may not.
        return kw.uppercased().replacingOccurrences(of: "-", with: "_")
    }

    /// Wave 52 (lane L11, brief `colour-not-reaching-text` F1) — twin of
    /// Compose `global/AllReset.kt` and web `applyAllReset`, one rule:
    ///  1. the LAST keyword `All` at index i governs (css-cascade-4 §6.4);
    ///  2. own[0, i) drops except `Direction` / `UnicodeBidi` (§3.1);
    ///  3. own(i, end] is kept verbatim — a later longhand beats the earlier
    ///     shorthand (`all: initial; color: green` computes green — the
    ///     all-prop-initial-color cells painted black under the old drop);
    ///  4. the inherited channel: INITIAL drops it except an inherited
    ///     `Direction` (§7.3.1 + §3.1); INHERIT / UNSET / REVERT /
    ///     REVERT_LAYER keep it (§7.3.2–§7.3.5 — the channel only carries
    ///     inherited types, for which all four mean "inherit");
    ///  5. every `All` entry leaves the list (it has no applier).
    /// Runs BEFORE `InheritedText.merge` (ComponentRenderer.mergedProperties,
    /// seam patch) — the only point that still sees own and inherited apart.
    /// Identity (the same arrays) for every All-free own list.
    /// KNOWN GAP (logged once): `all: inherit` should also hand NON-inherited
    /// properties the parent's value (§7.3.2 — all-prop-002's display); the
    /// channel cannot carry them, so they fall to their initial value.
    static func applyingAllReset(own: [IRProperty],
                                 inherited: [IRProperty]) -> (own: [IRProperty], inherited: [IRProperty]) {
        // Step 1 — the governing `all` is the last keyword one.
        guard let i = own.lastIndex(where: { allKeyword($0) != nil }),
              let keyword = allKeyword(own[i]) else {
            // No `all` on this element: identity on both arrays (fast path).
            return (own, inherited)
        }
        // A keyword the converter cannot emit: surface it, apply INITIAL.
        if keyword != "INITIAL" && !keepsInherited.contains(keyword) {
            PropertyTracker.logOnce(key: "All:\(keyword)",
                                    message: "all: unknown keyword \(keyword) — applied as initial")
        }
        // The documented `all: inherit` gap — reported once, never silent.
        if keyword == "INHERIT" {
            PropertyTracker.logOnce(key: "All:INHERIT",
                                    message: "all: inherit — non-inherited properties take their initial value (KNOWN GAP)")
        }
        // Step 2 — before the shorthand: only the §3.1 exemptions survive.
        let before = own[..<i].filter { allResetExemptTypes.contains($0.type) }
        // Steps 3 + 5 — after the shorthand: kept, minus any further `All`.
        let after = own[(i + 1)...].filter { $0.type != "All" }
        // Step 4 — the inherited channel, per keyword.
        let keptInherited = keepsInherited.contains(keyword)
            ? inherited                                                // inherit / unset / revert*
            : inherited.filter { allResetExemptTypes.contains($0.type) } // initial: `direction` flows
        // Source order preserved within each half.
        return (Array(before) + Array(after), keptInherited)
    }
}
