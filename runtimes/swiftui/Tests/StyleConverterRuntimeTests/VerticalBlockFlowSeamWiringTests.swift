//
//  VerticalBlockFlowSeamWiringTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 52 (lane L3 · failure-ink, fix F1) — the SEAM WIRING pin, shipped
//  inside seam-2.patch together with the seam it pins. Twin of Compose
//  VerticalBlockFlowSeamWiringTest.kt (shipped inside seam-1.patch).
//
//  WHY THIS EXISTS (wave-52 skeptic, should-fix 2): VerticalBlockFlowSeamGuardTests
//  pins the PREDICATE (BakedLayoutSignature.bakedPhysicalBox) but nothing
//  pinned that ComponentRenderer.verticalBlockFlowZ2() CALLS it. With seam-2
//  dropped or mis-merged (many lanes deliver hunks into that one file) every
//  predicate pin stays green and
//  css-writing-modes/flexbox_align-items-stretch-writing-modes silently keeps
//  its frozen VStack. The guard sits inside a private method of a View, so a
//  SOURCE-level read (the ChUnitInlineAxisTests / ConformanceTests #filePath
//  walk) is the honest unit-level check.
//
//  NEGATIVE CONTROL, EXECUTED (fix pass 2026-10-05, log
//  results/wave52-failure-ink/_seam2-verify.log): with ComponentRenderer.swift
//  at HEAD 7d9c22a7 bytes (seam-2 NOT applied) both tests FAIL.
//

import XCTest

final class VerticalBlockFlowSeamWiringTests: XCTestCase {

    /// ComponentRenderer.swift, resolved from this file's own path:
    /// Tests/StyleConverterRuntimeTests/ → runtimes/swiftui/ → Sources/….
    private func rendererSource() throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // StyleConverterRuntimeTests/
            .deletingLastPathComponent()   // Tests/
            .deletingLastPathComponent()   // runtimes/swiftui/
            .appendingPathComponent(
                "Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift")
        return try String(contentsOf: url, encoding: .utf8)
    }

    /// The Z2 seam's guard section: from the method's declaration to the
    /// "seam owns this container" breadcrumb that follows its last guard.
    private func z2Guards() throws -> Substring {
        let src = try rendererSource()
        // The method's declaration — the seam's own name since wave 47.
        let start = try XCTUnwrap(
            src.range(of: "private func verticalBlockFlowZ2() -> Bool?"),
            "verticalBlockFlowZ2() is missing from ComponentRenderer.swift")
        // The breadcrumb after the guards bounds the section.
        let end = try XCTUnwrap(
            src.range(of: "// The seam owns this container",
                      range: start.upperBound..<src.endIndex),
            "the post-guard breadcrumb no longer follows verticalBlockFlowZ2()")
        return src[start.lowerBound..<end.lowerBound]
    }

    /// The baked guard reads the ONE shared post-load signature predicate.
    func testZ2BakedGuardReadsBakedLayoutSignature() throws {
        XCTAssertTrue(try z2Guards().contains(
            "if BakedLayoutSignature.bakedPhysicalBox(child.properties) { return nil }"),
            "verticalBlockFlowZ2 must decline through BakedLayoutSignature (seam-2)")
    }

    /// The wave-47 two-property heuristic (authored squares trip it) is gone.
    func testZ2DropsTheWave47WidthHeightHeuristic() throws {
        XCTAssertFalse(try z2Guards().contains(#"$0.type == "Height""#),
            "verticalBlockFlowZ2 still reads the bare Width ∧ Height heuristic")
    }
}
