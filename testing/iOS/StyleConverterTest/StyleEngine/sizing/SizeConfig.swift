//
//  SizeConfig.swift
//  StyleEngine/sizing — Phase 3.
//
//  Canonical per-component sizing config. Each axis carries a full
//  `LengthValue` (not a CGFloat) so unit-resolution can defer until
//  the applier knows the parent width. Logical sides (BlockSize /
//  InlineSize etc.) are resolved to physical at extract time assuming
//  LTR horizontal writing mode — the only mode iOS targets today.
//
//  AspectRatio lives on its own field because its IR shape is disjoint
//  from LengthValue (see AspectRatioValue.swift).
//

// Foundation only — no SwiftUI dependency so SizeConfig stays testable.
import Foundation

// Unified sizing bundle. A `nil` LengthValue means the property was not
// present in the IR; the applier can use that to skip attaching a
// `.frame(...)` modifier on that axis. `.none` / `.auto` / etc. are
// distinct states carried inside the LengthValue itself.
struct SizeConfig: Equatable {
    // Physical width / height.
    var width: LengthValue? = nil
    var height: LengthValue? = nil

    // Min/Max constraints. `.none` arrives here when `max-*: none`.
    var minWidth: LengthValue? = nil
    var maxWidth: LengthValue? = nil
    var minHeight: LengthValue? = nil
    var maxHeight: LengthValue? = nil

    // `aspect-ratio` — own type because IR shape diverges from lengths.
    // Nil means the property was absent (distinct from `.isAuto`).
    var aspectRatio: AspectRatioValue? = nil

    // True when any sizing field is populated. Callers use this to skip
    // attaching SizeApplier altogether when the bag is empty.
    var hasAny: Bool {
        width != nil || height != nil ||
        minWidth != nil || maxWidth != nil ||
        minHeight != nil || maxHeight != nil ||
        aspectRatio != nil
    }
}
