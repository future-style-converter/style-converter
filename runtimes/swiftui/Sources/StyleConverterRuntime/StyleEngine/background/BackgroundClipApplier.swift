//
//  BackgroundClipApplier.swift
//  StyleEngine/background — Phase 4.
//
//  Applies BackgroundClip via `.clipShape(_:)`. Since border-widths and
//  padding values aren't threaded into this modifier (they live in
//  BorderConfig and PaddingConfig respectively), the clip uses the
//  view's outer bounds; the `padding-box` case is approximated with a
//  1pt inset so a visible difference is rendered during fixture capture.
//  Exact inset matching is deferred until a later phase threads border +
//  padding into the background chain — see Phase 4 open issues.
//

import SwiftUI

struct BackgroundClipApplier: ViewModifier {
    // Config from BackgroundClipExtractor.
    let config: BackgroundClipConfig?

    func body(content: Content) -> some View {
        // Fast path.
        guard let cfg = config, cfg.hasAny else { return AnyView(content) }

        // border-box → no extra clip (SwiftUI's default).
        if cfg.mode == .borderBox { return AnyView(content) }

        // text → mask to glyphs. Complex; stub as identity so non-text
        // parts of the layout don't get wiped out. Documented.
        if cfg.mode == .text { return AnyView(content) }

        // padding-box / content-box: clip the background to an inset
        // rectangle. With no border/padding data available here we use
        // a coarse 1pt / 2pt inset — visual signal rather than pixel-
        // perfect. Future work: thread BorderConfig / PaddingConfig.
        let inset: CGFloat = cfg.mode == .contentBox ? 2 : 1
        return AnyView(
            content.clipShape(
                Rectangle().inset(by: inset)
            )
        )
    }
}

extension View {
    // Chain helper.
    func engineBackgroundClip(_ config: BackgroundClipConfig?) -> some View {
        modifier(BackgroundClipApplier(config: config))
    }
}
