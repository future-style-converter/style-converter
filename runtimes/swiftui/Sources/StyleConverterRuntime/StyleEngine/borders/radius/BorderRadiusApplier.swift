//
//  BorderRadiusApplier.swift
//  StyleEngine/borders/radius — Phase 5, clip policy retro R4.
//
//  ViewModifier for the corner GEOMETRY's effect on the element's paint.
//  Two application modes, chosen by `clipsDescendants` (StyleBuilder
//  computes it through the static gate below):
//
//    • self-paint (default for a rounded box with overflow: visible) —
//      NO clip. The element's own paint is already curved by the
//      shape-aware appliers: ColorApplier fills BorderRadiusShape,
//      BorderSideApplier strokes it (fast paths B/C), BoxShadowApplier
//      and OutlineApplier take the radius too. css-backgrounds-3 §4.3
//      (Corner Clipping): "border images are not affected by
//      border-radius", and "other effects that clip painting or event
//      handling to the border, padding, or content edge must clip to
//      their respective curves" — i.e. the curve bounds the element's
//      OWN background (per background-clip) and any overflow clip it
//      establishes; DESCENDANTS are clipped only when `overflow` is not
//      visible (css-overflow-3 §3.1.2, Interaction of border-radius and
//      overflow) — so a child or an overflowing label paints in full
//      past the curve, as web and Compose (BorderRadiusApplier.kt
//      applySelfPaint, wave 41 T5) do. Only the hit-test region follows
//      the curve (`.contentShape`), honouring §4.3's event-handling
//      clause without cutting a pixel.
//
//    • legacy clip — `.clipShape(BorderRadiusShape)` on the whole
//      subtree, children included. Kept for exactly the cases where the
//      CSS paint model DOES bound painting at the curve, or where a paint
//      this engine still draws as a rectangle would lose its rounding
//      without the clip (see clipsDescendants).
//
//  History: Phase 5 shipped the clip UNCONDITIONALLY with a comment
//  claiming "matches CSS's overflow: hidden semantics" — which is the
//  bug: overflow: hidden is not the initial value. Every iOS-vs-other
//  pair on the visual-test rounded boxes (BorderRadius_Uniform / Pill /
//  Mixed, Edge_VeryLargeRadius, Edge_InsetRoundShadow) failed on
//  ~200 clipped label pixels with deltaE 0 — the label read "BORD" on
//  iOS and "BORDERRADIUS UNIFORM" on Android/web (retro A12#0, A11#7).
//
//  Geometry itself lives in `BorderRadiusShape`.
//

// SwiftUI for the ViewModifier API.
import SwiftUI

struct BorderRadiusApplier: ViewModifier {
    // Nil when no border-*-radius property appears in the IR.
    let config: BorderRadiusConfig?
    // TRUE → legacy `.clipShape` (children + every paint clipped to the
    // curve); FALSE → self-paint mode (no clip, hit-test shape only).
    // Defaults to TRUE so a caller that has not been taught the gate
    // renders byte-identically to Phase 5; StyleBuilder passes the
    // result of `clipsDescendants(...)` (retro R4 seam).
    var clipsDescendants: Bool = true

    func body(content: Content) -> some View {
        // Zero-radius: identity — avoids allocating a Shape for the
        // overwhelmingly common "no rounded corners" case.
        guard let cfg = config, cfg.hasAny else { return AnyView(content) }
        // ONE shape for both modes — the same BorderRadiusShape the fill
        // and stroke appliers use, so paint, clip and hit-test can never
        // disagree about the corner geometry.
        let shape = BorderRadiusShape(radius: cfg)
        if clipsDescendants {
            // Legacy / overflow-clip mode: SwiftUI `.clipShape` clips this
            // view's rendering, children included, and forwards hit-testing
            // through the shape — the css-overflow-3 §3 clip at the curved
            // padding edge (css-backgrounds-3 §4.3).
            return AnyView(content.clipShape(shape))
        }
        // Self-paint mode: paint nothing, clip nothing — the fill/stroke
        // appliers own the curved paint. `.contentShape` (SwiftUI: defines
        // the content shape for hit testing, does not affect drawing) keeps
        // §4.3's event rule without cutting a single pixel.
        return AnyView(content.contentShape(shape))
    }

    /// Should this element keep the descendant-clipping legacy mode?
    ///
    /// Pure truth table (XCTest-pinned in BorderRadiusClipPolicyTests),
    /// the iOS twin of Compose's `BorderRadiusApplier.selfPaintsWithoutClip`
    /// (inverted sense). TRUE exactly when dropping the clip would lose a
    /// paint this engine cannot yet round on its own, or when CSS says the
    /// children really are clipped:
    ///  - no radius → the modifier is identity either way (returns false);
    ///  - the element establishes an overflow clip (`overflow` ≠ visible
    ///    after css-overflow-3 §3.1 coercion — StyleBuilder.ownOverflowClips)
    ///    → css-backgrounds-3 §4.3 clips the content at the curved padding
    ///    edge, which is what the legacy clip renders;
    ///  - background-image layers paint → BackgroundImageApplier draws
    ///    gradient/url layers as RECTANGLES that today only look rounded
    ///    because the clip cuts their corners (Edge_GradientWithRadius);
    ///    the same documented residual Compose carries ("children under
    ///    gradient+radius stay clipped until the layer painter learns
    ///    shapes");
    ///  - the border does not paint along the shape by itself —
    ///    per-side / 3D / double / style-only borders take the straight-
    ///    edge Canvas path (BorderSideApplier.paintsAlongRoundedShape).
    /// Differences from the Compose table, both deliberate: no
    /// `hasClipInsets` bullet (ColorApplier's inset fill is still the
    /// BorderRadiusShape, so it keeps its rounding without the clip) and no
    /// `hasPartialOpacity` bullet (iOS applies `opacity` in
    /// applyGroupEffects, OUTER of the whole paint chain, so the fill can
    /// never escape the alpha group the way Compose's chain order allowed).
    static func clipsDescendants(radius: BorderRadiusConfig?,
                                 sides: AllBordersConfig?,
                                 overflowClips: Bool,
                                 hasBackgroundLayers: Bool) -> Bool {
        // No radius → nothing for either mode to do.
        guard let r = radius, r.hasAny else { return false }
        // Overflow clip requested → the children-clipping mode IS the spec.
        if overflowClips { return true }
        // Rectangular background layers would lose their rounding.
        if hasBackgroundLayers { return true }
        // Border must be self-rounding (absent, uniform solid, or the
        // uniform dashed/dotted rounded perimeter); otherwise keep the clip
        // whose corner cut is today's committed rendering.
        return !BorderSideApplier.paintsAlongRoundedShape(cfg: sides, radius: radius)
    }
}

// Public chain helper, following the `.engine*` naming convention.
extension View {
    // `clipsDescendants` defaults to the legacy clip so the un-patched
    // call shape keeps compiling and rendering unchanged; StyleBuilder
    // passes `BorderRadiusApplier.clipsDescendants(...)` (retro R4).
    func engineBorderRadius(_ config: BorderRadiusConfig?,
                            clipsDescendants: Bool = true) -> some View {
        modifier(BorderRadiusApplier(config: config, clipsDescendants: clipsDescendants))
    }
}
