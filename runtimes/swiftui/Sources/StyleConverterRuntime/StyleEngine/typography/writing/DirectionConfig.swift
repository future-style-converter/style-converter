//
//  DirectionConfig.swift
//  StyleEngine/typography/writing — Phase 6.
//
//  `direction: ltr | rtl`. Maps to SwiftUI's `LayoutDirection` so
//  `.environment(\.layoutDirection, ...)` can pick up the value at emit.
//

import SwiftUI

struct DirectionConfig: Equatable { var direction: LayoutDirection? = nil }
