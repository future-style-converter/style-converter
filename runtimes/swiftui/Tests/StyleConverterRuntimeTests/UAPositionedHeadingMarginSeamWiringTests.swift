//
//  UAPositionedHeadingMarginSeamWiringTests.swift
//  Wave 55 (lane L4, U1-ios) — SOURCE pin for the seam hunk lane L4 delivers
//  in tools/titan/results/wave55-ua-heading-margin/seam-2.patch (this file
//  ships INSIDE that patch, so it lands with it: rule 2b). mergedProperties(now:)
//  is a private member of the SwiftUI view, so the step is pinned on verbatim
//  wire in UAPositionedHeadingMarginTests through a replay — which stays green
//  whatever the renderer does. This pin proves the replay's premises: the
//  margin step runs once, LAST in mergedProperties(now:), with the post-reset
//  own list, the hoisted PRE-UA list as its em base, the UA font step's output
//  as the list it extends, and the ambient `wptCaptureMode` as its gate.
//  It reads neither the font step's `hasElementChildren:` argument (seam-4's)
//  nor anything outside mergedProperties(now:).
//  Mutations (executed, lane note "Pins"): HEAD's renderer (no seam);
//  `enabled: true`; the step fed `mergedPreUA` as the list it extends.
//  Precedent: AbsposStaticOffsetSeamWiringTests.
//

import XCTest

final class UAPositionedHeadingMarginSeamWiringTests: XCTestCase {

    /// The renderer's source, whitespace runs collapsed (the pins read tokens).
    private func rendererSource() throws -> String {
        // Located from this file: Tests/StyleConverterRuntimeTests/ → runtimes/swiftui/.
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // StyleConverterRuntimeTests/
            .deletingLastPathComponent()   // Tests/
            .deletingLastPathComponent()   // runtimes/swiftui/
            .appendingPathComponent("Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift")
        // One space for every whitespace run, so line wrapping is not pinned.
        return try String(contentsOf: url, encoding: .utf8)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
    }

    /// How many times `needle` occurs in `haystack`.
    private func count(_ needle: String, in haystack: String) -> Int {
        haystack.components(separatedBy: needle).count - 1
    }

    /// The margin step, verbatim: tag, OWN list, the hoisted pre-UA list, the font step's output, the flag.
    private let marginStep = "return UAPositionedHeadingMargin.apply(sourceTag: component.meta?.sourceTag, " +
        "own: ownProperties, preUA: mergedPreUA, merged: fontProperties, enabled: wptCaptureMode) }"

    /// The hoisted ListStyleUaRule binding (the margin step's em base).
    private let hoist = "let mergedPreUA = ListStyleUaRule.apply( sourceTag: component.meta?.sourceTag, own: ownProperties,"

    /// The UA font step bound to `fontProperties`, reading the hoisted list.
    private let fontStep = "let fontProperties = UAElementFontRule.apply( sourceTag: component.meta?.sourceTag, " +
        "own: ownProperties, merged: mergedPreUA,"

    func testTheMarginStepRunsOnceLastInMergedProperties() throws {
        let src = try rendererSource()
        // Exactly one call, with exactly these arguments, and no other site.
        XCTAssertEqual(count(marginStep, in: src), 1, "the UA margin step call")
        XCTAssertEqual(count("UAPositionedHeadingMargin.apply(", in: src), 1)
        // It closes mergedProperties(now:): the function's first `return`.
        guard let fn = src.range(of: "private func mergedProperties(now: Date?) -> [IRProperty] {"),
              let ret = src.range(of: "return ", range: fn.upperBound..<src.endIndex),
              let step = src.range(of: marginStep)
        else { return XCTFail("mergedProperties(now:) or the margin step not found") }
        XCTAssertEqual(ret.lowerBound, step.lowerBound, "the margin step must be mergedProperties' return")
    }

    func testTheHoistAndTheFontStepPrecedeIt() throws {
        let src = try rendererSource()
        // Each binding exists once…
        XCTAssertEqual(count(hoist, in: src), 1, "the mergedPreUA hoist")
        XCTAssertEqual(count(fontStep, in: src), 1, "the fontProperties binding")
        // …in cascade order: hoist, font step, margin step.
        guard let h = src.range(of: hoist), let f = src.range(of: fontStep), let m = src.range(of: marginStep)
        else { return XCTFail("a cascade binding is missing") }
        XCTAssertTrue(h.lowerBound < f.lowerBound && f.lowerBound < m.lowerBound)
    }
}
