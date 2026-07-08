//
//  HyphensConfig.swift
//  StyleEngine/typography/wrapping — Phase 6.
//
//  `hyphens`: none | manual | auto. SwiftUI has no hyphenation toggle on
//  `Text`; captured for future UILabel routing.
//

import Foundation

struct HyphensConfig: Equatable { var mode: String? = nil }
