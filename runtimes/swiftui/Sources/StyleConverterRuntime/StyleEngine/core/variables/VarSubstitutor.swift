//
//  VarSubstitutor.swift
//  StyleEngine/core/variables — wave 6 (dynamic values, issue #32).
//
//  Textual var() substitution per css-variables-1 §3 ("substitute a
//  var()"): every `var(--name)` / `var(--name, <fallback>)` occurrence
//  in a preserved declaration string is replaced by
//    1. the value of `--name` in the element's scope chain, else
//    2. the fallback (itself substituted — fallbacks nest per §2.3:
//       `var(--a, var(--b, 4px))`), else
//    3. nothing — the whole declaration becomes GUARANTEED-INVALID and
//       the property computes to `unset` (§3.1). We signal that with a
//       nil return so the caller drops the declaration.
//
//  Pure string → string?, fully covered by DynamicValuesTests.
//

import Foundation

enum VarSubstitutor {

    /// Substitution recursion cap. A stored value may itself contain
    /// var() (`--a: var(--b)`); §2.3 forbids cycles — a cycle makes the
    /// affected variables invalid. Bounding the depth turns any cycle
    /// (and absurdly deep chains) into guaranteed-invalid instead of an
    /// infinite loop.
    private static let maxDepth = 16

    /// Replace every var() reference in `raw` using `store`. Returns the
    /// fully-substituted string, or nil when any reference is
    /// guaranteed-invalid (undefined name with no fallback, or a cycle).
    /// Strings without "var(" pass through untouched (fast path).
    static func substitute(_ raw: String, store: VariableStore) -> String? {
        substitute(raw, store: store, depth: 0)
    }

    /// Depth-carrying worker — see maxDepth for why the bound exists.
    private static func substitute(_ raw: String, store: VariableStore,
                                   depth: Int) -> String? {
        // Cycle / depth guard → guaranteed-invalid (§2.3 cycles rule).
        guard depth < maxDepth else { return nil }
        // Fast path: nothing to substitute.
        guard raw.contains("var(") else { return raw }

        // Rebuild the string left-to-right, expanding each var() as we
        // meet it. String scanning (not regex) because the argument can
        // contain nested parentheses: `var(--a, var(--b, 4px))`.
        var out = ""
        var rest = Substring(raw)
        while let range = rest.range(of: "var(") {
            // Copy everything before the reference verbatim.
            out += rest[..<range.lowerBound]
            // Find the matching close paren for THIS var( — nested
            // parens (inner var()/calc() in the fallback) must balance.
            guard let close = matchingParen(in: rest, openingAt: range.upperBound)
            else { return nil } // Unbalanced — malformed, treat as invalid.
            // The argument list between "var(" and its ")".
            let args = rest[range.upperBound..<close]
            // Expand the single reference (nil bubbles up = invalid).
            guard let expanded = expand(args, store: store, depth: depth)
            else { return nil }
            out += expanded
            // Continue after the close paren.
            rest = rest[rest.index(after: close)...]
        }
        // Tail after the last reference.
        out += rest
        // The substituted VALUE may itself contain var() (`--a: var(--b)`
        // stored verbatim) — re-run until stable, depth-bounded.
        return out.contains("var(") ?
            substitute(out, store: store, depth: depth + 1) : out
    }

    /// Expand one var() argument list: `--name` or `--name, <fallback>`.
    /// Comma split honours nesting — only a TOP-LEVEL comma separates
    /// the name from the fallback (§2.3 grammar: the fallback swallows
    /// everything after the first comma, further commas included).
    private static func expand(_ args: Substring, store: VariableStore,
                               depth: Int) -> String? {
        // Locate the first comma at paren-depth zero.
        var parens = 0
        var commaIndex: Substring.Index? = nil
        for i in args.indices {
            switch args[i] {
            case "(": parens += 1
            case ")": parens -= 1
            case "," where parens == 0:
                commaIndex = i
            default: break
            }
            if commaIndex != nil { break }
        }
        // Custom-property name: everything before the comma (or all of
        // it). The NAME token tolerates surrounding whitespace, but its
        // case is significant — trim only, never lowercase (§2).
        let rawName = commaIndex.map { args[..<$0] } ?? args
        let name = rawName.trimmingCharacters(in: .whitespacesAndNewlines)
        // Must look like a custom property: `--` + at least one char
        // (bare `--` is reserved, css-variables-1 §2).
        guard name.hasPrefix("--"), name.count > 2 else { return nil }

        // 1. Scope-chain lookup — the merged store already applied
        //    nearest-wins shadowing. The stored value substitutes
        //    verbatim; inner var() references resolve on the re-run in
        //    substitute() above.
        if let value = store.value(name) {
            return value
        }
        // 2. Undefined → fallback, itself substituted (nested fallbacks,
        //    §2.3: `var(--a, var(--b, 4px))` walks b then the literal).
        if let comma = commaIndex {
            let fallback = String(args[args.index(after: comma)...])
            return substitute(fallback, store: store, depth: depth + 1)
        }
        // 3. No definition, no fallback → guaranteed-invalid (§3.1).
        return nil
    }

    /// Index of the ")" matching the "(" that `openingAt` follows (the
    /// caller consumed "var(" so we start at depth 1). nil when the
    /// string ends before balance — malformed input.
    private static func matchingParen(in s: Substring,
                                      openingAt start: Substring.Index)
    -> Substring.Index? {
        var depth = 1
        var i = start
        while i < s.endIndex {
            // Track every paren so nested var()/calc() in fallbacks work.
            if s[i] == "(" { depth += 1 }
            if s[i] == ")" {
                depth -= 1
                if depth == 0 { return i }
            }
            i = s.index(after: i)
        }
        return nil
    }
}
