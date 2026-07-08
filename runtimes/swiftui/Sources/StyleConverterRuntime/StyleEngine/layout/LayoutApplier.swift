//
//  LayoutApplier.swift
//  StyleEngine/layout — Phase 7, step 1 (scaffold only).
//
//  Consumes a single LayoutAggregate and emits the SwiftUI container
//  + per-item modifiers in one pass. Kept separate from per-property
//  Appliers so display+flex-direction+justify-content+align-items can
//  be fused into one `HStack(alignment:spacing:)` / `VStack(...)` /
//  `LazyVGrid(columns:...)` construction — SwiftUI's stack initialisers
//  take their configuration as constructor args, so a late per-modifier
//  chain cannot reshape the container after the fact.
//
//  STEP 1: identity. The applier returns the incoming view unchanged
//  regardless of aggregate content, because no extractor populates the
//  aggregate yet. Step 2 will add the flexbox container-builder; steps
//  3-5 extend it with grid, position, and advanced handling.
//

import SwiftUI

enum LayoutApplier {

    /// STEP 1 IMPLEMENTATION: identity. Returns `view` unchanged.
    ///
    /// TODO (Phase 7 step 2+): replace the identity pass-through with a
    /// real container builder driven by `aggregate.display` +
    /// `aggregate.flexDirection`, then layer on:
    ///   - step 3: grid containers via `LazyVGrid` / `LazyHGrid`
    ///   - step 4: per-child `.position(...)` / `.offset(...)` for
    ///             `position: absolute|relative|fixed`
    ///   - step 5: advanced anchor-positioning + motion-path modifiers
    /// Until then every layout property continues to flow through the
    /// legacy `StyleBuilder` path exactly as today.
    static func apply(_ aggregate: LayoutAggregate?, to view: AnyView) -> AnyView {
        // Fast path + only path in step 1: return the view untouched.
        // Consuming `aggregate` here would trip Swift's unused-parameter
        // warning, so we explicitly discard it.
        _ = aggregate
        return view
    }
}
