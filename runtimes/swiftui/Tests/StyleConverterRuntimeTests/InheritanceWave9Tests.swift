//
//  InheritanceWave9Tests.swift
//  StyleConverterRuntimeTests — wave 9 (#37).
//
//  Pins for the EXTENDED inheritance channel: the wave-1 14-text-property
//  whitelist grows to the full css-cascade-4 "Inherited: yes" set our IR
//  carries — including `Color`, whose inherited value now feeds the
//  currentColor consumers (border/outline threading reads
//  `style.text.color` from the MERGED list, StyleBuilder.swift).
//
//  Structure mirrors the Android InheritanceAndFlexGatingTest wave-9
//  block: whitelist shape, chain-of-3 transitivity, mid-chain
//  redeclaration, root initial values, currentColor-through-inheritance.
//

import XCTest
import SwiftUI
// @testable: everything under test is internal to the module.
@testable import StyleConverterRuntime

final class InheritanceWave9Tests: XCTestCase {

    // Concise IR builders (same shapes as FidelityWave1Tests).
    private func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
    private func props(_ p: [(String, IRValue)]) -> [IRProperty] {
        p.map { IRProperty(type: $0.0, data: $0.1) }
    }

    /// Wire-shape colour payload: the converter emits `{"srgb":{r,g,b,a}}`
    /// (schema/spec/02-values.md) — extractColor takes the static path.
    private func srgb(_ r: Double, _ g: Double, _ b: Double) -> IRValue {
        obj(["srgb": obj(["r": .double(r), "g": .double(g), "b": .double(b), "a": .double(1)])])
    }

    /// One parent→child hop exactly as ComponentRenderer performs it:
    /// merge the incoming channel UNDER the element's own declarations,
    /// then re-filter what flows onward to grandchildren.
    private func hop(channel: [IRProperty], own: [IRProperty]) -> [IRProperty] {
        InheritedText.inheritable(from: InheritedText.merge(own: own, inherited: channel))
    }

    /// sRGB components of a SwiftUI Color via the UIKit bridge (all IR
    /// colors are Color(.sRGB, …) so the conversion is exact).
    private func rgb(_ c: Color) -> (r: CGFloat, g: CGFloat, b: CGFloat) {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(c).getRed(&r, green: &g, blue: &b, alpha: &a)
        return (r, g, b)
    }

    // MARK: - Whitelist shape

    /// The wave-9 additions are all present — one pin per property so a
    /// future trim of the set fails with the property's name in the diff.
    func testExtendedInheritedSetCarriesTheCascadeYesTable() {
        for t in ["Color", "Visibility", "Cursor",
                  "ListStyleType", "ListStylePosition", "ListStyleImage",
                  "Quotes", "TextShadow",
                  "OverflowWrap", "WordBreak", "Hyphens",
                  "TextEmphasisStyle", "TextEmphasisColor", "TextEmphasisPosition",
                  "RubyAlign", "RubyPosition",
                  "CaptionSide", "BorderCollapse", "BorderSpacing", "EmptyCells",
                  "Orphans", "Widows"] {
            XCTAssertTrue(InheritedText.inheritedTypes.contains(t),
                          "\(t) must inherit (wave 9)")
        }
    }

    /// Box/layout properties stay uninheritable, and decoration still
    /// PROPAGATES rather than inherits (css-text-decor-3 §2).
    func testBoxPropertiesStillNeverInherit() {
        for t in ["Width", "Height", "PaddingTop", "BackgroundColor",
                  "BorderTopWidth", "Display", "Margin", "Position",
                  "BoxShadow", "Opacity", "Overflow", "TextDecorationLine"] {
            XCTAssertFalse(InheritedText.inheritedTypes.contains(t),
                           "\(t) must not inherit")
        }
    }

    // MARK: - Transitive chains

    /// Color + text-shadow survive root → mid → leaf when nobody
    /// redeclares (the transitive republication chain).
    func testColorSurvivesAChainOfThree() {
        let root = props([
            ("Color", .string("#336699")),
            ("TextShadow", .string("2px 2px 3px #00000080")),
        ])
        let midChannel = hop(channel: InheritedText.inheritable(from: root), own: [])
        let leafChannel = hop(channel: midChannel, own: [])
        XCTAssertTrue(leafChannel.contains { $0.type == "Color" })
        XCTAssertTrue(leafChannel.contains { $0.type == "TextShadow" })
    }

    /// A mid-chain redeclaration replaces the root's value in everything
    /// that flows onward (children inherit the parent's COMPUTED value —
    /// css-cascade-4 §7.3 — and own declarations beat inherited ones).
    func testMidChainRedeclarationWinsForDescendants() {
        let rootChannel = props([("Color", .string("#ff0000"))])
        let midOwn = props([("Color", .string("#0000ff"))])
        let leafChannel = hop(channel: rootChannel, own: midOwn)
        XCTAssertEqual(leafChannel.filter { $0.type == "Color" }.count, 1)
        // The surviving entry is the mid's blue, not the root's red.
        if case .string(let s) = leafChannel.first(where: { $0.type == "Color" })!.data {
            XCTAssertEqual(s, "#0000ff")
        } else {
            XCTFail("Color entry lost its payload shape")
        }
    }

    /// Root components see initial values: the empty channel is a strict
    /// identity (no entry added, no reordering).
    func testRootInitialValuesEmptyChannelIsIdentity() {
        let own = props([("Width", obj(["px": .double(100)]))])
        XCTAssertEqual(InheritedText.merge(own: own, inherited: []).count, 1)
        // And nothing materialises from nowhere at the root.
        XCTAssertTrue(hop(channel: [], own: []).isEmpty)
    }

    // MARK: - currentColor through inheritance

    /// The star of wave 9: a child with a colourless bordered box under a
    /// colour-declaring ancestor must resolve currentColor against the
    /// INHERITED color. StyleBuilder threads `style.text.color` (from the
    /// MERGED list) into engineBorderSides/engineOutline as the
    /// currentColor — so the pin is: the merged style's text color IS the
    /// ancestor's value when the child declares none.
    func testInheritedColorReachesCurrentColorThreading() {
        // Ancestor publishes color #336699 (r 0.2, g 0.4, b 0.6).
        let channel = props([("Color", srgb(0.2, 0.4, 0.6))])
        // Child declares a border with style+width but NO colour and NO
        // own `color` — css-backgrounds-3 §3.2: border-color initial is
        // currentColor, which the browser resolves against the inherited
        // color.
        let childOwn = props([
            ("BorderTopStyle", .string("SOLID")),
            ("BorderTopWidth", obj(["px": .double(3)])),
        ])
        let merged = InheritedText.merge(own: childOwn, inherited: channel)
        let style = StyleBuilder.build(from: merged)
        // The merged text color is the ancestor's — this is exactly the
        // value StyleBuilder hands to engineBorderSides(currentColor:).
        guard let c = style.text.color else {
            return XCTFail("inherited Color did not reach style.text.color")
        }
        let comps = rgb(c)
        XCTAssertEqual(comps.r, 0.2, accuracy: 0.01)
        XCTAssertEqual(comps.g, 0.4, accuracy: 0.01)
        XCTAssertEqual(comps.b, 0.6, accuracy: 0.01)
        // And the border config exists with no explicit colour of its own
        // (nil = currentColor sentinel for the applier).
        XCTAssertNotNil(style.borderSides)
    }

    /// The wave-1/wave-5 pins' element-LOCAL case must keep winning: an
    /// own-declared color beats the inherited one for currentColor too.
    func testOwnColorStillBeatsInheritedForCurrentColor() {
        let channel = props([("Color", srgb(0.2, 0.4, 0.6))])
        let childOwn = props([
            ("Color", srgb(0.8, 0.0, 0.0)),
            ("BorderTopStyle", .string("SOLID")),
            ("BorderTopWidth", obj(["px": .double(3)])),
        ])
        let merged = InheritedText.merge(own: childOwn, inherited: channel)
        let style = StyleBuilder.build(from: merged)
        guard let c = style.text.color else {
            return XCTFail("own Color did not reach style.text.color")
        }
        let comps = rgb(c)
        XCTAssertEqual(comps.r, 0.8, accuracy: 0.01)
        XCTAssertEqual(comps.g, 0.0, accuracy: 0.01)
        XCTAssertEqual(comps.b, 0.0, accuracy: 0.01)
    }
}
