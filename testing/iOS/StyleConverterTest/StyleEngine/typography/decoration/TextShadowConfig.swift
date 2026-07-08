//
//  TextShadowConfig.swift
//  StyleEngine/typography/decoration — Phase 6.
//
//  CSS `text-shadow`: list of `<x> <y> <blur>? <color>?` tuples. SwiftUI
//  Text can only render one shadow per view, so we keep the first layer.
//  Additional layers are preserved in the Config for a future CALayer
//  stacking pass.
//

import Foundation

struct TextShadowConfig: Equatable {
    var layers: [TextShadowLayer] = []
}
