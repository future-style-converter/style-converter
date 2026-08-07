//
//  BackdropApplier.swift
//  StyleEngine/effects/backdrop — lane BF-I.
//
//  The view-tree half of the two-pass backdrop. FilterApplier attaches this
//  for any element whose `backdrop-filter` chain is non-empty; what it does
//  depends entirely on the ambient `backdropPass` (BackdropPass.swift):
//
//    .disabled      → IDENTITY. The element renders exactly as it did
//                     before this lane existed. This is what the bundled
//                     327-pair capture path and every SDUI app see, because
//                     nothing on those paths publishes the environment key.
//    .sampling      → PASS A: the element's own paint (and its whole
//                     subtree, which paints above it) is suppressed with
//                     `.opacity(0)`, which removes the ink WITHOUT touching
//                     layout — the two passes must lay out identically or
//                     the pass-B crop would sample the wrong pixels.
//    .compositing   → PASS B: crop the plate to this element's border box,
//                     run the planned ops, draw the result UNDER the
//                     element (a `.background`, so the element's own
//                     background/border/content still paint on top, exactly
//                     like a CSS backdrop).
//
//  Attached inside the effects chain at the `filter` slot, so the backplate
//  sits behind everything the element painted and still rides the
//  element's clip-path/transform/opacity modifiers applied after it.
//

// SwiftUI for the modifier; the ops/plate types are module-internal.
import SwiftUI

struct BackdropApplier: ViewModifier {
    /// The executable program for this element (BackdropPlan.plan).
    let plan: BackdropPlan
    /// The element's border radius, used to clip the backplate to the same
    /// rounded border box the element's own background is clipped to
    /// (filter-effects-2 §2: the backdrop image is clipped to the element's
    /// border box, corners included). nil / all-square → no clip needed,
    /// since the crop is already sized to the box.
    let radius: BorderRadiusConfig?
    /// The element's CSS opacity, applied to the backplate so it fades with
    /// the element (the `.background` sits outside `.engineOpacity` in the
    /// StyleBuilder chain — see FilterApplier.elementOpacity). 1 = opaque.
    var elementOpacity: Double = 1

    /// The ambient pass. Default `.disabled` — see BackdropPass.swift.
    @Environment(\.backdropPass) private var pass

    func body(content: Content) -> some View {
        // Disclosure first: a chain this lane cannot fully execute is
        // announced once, whatever pass we are in, so a capture log never
        // implies a complete render (CLAUDE.md: no silent fallthroughs).
        BackdropLog.reportOnce(plan)
        // An identity plan (empty chain, `none`, invert(0), blur(0), or a
        // chain that is entirely out of scope) must not suppress paint in
        // pass A either: this element is still part of OTHER elements'
        // backdrops, and removing it from the plate would corrupt theirs.
        guard !plan.isIdentity else { return AnyView(content) }
        switch pass {
        case .disabled:
            // Pre-lane behaviour, byte-for-byte.
            return AnyView(content)
        case .sampling:
            // Paint suppressed, layout preserved. `.opacity(0)` rather than
            // `.hidden()` because `.hidden()` also drops the element from
            // layout on some containers, which would move every following
            // box and invalidate the whole plate.
            return AnyView(content.opacity(0))
        case .compositing(let plate):
            return AnyView(content.background {
                // The element's own paint fades via .engineOpacity; the
                // backplate must fade WITH it (filter-effects-2 §2 — the
                // filtered backdrop composites as part of the element).
                backplate(plate).opacity(elementOpacity)
            })
        }
    }

    /// The filtered crop, positioned in the element's own coordinate space.
    @ViewBuilder
    private func backplate(_ plate: BackdropPlate) -> some View {
        GeometryReader { geo in
            // The element's border box IN THE PLATE'S OWN SPACE — the
            // canvas coordinate space stamped by `backdropCanvasRoot()`.
            // `.background` content is laid out in the element's frame, so
            // this frame is the element's border box. (Unlike Compose, the
            // margin modifier is attached OUTSIDE this one in StyleBuilder's
            // chain, so no margin band has to be subtracted here.)
            let box = geo.frame(in: .named(BackdropCanvas.spaceName))
            // Crop the BORDER BOX → filter → keep. No σ term: filter-effects-2
            // §2 clips the backdrop to the border box before filtering, and
            // BackdropBlur's mirror band supplies whatever the Gaussian wants
            // beyond it (see BackdropSampleGeometry's header for the wave-34
            // measurement). Any step returning nil (off-canvas element,
            // CoreGraphics/CoreImage refusing the buffer) means NO backplate is
            // drawn, i.e. the pre-lane rendering — never a partial one.
            if let crop = BackdropImageOps.crop(plate, elementFrame: box),
               let filtered = BackdropImageOps.filtered(crop,
                                                        ops: plan.ops,
                                                        scale: plate.scale) {
                // `decorative:` — no accessibility label: this is a
                // re-render of pixels already on screen, not new content.
                // The plate scale makes the CGImage's natural size land in
                // points; `.resizable()` + the explicit frame pin it to the
                // crop's exact point rect regardless.
                Image(decorative: filtered, scale: plate.scale)
                    .resizable()
                    .frame(width: crop.localRect.width,
                           height: crop.localRect.height)
                    // Offset is non-zero only when the border box overhangs
                    // the canvas and the crop had to be clamped; the
                    // uncovered strip stays unpainted rather than smeared.
                    .offset(x: crop.localRect.minX, y: crop.localRect.minY)
            }
        }
        // Rounded border box — same Shape the element's own background and
        // border are clipped to, so the backplate cannot bleed past a
        // rounded corner. Applied UNCONDITIONALLY: an all-zero
        // BorderRadiusConfig makes BorderRadiusShape emit the plain border
        // rect, which is exactly the clip a square box needs anyway (the
        // `.integral` crop can be up to one pixel wider than the box, and
        // this is what cuts that overshoot back).
        .clipShape(BorderRadiusShape(radius: radius ?? BorderRadiusConfig()))
        // The backplate is a re-render of pixels already on screen, never an
        // interactive surface — keep it out of hit-testing so it cannot
        // shadow the element's own content in a live host.
        .allowsHitTesting(false)
    }
}

/// One-shot capture-log disclosure for a REFUSED backdrop chain. Keyed by the
/// joined function list so a fixture that uses `sepia` on twenty boxes prints
/// one line, not twenty, while a genuinely different chain still gets its own
/// line.
///
/// Diagnostics only — it never changes what is drawn. Renders happen on the
/// main actor, so the unsynchronised set is only ever touched from one
/// thread; it is intentionally NOT a correctness mechanism.
enum BackdropLog {
    /// Signatures already printed this process.
    private static var seen: Set<String> = []

    /// Print once per distinct refused chain.
    static func reportOnce(_ plan: BackdropPlan) {
        // Nothing out of scope → nothing to disclose.
        guard plan.isRefused else { return }
        let signature = plan.unsupported.joined(separator: "+")
        guard !seen.contains(signature) else { return }
        seen.insert(signature)
        // Same `[Category]` shape the other engine diagnostics use
        // (BorderMiscApplier, DynamicValueResolver). The wording states the
        // ALL-OR-NOTHING outcome explicitly: nothing was filtered, so nobody
        // reading the log can mistake this for a partial render. The Compose
        // twin logs the same refusal from FilterApplier.applyBackdropFilters.
        print("[Backdrop] backdrop-filter chain refused WHOLESALE on iOS "
              + "(out of lane scope: \(signature); scope is invert + blur) — "
              + "no backplate drawn")
    }
}
