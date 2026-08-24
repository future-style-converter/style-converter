//
//  MonoPinDocumentFaceTests.swift
//  StyleConverterRuntimeTests — wave-46 lane Y7
//
//  Pins the resolution CONTRACT the monospace font-metric pin pilot
//  (tools/titan/mono-pin.mjs) rides on. The pilot delivers DejaVu Sans Mono —
//  the free face with BYTE-IDENTICAL metrics to the Menlo the frozen browser
//  refs were rasterised with — as a DOCUMENT @font-face NAMED `monospace`,
//  relying on two existing rules rather than on any new resolver code:
//
//    1. css-fonts-4 §5 / wave-35 lane B2: a family name is resolved against
//       the document's own font database BEFORE any generic, so a declared
//       face named `monospace` wins over the `.monospaced` system design
//       (SF Mono, whose '0' advance is 1266/2048 vs Menlo's 1233/2048 — the
//       +0.5px-per-ch wall the pilot measures).
//    2. wave-36 lane M8: the UA fixed-default 13px quirk keys on the declared
//       family NAME, so pinning the face must not disarm it.
//
//  Byte-parallel twin: Compose `typography/font/MonoPinDocumentFaceTest.kt`.
//  Like DocumentFontRegistryTests, the face bytes come from an installed
//  monospace font (any real sfnt will do — it is the NAME routing under test,
//  not the outlines), and the suite skips rather than fails without one.
//

import XCTest
@testable import StyleConverterRuntime

final class MonoPinDocumentFaceTests: XCTestCase {

    override func tearDown() {
        // Process-scoped registry (CoreText registration is) — a leaked pin
        // would be exactly the cross-document shadowing bug `register` guards.
        DocumentFontRegistry.shared.clear()
        super.tearDown()
    }

    /// `<base>/_mono-pin/<name>` — the pilot's sandbox corner, copied from a
    /// real installed monospace face so CoreText has genuine bytes to name.
    private func makePinTree(name: String = "DejaVuSansMono.ttf") throws -> (base: URL, src: String)? {
        let candidates = ["/System/Library/Fonts/Supplemental/Courier New.ttf",
                          "/System/Library/Fonts/Supplemental/Andale Mono.ttf",
                          "/System/Library/Fonts/Supplemental/Arial.ttf"]
        guard let source = candidates.first(where: { FileManager.default.fileExists(atPath: $0) })
        else { return nil }
        let base = FileManager.default.temporaryDirectory
            .appendingPathComponent("w46y7-\(UUID().uuidString)", isDirectory: true)
        let dir = base.appendingPathComponent("_mono-pin", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try FileManager.default.copyItem(at: URL(fileURLWithPath: source),
                                         to: dir.appendingPathComponent(name))
        addTeardownBlock { try? FileManager.default.removeItem(at: base) }
        return (base, "_mono-pin/\(name)")
    }

    private func props(_ pairs: [(String, IRValue)]) -> [IRProperty] {
        pairs.map { IRProperty(type: $0.0, data: $0.1) }
    }

    /// The live wire: hyphens-manual-inline-010's bordered box declares ONLY
    /// the generic (plus a 32px size there; here the size is left OUT so the
    /// 13px quirk is exercised at the same time).
    private let bareMonospace = IRValue.array([.string("monospace")])

    // MARK: - the registry routes the generic's own name

    func testADocumentFaceNamedMonospaceRegistersUnderTheGenericName() throws {
        guard let tree = try makePinTree() else { throw XCTSkip("no installed monospace font file to copy") }
        let n = DocumentFontRegistry.shared.register(
            [IRFontFace(family: "monospace", src: tree.src, weight: "400", style: "normal")],
            baseDirectory: tree.base)
        XCTAssertEqual(n, 1)
        // The CSS name `monospace` now maps to the file's PostScript name —
        // the lookup TypographyApplier / StyleBuilder perform first.
        let resolved = DocumentFontRegistry.shared.resolvedName(for: "monospace")
        XCTAssertNotNil(resolved)
        XCTAssertEqual(DocumentFontRegistry.shared.resolvedName(for: " \"Monospace\" "), resolved)
    }

    // MARK: - StyleBuilder: the pin face wins over the .monospaced design

    func testStyleBuilderPicksThePinFaceAndKeepsThe13pxQuirk() throws {
        guard let tree = try makePinTree() else { throw XCTSkip("no installed monospace font file to copy") }
        DocumentFontRegistry.shared.register(
            [IRFontFace(family: "monospace", src: tree.src, weight: "400", style: "normal")],
            baseDirectory: tree.base)
        let style = StyleBuilder.build(from: props([("FontFamily", bareMonospace)]))
        // Rule 1: the declared face reaches the label (PlaceholderLabel's
        // `.custom(fontFaceName)` branch), ahead of the generic design — which
        // is STILL recorded, as the fallback ChUnitMetrics and
        // measurementUIFont measure when no document face resolves (wave-47
        // Z4 closed the pilot's two seams: with a registered face both now
        // measure the face itself — DocumentFaceMetricsTests pins that).
        XCTAssertEqual(style.text.fontFaceName, DocumentFontRegistry.shared.resolvedName(for: "monospace"))
        XCTAssertEqual(style.text.fontDesign, .monospaced)
        // Rule 2: no font-size + first family is the generic ⇒ 13px, pin or no
        // pin. The pin must never turn a 13px document into a 16px one.
        XCTAssertEqual(style.text.fontSize, 13)
        XCTAssertEqual(style.spacing.context.fontSizePx, 13)
    }

    // MARK: - the walk order the pilot relies on

    func testTheGenericBehindCourierNewReachesThePinThroughTheLabelBridge() throws {
        guard let tree = try makePinTree() else { throw XCTSkip("no installed monospace font file to copy") }
        DocumentFontRegistry.shared.register(
            [IRFontFace(family: "monospace", src: tree.src, weight: "400", style: "normal")],
            baseDirectory: tree.base)
        // hyphens-auto-control's payload. StyleBuilder's label bridge
        // (Renderer/StyleBuilder.swift, wave-35 B2) takes the FIRST name the
        // DOCUMENT registered and never probes installed concrete faces — so
        // today the label paints the `.monospaced` design (SF Mono) here, not
        // the installed Courier New the ref renders, and with the pin it
        // paints the pin. Stated blast radius of the pilot: this cell moves
        // from SF Mono to DejaVu, both a stand-in for the ref's Courier New.
        let style = StyleBuilder.build(from: props([
            ("FontFamily", .array([.string("Courier New"), .string("Courier"), .string("monospace")])),
        ]))
        XCTAssertEqual(style.text.fontFaceName, DocumentFontRegistry.shared.resolvedName(for: "monospace"))
        // And the 13px quirk stays OFF for a concrete-first list, pin or not.
        XCTAssertNil(style.text.fontSize)
        // Without the pin the same list registers nothing: the walk's generic
        // misses the registry and the label keeps the generic design.
        DocumentFontRegistry.shared.clear()
        let unpinned = StyleBuilder.build(from: props([
            ("FontFamily", .array([.string("Courier New"), .string("Courier"), .string("monospace")])),
        ]))
        XCTAssertNil(unpinned.text.fontFaceName)
        XCTAssertEqual(unpinned.text.fontDesign, .monospaced)
    }

    // MARK: - the §5.2 walk carries generics; names stays concrete-only

    func testExtractorWalkKeepsGenericsInAuthorOrderWhileNamesStayConcrete() {
        let cfg = FontFamilyExtractor.extract(from: props([
            ("FontFamily", .array([.string("Courier New"), .string("monospace"), .string("Inter")])),
        ]))
        XCTAssertEqual(cfg?.walk, ["Courier New", "monospace", "Inter"])
        XCTAssertEqual(cfg?.names, ["Courier New", "Inter"])
        XCTAssertEqual(cfg?.hasMonospace, true)
        // The object entry shape too ({"keyword": …} rides the walk).
        let obj = FontFamilyExtractor.extract(from: props([
            ("FontFamily", .array([.object(["keyword": .string("serif")]), .object(["name": .string("Georgia")])])),
        ]))
        XCTAssertEqual(obj?.walk, ["serif", "Georgia"])
        XCTAssertEqual(obj?.names, ["Georgia"])
    }

    func testNoGenericKeywordIsAnInstalledFontName() {
        // The walk hands generics to TypographyApplier's
        // `registry ?? UIFont(name:)` probe. They must keep missing UIFont,
        // or a keyword would be picked as a `.custom` face. Pins the
        // assumption against the installed font book.
        for g in ["monospace", "serif", "sans-serif", "cursive", "fantasy", "system-ui",
                  "ui-serif", "ui-sans-serif", "ui-monospace", "ui-rounded", "emoji", "math", "fangsong"] {
            XCTAssertNil(UIFont(name: g, size: 12), "'\(g)' must not resolve as a font name")
        }
    }

    func testAFaceFreeDocumentIsUntouchedByThePinMachinery() {
        // The universal case: no registration ⇒ the generic design alone.
        let style = StyleBuilder.build(from: props([("FontFamily", bareMonospace)]))
        XCTAssertNil(style.text.fontFaceName)
        XCTAssertEqual(style.text.fontDesign, .monospaced)
        XCTAssertEqual(style.text.fontSize, 13)
    }
}
