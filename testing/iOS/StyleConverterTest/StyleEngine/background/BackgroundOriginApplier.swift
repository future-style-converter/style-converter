//
//  BackgroundOriginApplier.swift
//  StyleEngine/background — Phase 4.
//
//  Stub — background-origin only has a visible effect when combined with
//  an explicit background-position, which SwiftUI can't apply to
//  gradients. The config is preserved for future work / diagnostics.
//

import SwiftUI

struct BackgroundOriginApplier: ViewModifier {
    let config: BackgroundOriginConfig?

    func body(content: Content) -> some View {
        // Identity — see file header.
        content
    }
}

extension View {
    func engineBackgroundOrigin(_ config: BackgroundOriginConfig?) -> some View {
        modifier(BackgroundOriginApplier(config: config))
    }
}
