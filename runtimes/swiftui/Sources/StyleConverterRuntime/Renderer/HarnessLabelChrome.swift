//
//  HarnessLabelChrome.swift
//  Renderer — wave 51 PR (A): the harness debug label as CHROME.
//
//  Normative home: docs/DYNAMIC_CAPTURE.md, section "Harness label chrome".
//
//  WHAT: exactly one block-font label (BlockLabel.swift) per capture,
//  pinned at literal (8, 6) in the CAPTURE FRAME, drawn by the capture
//  harness as a SIBLING overlay over the whole capture root — i.e. AFTER
//  the element's entire paint chain. It is never a descendant of the
//  styled element, so no runtime modifier (blend group, opacity, filter,
//  clip-path, overflow clip, transform, margin, relative offset) can reach
//  it. Before (A) ComponentRenderer's leaf branch drew the same view INSIDE
//  the element, and the three platforms disagreed on every effect-bearing
//  fixture (design §1 mechanisms (i)–(v)).
//
//  WHERE IT LIVES: in the runtime package rather than the harness app ONLY
//  so the Mac Catalyst test target can compose and rasterize it
//  (HarnessLabelChromeRasterTests) — apps/ios-harness has no runnable test
//  destination on the dev machines. The harness mounts it in
//  apps/ios-harness/…/Screenshot/CaptureCanvas.swift as
//  `.overlay(alignment: .topLeading) { HarnessLabelChrome(…) }` right
//  after `.background(canvasBackground)` on BOTH root branches.
//
//  WHEN IT SHOWS — the shared three-platform predicate (web CaptureGallery
//  `showLabel`, Android `showsLabel` in ScreenshotCaptureScreen.kt):
//    !wptCaptureMode              TITAN/WPT/inbox/composed captures stay
//                                 label-free, the same flag that gated the
//                                 runtime's suppressesNamePlaceholder
//    && backdropPass != .sampling defence-in-depth, see below
//    && root has NO children      containers are unlabelled
//    && root has NO non-empty text real text is content, not a name slot
//  `component` is the CaptureCanvas INPUT — the composed root, BEFORE any
//  runtime fold (PseudoTextFold / ContentsUnboxing run inside
//  ComponentHost's subtree, ComponentRenderer.init) — the same node web
//  and Android test.
//
//  `.sampling` guard — DEFENCE-IN-DEPTH ONLY. The two-pass backdrop
//  renderer (ScreenshotManager.renderBackdropTwoPass) publishes `.sampling`
//  while it rasterizes the pass-A plate; label ink baked into that plate
//  would be blurred into the backdrop result. No live path can reach it
//  today: both two-pass callers (ScreenshotCaptureView.captureAllComponents
//  and captureComposedDocument) already publish wptCaptureMode == true, and
//  the bundled baseline path (captureNext) is single-pass under `.disabled`.
//  The guard covers a future non-WPT two-pass caller; pin (g) of
//  HarnessLabelChromeRasterTests is its only executable check.
//

import SwiftUI

/// The harness label chrome view.
// public: the HARNESS APP mounts it through a plain
// `import StyleConverterRuntime` (CaptureCanvas.swift), and a public struct's
// synthesized memberwise init is INTERNAL — hence the explicit `public init`
// below, the same reason ComponentHost.swift spells out its own.
public struct HarnessLabelChrome: View {

    /// The composed capture root (the CaptureCanvas input): read for the
    /// predicate (children / text) and for the label text (name).
    let component: IRComponent

    /// The capture FRAME width the glyph run truncates against — the
    /// harness passes CaptureCanvas.width (390, or CAPTURE_WIDTH). NOT the
    /// element's width: truncating against the element (the pre-(A)
    /// `usedWidth` channel) is what made Android cut at the content box
    /// while web/iOS cut at the border box (design mechanism (v)).
    let frameWidth: CGFloat

    /// WPT/inbox capture flag (WPTCaptureMode.swift) — read HERE, not taken
    /// as a parameter, so the `.environment(\.wptCaptureMode, …)` the
    /// harness already publishes on the ImageRenderer tree gates the chrome
    /// and a test can flip it on the canvas exactly as ScreenshotCaptureView
    /// does (design C47: a parameter would make the negative pin vacuous).
    @Environment(\.wptCaptureMode) private var wptCaptureMode

    /// The running backdrop pass (BackdropPass.swift): `.disabled` on every
    /// single-pass render, `.sampling` only inside the two-pass plate render.
    @Environment(\.backdropPass) private var backdropPass

    // public: explicit memberwise init for the cross-module harness caller.
    public init(component: IRComponent, frameWidth: CGFloat) {
        self.component = component
        self.frameWidth = frameWidth
    }

    /// The shared predicate as a pure static so the rule is unit-pinnable
    /// without a render surface (the suppressesNamePlaceholder pattern).
    /// `children` is nil (never []) for a composed leaf (IRModels.swift,
    /// IRComponent.children); `text` nil or "" both mean "no real text" —
    /// "" is "extracted, was empty", still not content.
    static func showsLabel(_ component: IRComponent,
                           wptCaptureMode: Bool,
                           backdropPass: BackdropPass) -> Bool {
        // WPT/inbox/composed: the browser-ref paints no label, so neither do we.
        guard !wptCaptureMode else { return false }
        // Pass-A plate: never bake chrome into a backdrop sample.
        guard backdropPass != .sampling else { return false }
        // Containers: the composed children ARE the content.
        guard component.children?.isEmpty ?? true else { return false }
        // Real element text is content, not a slot for the debug name.
        return component.text?.isEmpty ?? true
    }

    /// The label text: the PLAIN component name with `_` → space, on all
    /// three platforms (design C51) — no text-transform / tab-size folding
    /// (Android used to apply them; the contract is byte-identical rects
    /// from the plain name). Case and unknown glyphs are handled by
    /// BlockLabelLayout.glyphString inside BlockLabel.
    static func labelText(for component: IRComponent) -> String {
        component.name.replacingOccurrences(of: "_", with: " ")
    }

    // public: View protocol witness on a public type must be public.
    public var body: some View {
        if Self.showsLabel(component, wptCaptureMode: wptCaptureMode,
                           backdropPass: backdropPass) {
            // BlockLabel collapses to a 0×0 slot and offsets its canvas to
            // (8, 6) (BlockLabel.swift `.offset` + zero frame), so under the
            // harness's `.overlay(alignment: .topLeading)` the first glyph
            // cell's top-left is frame pixel (8, 6); `.overlay` never sizes
            // its host, so the capture's dimensions are untouched.
            BlockLabel(label: Self.labelText(for: component),
                       componentWidth: frameWidth)
        } else {
            // Nothing at all — not even a 0×0 node — so WPT/inbox/composed
            // captures stay bit-for-bit what they were before PR (A).
            EmptyView()
        }
    }
}
