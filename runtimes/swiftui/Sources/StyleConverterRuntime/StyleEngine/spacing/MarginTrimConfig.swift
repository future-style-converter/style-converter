//
//  MarginTrimConfig.swift
//  StyleEngine/spacing — Phase 2.
//
//  `margin-trim` is a CSS Paged Media feature — it tells a container to
//  absorb the margins of its children at the given edges. SwiftUI has no
//  equivalent, so we record the value and let the applier render as a
//  no-op. Keeping the config means if later phases introduce a custom
//  layout container with the trim concept the wiring is already there.
//

// Foundation only.
import Foundation

// The seven enum tags the Kotlin converter emits as bare strings.
enum MarginTrimMode: String, Equatable {
    case none = "NONE"
    case block = "BLOCK"
    case inline = "INLINE"
    case blockStart = "BLOCK_START"
    case blockEnd = "BLOCK_END"
    case inlineStart = "INLINE_START"
    case inlineEnd = "INLINE_END"
}

// Wrapper struct — mirrors the other spacing configs so PropertyRegistry
// wiring stays uniform.
struct MarginTrimConfig: Equatable {
    // Resolved mode. Defaults to `.none`, matching CSS.
    var mode: MarginTrimMode = .none
}
