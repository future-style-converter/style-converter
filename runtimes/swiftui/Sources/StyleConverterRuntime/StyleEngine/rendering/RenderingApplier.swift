//
//  RenderingApplier.swift
//  StyleEngine/rendering — Phase 10.
//
//  Identity. image-rendering maps ambiguously in SwiftUI (nearest vs
//  linear is available via `.interpolation(.none|.high)` on Image
//  only, and the SDUI runtime doesn't control Image directly).
//  Everything else has no analog.
//
//  TODO(phase-11): plumb image-rendering through the Image pipeline
//  (requires a hook where SDUI instantiates Image nodes).
//
//  ZOOM IS NO LONGER THIS FILE'S BUSINESS (wave-37 zoom convergence).
//  The wave-36 TODO here said `zoom` was extracted-but-not-applied and
//  that `.scaleEffect` alone could not fix it — the second half was
//  right, the first is now obsolete. `zoom` has a real dedicated triplet
//  in this folder: ZoomConfig / ZoomExtractor / ZoomApplier, where
//  ZoomLayout moves the LAYOUT SLOT (measure at proposal/zoom, report
//  size×zoom) and a size-transparent `.scaleEffect(anchor: .topLeading)`
//  supplies the paint. It is chained as the outermost node in
//  StyleBuilder.applyGroupEffects, and Compose has the byte-parallel
//  twin, so all three runtimes now render `zoom` rather than web alone.
//  "Zoom" stays in RenderingProperty.names for registry ownership; the
//  string this identity applier records for it is inert.
//

import Foundation

enum RenderingApplier {
    static func contribute(_ cfg: RenderingConfig?) { _ = cfg }
}
