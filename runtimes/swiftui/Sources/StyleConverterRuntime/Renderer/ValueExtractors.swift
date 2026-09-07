//
//  ValueExtractors.swift
//  StyleConverterTest
//
//  Utility functions to pull typed values (lengths, colors, keywords,
//  numbers) out of the opaque `IRValue` blobs each IRProperty carries.
//
//  Mirrors the Android `ValueExtractors.kt` object. IR format recap:
//    IRLength  : { "px": 16.0 }                           (or { "keyword": "auto" })
//    IRColor   : { "srgb": { "r": 0.2, "g": 0.6, "b": 0.9, "a": 1.0 }, ... }
//    Keyword   : "flex"   OR   { "keyword": "flex" }   OR   { "type": "flex" }
//
//  Retro P2b (A6#10) removed four zero-reference members: the
//  `LengthOrPercentage` enum with `extractLengthOrPercentage`, plus
//  `extractFloat` and `extractDegrees`. All three were pre-StyleEngine
//  twins of the typed value readers every extractor uses today —
//  StyleEngine/core/types/{LengthValue,NumberValue,AngleValue}.swift —
//  which is why nothing had called them since Phase 2. Percentages and
//  angles therefore have no reader in this file at all; reach for the
//  core/types ones.
//

import CoreGraphics
import SwiftUI

enum ValueExtractors {

    // MARK: - Length

    /// Returns the absolute pixel value (as CGFloat — points on iOS) or nil if the
    /// length is auto / relative / unavailable.
    static func extractPx(_ value: IRValue?) -> CGFloat? {
        guard let value = value else { return nil }
        switch value {
        case .double(let d): return CGFloat(d)
        case .int(let i):    return CGFloat(i)
        case .object(let o):
            if let px = o["px"]?.doubleValue { return CGFloat(px) }
            return nil
        default: return nil
        }
    }

    // MARK: - Color

    /// Extracts a SwiftUI Color from an IRColor (normalized to sRGB).
    /// Returns nil for currentColor / var() / unresolved colors.
    static func extractColor(_ value: IRValue?) -> Color? {
        guard case .object(let o) = value,
              let srgb = o["srgb"]?.objectValue,
              let r = srgb["r"]?.doubleValue,
              let g = srgb["g"]?.doubleValue,
              let b = srgb["b"]?.doubleValue else {
            return nil
        }
        let a = srgb["a"]?.doubleValue ?? 1.0
        return Color(.sRGB, red: r, green: g, blue: b, opacity: a)
    }

    // MARK: - Scalars

    static func extractInt(_ value: IRValue?) -> Int? {
        guard let value = value else { return nil }
        switch value {
        case .int(let i):    return i
        case .double(let d): return Int(d)
        case .object(let o):
            return (o["value"] ?? o["numeric"])?.intValue
        default: return nil
        }
    }

    // MARK: - Keyword

    /// "flex", "column", "center", etc. Handles plain strings, { keyword: "..." },
    /// { value: "..." }, and sealed-type discriminators ({ type: "..." }).
    static func extractKeyword(_ value: IRValue?) -> String? {
        guard let value = value else { return nil }
        switch value {
        case .string(let s): return s
        case .object(let o):
            return (o["keyword"] ?? o["value"] ?? o["type"])?.stringValue
        default: return nil
        }
    }

    /// Normalizes keywords for switch statements: uppercased with "-" → "_".
    static func normalize(_ keyword: String?) -> String {
        (keyword ?? "").uppercased().replacingOccurrences(of: "-", with: "_")
    }
}
