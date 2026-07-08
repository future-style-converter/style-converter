//
//  TransformsTests.swift
//  StyleConverterRuntimeTests
//
//  XCTest port of the Phase 8 transforms launch self-test (formerly
//  StyleEngine/transforms/TransformsSelfTest.swift). The check body is
//  unchanged — every failure-collection site survives 1:1; the print-
//  PASS/FAIL summary becomes one XCTFail per collected failure name.
//

import Foundation
import XCTest
// @testable: the transforms extractors are internal to the module.
@testable import StyleConverterRuntime

final class TransformsTests: XCTestCase {

    func testTransformsFamily() {
        var f: [String] = []

        // Helpers — succinct IR builders.
        func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
        func props(_ p: [(String, IRValue)]) -> [IRProperty] {
            p.map { IRProperty(type: $0.0, data: $0.1) }
        }

        // ── Registry drift check ─────────────────────────────────────
        let missing = TransformsProperty.set.subtracting(PropertyRegistry.migrated).sorted()
        if !missing.isEmpty {
            f.append("Missing from PropertyRegistry.migrated: \(missing.joined(separator: ", "))")
        }

        // ── transform: none ──────────────────────────────────────────
        let none = TransformsExtractor.extract(from: props([
            ("Transform", obj(["type": .string("none")])),
        ]))
        if none?.touched != true || none?.functions.isEmpty != true {
            f.append("Transform: 'none' did not produce touched-empty")
        }

        // ── transform: translateX(20px) ──────────────────────────────
        let tX = TransformsExtractor.extract(from: props([
            ("Transform", obj([
                "type": .string("functions"),
                "list": .array([
                    obj(["fn": .string("translateX"),
                         "x": obj(["px": .double(20)])]),
                ]),
            ])),
        ]))
        if tX?.functions.count != 1 { f.append("Transform: translateX not added") }
        if case .translate(let x, _, _, _, _) = tX?.functions.first ?? .perspective(d: 0) {
            if x != 20 { f.append("Transform: translateX x != 20") }
        } else { f.append("Transform: translateX kind mismatch") }

        // ── transform: rotate(45deg) ─────────────────────────────────
        let rot = TransformsExtractor.extract(from: props([
            ("Transform", obj([
                "type": .string("functions"),
                "list": .array([
                    obj(["fn": .string("rotate"),
                         "a": obj(["deg": .double(45)])]),
                ]),
            ])),
        ]))
        if case .rotate(_, _, _, let d) = rot?.functions.first ?? .perspective(d: 0) {
            if d != 45 { f.append("Transform: rotate deg != 45") }
        } else { f.append("Transform: rotate kind mismatch") }

        // ── transform: scale(1.5) + translate chain ordering ─────────
        let chain = TransformsExtractor.extract(from: props([
            ("Transform", obj([
                "type": .string("functions"),
                "list": .array([
                    obj(["fn": .string("scale"),
                         "x": .double(1.5), "y": .double(1.5)]),
                    obj(["fn": .string("translate"),
                         "x": obj(["px": .double(10)]),
                         "y": obj(["px": .double(20)])]),
                ]),
            ])),
        ]))
        if chain?.functions.count != 2 { f.append("Transform: chain count != 2") }

        // ── matrix(1, .2, -.2, 1, 10, 20) ────────────────────────────
        let mat = TransformsExtractor.extract(from: props([
            ("Transform", obj([
                "type": .string("functions"),
                "list": .array([
                    obj(["fn": .string("matrix"),
                         "a": .double(1), "b": .double(0.2),
                         "c": .double(-0.2), "d": .double(1),
                         "e": .double(10), "f": .double(20)]),
                ]),
            ])),
        ]))
        if case .matrix(_, _, _, _, let e, let f2) = mat?.functions.first ?? .perspective(d: 0) {
            if e != 10 || f2 != 20 { f.append("Transform: matrix tx/ty") }
        } else { f.append("Transform: matrix kind mismatch") }

        // ── skew(10deg, 5deg) ────────────────────────────────────────
        let sk = TransformsExtractor.extract(from: props([
            ("Transform", obj([
                "type": .string("functions"),
                "list": .array([
                    obj(["fn": .string("skew"),
                         "x": obj(["deg": .double(10)]),
                         "y": obj(["deg": .double(5)])]),
                ]),
            ])),
        ]))
        if case .skew(let xd, let yd) = sk?.functions.first ?? .perspective(d: 0) {
            if xd != 10 || yd != 5 { f.append("Transform: skew deg") }
        } else { f.append("Transform: skew kind mismatch") }

        // ── rotate longhand: axis-angle ──────────────────────────────
        let rotLong = TransformsExtractor.extract(from: props([
            ("Rotate", obj([
                "type": .string("axis-angle"),
                "x": .double(1), "y": .double(0), "z": .double(0),
                "angle": obj(["deg": .double(45)]),
            ])),
        ]))
        if case .rotate(let x, _, _, let d) = rotLong?.rotate ?? .perspective(d: 0) {
            if x != 1 || d != 45 { f.append("Rotate longhand: axis-angle x/deg") }
        } else { f.append("Rotate longhand: kind") }

        // ── scale longhand: uniform + 2d ─────────────────────────────
        let scU = TransformsExtractor.extract(from: props([
            ("Scale", obj(["type": .string("uniform"), "value": .double(1.5)])),
        ]))
        if case .scale(let x, let y, _) = scU?.scale ?? .perspective(d: 0) {
            if x != 1.5 || y != 1.5 { f.append("Scale longhand: uniform x/y") }
        } else { f.append("Scale longhand: uniform kind") }

        let sc2 = TransformsExtractor.extract(from: props([
            ("Scale", obj(["type": .string("2d"),
                           "x": .double(1.2), "y": .double(0.8)])),
        ]))
        if case .scale(let x, let y, _) = sc2?.scale ?? .perspective(d: 0) {
            if x != 1.2 || y != 0.8 { f.append("Scale longhand: 2d x/y") }
        } else { f.append("Scale longhand: 2d kind") }

        // ── translate longhand: length + 2d ──────────────────────────
        let trL = TransformsExtractor.extract(from: props([
            ("Translate", obj([
                "type": .string("length"),
                "length": obj(["px": .double(20)]),
            ])),
        ]))
        if case .translate(let x, _, _, _, _) = trL?.translate ?? .perspective(d: 0) {
            if x != 20 { f.append("Translate longhand: length x") }
        } else { f.append("Translate longhand: length kind") }

        let tr2 = TransformsExtractor.extract(from: props([
            ("Translate", obj([
                "type": .string("2d"),
                "x": obj(["type": .string("length"), "px": .double(20)]),
                "y": obj(["type": .string("length"), "px": .double(10)]),
            ])),
        ]))
        if case .translate(let x, let y, _, _, _) = tr2?.translate ?? .perspective(d: 0) {
            if x != 20 || y != 10 { f.append("Translate longhand: 2d x/y") }
        } else { f.append("Translate longhand: 2d kind") }

        // ── transform-origin: keyword pair ───────────────────────────
        let origKW = TransformsExtractor.extract(from: props([
            ("TransformOrigin", obj([
                "x": obj(["type": .string("keyword"), "value": .string("LEFT")]),
                "y": obj(["type": .string("keyword"), "value": .string("TOP")]),
            ])),
        ]))
        if origKW?.origin?.unit.x != 0 || origKW?.origin?.unit.y != 0 {
            f.append("TransformOrigin: LEFT TOP not 0/0")
        }
        // percentage pair.
        let origP = TransformsExtractor.extract(from: props([
            ("TransformOrigin", obj([
                "x": obj(["type": .string("percentage"), "percentage": .double(50)]),
                "y": obj(["type": .string("percentage"), "percentage": .double(50)]),
            ])),
        ]))
        if origP?.origin?.unit.x != 0.5 || origP?.origin?.unit.y != 0.5 {
            f.append("TransformOrigin: 50% 50% not 0.5/0.5")
        }

        // ── transform-box: all 5 keywords ────────────────────────────
        let boxCases: [(String, TransformBoxKind)] = [
            ("CONTENT_BOX", .contentBox), ("BORDER_BOX", .borderBox),
            ("FILL_BOX", .fillBox), ("STROKE_BOX", .strokeBox),
            ("VIEW_BOX", .viewBox),
        ]
        for (s, exp) in boxCases {
            let r = TransformsExtractor.extract(from: props([
                ("TransformBox", .string(s)),
            ]))
            if r?.box != exp { f.append("TransformBox: \(s) did not resolve") }
        }

        // ── transform-style + backface-visibility ────────────────────
        let ts = TransformsExtractor.extract(from: props([
            ("TransformStyle", .string("PRESERVE_3D")),
        ]))
        if ts?.preserve3D != true { f.append("TransformStyle: PRESERVE_3D flag") }

        let bf = TransformsExtractor.extract(from: props([
            ("BackfaceVisibility", .string("HIDDEN")),
        ]))
        if bf?.backfaceHidden != true { f.append("BackfaceVisibility: HIDDEN flag") }

        // ── perspective: length + none ───────────────────────────────
        let psp = TransformsExtractor.extract(from: props([
            ("Perspective", obj(["type": .string("length"), "px": .double(500)])),
        ]))
        if psp?.perspective?.distancePx != 500 { f.append("Perspective: 500px") }
        let pspNone = TransformsExtractor.extract(from: props([
            ("Perspective", obj(["type": .string("none")])),
        ]))
        if pspNone?.perspective?.distancePx != nil {
            f.append("Perspective: none should yield nil distance")
        }

        // ── perspective-origin: center / pct pair ────────────────────
        let po = TransformsExtractor.extract(from: props([
            ("PerspectiveOrigin", obj([
                "x": obj(["type": .string("center")]),
                "y": obj(["type": .string("center")]),
            ])),
        ]))
        if po?.perspective?.origin.x != 0.5 { f.append("PerspectiveOrigin: center") }

        // Absent → nil.
        if TransformsExtractor.extract(from: props([])) != nil {
            f.append("Transforms: absent should yield nil aggregate")
        }

        // ── Report ───────────────────────────────────────────────────
        // One XCTFail per collected failure name (was: print PASS/FAIL).
        for failure in f { XCTFail(failure) }
    }
}
