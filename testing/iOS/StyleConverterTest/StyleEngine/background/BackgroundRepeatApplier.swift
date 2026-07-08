//
//  BackgroundRepeatApplier.swift
//  StyleEngine/background — Phase 4.
//
//  Stub. SwiftUI gradients don't repeat — they always fill the container.
//  For future raster URL layers, a tiling `Image(uiImage:).resizable()`
//  wrapped with a tiled rendering mode would implement `repeat`; that
//  work is deferred. The applier here is identity and exists only for
//  per-property contract symmetry. Documented limitation.
//

import SwiftUI

struct BackgroundRepeatApplier: ViewModifier {
    // Config held for diagnostics and future extension.
    let config: BackgroundRepeatConfig?

    func body(content: Content) -> some View {
        // Intentional no-op today.
        content
    }
}

extension View {
    // Symmetric chain helper — reserved for future tiling work.
    func engineBackgroundRepeat(_ config: BackgroundRepeatConfig?) -> some View {
        modifier(BackgroundRepeatApplier(config: config))
    }
}
