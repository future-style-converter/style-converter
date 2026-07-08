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
//    IRAngle   : { "degrees": 45.0 }
//    Keyword   : "flex"   OR   { "keyword": "flex" }   OR   { "type": "flex" }
//

import CoreGraphics
import SwiftUI

enum LengthOrPercentage {
    case length(CGFloat)
    case percentage(CGFloat)   // 0.0 – 1.0
    case auto
}

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

    /// Full length resolution including percentage and `auto`.
    static func extractLengthOrPercentage(_ value: IRValue?) -> LengthOrPercentage? {
        guard let value = value else { return nil }

        if case .object(let o) = value {
            if let px = o["px"]?.doubleValue {
                return .length(CGFloat(px))
            }
            if let kw = o["keyword"]?.stringValue, kw.lowercased() == "auto" {
                return .auto
            }
            if let original = o["original"]?.objectValue,
               let u = original["u"]?.stringValue, u.uppercased() == "PERCENT",
               let v = original["v"]?.doubleValue {
                return .percentage(CGFloat(v / 100.0))
            }
            if let pct = (o["percentage"]?.doubleValue ?? o["pct"]?.doubleValue) {
                return .percentage(CGFloat(pct / 100.0))
            }
        }

        if case .double(let d) = value { return .length(CGFloat(d)) }
        if case .int(let i) = value { return .length(CGFloat(i)) }
        if case .string(let s) = value, s.lowercased() == "auto" { return .auto }
        return nil
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

    static func extractFloat(_ value: IRValue?) -> CGFloat? {
        guard let value = value else { return nil }
        switch value {
        case .double(let d): return CGFloat(d)
        case .int(let i):    return CGFloat(i)
        case .object(let o):
            if let d = (o["alpha"] ?? o["value"] ?? o["numeric"])?.doubleValue {
                return CGFloat(d)
            }
            return nil
        default: return nil
        }
    }

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

    // MARK: - Angle

    static func extractDegrees(_ value: IRValue?) -> CGFloat? {
        guard let value = value else { return nil }
        if case .object(let o) = value, let deg = o["degrees"]?.doubleValue {
            return CGFloat(deg)
        }
        if case .double(let d) = value { return CGFloat(d) }
        if case .int(let i) = value { return CGFloat(i) }
        return nil
    }
}
