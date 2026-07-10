//
//  BackgroundClipApplier.swift
//  StyleEngine/background — Phase 4 (rewired in fidelity wave 1).
//
//  `background-clip` no longer clips HERE. CSS Backgrounds 3 §2.4 says
//  the property restricts the *background painting area* only — it must
//  never clip the element's content or borders. The old implementation
//  applied a coarse `.clipShape(Rectangle().inset(...))` to the whole
//  view, which sliced off content AND was numerically wrong (1–2pt
//  instead of the real border/padding bands).
//
//  The real work now happens where the background actually paints:
//  StyleBuilder computes the concrete shrink band (border widths for
//  padding-box, border+padding for content-box) and threads it into
//  ColorApplier / BackgroundImageApplier as `clipInsets`. This modifier
//  is retained as a chain marker so the applyStyle chain shape (and the
//  per-property triplet contract) stays intact; `text` mode is likewise
//  handled upstream (PlaceholderLabel paints the gradient as glyph fill).
//

import SwiftUI

struct BackgroundClipApplier: ViewModifier {
    // Config from BackgroundClipExtractor. Kept for chain-shape parity;
    // consumed for paint-area computation in StyleBuilder instead.
    let config: BackgroundClipConfig?

    func body(content: Content) -> some View {
        // Identity — see the header. The paint-area restriction lives in
        // the background paint chain (ColorApplier/BackgroundImageApplier).
        content
    }
}

extension View {
    // Chain helper.
    func engineBackgroundClip(_ config: BackgroundClipConfig?) -> some View {
        modifier(BackgroundClipApplier(config: config))
    }
}
