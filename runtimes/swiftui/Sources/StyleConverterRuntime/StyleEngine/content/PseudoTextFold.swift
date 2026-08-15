//
//  PseudoTextFold.swift
//  StyleEngine/content — wave 42, lane W2 (the iOS pseudos seam).
//
//  The COMPONENT half of the ordinary-element generated-content path:
//  fold the baked ::before text in as a LEADING inline run and the baked
//  ::after text as a TRAILING one, by rewriting the component's `text`
//  before the renderer reads it. CSS 2.1 §12.1 places ::before content
//  immediately before, and ::after immediately after, the element's own
//  document-tree content; web's NodeRenderer spells the same order as
//  positional children (marker, before, text, children, after —
//  runtimes/web/src/renderer/NodeRenderer.ts). Concatenating
//  before + own text + after reproduces that glyph order exactly for the
//  foldable population, and because `component.text` is the ONE channel
//  every downstream consumer reads (leaf PlaceholderLabel, the
//  mixed-content leading label, wrap-width measurement), paint and
//  measurement agree by construction — no second text channel to drift.
//
//  A pseudo inherits its typography from the originating element
//  (css-pseudo-4 §2; web's <span> inherits the same way), so a fold that
//  carries no styling declarations of its own — the PseudoTextBridge gate
//  guarantees exactly that — renders with precisely the style the host's
//  text run already has.
//

import Foundation

/// The pre-render fold: `pseudos.{before,after}._text` → `component.text`.
// enum: pure namespace — never instantiated (mirrors PseudoTextBridge).
enum PseudoTextFold {

    /// Rewrite `component` with its foldable pseudo text folded into
    /// `text`; identity (the SAME value back, field-for-field) whenever
    /// there is nothing to fold — which is every component of the
    /// committed 327-fixture baseline corpus (none carries `pseudos`),
    /// keeping all committed captures byte-stable.
    static func resolve(_ component: IRComponent) -> IRComponent {
        // No bucket at all → the overwhelmingly common identity path.
        guard let pseudos = component.pseudos else { return component }
        // The body-root's bucket belongs to the RootPseudoBox BOX path
        // (wave-28 lane PG): that family is `content:"" + width/height/
        // background`, a box not a text run, and folding here would
        // double-render anything RootPseudoBoxView paints. Same guard
        // direction as RootPseudoBox's banner ("this file must never
        // double-render either"), from the opposite shore.
        guard component.meta?.role != "body-root" else { return component }
        // `pseudos.marker` belongs to the list machinery (meta.markerText
        // and the markerPlacement loop in ComponentRenderer) — folding it
        // into `text` would double-paint against that path. Named once,
        // per the no-silent-fallthrough rule, not per component.
        if pseudos["marker"] != nil {
            PropertyTracker.logOnce(
                key: "pseudotext-marker",
                message: "[PseudoText] pseudos.marker is delegated to the list marker path — not folded")
        }
        // `meta.runs` is AUTHORITATIVE over `text` (spec 03 §4.1): when a
        // run plan resolves, the renderer drops the text slot entirely, so
        // a fold into `text` would silently vanish. Refuse and name it.
        if component.meta?.runs != nil {
            // Only worth a log line when there was something to lose.
            if pseudos["before"] != nil || pseudos["after"] != nil {
                PropertyTracker.logOnce(
                    key: "pseudotext-runs",
                    message: "[PseudoText] component carries meta.runs — pseudo text not folded (runs own the content slot)")
            }
            return component
        }
        // ::before — always foldable as the LEADING run: web renders its
        // span first, then the element's own text, whether or not real
        // children follow (NodeRenderer's positional order).
        let before = PseudoTextBridge.inlineText(bucket: pseudos["before"], role: "before")
        // ::after — foldable only on a CHILDLESS component: with composed
        // children, web paints the after-span BEHIND them (text, children,
        // after), an order a leading text fold cannot express.
        var after: String? = nil
        // Bucket presence checked first so the refusal log fires only for
        // components that actually carry an ::after payload.
        if let afterBucket = pseudos["after"] {
            // nil/empty children = the leaf shape (never [], per the
            // IRComponent decode contract) — the safe append position.
            if component.children?.isEmpty ?? true {
                after = PseudoTextBridge.inlineText(bucket: afterBucket, role: "after")
            } else {
                // Named refusal: the trailing seam behind a child stack is
                // a renderer change this fold deliberately does not make.
                PropertyTracker.logOnce(
                    key: "pseudotext-after-children",
                    message: "[PseudoText] ::after on a component with children is not folded (it must trail the children)")
            }
        }
        // Nothing survived the gates → identity, so the view tree of every
        // non-foldable component is untouched.
        guard before != nil || after != nil else { return component }
        // The fold itself: before + own text + after, in CSS 2.1 §12.1
        // order. `_text` bakes its own separators ("1 " / " 1"), so plain
        // concatenation is the whole job — non-empty by construction
        // because the bridge never returns an empty string.
        let folded = (before ?? "") + (component.text ?? "") + (after ?? "")
        // Field-for-field copy with only `text` rewritten. `pseudos` is
        // KEPT on the copy: ContentsUnboxing's eligibility gate reads it
        // (a pseudos-carrying component never unboxes) and spec 05 rule 4
        // wants the wire shape re-derivable — consuming is not erasing.
        return IRComponent(id: component.id, name: component.name,
                           properties: component.properties,
                           selectors: component.selectors,
                           media: component.media,
                           children: component.children,
                           slot: component.slot,
                           text: folded,
                           pseudos: component.pseudos,
                           meta: component.meta,
                           variables: component.variables)
    }
}
