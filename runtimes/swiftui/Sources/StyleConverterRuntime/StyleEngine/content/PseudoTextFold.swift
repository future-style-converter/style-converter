//
//  PseudoTextFold.swift
//  StyleEngine/content — wave 42, lane W2 (the iOS pseudos seam);
//  wave 43, lane V7 (the styled-text extension).
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
//  (css-pseudo-4 §2; web's <span> inherits the same way), so an UNSTYLED
//  fold renders with precisely the style the host's text run already has.
//  Wave 43 adds the bucket's OWN styling (PseudoTextBridge → typed
//  properties): `component.text` is ONE uniform run, so a styled fold is
//  only honest when the pseudo is the component's SOLE ink — then the
//  typed entries append to the copy's property list (LAST, so they win
//  the extractors' last-wins cascade like the pseudo's own declaration
//  beating the host's inherited value) and the ordinary text pipeline
//  paints them. Any other styled shape refuses, named.
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
        // The em/% base for the pseudo's own `font-size`: css-values-4
        // §5.1.1 resolves it against the INHERITED size, and the pseudo's
        // parent IS the originating element — so the host's own declared
        // size (last FontSize wins, the extractors' cascade) or the 16px
        // document default when the host declares none (the only base the
        // wave-43 corpus produces: contain-content-011's host is bare).
        let emBasePx = component.properties.last { $0.type == "FontSize" }
            .flatMap { ValueExtractors.extractPx($0.data) }.map(Double.init) ?? 16.0
        // ::before — always foldable as the LEADING run: web renders its
        // span first, then the element's own text, whether or not real
        // children follow (NodeRenderer's positional order).
        var before = PseudoTextBridge.inlineRun(
            bucket: pseudos["before"], role: "before", emBasePx: emBasePx)
        // ::after — foldable only on a CHILDLESS component: with composed
        // children, web paints the after-span BEHIND them (text, children,
        // after), an order a leading text fold cannot express.
        var after: PseudoTextBridge.PseudoInlineRun? = nil
        // Bucket presence checked first so the refusal log fires only for
        // components that actually carry an ::after payload.
        if let afterBucket = pseudos["after"] {
            // nil/empty children = the leaf shape (never [], per the
            // IRComponent decode contract) — the safe append position.
            if component.children?.isEmpty ?? true {
                after = PseudoTextBridge.inlineRun(
                    bucket: afterBucket, role: "after", emBasePx: emBasePx)
            } else {
                // Named refusal: the trailing seam behind a child stack is
                // a renderer change this fold deliberately does not make.
                PropertyTracker.logOnce(
                    key: "pseudotext-after-children",
                    message: "[PseudoText] ::after on a component with children is not folded (it must trail the children)")
            }
        }
        // ── the wave-43 uniformity gate ─────────────────────────────────
        // `component.text` is ONE uniform run, so typed styling may only
        // apply when the styled pseudo is the SOLE ink: host text empty
        // AND the opposite side folding nothing. Judged against the
        // PRE-GATE pair so two styled sides refuse each other
        // symmetrically instead of the later one stealing the slot.
        let hostText = component.text ?? ""
        // Snapshot of the pre-gate state both checks read.
        let beforeFolds = before != nil, afterFolds = after != nil
        // A styled ::before sharing the run with host text or an ::after.
        if let b = before, !b.styling.isEmpty, !(hostText.isEmpty && !afterFolds) {
            // Named refusal — the declared style cannot be honoured
            // uniformly, and painting it in the host's style would lie.
            PropertyTracker.logOnce(
                key: "pseudotext-styled-nonuniform-before",
                message: "[PseudoText] ::before styling cannot apply — the fold is not the component's sole text run")
            before = nil
        }
        // Mirror check for a styled ::after (census: not present today).
        if let a = after, !a.styling.isEmpty, !(hostText.isEmpty && !beforeFolds) {
            PropertyTracker.logOnce(
                key: "pseudotext-styled-nonuniform-after",
                message: "[PseudoText] ::after styling cannot apply — the fold is not the component's sole text run")
            after = nil
        }
        // Nothing survived the gates → identity, so the view tree of every
        // non-foldable component is untouched.
        guard before != nil || after != nil else { return component }
        // The fold itself: before + own text + after, in CSS 2.1 §12.1
        // order. `_text` bakes its own separators ("1 " / " 1"), so plain
        // concatenation is the whole job — non-empty by construction
        // because the bridge never returns an empty string.
        let folded = (before?.text ?? "") + hostText + (after?.text ?? "")
        // The surviving styling (at most one side by the gate above) —
        // appended AFTER the host's properties so the pseudo's own
        // declarations win the extractors' last-wins cascade.
        let styling = (before?.styling ?? []) + (after?.styling ?? [])
        // Field-for-field copy with `text` rewritten and the typed styling
        // appended. `pseudos` is KEPT on the copy: ContentsUnboxing's
        // eligibility gate reads it (a pseudos-carrying component never
        // unboxes) and spec 05 rule 4 wants the wire shape re-derivable —
        // consuming is not erasing.
        return IRComponent(id: component.id, name: component.name,
                           properties: component.properties + styling,
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
