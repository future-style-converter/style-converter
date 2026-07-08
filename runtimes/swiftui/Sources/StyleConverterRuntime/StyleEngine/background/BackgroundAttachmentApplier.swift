//
//  BackgroundAttachmentApplier.swift
//  StyleEngine/background — Phase 4.
//
//  Stub — SwiftUI can't pin a background to the viewport without custom
//  GeometryReader work that's outside Phase 4 scope. Documented.
//

import SwiftUI

struct BackgroundAttachmentApplier: ViewModifier {
    let config: BackgroundAttachmentConfig?
    func body(content: Content) -> some View { content }
}

extension View {
    func engineBackgroundAttachment(_ config: BackgroundAttachmentConfig?) -> some View {
        modifier(BackgroundAttachmentApplier(config: config))
    }
}
