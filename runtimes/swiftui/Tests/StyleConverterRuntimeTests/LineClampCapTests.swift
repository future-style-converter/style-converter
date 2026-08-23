//
//  LineClampCapTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 46 (lane Y1) — the iOS block-level line-clamp cap, twin of
//  Compose's LineClampCapTest.kt (wave 41). Pins:
//    • the RAW-wire gate (only `{"type":"lines","count":N}` caps);
//    • the marker component (`no-ellipsis` / `""` → leaf lineLimit nil);
//    • the line-box pick (declared px wins, else font-size × 1.25);
//    • the pure cap decision (slack; ceil'd cap) — byte-parallel to Compose;
//    • the line-box CENSUS on the verbatim wave-45 per-test IR shapes
//      (line-clamp-005/-006: 112 / 192, NOT the uniform 96 / 160;
//      block-ellipsis-012: unprovable → uniform 16.25);
//    • the Layout half on a raster: the reported box is the cap, the
//      discarded tail is clipped, X is not.
//

import Foundation
import SwiftUI
import XCTest
// @testable: the cap/census/layout under pin are internal.
@testable import StyleConverterRuntime

final class LineClampCapTests: XCTestCase {

    // ── IR builders ───────────────────────────────────────────────────────

    private func component(_ json: String) throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }
    private func props(_ json: String) throws -> [IRProperty] {
        try component(#"{"id":"c","name":"c","properties":\#(json)}"#).properties
    }
    /// The root metrics the seam would build off the root's TextConfig.
    private func root(_ properties: [IRProperty], fontSize: CGFloat = 16,
                      lineHeight: CGFloat? = nil, normal: Bool = false) -> LineClampRootMetrics {
        LineClampRootMetrics(rootProperties: properties, fontSizePx: fontSize,
                             declaredLineHeightPx: lineHeight, declaredNormal: normal)
    }

    // ── linesCount: the raw-wire gate ─────────────────────────────────────

    func testLinesVariantYieldsItsCount() throws {
        // IRNumber serializes the count as a float on the live wire.
        let p = try props(#"[{"type":"LineClamp","data":{"type":"lines","count":3.0}}]"#)
        XCTAssertEqual(LineClampCap.linesCount(p), 3)
    }

    func testNoneAutoAndLegacyIntegerDoNotCap() throws {
        // `none` / `auto` carry no fixed count (css-overflow-4 §5.1).
        XCTAssertNil(LineClampCap.linesCount(try props(#"[{"type":"LineClamp","data":{"type":"none"}}]"#)))
        XCTAssertNil(LineClampCap.linesCount(try props(#"[{"type":"LineClamp","data":{"type":"auto"}}]"#)))
        // The legacy bare integer is refused by the cap (Compose parity)
        // even though the leaf extractor still reads it.
        XCTAssertNil(LineClampCap.linesCount(try props(#"[{"type":"LineClamp","data":4}]"#)))
        XCTAssertEqual(LineClampExtractor.extract(from: try props(#"[{"type":"LineClamp","data":4}]"#))?.lines, 4)
    }

    func testSubOneCountsAndAbsentPropertyDoNotCap() throws {
        XCTAssertNil(LineClampCap.linesCount(try props(#"[{"type":"LineClamp","data":{"type":"lines","count":0}}]"#)))
        XCTAssertNil(LineClampCap.linesCount([]))
    }

    func testLastDeclarationWinsLikeTheCascade() throws {
        let p = try props(#"[{"type":"LineClamp","data":{"type":"lines","count":2}},{"type":"LineClamp","data":{"type":"lines","count":5}}]"#)
        XCTAssertEqual(LineClampCap.linesCount(p), 5)
        // …and a trailing `none` switches the cap off.
        let off = try props(#"[{"type":"LineClamp","data":{"type":"lines","count":2}},{"type":"LineClamp","data":{"type":"none"}}]"#)
        XCTAssertNil(LineClampCap.linesCount(off))
    }

    // ── the marker component (block-ellipsis-023 / -024) ─────────────────

    func testNoEllipsisAndEmptyStringSuppressTheMarker() throws {
        // block-ellipsis-023's verbatim wire: `line-clamp: 4 no-ellipsis`.
        let no = try props(#"[{"type":"LineClamp","data":{"type":"lines","count":4,"ellipsis":{"type":"no-ellipsis"}}}]"#)
        XCTAssertTrue(LineClampCap.markerSuppressed(no))
        XCTAssertEqual(LineClampExtractor.extract(from: no)?.lines, 4, "the clamp itself still engages")
        // block-ellipsis-024's: `line-clamp: 4 ""` — the string is kept
        // verbatim on the wire but renders nothing.
        let empty = try props(#"[{"type":"LineClamp","data":{"type":"lines","count":4,"ellipsis":{"type":"string","value":""}}}]"#)
        XCTAssertTrue(LineClampCap.markerSuppressed(empty))
    }

    func testDrawnMarkersAreNotSuppressed() throws {
        // Absent (the initial `ellipsis`), `auto`, a non-empty string.
        XCTAssertFalse(LineClampCap.markerSuppressed(try props(#"[{"type":"LineClamp","data":{"type":"lines","count":4}}]"#)))
        XCTAssertFalse(LineClampCap.markerSuppressed(try props(#"[{"type":"LineClamp","data":{"type":"lines","count":4,"ellipsis":{"type":"auto"}}}]"#)))
        XCTAssertFalse(LineClampCap.markerSuppressed(try props(#"[{"type":"LineClamp","data":{"type":"lines","count":4,"ellipsis":{"type":"string","value":"…"}}}]"#)))
    }

    func testLeafLineLimitRoutesAMarkerlessClampToTheCap() throws {
        // SwiftUI's .lineLimit always paints "…", so the marker-less
        // clamp must leave the leaf unlimited and let the cap discard.
        let no = try props(#"[{"type":"LineClamp","data":{"type":"lines","count":4,"ellipsis":{"type":"no-ellipsis"}}}]"#)
        XCTAssertNil(LineClampCap.leafLineLimit(4, properties: no))
        // A drawn marker keeps today's leaf truncation byte-for-byte.
        let yes = try props(#"[{"type":"LineClamp","data":{"type":"lines","count":4}}]"#)
        XCTAssertEqual(LineClampCap.leafLineLimit(4, properties: yes), 4)
    }

    // ── the line box + the uniform cap ────────────────────────────────────

    func testDeclaredLineHeightWinsVerbatim() {
        // line-clamp-005's root: `font: 16px/32px` → 32pt boxes.
        XCTAssertEqual(LineClampCap.lineBoxPx(declaredLineHeightPx: 32, fontSizePx: 16), 32)
        XCTAssertEqual(LineClampCap.capPx(lines: 3, lineBoxPx: 32), 96)
    }

    func testDefaultLineBoxUsesTheComposed1_25RefPin() {
        // block-ellipsis-004's root: no line-height, 16px → 20pt; the
        // monospace-UA 13px root (block-ellipsis-012) → 16.25pt.
        XCTAssertEqual(LineClampCap.lineBoxPx(declaredLineHeightPx: nil, fontSizePx: 16), 20)
        XCTAssertEqual(try XCTUnwrap(LineClampCap.lineBoxPx(declaredLineHeightPx: nil, fontSizePx: 13)),
                       16.25, accuracy: 0.001)
        XCTAssertEqual(LineClampCap.capPx(lines: 3, lineBoxPx: 20), 60)
    }

    func testBrokenInputsYieldNoCap() {
        XCTAssertNil(LineClampCap.lineBoxPx(declaredLineHeightPx: nil, fontSizePx: 0))
        XCTAssertNil(LineClampCap.capPx(lines: 0, lineBoxPx: 20))
        XCTAssertNil(LineClampCap.capPx(lines: 2, lineBoxPx: 0))
    }

    // ── cappedHeight: the pure decision (Compose byte-parallel) ───────────

    func testMeasurementWithinSlackIsKeptByteIdentically() {
        // A leaf already clamped at 2 × 16.25 = 32.5 → rounded 33 or 34:
        // within the 2pt slack of the 32.5 cap → untouched.
        XCTAssertEqual(LineClampCap.cappedHeight(measuredPx: 34, capPx: 32.5), 34)
        XCTAssertEqual(LineClampCap.cappedHeight(measuredPx: 30, capPx: 32.5), 30)
    }

    func testOverflowingMeasurementIsCappedAtCeilOfTheCap() {
        // One extra 16.25 line (48.75) is well past slack → ceil(32.5) = 33.
        XCTAssertEqual(LineClampCap.cappedHeight(measuredPx: 48.75, capPx: 32.5), 33)
    }

    func testBoundaryJustPastSlackTripsTheCap() {
        // cap 60 + slack 2 = 62: 62 is kept, 62.01 trips.
        XCTAssertEqual(LineClampCap.cappedHeight(measuredPx: 62, capPx: 60), 62)
        XCTAssertEqual(LineClampCap.cappedHeight(measuredPx: 62.01, capPx: 60), 60)
    }

    // ── the census: verdicts ──────────────────────────────────────────────

    func testCensusStopsAtTheNthLineBoxAcrossRunsOfDifferentHeights() {
        // line-clamp-006's shape: 2 × 32 | 2 × 48 | 2 × 32, clamp 5 → 192.
        let runs = [LineClampRun(lineBoxPx: 32, exactLines: 2),
                    LineClampRun(lineBoxPx: 48, exactLines: 2),
                    LineClampRun(lineBoxPx: 32, exactLines: 2)]
        XCTAssertEqual(LineClampCensus.verdict(lines: 5, runs: runs, rootLineBoxPx: 32), .capped(192))
        // line-clamp-005: clamp 3 → 32 + 32 + 48 = 112 (the ref's box).
        XCTAssertEqual(LineClampCensus.verdict(lines: 3, runs: runs, rootLineBoxPx: 32), .capped(112))
        // Fewer proven boxes than the clamp → nothing to discard.
        XCTAssertEqual(LineClampCensus.verdict(lines: 7, runs: runs, rootLineBoxPx: 32), .short)
    }

    func testUnprovableCountSplitsOnHomogeneity() {
        // Every run on the root's box, no bands: the uniform model is
        // exact whatever the counts → .uniform (block-ellipsis-012/-022).
        let homo = [LineClampRun(lineBoxPx: 20, exactLines: nil),
                    LineClampRun(lineBoxPx: 20, exactLines: 1)]
        XCTAssertEqual(LineClampCensus.verdict(lines: 2, runs: homo, rootLineBoxPx: 20), .uniform)
        // A run on a DIFFERENT box (block-ellipsis-003's 1.5em child):
        // the Nth edge is unknowable → .unbounded, never a guessed cap.
        let hetero = [LineClampRun(lineBoxPx: 20, exactLines: nil),
                      LineClampRun(lineBoxPx: 30, exactLines: nil)]
        XCTAssertEqual(LineClampCensus.verdict(lines: 3, runs: hetero, rootLineBoxPx: 20), .unbounded)
        // A band or a monolithic box also breaks homogeneity.
        XCTAssertEqual(LineClampCensus.verdict(
            lines: 3, runs: [LineClampRun(lineBoxPx: 20, exactLines: nil),
                             LineClampRun(lineBoxPx: 20, exactLines: 0, leadingBandPx: 0, monolithicPx: 20)],
            rootLineBoxPx: 20), .unbounded)
        XCTAssertEqual(LineClampCensus.verdict(
            lines: 3, runs: [LineClampRun(lineBoxPx: 20, exactLines: 1, leadingBandPx: nil)],
            rootLineBoxPx: 20), .unbounded)
        // …but an unprovable run AFTER the Nth box is never consulted.
        XCTAssertEqual(LineClampCensus.verdict(
            lines: 1, runs: [LineClampRun(lineBoxPx: 20, exactLines: 1), hetero[1]],
            rootLineBoxPx: 20), .capped(20))
        // Glyph-less runs cost nothing and do not poison the walk.
        XCTAssertEqual(LineClampCensus.verdict(
            lines: 1, runs: [LineClampRun(lineBoxPx: 20, exactLines: 0),
                             LineClampRun(lineBoxPx: 20, exactLines: 1)],
            rootLineBoxPx: 20), .capped(20))
    }

    func testMonolithicRunsAreKeptWholeAndYieldNoLineBox() {
        // line-clamp-007: 2 × 32 | a 96px `overflow: auto` child (no line
        // boxes of the container) | 2 × 32, clamp 3 → the 3rd line box is
        // "Line 5" after the whole scroller: 32 + 32 + 96 + 32 = 192.
        let runs = [LineClampRun(lineBoxPx: 32, exactLines: 2),
                    LineClampRun(lineBoxPx: 48, exactLines: 0, leadingBandPx: 0, monolithicPx: 96),
                    LineClampRun(lineBoxPx: 32, exactLines: 2)]
        XCTAssertEqual(LineClampCensus.verdict(lines: 3, runs: runs, rootLineBoxPx: 32), .capped(192))
        // A block child's top band sits above its first line box.
        let banded = [LineClampRun(lineBoxPx: 20, exactLines: 1, leadingBandPx: 3)]
        XCTAssertEqual(LineClampCensus.verdict(lines: 1, runs: banded, rootLineBoxPx: 20), .capped(23))
    }

    func testExactLineCountFollowsTheWhiteSpaceKeyword() {
        // `pre`: segments are lines, spaces never wrap (css-text-3 §5.1).
        XCTAssertEqual(LineClampCensus.exactLineCount("Line 1\nLine 2", whiteSpace: "pre"), 2)
        // `nowrap`: one line, whatever the text.
        XCTAssertEqual(LineClampCensus.exactLineCount("a b\nc", whiteSpace: "nowrap"), 1)
        // pre-wrap family (both wire spellings): provable without spaces.
        XCTAssertEqual(LineClampCensus.exactLineCount("ab\ncd", whiteSpace: "pre_wrap"), 2)
        XCTAssertNil(LineClampCensus.exactLineCount("a b\ncd", whiteSpace: "pre-wrap"))
        // `normal`: a single whitespace-free word is the only provable run.
        XCTAssertEqual(LineClampCensus.exactLineCount("supercalifragilistic", whiteSpace: nil), 1)
        XCTAssertNil(LineClampCensus.exactLineCount("This line should", whiteSpace: nil))
        XCTAssertNil(LineClampCensus.exactLineCount("a\nb", whiteSpace: "normal"), "breaks collapse to spaces")
        XCTAssertEqual(LineClampCensus.exactLineCount("", whiteSpace: "pre"), 0)
    }

    // ── the census on the verbatim wave-45 per-test IR ────────────────────

    /// line-clamp-006's clamp root + child, as the wave-45 per-test IR
    /// ships them (runs-ordered, nested line-height wire, inherited `pre`).
    private func lineClamp006() throws -> (IRComponent, [IRComponent]) {
        let root = try component(#"""
        {"id":"r-154","name":"wpt__css-overflow__line-clamp__line-clamp-006__0",
         "text":"Line 1\nLine 2Line 5\nLine 6",
         "meta":{"runs":[{"text":"Line 1\nLine 2"},{"child":"line-clamp__line-clamp-006__0__0"},{"text":"Line 5\nLine 6"}]},
         "properties":[
           {"type":"LineClamp","data":{"type":"lines","count":5}},
           {"type":"FontSize","data":{"px":16,"original":{"type":"length","px":16}}},
           {"type":"LineHeight","data":{"original":{"type":"length","px":32}}},
           {"type":"FontFamily","data":["serif"]},
           {"type":"WhiteSpace","data":"PRE"}]}
        """#)
        let child = try component(#"""
        {"id":"c-155","name":"line-clamp__line-clamp-006__0__0","text":"Line 3\nLine 4",
         "slot":{"parent":"r-154"},
         "properties":[
           {"type":"FontSize","data":{"px":24,"original":{"type":"length","px":24}}},
           {"type":"LineHeight","data":{"original":{"type":"length","px":48}}},
           {"type":"FontFamily","data":["serif"]}]}
        """#)
        return (root, [child])
    }

    func testLineClamp006CensusClosesAfterLine5Not4() throws {
        let (rootC, kids) = try lineClamp006()
        // The seam hands in the root's TextConfig numbers: 16px / 32px.
        let r = root(rootC.properties, fontSize: 16, lineHeight: 32)
        XCTAssertEqual(r.lineBoxPx, 32)
        XCTAssertEqual(r.whiteSpace, "pre")
        let runs = LineClampCensus.runs(component: rootC, inFlowChildren: kids, root: r)
        XCTAssertEqual(runs, [LineClampRun(lineBoxPx: 32, exactLines: 2),
                              LineClampRun(lineBoxPx: 48, exactLines: 2),
                              LineClampRun(lineBoxPx: 32, exactLines: 2)])
        // 32+32+48+48+32 = 192: "Line 5" stays, "Line 6" is discarded —
        // the uniform model (5 × 32 = 160) would discard "Line 5" too.
        XCTAssertEqual(LineClampCap.resolveCapPx(component: rootC, inFlowChildren: kids,
                                                 rootProperties: rootC.properties, root: r), 192)
    }

    func testLineClamp005CensusIs112NotTheUniform96() throws {
        let (rootC, kids) = try lineClamp006()
        // Same document shape with `line-clamp: 3` (line-clamp-005).
        let three = try props(#"[{"type":"LineClamp","data":{"type":"lines","count":3}},{"type":"LineHeight","data":{"original":{"type":"length","px":32}}},{"type":"WhiteSpace","data":"PRE"}]"#)
        let r = root(three, fontSize: 16, lineHeight: 32)
        XCTAssertEqual(LineClampCap.resolveCapPx(component: rootC, inFlowChildren: kids,
                                                 rootProperties: three, root: r), 112)
    }

    func testBlockEllipsis012CensusBailsToTheUniformMonospaceBox() throws {
        // block-ellipsis-012: a text-less clamp root (1 line, monospace →
        // UA 13px) over two wrapping `<p>` children: the first child's
        // count is unprovable → the uniform 1 × 16.25 content box.
        let rootC = try component(#"""
        {"id":"r","name":"r","properties":[
           {"type":"LineClamp","data":{"type":"lines","count":1}},
           {"type":"FontFamily","data":["monospace"]}]}
        """#)
        let kids = [try component(#"{"id":"k1","name":"k1","text":"This line should have an ellipsis here","properties":[]}"#),
                    try component(#"{"id":"k2","name":"k2","text":"After all, it is not the last line","properties":[]}"#)]
        let r = root(rootC.properties, fontSize: 13)
        let runs = LineClampCensus.runs(component: rootC, inFlowChildren: kids, root: r)
        XCTAssertEqual(runs.map(\.exactLines), [nil, nil])
        XCTAssertEqual(try XCTUnwrap(LineClampCap.resolveCapPx(
            component: rootC, inFlowChildren: kids, rootProperties: rootC.properties, root: r)),
            16.25, accuracy: 0.001)
    }

    func testChildInheritsTheRootMultiplierAndTheMonospaceQuirk() throws {
        // A root `line-height: 1.5` inherits as the NUMBER (CSS 2.1
        // §10.8): a 24px child gets 36pt boxes, not the root's 24pt.
        let rp = try props(#"[{"type":"LineHeight","data":{"multiplier":1.5,"original":{"type":"number","value":1.5}}}]"#)
        let r = root(rp, fontSize: 16, lineHeight: 24)
        XCTAssertEqual(r.declaredMultiplier, 1.5)
        XCTAssertEqual(r.lineBoxPx, 24)
        let kid = try props(#"[{"type":"FontSize","data":{"px":24}}]"#)
        XCTAssertEqual(LineClampChildMetrics.lineBoxPx(kid, fontSizePx: 24, root: r), 36)
        // A size-less first-family-monospace child computes to 13px.
        let mono = try props(#"[{"type":"FontFamily","data":["monospace"]}]"#)
        XCTAssertEqual(LineClampChildMetrics.fontSizePx(mono, root: r), 13)
        // An em child resolves against the root's size (1.5em × 16 = 24).
        let em = try props(#"[{"type":"FontSize","data":{"original":{"type":"length","original":{"v":1.5,"u":"EM"}}}}]"#)
        XCTAssertEqual(LineClampChildMetrics.fontSizePx(em, root: r), 24)
        // A declared `normal` line-height is not provable → nil.
        // (the serializer encodes ONLY the keyword as a bare string at
        // `original` — LineHeightNormal.isDeclaredNormal)
        let normal = try props(#"[{"type":"LineHeight","data":{"multiplier":1.2,"original":"normal"}}]"#)
        XCTAssertNil(LineClampChildMetrics.lineBoxPx(normal, fontSizePx: 16, root: r))
    }

    func testLineClamp007ScrollContainerChildIsMonolithic() throws {
        // line-clamp-007: the 24px/48px child is `overflow: auto` with
        // `padding: 0 4px` — kept whole (96px, no line boxes), so the
        // clamp-3 box closes after "Line 5": 32 + 32 + 96 + 32 = 192.
        let (rootC, _) = try lineClamp006()
        let child = try component(#"""
        {"id":"c","name":"line-clamp__line-clamp-006__0__0","text":"Line 3\nLine 4",
         "properties":[
           {"type":"OverflowX","data":"AUTO"},{"type":"OverflowY","data":"AUTO"},
           {"type":"FontSize","data":{"px":24}},
           {"type":"LineHeight","data":{"original":{"type":"length","px":48}}},
           {"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":4}},
           {"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":4}}]}
        """#)
        let three = try props(#"[{"type":"LineClamp","data":{"type":"lines","count":3}},{"type":"LineHeight","data":{"original":{"type":"length","px":32}}},{"type":"WhiteSpace","data":"PRE"}]"#)
        let r = root(three, fontSize: 16, lineHeight: 32)
        let runs = LineClampCensus.runs(component: rootC, inFlowChildren: [child], root: r)
        XCTAssertEqual(runs[1].monolithicPx, 96)
        XCTAssertEqual(LineClampCap.resolveCapPx(component: rootC, inFlowChildren: [child],
                                                 rootProperties: three, root: r), 192)
    }

    func testExplicitHeightChildIsAMonolithicBoxOfThatHeight() throws {
        // The `<br>` placeholders of block-ellipsis-002: 0×0 and 0×20
        // boxes — monolithic, no line box; a text run on the root's box
        // around them is unprovable → the list is heterogeneous → no cap.
        let br0 = try component(#"{"id":"b0","name":"b0","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":0}}]}"#)
        let br20 = try component(#"{"id":"b1","name":"b1","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}]}"#)
        let r = root([], fontSize: 16)
        XCTAssertEqual(LineClampCensus.childRun(br0, root: r).monolithicPx, 0)
        XCTAssertEqual(LineClampCensus.childRun(br20, root: r).monolithicPx, 20)
        let rootC = try component(#"""
        {"id":"r","name":"r","text":"Line 1 Line 2","meta":{"runs":[{"text":"Line 1"},{"child":"b0"},{"text":" Line 2"},{"child":"b1"}]},
         "properties":[{"type":"LineClamp","data":{"type":"lines","count":3}}]}
        """#)
        XCTAssertNil(LineClampCap.resolveCapPx(component: rootC, inFlowChildren: [br0, br20],
                                               rootProperties: rootC.properties, root: r))
    }

    func testHeterogeneousUnprovableContentGetsNoCap() throws {
        // block-ellipsis-003: "Line 1 " (unprovable, 20pt box) then a
        // 1.5em child (30pt box) — the uniform 60pt cap would slice the
        // child's second line (the Android capture) → no cap on iOS.
        let rootC = try component(#"""
        {"id":"r","name":"r","text":"Line 1 Line 4","meta":{"runs":[{"text":"Line 1 "},{"child":"k"},{"text":" Line 4"}]},
         "properties":[{"type":"LineClamp","data":{"type":"lines","count":3}}]}
        """#)
        let kid = try component(#"{"id":"k","name":"k","text":"Line 2 Line 3","properties":[{"type":"FontSize","data":{"original":{"type":"length","original":{"v":1.5,"u":"EM"}}}}]}"#)
        let r = root(rootC.properties, fontSize: 16)
        let runs = LineClampCensus.runs(component: rootC, inFlowChildren: [kid], root: r)
        XCTAssertEqual(runs.map(\.lineBoxPx), [20, 30, 20])
        XCTAssertNil(LineClampCap.resolveCapPx(component: rootC, inFlowChildren: [kid],
                                               rootProperties: rootC.properties, root: r))
    }

    func testBlockEllipsis022AtomicInlineChildStaysUniform() throws {
        // block-ellipsis-022: a wrapping root run + an inline-block
        // "hidden" on the same 16.25pt box → uniform 2 × 16.25 = 32.5.
        let rootC = try component(#"""
        {"id":"r","name":"r","text":"There should be an ellipsis at the end of this text line",
         "properties":[{"type":"LineClamp","data":{"type":"lines","count":2}},{"type":"FontFamily","data":["monospace"]}]}
        """#)
        let kid = try component(#"{"id":"k","name":"k","text":"hidden","properties":[{"type":"Display","data":"INLINE_BLOCK"}]}"#)
        let r = root(rootC.properties, fontSize: 13)
        XCTAssertEqual(try XCTUnwrap(LineClampCap.resolveCapPx(
            component: rootC, inFlowChildren: [kid], rootProperties: rootC.properties, root: r)),
            32.5, accuracy: 0.001)
    }

    func testExplicitRootHeightNeutralizesTheCap() throws {
        // block-ellipsis-007's post-load-extracted root: `height: 50px`
        // from the browser already encodes the clamp — no cap inside it.
        let rootC = try component(#"""
        {"id":"r","name":"r","text":"Line 1 Line 2",
         "properties":[{"type":"LineClamp","data":{"type":"lines","count":1}},{"type":"Height","data":{"type":"length","px":50}}]}
        """#)
        let r = root(rootC.properties, fontSize: 16)
        XCTAssertNil(LineClampCap.resolveCapPx(component: rootC, inFlowChildren: [],
                                               rootProperties: rootC.properties, root: r))
        // `height: auto` is not a definite height — the cap applies.
        let auto = try component(#"""
        {"id":"r","name":"r","text":"supercalifragilistic",
         "properties":[{"type":"LineClamp","data":{"type":"lines","count":1}},{"type":"Height","data":{"keyword":"auto"}}]}
        """#)
        XCTAssertEqual(LineClampCap.resolveCapPx(component: auto, inFlowChildren: [],
                                                 rootProperties: auto.properties, root: r), 20)
    }

    func testDisplayNoneAndUnprovableScrollContainerChildren() throws {
        let r = root([], fontSize: 16)
        // `display: none` generates no box: a zero monolithic run.
        let none = try component(#"{"id":"n","name":"n","text":"hidden words here","properties":[{"type":"Display","data":"NONE"}]}"#)
        XCTAssertEqual(LineClampCensus.childRun(none, root: r),
                       LineClampRun(lineBoxPx: 20, exactLines: 0, leadingBandPx: 0, monolithicPx: 0))
        // A scroll container with WRAPPING text: unprovable, and its nil
        // band keeps it out of the uniform fallback (its lines are not
        // the container's line boxes).
        let scroller = try component(#"{"id":"s","name":"s","text":"a b c d e","properties":[{"type":"OverflowY","data":"AUTO"}]}"#)
        let run = LineClampCensus.childRun(scroller, root: r)
        XCTAssertNil(run.exactLines)
        XCTAssertNil(run.leadingBandPx)
        XCTAssertEqual(LineClampCensus.verdict(lines: 2, runs: [run], rootLineBoxPx: 20), .unbounded)
    }

    func testNoClampYieldsNoCap() throws {
        let rootC = try component(#"{"id":"r","name":"r","text":"a","properties":[]}"#)
        XCTAssertNil(LineClampCap.resolveCapPx(component: rootC, inFlowChildren: [],
                                               rootProperties: [], root: root([])))
    }

    // ── the Layout half on a raster ───────────────────────────────────────

    /// Three 20pt bars stacked (60pt of painted content) under the cap.
    @MainActor
    private func bandHeight(cap: CGFloat?) throws -> Int {
        let view = VStack(spacing: 0) {
            ForEach(0..<3) { _ in
                Color.black.frame(width: 100, height: 20)
            }
        }
        .engineLineClampCap(cap)
        .frame(width: 200, height: 100, alignment: .topLeading)
        .background(Color.white)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage)
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        // Count the dark rows down column 50 (inside the bars).
        return (0..<h).filter { y in buf[(y * w + 50) * 4] < 128 }.count
    }

    @MainActor
    func testCapDiscardsTheTailAndNilIsIdentity() throws {
        // No cap: all three bars paint (60 rows).
        XCTAssertEqual(try bandHeight(cap: nil), 60)
        // A 2-bar cap: the third bar's ink is clipped (40 rows) —
        // css-overflow-4 §4.3, the discarded content is not rendered.
        XCTAssertEqual(try bandHeight(cap: 40), 40)
        // Within slack: 60 vs cap 58.5 + 2 → kept byte-identically.
        XCTAssertEqual(try bandHeight(cap: 58.5), 60)
    }
}
