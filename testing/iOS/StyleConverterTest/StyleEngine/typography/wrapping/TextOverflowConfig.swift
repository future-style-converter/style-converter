//
//  TextOverflowConfig.swift
//  StyleEngine/typography/wrapping — Phase 6.
//
//  `text-overflow: clip | ellipsis | fade() | <string>`. SwiftUI maps
//  ellipsis → `.truncationMode(.tail)`, clip → `.truncationMode(.head)`
//  doesn't fit — but `.lineLimit` + no truncationMode produces clip.
//  Custom-string variants fall back to ellipsis.
//

import Foundation

enum TextOverflowMode: Equatable { case clip, ellipsis, fade, customString(String) }

struct TextOverflowConfig: Equatable { var mode: TextOverflowMode? = nil }
