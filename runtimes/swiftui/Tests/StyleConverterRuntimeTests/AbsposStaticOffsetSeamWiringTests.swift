//
//  AbsposStaticOffsetSeamWiringTests.swift
//  Wave 52 (lane L7, static-position T2) — SOURCE pin for the one seam hunk
//  lane L7 delivers in tools/titan/results/wave52-static-position/
//  seam-2.patch (this file ships INSIDE that patch, so it lands with it).
//  positionedChildren() is a private SwiftUI view builder; the math it calls
//  is pinned in AbsposFlexStaticOffsetTests (S1–S4). This pin proves the
//  seam hands staticOffset the CONTENT-box extents (flexContentSize), not
//  the §3.1 padding box (childCB / childCBH, which stay the percent basis).
//  NEGATIVE CONTROL EXECUTED: run on bare HEAD (seam not applied) → fails
//  (tools/titan/results/wave52-static-position/_seam2-verify.log).
//

import XCTest

final class AbsposStaticOffsetSeamWiringTests: XCTestCase {

    /// The renderer's source text, located from this file (#filePath).
    private func rendererSource() throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // StyleConverterRuntimeTests/
            .deletingLastPathComponent()   // Tests/
            .deletingLastPathComponent()   // runtimes/swiftui/
            .appendingPathComponent("Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift")
        return try String(contentsOf: url, encoding: .utf8)
    }

    func testWptStaticOffsetCallPassesContentBoxExtents() throws {
        let src = try rendererSource()
        // The WPT-capture call inside positionedChildren().
        guard let start = src.range(of: "? AbsposStaticPosition.staticOffset("),
              let end = src.range(of: "wptCaptureMode: true)", range: start.upperBound..<src.endIndex)
        else { return XCTFail("staticOffset call not found") }
        let call = String(src[start.lowerBound..<end.upperBound])
        // Content-box extents on both axes (css-flexbox-1 §4.1).
        XCTAssertTrue(call.contains("containerW: flexContentSize(style: style, vertical: false)"))
        XCTAssertTrue(call.contains("containerH: flexContentSize(style: style, vertical: true)"))
        // The padding-box channels are no longer the alignment container.
        XCTAssertFalse(call.contains("containerW: childCB,"))
        XCTAssertFalse(call.contains("containerH: childCBH,"))
    }
}
