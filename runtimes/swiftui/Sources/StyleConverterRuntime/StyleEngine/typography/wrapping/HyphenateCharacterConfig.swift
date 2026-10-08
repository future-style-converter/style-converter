//
//  HyphenateCharacterConfig.swift
//  StyleEngine/typography/wrapping — Phase 6; made real in wave 54 (lane L3, U2-ios).
//
//  css-text-4 §6.3 `hyphenate-character: auto | <string>` — the string a taken
//  hyphenation opportunity paints at the end of its line (css-text-3 §5.3).
//  Phase 6 captured a lower-cased "keyword" that no consumer read; the value
//  now rides to PlaceholderLabel's greedy pre-break, where it is measured and
//  painted (see HyphenateCharacterApplier).
//

// Foundation only: the Config is plain data, no SwiftUI.
import Foundation

/// The element's own `hyphenate-character` declaration.
struct HyphenateCharacterConfig: Equatable {
    /// The author's string VERBATIM — `""` included (WPT css-text/hyphens/
    /// hyphenate-character-001, "no visible hyphens appear"), never
    /// lower-cased (it is painted, not matched). nil = `auto`: the UA hyphen
    /// (`AutoHyphenation.defaultHyphenCharacter`, U+2010).
    var value: String? = nil
}
