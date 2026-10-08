//
//  OutOfFlowContainingBlockTests.swift
//  Wave 54 (lane L4, unit OOF-ios) — pins for OutOfFlowContainingBlock (the
//  non-transform containing-block rule table) and the three FixedHoist call
//  sites it feeds (:309 the OR, :347 the fixed strip's inset clause, :115
//  FIXED joins the static-position class). Twin of Compose
//  runtimes/compose/src/test/java/com/styleconverter/runtime/layout/
//  position/OutOfFlowContainingBlockTest.kt — same rows, same order.
//
//    P1  the rule table, on the wire shapes the converter emits;
//    P3  FixedHoist.split over VERBATIM per-test IR (three payloads inline;
//        the whole wave53-final corpus when present locally), compared with
//        the census of the UNCHANGED code executed before the rule table
//        existed: tools/titan/results/wave54-oof-layout/census-swift.base.txt.
//

import XCTest
@testable import StyleConverterRuntime

final class OutOfFlowContainingBlockTests: XCTestCase {

    // One wire property from a JSON literal (the converter's exact shapes).
    private func prop(_ type: String, _ json: String) throws -> IRProperty {
        try JSONDecoder().decode(IRProperty.self, from: Data(#"{"type":"\#(type)","data":\#(json)}"#.utf8))
    }

    // The rule table over a one-property list.
    private func est(_ type: String, _ json: String) throws -> Bool {
        OutOfFlowContainingBlock.establishes([try prop(type, json)])
    }

    // A document's composed roots (IRDocument's decoder already composes slots).
    private func roots(_ json: String) throws -> [IRComponent] {
        try JSONDecoder().decode(IRDocument.self, from: Data(json.utf8)).components
    }

    // MARK: - P1: the rule table

    func testP1ContainLayoutPaintStrictContentEstablish() throws {
        // css-contain-2 §3.2 / §3.4; strict and content include both.
        for k in ["PAINT", "STRICT", "CONTENT", "LAYOUT"] { XCTAssertTrue(try est("Contain", #"["\#(k)"]"#), k) }
    }

    func testP1ContainSizeInlineSizeStyleNoneDoNot() throws {
        // Neither layout nor paint containment: no containing block.
        for k in ["SIZE", "INLINE_SIZE", "STYLE", "NONE"] { XCTAssertFalse(try est("Contain", #"["\#(k)"]"#), k) }
    }

    func testP1FilterBackdropFilterAndWillChangeHintsEstablish() throws {
        // filter-effects-1 §5 / filter-effects-2 §2 / css-will-change-1 §3.
        XCTAssertTrue(try est("Filter", #"[{"fn":"invert","v":100}]"#))
        XCTAssertTrue(try est("BackdropFilter", #"[{"fn":"blur","r":{"px":10}}]"#))
        XCTAssertTrue(try est("WillChange", #"[{"type":"property-name","name":"backdrop-filter"}]"#))
        XCTAssertTrue(try est("WillChange", #"[{"type":"property-name","name":"filter"}]"#))
        XCTAssertTrue(try est("WillChange", #"[{"type":"property-name","name":"contain"}]"#))
    }

    func testP1NoneFormsAndWillChangeOpacityDoNotEstablish() throws {
        // The converter's none shapes: "none" for filter, [] for backdrop-filter.
        XCTAssertFalse(try est("Filter", #""none""#))
        XCTAssertFalse(try est("BackdropFilter", "[]"))
        // opacity creates no containing block; transform is the twin table's.
        XCTAssertFalse(try est("WillChange", #"[{"type":"property-name","name":"opacity"}]"#))
        XCTAssertFalse(try est("WillChange", #"[{"type":"property-name","name":"transform"}]"#))
    }

    func testP1PositionedEstablisherWithholdsTheClaim() throws {
        // backdrop-filter-clip-rect's container shape: ABSOLUTE + backdrop-filter.
        XCTAssertFalse(OutOfFlowContainingBlock.establishes(
            [try prop("Position", #""ABSOLUTE""#), try prop("BackdropFilter", #"[{"fn":"blur","r":{"px":10}}]"#)]))
        // FIXED is withheld too; RELATIVE still claims.
        XCTAssertFalse(OutOfFlowContainingBlock.establishes([try prop("Position", #""FIXED""#), try prop("Contain", #"["PAINT"]"#)]))
        XCTAssertTrue(OutOfFlowContainingBlock.establishes([try prop("Position", #""RELATIVE""#), try prop("Contain", #"["PAINT"]"#)]))
    }

    func testP1DeclaredInsets() throws {
        // css-position-3 §3.5.3: only all-auto insets give the static position.
        XCTAssertFalse(OutOfFlowContainingBlock.declaresAnyInset([try prop("Position", #""FIXED""#)]))
        XCTAssertFalse(OutOfFlowContainingBlock.declaresAnyInset([try prop("Top", #""auto""#)]))
        XCTAssertTrue(OutOfFlowContainingBlock.declaresAnyInset([try prop("Top", #"{"px":0}"#)]))
        // anchor-center-safe-rtl `__4-480` verbatim: unresolvable, still declared.
        XCTAssertTrue(OutOfFlowContainingBlock.declaresAnyInset([
            try prop("Top", #"{"expr":"calc(anchor(right) + 5px)"}"#),
            try prop("Right", #"{"expr":"calc(anchor(left) + 5px)"}"#)]))
    }

    // MARK: - P3: split over verbatim payloads (CI-resident)
    // tools/titan/runs/wave53-final/sections/<sec>/per-test-ir/<file>,
    // byte-identical to wave54-open (cmp).

    /// CSS2/…static-fixed-inside-abspos.json (sha1 d6cc16b49924976b00c6d6fce2f0fcd4fe48831a).
    private let staticFixedInsideAbspos = #"{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css2__abspos__static-fixed-inside-abspos__0-015","name":"wpt__CSS2__abspos__static-fixed-inside-abspos__0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css2__abspos__static-fixed-inside-abspos__1-016","name":"wpt__CSS2__abspos__static-fixed-inside-abspos__1","properties":[{"type":"Generic","data":{"propertyName":"display","rawValue":"absolute","_unmapped":true}},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}]},{"id":"abspos__static-fixed-inside-abspos__1__0-017","name":"abspos__static-fixed-inside-abspos__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"MarginLeft","data":{"px":50}},{"type":"MarginTop","data":{"px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"slot":{"parent":"wpt__css2__abspos__static-fixed-inside-abspos__1-016"}},{"id":"abspos__static-fixed-inside-abspos__1__0__0-018","name":"abspos__static-fixed-inside-abspos__1__0__0","properties":[{"type":"Position","data":"FIXED"},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"abspos__static-fixed-inside-abspos__1__0-017"}}]}"#

    /// filter-effects/…backdrop-filter-containing-block.json (sha1 20d48b00652c95a8cc30e30c4fee2c1d9d989282).
    private let backdropContainingBlock = #"{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__filter-effects__backdrop-filter-containing-block__0-087","name":"wpt__filter-effects__backdrop-filter-containing-block__0","properties":[],"text":"Expected: one green square and one red square, both 200px by 200px.","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__filter-effects__backdrop-filter-containing-block__1-088","name":"wpt__filter-effects__backdrop-filter-containing-block__1","properties":[{"type":"Width","data":{"type":"length","px":200}},{"type":"BackdropFilter","data":[{"fn":"invert","v":100}]}]},{"id":"backdrop-filter-containing-block__1__0-089","name":"backdrop-filter-containing-block__1__0","properties":[{"type":"Position","data":"FIXED"},{"type":"Top","data":{"px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"Width","data":{"type":"percentage","value":100}},{"type":"Height","data":{"type":"length","px":200}}],"slot":{"parent":"wpt__filter-effects__backdrop-filter-containing-block__1-088"},"meta":{"role":"ws-after"}},{"id":"backdrop-filter-containing-block__1__1-090","name":"backdrop-filter-containing-block__1__1","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":210}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Width","data":{"type":"percentage","value":100}},{"type":"Height","data":{"type":"length","px":200}}],"slot":{"parent":"wpt__filter-effects__backdrop-filter-containing-block__1-088"}}]}"#

    /// css-position/…position-relative-003.json (sha1 456044b91e90339d84191fc455c0e961920d80e1).
    private let positionRelative003 = #"{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-position__position-relative-003__0-197","name":"wpt__css-position__position-relative-003__0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-position__position-relative-003__1-198","name":"wpt__css-position__position-relative-003__1","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}]},{"id":"position-relative-003__1__0-199","name":"position-relative-003__1__0","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Top","data":100},{"type":"Left","data":100}],"slot":{"parent":"wpt__css-position__position-relative-003__1-198"},"meta":{"sourceTag":"span"}},{"id":"position-relative-003__1__0__0-200","name":"position-relative-003__1__0__0","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Top","data":{"px":-100}},{"type":"Left","data":{"px":-100}}],"slot":{"parent":"position-relative-003__1__0-199"},"meta":{"sourceTag":"span"}},{"id":"position-relative-003__1__0__0__0-201","name":"position-relative-003__1__0__0__0","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"Position","data":"FIXED"}],"slot":{"parent":"position-relative-003__1__0__0-200"}}]}"#

    func testP3TheCarriersHoistNothing() throws {
        // M1 (all-auto fixed) and M2 (backdrop-filter): no fixed box is
        // stripped to the canvas any more; each stays in its parent's overlay.
        for doc in [staticFixedInsideAbspos, backdropContainingBlock, positionRelative003] {
            let split = FixedHoist.split(roots: try roots(doc))
            XCTAssertTrue(split.hoisted.isEmpty)
            XCTAssertEqual(split.flow.count, 2)
        }
    }

    // MARK: - P3 over the whole corpus (local-only: tools/titan/runs is gitignored)

    // The repo root, five hops above this file.
    private static var repoRoot: URL {
        URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
    }

    // One line per document, the base file's exact format.
    private func censusLine(_ name: String, _ roots: [IRComponent]) -> String {
        let split = FixedHoist.split(roots: roots)
        let statics = roots.filter { FixedHoist.rendersInFlowAsStaticPosition($0) }.map(\.id)
        var kept: [String] = []
        func walk(_ c: IRComponent, depth: Int) {
            if depth > 0 && FixedHoist.isFixed(c) { kept.append(c.id) }
            (c.children ?? []).forEach { walk($0, depth: depth + 1) }
        }
        (split.flow + split.hoisted).forEach { walk($0, depth: 0) }
        return "OOFCENSUS \(name) flow=\(split.flow.map(\.id)) hoisted=\(split.hoisted.map(\.id)) static=\(statics) keptFixed=\(kept)"
    }

    func testP3TheCorpusMovesInExactlyTheSixOOFiosCarriers() throws {
        let fm = FileManager.default
        let sections = Self.repoRoot.appendingPathComponent("tools/titan/runs/wave53-final/sections")
        guard fm.fileExists(atPath: sections.path) else { throw XCTSkip("wave53-final corpus not present") }
        // Every per-test IR document, sorted by name (the base file's order).
        var files: [URL] = []
        for sec in try fm.contentsOfDirectory(at: sections, includingPropertiesForKeys: nil) {
            let d = sec.appendingPathComponent("per-test-ir")
            files += ((try? fm.contentsOfDirectory(at: d, includingPropertiesForKeys: nil)) ?? []).filter { $0.pathExtension == "json" }
        }
        files.sort { $0.lastPathComponent < $1.lastPathComponent }
        let now = try files.map { f in
            censusLine(f.lastPathComponent, try JSONDecoder().decode(IRDocument.self, from: Data(contentsOf: f)).components)
        }
        // Written beside the build products so a red run can be read line by line.
        try (now.joined(separator: "\n") + "\n").write(
            to: fm.temporaryDirectory.appendingPathComponent("oof-census-swift.now.txt"), atomically: true, encoding: .utf8)
        let base = try String(contentsOf: Self.repoRoot.appendingPathComponent(
            "tools/titan/results/wave54-oof-layout/census-swift.base.txt"), encoding: .utf8)
            .split(separator: "\n").map(String.init)
        XCTAssertEqual(now.count, 1435)
        XCTAssertEqual(base.count, now.count)
        // The documents whose split moved, by file name.
        let moved = zip(base, now).filter { $0 != $1 }.map { $0.1.split(separator: " ")[1] }
        XCTAssertEqual(moved, [
            "wpt__CSS2__abspos__static-fixed-inside-abspos.json",
            "wpt__css-position__change-insets-inside-strict-containment-nested.json",
            "wpt__css-position__position-fixed-scroll-nested-fixed.json",
            "wpt__css-position__position-relative-003.json",
            "wpt__css-position__position-relative-004.json",
            "wpt__filter-effects__backdrop-filter-containing-block.json",
        ])
    }
}
