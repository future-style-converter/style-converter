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
    // Lane BX — `box-sizing: content-box` frame inflation, pre-resolved
    // by StyleBuilder.contentBoxInflation: the padding band + border
    // widths per axis, in px. Both stay 0 unless the IR EXPLICITLY
    // declared content-box (css-sizing-3 §3: declared size = content;
    // frame = content + padding + border), so every unset/border-box
    // fixture keeps its byte-stable border-box frame.
    var contentBoxInflateH: CGFloat = 0
    var contentBoxInflateV: CGFloat = 0

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
        //
        // Wave 9 — the HEIGHT axis gains the same channel: when an
        // ancestor published a DEFINITE containing-block height
        // (SpacingContext.containingBlockHeightPx, threaded by
        // ComponentRenderer from the containingBlockHeight environment),
        // percent heights resolve against it (CSS 2.1 §10.5). When the
        // basis is nil — the root under the unbounded ScrollView, or a
        // child of an indefinite-height parent — the pre-wave-9 skip is
        // preserved (allowPercent:false → percent degrades to auto),
        // exactly the documented ScrollView rationale.
        let basisH: CGFloat? = context.containingBlockHeightPx.map { CGFloat($0) }
        return AnyView(
            SizeApplierMath.apply(content,
                                  config: config,
                                  context: context,
                                  parentW: context.containingBlockWidth,
                                  // Definite ancestor basis wins; viewport
                                  // height only ever feeds vh (percent is
                                  // gated off without a basis below).
                                  parentH: basisH ?? CGFloat(context.viewportHeight),
                                  horizontalPadding: horizontalPadding,
                                  contentBoxInflateH: contentBoxInflateH,
                                  contentBoxInflateV: contentBoxInflateV,
                                  allowPercentH: basisH != nil)
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
                                     horizontalPadding: CGFloat = 0,
                                     contentBoxInflateH: CGFloat = 0,
                                     contentBoxInflateV: CGFloat = 0,
                                     // Wave 9 — percent HEIGHTS resolve only
                                     // when the caller has a DEFINITE
                                     // containing-block height basis (CSS 2.1
                                     // §10.5); default false preserves the
                                     // historical skip for every legacy
                                     // call-site (indefinite ScrollView).
                                     allowPercentH: Bool = false) -> AnyView {
        // Resolve each axis to a concrete CGFloat (or nil for
        // unresolvable / auto / none). We split width and height lanes
        // because SwiftUI's `.frame` builder wants both as paired args.
        let w  = SizeApplierResolve.exact(c.width,  ctx: ctx, parent: parentW)
        // Height axis: CSS says `height: %` is `auto` when parent has no
        // definite height. Our parent is a ScrollView with unbounded
        // height, so percent heights are skipped UNLESS the wave-9
        // containing-block-height channel supplied a definite basis
        // (allowPercentH) — then `height: 50%` resolves against parentH,
        // which the caller set to that basis. Same rule for min/max
        // height percent constraints below.
        let h  = SizeApplierResolve.exact(c.height,
                                          ctx: ctx, parent: parentH,
                                          allowPercent: allowPercentH)
        let minW = SizeApplierResolve.constraint(c.minWidth,  ctx: ctx, parent: parentW)
        var maxW = SizeApplierResolve.constraint(c.maxWidth,  ctx: ctx, parent: parentW)
        let minH = SizeApplierResolve.constraint(c.minHeight, ctx: ctx, parent: parentH,
                                                 allowPercent: allowPercentH)
        var maxH = SizeApplierResolve.constraint(c.maxHeight, ctx: ctx, parent: parentH,
                                                 allowPercent: allowPercentH)
        // Fold `fit-content(W)` into the max-clamp so the box uses
        // ideal-size capped at W, matching CSS spec
        // (`min(max(content, min-content), W)`). Only fold when no
        // explicit max-* already constrains the axis tighter.
        if let bw = SizeApplierResolve.fitContentBound(c.width, ctx: ctx, parent: parentW) {
            maxW = maxW.map { min($0, bw) } ?? bw
        }
        if let bh = SizeApplierResolve.fitContentBound(c.height, ctx: ctx, parent: parentH,
                                                       // Wave 9 — same definite-
                                                       // basis gate as the exact
                                                       // height above.
                                                       allowPercent: allowPercentH) {
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

        // Lane BX — `box-sizing: content-box` (css-sizing-3 §3): the
        // declared width/height size the CONTENT box, so the SwiftUI
        // frame (which the iOS chain treats as the border box — padding
        // and the border-band inset both sit INSIDE it, see
        // StyleBuilder.applyStyle ordering) must grow by padding+border.
        // Runs AFTER the min/max clamp and the aspect-ratio fill because
        // both of those operate in content-box coordinates per spec;
        // only the final frame conversion changes coordinate systems.
        // Nil axes (auto / intrinsic) stay nil — content-box only
        // reinterprets DEFINITE sizes. min/max-only axes keep border-box
        // clamps for now: no fixture exercises content-box min/max, and
        // inflating the clamp without an explicit size would desync the
        // fixedSize lane below (TODO, tracked by the BX lane notes).
        effW = inflatedAxis(effW, boxSizing: c.boxSizing, by: contentBoxInflateH)
        effH = inflatedAxis(effH, boxSizing: c.boxSizing, by: contentBoxInflateV)

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

    // Lane BX — pure content-box→border-box axis conversion, split out so
    // BoxSizingTests can pin the arithmetic without a SwiftUI view tree.
    // Returns the axis untouched unless box-sizing is EXPLICITLY
    // content-box (nil = unset keeps border-box; .borderBox is the
    // declared status quo). `by` is the padding band + border widths for
    // this axis (px), pre-resolved by StyleBuilder.contentBoxInflation.
    static func inflatedAxis(_ v: CGFloat?,
                             boxSizing: BoxSizingKeyword?,
                             by inflation: CGFloat) -> CGFloat? {
        // No definite size on this axis → nothing to reinterpret
        // (css-sizing-3 §3 only changes how definite sizes resolve).
        guard let v = v else { return nil }
        // The frame grows ONLY on an explicit content-box declaration —
        // the tri-state guard that keeps every unset fixture byte-stable.
        guard boxSizing == .contentBox else { return v }
        // content-box: frame = declared content size + padding + border.
        return v + inflation
    }
}

// MARK: - min-content width emulation (fidelity wave 5)
// MinContentWidthLayout moved to its own file (MinContentWidthLayout.swift,
// same folder) when Lane BX pushed this file past the ~300-line split rule.

// Thin View extension so StyleBuilder can chain `.engineSizing(cfg, ctx)`
// without exposing the ViewModifier type at call-sites.
extension View {
    func engineSizing(_ config: SizeConfig,
                      context: SpacingContext,
                      horizontalPadding: CGFloat = 0,
                      contentBoxInflateH: CGFloat = 0,
                      contentBoxInflateV: CGFloat = 0) -> some View {
        modifier(SizeApplier(config: config, context: context,
                             horizontalPadding: horizontalPadding,
                             contentBoxInflateH: contentBoxInflateH,
                             contentBoxInflateV: contentBoxInflateV))
    }
}
