//
//  RootCanvasClip.swift
//  StyleEngine/effects/clip — wave 49 (lane A4).
//
//  The DOCUMENT-ELEMENT half of `clip-path`. Twin of the web runtime's
//  RootClipPathResolver.ts and of Compose's RootCanvasClip.kt.
//
//  ── THE DEFECT (measured) ─────────────────────────────────────────────
//  css-masking-1 §5: a non-`none` `clip-path` clips the element AND its
//  descendants. On the DOCUMENT element that covers the whole page — the
//  background css-backgrounds-3 §2.11.2 propagates to the canvas included,
//  which the fxtf compositing §rootgroup / §pagebackdrop chain composites
//  through the root's own group. The WPT test says it outright in its
//  `meta name=assert`: "Clip-path on the document element applies to the
//  root background."
//
//  The TITAN extractor merges every `html` / `body` / `:root` / `*` rule
//  into ONE synthetic `meta.role: "body-root"` component, and the flat IR
//  v2 wire makes that component a SIBLING of the document's top-level
//  boxes rather than their parent — so the root's clip landed on an empty
//  box with nothing inside it to clip. MEASURED on the wave-48 gate, WPT
//  css-masking/clip-path/clip-path-document-element and its
//  `-will-change` twin:
//     Chromium ref     green 7500 px, an "L" at image [66,66]-[165,165]
//     all 3 platforms  green 187 000 px (79.91 %) + red 47 000 px (20.09 %)
//  — 0.5367 against the ref on all six cells.
//
//  ── WHAT THIS FILE PROVIDES ───────────────────────────────────────────
//  `RootCanvasClip.config(_:)` answers "does the document element declare
//  a clip?", and `View.rootCanvasClip(_:frame:)` applies it: the clipped
//  content keeps whatever background the canvas already painted (the
//  propagated root background), and the WPT canvas default is painted
//  BEHIND the clip as the page backdrop, so everything outside the clip
//  reads as the ref's white.
//
//  ── WHY `frame` ───────────────────────────────────────────────────────
//  The clip's lengths are measured from the ROOT element's border box,
//  which is the INITIAL CONTAINING BLOCK — the framed content corner, not
//  the image corner (CaptureCanvas's wave-25 CAL-RC1 history). The modifier
//  rides the OUTER capture surface, so the frame is handed to
//  `ClipApplier.outerInset`, which insets the rect every shape resolves
//  against. Verified by construction against the frozen ref:
//  polygon(50px 50px …) must land at image (66,66), and the ref's ink
//  bounding box is exactly [66,66]-[165,165].
//

import SwiftUI

/// Namespace for the document-element clip.
public enum RootCanvasClip {

    /// The `meta.role` marker the extractor stamps on the merged root bag.
    private static let bodyRootRole = "body-root"

    /// The document element's `clip-path`, or nil when it declares none.
    ///
    /// Read through the SAME `ClipExtractor` the live style chain uses, so
    /// the canvas can never disagree with what a component would have
    /// clipped with — the one-engine-hop rule `CaptureCanvas`'s background /
    /// padding resolvers already follow.
    ///
    /// Deliberately NOT gated on containment: css-contain-2 §2 and
    /// css-contain-1 §2 take a contained root off the PROPAGATION path
    /// for background / writing-mode / direction, but a clip-path is not
    /// propagated to anything — it is the root's own clip over its own
    /// subtree.
    ///
    /// Nil for: no body-root, no `ClipPath` leaf, an explicit `none`, and a
    /// `url(#id)` reference (which this platform cannot resolve — the
    /// applier leaves such a view UNCLIPPED, so accepting it here would
    /// repaint the page backdrop white for a clip that never happens).
    /// Internal because `ClipConfig` is: the public surface is the View
    /// extension below, which takes the components instead.
    static func config(_ components: [IRComponent]) -> ClipConfig? {
        // One body per document, one synthetic bag for it.
        guard let bodyRoot = components.first(where: { $0.meta?.role == bodyRootRole })
        else { return nil }
        guard let cfg = ClipExtractor.extract(from: bodyRoot.properties), cfg.touched
        else { return nil }
        // Only a shape that this platform actually paints counts. The legacy
        // `clip` rect is deliberately not accepted: CSS 2.1 §11.1.2 defines
        // it on the element's own box for absolutely positioned elements, it
        // is not a subtree clip, so it is not the document-element clip this
        // file is about.
        switch cfg.shape {
        case .none, .some(.none), .some(.url(_)):
            return nil
        default:
            return cfg
        }
    }

    /// Does this document declare a document-element clip? Public so a
    /// harness can branch its canvas chain without seeing `ClipConfig`.
    public static func declaresRootClip(_ components: [IRComponent]) -> Bool {
        config(components) != nil
    }
}

public extension View {
    /// Apply the document-element clip to a composed capture canvas.
    ///
    /// - Parameters:
    ///   - components: the document's component list, UNSPLIT — the root bag
    ///     must be found even if it were ever hoisted, the same rule
    ///     `CaptureCanvas.canvasBackground` states for its own lookup.
    ///   - frame: the inset between this view's bounds — the OUTER, framed
    ///     capture surface — and the ICB the clip's lengths are measured
    ///     from. The same `CaptureCanvas.padding` the out-of-flow overlay
    ///     anchors against, threaded in rather than re-derived.
    ///
    /// A document with no root clip renders unchanged — no clip, no extra
    /// background layer — so every other capture is pixel-identical.
    @ViewBuilder
    func rootCanvasClip(_ components: [IRComponent], frame: CGFloat) -> some View {
        if let cfg = RootCanvasClip.config(components) {
            // The clip wraps everything already composed INTO this view,
            // which at the canvas's attach point is the root forest plus the
            // propagated root background — exactly the group css-masking-1
            // §5 clips. The WPT canvas default then paints BEHIND it as the
            // page backdrop (a later `.background` sits further back), so
            // outside the clip the capture reads white, like the ref.
            self.modifier(ClipApplier(config: cfg, outerInset: frame))
                .background(WPTCanvas.background)
        } else {
            self
        }
    }
}
