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
        // block's CONTENT width, which in the capture harness is the
        // canvas minus its 16px padding per side (390 − 32 = 358), NOT
        // the raw 390 viewport (css-sizing-3 §5.1: percentages resolve
        // against the containing block). The committed web baseline for
        // Sizing_PercentWidth measures 179px = 50% × 358 while iOS
        // rendered 195px = 50% × 390 (Sizing_C01_BlockSize right-edge
        // band). Height keeps the viewport basis but percent heights
        // are skipped anyway (allowPercent: false in the math).
        return AnyView(
            SizeApplierMath.apply(content,
                                  config: config,
                                  context: context,
                                  parentW: CGFloat(context.viewportWidth) - 32,
                                  parentH: CGFloat(context.viewportHeight))
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
                                     parentH: CGFloat) -> AnyView {
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
        let fixH = SizeApplierResolve.wantsIntrinsic(c.width)
            || (c.width == nil && (minW != nil || maxW != nil))
        let fixV = SizeApplierResolve.wantsIntrinsic(c.height)
            || (c.height == nil && (minH != nil || maxH != nil))
        if fixH || fixV {
            out = AnyView(out.fixedSize(horizontal: fixH, vertical: fixV))
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

// Thin View extension so StyleBuilder can chain `.engineSizing(cfg, ctx)`
// without exposing the ViewModifier type at call-sites.
extension View {
    func engineSizing(_ config: SizeConfig,
                      context: SpacingContext) -> some View {
        modifier(SizeApplier(config: config, context: context))
    }
}
