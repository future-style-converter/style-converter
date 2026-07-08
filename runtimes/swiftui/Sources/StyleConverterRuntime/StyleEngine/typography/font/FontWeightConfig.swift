//
//  FontWeightConfig.swift
//  StyleEngine/typography/font — Phase 6.
//
//  CSS `font-weight` accepts keywords (`normal`, `bold`, `bolder`,
//  `lighter`) and numbers 1-1000. The IR normalizes keywords to their
//  100–900 numeric equivalent. See ValueTypes.kt / FontWeightPropertyParser.kt.
//

import SwiftUI

/// Pre-mapped SwiftUI Font.Weight. `nil` → inherit.
struct FontWeightConfig: Equatable {
    /// SwiftUI Font.Weight bucket corresponding to the numeric weight.
    var weight: Font.Weight? = nil
}
