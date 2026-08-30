//
//  BackgroundColorInheritance.swift
//  StyleEngine/color — wave-49 lane A5.
//
//  `background-color: inherit` (css-cascade-5 §7.3.2). Byte-parallel twin of
//  the Compose `color/BackgroundColorInheritance.kt`.
//
//  WHY A TREE PASS AND NOT AN EXTRACTOR
//  `background-color` is a NON-inherited property, so the only way a child
//  takes its parent's value is the explicit `inherit` keyword — and §7.3.2
//  defines that as "the COMPUTED value of the property on the parent". An
//  extractor sees one component's declarations and has no parent, so the
//  substitution must happen where the parent link exists: on the composed
//  tree, once, before styling. It must NOT be done by adding BackgroundColor
//  to the inherited-text channel, which means "inherits by default" and would
//  hand every child its parent's background.
//
//  WHY THE SUBSTITUTED VALUE STAYS UNRESOLVED
//  What is inherited is the parent's COMPUTED value, and css-color-4 §6.4
//  says `currentcolor` "computes to itself" — so a parent's
//  `background-color: currentcolor` reaches the child still a keyword and
//  re-resolves against the CHILD's own `color`. Copying the parent's wire
//  value verbatim (never a resolved colour) is what preserves that;
//  CurrentColorBackground then does the per-element §6.4 resolution, for the
//  bare keyword and for a `color-mix()` containing it alike.
//
//  MEASURED in Chrome 151 on the exact three-div nesting of
//  css-color/currentcolor-002 — outer `color:red; background:currentcolor`,
//  middle `background:inherit`, inner `color:green; background:inherit` —
//  the computed backgrounds are rgb(255,0,0) / rgb(255,0,0) / rgb(0,128,0):
//  the inner box is GREEN, which is why the reference is a plain green square
//  while both natives painted nothing at all on the wave-48 gate (iOS ssim
//  0.8727, Android 0.8721).
//

import Foundation

enum BackgroundColorInheritance {

    /// The IR type this pass rewrites.
    private static let type = "BackgroundColor"

    /// Resolve `inherit` through a whole composed forest — the shape
    /// `IRComposer.compose` returns.
    static func resolveAll(_ roots: [IRComponent]) -> [IRComponent] {
        roots.map { resolve($0, inherited: nil) }
    }

    /// Resolve one composed tree. A root has no parent, so an `inherit` there
    /// takes the initial value (`transparent`, css-backgrounds-3 §2.2) —
    /// expressed by dropping the declaration, which is already what "no
    /// BackgroundColor property" means to every applier.
    static func resolve(_ root: IRComponent) -> IRComponent {
        resolve(root, inherited: nil)
    }

    /// - Parameter inherited: the parent's COMPUTED `background-color` wire
    ///   value; nil when the parent declared none (its computed value is then
    ///   the initial `transparent`, which needs no declaration).
    private static func resolve(_ component: IRComponent, inherited: IRValue?) -> IRComponent {
        // The cascade already ran in the converter, so the LAST declaration of
        // the type is the specified one — the same last-wins rule every
        // extractor applies within one property list.
        let declared = component.properties.last { $0.type == type }
        let declaredIsInherit = declared.map { isInheritKeyword($0.data) } ?? false

        // This element's computed value: the parent's when `inherit`, its own
        // declaration otherwise, nil (the initial value) when it declares none.
        let computed: IRValue? = declaredIsInherit ? inherited : declared?.data

        // Rewrite only when the keyword is present, so a document that never
        // uses `inherit` comes back structurally untouched.
        var out = component
        if declaredIsInherit {
            // Drop EVERY BackgroundColor entry and re-add the resolved one
            // last. Dropping all of them keeps last-wins honest: an earlier
            // real colour must not resurface when the winning `inherit`
            // resolves to nothing (the root case).
            var kept = component.properties.filter { $0.type != type }
            if let computed { kept.append(IRProperty(type: type, data: computed)) }
            out = IRComponent(
                id: component.id, name: component.name, properties: kept,
                selectors: component.selectors, media: component.media,
                children: component.children, slot: component.slot,
                text: component.text, pseudos: component.pseudos,
                meta: component.meta, variables: component.variables)
        }

        // Recurse with THIS element's computed value as the children's
        // inheritance source. `children` is nil (never []) for leaves — the
        // absent-vs-empty discipline the composer keeps — so preserve it.
        guard let kids = out.children else { return out }
        return out.withChildren(kids.map { resolve($0, inherited: computed) })
    }

    /// True when the wire value is the CSS-wide keyword `inherit`.
    ///
    /// The converter ships keywords it cannot pre-resolve in the same
    /// srgb-less envelope it uses for `currentColor` — `{"original":"inherit"}`
    /// (verbatim in tools/titan/runs/wave48-final/sections/css-color/
    /// per-test-ir/wpt__css-color__currentcolor-002.json and both
    /// color-mix-currentcolor-00{1,2}.json); a bare `"inherit"` string is
    /// accepted for symmetry with the other keyword readers. Deliberately
    /// narrow: `initial` / `unset` / `revert` all compute to the initial value
    /// on a non-inherited property, which is already what no declaration
    /// produces, so claiming them would widen the surface for no effect.
    private static func isInheritKeyword(_ data: IRValue) -> Bool {
        // CSS keywords are ASCII case-insensitive (css-values-4 §4.1).
        if let s = data.stringValue { return s.lowercased() == "inherit" }
        if let o = data.objectValue, let s = o["original"]?.stringValue {
            return s.lowercased() == "inherit"
        }
        return false
    }
}
