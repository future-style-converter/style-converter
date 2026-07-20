//
//  ContentsUnboxing.swift
//  StyleConverterRuntime — wave 18 (RC6, lane 1).
//
//  `display: contents` unboxing, css-display-3 §2.5: the element itself
//  generates NO box; its contents (text run + children) render as if the
//  element were replaced by them in its parent. Before this file neither
//  native unboxed: iOS mapped contents→block (StyleBuilder.legacyDisplay),
//  so the wrapper became a real block box — its border/background painted
//  (display-contents-button/-details drew spurious full-width 10px red
//  borders) and in a grid/flex parent the WRAPPER became the item, so the
//  container's justify-items/align-items never reached the grandchildren
//  (display-contents-alignment-002).
//
//  The unboxing is a PURE IR tree rewrite applied at ComponentRenderer's
//  init (the single entry every render path shares), so every child-
//  collection path — block flow, positioned overlay, flex, grid item
//  building — sees the spliced children without per-branch code. Twin of
//  Compose core/renderer/ContentsUnboxing.kt; the two gates and splice
//  rules must match (pinned by ContentsUnboxingTest(s) on both natives).
//

import Foundation

enum ContentsUnboxing {

    /// The wire keyword the CSS reader emits for `display: contents`.
    private static let contentsKeyword = "CONTENTS"

    /// True when the BASE declarations compute `display: contents` —
    /// read with the shared keyword decoder (last declaration wins,
    /// matching the extractors' cascade order).
    static func isDisplayContents(_ properties: [IRProperty]) -> Bool {
        guard let display = properties.last(where: { $0.type == "Display" })
        else { return false }
        return ValueExtractors.normalize(
            ValueExtractors.extractKeyword(display.data)) == contentsKeyword
    }

    /// The unboxing gate (pin C1/C3 — mirror of the Compose table).
    /// A component unboxes only when ALL of:
    ///  - its base Display computes to `contents`;
    ///  - it is NOT positioned out-of-flow and NOT floated —
    ///    css-display-3 §2.7 blockifies absolutely-positioned and floated
    ///    elements, so `contents` never survives on them;
    ///  - it carries NO ::before/::after payload — a contents element's
    ///    pseudo-elements DO render (treated as its first/last children,
    ///    §2.5) and the pseudo machinery hangs off the component box;
    ///    such elements keep the legacy block-wrapper approximation
    ///    (documented TODO — pins the four green before-after tests);
    ///  - it carries NO selector/media buckets — a bucket could flip
    ///    `display` at runtime and the static rewrite cannot follow
    ///    (same base-declaration conservatism as FixedHoist).
    static func isUnboxable(_ component: IRComponent) -> Bool {
        guard isDisplayContents(component.properties) else { return false }
        // §2.7 blockification: out-of-flow boxes never stay `contents`.
        // Deliberately WIDER than the spec: ANY non-static position keeps
        // the box (a `position: relative` contents element would unbox per
        // spec, but the positioned-children partition reads the ORIGINAL
        // ancestry — stripping the wrapper would desync the containing-
        // block classification on both natives, so the wrapper stays: the
        // safe, honest approximation, mirrored in the Compose gate).
        let agg = LayoutExtractor.extract(from: component.properties)
        if let pos = agg?.position, pos != .staticPos { return false }
        // Float blockifies too (§2.7); any non-none float keeps the box.
        if let f = agg?.float, f != FloatKeyword.none { return false }
        // Pseudo payloads render through the component box — keep it.
        if component.pseudos != nil { return false }
        // Dynamic buckets could flip display — conservative keep.
        return (component.selectors?.isEmpty ?? true)
            && (component.media?.isEmpty ?? true)
    }

    /// The inheritable subset of a contents element's declarations —
    /// §2.5 removes the BOX, not the ELEMENT, so inheritance still flows
    /// through it (its color/font reach the spliced children). Everything
    /// non-inherited had only the box to apply to and is dropped (border,
    /// background, size, margin, padding, Display itself, `All`, …) —
    /// filtered by the SAME inherited-type set the live inheritance
    /// channel uses (InheritedText — one cascade table).
    private static func inheritableSubset(_ properties: [IRProperty]) -> [IRProperty] {
        InheritedText.inheritable(from: properties)
    }

    /// Splice one unboxable child into its replacement sequence (C1/C2):
    ///  - a non-empty text run becomes a synthetic carrier component (the
    ///    contents element's text is a real text node the browser keeps —
    ///    display-contents-button's "PASS") holding ONLY the inheritable
    ///    declarations, so it paints unboxed text with the element's
    ///    inherited styling and nothing else;
    ///  - then the element's own children, recursively unboxed (contents
    ///    inside contents flattens — the -details chain), each with the
    ///    element's inheritable declarations merged UNDER its own (the
    ///    tree-inheritance hop the splice would otherwise skip) and the
    ///    element's custom-property scope merged under its own
    ///    (shadowing order preserved, css-variables-1 §2).
    private static func splice(_ child: IRComponent) -> [IRComponent] {
        let inherited = inheritableSubset(child.properties)
        var out: [IRComponent] = []
        // The text run carrier — id-suffixed so debug output stays traceable.
        if let text = child.text, !text.isEmpty {
            out.append(IRComponent(
                id: "\(child.id)::contents-text", name: child.name,
                properties: inherited,
                selectors: nil, media: nil, children: nil, slot: nil,
                text: text, pseudos: nil, meta: nil))
        }
        // Grandchildren become the parent's direct children (THE grid/flex
        // item fix: §2.5 — they participate in the grandparent's
        // formatting context as if the wrapper never existed).
        for grandchild in unboxChildren(child.children) ?? [] {
            out.append(IRComponent(
                id: grandchild.id, name: grandchild.name,
                // Grandchild declarations win; the wrapper's inheritable
                // set fills the gaps (InheritedText.merge's documented
                // order — the same fold the live channel applies).
                properties: InheritedText.merge(own: grandchild.properties,
                                                inherited: inherited),
                selectors: grandchild.selectors, media: grandchild.media,
                children: grandchild.children, slot: grandchild.slot,
                text: grandchild.text, pseudos: grandchild.pseudos,
                meta: grandchild.meta,
                // Wrapper scope under the grandchild's own (element wins).
                variables: mergeVariables(outer: child.variables,
                                          own: grandchild.variables)))
        }
        return out
    }

    /// Nearest-wins merge of two custom-property scopes (own shadows outer).
    private static func mergeVariables(outer: [String: String]?,
                                       own: [String: String]?) -> [String: String]? {
        switch (outer, own) {
        case (nil, let o), (let o, nil): return o
        case (let outer?, let own?):
            // Dictionary merge with the OWN side winning collisions.
            return outer.merging(own) { _, new in new }
        }
    }

    /// Rewrite a children list, replacing every unboxable `contents`
    /// child with its spliced sequence. Unchanged input comes back as-is
    /// (value-identical) when no child unboxes; an EMPTY result
    /// normalizes to nil — the decode contract says children is nil,
    /// never [], when empty (same rule FixedHoist.strippingFixedDescendants
    /// pins), so a container whose only child was an empty contents
    /// wrapper renders exactly like a wire-decoded childless leaf.
    static func unboxChildren(_ children: [IRComponent]?) -> [IRComponent]? {
        guard let children, !children.isEmpty else { return children }
        // Fast path: no contents child at this level → input unchanged.
        guard children.contains(where: { isUnboxable($0) }) else { return children }
        let out = children.flatMap { isUnboxable($0) ? splice($0) : [$0] }
        return out.isEmpty ? nil : out
    }

    /// The renderer entry (pin C4): resolve a component about to render.
    ///  - An unboxable component ITSELF (a document ROOT — the parent-side
    ///    splice never sees roots) strips to its inheritable declarations
    ///    + text + (spliced) children: an undecorated block pass-through.
    ///    The residual block box is a documented approximation — at the
    ///    canvas root the extra block wrapper is visually inert (block in
    ///    block flow, no decoration to paint), while a true splice has no
    ///    parent list to splice into.
    ///  - Any other component gets its CHILDREN spliced (all layout paths
    ///    downstream consume the result).
    /// The contents-free corpus flows through untouched (both guards are
    /// cheap scans), keeping every committed baseline byte-stable.
    static func resolve(_ component: IRComponent) -> IRComponent {
        // Cheap change detection FIRST so the contents-free corpus (every
        // committed baseline) returns the input verbatim — no rebuild.
        let hasContentsChild = component.children?
            .contains(where: { isUnboxable($0) }) ?? false
        if isUnboxable(component) {
            // Self-strip: no box-generating declarations survive.
            return IRComponent(
                id: component.id, name: component.name,
                properties: inheritableSubset(component.properties),
                selectors: component.selectors, media: component.media,
                children: hasContentsChild
                    ? unboxChildren(component.children) : component.children,
                slot: component.slot,
                text: component.text, pseudos: component.pseudos,
                meta: component.meta, variables: component.variables)
        }
        // Children-only rewrite (withChildren copies every other field).
        guard hasContentsChild else { return component }
        return component.withChildren(unboxChildren(component.children))
    }
}
