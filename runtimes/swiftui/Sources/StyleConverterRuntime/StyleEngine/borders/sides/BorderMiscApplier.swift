//
//  BorderMiscApplier.swift
//  StyleEngine/borders/sides — Phase 5.
//
//  Identity modifier for the three keyword-only miscellaneous border
//  properties. We capture them in the config so the coverage report
//  stays honest, but iOS has no rendering obligation for any of the
//  three:
//    - `box-decoration-break` is a pagination hint; SwiftUI doesn't
//      fragment elements across pages.
//    - `corner-shape` is a draft spec that would need a bespoke Shape
//      per corner — deferred to a follow-up phase once the draft lands.
//    - `border-boundary` is proposal-stage; nothing to do even on Web.
//
//  The applier emits a single log line per component so fixtures make
//  the "captured but deferred" status obvious in simulator output.
//

import SwiftUI

struct BorderMiscApplier: ViewModifier {
    let config: BorderMiscConfig?

    func body(content: Content) -> some View {
        // Nil / empty → true identity with no side effect.
        guard let cfg = config, cfg.hasAny else { return AnyView(content) }
        // Logging side-effect: SwiftUI's view body can run many times;
        // keep the log concise and single-line so it doesn't dominate
        // the simulator console.
        #if DEBUG
        print("[BorderMisc] deferred keywords captured: "
              + "decorationBreak=\(cfg.decorationBreak?.rawValue ?? "-") "
              + "cornerShape=\(cfg.cornerShape?.rawValue ?? "-") "
              + "borderBoundary=\(cfg.borderBoundary ?? "-")")
        #endif
        return AnyView(content)
    }
}

// View chain helper.
extension View {
    func engineBorderMisc(_ config: BorderMiscConfig?) -> some View {
        modifier(BorderMiscApplier(config: config))
    }
}
