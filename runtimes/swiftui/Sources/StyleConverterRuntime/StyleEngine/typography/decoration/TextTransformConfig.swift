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
    /// Lane IOS-TEXT fix 6 — CSS `capitalize` (css-text-3 §2.1: "puts
    /// the first typographic letter unit of each word in titlecase").
    /// SwiftUI's Text.Case has no capitalize member, so this flag routes
    /// the transform to a STRING rewrite in PlaceholderLabel (mirror of
    /// Compose's TextFormattingApplier.capitalizeWords) instead of the
    /// former `.some(nil)` mapping that silently rendered it as `none`.
    var capitalize: Bool = false
}
