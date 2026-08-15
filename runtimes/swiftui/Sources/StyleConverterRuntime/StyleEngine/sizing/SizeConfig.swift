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

// css-sizing-3 §3 `box-sizing` keyword. Only the two spec values exist —
// the TRI-STATE the sizing lane needs (unset ≠ content-box!) is carried
// by Optional wrapping on SizeConfig.boxSizing below: `nil` means "the IR
// never declared box-sizing", which must keep today's border-box frames
// byte-stable (the whole width+padding fixture corpus is captured against
// the web harness's `* { box-sizing: border-box }` reset).
enum BoxSizingKeyword: Equatable {
    // Declared size = content box; frame = content + padding + border.
    case contentBox
    // Declared size = border box (the pre-existing iOS chain behaviour).
    case borderBox
}

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

    // css-values-5 calc-size() typed values (wave 42 lane W3) — the two
    // slots iOS consumes (preferred width/height). A slot carries EITHER
    // its LengthValue OR its calc value, never both (SizeExtractor routes
    // exclusively). Min*/Max* calc-size deliberately has NO slot here —
    // see SizeExtractor's calc-size comment for the measured record (the
    // flex family PASSES on the intrinsic fallback those slots keep).
    var widthCalc: CalcSizeValue? = nil
    var heightCalc: CalcSizeValue? = nil

    // css-sizing-3 §3 `box-sizing`. Nil = the IR never declared it (the
    // border-box status quo must not change); `.contentBox` makes the
    // applier inflate explicit width/height by the padding + border
    // bands so the FRAME equals content + padding + border.
    var boxSizing: BoxSizingKeyword? = nil

    // True when any sizing field is populated. Callers use this to skip
    // attaching SizeApplier altogether when the bag is empty.
    // `boxSizing` is deliberately EXCLUDED: box-sizing only changes how
    // definite width/height resolve (css-sizing-3 §3) — with no size to
    // reinterpret the applier has nothing to do, so a lone box-sizing
    // declaration must not force the modifier onto the chain.
    var hasAny: Bool {
        width != nil || height != nil ||
        minWidth != nil || maxWidth != nil ||
        minHeight != nil || maxHeight != nil ||
        aspectRatio != nil ||
        // calc-size slots size the box too — without these the applier's
        // fast path would skip a component whose ONLY sizing is a typed
        // calc-size width (exactly the calc-size-min-max inner-div shape).
        widthCalc != nil || heightCalc != nil
    }
}
