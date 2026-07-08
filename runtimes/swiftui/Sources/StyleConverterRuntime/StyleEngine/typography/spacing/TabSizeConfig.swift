//
//  TabSizeConfig.swift
//  StyleEngine/typography/spacing — Phase 6.
//
//  `tab-size`: integer (characters) or length. SwiftUI has no tab-stop
//  API; we record the integer for audit and ignore length-form.
//

import Foundation

struct TabSizeConfig: Equatable { var count: Int? = nil }
