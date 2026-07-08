//
//  TextTransformConfig.swift
//  StyleEngine/typography/decoration — Phase 6.
//
//  `text-transform`: none | uppercase | lowercase | capitalize | …
//  Mapped to SwiftUI's Text.Case (nil = no transform). We mirror the
//  aggregate's `Text.Case??` shape: outer nil = inherit, inner nil =
//  explicit `none`.
//

import SwiftUI

struct TextTransformConfig: Equatable {
    /// Two-state presence: outer nil → property absent; inner nil →
    /// explicit `none`; .some(c) → case transform to apply.
    var textCase: Text.Case?? = nil
}
