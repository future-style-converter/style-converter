//
//  TextDecorationLineConfig.swift
//  StyleEngine/typography/decoration — Phase 6.
//
//  CSS `text-decoration-line`: `none` or any combination of `underline`,
//  `overline`, `line-through`, `blink`. SwiftUI Text exposes
//  `.underline(_:color:)` and `.strikethrough(_:color:)` — overline and
//  blink have no native path so they're captured but not painted.
//

import Foundation

struct TextDecorationLineConfig: Equatable {
    var underline: Bool = false
    var overline: Bool = false       // captured, not rendered (TODO)
    var lineThrough: Bool = false
    var blink: Bool = false          // captured, not rendered (TODO)
}
