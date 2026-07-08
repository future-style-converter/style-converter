//
//  TextDecorationColorConfig.swift
//  StyleEngine/typography/decoration — Phase 6.
//
//  Colour for underline / line-through strokes. Falls back to text colour
//  when nil. Kept as a SwiftUI.Color here since it is routed directly to
//  `.underline(color:)` / `.strikethrough(color:)`.
//

import SwiftUI

struct TextDecorationColorConfig: Equatable { var color: Color? = nil }
