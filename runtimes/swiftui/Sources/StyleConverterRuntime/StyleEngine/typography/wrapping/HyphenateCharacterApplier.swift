//
//  HyphenateCharacterApplier.swift
//  StyleEngine/typography/wrapping — Phase 6; made real in wave 54 (lane L3, U2-ios).
//
//  WHERE IT LANDS ON iOS. SwiftUI `Text` has no hyphenate-character API, and
//  the TextKit route (NSParagraphStyle / a UIKit label) cannot be rasterised by
//  ImageRenderer (GreedyLineBreaker's banner). The one place this runtime
//  PAINTS a hyphen of its own is PlaceholderLabel's greedy pre-break
//  (`GreedyLineBreaker.lines(hyphenChar:)`): a taken U+00AD — or a CF-dictionary
//  point under `hyphens: auto` — becomes a real character at the line end,
//  measured and rendered like any other glyph. So the string must enter the
//  FOLD (its width decides the breaks: `real` fits 4.5ch only with `""`), not be
//  substituted afterwards. This applier puts it on the aggregate; StyleBuilder
//  mirrors it into TextConfig; the label passes it as `hyphenChar` and as the
//  `spentHyphen` of the overflow check (wave-54 seam-2, SpentHyphen.swift).
//
//  nil (no declaration, or `auto`) leaves `hyphenChar` at the UA default and
//  `spentHyphen` nil, so every other run is byte-identical by construction.
//

// Foundation only: the reducer writes plain aggregate state.
import Foundation

/// css-text-4 §6.3 contribution to the typography aggregate.
enum HyphenateCharacterApplier {
    /// Mirror the element's declaration onto the aggregate.
    static func contribute(_ cfg: HyphenateCharacterConfig?, into agg: inout TypographyAggregate) {
        // No declaration on this element → leave the aggregate untouched.
        guard let cfg = cfg else { return }
        // Last-write-wins like every aggregate field; `auto` writes nil.
        agg.hyphenateCharacter = cfg.value
        // `touched` is what makes StyleBuilder mirror the aggregate into
        // TextConfig at all (the HyphensApplier lesson: an element whose only
        // typography declaration is this one would otherwise drop it). Raised
        // only for a real string — `auto` changes nothing and must leave a
        // property-less aggregate nil exactly as before.
        if cfg.value != nil { agg.touched = true }
    }
}
