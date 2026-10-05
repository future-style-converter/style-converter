//
//  GlobalConfig.swift
//  StyleEngine/global — Phase 10.
//
//  The `all` shorthand. THIS STRUCT is still inert — nothing reads
//  `rawByType` and there is no GlobalApplier: `all` has no paint of its own.
//
//  `all` is NOT unhandled, though. Wave 52 (lane L11) replaced the wave-18
//  drop-everything with GlobalExtractor.applyingAllReset(own:inherited:),
//  the ORDER-AWARE reset (css-cascade-4 §6.4 / §3.1 / §7.3): declarations
//  BEFORE the last `all` drop (except `direction` / `unicode-bidi`),
//  declarations AFTER it are kept, and the inherited channel is dropped
//  for `initial` and kept for `inherit` / `unset` / `revert*`.
//  ComponentRenderer.mergedProperties runs it on the OWN list and the
//  inherited channel BEFORE InheritedText.merge — twin of Compose
//  `global/AllReset.kt` and web `applyAllReset` (engine/global/_dispatch.ts).
//  Pinned in AllResetTests.swift (verbatim wave51-fix all-prop-* IR).
//

import Foundation

struct GlobalConfig: Equatable {
    // Owned type → its keyword (or raw payload description); inert record.
    var rawByType: [String: String] = [:]
    // True once any owned type was seen (extract returns nil otherwise).
    var touched: Bool = false
}
