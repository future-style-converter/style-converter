//
//  BackgroundAttachmentConfig.swift
//  StyleEngine/background — Phase 4.
//
//  CSS `background-attachment` (scroll / fixed / local). SwiftUI has no
//  equivalent — the background always scrolls with the view unless a
//  custom GeometryReader trick is used. Config is parsed for parity; the
//  applier is identity. Documented limitation.
//

import Foundation

// Per-layer attachment mode.
enum BackgroundAttachmentMode: Equatable {
    case scroll
    case fixed
    case local
}

struct BackgroundAttachmentConfig: Equatable {
    var layers: [BackgroundAttachmentMode] = []
    var hasAny: Bool { !layers.isEmpty }
}
