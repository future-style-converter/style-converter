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

    /// Fixed canvas width. Matches the 390 px phone frame used by the
    /// gallery and other testing surfaces.
    static let width: CGFloat = 390

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
        }
    }
}
