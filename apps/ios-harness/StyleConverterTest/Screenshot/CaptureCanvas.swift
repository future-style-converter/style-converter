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
//  - Background           : solid #1A1A2E (no alpha compositing) on the
//                           bundled/baseline path; solid WHITE in WPT
//                           capture mode (wptCaptureMode — the corpus-v4
//                           white canvas, mirrored by the white browser-ref
//                           and the web/Android WPT canvases so white WPT
//                           ink vanishes identically on every surface)
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

    /// WPT capture mode (TITAN inbox path — ScreenshotCaptureView publishes
    /// it on the ImageRenderer tree). Read here so the canvas can flip to
    /// the corpus-v4 WHITE stage in WPT capture while the bundled/baseline
    /// path (default false) keeps the dark stage byte-identically.
    @Environment(\.wptCaptureMode) private var wptCaptureMode

    /// Solid background matching the phone-frame color used by the web and
    /// Android capture canvases. Chosen so common dark-mode backgrounds on
    /// captured components blend cleanly; kept in sync with Android's
    /// `CaptureCanvasBg` and web's `--capture-bg`. NON-WPT paths only —
    /// WPT capture routes through `canvasBackground` below.
    static let backgroundColor = Color(
        red:   0x1A / 255.0,
        green: 0x1A / 255.0,
        blue:  0x2E / 255.0
    )

    /// Mode-split stage color (TITAN-WHITE lane): the corpus-v4 WHITE
    /// canvas in WPT capture mode — so white WPT ink (borders/backgrounds)
    /// vanishes exactly as it does in the white browser-ref and the
    /// web/Android WPT canvases — else the historical dark stage. The
    /// decision itself is the runtime's pure `WPTCanvas.captureBackground`
    /// (unit-pinned in WPTCaptureModeTests), so all platforms split
    /// identically.
    private var canvasBackground: Color {
        WPTCanvas.captureBackground(wptCaptureMode: wptCaptureMode,
                                    defaultBackground: CaptureCanvas.backgroundColor)
    }

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
                // Mode-split stage: dark for baseline, white in WPT capture
                // (canvasBackground above — the corpus-v4 contract).
                .background(canvasBackground)
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
            // Mode-split stage: dark for baseline, white in WPT capture
            // (canvasBackground above — the corpus-v4 contract).
            .background(canvasBackground)
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
///   - content box 358 px            (390 − 2×16 = REF_RENDER_WIDTH, the
///                                    viewport the ref is RENDERED at)
///   - frame       16 px all sides   (CANVAS_PAD_PX — since wave-25 CAL-RC1
///                                    the ref applies it to the finished PNG
///                                    in IMAGE space, so it is unconditional
///                                    and an author `body { padding }` adds
///                                    INSIDE it; see `resolvedPadding`)
///   - background  WHITE             (CANVAS_BG — the ref html+body bg
///                                    since the corpus-v4 white-canvas
///                                    boundary; WPTCanvas.background)
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
    /// The composed canvas's image-space FRAME — 16px per side, so the
    /// content space is 390 − 32 = 358, exactly the viewport
    /// capture-browser-ref.mjs renders at (REF_RENDER_WIDTH).
    ///
    /// wave-25 round 3: read from the runtime's shared constant rather than
    /// spelled here, so Compose (`WPT_CANVAS_FRAME_DP`), SwiftUI and web
    /// (`CANVAS_FRAME_PX`) can never disagree about the ref's frame. It is no
    /// longer "the ref's `:where(body)` padding": that injection is gone, and
    /// the frame is now applied to the ref PNG in image space, which no
    /// author rule can cancel. See `resolvedPadding` for the frame/author
    /// split that replaced it.
    static let padding: CGFloat = WPTCanvas.canvasFramePx
    /// Minimum canvas height — the ref's `min-height:100vh` floors
    /// capture-browser-ref.mjs's docHeight at 600; the surface grows past
    /// 600 when the composed content is taller.
    static let minHeight: CGFloat = 600

    /// The capture geometry published to the runtime's styleViewport
    /// channel — SAME numbers as CaptureCanvas.viewport (390×844 viewport,
    /// 358 root containing block) so vw/vh/% resolve identically to the
    /// per-component path and the web reference.
    ///
    /// wave-24 B-RC5: this is the 16px-DEFAULT geometry. `body` publishes a
    /// viewport built from `resolvedPadding` instead, which equals this one
    /// for every document whose body-root declares no padding. Kept as the
    /// documented default (and for any non-`body` caller) rather than
    /// deleted, since the two must never drift apart.
    static let viewport = StyleViewport(
        width: Double(width),
        height: 844,
        rootContainingBlock: Double(width - padding * 2)
    )

    /// Wave 17 (the out-of-flow contract) — the FixedHoist split of the
    /// root list: out-of-flow ROOTS (absolute + fixed — both anchor at
    /// the UNPADDED canvas origin per the measured web behavior) and
    /// `position: fixed` DESCENDANTS at any depth leave the padded flow
    /// stack entirely (css-position-3 §2.1: no flow space reserved — the
    /// next in-flow root starts where the hoisted box would have been)
    /// and mount in the canvas-root overlay attached below. Documents
    /// with no out-of-flow boxes split to (all, []) — their VStack is
    /// built from an identical root list, byte-unchanged.
    private var splitRoots: (flow: [IRComponent], hoisted: [IRComponent]) {
        FixedHoist.split(roots: document.components)
    }

    /// TITAN Round 4 GAP 1 + RC-A4 (wave 19) — the per-root stack plan.
    /// Round 4 folded only the UA-INJECTED block margins (deferring to any
    /// IR-declared side); RC-A4 additionally folds statically-resolvable
    /// DECLARED block margins into the same collapsed gaps and flags the
    /// root for a block-margin STRIP (via composedRootBlockMarginStrip), so
    /// adjacent declared margins collapse to max() like the browser instead
    /// of stacking (safe-001's 96px vs 76px root pitch). Out-of-flow roots
    /// (their margins never collapse — §8.3.1 in-flow precondition) and
    /// non-static value flavors bail to the Round 4 behavior. Composed WPT
    /// only — this canvas is built solely by captureComposedDocument.
    /// Wave 17: computed over the IN-FLOW roots only — hoisted boxes
    /// occupy no flow space, so they contribute no stack margins (and the
    /// per-index arrays must match the flow ForEach exactly).
    private func rootPlans(for flowRoots: [IRComponent]) -> [UABlockMargin.RootStackMargin] {
        flowRoots.map { root in
            // Wave-19 follow-up: the RC1 static-position family (the ONLY
            // out-of-flow roots FixedHoist.split leaves in the flow list —
            // absolute, no positioned ancestor, no inset) mounts at 0×0
            // behind StaticPositionAnchor below, so it is margin-
            // TRANSPARENT: CSS 2.1 §8.3.1 collapses the neighbors' block
            // margins THROUGH a zero-flow-footprint box into ONE max() gap
            // (hoisted roots already collapse through by ABSENCE — they
            // never reach this list). Same classifier the mount uses — one
            // decision, two consumers, never re-derived.
            if FixedHoist.rendersInFlowAsStaticPosition(root) {
                // Its OWN declared margins join the collapse-through set
                // (§8.3.1's empty-box model — the hypothetical static box's
                // margins are adjoining) via the SAME resolution as any
                // other root, then get stripped from its render, so the
                // slot anchor stays the §8.3.1 hypothetical position and
                // nothing double-renders. Unresolvable flavors bail inside
                // rootStackMargin (R4/R5) exactly like opaque roots.
                let base = UABlockMargin.rootStackMargin(
                    tag: root.meta?.sourceTag,
                    declaresTop: UABlockMargin.declaresBlockMarginTop(root.properties),
                    declaresBottom: UABlockMargin.declaresBlockMarginBottom(root.properties),
                    staticDeclaredEdges: UABlockMargin.staticDeclaredEdges(root.properties))
                // Same contribution + strip, flagged transparent so the fold
                // keeps the adjoining set open across this root's slot.
                return UABlockMargin.RootStackMargin(
                    top: base.top, bottom: base.bottom,
                    stripDeclared: base.stripDeclared, marginTransparent: true)
            }
            return UABlockMargin.rootStackMargin(
                tag: root.meta?.sourceTag,
                declaresTop: UABlockMargin.declaresBlockMarginTop(root.properties),
                declaresBottom: UABlockMargin.declaresBlockMarginBottom(root.properties),
                // The runtime's §8.3.1 classifier (same values MarginApplier
                // paints); nil for any other out-of-flow root (defensive —
                // split hoists them all, so none should reach here).
                staticDeclaredEdges: ComponentRenderer.isOutOfFlow(root)
                    ? nil : UABlockMargin.staticDeclaredEdges(root.properties))
        }
    }

    /// TITAN Round 4 GAP 2 — the canvas background. The browser-ref frames
    /// every page with a ZERO-specificity `:where(html,body){background:
    /// WHITE}` (the corpus-v4 white canvas), so a reference that sets its
    /// OWN `body{background}` WINS and paints the whole page that color
    /// (css-color/a98rgb-003's grey). The reader tags that body background
    /// as a `meta.role:"body-root"` component; we resolve it through the
    /// SAME engine the renderer uses
    /// (ComponentRenderer.resolvedBackgroundColor) and honor it here,
    /// falling back to the corpus-v4 WHITE default (WPTCanvas.background —
    /// this canvas is WPT-ONLY, so unlike the per-component canvas there is
    /// no dark-stage branch) when there is no body-root or it declares no
    /// background.
    ///
    /// Wave 15 (NATIVES-ALPHA) — the resolved color is ALPHA-COMPOSITED
    /// over the white canvas, never painted verbatim: the ref's page canvas
    /// blends a translucent `body{background}` source-over onto white, so
    /// `rgba(0,0,0,0)` must yield WHITE (verbatim it flattened to BLACK on
    /// the opaque ImageRenderer PNG — the 0.000 on
    /// background-color-transparent-animation-in-body). The pure decision
    /// is WPTCanvas.composedBackground (unit-pinned in WPTCaptureModeTests,
    /// twinned by Compose's composedCanvasBackground).
    private var canvasBackground: Color {
        // Resolve the body-root's own background through the SAME engine
        // path the renderer uses (nil when no body-root / no background) …
        let resolved = document.components
            .first(where: { $0.meta?.role == "body-root" })
            .flatMap { ComponentRenderer.resolvedBackgroundColor(from: $0.properties) }
        // … then let the runtime's pure composed-canvas rule composite it
        // over the corpus-v4 white (or fall back to white on nil).
        return WPTCanvas.composedBackground(resolved: resolved)
    }

    /// wave-24 B-RC5 — the composed canvas's per-side pad. The twin of
    /// `canvasBackground` above for the other half of the ref's
    /// zero-specificity body frame, and of the web harness's
    /// `resolveCanvasPadding` / Compose's `resolveComposedCanvasPadding`.
    ///
    /// WHY (wave 24): capture-browser-ref.mjs used to inject
    /// `:where(body) { padding: 16px }`. `:where()` contributes NO
    /// specificity (CSS Selectors L4 §17), so a ref declaring its own
    /// `body { padding: 0 }` (0,0,1) WON and rendered with no body pad. This
    /// canvas hardcoded `Self.padding`, so every such test rendered at a
    /// (+16,+16) offset against its ref — MEASURED as the ENTIRE divergence
    /// of css-masking/clip-path-circle-007, whose test and ref both open
    /// with `body, div { padding: 0; margin: 0 }`.
    ///
    /// WHY IT CHANGED (wave 25 round 3): at CAL-RC1 the ref stopped injecting
    /// a body padding at all. It renders at 358 wide with `padding: 0` and
    /// the 16px frame is memcpy'd around the finished PNG. An image-space
    /// translation has no cascade, so the two halves wave 24 conflated have
    /// SPLIT: the FRAME (`Self.padding`) is unconditional, and the AUTHOR's
    /// body padding is an ADDITIONAL inset inside it, defaulting to ZERO.
    /// Each side therefore resolves to `frame + declared`: nothing declared →
    /// 16 (unchanged, so every capture without a body pad is byte-identical);
    /// `padding: 0` → 16 (wave 24 gave 0 — the stale calibration this round
    /// repairs); `padding: 40px` → 56.
    ///
    /// PER SIDE, because the cascade is per-longhand: a ref declaring only
    /// `padding-left: 0` leaves the other three at the bare frame. Only
    /// CONCRETE px are honored — the converter emits runtime-dependent
    /// lengths (`em`, `%`, `calc()`) with no absolute value and this canvas
    /// has no honest answer for them, so such a side keeps the bare frame
    /// instead of guessing.
    ///
    /// The lookup reads `document.components` UNSPLIT, exactly like
    /// `canvasBackground` — the body's frame must apply even if that root
    /// were ever hoisted out of the flow stack.
    var resolvedPadding: EdgeInsets {
        guard let bodyRoot = document.components
            .first(where: { $0.meta?.role == "body-root" }) else {
            return EdgeInsets(top: Self.padding, leading: Self.padding,
                              bottom: Self.padding, trailing: Self.padding)
        }
        // One reader for all four sides. The runtime's ValueExtractors is
        // module-internal, so this reads the IRLength leaf through IRValue's
        // PUBLIC accessors — the two absolute-px shapes the wire actually
        // carries, in the same order Compose's ValueExtractors.extractDp
        // reads them (its doc records both generations): the top-level
        // `{"px": N}` and the typed-wrapper `{"original": {"px": N}}`.
        // Deliberately NOT resolving relative `{v,u}` pairs — the converter
        // leaves those unresolved because they need a runtime base this
        // canvas does not own, so such a side keeps the 16px default rather
        // than inventing a number (no silent fallthrough: the fallback IS
        // the documented contract, not an accident).
        func side(_ type: String) -> CGFloat {
            guard let data = bodyRoot.properties
                .first(where: { $0.type == type })?.data else { return Self.padding }
            guard let px = data["px"]?.doubleValue
                    ?? data["original"]?["px"]?.doubleValue
            else { return Self.padding }
            // CSS 2.1 §8.4 forbids negative padding; clamp the AUTHOR term so
            // a malformed IR can never pull content outside the frame — and
            // stack it ON the frame, which no author rule can cancel now.
            return Self.padding + max(0, CGFloat(px))
        }
        return EdgeInsets(
            top: side("PaddingTop"),
            leading: side("PaddingLeft"),
            bottom: side("PaddingBottom"),
            trailing: side("PaddingRight"),
        )
    }

    var body: some View {
        // Wave 17 — split the roots once per body eval (pure transform):
        // the flow half stacks in the padded VStack below, the hoisted
        // half mounts in the canvas-root overlay after the frame chain.
        let split = splitRoots
        // Wave 19 (RC-A5b) — Appendix E paint split of the hoisted half:
        // negative-z boxes mount BEHIND flow content (step 3), the rest
        // keep the wave-17 overlay (step 8). Computed once per body eval.
        let paint = FixedHoist.paintPartition(split.hoisted)
        // GAP 1 + RC-A4 — resolve each flow root's plan (UA + static
        // declared block margins, strip flags), then fold the margins with
        // adjacent collapse (no collapse at the padded top/bottom edges).
        // Wave 17: over the FLOW roots only (hoisted boxes take no space).
        let plans = rootPlans(for: split.flow)
        // wave-24 B-RC5 — the canvas pad, resolved ONCE per body eval (pure
        // over `document`): 16px per side unless this document's body-root
        // declares its own, exactly as an author `body { padding }` beats the
        // ref's zero-specificity `:where(body)` injection. See resolvedPadding.
        let pad = resolvedPadding
        // Wave-19 follow-up: the transparency-aware fold — a margin-
        // transparent (zero-flow) root keeps the §8.3.1 adjoining set open,
        // so {prev bottom, transparent margins, next top} emit ONE max() gap
        // instead of one gap per opaque neighbor (the full plan rides in,
        // not just the (top, bottom) projection).
        let spacing = UABlockMargin.stackedSpacing(plans: plans)
        let lastIndex = split.flow.count - 1
        return VStack(alignment: .leading, spacing: 0) {
            // enumerated()+offset id: roots are rendered positionally, never
            // reordered — a stable positional key is correct and avoids
            // relying on component.id uniqueness across a malformed doc.
            ForEach(Array(split.flow.enumerated()), id: \.offset) { idx, root in
                // Identical host shim the per-component canvas and the
                // engine's own child loop use — placement parent-data
                // attached (inert under this VStack), full ComponentRenderer
                // engine underneath. Zero per-node render difference.
                Group {
                    // Wave 18 (RC1) — a no-inset ABSOLUTE root stays in
                    // the flow stack (FixedHoist.split keeps it: its
                    // static position IS this slot, css-position-3 §3.1)
                    // but must reserve NO flow space (§2.1). The runtime's
                    // StaticPositionAnchor measures it at ideal size and
                    // reports 0×0 to this VStack — the S5 zero-report —
                    // so the next root starts where this box would have.
                    // Same classifier `split` used: one decision, two
                    // consumers, never disagreeing.
                    if FixedHoist.rendersInFlowAsStaticPosition(root) {
                        ComponentHost(component: root)
                            .modifier(StaticPositionAnchor())
                    } else {
                        ComponentHost(component: root)
                    }
                }
                    // RC-A4 — a stripped root renders with its block margins
                    // ZEROED through the runtime's §8.3.1 override channel:
                    // the declared values now live in the collapsed paddings
                    // below, so adjacent declared margins fold to max() like
                    // the browser. Inline margins are untouched, and the
                    // renderer rewrites the channel per child (root-only).
                    // Non-stripped roots write the default nil — identity.
                    .composedRootBlockMarginStrip(plans[idx].stripDeclared)
                    // GAP 1 — the block margin ABOVE this root: its full
                    // top margin for the first root (the canvas's 16px
                    // padding blocks parent↔child collapse there), or the
                    // previous root's bottom COLLAPSED with this root's top
                    // for interior roots. Declared margins contribute here
                    // ONLY when stripped from the host (RC-A4) — never
                    // double-counted.
                    .padding(.top, spacing.leading[idx])
                    // Only the LAST root carries the trailing bottom margin
                    // (again uncollapsed — the padded bottom edge). Interior
                    // bottoms are folded into the next root's leading gap.
                    .padding(.bottom, idx == lastIndex ? spacing.trailing : 0)
            }
        }
        // Constrain the composed content to the ref content box (358px at the
        // 16px default), anchored at the block-flow origin (top-leading) —
        // same maxWidth rule the per-component canvas applies to its single
        // component. wave-24 B-RC5: derived from the RESOLVED pad, so a ref
        // that zeroes its body padding gets the full 390px content box, the
        // same number Chromium gives that ref's body.
        .frame(maxWidth: Self.width - pad.leading - pad.trailing, alignment: .topLeading)
        // The ref's body padding — the exact offset the stitched path
        // dropped (web composedCanvasStyle carries it too). wave-24 B-RC5:
        // 16px per side by default, overridden per side by a body-root that
        // declares its own (see resolvedPadding).
        .padding(pad)
        // Frame to the full 390px width (min==max pins it) and floor the
        // height at the ref's 600px min; fixedSize(vertical) below lets the
        // surface adopt its natural height above that floor. Uses the
        // flexible-frame overload because SwiftUI has no width+minHeight form.
        .frame(minWidth: Self.width, maxWidth: Self.width,
               minHeight: Self.minHeight, alignment: .topLeading)
        // Natural (content) height beyond the 600 floor — mirrors the ref's
        // documentHeight capture and the per-component canvas's height rule.
        .fixedSize(horizontal: false, vertical: true)
        // Wave 19 (RC-A5b) — the NEGATIVE-z half of the hoisted list
        // paints in CSS 2.1 Appendix E step 3: behind ALL in-flow canvas
        // content, above only the canvas background. A `.background`
        // attached BEFORE the canvasBackground layer sits exactly there
        // (later .background modifiers paint further behind), and the
        // same full-frame attach point keeps the unpadded-origin anchor
        // the overlay half uses. dynamic-align-self-001's z:-1 red probe
        // otherwise covered the green abspos child pixel-for-pixel (the
        // wave18 iOS "green never paints" failure — see
        // FixedHoist.paintPartition). Empty half → no layer, view tree
        // otherwise identical.
        .background(alignment: .topLeading) {
            if !paint.behind.isEmpty {
                // wave-25 round 3: the frame inset rides BOTH z-partitions —
                // a negative-z hoisted box anchors in the same ICB as a
                // positive-z one (Appendix E reorders paint, not geometry).
                FixedHoistOverlay(components: paint.behind,
                                  canvasFrame: Self.padding)
            }
        }
        // GAP 2 — the ref canvas background: corpus-v4 WHITE by default, or
        // the document body-root's own background COMPOSITED over that white
        // when it declares one (opaque grey for a98rgb-003; translucent
        // rgba blends toward white — wave 15). Any sub-root gap paints this
        // so seams stay invisible against the ref. (The body-root lookup in
        // canvasBackground reads document.components UNSPLIT on purpose —
        // its background must paint even if that root were ever hoisted.)
        .background(canvasBackground)
        // Wave 17 (F1/F2) — the canvas-root out-of-flow overlay: attached
        // HERE, after the full-width frame chain and OUTSIDE the `.padding`
        // above, so it sees the WHOLE capture surface and applies its own
        // `canvasFrame` inset to reach the initial containing block. One
        // owner for that corner (the overlay), instead of two modifiers that
        // could drift.
        //
        // Wave 17 pinned the corner at the UNPADDED canvas origin (0,0) from
        // measured web behavior — an absolute root's `left:100` landed at
        // canvas x=100, not 116 — which was right while the ref framed its
        // pages with a CSS body pad that moves in-flow content only. Wave 25
        // CAL-RC1 moved that frame into IMAGE space, so ref abspos content
        // now translates with its prose and the corner is (16,16); hence the
        // `canvasFrame: Self.padding` argument.
        //
        // As an overlay it paints ABOVE all in-flow content (CSS 2.1
        // Appendix E step 8); order/z-index resolve inside
        // FixedHoistOverlay's ZStack. Wave 19 (RC-A5b): only the
        // NON-negative-z half mounts here — the negative-z half rides the
        // step-3 background above. Empty half → no overlay content, view
        // tree otherwise identical.
        .overlay(alignment: .topLeading) {
            if !paint.above.isEmpty {
                FixedHoistOverlay(components: paint.above,
                                  canvasFrame: Self.padding)
            }
        }
        // Publish the capture geometry so the runtime resolves vw/vh/% and
        // containing blocks against 390×844/358, not the device screen.
        // wave-24 B-RC5: the root containing block tracks the RESOLVED pad
        // (Self.viewport's 358 is the 16px-default case, byte-identical);
        // vw/vh are unaffected — the viewport is the canvas, not the body.
        .environment(\.styleViewport, StyleViewport(
            width: Double(Self.width),
            height: 844,
            rootContainingBlock: Double(Self.width - pad.leading - pad.trailing)
        ))
        // GAP 1 (WIDTH half) — publish the 358px content-box width so the
        // runtime stretches each auto-width, in-flow ROOT to full bleed like
        // the browser-ref's block `<p>`/`<div>` (iOS otherwise hugs content).
        // Set on the whole stack, but ComponentRenderer folds it into ROOTS
        // only and resets it for their children, so block-fill is root-scoped.
        // Composed WPT only (this canvas is built solely by
        // captureComposedDocument), and the fold is additionally gated on
        // wptCaptureMode — the 327-pair baseline never sees it.
        // wave-24 B-RC5: the fill width is the RESOLVED content box, so a
        // ref that zeroes its body padding block-fills to the full 390px —
        // matching what Chromium gives that ref's unpadded body.
        .environment(\.wptBlockFlowFillWidth, Self.width - pad.leading - pad.trailing)
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
