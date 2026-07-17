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

    /// IR inset property types per axis — the physical sides plus their
    /// LTR/horizontal-tb logical resolutions (the runtime's
    /// PositionExtractor maps InsetInlineStart→left, InsetBlockStart→top,
    /// … the same way), so a logical-inset fixture anchors identically to
    /// its physical twin. Kept in sync with Android's
    /// Horizontal/VerticalInsetTypes in ScreenshotCaptureScreen.kt.
    static let horizontalInsetTypes: Set<String> =
        ["Left", "Right", "InsetInlineStart", "InsetInlineEnd"]
    static let verticalInsetTypes: Set<String> =
        ["Top", "Bottom", "InsetBlockStart", "InsetBlockEnd"]

    /// True when the out-of-flow capture subject declares ANY horizontal
    /// inset. Per CSS 2.1 §10.3.7, `left`/`right` both `auto` keeps an
    /// absolutely positioned box at its STATIC inline position, so the
    /// canvas must NOT re-anchor it at the padding edge on that axis.
    /// Presence-based approximation: an explicit `left: auto` IR property
    /// would count as an inset here — accepted, since no fixture declares
    /// an auto inset explicitly (Chromium treats it as all-auto).
    static func hasHorizontalInset(_ component: IRComponent) -> Bool {
        component.properties.contains { horizontalInsetTypes.contains($0.type) }
    }

    /// Vertical twin of `hasHorizontalInset` (CSS 2.1 §10.6.4:
    /// `top`/`bottom` both `auto` → static block position) — same
    /// presence rule per axis.
    static func hasVerticalInset(_ component: IRComponent) -> Bool {
        component.properties.contains { verticalInsetTypes.contains($0.type) }
    }

    var body: some View {
        // Fidelity wave 3 — a ROOT component that is itself absolutely
        // positioned (a per-child standalone crop of e.g. B_RelativeAnchor's
        // `floating`, or any abs-positioned fixture root) gets the web
        // canvas's card treatment instead of the block-flow one:
        //   • the containing block is the card's PADDING box, whose origin
        //     is the card corner (the web canvas is `position: relative`
        //     with no border — CSS 2.1 §10.1), so DECLARED top/left offsets
        //     anchor at (0,0) of the card, NOT inside the 16px content inset;
        //   • PER-AXIS auto-inset rule (CSS 2.1 §10.3.7 / §10.6.4): an axis
        //     whose insets are ALL `auto` keeps the box at its STATIC
        //     position — the content origin INSIDE the card padding
        //     ((16,16), where Chromium leaves an all-auto box) — restored
        //     below by padding that axis of the hosted component;
        //   • the card's flow height COLLAPSES to the padding band alone
        //     (out-of-flow boxes add no height — the web card measures
        //     exactly 2 × 16 = 32px for the `floating` crop);
        //   • `overflow: hidden` on the web canvas clips whatever the
        //     offsets push past the card — mirrored with `.clipped()`.
        // Without this the iOS crop drew the box at content-origin +
        // offset on an unclipped tall card (wave-3 floating crop 0.833).
        if ComponentRenderer.isOutOfFlow(component) {
            // Collapsed flow: the BASE view pins the card at the padding-band
            // height (2 × 16) and full canvas width. The component rides an
            // .overlay, which NEVER influences the base's size — unlike the
            // previous ZStack, which adopts the max child size (an 80pt-tall
            // component grew the card to 80pt and defeated the collapse).
            Color.clear
                .frame(width: CaptureCanvas.width,
                       height: CaptureCanvas.padding * 2)
                .overlay(alignment: .topLeading) {
                    // The component renders through the normal engine path —
                    // its own PositionApplier applies declared top/left
                    // offsets from this overlay's top-leading corner (card
                    // origin). v2: hosted so every component carries its
                    // placement parent-data (inert at root — no Layout
                    // parent here).
                    ComponentHost(component: component)
                        // Per-axis static-position rule: an all-auto axis
                        // gets the 16pt canvas padding back so the box sits
                        // at the content origin (its static position); an
                        // axis with a declared inset stays anchored at the
                        // card corner (padding edge) — offset 0 here.
                        .padding(.leading, CaptureCanvas.hasHorizontalInset(component)
                                 ? 0 : CaptureCanvas.padding)
                        .padding(.top, CaptureCanvas.hasVerticalInset(component)
                                 ? 0 : CaptureCanvas.padding)
                }
                // Web canvas `overflow: hidden` parity — clip whatever the
                // overlaid component paints past the collapsed 390×32 card.
                .clipped()
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

/// TITAN WPT Round 3 — the COMPOSED whole-document capture surface.
///
/// The per-component `CaptureCanvas` above renders ONE IRComponent per PNG;
/// the legacy WPT native path captured every component that way and the
/// orchestrator STITCHED the crops vertically before diffing against the
/// browser-ref. That vertical concatenation does not reproduce the
/// reference PAGE's layout (bar stacking, gaps, the ref's 16px body
/// padding), so a multi-component test scored dishonestly.
///
/// This canvas fixes the geometry the same way the web harness's
/// `ComposedCaptureGallery.tsx` does: it renders ALL of a fed doc's
/// slot-composed roots (document.components is already the IRComposer
/// output — see IRDocument.init(from:)) stacked in document flow on ONE
/// surface, then the caller ImageRenderer's it ONCE into a single
/// `<safe(testKey)>.png`. inject-wpt-block.mjs's diffComposedVsRef diffs
/// that composite DIRECTLY against the browser-ref (no stitch).
///
/// The framing MIRRORS tools/titan/capture-browser-ref.mjs and the web
/// composedCanvasStyle EXACTLY so the two images are pixel-comparable:
///   - width       390 px            (CANVAS_WIDTH)
///   - content box 358 px            (390 − 2×16, the ref body content box)
///   - padding     16 px all sides   (CANVAS_PAD_PX / the ref `:where(body)` pad)
///   - background  #1A1A2E           (CANVAS_BG — the ref html+body bg)
///   - min-height  600 px            (the ref `min-height:100vh` floors
///                                    capture-browser-ref's docHeight at 600;
///                                    the canvas grows past 600 on overflow)
///
/// The ONLY thing that differs from the per-component path is the
/// composition geometry — every node still renders through the identical
/// ComponentHost → ComponentRenderer engine, so per-node fidelity is
/// measured on the exact same footing (web harness's stated invariant).
struct ComposedCaptureCanvas: View {
    /// The fed per-test IR document. `components` are the slot-composed
    /// roots (IRComposer ran at decode); we render them in flat sibling
    /// order = the document/composition order (spec 03), identical to the
    /// web ComposedTestCanvas root loop.
    let document: IRDocument

    /// Canvas width — the browser-ref CANVAS_WIDTH. Fixed at 390 (NOT
    /// CaptureOverrides.captureWidth): the WPT browser-ref is always
    /// captured at 390, so the composed comparison surface is too.
    static let width: CGFloat = 390
    /// Uniform 16px pad — the ref's `:where(body) { padding }`. Content box
    /// is therefore 390 − 32 = 358, mirroring the ref body content box.
    static let padding: CGFloat = 16
    /// Minimum canvas height — the ref's `min-height:100vh` floors
    /// capture-browser-ref.mjs's docHeight at 600; the surface grows past
    /// 600 when the composed content is taller.
    static let minHeight: CGFloat = 600

    /// The capture geometry published to the runtime's styleViewport
    /// channel — SAME numbers as CaptureCanvas.viewport (390×844 viewport,
    /// 358 root containing block) so vw/vh/% resolve identically to the
    /// per-component path and the web reference.
    static let viewport = StyleViewport(
        width: Double(width),
        height: 844,
        rootContainingBlock: Double(width - padding * 2)
    )

    /// TITAN Round 4 GAP 1 — the per-root effective UA vertical block
    /// margins (deferring to any IR-declared margin the runtime's
    /// MarginApplier already paints), collapsed into the space to place
    /// ABOVE each root + BELOW the last. See UABlockMargin: this reproduces
    /// the browser-ref's UA `<p>`/`<hN>`/… block margins the native flush
    /// stack lacked, so a multi-bar test's gaps match the ref. Composed
    /// WPT only — this canvas is built solely by captureComposedDocument.
    private var stackedSpacing: (leading: [CGFloat], trailing: CGFloat) {
        let margins = document.components.map {
            UABlockMargin.effectiveVertical(tag: $0.meta?.sourceTag,
                                            properties: $0.properties)
        }
        return UABlockMargin.stackedSpacing(margins)
    }

    /// TITAN Round 4 GAP 2 — the canvas background. The browser-ref frames
    /// every page with a ZERO-specificity `:where(html,body){background:
    /// #1A1A2E}`, so a reference that sets its OWN `body{background}` WINS
    /// and paints the whole page that color (css-color/a98rgb-003's grey).
    /// The reader tags that body background as a `meta.role:"body-root"`
    /// component; we resolve it through the SAME engine the renderer uses
    /// (ComponentRenderer.resolvedBackgroundColor) and honor it here,
    /// falling back to the pipeline's #1A1A2E default when there is no
    /// body-root or it declares no background.
    private var canvasBackground: Color {
        guard let bodyRoot = document.components.first(where: {
                  $0.meta?.role == "body-root"
              }),
              let bg = ComponentRenderer.resolvedBackgroundColor(
                  from: bodyRoot.properties)
        else { return CaptureCanvas.backgroundColor }
        return bg
    }

    var body: some View {
        // GAP 1 — fold the per-root UA margins (with adjacent collapse and
        // no collapse at the padded top/bottom edges) into per-root spacing.
        let spacing = stackedSpacing
        let lastIndex = document.components.count - 1
        return VStack(alignment: .leading, spacing: 0) {
            // enumerated()+offset id: roots are rendered positionally, never
            // reordered — a stable positional key is correct and avoids
            // relying on component.id uniqueness across a malformed doc.
            ForEach(Array(document.components.enumerated()), id: \.offset) { idx, root in
                // Identical host shim the per-component canvas and the
                // engine's own child loop use — placement parent-data
                // attached (inert under this VStack), full ComponentRenderer
                // engine underneath. Zero per-node render difference.
                ComponentHost(component: root)
                    // GAP 1 — the UA block margin ABOVE this root: its full
                    // top margin for the first root (the canvas's 16px
                    // padding blocks parent↔child collapse there), or the
                    // previous root's bottom COLLAPSED with this root's top
                    // for interior roots. IR-declared margins contribute 0
                    // here (already painted by MarginApplier inside the host)
                    // so they are never double-counted.
                    .padding(.top, spacing.leading[idx])
                    // Only the LAST root carries the trailing bottom margin
                    // (again uncollapsed — the padded bottom edge). Interior
                    // bottoms are folded into the next root's leading gap.
                    .padding(.bottom, idx == lastIndex ? spacing.trailing : 0)
            }
        }
        // Constrain the composed content to the 358px ref content box,
        // anchored at the block-flow origin (top-leading) — same maxWidth
        // rule the per-component canvas applies to its single component.
        .frame(maxWidth: Self.width - Self.padding * 2, alignment: .topLeading)
        // The ref's 16px body padding — the exact offset the stitched path
        // dropped (web composedCanvasStyle carries it too).
        .padding(Self.padding)
        // Frame to the full 390px width (min==max pins it) and floor the
        // height at the ref's 600px min; fixedSize(vertical) below lets the
        // surface adopt its natural height above that floor. Uses the
        // flexible-frame overload because SwiftUI has no width+minHeight form.
        .frame(minWidth: Self.width, maxWidth: Self.width,
               minHeight: Self.minHeight, alignment: .topLeading)
        // Natural (content) height beyond the 600 floor — mirrors the ref's
        // documentHeight capture and the per-component canvas's height rule.
        .fixedSize(horizontal: false, vertical: true)
        // GAP 2 — the ref canvas background: #1A1A2E by default, or the
        // document body-root's own background when it declares one (e.g.
        // a98rgb-003's full-page grey). Any sub-root gap paints this so
        // seams stay invisible against the ref.
        .background(canvasBackground)
        // Publish the capture geometry so the runtime resolves vw/vh/% and
        // containing blocks against 390×844/358, not the device screen.
        .environment(\.styleViewport, Self.viewport)
        // GAP 1 (WIDTH half) — publish the 358px content-box width so the
        // runtime stretches each auto-width, in-flow ROOT to full bleed like
        // the browser-ref's block `<p>`/`<div>` (iOS otherwise hugs content).
        // Set on the whole stack, but ComponentRenderer folds it into ROOTS
        // only and resets it for their children, so block-fill is root-scoped.
        // Composed WPT only (this canvas is built solely by
        // captureComposedDocument), and the fold is additionally gated on
        // wptCaptureMode — the 327-pair baseline never sees it.
        .environment(\.wptBlockFlowFillWidth, Self.width - Self.padding * 2)
        // Same dynamic-capture hooks the per-component canvas carries
        // (pinned light scheme, empty forced set, live clock) so the
        // composed capture is a deterministic base render.
        .modifier(DynamicCaptureHooks())
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
