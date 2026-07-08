//
//  BackgroundSizeApplier.swift
//  StyleEngine/background — Phase 4.
//
//  SwiftUI can't reliably size CSS background-image layers when those
//  layers are gradients — gradients naturally fill their container, and
//  SwiftUI has no "sized background layer" primitive. This applier is
//  therefore a stub that preserves the parsed config for diagnostics
//  and leaves rendering untouched. Documented limitation in Phase 4.
//
//  When a future phase adds raster URL support, this file gains a
//  GeometryReader branch that applies `.aspectRatio(_, contentMode:)`
//  for cover/contain. Per-file line budget kept small intentionally.
//

import SwiftUI

struct BackgroundSizeApplier: ViewModifier {
    // Kept nullable so StyleBuilder can chain unconditionally. Nil and
    // any value mean "render unchanged" at the moment.
    let config: BackgroundSizeConfig?

    func body(content: Content) -> some View {
        // No-op today. See file header for the rationale.
        content
    }
}

extension View {
    // Chain helper — currently identity. Reserved for future work.
    func engineBackgroundSize(_ config: BackgroundSizeConfig?) -> some View {
        modifier(BackgroundSizeApplier(config: config))
    }
}
