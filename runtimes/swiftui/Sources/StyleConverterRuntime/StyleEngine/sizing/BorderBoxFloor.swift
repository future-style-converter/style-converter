//
//  BorderBoxFloor.swift
//  StyleEngine/sizing — wave-40 lane T7 (iOS depth tails).
//
//  css-ui-3 §5 `box-sizing: border-box`, the clause every implementation has
//  to spell out separately: the declared size sets the BORDER box, the content
//  box is what is left after the padding + border bands, and "the content
//  width/height cannot be negative — it is floored at zero". A declared size
//  SMALLER than its own bands therefore does not shrink the box; it produces a
//  zero-content box whose used border-box size IS the band sum.
//
//  ## The defect this closes (measured, not theoretical)
//
//  wave39-final css-ui/box-sizing-026 —
//  `#test { box-sizing: border-box; border: 50px green solid;
//           width: 10px; height: 10px }` over an abspos `z-index: -1` red
//  100×100 square. The browser ref (and web, which passes) paints a solid
//  100×100 GREEN square: the 10px declared size floors up to the 100px band
//  sum and the green border fills it, hiding the red. iOS took the 10px
//  literally, so the red square showed through and the cell failed on the
//  colour gate alone (ssim 0.9974, `colorFailed: true`, composite 0.9333) —
//  the shape was already right, only the colour was wrong. box-sizing-001,
//  the same red-behind-green shape WITHOUT the under-band size, passes on iOS
//  today, which is what pins the diagnosis to the floor and not to z-index.
//
//  ## Trigger scope, enumerated rather than assumed
//
//  ONLY an EXPLICIT `box-sizing: border-box` declaration arms this. That is
//  not timidity, it is the runtime's own model: SizeConfig.boxSizing is a
//  tri-state and `nil` means "the IR declared nothing", a state the iOS chain
//  treats as border-box for FRAME purposes while the CSS default is
//  content-box. Flooring the nil case would therefore inflate boxes CSS sizes
//  from their content — measured across all 30 wave39-final sections, a
//  nil-inclusive predicate fires on 46 currently-PASSING iOS cells (the
//  css-grid/css-flexbox abspos-staticpos families, `width: 4px` inside 1px
//  borders). The explicit-only predicate fires on exactly ONE cell corpus-wide
//  — box-sizing-026 — and it is a failing one, so this rule cannot cost a pass.
//
//  ## Why here and not in SizeApplier
//
//  The floor needs the padding AND border bands, which live on sibling configs
//  (`spacing.padding`, `borderSides`); SizeApplier receives only SizeConfig.
//  StyleBuilder is the one place holding all three, and it already computes
//  exactly these bands for the content-box inflation — so the floor reuses
//  `contentBoxInflation`'s own band helpers instead of growing a second,
//  drift-prone copy of "what counts as a border".
//
//  TWIN STATUS: iOS-only in wave 40. Android shows the same red square in the
//  same capture; the Compose twin (`sizing/BorderBoxFloor.kt`) is DEFERRED
//  because this lane owns no Android device.
//

import CoreGraphics

enum BorderBoxFloor {

    /// The floored declared length for one axis.
    ///
    /// - Parameters:
    ///   - declared: the axis's declared LengthValue, or nil when absent.
    ///   - bandPx: that axis's padding + border sum, in px.
    /// - Returns: the value to use. Only an `.exact` declaration BELOW the
    ///   band sum changes — everything else (auto, intrinsic, percent, calc,
    ///   fr, nil) is returned verbatim, because a floor needs a definite
    ///   number on both sides to be a floor rather than a guess.
    static func floored(_ declared: LengthValue?, bandPx: CGFloat) -> LengthValue? {
        // No band to floor against (the overwhelming case: no border, no
        // padding) — nothing this rule can do, and the identity keeps every
        // existing capture bit-stable.
        guard bandPx > 0 else { return declared }
        // Only a definite px declaration is comparable. A percent/calc width
        // resolves later against a containing block this helper never sees,
        // so flooring it here would be arithmetic on two different bases.
        guard case .exact(let px)? = declared else { return declared }
        guard px < Double(bandPx) else { return declared }
        return .exact(px: Double(bandPx))
    }

    /// Apply the floor to a fully-built style, in place.
    ///
    /// Fires only on an EXPLICIT `box-sizing: border-box` (see the header's
    /// scope note); every other component — including every component that
    /// declares no box-sizing at all — leaves this function byte-unchanged.
    static func apply(to style: inout ComponentStyle,
                      bands: (h: CGFloat, v: CGFloat)) {
        guard style.size.boxSizing == .borderBox else { return }
        style.size.width  = floored(style.size.width,  bandPx: bands.h)
        style.size.height = floored(style.size.height, bandPx: bands.v)
    }
}
