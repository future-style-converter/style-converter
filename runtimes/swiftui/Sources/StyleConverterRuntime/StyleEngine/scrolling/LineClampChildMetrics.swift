//
//  LineClampChildMetrics.swift
//  StyleEngine/scrolling — Wave 46 (lane Y1): the per-CHILD typography the
//  line-box census needs, plus the renderer-facing cap resolver.
//
//  A clamp root's children are rendered LATER, recursively, each with its
//  inherited properties merged in at its own render (InheritedText) — so
//  at the root's render, where the cap must be decided, a child's used
//  font-size / line-height are not on any style yet. This file resolves
//  the two numbers off the child's RAW wire the way the child's own chain
//  will (same px reads, same em/rem wires — DynamicValueResolver's
//  resolveFontSizeWire / resolveLineHeightWire — same monospace-UA 13px
//  quirk), inheriting from the root where the child declares nothing
//  (CSS 2.1 §6.2: font-size, line-height and white-space all inherit).
//  Anything this reader cannot resolve reads nil, which the census turns
//  into "unknown" → the uniform fallback, never a guessed height.
//

import CoreGraphics
import Foundation

enum LineClampChildMetrics {

    /// The child's used font size in px, or nil when its declaration is a
    /// shape this reader cannot resolve (vw, calc, …).
    static func fontSizePx(_ properties: [IRProperty], root: LineClampRootMetrics) -> CGFloat? {
        // The last FontSize declaration wins (cascade order), like
        // FontSizeExtractor's last-wins scan.
        if let fs = properties.last(where: { $0.type == MonospaceUAFontSize.sizePropertyType }) {
            // Resolved px — authoritative.
            if let px = ValueExtractors.extractPx(fs.data) { return px }
            // em / rem / % against the INHERITED size — the root's
            // (css-fonts-4 §3.2; the resolver's documented contract).
            if let px = DynamicValueResolver.resolveFontSizeWire(
                fs.data, inheritedFontSizePx: Double(root.fontSizePx)) {
                return CGFloat(px)
            }
            // Any other declared shape: not provable here.
            return nil
        }
        // No declaration: the UA fixed default for a first-family
        // monospace child (wave-36 M8, 13px), else the inherited size.
        if let ua = MonospaceUAFontSize.resolvePx(from: properties) { return CGFloat(ua) }
        return root.fontSizePx
    }

    /// The child's one-line box in px, or nil when it is not provable:
    /// a declared `line-height: normal` (the face's metrics — unknown
    /// without a layout pass), an unresolvable wire, or a nil font size.
    static func lineBoxPx(_ properties: [IRProperty],
                          fontSizePx: CGFloat?,
                          root: LineClampRootMetrics) -> CGFloat? {
        if let lh = properties.last(where: { $0.type == LineHeightProperty.name }) {
            // `normal` → natural metrics, not a number this reader owns.
            if LineHeightNormal.isDeclaredNormal(lh.data) { return nil }
            // Flat px (resolved wire) — authoritative.
            if let px = ValueExtractors.extractPx(lh.data), px > 0 { return px }
            // A child's line box needs ITS size for em / multipliers.
            guard let fs = fontSizePx else { return nil }
            // Nested length wire (`{"original":{"type":"length",…}}` —
            // px one level down, or em against the child's OWN size,
            // css-values-4 §6.1), exactly as the child's chain resolves it.
            if let px = DynamicValueResolver.resolveLineHeightWire(lh.data, ownFontSizePx: Double(fs)),
               px > 0 { return CGFloat(px) }
            // Unitless multiplier (`line-height: 2`): × the child's size.
            if case .object(let o) = lh.data, let m = o["multiplier"]?.doubleValue, m > 0 {
                return fs * CGFloat(m)
            }
            // Percentages and other shapes: not provable here.
            return nil
        }
        // Inherited: a root UNITLESS multiplier recomputes against the
        // child's own size (CSS 2.1 §10.8: the computed value is the
        // number); a root px length inherits as that px; otherwise the
        // calibrated ratio × the child's size (LineClampCap.lineBoxPx).
        guard let fs = fontSizePx else { return nil }
        if let m = root.declaredMultiplier { return fs * m }
        if let px = root.declaredLineHeightPx { return px }
        return LineClampCap.lineBoxPx(declaredLineHeightPx: nil, fontSizePx: fs)
    }

    /// The child's vertical bands — padding + border width + margin, top
    /// and bottom — in px, or nil when any of them is declared in a
    /// non-px shape (em/%/auto) this reader does not resolve. Absent
    /// longhands are 0 (the CSS initial for padding/border/margin).
    /// The box-model bands sit above / below a block child's line boxes
    /// (CSS 2.1 §8.1), so the census must budget them.
    static func verticalBands(_ properties: [IRProperty]) -> (top: CGFloat, bottom: CGFloat)? {
        // Each band longhand: the last declaration wins; px or nothing.
        func band(_ type: String) -> CGFloat? {
            guard let p = properties.last(where: { $0.type == type }) else { return 0 }
            return ValueExtractors.extractPx(p.data).map { max(0, $0) }
        }
        guard let pt = band("PaddingTop"), let pb = band("PaddingBottom"),
              let bt = band("BorderTopWidth"), let bb = band("BorderBottomWidth"),
              let mt = band("MarginTop"), let mb = band("MarginBottom") else { return nil }
        return (pt + bt + mt, pb + bb + mb)
    }

    /// A definite px `height` on the child (nil for absent / `auto` /
    /// any non-px shape): the box is exactly that tall, so it is a
    /// monolithic run of that height rather than a stack of line boxes.
    static func explicitHeightPx(_ properties: [IRProperty]) -> CGFloat? {
        guard let h = properties.last(where: { $0.type == "Height" }) else { return nil }
        return ValueExtractors.extractPx(h.data)
    }

    /// True when the child declares `display: none` (serializer keyword
    /// `NONE`): no box is generated, so the census skips it entirely.
    static func isDisplayNone(_ properties: [IRProperty]) -> Bool {
        guard let d = properties.last(where: { $0.type == "Display" }) else { return false }
        return ValueExtractors.extractKeyword(d.data)?.lowercased() == "none"
    }

    /// True when the child's used overflow is not `visible` on either axis
    /// — a scroll container / clip box, i.e. an independent formatting
    /// context whose line boxes are not this container's (css-overflow-3
    /// §3; css-overflow-4 §5 fragments only the container's own line
    /// boxes). Reads the same five longhands the overflow extractors do.
    static func isScrollContainer(_ properties: [IRProperty]) -> Bool {
        let axes = ["Overflow", "OverflowX", "OverflowY", "OverflowBlock", "OverflowInline"]
        return properties.contains { p in
            axes.contains(p.type)
                && (ValueExtractors.extractKeyword(p.data)?.lowercased() ?? "visible") != "visible"
        }
    }
}
