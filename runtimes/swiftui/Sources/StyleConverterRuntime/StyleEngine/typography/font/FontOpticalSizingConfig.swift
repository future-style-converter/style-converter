//
//  FontOpticalSizingConfig.swift
//  StyleEngine/typography/font — Phase 6.
//
//  `font-optical-sizing: auto | none`. SwiftUI Text does not expose an
//  optical-sizing switch — applier is a no-op. Config preserves the
//  value so audits can still detect coverage.
//

import Foundation

enum FontOpticalSizing: Equatable { case auto, none }

struct FontOpticalSizingConfig: Equatable { var mode: FontOpticalSizing? = nil }
