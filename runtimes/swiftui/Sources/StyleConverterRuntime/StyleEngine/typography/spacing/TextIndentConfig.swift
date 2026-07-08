//
//  TextIndentConfig.swift
//  StyleEngine/typography/spacing — Phase 6.
//
//  `text-indent`: first-line indent. SwiftUI Text has no native first-line
//  indent; the aggregate keeps the value so a future `.padding(.leading)`
//  on a first-line Text fragment can consume it.
//

import CoreGraphics

struct TextIndentConfig: Equatable { var px: CGFloat? = nil }
