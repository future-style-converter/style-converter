//
//  SizeApplier.swift
//  StyleEngine/sizing — Phase 3.
//
//  ViewModifier that turns a SizeConfig into a chain of SwiftUI
//  `.frame(...)` calls. Percentage lengths need the parent width, so
//  whenever any axis is percent we wrap the content in a GeometryReader.
//  All other lengths are pre-resolved through SpacingResolver so this
//  file stays focused on the SwiftUI wiring.
//
//  Approximations vs CSS:
//    * min-content / max-content → `.fixedSize(...)` on the relevant
//      axis. SwiftUI doesn't differentiate the two; both collapse to
//      the intrinsic size.
//    * fit-content(bound) → clamp with `.frame(maxW/maxH: bound)`.
//      Unbounded fit-content (no bound arrived) falls back to
//      `.fixedSize` — same heuristic as min/max-content.
//    * `auto` → no `.frame` on that axis (SwiftUI's default).
//    * `.none` on max-* → no `maxWidth/Height` attached (no clamp).
//

// SwiftUI for ViewModifier + GeometryReader + frame APIs.
import SwiftUI

// Public modifier. Attached by StyleBuilder.applyStyle via the view
// extension below. Fast-paths the no-sizing case with a single guard.
struct SizeApplier: ViewModifier {
    // The resolved sizing. When nil or empty we return `content` unchanged.
    let config: SizeConfig
    // Threaded spacing context — we reuse it so em/rem/vw all resolve
    // through the same code path as Phase 2 spacing.
    let context: SpacingContext
    // Wave 5 — resolved horizontal padding band (left + right, px).
    // Only consumed by the min-content lane: the narrow proposal must
    // reach the CONTENT, but `.padding` sits INSIDE this modifier and
    // swallows the proposal first, so the layout proposes `inset + 1`
    // to leave ~1px for the glyph run (0px makes SwiftUI text drop its
    // glyphs entirely instead of wrapping per character).
    var horizontalPadding: CGFloat = 0

    func body(content: Content) -> some View {
        // Fast path: nothing to apply.
        guard config.hasAny else { return AnyView(content) }

        // Percent axes resolve against the threaded viewport size. We
        // intentionally do NOT use GeometryReader here because GR has
        // an expand-to-fill side-effect that inflates component card
        // heights in the screenshot harness — breaking byte-stable
        // rendering on the existing baselines. Nested components would
        // need a richer context, deferred to a later phase.
        //
        // Fidelity wave 2 — the WIDTH percent basis is the containing
        // block's CONTENT width (css-sizing-3 §5.1: percentages resolve
        // against the containing block). Wave 3 upgraded the basis from
        // the fixed canvas content width (390 − 32 = 358) to the value
        // threaded through SpacingContext by ComponentRenderer's
        // containing-block channel: for NESTED components this is the
        // parent's content-box width (trees/block-flow B_Stack —
        // `width: 75%` of a 280px/10px-padding parent is 195px, not
        // 75% × 358), and for root components / children of
        // indefinite-width parents it falls back to the same 358 canvas
        // basis as before (see SpacingContext.containingBlockWidth).
        // Height keeps the viewport basis but percent heights are
        // skipped anyway (allowPercent: false in the math).
        return AnyView(
            SizeApplierMath.apply(content,
                                  config: config,
                                  context: context,
                                  parentW: context.containingBlockWidth,
                                  parentH: CGFloat(context.viewportHeight),
                                  horizontalPadding: horizontalPadding)
        )
    }
}

// Helpers live in an enum to keep `SizeApplier.body` concise and to let
// the math be unit-tested without constructing a SwiftUI view tree.
enum SizeApplierMath {

    // Apply every sizing axis. Returns an AnyView so the caller can
    // embed us in either the GeometryReader branch or the fast path.
    static func apply<Content: View>(_ content: Content,
                                     config c: SizeConfig,
                                     context ctx: SpacingContext,
                                     parentW: CGFloat,
                                     parentH: CGFloat,
                                     horizontalPadding: CGFloat = 0) -> AnyView {
        // Resolve each axis to a concrete CGFloat (or nil for
        // unresolvable / auto / none). We split width and height lanes
        // because SwiftUI's `.frame` builder wants both as paired args.
        let w  = SizeApplierResolve.exact(c.width,  ctx: ctx, parent: parentW)
        // Height axis: CSS says `height: %` is `auto` when parent has no
        // definite height. Our parent is a ScrollView with unbounded
        // height, so we treat percent-heights as "skip" — matches the
        // pre-Phase-3 rendering and the CSS fallback. Same for min/max
        // height percent constraints.
        let h  = SizeApplierResolve.exact(c.height,
                                          ctx: ctx, parent: parentH,
                                          allowPercent: false)
        let minW = SizeApplierResolve.constraint(c.minWidth,  ctx: ctx, parent: parentW)
        var maxW = SizeApplierResolve.constraint(c.maxWidth,  ctx: ctx, parent: parentW)
        let minH = SizeApplierResolve.constraint(c.minHeight, ctx: ctx, parent: parentH,
                                                 allowPercent: false)
        var maxH = SizeApplierResolve.constraint(c.maxHeight, ctx: ctx, parent: parentH,
                                                 allowPercent: false)
        // Fold `fit-content(W)` into the max-clamp so the box uses
        // ideal-size capped at W, matching CSS spec
        // (`min(max(content, min-content), W)`). Only fold when no
        // explicit max-* already constrains the axis tighter.
        if let bw = SizeApplierResolve.fitContentBound(c.width, ctx: ctx, parent: parentW) {
            maxW = maxW.map { min($0, bw) } ?? bw
        }
        if let bh = SizeApplierResolve.fitContentBound(c.height, ctx: ctx, parent: parentH,
                                                       allowPercent: false) {
            maxH = maxH.map { min($0, bh) } ?? bh
        }

        // Wave 5 — css-sizing-3 §5.2: when min > max, the MIN wins (the
        // max is clamped up to it). SwiftUI's `.frame(minHeight: 80,
        // maxHeight: 50)` is an INVALID frame (runtime warning,
        // undefined layout) — PW_Sizing_Spacing_02 (`min-block-size:
        // 80px; max-block-size: 50px`) collapsed to the raw content
        // height instead of the CSS-resolved 80px.
        if let mn = minW, let mx = maxW, mx < mn { maxW = mn }
        if let mn = minH, let mx = maxH, mx < mn { maxH = mn }

        // Start unmodified and layer modifiers in CSS order: first the
        // min/max clamps (only attach if present), then exact width/height,
        // then aspect-ratio. `.frame(alignment:.topLeading)` matches the
        // CSS block-model origin — important for visual parity.
        var out = AnyView(content)

        // Min/max clamp. Skip attaching if everything is nil so we don't
        // produce `.frame()` with all-nil parameters.
        if minW != nil || maxW != nil || minH != nil || maxH != nil {
            out = AnyView(out.frame(minWidth: minW, maxWidth: maxW,
                                    minHeight: minH, maxHeight: maxH,
                                    alignment: .topLeading))
        }

        // Resolve aspect-ratio assistance BEFORE we attach the frame.
        // SwiftUI's `.aspectRatio(_:contentMode:.fit)` modifier — when
        // applied OUTSIDE a `.frame(width: 150, height: nil)` — does not
        // synthesise the missing height from the ratio; it instead
        // computes a fitting box around the child's intrinsic size.
        // For text content with intrinsic height ≈ 22pt, this collapsed
        // 002_Sizing_AspectRatio (`width: 150; aspect-ratio: 16/9`) to a
        // 150×~22 strip on iOS while Android+Web rendered the expected
        // 150×84 box (`84 = 150 * 9 / 16`). Compute the missing axis
        // arithmetically when we have one fixed dimension + a non-auto
        // ratio, and feed the resolved pair to `.frame` directly. The
        // outer `.aspectRatio` modifier below stays as a fallback for the
        // "no fixed dimension" case (lets SwiftUI scale around the
        // intrinsic content while preserving ratio).
        var effW = w
        var effH = h
        // CSS clamp: `effective = max(min, min(width, max))`. The min/max
        // .frame above only constrains relative to the parent's proposal
        // — it does NOT clamp our own explicit width/height. So when CSS
        // declared `width: 300; max-width: 50`, the exact .frame below
        // would otherwise pin us to 300 and overrule the max. Apply the
        // clamp here so the second .frame() respects the bounds.
        if let mw = maxW, let cw = effW { effW = min(cw, mw) }
        if let mh = maxH, let ch = effH { effH = min(ch, mh) }
        if let mnw = minW, let cw = effW { effW = max(cw, mnw) }
        if let mnh = minH, let ch = effH { effH = max(ch, mnh) }
        // CSS `aspect-ratio: auto W/H` means "use natural ratio if the
        // element has one (replaced elements like <img>), otherwise W/H".
        // Placeholder views have no intrinsic ratio so the W/H fallback
        // always applies — gate on `ratio > 0`, not on `!isAuto`.
        if let ar = c.aspectRatio, ar.ratio > 0 {
            // CSS aspect-ratio is width / height, so height = width / ratio
            // and width = height * ratio. Only fill in the MISSING axis —
            // never override an explicit width/height from the cascade.
            if effW != nil && effH == nil {
                effH = effW! / CGFloat(ar.ratio)
            } else if effH != nil && effW == nil {
                effW = effH! * CGFloat(ar.ratio)
            }
        }

        // Exact width/height. Only attach when at least one is resolved.
        if effW != nil || effH != nil {
            out = AnyView(out.frame(width: effW, height: effH, alignment: .topLeading))
        }

        // ── fixedSize (outermost sizing modifier) ────────────────────────
        // CSS treats `max-width: 300px` as a CAP on the natural content
        // width — content under 300 stays at its natural size. SwiftUI's
        // flex `.frame(maxWidth: 300)` is EXPANDING: when the parent
        // proposes a larger size, the frame greedily expands all the way
        // to maxWidth. Sizing_MinMax (`min-width: 100; max-width: 300`)
        // rendered as 300 wide on iOS while web/Android measured 100/113
        // (the natural-width-then-clamped-from-below case).
        //
        // The fix: wrap the flex frame in `.fixedSize(...)` so the frame
        // uses its IDEAL size instead of the parent proposal. Apple's
        // FrameLayout docs: "When the ideal width / height parameters
        // are nil, the ideal size is the child's ideal size, clamped
        // between min and max." So the resulting frame size becomes
        // `clamp(content_ideal, minWidth, maxWidth)` — exactly the CSS
        // semantics. fixedSize must be the OUTERMOST sizing modifier so
        // the bg / border / shadow appliers further down the chain paint
        // on the clamped-ideal box, not the flex frame's expanded bounds.
        //
        // Conditions: (a) the original `wantsIntrinsic` case for
        // min-content / max-content / fit-content keywords; OR (b) the
        // axis has no exact width but does have min/max bounds (the new
        // CSS-cap case). Pure exact-width axes don't need fixedSize.
        // Wave 5: `width: min-content` is NOT the ideal size — it's the
        // narrowest wrap (css-sizing-3 §4). `.fixedSize` reports the
        // single-line MAX-content width for text, so min-content boxes
        // rendered one wide line while web wrapped at every word
        // (PW_Sizing_Spacing_02). MinContentWidthLayout below proposes
        // width 0 instead; exclude the keyword from the fixedSize lane.
        let widthIsMin = SizeApplierResolve.isMinContent(c.width)
        let fixH = (SizeApplierResolve.wantsIntrinsic(c.width) && !widthIsMin)
            || (c.width == nil && (minW != nil || maxW != nil))
        let fixV = SizeApplierResolve.wantsIntrinsic(c.height)
            || (c.height == nil && (minH != nil || maxH != nil))
        if fixH || fixV {
            out = AnyView(out.fixedSize(horizontal: fixH, vertical: fixV))
        }
        // min-content emulation — a zero-width proposal makes SwiftUI
        // text wrap at every opportunity and report the longest-word
        // width, exactly the CSS min-content measure.
        if widthIsMin {
            out = AnyView(MinContentWidthLayout(inset: horizontalPadding) {
                out
            })
        }

        // AspectRatio last — aspectRatio reinterprets any remaining
        // degree of freedom in the frame. When `auto`, we skip so
        // SwiftUI uses the natural ratio. We also skip when the
        // arithmetic above already resolved BOTH axes from the ratio
        // (i.e. `effW != nil && effH != nil`) — re-applying the
        // SwiftUI modifier on a fully-sized frame just adds noise.
        let bothResolved = effW != nil && effH != nil
        // Same `auto W/H` logic as above — apply the ratio whenever it's
        // usable, regardless of `isAuto`. The bothResolved guard skips
        // double-application when the explicit-axis path above already
        // computed both dimensions.
        if let ar = c.aspectRatio, ar.ratio > 0, !bothResolved {
            out = AnyView(out.aspectRatio(ar.ratio, contentMode: .fit))
        }
        return out
    }
}

// MARK: - min-content width emulation (fidelity wave 5)

/// Proposes width 0 to its single child and adopts whatever size the
/// child reports back — SwiftUI text under a zero-width proposal wraps
/// at every soft-wrap opportunity and measures its longest word, which
/// IS the CSS min-content inline size (css-sizing-3 §4). `.fixedSize`
/// can't express this (it reports the IDEAL = max-content size), so
/// `width: min-content` boxes rendered a single wide line on iOS while
/// web/Android wrapped (PW_Sizing_Spacing_02, i-w 0.703).
struct MinContentWidthLayout: Layout {
    /// Horizontal padding band inside the wrapped chain — the proposal
    /// must survive `.padding`'s subtraction so ~1px reaches the text.
    var inset: CGFloat = 0

    /// The narrowest proposal that still renders glyphs: the padding
    /// band plus one pixel for the character column.
    private var probe: CGFloat { inset + 1 }

    /// Report the child's size under the narrow proposal. Height still
    /// follows the container's proposal so explicit heights and
    /// min/max clamps inside the child chain behave unchanged.
    func sizeThatFits(proposal: ProposedViewSize,
                      subviews: Subviews, cache: inout ()) -> CGSize {
        guard let sub = subviews.first else { return .zero }
        return sub.sizeThatFits(ProposedViewSize(width: probe,
                                                 height: proposal.height))
    }

    /// Place the child at the size it measured under the narrow
    /// proposal (re-proposing the CONCRETE measured size so the wrap
    /// layout is reproduced at render time), anchored top-leading like
    /// every other block box.
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize,
                       subviews: Subviews, cache: inout ()) {
        guard let sub = subviews.first else { return }
        let sz = sub.sizeThatFits(ProposedViewSize(width: probe,
                                                   height: proposal.height))
        sub.place(at: bounds.origin, anchor: .topLeading,
                  proposal: ProposedViewSize(sz))
    }
}

// Thin View extension so StyleBuilder can chain `.engineSizing(cfg, ctx)`
// without exposing the ViewModifier type at call-sites.
extension View {
    func engineSizing(_ config: SizeConfig,
                      context: SpacingContext,
                      horizontalPadding: CGFloat = 0) -> some View {
        modifier(SizeApplier(config: config, context: context,
                             horizontalPadding: horizontalPadding))
    }
}
