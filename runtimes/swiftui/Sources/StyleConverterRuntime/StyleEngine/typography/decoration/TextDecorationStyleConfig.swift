//
//  TextDecorationStyleConfig.swift
//  StyleEngine/typography/decoration — Phase 6.
//
//  `text-decoration-style`: keyword. Mapped to TextDecorationPattern
//  (see TypographyAggregate.swift) so the Config type stays SwiftUI-free.
//

import Foundation

struct TextDecorationStyleConfig: Equatable {
    var pattern: TextDecorationPattern = .solid
}
