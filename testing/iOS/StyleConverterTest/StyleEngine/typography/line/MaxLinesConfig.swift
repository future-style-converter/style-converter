//
//  MaxLinesConfig.swift
//  StyleEngine/typography/line — Phase 6.
//
//  `max-lines` is the CSS Overflow 4 equivalent of `line-clamp` for
//  fragmenting layout. On SwiftUI we collapse it to the same lineLimit
//  effect as LineClamp.
//

import Foundation

struct MaxLinesConfig: Equatable { var lines: Int? = nil }
