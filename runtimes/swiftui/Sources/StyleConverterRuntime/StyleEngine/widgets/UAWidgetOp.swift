//
//  UAWidgetOp.swift
//  StyleEngine/widgets — lane W2 (wave 20).
//
//  The platform-neutral paint-instruction enum of the UA-widget replica
//  plan, split out of UAWidgetsGeometry.swift (file-size rule: keep each
//  file ≤~300 lines). See that file's header for the pin-table contract;
//  the `pin` encoding here is byte-parallel with the Kotlin twin's
//  UAWidgetOp (runtimes/compose .../widgets/UAWidgetsGeometry.kt).
//

import Foundation

/// One platform-neutral paint instruction. Coordinates are CSS px from
/// the widget box's top-left; colors packed ARGB (identical literals on
/// both natives). `pin` is the language-neutral encoding the twin test
/// suites assert on — one line per op, space-separated, %.2f-trimmed.
enum UAWidgetOp: Equatable {
    case fillRect(x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat, color: UInt32)
    case strokeRect(x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat, color: UInt32)
    case fillRRect(x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat, r: CGFloat, color: UInt32)
    case strokeRRect(x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat, r: CGFloat, color: UInt32)
    case fillCircle(cx: CGFloat, cy: CGFloat, r: CGFloat, color: UInt32)
    case strokeCircle(cx: CGFloat, cy: CGFloat, r: CGFloat, color: UInt32)
    /// Stroked segment — checkbox check mark, textarea resize grip and
    /// (fix 4) the menulist chevron arms. The ref's menulist arrow is a
    /// STROKED chevron, not a filled triangle (white-black-ink ref rows
    /// y138-143 show two ~2px arms with a WHITE gap between them at
    /// y138-141 — a filled triangle would be solid across); the old
    /// fillTriangle op painted that solid wedge and is retired.
    case line(x1: CGFloat, y1: CGFloat, x2: CGFloat, y2: CGFloat, sw: CGFloat, color: UInt32)
    /// Text run, top-left anchored — executed with the platform's Inter
    /// face at `size`px (labels are the only platform-divergent ink).
    case label(text: String, x: CGFloat, y: CGFloat, size: CGFloat, color: UInt32)

    /// Trimmed %.2f so 13.0 pins as "13" and 17.75 as "17.75" — the
    /// Kotlin twin formats identically (byte-parallel pin contract).
    static func f(_ v: CGFloat) -> String {
        var s = String(format: "%.2f", Double(v))
        // Trim trailing zeros then a dangling point (mirrors Kotlin's
        // trimEnd('0').trimEnd('.') exactly).
        while s.hasSuffix("0") { s.removeLast() }
        if s.hasSuffix(".") { s.removeLast() }
        return s
    }
    /// ARGB as #AARRGGBB — the palette half of every pin assert.
    static func c(_ argb: UInt32) -> String { String(format: "#%08X", argb) }

    /// The language-neutral pin string (see the twin's `pin` property).
    var pin: String {
        switch self {
        case let .fillRect(x, y, w, h, color):
            return "fillRect \(Self.f(x)) \(Self.f(y)) \(Self.f(w)) \(Self.f(h)) \(Self.c(color))"
        case let .strokeRect(x, y, w, h, color):
            return "strokeRect \(Self.f(x)) \(Self.f(y)) \(Self.f(w)) \(Self.f(h)) \(Self.c(color))"
        case let .fillRRect(x, y, w, h, r, color):
            return "fillRRect \(Self.f(x)) \(Self.f(y)) \(Self.f(w)) \(Self.f(h)) \(Self.f(r)) \(Self.c(color))"
        case let .strokeRRect(x, y, w, h, r, color):
            return "strokeRRect \(Self.f(x)) \(Self.f(y)) \(Self.f(w)) \(Self.f(h)) \(Self.f(r)) \(Self.c(color))"
        case let .fillCircle(cx, cy, r, color):
            return "fillCircle \(Self.f(cx)) \(Self.f(cy)) \(Self.f(r)) \(Self.c(color))"
        case let .strokeCircle(cx, cy, r, color):
            return "strokeCircle \(Self.f(cx)) \(Self.f(cy)) \(Self.f(r)) \(Self.c(color))"
        case let .line(x1, y1, x2, y2, sw, color):
            return "line \(Self.f(x1)) \(Self.f(y1)) \(Self.f(x2)) \(Self.f(y2)) \(Self.f(sw)) \(Self.c(color))"
        case let .label(text, x, y, size, color):
            return "label '\(text)' \(Self.f(x)) \(Self.f(y)) \(Self.f(size)) \(Self.c(color))"
        }
    }
}

