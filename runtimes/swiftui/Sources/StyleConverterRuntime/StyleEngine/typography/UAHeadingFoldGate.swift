//
//  UAHeadingFoldGate.swift
//  StyleEngine/typography — wave 54 (lane L5, unit U2-ios).
//
//  WHEN the UA heading face may apply to a heading that hosts child boxes.
//  Twin of Compose `typography/UAHeadingFoldGate.kt`.
//
//  ## The stand-down it retires (and the one it keeps)
//
//  UAElementFontRule.headingAppliesTo stood the heading half down for EVERY
//  heading with element children: in wave 40 such a heading painted as a
//  vertical STACK of labels and a 2em face only magnified the stack
//  (inset-011 0.6999 → 0.6290, inset-014 0.9317 → 0.9150, both WORSE). Its
//  own doc said "when [an inline formatting context] lands, this gate should
//  be deleted, not widened". The wave-44/45 inline-run fold IS that context
//  for the shapes it admits: on wave53-final iOS paints inset-005/-006/-014
//  as ONE folded line (sup raised, sub lowered) at the 16px face, where the
//  ref wraps a 2em bold paragraph (tools/titan/results/wave54-plan/
//  ua-heading-face.md §2). So the heading half now stands down only for a
//  heading whose content would still paint STACKED — inset-011 (its fold
//  refuses the diverging decoration colours) keeps the wave-40 verdict.
//
//  ## Why evaluating the fold on the PRE-UA list is sound (brief §4 D)
//
//  InlineRunFlow.fold's admission never reads the host's FontSize or
//  FontWeight — it reads the writing mode (:298), TextTransform (:305),
//  Hyphens (:312), FontVariantCaps (:468) and, through InlineSpanRing,
//  Color (:251) — so the verdict on the pre-UA merged list equals the
//  render-time verdict on the post-UA one (UAHeadingFoldGateTests pins it).
//
//  ## What it mirrors, and what it does not (stated, not hidden)
//
//  It calls the fold with the renderer's own arguments (ComponentRenderer
//  `contentOrPlaceholder`: the order-sorted in-flow children, the TOTAL child
//  count, the host's `meta.lang`). It does NOT mirror the body's branch
//  choice: the fold only runs in the plain block VStack, so a heading host
//  routed to the multicol / flex / float-row / inline-atom layouts would get
//  the face while painting stacked. Float and atom members are refused by
//  the fold's own member ring anyway, and every wave53-final heading host
//  with children (inset-005/-006/-011/-014) is a plain abspos block — 0
//  corpus hosts reach an unmirrored branch. The pre-UA list is also
//  pre-DynamicValueResolver: a host whose admission hinges on a var() Color
//  could disagree with the render-time fold (0 corpus hosts).
//
//  Not a silent fallthrough: a stand-down IS the fold's refusal, and
//  InlineRunFlow.fold logs every refusal once (PropertyTracker.logOnce).
//

import Foundation

enum UAHeadingFoldGate {

    /// The verdict the seam hands to `UAElementFontRule.apply`'s
    /// `hasElementChildren:` argument (which gates the heading half only).
    ///
    /// - Parameters:
    ///   - component: the element being rendered (tag, runs, children, lang).
    ///   - inFlowChildren: the renderer's in-flow children (out-of-flow
    ///     boxes removed) — the exact list the fold call site sorts.
    ///   - mergedPreUA: the inheritance-merged list BEFORE the UA font step
    ///     (the seam's ListStyleUaRule output) — the fold's container list.
    /// - Returns: false for a leaf (the face applies, the wave-40 BETTER
    ///   case); true for a non-heading with children (the old flag value —
    ///   never read, the sup/sub half is ungated); for a heading with
    ///   children, true exactly when its runs do NOT fold.
    static func standsDown(component: IRComponent,
                           inFlowChildren: [IRComponent],
                           mergedPreUA: [IRProperty]) -> Bool {
        // A leaf (no composed children) takes the face, as since wave 40.
        guard component.children?.isEmpty == false else { return false }
        // Not a heading: no heading half to gate — the old answer, and no
        // fold is run for any non-heading container.
        guard UAElementFontRule.isBold(forTag: component.meta?.sourceTag) else { return true }
        // The fold with the call site's inputs (ComponentRenderer's
        // `InlineRunFlow.fold(runs:children:totalChildCount:…)`): nil — no
        // runs, or any refusal — means the children paint stacked.
        let flow = InlineRunFlow.fold(runs: component.meta?.runs,
                                      children: FlexboxApplier.sorted(inFlowChildren),
                                      totalChildCount: component.children?.count ?? 0,
                                      containerProperties: mergedPreUA,
                                      containerLang: component.meta?.lang)
        // Folded → one paragraph → the face applies; stacked → stand down.
        return flow == nil
    }
}
