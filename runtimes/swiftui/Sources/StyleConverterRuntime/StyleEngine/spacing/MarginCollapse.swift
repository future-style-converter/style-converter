//
//  MarginCollapse.swift
//  StyleEngine/spacing — Lane IOS-COLLAPSE (CSS 2.1 §8.3.1 emulation).
//
//  SwiftUI has no margin collapsing: MarginApplier paints each child's
//  margin as OUTER `.padding()`, so two adjacent 20px block margins
//  produced a 40px gap where every browser renders 20px (§8.3.1: the
//  used gap between adjoining vertical margins is the LARGER margin).
//  The measured web geometry this lane matches: three 100×40 bars with
//  `margin: 20px` inside an unpadded 300px block parent → parent box
//  300×160 with bars at y 20/80/140 inside the hoisted outer region.
//
//  Emulation shape (mirrors the sibling web/Android lanes):
//    • The PARENT statically folds its in-flow children's vertical
//      margins into a Plan: interior gaps collapse to max(bottomᵢ, topᵢ₊₁)
//      and ride the FOLLOWING child's top margin; the first-top / last-
//      bottom margins HOIST through an unpadded, unbordered parent as
//      transparent outer bands the parent's background never paints.
//    • Per-child overrides thread parent→child via the SwiftUI
//      environment (the same channel pattern as containingBlockWidth);
//      the child folds them into its MarginConfig before the style chain.
//    • POSITIVE px margins only. Negative / auto / relative(em, %, …) /
//      calc margins bail the WHOLE container back to the legacy sum
//      behaviour with a PropertyTracker breadcrumb — never a guess.
//    • Gated OFF for flex/grid containers (css-flexbox-1 §4 / css-grid-1
//      §6: item margins never collapse) — table display doesn't exist in
//      DisplayKeyword, so the block-path gate covers it.
//
//  Everything below is pure (component + style in, plan out) so
//  MarginCollapseTests pins the math and gates device-free — the same
//  pattern as UABlockMargin / ComponentRenderer.isOutOfFlow.
//

// SwiftUI for the EnvironmentKey; CoreGraphics CGFloat rides along.
import SwiftUI

/// The parent-computed collapsed vertical margins for ONE child: what the
/// child's MarginApplier should paint INSTEAD of its declared top/bottom.
struct MarginCollapseOverride: Equatable {
    /// Used top margin (0 for a hoisted first child; the collapsed
    /// max(prev.bottom, own.top) for interior children).
    let top: CGFloat
    /// Used bottom margin (interior children carry 0 — the gap below
    /// them rides the NEXT child's collapsed top; see Plan docs).
    let bottom: CGFloat
}

/// Environment key threading the override parent → child. Default nil =
/// "no collapse plan applies" (root components, non-block parents).
private struct MarginCollapseOverrideKey: EnvironmentKey {
    static let defaultValue: MarginCollapseOverride? = nil
}

extension EnvironmentValues {
    /// The collapse override the direct parent computed for this child,
    /// or nil when the child's declared margins apply untouched. ALWAYS
    /// rewritten (value or nil) at every tree level so a grandparent's
    /// plan can never leak past its own children (same reset discipline
    /// as containingBlockWidth / gridStretchHeight).
    var marginCollapseOverride: MarginCollapseOverride? {
        get { self[MarginCollapseOverrideKey.self] }
        set { self[MarginCollapseOverrideKey.self] = newValue }
    }
}

/// Wave 26 (lane RES residual 3a) — HOIST-BAND suppression channel for the
/// composed capture's ROOT slots. Twin of Compose's
/// `BlockMarginCollapse.LocalHoistBandSuppressedFor`.
///
/// ## The double count this closes
/// A composed root's outer block spacing had TWO owners: the harness's
/// root-stack fold (`ComposedRootStack.stackedSpacing`, painted as the
/// per-root leading/trailing padding) and the root's OWN plan band
/// (`Plan.hoistTop/hoistBottom`, painted as transparent padding outside its
/// styled box). Both are outer spacing in the SAME adjoining-margin region,
/// so they ADDED where CSS 2.1 §8.3.1 takes ONE max() over the whole chain:
/// with a previous root ending in a 40px bottom margin, a `<blockquote>`
/// root (UA 16) whose first child is an `<h5>` (UA 27) rendered
/// max(40,16) = 40 plus max(16,27) − 16 = 11 → 51px against the browser's
/// max(40, 16, 27) = 40.
///
/// ## Why an id and not a Bool
/// The suppression must cover exactly ONE level. A Bool would need an
/// explicit reset on every child-rendering path and one miss silently
/// deletes a descendant's band; keying on the flagged component's `id` is
/// leak-proof by construction, because the extractor's ids are hierarchical
/// (`<root>__<i>` — tools/titan/extract-fixture.mjs) so a descendant's id can
/// never EQUAL its ancestor's. Default nil ⇒ nothing suppressed on any
/// non-composed path, so the 327 dark-stage baselines are byte-identical.
private struct HoistBandSuppressedForKey: EnvironmentKey {
    static let defaultValue: String? = nil
}

extension EnvironmentValues {
    /// The `id` of the single component whose hoist band the host owns, or
    /// nil (the default, and the value everywhere outside the composed root
    /// loop). See HoistBandSuppressedForKey for the full argument.
    var hoistBandSuppressedFor: String? {
        get { self[HoistBandSuppressedForKey.self] }
        set { self[HoistBandSuppressedForKey.self] = newValue }
    }
}

// Pure namespace — never instantiated (mirrors StyleBuilder et al.).
enum MarginCollapse {

    /// A block container's full collapse plan: one override per SORTED
    /// in-flow child (index-aligned with the renderer's ForEach) plus
    /// the two hoisted bands the parent must add as transparent outer
    /// spacing (§8.3.1 parent↔first/last-child collapse-through).
    struct Plan: Equatable {
        /// Per-child used vertical margins, in render (CSS-order) order.
        var overrides: [MarginCollapseOverride]
        /// First child's top margin when it collapses THROUGH the parent
        /// (0 when the parent's top edge blocks the hoist).
        var hoistTop: CGFloat
        /// Last child's bottom margin when it collapses through (0 when
        /// blocked by padding/border/definite height).
        var hoistBottom: CGFloat
    }

    // MARK: - Static value classification

    /// A margin edge the static fold can reason about: a NON-NEGATIVE
    /// absolute pixel value. Everything else — negative px (§8.3.1's
    /// deduct-from-max rule is not emulated), `auto` (alignment, not
    /// pixels), em/rem/%/viewport (need the CHILD's resolved context the
    /// parent doesn't have), calc/unknown — returns nil ⇒ ineligible.
    static func staticEdge(_ v: LengthValue) -> CGFloat? {
        // `.exact` is the converter's absolute-px wire (`{"px":20.0}`).
        if case .exact(let px) = v, px >= 0 { return CGFloat(px) }
        // Any other flavor: not statically collapsible.
        return nil
    }

    /// The (top, bottom) static margins of a child's property list, or
    /// nil when either vertical edge is not a static non-negative px.
    /// Horizontal edges are IGNORED — only vertical margins collapse in
    /// horizontal writing mode (§8.3.1 "vertical margins"), so a child
    /// with `margin-left: auto` stays eligible.
    static func staticVerticalEdges(_ properties: [IRProperty]) -> (top: CGFloat, bottom: CGFloat)? {
        // No margin longhand at all ⇒ both edges are the initial 0
        // (css-box-3 §3: margin initial value is 0) — fully eligible.
        guard let cfg = MarginExtractor.extract(from: properties) else { return (0, 0) }
        // Each vertical edge must classify; one dynamic edge bails both.
        guard let t = staticEdge(cfg.top), let b = staticEdge(cfg.bottom) else { return nil }
        return (t, b)
    }

    // MARK: - The §8.3.1 fold

    /// Fold per-child (top, bottom) margins — in render order — into the
    /// Plan. Interior adjacency: the gap between child i and i+1 is
    /// max(bottomᵢ, topᵢ₊₁) (§8.3.1 positive-margin rule), carried
    /// ENTIRELY by child i+1's used top (child i's used bottom = 0) so
    /// the spacing-0 VStack renders exactly one collapsed gap.
    ///
    /// `parentOwn` is the parent's own DECLARED (pre-override) block-axis
    /// margins: the hoist bands are COMPOSED against them here (the shared
    /// HOIST BAND MATH — band = max(parentOwn, childEdge) − parentOwn),
    /// so the Plan carries the FINAL transparent band the renderer paints.
    /// The parent's MarginApplier already renders `parentOwn`, so only the
    /// excess escapes. Reading declared margins is the fix for the iOS
    /// double-count: the caller must NOT pass the override-folded own
    /// margin (an outer collapse could have zeroed the parent's own top,
    /// which would make the band the full child edge instead of the excess).
    static func fold(edges: [(top: CGFloat, bottom: CGFloat)],
                     parentOwn: (top: CGFloat, bottom: CGFloat),
                     hoistTopAllowed: Bool,
                     hoistBottomAllowed: Bool) -> Plan {
        // Build one override per child, walking render order.
        var overrides: [MarginCollapseOverride] = []
        for (i, e) in edges.enumerated() {
            // Used top: first child either hoists (margin moves OUTSIDE
            // the parent → 0 inside) or keeps its full declared top
            // (a padded/bordered parent contains it, §8.3.1).
            let top: CGFloat = i == 0
                ? (hoistTopAllowed ? 0 : e.top)
                // Interior: the single collapsed gap with the previous
                // sibling's bottom margin (max of the two, §8.3.1).
                : max(edges[i - 1].bottom, e.top)
            // Used bottom: interior children carry 0 (gap rides the next
            // child's top); the last child hoists or keeps its declared
            // bottom, mirroring the top-edge logic.
            let bottom: CGFloat = i == edges.count - 1
                ? (hoistBottomAllowed ? 0 : e.bottom)
                : 0
            overrides.append(MarginCollapseOverride(top: top, bottom: bottom))
        }
        // Hoisted bands: the first-top / last-bottom child margin composed
        // with the parent's own declared margin (max rule, own share
        // subtracted) when the edge gate lets it collapse through (else 0).
        return Plan(overrides: overrides,
                    hoistTop: hoistTopAllowed
                        ? hoistBand(childEdge: edges.first?.top ?? 0, parentOwnEdge: parentOwn.top)
                        : 0,
                    hoistBottom: hoistBottomAllowed
                        ? hoistBand(childEdge: edges.last?.bottom ?? 0, parentOwnEdge: parentOwn.bottom)
                        : 0)
    }

    // MARK: - Parent-edge hoist gates

    /// May a first-top (topEdge) / last-bottom (!topEdge) child margin
    /// collapse THROUGH this parent? The unified collapse gate contract's
    /// PARENT HOIST GATES (§8.3.1's adjoining conditions), implemented
    /// byte-for-byte with the Compose BlockMarginCollapse.hoistGates:
    ///   G1 padding: a declared 0 keeps the gate OPEN, any positive px
    ///      closes it (value-based — the live wire emits {"px":0.0}).
    ///   G2 border: a border edge whose used width resolves to 0 keeps the
    ///      gate OPEN; positive width closes; a visible border-style with
    ///      NO width closes (initial medium = 3px); BorderImage closes;
    ///      Radius does NOT close.
    ///   G3 overflow: EITHER axis non-visible closes BOTH gates (a BFC
    ///      root's margins never collapse with its children — CSS 2.1
    ///      §9.4.1; css-overflow-3 §2.1 computes the sibling axis to auto).
    ///   G4 position: an absolutely/fixed-positioned parent closes BOTH
    ///      gates (out of flow, establishes its own context).
    ///   G5 bottom only: Height / BlockSize / MinHeight / MinBlockSize /
    ///      AspectRatio pin the bottom edge (§8.3.1 needs computed
    ///      `height: auto`); MaxHeight / MaxBlockSize do NOT close it.
    static func hoistAllowed(style: ComponentStyle, topEdge: Bool) -> Bool {
        // G2 — a painted border on the edge blocks adjacency (§8.3.1).
        // BorderSideConfig.hasBorder is already value-based: explicit 0
        // width → false (gate stays open, `border: 0` still collapses);
        // a visible style without a width upgrades to medium=3px → true.
        if let b = style.borderSides, (topEdge ? b.top : b.bottom).hasBorder { return false }
        // G2 — a border-image occupies the border area (css-backgrounds-3
        // §6) even without a border-style, so ANY BorderImage* declaration
        // (the extractor returns non-nil only when one is present) closes
        // both gates conservatively. Radius never reaches borderImage.
        if style.borderImage != nil { return false }
        // G1 — any non-zero padding on the edge blocks adjacency. Resolve
        // via the exact lane PaddingApplier paints with, so a declared 0
        // (.px(0)) keeps the gate OPEN while any positive px (or an
        // unresolvable em/%/calc edge) conservatively closes it.
        if let p = style.spacing.padding,
           SpacingResolver.resolve(topEdge ? p.top : p.bottom,
                                   ctx: style.spacing.context,
                                   isPadding: true) != .px(0) { return false }
        // G3 — BFC root: overflow other than visible on EITHER axis
        // (CSS 2.1 §9.4.1). css-overflow-3 §2.1 computes `overflow-x:
        // hidden` to `overflow-y: auto`, so one non-visible axis already
        // implies a BFC — check both (the old code missed overflow-x).
        if let ox = style.visibility?.overflowX, ox != .visible { return false }
        if let oy = style.visibility?.overflowY, oy != .visible { return false }
        // G4 — out-of-flow parents establish their own context — no
        // collapse through (css-position-3 §2: absolute/fixed are out of flow).
        if let pos = style.layout7?.position, pos == .absolute || pos == .fixed { return false }
        // G5 — bottom edge only: a definite height (or min-height, or an
        // aspect-ratio that derives one — css-sizing-4 §4.2) separates the
        // last child's bottom margin from the parent's bottom edge
        // (§8.3.1 requires computed `height: auto` + zero min-height).
        // SizeConfig resolves BlockSize→height and MinBlockSize→minHeight
        // at extract time (logical→physical), so the logical longhands are
        // covered here too. MaxHeight/MaxBlockSize are deliberately NOT in
        // the set (contract G5): max-height leaves the used height auto.
        if !topEdge, style.size.height != nil || style.size.minHeight != nil
            || style.size.aspectRatio != nil { return false }
        // All adjoining conditions hold — the margin collapses through.
        return true
    }

    /// The transparent outer band the parent adds for one hoisted edge,
    /// COMPOSED with the parent's own DECLARED margin on that edge:
    /// §8.3.1 collapses parent+child margins to max(own, child), and the
    /// parent's MarginApplier already paints `own`, so the extra band is
    /// max(own, child) − own. `fold` calls this with the parent's declared
    /// (pre-override) own margin — the container plan bails (B7) before
    /// this point when that margin is non-static, so the nil branch is
    /// only exercised by the standalone unit pin (defaults to the full
    /// child band, the historical sum approximation).
    static func hoistBand(childEdge: CGFloat, parentOwnEdge: CGFloat?) -> CGFloat {
        // Static own margin: the exact §8.3.1 max-composition.
        if let own = parentOwnEdge { return max(childEdge, own) - own }
        // No own margin supplied: full child band (defensive fallback).
        return childEdge
    }

    /// Wave 26 (lane RES residual 3a) — pure predicate for the
    /// `hoistBandSuppressedFor` channel: true iff `componentId` is exactly
    /// the id the host flagged. Byte-parallel twin of Compose's
    /// `BlockMarginCollapse.suppressesHoistBand`.
    static func suppressesHoistBand(suppressedForId: String?,
                                    componentId: String) -> Bool {
        // nil channel (every non-composed path) suppresses nothing.
        guard let flagged = suppressedForId else { return false }
        // Exact id match — hierarchical ids make descendants un-matchable.
        return flagged == componentId
    }

    // MARK: - Override application (child side)

    /// Fold a parent-sent override into the child's extracted margin:
    /// the used top/bottom replace the declared ones; left/right (and
    /// their auto-alignment semantics) pass through untouched. Nil
    /// override = identity, so override-free renders are byte-identical.
    static func applying(_ override: MarginCollapseOverride?,
                         to margin: MarginConfig?) -> MarginConfig? {
        // No plan for this child → declared margins apply as-is.
        guard let ov = override else { return margin }
        // A margin-less child can still RECEIVE a collapsed gap (the
        // previous sibling's bottom margin rides its top) — start from
        // the zero config in that case.
        var m = margin ?? MarginConfig()
        // Replace both vertical edges with the parent-computed used
        // values (eligibility guaranteed they were static px anyway).
        m.top = .exact(px: Double(ov.top))
        m.bottom = .exact(px: Double(ov.bottom))
        return m
    }
}
