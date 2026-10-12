//
//  UAPositionedHeadingMargin.swift
//  StyleEngine/spacing — wave 55 (lane L4, unit U1-ios).
//
//  The UA `margin-block` of an OUT-OF-FLOW heading, BACKLOG 0(h). Twin of
//  Compose `spacing/UaPositionedHeadingMargin.kt`.
//
//  ## The defect this closes (measured on wave54-final)
//
//  css-text-decor/text-decoration-inset-005/-006/-011/-014 each hold a
//  `position: absolute` <h1> with no author margin inside a relative <div>.
//  Chromium's html.css gives it `margin-block: .67em` of its own 2em face
//  (HTML §15.3 "Sections and headings"; css-values-4 §6.1.1): 21.44px, or
//  17.42px for 014's 26px monospace face. iOS painted the heading ~21px high,
//  because a NESTED out-of-flow box takes none of the UA margin routes:
//  MarginCollapsePlanner bails the whole container on an out-of-flow child
//  (:82–89), and ComposedRootStack / the wave-52 T6 rule serve ROOTS only.
//
//  ## Why a cascade step is enough
//
//  CSS 2.1 §10.6.4: with `top: auto` the static position is the hypothetical
//  box's top MARGIN edge, so the margin offsets the border box; §8.3.1:
//  margins of absolutely positioned boxes never collapse. The overlay child
//  (ComponentRenderer.positionedChildren) already paints a DECLARED margin
//  through StyleBuilder / MarginApplier — inset-001's extractor-baked 21.44px
//  lands within 2px of the ref — so the UA value only has to reach the merged
//  list as two ordinary longhands in the T6 leaf shape `{"px": m}`. Margins
//  are not inherited (InheritedText.inheritable), so nothing leaks down.
//
//  ## The em base: the UA-CASCADE face, not the painted one
//
//  m = uaBlockMarginEm(tag) × F, where F for an author-unsized heading is the
//  UA sheet's computed size (UAElementFontRule.sizeMultiplier × emBasePx of
//  the PRE-font-step list) — independent of the heading face gate, so the
//  margin does not move when the fold-aware face lands or reverts.
//
//  ## Scope, stated rather than discovered
//
//  Capture-gated (`enabled` = the `wptCaptureMode` environment flag, the
//  UABlockChildMargin contract): every product render and the 327-pair corpus
//  are identity. Headings only this wave; an out-of-flow p / ul / ol /
//  blockquote / pre / figure, a vertical writing mode and an author size this
//  step cannot resolve are each logged once and left alone.
//

import CoreGraphics
import Foundation

enum UAPositionedHeadingMargin {

    /// The six sectioning headings: this wave's scope (R9 queues the rest).
    private static let headings: Set<String> = ["h1", "h2", "h3", "h4", "h5", "h6"]

    /// css-writing-modes-4 §3.1 vertical modes, where `margin-block` maps to
    /// left / right (the AbsposStaticPosition set, normalised the same way).
    private static let verticalModes: Set<String> = ["VERTICAL_RL", "VERTICAL_LR", "SIDEWAYS_RL", "SIDEWAYS_LR"]

    /// Two inherited bases for the author-sized rung: a declared size that
    /// resolves differently over them is relative to the parent (em / % /
    /// larger / smaller), whose px this step does not hold.
    private static let probeBases: (CGFloat, CGFloat) = (16, 32)

    /// Breadcrumb keys already reported (the test hook reads it).
    private static var logged = Set<String>()

    /// Guards `logged`: SwiftUI may resolve styles from several passes.
    private static let lock = NSLock()

    /// Append the UA block margin of an out-of-flow heading to its merged list.
    ///
    /// - Parameters:
    ///   - sourceTag: the element's `meta.sourceTag`.
    ///   - own: the element's OWN post-reset declarations: its Position and
    ///     any author margin edge are read here.
    ///   - preUA: the merged list BEFORE the UA font step (the seam's
    ///     ListStyleUaRule output): the em base of the UA face.
    ///   - merged: the list after the UA font step, the one this step extends.
    ///   - enabled: the ambient WPT-capture flag; false is the identity.
    /// - Returns: `merged` unchanged when nothing fires, else `merged` plus
    ///   the UA MarginTop / MarginBottom on each undeclared edge.
    static func apply(sourceTag: String?,
                      own: [IRProperty],
                      preUA: [IRProperty],
                      merged: [IRProperty],
                      enabled: Bool) -> [IRProperty] {
        // Outside WPT capture no UA margin is emulated anywhere.
        guard enabled else { return merged }
        // The tag's UA block-margin factor; nil for div / span / … (fast path).
        guard let em = UABlockMarginFontBasis.uaBlockMarginEm(forTag: sourceTag) else { return merged }
        // ComponentRenderer.isOutOfFlow's read, over the post-reset own list.
        let position = LayoutExtractor.extract(from: own)?.position
        // In-flow boxes take their UA margin from the §8.3.1 planner, not here.
        guard position == .absolute || position == .fixed else { return merged }
        // Author beats UA per edge (css-cascade-4 §6.1): UABlockMargin's sets.
        let top = !UABlockMargin.declaresBlockMarginTop(own)
        // The block-end edge cascades independently of the block-start one.
        let bottom = !UABlockMargin.declaresBlockMarginBottom(own)
        // Both edges declared (the baked / declared abspos <h1>s, T6's root).
        guard top || bottom else { return merged }
        // The tag, normalised the way the UA tables compare it.
        let tag = (sourceTag ?? "").lowercased()
        // A non-heading UA-margin tag: queued (R9), so say so and leave it.
        guard headings.contains(tag) else {
            return skip("tag:\(tag)", "out-of-flow <\(tag)>: UA margin-block not emitted (headings only)", merged)
        }
        // A vertical writing mode would put margin-block on the inline axis.
        guard !isVertical(preUA) else {
            return skip("vertical", "out-of-flow <\(tag)> in a vertical writing mode: UA margin-block not emitted", merged)
        }
        // The UA-cascade face; nil = an author size this step cannot resolve.
        guard let face = uaCascadeFacePx(tag: tag, own: own, preUA: preUA) else {
            return skip("size:\(tag)", "out-of-flow <\(tag)> with an unresolvable author font-size: UA margin-block not emitted", merged)
        }
        // The UA margin in the wire's plain-px leaf shape.
        let leaf = IRValue.object(["px": .double(Double(em * face))])
        // A copy; the caller's array is never mutated.
        var out = merged
        // Block-start: the edge that offsets the static position (§10.6.4).
        if top { out.append(IRProperty(type: "MarginTop", data: leaf)) }
        // Block-end: painted as the box's own margin, nothing collapses.
        if bottom { out.append(IRProperty(type: "MarginBottom", data: leaf)) }
        // The merged list plus the undeclared edges' UA margins.
        return out
    }

    /// The element's computed font-size under the UA cascade, in px: for an
    /// author-unsized heading the UA face (sizeMultiplier × the UA font step's
    /// own em base over `preUA`), else the author's declared size when it is
    /// absolute (UABlockMarginFontBasis rungs B3 / B4). Nil for the unexpanded
    /// `Font` shorthand, var() / calc(), and any size relative to the parent.
    static func uaCascadeFacePx(tag: String, own: [IRProperty], preUA: [IRProperty]) -> CGFloat? {
        // No own size declaration: the UA font-size is the computed one.
        if !own.contains(where: { UAElementFontRule.sizeDeclaring.contains($0.type) }) {
            // The tag's UA multiplier (every heading has one).
            guard let multiplier = UAElementFontRule.sizeMultiplier(forTag: tag) else { return nil }
            // × the em base the UA font step itself resolves (inherited px, the
            // monospace 13px default, else 16): 32 for inset-005, 26 for -014.
            return CGFloat(multiplier * UAElementFontRule.emBasePx(merged: preUA))
        }
        // Only the shorthand declared: its size component is not decoded here.
        guard own.contains(where: { $0.type == "FontSize" }) else { return nil }
        // The declared size over two inherited bases (nil = var / calc / …).
        guard let a = UABlockMarginFontBasis.ownFontSizePx(own, inheritedPx: probeBases.0, sourceTag: tag),
              let b = UABlockMarginFontBasis.ownFontSizePx(own, inheritedPx: probeBases.1, sourceTag: tag)
        else { return nil }
        // Parent-relative sizes need the parent's px, which the merge dropped
        // for the own declaration: unresolvable at this step.
        return a == b ? a : nil
    }

    /// Is the element in a vertical writing mode? (WritingMode inherits, so
    /// the merged list carries the ancestor's value.)
    private static func isVertical(_ preUA: [IRProperty]) -> Bool {
        // The first WritingMode entry: own wins over inherited after the merge.
        let mode = ValueExtractors.extractKeyword(preUA.first { $0.type == "WritingMode" }?.data)
        // Normalised like AbsposStaticPosition (uppercase, '-' → '_').
        return verticalModes.contains(ValueExtractors.normalize(mode))
    }

    /// Leave `merged` untouched, reporting `message` under `key` once.
    private static func skip(_ key: String, _ message: String, _ merged: [IRProperty]) -> [IRProperty] {
        // Record the sighting under the lock (the hook reads the same set).
        lock.lock()
        let first = logged.insert(key).inserted
        lock.unlock()
        // First sighting only: one stderr breadcrumb per reason per process.
        if first { PropertyTracker.logOnce(key: "UAPositionedHeadingMargin.\(key)", message: "UAPositionedHeadingMargin: \(message)") }
        // The identity: the caller's list, unchanged.
        return merged
    }

    /// Test hook: was `key` reported? (Pins the logged-once branches.)
    static func hasLogged(_ key: String) -> Bool {
        // Same lock discipline as `skip`.
        lock.lock()
        defer { lock.unlock() }
        return logged.contains(key)
    }
}
