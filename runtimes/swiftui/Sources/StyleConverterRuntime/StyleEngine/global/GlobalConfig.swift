//
//  GlobalConfig.swift
//  StyleEngine/global — Phase 10.
//
//  The `all` shorthand. TRUE per-property reset semantics (initial /
//  inherit / unset / revert / revert-layer resolved against the parent
//  computed style, css-cascade-4 §3.2) still need machinery the SDUI
//  runtime does not maintain, and THIS STRUCT is still inert — nothing
//  reads `rawByType` and there is no GlobalApplier.
//
//  But `all` is NOT unhandled: retro P2e (finding A6#15, phrase sweep)
//  replaced "Identity until that machinery exists", which had outlived
//  wave 18 by thirty waves. GlobalExtractor.applyingAllReset implements
//  the observable common case — any recognizable `all` keyword DROPS
//  every other declaration so the element collapses to its untouched
//  defaults — and ComponentRenderer.mergedProperties calls it after the
//  inheritance merge (twin of Compose's `allReset`). Pinned in
//  ContentsUnboxingTests (:182, :190).
//

import Foundation

struct GlobalConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
