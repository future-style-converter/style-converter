//
//  FontKerningConfig.swift
//  StyleEngine/typography/font — Phase 6.
//
//  `font-kerning: auto | normal | none`. We model it as three-state so
//  the applier can decide whether to force `.kerning(0)` (for `none`),
//  leave the platform default (for `auto`/`normal`), or skip entirely.
//

import Foundation

enum FontKerningMode: Equatable { case auto, normal, none }

struct FontKerningConfig: Equatable { var mode: FontKerningMode? = nil }
