//
//  UnsupportedFontMetaConfig.swift
//  StyleEngine/typography/unsupported — Phase 6.
//
//  Font descriptor metadata + synthesis toggles. SwiftUI picks a concrete face at render time; these hints have no runtime analogue.
//
//  Grouped per the Phase 6 brief's explicit authorisation: SwiftUI
//  cannot render these families meaningfully, so we keep a single
//  Config/Extractor/Applier triplet per family rather than ~60 tiny
//  identity triplets.
//

import Foundation

struct UnsupportedFontMetaConfig: Equatable {
    /// Map of property type → raw keyword or stringified value. Preserved so
    /// the coverage audit in `PropertyRegistry.allRegistered()` still sees
    /// every property name flow through a dedicated extractor.
    var rawByType: [String: String] = [:]
    /// True when the extractor saw at least one owned property in the IR.
    var touched: Bool = false
}
