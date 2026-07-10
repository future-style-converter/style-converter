//
//  VariableStore.swift
//  StyleEngine/core/variables — wave 6 (dynamic values, issue #32).
//
//  The custom-property scope for one element: every `--name` definition
//  visible at this point of the slot-composed tree (css-variables-1 §2:
//  custom properties are ordinary inherited properties, so an element's
//  scope is its own `variables` map layered over its slot-parent chain).
//
//  Populated from the decoded IRComponent.variables maps: each
//  ComponentRenderer merges its component's own definitions OVER the
//  chain it inherited through the Environment channel below, uses the
//  merged store for its own var() resolution, and republishes the merged
//  store for its children — reproducing the scope chain transitively,
//  exactly like the InheritedText channel does for text properties.
//

import SwiftUI

/// Immutable custom-property scope. Names are case-SENSITIVE and values
/// are the verbatim declaration strings (never trimmed, empty legal) —
/// both straight from css-variables-1 §2 and the IR v2 `variables`
/// contract (schema/spec/02-values.md).
struct VariableStore: Equatable {
    /// The flattened scope: nearest definition already won during merge,
    /// so lookup is a single exact-case dictionary hit.
    private let definitions: [String: String]

    /// Empty scope — the Environment default at the tree root.
    static let empty = VariableStore(definitions: [:])

    /// Memberwise wrap; internal so tests can build scopes directly.
    init(definitions: [String: String]) {
        self.definitions = definitions
    }

    /// Layer `own` definitions over an inherited chain. Own wins on name
    /// collision — css-variables-1 §2: the nearest scope shadows outer
    /// ones (TK_TwoLevelShadow's mid `--accent` repaints its subtree).
    /// nil / empty own returns the inherited store unchanged (cheap).
    static func merge(inherited: VariableStore,
                      own: [String: String]?) -> VariableStore {
        // Fast path — components without definitions (the vast majority).
        guard let own = own, !own.isEmpty else { return inherited }
        // Dictionary.merging with `new` winning = shadowing semantics.
        return VariableStore(definitions:
            inherited.definitions.merging(own) { _, new in new })
    }

    /// Exact-case lookup (`--Brand-Fg` ≠ `--brand-fg`, css-variables-1
    /// §2). nil = not defined anywhere in the chain → the var() falls
    /// back or goes guaranteed-invalid (VarSubstitutor).
    func value(_ name: String) -> String? {
        definitions[name]
    }

    /// True when no definition is visible — lets callers skip the
    /// substitution pass entirely for variable-free trees.
    var isEmpty: Bool { definitions.isEmpty }
}

// MARK: - Environment plumbing

/// Environment key carrying the parent chain's merged variable scope.
/// Mirrors the InheritedText channel: parents publish their MERGED store
/// for children, so each level sees exactly its slot-parent chain.
private struct CSSVariablesKey: EnvironmentKey {
    /// Root default: no definitions in scope.
    static let defaultValue: VariableStore = .empty
}

extension EnvironmentValues {
    /// The custom-property scope inherited from the slot-parent chain
    /// (empty at the root). ComponentRenderer merges the component's own
    /// `variables` map over this before resolving its declarations.
    var cssVariables: VariableStore {
        get { self[CSSVariablesKey.self] }
        set { self[CSSVariablesKey.self] = newValue }
    }
}
