//
//  MulticolDiscardThreadingTests.swift
//  Wave-43 lane V6 — the iOS `continue: discard` THREADING pins.
//
//  Wave-42 W4 landed MulticolSpannerFlow.plan's discardOverflow on BOTH
//  natives (the shared BRK/DSC pin table), and Compose threads its config
//  flag into the plan (MultiColumnApplier → MulticolSpannerFlow.plan) —
//  but iOS shipped the plan machinery WITHOUT the wire: ColumnsConfig had
//  no discard field, ComponentRenderer never passed one, and
//  MulticolGreedyLayout called plan() with the flag defaulted false. The
//  measured consequence (wave42-final css-overflow, ios-ref):
//  discard-multicol-003 at SSIM 0.8244 — the 4th `break-after: column`
//  chunk lands in a css-multicol-1 §8.2 overflow column right of the box
//  and "Spanner 1" paints on a second row instead of being discarded
//  (css-overflow-4 §3: content from the first overflow column on drops,
//  spanners included). These tests pin the three hops of the new thread:
//  IR → ColumnsConfig.continueDiscard → MulticolGreedyLayout.discardOverflow
//  → MulticolSpannerFlow.plan(discardOverflow:).
//

import XCTest
@testable import StyleConverterRuntime

final class MulticolDiscardThreadingTests: XCTestCase {

    // MARK: - hop 1: IR "Continue" → ColumnsConfig.continueDiscard

    /// The live discard-multicol container declarations (wave42-final
    /// per-test IR): ColumnCount 3 (bare number wire) + Continue "DISCARD"
    /// (bare keyword-string wire, converter ContinueProperty).
    private func discardMulticolProps() -> [IRProperty] {
        [IRProperty(type: "ColumnCount", data: .double(3)),
         IRProperty(type: "Continue", data: .string("DISCARD"))]
    }

    /// The DISCARD keyword flips the flag on a real multicol config.
    func testContinueDiscardExtracts() throws {
        let cfg = try XCTUnwrap(ColumnsExtractor.extract(from: discardMulticolProps()))
        // The typed §3.1 count still extracts alongside the new flag.
        XCTAssertEqual(cfg.count, 3)
        // css-overflow-4 §3: DISCARD is the one keyword that engages.
        XCTAssertTrue(cfg.continueDiscard)
    }

    /// `continue: auto` (the initial value) keeps normal overflow.
    func testContinueAutoStaysOff() throws {
        let cfg = try XCTUnwrap(ColumnsExtractor.extract(from:
            [IRProperty(type: "ColumnCount", data: .double(3)),
             IRProperty(type: "Continue", data: .string("AUTO"))]))
        XCTAssertFalse(cfg.continueDiscard, "auto must not discard")
    }

    /// No Continue declaration at all — the flag stays at its false default.
    func testAbsentContinueStaysOff() throws {
        let cfg = try XCTUnwrap(ColumnsExtractor.extract(from:
            [IRProperty(type: "ColumnCount", data: .double(3))]))
        XCTAssertFalse(cfg.continueDiscard, "absent must default off")
    }

    /// "Continue" alone must NOT fabricate a columns config: the property
    /// is REGISTERED by the regions no-op tree, and css-multicol-1 §2 says
    /// only a non-auto count/width establishes a multicol context — the
    /// extractor reads the keyword without flipping `touched`.
    func testContinueAloneDoesNotTouch() {
        XCTAssertNil(ColumnsExtractor.extract(from:
            [IRProperty(type: "Continue", data: .string("DISCARD"))]),
            "a Continue-only list must not create a columns config")
    }

    // MARK: - hop 1b: the cascade fold is last-write-wins (wave-43 G3)

    /// A duplicated `continue` declaration resolves by ORDER OF APPEARANCE
    /// (css-cascade-5 §6.4.4), so the LAST one wins — here `auto`, which
    /// keeps normal §8.2 overflow rendering.
    ///
    /// Wave-42 folded this property with `properties.contains { … DISCARD }`
    /// — any-DISCARD-wins — so this exact wire extracted `true` on iOS while
    /// Compose's per-declaration reassignment (MultiColumnExtractor lines
    /// 40-41) extracted `false`. The twin of this pin lives in Compose's
    /// MulticolRunFragmentGateTest.
    func testDuplicateContinueTakesTheLastDeclarationDiscardThenAuto() throws {
        let cfg = try XCTUnwrap(ColumnsExtractor.extract(from:
            [IRProperty(type: "ColumnCount", data: .double(3)),
             IRProperty(type: "Continue", data: .string("DISCARD")),
             IRProperty(type: "Continue", data: .string("AUTO"))]))
        XCTAssertFalse(cfg.continueDiscard,
                       "[DISCARD, AUTO]: the later `auto` overrides the earlier discard")
    }

    /// The mirror order — the later `discard` overrides the earlier `auto`.
    /// Both directions are pinned because an any-KEYWORD-wins fold would
    /// pass one of them by accident; only last-write-wins passes both.
    func testDuplicateContinueTakesTheLastDeclarationAutoThenDiscard() throws {
        let cfg = try XCTUnwrap(ColumnsExtractor.extract(from:
            [IRProperty(type: "ColumnCount", data: .double(3)),
             IRProperty(type: "Continue", data: .string("AUTO")),
             IRProperty(type: "Continue", data: .string("DISCARD"))]))
        XCTAssertTrue(cfg.continueDiscard,
                      "[AUTO, DISCARD]: the later `discard` overrides the earlier auto")
    }

    // MARK: - hop 3 (the plan): config flag → discarded slots, live shape

    /// End-to-end through the NEW seam: extract the live
    /// discard-multicol-003 container config, then feed its flag into the
    /// shared plan with the test's real role sequence — 4 one-line
    /// `break-after: column` chunks + a trailing `column-span: all` under
    /// N = 3. Chunks 0–2 keep their columns; chunk 3 opens the §8.2
    /// overflow column, so css-overflow-4 §3 discards it AND the spanner
    /// after it, and the container is exactly one 19px line tall (the
    /// BRK1 pin row, now driven by the extractor instead of a literal).
    func testExtractedFlagDrivesTheLivePlanShape() throws {
        // The container's typed config through the real extractor.
        let cfg = try XCTUnwrap(ColumnsExtractor.extract(from: discardMulticolProps()))
        // The plan under the extracted inputs (19 = the fixture line height).
        let plan = MulticolSpannerFlow.plan(
            children: [.init(heightPx: 19, role: .flowBreakAfter),
                       .init(heightPx: 19, role: .flowBreakAfter),
                       .init(heightPx: 19, role: .flowBreakAfter),
                       .init(heightPx: 19, role: .flowBreakAfter),
                       .init(heightPx: 12, role: .spanner)],
            columnCount: try XCTUnwrap(cfg.count),
            discardOverflow: cfg.continueDiscard)
        // Chunks 0–2 own columns 0–2 whole (css-break-3 §4.1 sequencing).
        XCTAssertEqual(Array(plan.slots[0...2]), [
            .init(role: .flowBreakAfter, columnIndex: 0, yPx: 0),
            .init(role: .flowBreakAfter, columnIndex: 1, yPx: 0),
            .init(role: .flowBreakAfter, columnIndex: 2, yPx: 0),
        ])
        // The overflow chunk and the spanner after it are DISCARDED.
        XCTAssertTrue(plan.slots[3].discarded, "the §8.2 overflow chunk must drop")
        XCTAssertTrue(plan.slots[4].discarded, "everything after it drops too — spanners included")
        // Discarded content contributes no block-size: one line tall,
        // exactly the ref box (discard-multicol-003's match reference).
        XCTAssertEqual(plan.containerBlockSizePx, 19)
    }

    // MARK: - hop 2 (the wiring): source pins for the two seams

    /// Walk up from THIS SOURCE FILE until the relative path resolves
    /// (the BackdropContractParityTests precedent, byte-for-byte: the
    /// Catalyst test process's working directory sits OUTSIDE the repo,
    /// so #filePath — compiled into the binary — is the only reliable
    /// anchor for a source-scan pin).
    private static func repoPath(_ relative: String) throws -> String {
        // Start beside this file and climb toward the filesystem root.
        var dir = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        while dir.path != "/" {
            // First hit wins — the repo checkout contains the target once.
            let candidate = dir.appendingPathComponent(relative)
            if FileManager.default.fileExists(atPath: candidate.path) {
                return candidate.path
            }
            dir = dir.deletingLastPathComponent()
        }
        // A moved checkout (CI sandboxes copy sources) skips rather than
        // fails — the pin is structural, not behavioral, and the five
        // behavioral tests above still run everywhere.
        throw XCTSkip("repo root not found for \(relative)")
    }

    /// MulticolGreedyLayout declares the flag and hands it to the plan —
    /// pinned at the source because the Layout protocol only runs inside
    /// a live SwiftUI host (no headless layout pass in XCTest).
    func testGreedyLayoutThreadsTheFlagIntoThePlan() throws {
        let src = try String(contentsOfFile: Self.repoPath(
            "runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/columns/MulticolGreedyLayout.swift"),
            encoding: .utf8)
        // The stored property exists, defaulted false (dark-stage identity).
        XCTAssertTrue(src.contains("var discardOverflow: Bool = false"),
                      "MulticolGreedyLayout must carry the discard flag")
        // …and the spanner-flow plan call receives it (not the default).
        XCTAssertTrue(src.contains("discardOverflow: discardOverflow"),
                      "the plan call must thread the layout's flag")
    }

    /// ComponentRenderer's construction seam passes the typed config flag —
    /// the exact wire wave-42 shipped without.
    func testRendererSeamPassesTheConfigFlag() throws {
        let src = try String(contentsOfFile: Self.repoPath(
            "runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift"),
            encoding: .utf8)
        XCTAssertTrue(
            src.contains("discardOverflow: style.columns?.continueDiscard ?? false"),
            "the renderer must thread ColumnsConfig.continueDiscard into MulticolGreedyLayout")
    }
}
