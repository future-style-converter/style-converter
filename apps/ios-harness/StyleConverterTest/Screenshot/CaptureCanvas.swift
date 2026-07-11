//
//  CaptureCanvas.swift
//  StyleConverterTest
//
//  Chromeless per-component capture surface used by the three-way
//  screenshot comparison pipeline (iOS / Android / Web).
//
//  The contract is identical on all three platforms so captures can be
//  pixel-diffed directly:
//
//  - Width                : exactly 390 px
//  - Height               : component's natural height (no clamping)
//  - Background           : solid #1A1A2E (no alpha compositing)
//  - Padding              : 16 px on all sides
//  - Scale                : 1 px per logical pixel
//
//  No card header, no footer, no border, no name label — just the
//  rendered component on a known background. That keeps the pixel diff
//  focused on the component itself rather than the test chrome.
//

import SwiftUI
// IRComponent + ComponentRenderer come from the runtime package.
import StyleConverterRuntime

struct CaptureCanvas: View {
    let component: IRComponent

    /// Solid background matching the phone-frame color used by the web and
    /// Android capture canvases. Chosen so common dark-mode backgrounds on
    /// captured components blend cleanly; kept in sync with Android's
    /// `CaptureCanvasBg` and web's `--capture-bg`.
    static let backgroundColor = Color(
        red:   0x1A / 255.0,
        green: 0x1A / 255.0,
        blue:  0x2E / 255.0
    )

    /// Uniform padding around the component. The 16pt value matches the
    /// web / Android canvases exactly.
    static let padding: CGFloat = 16

    /// Canvas width. 390 (the phone-frame default every committed
    /// baseline was captured at) unless the CAPTURE_WIDTH hook
    /// overrides it for a two-width media run (docs/DYNAMIC_CAPTURE.md
    /// §2) — the canvas IS the render surface media queries evaluate
    /// against (spec 06 §4), so the recipe only changes this number.
    static let width: CGFloat = CaptureOverrides.captureWidth

    /// Fixed canvas height basis for vh units. Matches the 390×844
    /// phone frame (StyleConverterTestApp) and web's #root — captures
    /// have natural height, but vh must resolve against the same
    /// number on all three platforms.
    static let height: CGFloat = 844

    /// Wave 6 (#39): the capture geometry the HARNESS publishes to the
    /// runtime's styleViewport Environment channel. Same numbers the
    /// runtime previously hardcoded (390×844 viewport; 390 − 2×16 = 358
    /// root containing block), so every committed baseline stays
    /// byte-identical — the values just come from the right owner now.
    static let viewport = StyleViewport(
        width: Double(width),
        height: Double(height),
        rootContainingBlock: Double(width - padding * 2)
    )

    var body: some View {
        // Fidelity wave 3 — a ROOT component that is itself absolutely
        // positioned (a per-child standalone crop of e.g. B_RelativeAnchor's
        // `floating`, or any abs-positioned fixture root) gets the web
        // canvas's card treatment instead of the block-flow one:
        //   • the containing block is the card's PADDING box, whose origin
        //     is the card corner (the web canvas is `position: relative`
        //     with no border — CSS 2.1 §10.1), so top/left offsets anchor
        //     at (0,0) of the card, NOT inside the 16px content inset;
        //   • the card's flow height COLLAPSES to the padding band alone
        //     (out-of-flow boxes add no height — the web card measures
        //     exactly 2 × 16 = 32px for the `floating` crop);
        //   • `overflow: hidden` on the web canvas clips whatever the
        //     offsets push past the card — mirrored with `.clipped()`.
        // Without this the iOS crop drew the box at content-origin +
        // offset on an unclipped tall card (wave-3 floating crop 0.833).
        if ComponentRenderer.isOutOfFlow(component) {
            ZStack(alignment: .topLeading) {
                // Collapsed flow: transparent strut fixes the card at the
                // padding-band height (2 × 16) and full canvas width.
                Color.clear
                    .frame(width: CaptureCanvas.width,
                           height: CaptureCanvas.padding * 2)
                // The component renders through the normal engine path —
                // its own PositionApplier applies the top/left offsets
                // from this ZStack's top-leading corner (card origin).
                // v2: hosted so every component carries its placement
                // parent-data (inert at root — no Layout parent here).
                ComponentHost(component: component)
            }
            .frame(width: CaptureCanvas.width, alignment: .topLeading)
            // Web canvas `overflow: hidden` parity.
            .clipped()
            .fixedSize(horizontal: false, vertical: true)
            .background(CaptureCanvas.backgroundColor)
            // #39: the harness supplies the capture geometry — the
            // runtime no longer assumes a 390×844 canvas on its own.
            .environment(\.styleViewport, CaptureCanvas.viewport)
            // Wave 7 — the dynamic-styling capture hooks ride the same
            // environment channel (see the block on the flow branch).
            .modifier(DynamicCaptureHooks())
        } else {
        // Critical: explicit alignment on the outer frame.
        //
        // The rendered component's natural width is often narrower than the
        // 390 pt canvas (e.g. a `Sizing_Fixed` box with `width: 200`). Without
        // an explicit alignment, SwiftUI's default `.center` positions the
        // narrow child in the middle of the 390-wide frame — giving the iOS
        // capture a ~79 pt leftward offset vs Android and Web, which both
        // place block content at the flow origin. Anchoring to top-leading
        // mirrors the CSS block-flow origin and keeps captures pixel-aligned
        // across platforms.
        //
        // Fidelity wave 1: the web capture page honours a top-level
        // component's `justify-self` (center/end shift the box along the
        // canvas's inline axis — grid-2col standalone crops b/c). Resolve
        // the alignment through the runtime's RootAlignment helper so the
        // three canvases agree; components without justify-self keep the
        // .topLeading block-flow origin exactly as before.
        // v2: root components render through ComponentHost too — the
        // placement layout value is inert against this frame/padding
        // chain but keeps the "every component is hosted" invariant.
        ComponentHost(component: component)
            .frame(
                maxWidth: CaptureCanvas.width - (CaptureCanvas.padding * 2),
                alignment: RootAlignment.alignment(for: component)
            )
            .padding(CaptureCanvas.padding)
            .frame(width: CaptureCanvas.width, alignment: .topLeading)
            // `fixedSize(vertical:)` lets SwiftUI give the component its
            // natural height rather than expanding to fill the parent.
            .fixedSize(horizontal: false, vertical: true)
            .background(CaptureCanvas.backgroundColor)
            // #39: the harness supplies the capture geometry — the
            // runtime no longer assumes a 390×844 canvas on its own.
            .environment(\.styleViewport, CaptureCanvas.viewport)
            // Wave 7 — the dynamic-styling capture hooks: forced state,
            // pinned scheme, and the forced-run marker.
            .modifier(DynamicCaptureHooks())
        }
    }
}

/// Wave 7 (docs/DYNAMIC_CAPTURE.md) — the environment half of the iOS
/// capture hooks, shared by both canvas branches:
///
///   • `forcedStyleStates` — the spec 06 §6 forced-state set; the
///     runtime's StateResolver treats it as active on EVERY component
///     in this canvas (deterministic state captures).
///   • `colorScheme` — pinned LIGHT by default / dark on the forced-
///     dark run (§3). Explicit so ImageRenderer output never depends
///     on app or system appearance (the harness chrome runs dark).
///   • accessibility identifier — the native analogue of the web
///     canvas's `data-force-state` stamp: a forced run is VERIFIABLE
///     ("force-state-active"), a base run reads "capture-canvas".
struct DynamicCaptureHooks: ViewModifier {
    func body(content: Content) -> some View {
        content
            // Forced-state set into the runtime's resolution channel.
            .environment(\.forcedStyleStates, CaptureOverrides.forcedStates)
            // Deterministic scheme signal for prefers-color-scheme
            // buckets AND light-dark() arms (one surface, one answer).
            .environment(\.colorScheme, CaptureOverrides.colorScheme)
            // Wave 8 — the spec 07 §5 pinned motion clock: every
            // animation on this canvas renders its state at absolute
            // second t, paused (the renderer's TimelineView schedule
            // pauses outright when this is non-nil). nil = live clock,
            // byte-identical to every committed baseline.
            .environment(\.animationCaptureTime, CaptureOverrides.animationTime)
            // Run markers (contract: never silently diff two base runs
            // believing one was forced/seized). The composite identifier
            // is the native analogue of the web canvas's
            // data-force-state + data-animation-time attributes.
            .accessibilityIdentifier(
                (CaptureOverrides.forceState.map { "force-state-\($0)" }
                    ?? "capture-canvas")
                + (CaptureOverrides.animationTimeRaw.map { "+animation-time-\($0)" } ?? ""))
    }
}
