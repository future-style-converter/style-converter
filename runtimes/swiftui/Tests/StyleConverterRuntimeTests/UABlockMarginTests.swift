//
//  UABlockMarginTests.swift
//  StyleConverterRuntimeTests
//
//  Pins the pure UA-default-block-margin decision (UABlockMargin) that
//  TITAN Round 4 GAP 1 uses to space the composed WPT canvas's roots like
//  the browser-ref's UA stylesheet. No render surface — deterministic
//  arithmetic pins, the same style as WPTCaptureModeTests' predicate pins.
//

import XCTest
import CoreGraphics
@testable import StyleConverterRuntime

final class UABlockMarginTests: XCTestCase {

    // MARK: - IR helper

    /// Decode the `properties` array of a childless component from wire JSON
    /// (IRProperty is Decodable-only) so tests stay on the exact byte shapes
    /// the converter emits.
    private func props(_ propsJSON: String) throws -> [IRProperty] {
        let json = "{\"id\":\"t\",\"name\":\"t\",\"properties\":[\(propsJSON)]}"
        return try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8)).properties
    }

    /// A single margin longhand at 5px, e.g. marginProp("MarginTop").
    private func marginProp(_ type: String) -> String {
        "{\"type\":\"\(type)\",\"data\":{\"type\":\"length\",\"px\":5.0}}"
    }

    // MARK: - Per-tag UA vertical margins (CSS 2.1 UA sheet @16px root)

    func testParagraphAndListMarginsAre16() {
        for tag in ["p", "ul", "ol", "blockquote", "pre", "figure"] {
            let m = UABlockMargin.vertical(forTag: tag)
            XCTAssertEqual(m.top, 16, "\(tag) top")
            XCTAssertEqual(m.bottom, 16, "\(tag) bottom")
        }
    }

    func testHeadingMarginsMatchResolvedEmValues() {
        // Task-pinned resolved px (each hN's em over its OWN font size).
        let expected: [String: CGFloat] = [
            "h1": 21, "h2": 19, "h3": 16, "h4": 21, "h5": 27, "h6": 37,
        ]
        for (tag, px) in expected {
            let m = UABlockMargin.vertical(forTag: tag)
            XCTAssertEqual(m.top, px, "\(tag) top")
            XCTAssertEqual(m.bottom, px, "\(tag) bottom")
        }
    }

    func testFlowContainersAndUnknownGetZero() {
        // The UA sheet declares no block margin for these; unknown tags and
        // nil (role-only components, e.g. body-root) fall to 0 too.
        for tag in ["div", "section", "article", "header", "footer",
                    "main", "nav", "aside", "span", "wat"] {
            let m = UABlockMargin.vertical(forTag: tag)
            XCTAssertEqual(m.top, 0, "\(tag) top")
            XCTAssertEqual(m.bottom, 0, "\(tag) bottom")
        }
        let none = UABlockMargin.vertical(forTag: nil)
        XCTAssertEqual(none.top, 0)
        XCTAssertEqual(none.bottom, 0)
    }

    func testTagMatchIsCaseInsensitive() {
        XCTAssertEqual(UABlockMargin.vertical(forTag: "P").top, 16)
        XCTAssertEqual(UABlockMargin.vertical(forTag: "H1").bottom, 21)
    }

    // MARK: - IR-margin declaration detection (deferral triggers)

    func testDeclaresBlockMarginDetectsPhysicalAndLogical() throws {
        XCTAssertTrue(UABlockMargin.declaresBlockMarginTop(try props(marginProp("MarginTop"))))
        XCTAssertTrue(UABlockMargin.declaresBlockMarginTop(try props(marginProp("MarginBlockStart"))))
        XCTAssertTrue(UABlockMargin.declaresBlockMarginBottom(try props(marginProp("MarginBottom"))))
        XCTAssertTrue(UABlockMargin.declaresBlockMarginBottom(try props(marginProp("MarginBlockEnd"))))
    }

    func testDeclaresBlockMarginIgnoresOtherEdgesAndProps() throws {
        // Horizontal margins and non-margin props never trigger a block edge.
        let horizontal = try props(marginProp("MarginLeft") + "," + marginProp("MarginRight"))
        XCTAssertFalse(UABlockMargin.declaresBlockMarginTop(horizontal))
        XCTAssertFalse(UABlockMargin.declaresBlockMarginBottom(horizontal))
        let bg = try props("{\"type\":\"BackgroundColor\",\"data\":{\"srgb\":{\"r\":0,\"g\":0,\"b\":0},\"original\":\"#000\"}}")
        XCTAssertFalse(UABlockMargin.declaresBlockMarginTop(bg))
        XCTAssertFalse(UABlockMargin.declaresBlockMarginBottom(bg))
    }

    // MARK: - Effective margin (UA default, deferring to IR per edge)

    func testEffectiveDefersToIRPerEdge() throws {
        // A <p> with NO margins → full UA 16/16.
        let bare = UABlockMargin.effectiveVertical(tag: "p", properties: [])
        XCTAssertEqual(bare.top, 16); XCTAssertEqual(bare.bottom, 16)

        // <p> declaring only margin-top → UA defers on TOP (runtime paints
        // it), keeps UA on the undeclared BOTTOM.
        let topOnly = UABlockMargin.effectiveVertical(
            tag: "p", properties: try props(marginProp("MarginTop")))
        XCTAssertEqual(topOnly.top, 0, "IR top wins → UA contributes 0")
        XCTAssertEqual(topOnly.bottom, 16, "undeclared bottom keeps UA default")

        // <p> declaring only margin-bottom → mirror.
        let bottomOnly = UABlockMargin.effectiveVertical(
            tag: "p", properties: try props(marginProp("MarginBottom")))
        XCTAssertEqual(bottomOnly.top, 16)
        XCTAssertEqual(bottomOnly.bottom, 0)

        // A flow container declaring no margin → 0/0 (no UA default anyway).
        let div = UABlockMargin.effectiveVertical(tag: "div", properties: [])
        XCTAssertEqual(div.top, 0); XCTAssertEqual(div.bottom, 0)
    }

    // MARK: - Adjacent-margin collapse

    func testCollapseIsTheLargerOfTwoPositives() {
        XCTAssertEqual(UABlockMargin.collapsed(16, 16), 16, "two equal → one, not summed")
        XCTAssertEqual(UABlockMargin.collapsed(16, 8), 16)
        XCTAssertEqual(UABlockMargin.collapsed(0, 16), 16)
        XCTAssertEqual(UABlockMargin.collapsed(21, 16), 21)
    }

    // MARK: - Stacked spacing (the composed-canvas fold)

    func testStackedSpacingTenParagraphs() {
        // Ten <p> bars (background-color-hsl-001 shape): 16 above the first,
        // 16 collapsed between each pair, 16 below the last.
        let margins = Array(repeating: (top: CGFloat(16), bottom: CGFloat(16)), count: 10)
        let s = UABlockMargin.stackedSpacing(margins)
        XCTAssertEqual(s.leading, Array(repeating: CGFloat(16), count: 10),
                       "every inter-bar gap collapses to 16, first top is 16")
        XCTAssertEqual(s.trailing, 16)
    }

    func testStackedSpacingBodyRootThenParagraph() {
        // body-root (0/0) then a <p> (16/16): no space above the body-root,
        // 16 above the <p> (max(0,16)), 16 below it.
        let s = UABlockMargin.stackedSpacing([(0, 0), (16, 16)])
        XCTAssertEqual(s.leading, [0, 16])
        XCTAssertEqual(s.trailing, 16)
    }

    func testStackedSpacingSingleAndEmpty() {
        let single = UABlockMargin.stackedSpacing([(16, 16)])
        XCTAssertEqual(single.leading, [16])
        XCTAssertEqual(single.trailing, 16)

        let empty = UABlockMargin.stackedSpacing([])
        XCTAssertTrue(empty.leading.isEmpty)
        XCTAssertEqual(empty.trailing, 0)
    }

    func testStackedSpacingCollapsesUnequalNeighbors() {
        // h1 (21/21) then p (16/16): gap between = max(21,16) = 21.
        let s = UABlockMargin.stackedSpacing([(21, 21), (16, 16)])
        XCTAssertEqual(s.leading, [21, 21])
        XCTAssertEqual(s.trailing, 16)
    }
}
