//
//  TextWrapConfig.swift
//  StyleEngine/typography/wrapping — Phase 6.
//
//  `TextWrap` is keyword-only (or string, for hyphenate-character). SwiftUI
//  has no direct API today; the value is captured so audits can see the
//  property came through. See the matching Applier for the TODO.
//

import Foundation

struct TextWrapConfig: Equatable { var keyword: String? = nil }
