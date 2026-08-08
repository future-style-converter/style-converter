//
//  DocumentFontRegistryTests.swift
//  StyleConverterRuntimeTests — wave-35 lane B2
//
//  Pins for the document `@font-face` registry (spec 01 §5).
//
//  These run under Mac Catalyst with a REAL CoreText, so unlike the Compose
//  twin they can register an actual font file and assert the CSS-name →
//  platform-name mapping end to end. What they deliberately do NOT assert is
//  how the face RASTERISES — that is the device gate's job.
//
//  The fixture font is synthesised from a face already on the machine rather
//  than committed: the suite must not depend on the gitignored WPT corpus, and
//  copying an installed face gives CoreText real bytes to describe.
//

import XCTest
import CoreText
@testable import StyleConverterRuntime

final class DocumentFontRegistryTests: XCTestCase {

    override func tearDown() {
        // The registry is process-scoped by construction (CoreText registration
        // is), so a face leaking into the next test would reproduce exactly the
        // cross-document shadowing bug `register` exists to prevent.
        DocumentFontRegistry.shared.clear()
        super.tearDown()
    }

    /// A directory holding `css/res/<name>` copied from a real installed font,
    /// mirroring the corpus-relative layout the feeder pushes.
    private func makeFontTree(name: String = "Face.ttf") throws -> (base: URL, src: String)? {
        // Any installed face will do — we need real font BYTES, not a
        // particular typeface. Skip (rather than fail) if the machine has none
        // at the expected path: an environment without it is not a defect in
        // the code under test.
        let candidates = ["/System/Library/Fonts/Supplemental/Arial.ttf",
                          "/System/Library/Fonts/Supplemental/Courier New.ttf",
                          "/Library/Fonts/Arial.ttf"]
        guard let source = candidates.first(where: { FileManager.default.fileExists(atPath: $0) })
        else { return nil }
        let base = FileManager.default.temporaryDirectory
            .appendingPathComponent("w35b2-\(UUID().uuidString)", isDirectory: true)
        let dir = base.appendingPathComponent("css/res", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try FileManager.default.copyItem(at: URL(fileURLWithPath: source),
                                         to: dir.appendingPathComponent(name))
        addTeardownBlock { try? FileManager.default.removeItem(at: base) }
        return (base, "css/res/\(name)")
    }

    // MARK: - registration

    func testRegistersDeclaredFaceUnderItsCssFamilyName() throws {
        guard let tree = try makeFontTree() else {
            throw XCTSkip("no installed font file available to copy")
        }
        let n = DocumentFontRegistry.shared.register(
            [IRFontFace(family: "test", src: tree.src)], baseDirectory: tree.base)
        XCTAssertEqual(n, 1)
        // The mapping is the whole point: CoreText registered the file under
        // the FILE's own name, so `.custom("test")` would still miss without it.
        let resolved = DocumentFontRegistry.shared.resolvedName(for: "test")
        XCTAssertNotNil(resolved)
        XCTAssertNotEqual(resolved, "test", "the mapping must be the PLATFORM name, not the CSS name")
        XCTAssertTrue(DocumentFontRegistry.shared.lastReport.declined.isEmpty)
    }

    func testFamilyMatchingIsCaseInsensitiveAndQuoteTolerant() throws {
        guard let tree = try makeFontTree() else {
            throw XCTSkip("no installed font file available to copy")
        }
        // css-fonts-4 §4.2 — one family, however it is spelled.
        DocumentFontRegistry.shared.register(
            [IRFontFace(family: "Test", src: tree.src)], baseDirectory: tree.base)
        XCTAssertNotNil(DocumentFontRegistry.shared.resolvedName(for: "test"))
        XCTAssertNotNil(DocumentFontRegistry.shared.resolvedName(for: "TEST"))
        XCTAssertNotNil(DocumentFontRegistry.shared.resolvedName(for: "\"test\""))
    }

    func testRegisteredNameIsUsableByUIFont() throws {
        guard let tree = try makeFontTree() else {
            throw XCTSkip("no installed font file available to copy")
        }
        DocumentFontRegistry.shared.register(
            [IRFontFace(family: "test", src: tree.src)], baseDirectory: tree.base)
        let name = try XCTUnwrap(DocumentFontRegistry.shared.resolvedName(for: "test"))
        // The contract with TypographyApplier/ComponentRenderer: whatever this
        // returns must be a name `.custom(_:size:)` can actually instantiate.
        XCTAssertNotNil(UIFont(name: name, size: 12))
    }

    // MARK: - declines

    func testMissingFileDeclinesRatherThanRegistering() {
        let base = FileManager.default.temporaryDirectory
            .appendingPathComponent("w35b2-absent-\(UUID().uuidString)")
        let n = DocumentFontRegistry.shared.register(
            [IRFontFace(family: "test", src: "nope.woff")], baseDirectory: base)
        XCTAssertEqual(n, 0)
        // nil, not a fallback: the caller is walking a §5.2 list and must stay
        // free to continue to the next name.
        XCTAssertNil(DocumentFontRegistry.shared.resolvedName(for: "test"))
        XCTAssertEqual(DocumentFontRegistry.shared.lastReport.declined, ["test"])
    }

    func testNilBaseDirectoryDeclinesEveryFace() {
        // A bundled-asset run has no fonts hop at all. Degrading to the wave-34
        // behaviour is correct; pretending to register is not.
        let n = DocumentFontRegistry.shared.register(
            [IRFontFace(family: "test", src: "css/res/Face.ttf")], baseDirectory: nil)
        XCTAssertEqual(n, 0)
        XCTAssertEqual(DocumentFontRegistry.shared.lastReport.declined.count, 1)
    }

    func testNonFontBytesDecline() throws {
        let base = FileManager.default.temporaryDirectory
            .appendingPathComponent("w35b2-junk-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: base) }
        try Data("definitely not a font".utf8).write(to: base.appendingPathComponent("x.ttf"))
        // A file CoreText cannot describe is one it cannot register — declining
        // BEFORE registration keeps the process free of half-registered state.
        XCTAssertEqual(DocumentFontRegistry.shared.register(
            [IRFontFace(family: "test", src: "x.ttf")], baseDirectory: base), 0)
        XCTAssertNil(DocumentFontRegistry.shared.resolvedName(for: "test"))
    }

    // MARK: - document lifetime

    func testFaceFreeDocumentClearsThePreviousOne() throws {
        guard let tree = try makeFontTree() else {
            throw XCTSkip("no installed font file available to copy")
        }
        DocumentFontRegistry.shared.register(
            [IRFontFace(family: "test", src: tree.src)], baseDirectory: tree.base)
        XCTAssertNotNil(DocumentFontRegistry.shared.resolvedName(for: "test"))
        // Document N+1 declares nothing. The previous face must NOT survive, or
        // its file would shape text in a document that never asked for it.
        DocumentFontRegistry.shared.register(nil, baseDirectory: tree.base)
        XCTAssertNil(DocumentFontRegistry.shared.resolvedName(for: "test"))
        XCTAssertTrue(DocumentFontRegistry.shared.isEmpty)
    }

    func testEmptyRegistryIsTheUniversalCase() {
        // The guard every face-free capture takes: an empty registry must
        // resolve nothing, so the Inter/system pick stays byte-for-byte
        // identical to wave 34.
        XCTAssertTrue(DocumentFontRegistry.shared.isEmpty)
        XCTAssertNil(DocumentFontRegistry.shared.resolvedName(for: "Inter"))
        XCTAssertNil(DocumentFontRegistry.shared.resolvedName(for: "serif"))
    }

    func testPlatformFontNameReadsARealFile() throws {
        guard let tree = try makeFontTree() else {
            throw XCTSkip("no installed font file available to copy")
        }
        let url = tree.base.appendingPathComponent(tree.src)
        // Reading the name off CoreText's own descriptors (rather than guessing
        // it from the filename) is what makes the mapping trustworthy.
        XCTAssertNotNil(DocumentFontRegistry.platformFontName(of: url))
        XCTAssertNil(DocumentFontRegistry.platformFontName(
            of: tree.base.appendingPathComponent("absent.ttf")))
    }
}
