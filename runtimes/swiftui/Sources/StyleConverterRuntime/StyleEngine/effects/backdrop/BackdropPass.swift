//
//  BackdropPass.swift
//  StyleEngine/effects/backdrop — lane BF-I.
//
//  The host→runtime channel for the two-pass backdrop capture, cut from
//  the same cloth as `wptCaptureMode` (WPTCaptureMode.swift) and
//  `styleViewport` (#39): an EnvironmentKey whose DEFAULT keeps every
//  existing surface byte-identical, set only by the capture path that
//  opts in.
//
//  Default `.disabled` is the whole 327-pair protection story: on the
//  bundled property-fixture capture path (ScreenshotCaptureView's
//  captureNext → ScreenshotManager.render) nothing publishes this key, so
//  BackdropApplier takes its identity branch and the dark-stage baselines
//  render exactly as they did before this lane — including the
//  fixtures/properties/effects/backdrop-filter.json components, whose
//  backdrop chains have been a documented iOS no-op since Phase 8.
//
//  BACKDROP-ROOT BOUNDARY (the lane's stated scope): the backdrop root is
//  the WHOLE capture canvas, and there is exactly ONE pass-A raster per
//  capture. Consequences, all deliberate and all divergences from
//  filter-effects-2 §2's per-element "Backdrop Root" definition:
//    • pass A suppresses the paint of EVERY backdrop element at once, so
//      two sibling backdrop boxes do not see each other — the first
//      sibling is part of the second's backdrop per spec, but not here;
//    • a backdrop element nested inside another samples the same single
//      raster, i.e. the ancestor's own paint is missing from its backdrop;
//    • filters that need the element's own pixels (none in scope) are out
//      of reach by construction.
//  A fixture that depends on any of the above is honestly out of scope for
//  this lane rather than silently mis-rendered — the plan's `unsupported`
//  list plus this comment are the disclosure.
//

// SwiftUI for EnvironmentKey/EnvironmentValues; CoreGraphics for CGImage.
import SwiftUI

/// The pass-A raster handed to pass B, plus the geometry needed to map
/// SwiftUI POINTS (what a GeometryReader reports) to plate PIXELS.
public struct BackdropPlate {
    /// The composed canvas rendered with backdrop paint suppressed. Opaque
    /// by construction — it is an ImageRenderer capture of the canvas,
    /// which always paints a solid stage colour underneath.
    public let image: CGImage
    /// Plate pixels per SwiftUI point. 1.0 on every capture path today
    /// (ScreenshotManager.render pins ImageRenderer.scale = 1 so 1pt == 1px
    /// across platforms); carried explicitly so a hi-res probe render
    /// (renderHires, scale 4) crops and blurs correctly instead of
    /// sampling a quarter of the intended region.
    public let scale: CGFloat

    /// Memberwise init spelled out because the struct is public and the
    /// harness (a different module) constructs it after its pass-A render.
    public init(image: CGImage, scale: CGFloat) {
        self.image = image
        self.scale = scale
    }
}

// CGImage is a CoreFoundation class type, so identity comparison is both
// available and the RIGHT equality here: two plates are the same plate
// when they wrap the same raster object. Content comparison would mean
// hashing megabytes per Environment diff on every SwiftUI update.
extension BackdropPlate: Equatable {
    public static func == (lhs: BackdropPlate, rhs: BackdropPlate) -> Bool {
        lhs.image === rhs.image && lhs.scale == rhs.scale
    }
}

/// Which capture pass is running.
public enum BackdropPass: Equatable {
    /// No backdrop capture — every element paints normally and
    /// BackdropApplier is the identity. The DEFAULT, and what the
    /// 327-pair baseline path and every SDUI app always see.
    case disabled
    /// PASS A. Backdrop elements suppress their own paint (layout
    /// unchanged) so the resulting raster is their backdrop.
    case sampling
    /// PASS B. Each backdrop element crops this plate, filters the crop,
    /// and draws it under itself.
    case compositing(BackdropPlate)
}

/// Environment key. Default `.disabled` = product / baseline behaviour
/// UNCHANGED (see the file header).
private struct BackdropPassKey: EnvironmentKey {
    static let defaultValue: BackdropPass = .disabled
}

public extension EnvironmentValues {
    /// The running backdrop capture pass. Published ONLY by the harness's
    /// two-pass renderer (ScreenshotManager.renderBackdropTwoPass); unset —
    /// hence `.disabled` — everywhere else.
    var backdropPass: BackdropPass {
        get { self[BackdropPassKey.self] }
        set { self[BackdropPassKey.self] = newValue }
    }
}

/// The canvas coordinate space the crop is measured in.
public enum BackdropCanvas {
    /// Name of the coordinate space stamped on the capture canvas root.
    /// A backdrop element asks GeometryReader for its frame IN THIS SPACE,
    /// which is exactly the plate's own coordinate system (the plate is a
    /// render of that canvas), so the crop rect needs no further offsetting.
    /// String-namespaced to avoid colliding with any app-defined space.
    public static let spaceName = "styleconverter.backdrop.canvas"
}

public extension View {
    /// Stamp this view as the backdrop-canvas root. Called by the harness
    /// capture canvases at the OUTERMOST position so the space covers the
    /// whole rendered surface, matching the plate 1:1.
    ///
    /// Attaching a coordinate space does not change layout or paint (it
    /// only names an existing geometry frame), so canvases carry it
    /// unconditionally — including on the `.disabled` baseline path, where
    /// nothing ever reads it.
    func backdropCanvasRoot() -> some View {
        coordinateSpace(name: BackdropCanvas.spaceName)
    }
}

/// Pure harness-side decisions about whether a capture needs two passes.
/// Lives in the runtime (not the harness) so it is unit-pinnable in the
/// device-free SwiftPM suite, and so Compose's twin can mirror one rule.
public enum BackdropCapture {
    /// The IR property type that triggers the second pass.
    private static let backdropPropertyType = "BackdropFilter"

    /// True when ANY component in `components` (at any depth) declares
    /// `backdrop-filter`. False → the caller renders exactly one pass
    /// through the unchanged code path, so a document with no backdrop
    /// element cannot possibly differ from its pre-lane capture.
    ///
    /// Presence-based on purpose: `backdrop-filter: none` also returns
    /// true here and costs a wasted pass, but the two renders are then
    /// pixel-identical (the plan is empty → the applier is identity), so
    /// the cost is time, never fidelity. Parsing the value at this level
    /// would duplicate FilterExtractor's IR-shape knowledge for no gain.
    public static func needsTwoPass(_ components: [IRComponent]) -> Bool {
        for c in components {
            // This component's own declarations.
            if c.properties.contains(where: { $0.type == backdropPropertyType }) {
                return true
            }
            // Composed children (nil, never [], when empty — IRComponent).
            if let kids = c.children, needsTwoPass(kids) { return true }
        }
        return false
    }
}
