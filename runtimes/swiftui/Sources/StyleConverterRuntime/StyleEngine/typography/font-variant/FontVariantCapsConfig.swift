//
//  FontVariantCapsConfig.swift
//  StyleEngine/typography/font-variant — Phase 6.
//
//  `font-variant-caps` keywords: normal | small-caps | all-small-caps |
//  petite-caps | all-petite-caps | unicase | titling-caps.
//  SwiftUI Text has `.smallCaps()` (iOS 16+) but nothing for the rest;
//  we collapse the spec to a simple small-caps flag + TODOs.
//

import Foundation

/// Mode selected; `nil` means the property was absent.
enum FontVariantCapsMode: Equatable {
    case normal, smallCaps, allSmallCaps, petiteCaps, allPetiteCaps, unicase, titlingCaps
}

struct FontVariantCapsConfig: Equatable { var mode: FontVariantCapsMode? = nil }
