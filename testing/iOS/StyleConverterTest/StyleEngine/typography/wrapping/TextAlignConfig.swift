//
//  TextAlignConfig.swift
//  StyleEngine/typography/wrapping — Phase 6.
//
//  `text-align`: keyword mapped to SwiftUI TextAlignment. `start`/`end`
//  collapse to leading/trailing; `justify` has no native SwiftUI support
//  (iOS 17+ has `.multilineTextAlignment(...)` only for the three
//  fundamentals) so we fall back to leading.
//

import SwiftUI

struct TextAlignConfig: Equatable { var alignment: TextAlignment? = nil }
