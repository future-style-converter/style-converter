//
//  DocumentFaceMetricsTests.swift
//  StyleConverterRuntimeTests — wave-47 lane Z4 (SEAM 2 of the monospace pin)
//
//  Pins the document-@font-face METRIC seams the wave-46 Y7 pilot measured
//  open: the label painted the registered face while every measurement lane
//  kept the generic design — the `ch` basis stayed SF Mono (a pinned
//  `width: 10ch` box at 202px vs the frozen Menlo ref's 197px) and the line
//  grid split its leading around SF Mono's content area while DejaVu painted
//  (glyph runs ~3px low; hyphens-auto-inline-010 regressed 0.9528→0.9406
//  under the pilot). These tests pin the closed contract:
//
//    * ChUnitMetrics.zeroAdvancePx(documentFaceName:) measures the NAMED
//      face, outranking the design, and memoizes per registry epoch;
//    * LineBoxMetrics.contentHeight(faceName:) probes the same face;
//    * StyleBuilder's ch gate hands the measurement the SAME §5.2 registry
//      walk that fills `text.fontFaceName`, so measure == paint.
//
//  Like DocumentFontRegistryTests these run under Mac Catalyst with a real
//  CoreText, using an installed monospace face as stand-in bytes; the suite
//  skips (never fails) on a machine without one.
//

import XCTest
import CoreText
@testable import StyleConverterRuntime

final class DocumentFaceMetricsTests: XCTestCase {

    override func tearDown() {
        // Process-scoped registry — a leaked face would shadow the next
        // test's document, the exact bug `register` exists to prevent.
        DocumentFontRegistry.shared.clear()
        super.tearDown()
    }

    /// `<base>/_mono-pin/<name>` copied from a real installed monospace face —
    /// the pilot's sandbox corner (same helper family as
    /// MonoPinDocumentFaceTests; it is the METRIC routing under test here).
    private func makePinTree(name: String = "DejaVuSansMono.ttf") throws -> (base: URL, src: String)? {
        let candidates = ["/System/Library/Fonts/Supplemental/Courier New.ttf",
                          "/System/Library/Fonts/Supplemental/Andale Mono.ttf"]
        guard let source = candidates.first(where: { FileManager.default.fileExists(atPath: $0) })
        else { return nil }
        let base = FileManager.default.temporaryDirectory
            .appendingPathComponent("w47z4-\(UUID().uuidString)", isDirectory: true)
        let dir = base.appendingPathComponent("_mono-pin", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try FileManager.default.copyItem(at: URL(fileURLWithPath: source),
                                         to: dir.appendingPathComponent(name))
        addTeardownBlock { try? FileManager.default.removeItem(at: base) }
        return (base, "_mono-pin/\(name)")
    }

    /// Register the pin tree under the generic's own name and answer the
    /// platform name the label will paint with, or nil to skip.
    private func registerPin() throws -> String? {
        guard let tree = try makePinTree() else { return nil }
        DocumentFontRegistry.shared.register(
            [IRFontFace(family: "monospace", src: tree.src, weight: "400", style: "normal")],
            baseDirectory: tree.base)
        return DocumentFontRegistry.shared.resolvedName(for: "monospace")
    }

    /// The reference measurement: '0' advance of the NAMED face via the same
    /// CoreText calls ChUnitMetrics uses — computed independently so the test
    /// pins the routing, not the arithmetic against itself.
    private func directAdvance(name: String, sizePx: CGFloat) -> Double? {
        guard let font = UIFont(name: name, size: sizePx) else { return nil }
        var chars: [UniChar] = [0x30]
        var glyphs: [CGGlyph] = [0]
        guard CTFontGetGlyphsForCharacters(font as CTFont, &chars, &glyphs, 1),
              glyphs[0] != 0 else { return nil }
        return CTFontGetAdvancesForGlyphs(font as CTFont, .horizontal, glyphs, nil, 1)
    }

    private func props(_ pairs: [(String, IRValue)]) -> [IRProperty] {
        pairs.map { IRProperty(type: $0.0, data: $0.1) }
    }

    // MARK: - ChUnitMetrics: the document face outranks the design

    func testZeroAdvanceMeasuresTheRegisteredDocumentFace() throws {
        guard let name = try registerPin() else { throw XCTSkip("no installed monospace font file to copy") }
        guard let expected = directAdvance(name: name, sizePx: 32) else {
            throw XCTSkip("registered face not measurable on this host")
        }
        // The named face wins over the .monospaced design — the exact basis
        // the label paints with (Courier New '0' is 0.600em where SF Mono's
        // is 0.618em, the +0.5px/ch wall Y7 measured).
        let adv = ChUnitMetrics.zeroAdvancePx(
            rounded: false, monospaced: true, serif: false, sizePx: 32,
            documentFaceName: name)
        XCTAssertNotNil(adv)
        XCTAssertEqual(adv!, expected, accuracy: 1e-6)
        // And it genuinely diverges from the design-only answer whenever the
        // two faces' advances differ (they do for every candidate face).
        let designOnly = ChUnitMetrics.zeroAdvancePx(
            rounded: false, monospaced: true, serif: false, sizePx: 32)
        if let d = designOnly, abs(d - expected) > 0.01 {
            XCTAssertNotEqual(adv!, d, accuracy: 0.005)
        }
    }

    func testAnUnresolvableFaceNameFallsBackToTheDesign() throws {
        // A stale/bogus name must degrade to the pre-wave-47 design basis —
        // never nil-by-surprise (nil means "no metrics at all" to callers).
        let stale = ChUnitMetrics.zeroAdvancePx(
            rounded: false, monospaced: true, serif: false, sizePx: 16,
            documentFaceName: "NoSuchFaceZ4-\(UUID().uuidString.prefix(8))")
        let design = ChUnitMetrics.zeroAdvancePx(
            rounded: false, monospaced: true, serif: false, sizePx: 16)
        XCTAssertNotNil(stale)
        XCTAssertEqual(stale!, design!, accuracy: 1e-9)
    }

    func testRegistryEpochAdvancesAcrossDocuments() throws {
        // The memo key rides the epoch: every clear (and so every register,
        // which clears first) must move it, or document N's cached advance
        // could answer for document N+1's same-named face.
        let e0 = DocumentFontRegistry.shared.epoch
        DocumentFontRegistry.shared.clear()
        XCTAssertGreaterThan(DocumentFontRegistry.shared.epoch, e0)
        guard let _ = try registerPin() else { throw XCTSkip("no installed monospace font file to copy") }
        XCTAssertGreaterThan(DocumentFontRegistry.shared.epoch, e0 + 1)
    }

    // MARK: - LineBoxMetrics: the leading splits around the rendered face

    func testContentHeightProbesTheDocumentFace() throws {
        guard let name = try registerPin() else { throw XCTSkip("no installed monospace font file to copy") }
        guard let face = UIFont(name: name, size: 32) else {
            throw XCTSkip("registered face not resolvable on this host")
        }
        // The content area is the RENDERED face's ascent+|descent| — the
        // browser's line-height:normal basis for the face it paints.
        XCTAssertEqual(
            LineBoxMetrics.contentHeight(fontSizePx: 32, design: .monospaced, faceName: name),
            face.lineHeight, accuracy: 1e-9)
        // nil faceName keeps the pre-wave-47 design probe byte-identical.
        XCTAssertEqual(
            LineBoxMetrics.contentHeight(fontSizePx: 32, design: .monospaced),
            UIFont.systemFont(ofSize: 32).lineHeight, accuracy: 1e-9)
    }

    // MARK: - StyleBuilder: measurement takes the same walk the label takes

    func testChBasisFollowsTheLabelsRegistryWalk() throws {
        guard let name = try registerPin() else { throw XCTSkip("no installed monospace font file to copy") }
        guard let expected = directAdvance(name: name, sizePx: 32) else {
            throw XCTSkip("registered face not measurable on this host")
        }
        // hyphens-manual-inline-010's live wire: 32px monospace, width 10ch
        // (the exact shape the converter emits — `original.v/u`, unresolved).
        let style = StyleBuilder.build(from: props([
            ("FontFamily", .array([.string("monospace")])),
            ("FontSize", .object(["px": .double(32)])),
            ("Width", .object(["type": .string("length"),
                               "original": .object(["v": .double(10), "u": .string("CH")])])),
        ]))
        // The label will paint the registered face (fontFaceName) and the ch
        // basis must be measured from THE SAME face — seam 2's whole point.
        XCTAssertEqual(style.text.fontFaceName, name)
        XCTAssertNotNil(style.spacing.context.chAdvancePx)
        XCTAssertEqual(style.spacing.context.chAdvancePx!, expected, accuracy: 1e-6)
    }

    func testFaceFreeDocumentKeepsTheDesignBasis() {
        // The universal case: empty registry ⇒ the walk is a no-op and the
        // design-measured basis (SF Mono here) is byte-identical to wave 46.
        let style = StyleBuilder.build(from: props([
            ("FontFamily", .array([.string("monospace")])),
            ("FontSize", .object(["px": .double(32)])),
            ("Width", .object(["type": .string("length"),
                               "original": .object(["v": .double(10), "u": .string("CH")])])),
        ]))
        XCTAssertNil(style.text.fontFaceName)
        XCTAssertEqual(style.spacing.context.chAdvancePx,
                       ChUnitMetrics.zeroAdvancePx(rounded: false, monospaced: true,
                                                   serif: false, sizePx: 32))
    }
}
