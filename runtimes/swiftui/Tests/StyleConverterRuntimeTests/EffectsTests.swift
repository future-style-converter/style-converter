//
//  EffectsTests.swift
//  StyleConverterRuntimeTests
//
//  XCTest port of the Phase 8 effects launch self-test (formerly
//  StyleEngine/effects/EffectsSelfTest.swift). Covers clip-path,
//  clip-rule, legacy clip, filter chain, backdrop, mask images +
//  keyword family, visibility, overflow. The check body is unchanged —
//  every failure-collection site survives 1:1; the print-PASS/FAIL
//  summary becomes one XCTFail per collected failure name.
//

import Foundation
import XCTest
// @testable: the effects extractors are internal to the module.
@testable import StyleConverterRuntime

final class EffectsTests: XCTestCase {

    func testEffectsAndVisibilityFamily() {
        var f: [String] = []
        func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
        func props(_ p: [(String, IRValue)]) -> [IRProperty] {
            p.map { IRProperty(type: $0.0, data: $0.1) }
        }

        // ── Registry drift ──────────────────────────────────────────
        let owned: Set<String> = ClipProperty.set
            .union(FilterProperty.set)
            .union(MaskProperty.set)
            .union(VisibilityProperty.set)
        let missing = owned.subtracting(PropertyRegistry.migrated).sorted()
        if !missing.isEmpty {
            f.append("Missing from PropertyRegistry.migrated: \(missing.joined(separator: ", "))")
        }

        // ── Clip ─────────────────────────────────────────────────────
        // clip-path: none
        let cpNone = ClipExtractor.extract(from: props([
            ("ClipPath", .string("none")),
        ]))
        if case .some(ClipShape.none) = cpNone?.shape { /* ok */ }
        else if cpNone?.touched != true { f.append("ClipPath: none not touched") }

        // inset(10 20 30 40 round 5)
        let ins = ClipExtractor.extract(from: props([
            ("ClipPath", obj([
                "type": .string("inset"),
                "t": obj(["px": .double(10)]),
                "r": obj(["px": .double(20)]),
                "b": obj(["px": .double(30)]),
                "l": obj(["px": .double(40)]),
                "round": obj(["px": .double(5)]),
            ])),
        ]))
        if case .inset(let t, let r, let b, let l, let cr, _) = ins?.shape ?? .none,
           t == 10, r == 20, b == 30, l == 40, cr == 5 { /* ok */ }
        else { f.append("ClipPath: inset 10 20 30 40 round 5 mismatch") }

        // circle(50px at 50% 50%)
        let circ = ClipExtractor.extract(from: props([
            ("ClipPath", obj([
                "type": .string("circle"),
                "px": .double(50),
            ])),
        ]))
        if case .circle(let r, let k, _, _) = circ?.shape ?? .none,
           r == 50, k == .length { /* ok */ }
        else { f.append("ClipPath: circle(50px) mismatch") }

        // swarm-003 css-masking/clip-path-borderBox-1a regression pin.
        // `circle(farthest-side)` — wire form is `r: "farthest-side"`
        // (JSON string primitive). Pre-fix the keyword was silently
        // dropped at parse so iOS never saw it. Now the extractor must
        // land it in the .farthestSide ShapeRadiusKind so the applier
        // can resolve against the rect at draw time.
        let circKw = ClipExtractor.extract(from: props([
            ("ClipPath", obj([
                "type": .string("circle"),
                "r": .string("farthest-side"),
            ])),
        ]))
        if case .circle(_, let k, _, _) = circKw?.shape ?? .none,
           k == .farthestSide { /* ok */ }
        else { f.append("ClipPath: circle(farthest-side) keyword mismatch (swarm-003)") }

        // Same regression — `closest-side` variant.
        let circCs = ClipExtractor.extract(from: props([
            ("ClipPath", obj([
                "type": .string("circle"),
                "r": .string("closest-side"),
            ])),
        ]))
        if case .circle(_, let k, _, _) = circCs?.shape ?? .none,
           k == .closestSide { /* ok */ }
        else { f.append("ClipPath: circle(closest-side) keyword mismatch") }

        // ellipse(closest-side farthest-side) — per-axis keyword
        // dispatch. Each axis must independently land in its own
        // ShapeRadiusKind variant. This catches a copy-paste regression
        // where rx + ry might share a single decoded kind.
        let ellKw = ClipExtractor.extract(from: props([
            ("ClipPath", obj([
                "type": .string("ellipse"),
                "rx": .string("closest-side"),
                "ry": .string("farthest-side"),
            ])),
        ]))
        if case .ellipse(_, _, let rxk, let ryk, _, _) = ellKw?.shape ?? .none,
           rxk == .closestSide, ryk == .farthestSide { /* ok */ }
        else { f.append("ClipPath: ellipse(closest-side farthest-side) per-axis mismatch") }

        // polygon — legacy raw-number wire form (each axis is a percent).
        let poly = ClipExtractor.extract(from: props([
            ("ClipPath", obj([
                "type": .string("polygon"),
                "points": .array([
                    obj(["x": .double(50), "y": .double(0)]),
                    obj(["x": .double(100), "y": .double(100)]),
                    obj(["x": .double(0), "y": .double(100)]),
                ]),
            ])),
        ]))
        if case .polygon(let pts) = poly?.shape ?? .none, pts.count == 3,
           case .percent = pts[0].x, case .percent = pts[0].y { /* ok */ }
        else { f.append("ClipPath: polygon 3 percent-points mismatch") }

        // polygon with px IRLength coordinates (CSS Shapes 2 §3.1.4
        // <length-percentage> length branch). Regression pin for the
        // swarm-002 root cause: the WPT polygon
        // `polygon(0 0, 100px 0, 100px 30px, 30px 30px, 30px 100px, 0 100px)`
        // used to collapse to a single vertex; now all 6 must survive
        // and each axis must round-trip as `.length`.
        let pxPoly = ClipExtractor.extract(from: props([
            ("ClipPath", obj([
                "type": .string("polygon"),
                "points": .array([
                    obj(["x": obj(["px": .double(0)]),   "y": obj(["px": .double(0)])]),
                    obj(["x": obj(["px": .double(100)]), "y": obj(["px": .double(0)])]),
                    obj(["x": obj(["px": .double(100)]), "y": obj(["px": .double(30)])]),
                    obj(["x": obj(["px": .double(30)]),  "y": obj(["px": .double(30)])]),
                    obj(["x": obj(["px": .double(30)]),  "y": obj(["px": .double(100)])]),
                    obj(["x": obj(["px": .double(0)]),   "y": obj(["px": .double(100)])]),
                ]),
            ])),
        ]))
        if case .polygon(let pts) = pxPoly?.shape ?? .none, pts.count == 6,
           case .length(let secondX) = pts[1].x, secondX == 100 { /* ok */ }
        else { f.append("ClipPath: polygon 6 px-length points mismatch (swarm-002 regression)") }

        // path('M 0 0 L 10 10 Z')
        let path = ClipExtractor.extract(from: props([
            ("ClipPath", obj([
                "type": .string("path"),
                "d": .string("M 0 0 L 10 10 Z"),
            ])),
        ]))
        if case .path(let d) = path?.shape ?? .none, d.contains("M 0 0") { /* ok */ }
        else { f.append("ClipPath: path() mismatch") }

        // url(#id)
        let cpUrl = ClipExtractor.extract(from: props([
            ("ClipPath", .string("#clipShape")),
        ]))
        if case .url(let id) = cpUrl?.shape ?? .none, id == "clipShape" { /* ok */ }
        else { f.append("ClipPath: url(#clipShape) mismatch") }

        // ClipRule NONZERO / EVENODD
        let cr1 = ClipExtractor.extract(from: props([
            ("ClipRule", .string("EVENODD")),
        ]))
        if cr1?.rule != ClipRule.evenodd { f.append("ClipRule: EVENODD did not map") }

        // Legacy Clip auto / rect.
        let clAuto = ClipExtractor.extract(from: props([
            ("Clip", obj(["type": .string("auto")])),
        ]))
        if case .auto = clAuto?.legacy ?? .rect(top: 0, right: 0, bottom: 0, left: 0) { /* ok */ }
        else { f.append("Clip: auto legacy mismatch") }

        let clRect = ClipExtractor.extract(from: props([
            ("Clip", obj([
                "type": .string("rect"),
                "top": obj(["px": .double(10)]),
                "right": obj(["px": .double(150)]),
                "bottom": obj(["px": .double(110)]),
                "left": obj(["px": .double(10)]),
            ])),
        ]))
        if case .rect(let t, _, _, _) = clRect?.legacy ?? .auto, t == 10 { /* ok */ }
        else { f.append("Clip: rect legacy mismatch") }

        // PartialAuto — IR omits keys for `auto` axes; the extractor
        // surfaces them as `nil` so the LegacyClipRect resolves them at
        // draw time.
        let clPartial = ClipExtractor.extract(from: props([
            ("Clip", obj([
                "type": .string("rect"),
                "right": obj(["px": .double(100)]),
                "bottom": obj(["px": .double(200)]),
                "left": obj(["px": .double(0)]),
            ])),
        ]))
        if case .rect(let topAuto, _, _, _) = clPartial?.legacy ?? .auto, topAuto == nil { /* ok */ }
        else { f.append("Clip: partial-auto legacy mismatch") }

        // ── Visibility / Overflow ───────────────────────────────────
        let vis = VisibilityExtractor.extract(from: props([
            ("Visibility", .string("HIDDEN")),
        ]))
        if vis?.visibility != VisibilityKind.hidden {
            f.append("Visibility: HIDDEN did not map")
        }
        let visC = VisibilityExtractor.extract(from: props([
            ("Visibility", .string("COLLAPSE")),
        ]))
        if visC?.visibility != VisibilityKind.collapse {
            f.append("Visibility: COLLAPSE did not map")
        }
        // overflow-x/y axis keywords.
        let kws: [(String, OverflowKind)] = [
            ("VISIBLE", .visible), ("HIDDEN", .hidden), ("CLIP", .clip),
            ("SCROLL", .scroll), ("AUTO", .auto),
        ]
        for (s, exp) in kws {
            let r = VisibilityExtractor.extract(from: props([
                ("OverflowX", .string(s)),
            ]))
            if r?.overflowX != exp { f.append("OverflowX: \(s) mismatch") }
        }
        // logical block/inline → physical.
        let log = VisibilityExtractor.extract(from: props([
            ("OverflowBlock", .string("HIDDEN")),
            ("OverflowInline", .string("AUTO")),
        ]))
        if log?.overflowY != OverflowKind.hidden || log?.overflowX != OverflowKind.auto {
            f.append("Overflow: logical block/inline → physical failed")
        }

        // ── Filter ──────────────────────────────────────────────────
        // blur(4px)
        let blur = FilterExtractor.extract(from: props([
            ("Filter", .array([
                obj(["fn": .string("blur"), "r": obj(["px": .double(4)])]),
            ])),
        ]))
        if case .blur(let r) = blur?.filter.first ?? .url(id: ""), r == 4 { /* ok */ }
        else { f.append("Filter: blur(4px) mismatch") }

        // brightness(150)
        let br = FilterExtractor.extract(from: props([
            ("Filter", .array([
                obj(["fn": .string("brightness"), "v": .double(150)]),
            ])),
        ]))
        if case .brightness(let p) = br?.filter.first ?? .url(id: ""), p == 150 { /* ok */ }
        else { f.append("Filter: brightness(150) mismatch") }

        // chain ordering — blur then brightness preserves order.
        let chain = FilterExtractor.extract(from: props([
            ("Filter", .array([
                obj(["fn": .string("blur"), "r": obj(["px": .double(2)])]),
                obj(["fn": .string("brightness"), "v": .double(120)]),
                obj(["fn": .string("contrast"), "v": .double(80)]),
            ])),
        ]))
        if chain?.filter.count != 3 { f.append("Filter: 3-chain count") }
        if case .blur = chain?.filter.first ?? .url(id: "") { /* ok */ }
        else { f.append("Filter: chain order — blur not first") }

        // drop-shadow(2px 2px 4px #0008)
        let ds = FilterExtractor.extract(from: props([
            ("Filter", .array([
                obj([
                    "fn": .string("drop-shadow"),
                    "x": obj(["px": .double(2)]),
                    "y": obj(["px": .double(2)]),
                    "r": obj(["px": .double(4)]),
                    "c": obj(["srgb": obj(["r": .double(0), "g": .double(0),
                                             "b": .double(0), "a": .double(0.5)])]),
                ]),
            ])),
        ]))
        if case .dropShadow(let x, _, let r, _) = ds?.filter.first ?? .url(id: ""),
           x == 2, r == 4 { /* ok */ }
        else { f.append("Filter: drop-shadow mismatch") }

        // BackdropFilter presence.
        let bd = FilterExtractor.extract(from: props([
            ("BackdropFilter", .array([
                obj(["fn": .string("blur"), "r": obj(["px": .double(4)])]),
            ])),
        ]))
        if bd?.backdrop.count != 1 { f.append("BackdropFilter: blur not captured") }

        // url(#id) form.
        let furl = FilterExtractor.extract(from: props([
            ("Filter", obj(["url": .string("#mono")])),
        ]))
        if case .url(let id) = furl?.filter.first ?? .blur(radius: 0), id == "#mono" { /* ok */ }
        else { f.append("Filter: url(#mono) mismatch") }

        // ── Mask ────────────────────────────────────────────────────
        // linear-gradient.
        let mimg = MaskExtractor.extract(from: props([
            ("MaskImage", .array([
                obj([
                    "type": .string("linear-gradient"),
                    "angle": obj(["deg": .double(45)]),
                    "stops": .array([
                        obj(["color": obj(["srgb": obj(["r": .double(0),
                                                            "g": .double(0),
                                                            "b": .double(0)])])]),
                        obj(["color": obj(["srgb": obj(["r": .double(0),
                                                            "g": .double(0),
                                                            "b": .double(0),
                                                            "a": .double(0)])])]),
                    ]),
                ]),
            ])),
        ]))
        if case .linearGradient(let deg, _) = mimg?.images.first ?? .none, deg == 45 { /* ok */ }
        else { f.append("MaskImage: linear-gradient(45deg) mismatch") }

        // url form.
        let murl = MaskExtractor.extract(from: props([
            ("MaskImage", .array([.string("mask.png")])),
        ]))
        if case .url = murl?.images.first ?? .none { /* ok */ }
        else { f.append("MaskImage: url mismatch") }

        // mode luminance.
        let mm = MaskExtractor.extract(from: props([
            ("MaskMode", obj(["type": .string("app.irmodels.properties.effects.MaskModeValue.Luminance")])),
        ]))
        if mm?.mode != MaskMode.luminance { f.append("MaskMode: LUMINANCE did not map") }

        // origin / clip.
        let mo = MaskExtractor.extract(from: props([
            ("MaskOrigin", .string("BORDER_BOX")),
            ("MaskClip",   .string("CONTENT_BOX")),
        ]))
        if mo?.origin != .borderBox || mo?.clip != .contentBox {
            f.append("Mask origin/clip keywords mismatch")
        }

        // composite.
        let mc = MaskExtractor.extract(from: props([
            ("MaskComposite", obj(["type": .string("app.irmodels.properties.effects.MaskCompositeValue.Subtract")])),
        ]))
        if mc?.composite != MaskComposite.subtract {
            f.append("MaskComposite: Subtract did not map")
        }

        // border mode.
        let mb = MaskExtractor.extract(from: props([
            ("MaskBorderMode", .string("ALPHA")),
        ]))
        if mb?.borderMode != MaskType.alpha { f.append("MaskBorderMode: ALPHA mismatch") }

        // Absent → nil.
        if MaskExtractor.extract(from: props([])) != nil {
            f.append("Mask: absent should yield nil aggregate")
        }

        // ── Report ──────────────────────────────────────────────────
        // One XCTFail per collected failure name (was: print PASS/FAIL).
        for failure in f { XCTFail(failure) }
    }
}
