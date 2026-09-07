//
//  EasingReferenceTests.swift
//  Cross-runtime easing conformance (Wave 2).
//
//  An animation is a function from time to a value, and most of what can go
//  wrong in that function — a wrong curve, a wrong step boundary, a wrong
//  endpoint — is pure arithmetic. This asserts that arithmetic against
//  schema/conformance/easing/easing-reference.json, the SAME table the
//  Compose and web suites use, so a divergence is attributable to a named
//  easing function at a named input with no simulator and no screenshot.
//
//  The reference is generated from css-easing-1 by
//  schema/conformance/easing/gen-easing-reference.mjs — deliberately NOT by
//  running any runtime, since a table generated from an implementation would
//  enshrine that implementation's bugs as the standard. It already caught a
//  real one: Compose's steps(n, jump-both) clamped to the step count instead
//  of to `jumps`, so those animations never reached their end state.
//

import XCTest
@testable import StyleConverterRuntime

final class EasingReferenceTests: XCTestCase {

    // MARK: - Locating the shared table

    /// Walk up from this source file to the repo root. `#filePath` is used
    /// rather than a working directory or a test bundle resource because the
    /// suite is run through an xcodebuild Mac Catalyst destination, whose cwd
    /// is not the package root and whose bundle does not carry repo files.
    private func referenceURL() throws -> URL {
        var dir = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        for _ in 0..<10 {
            let candidate = dir
                .appendingPathComponent("schema/conformance/easing/easing-reference.json")
            if FileManager.default.fileExists(atPath: candidate.path) { return candidate }
            dir = dir.deletingLastPathComponent()
        }
        throw XCTSkip("easing-reference.json not found walking up from \(#filePath)")
    }

    private func loadTable() throws -> [String: Any] {
        let data = try Data(contentsOf: try referenceURL())
        guard let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            XCTFail("easing-reference.json is not a JSON object"); return [:]
        }
        return obj
    }

    /// Build the runtime's timing-function value for one reference case.
    /// Returns nil only for shapes this runtime genuinely cannot express —
    /// there are none today, which the coverage test below pins.
    private func timingFn(for c: [String: Any]) -> AnimationTimingFn? {
        switch c["kind"] as? String {
        case "cubic-bezier":
            guard let cb = c["cubicBezier"] as? [String: Any],
                  let x1 = cb["x1"] as? Double, let y1 = cb["y1"] as? Double,
                  let x2 = cb["x2"] as? Double, let y2 = cb["y2"] as? Double else { return nil }
            return .cubicBezier(x1: x1, y1: y1, x2: x2, y2: y2)

        case "steps":
            guard let st = c["steps"] as? [String: Any],
                  let count = st["count"] as? Int,
                  let raw = st["position"] as? String,
                  let pos = StepsPosition(rawValue: raw) else { return nil }
            return .steps(count: count, position: pos)

        case "linear":
            guard let stops = c["linearStops"] as? [[String: Any]] else { return nil }
            // The table stores positions as 0–1 (the spec's progress domain);
            // LinearStop carries a PERCENT. Converting here rather than
            // changing the table keeps the table platform-neutral.
            return .linearStops(stops.map { s in
                LinearStop(
                    value: (s["value"] as? Double) ?? 0,
                    percent: (s["position"] as? Double).map { $0 * 100.0 }
                )
            })

        default:
            return nil
        }
    }

    // MARK: - The table itself must be intact

    func testReferenceTableLoadsAndIsNonTrivial() throws {
        let table = try loadTable()
        XCTAssertEqual(table["version"] as? Int, 1)
        let cases = table["cases"] as? [[String: Any]] ?? []
        XCTAssertGreaterThanOrEqual(cases.count, 30, "expected a substantial case list")
        let samples = cases.reduce(0) { $0 + (($1["samples"] as? [Any])?.count ?? 0) }
        XCTAssertEqual(samples, table["sampleCount"] as? Int)
    }

    func testKeywordControlPointsMatchTheSpec() throws {
        // css-easing-1 §2.2. AnimationDriver documents `ease` as the CSS
        // initial timing function; if the constant drifts from the spec every
        // keyword-built curve is quietly wrong, and no pixel comparison would
        // attribute the error to the keyword mapping.
        let table = try loadTable()
        let cases = table["cases"] as? [[String: Any]] ?? []
        let expected: [String: [Double]] = [
            "keyword-linear": [0, 0, 1, 1],
            "keyword-ease": [0.25, 0.1, 0.25, 1],
            "keyword-ease-in": [0.42, 0, 1, 1],
            "keyword-ease-out": [0, 0, 0.58, 1],
            "keyword-ease-in-out": [0.42, 0, 0.58, 1],
        ]
        for (id, want) in expected {
            guard let c = cases.first(where: { ($0["id"] as? String) == id }),
                  let cb = c["cubicBezier"] as? [String: Any] else {
                XCTFail("missing case \(id)"); continue
            }
            let got = [cb["x1"] as? Double, cb["y1"] as? Double, cb["x2"] as? Double, cb["y2"] as? Double]
            XCTAssertEqual(got.compactMap { $0 }, want, "\(id) control points")
        }
    }

    // MARK: - The conformance sweep

    func testEveryEasingCaseMatchesTheReference() throws {
        let table = try loadTable()
        let cases = table["cases"] as? [[String: Any]] ?? []
        let tol = table["tolerance"] as? [String: Any] ?? [:]
        let bezierTol = (tol["cubicBezier"] as? Double) ?? 1e-4
        let exactTol = (tol["stepsAndLinear"] as? Double) ?? 1e-9

        var failures: [String] = []
        var checked = 0

        for c in cases {
            guard let fn = timingFn(for: c) else { continue }
            let id = (c["id"] as? String) ?? "?"
            let kind = (c["kind"] as? String) ?? "?"
            // steps() and linear() are exact rational arithmetic; only the
            // bezier solver gets float slack.
            let tolerance = (kind == "cubic-bezier") ? bezierTol : exactTol

            for s in (c["samples"] as? [[String: Any]] ?? []) {
                guard let t = s["t"] as? Double, let expected = s["expected"] as? Double else { continue }
                let actual = AnimationDriver.ease(t, with: fn)
                checked += 1
                if abs(actual - expected) > tolerance {
                    failures.append("\(id)  t=\(t)  expected=\(expected)  actual=\(actual)  (Δ=\(actual - expected), tol=\(tolerance))")
                }
            }
        }

        XCTAssertGreaterThan(checked, 400, "expected to check a meaningful number of samples")
        if !failures.isEmpty {
            XCTFail("""
                \(failures.count) of \(checked) easing samples diverge from \
                schema/conformance/easing/easing-reference.json:
                \(failures.prefix(40).joined(separator: "\n"))
                """)
        }
    }

    // MARK: - Honest coverage reporting

    func testEveryCaseIsRepresentable() throws {
        // A conformance suite that quietly drops what it cannot express is
        // indistinguishable from one that passes. SwiftUI's AnimationTimingFn
        // covers all three shapes — unlike Compose, whose TimingFunctionConfig
        // has no linearStops field. If that ever regresses, this fails loudly
        // instead of the sweep silently checking fewer cases.
        let table = try loadTable()
        let cases = table["cases"] as? [[String: Any]] ?? []
        let unrepresentable = cases
            .filter { timingFn(for: $0) == nil }
            .compactMap { $0["id"] as? String }
        XCTAssertEqual(unrepresentable, [], "SwiftUI must be able to express every reference case")
    }
}
