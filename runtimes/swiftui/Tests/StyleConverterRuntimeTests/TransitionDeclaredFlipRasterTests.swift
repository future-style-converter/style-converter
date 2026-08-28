//
//  TransitionDeclaredFlipRasterTests.swift
//  StyleConverterRuntimeTests — the closest a test can get to the flip.
//
//  The live TRIGGER (.onChange → beginTransition → transitionSnapshot) is
//  unreachable from XCTest: ImageRenderer is one synchronous layout+draw
//  over a fresh view graph, so @State re-initialises and .onChange never
//  fires — the same reason capture cannot observe a flip on iOS
//  (docs/DYNAMIC_CAPTURE.md §4). What IS reachable through a real render
//  is the branch NEXT to it: ComponentRenderer.motionEffectiveProperties'
//  DECLARED flip, which under a pinned clock + a forced state computes the
//  pre-flip list itself and blends at t.
//
//  So this file pins the branch through the public View, not through the
//  pure resolver (TransitionFlipDetectionTests does that): the environment
//  plumbing, the fold, the blend and StyleBuilder all in one pass, ending
//  in a pixel. It is deliberately a CONTROLLED set of four renders that
//  differ only in the two environment knobs — the reason being the trap
//  RatioInlineSizeTests records, where a raster that renders the same
//  thing on both arms reads as a pass while proving nothing:
//
//    forced ∅  + clock ∅    → base     #7f8c8d   (the whole static corpus)
//    forced ∅  + clock 0.4  → base     #7f8c8d   (a seized run stays inert)
//    forced hover + clock ∅ → endpoint #c0392b   (settled forced appearance,
//                                                 what interaction-states.mjs
//                                                 captures — must NOT move)
//    forced hover + clock 0.4 → blend  (153,107,102)  ← the thing under test
//
//  Wire shapes are MT_BgFade's, live-converter verbatim (see the Android
//  twin in runtimes/compose/src/test/.../TransitionDriverTest.kt).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class TransitionDeclaredFlipRasterTests: XCTestCase {

    // MARK: - Fixture

    /// MT_BgFade: a 140×48 box, `background-color: #7f8c8d`,
    /// `transition: background-color 1s linear`, `:hover { #c0392b }`.
    /// Named "B" so the leaf's synthesized name placeholder — which hugs
    /// TOP-LEADING — stays far from the probes below.
    private func bgFade() throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"mt-bgfade","name":"B",
         "properties":[
           {"type":"Width","data":{"type":"length","px":140.0}},
           {"type":"Height","data":{"type":"length","px":48.0}},
           {"type":"BackgroundColor","data":{"srgb":{"r":0.4980392156862745,
                                                     "g":0.5490196078431373,
                                                     "b":0.5529411764705883},
                                             "original":"#7f8c8d"}},
           {"type":"TransitionProperty","data":[{"type":"property-name","name":"background-color"}]},
           {"type":"TransitionDuration","data":[{"ms":1000.0,"original":{"v":1.0,"u":"S"}}]},
           {"type":"TransitionTimingFunction","data":[{"cb":[0.0,0.0,1.0,1.0]}]}
         ],
         "selectors":[
           {"condition":"hover",
            "properties":[
              {"type":"BackgroundColor","data":{"srgb":{"r":0.7529411764705882,
                                                        "g":0.2235294117647059,
                                                        "b":0.16862745098039217},
                                                "original":"#c0392b"}}
            ]}
         ]}
        """.utf8))
    }

    // MARK: - Raster plumbing (WPTBlockChildFillTests recipe, verbatim)

    /// Render with the two capture knobs and read one pixel. Scale 1 =
    /// one buffer pixel per logical point, like the harness; top-leading
    /// puts the component's border box at raster (0,0).
    @MainActor
    private func pixel(_ comp: IRComponent, forced: Set<String>, clock: Double?,
                       x: Int, y: Int) throws -> (r: Int, g: Int, b: Int) {
        let view = ComponentRenderer(component: comp)
            // Exactly the two channels the capture app publishes:
            // -forceState (spec 06 §6) and -animationTime (spec 07 §5).
            .environment(\.forcedStyleStates, forced)
            .environment(\.animationCaptureTime, clock)
            .frame(width: 390, height: 100, alignment: .topLeading)
            .background(Color.white)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        // Redraw into a known RGBA8 layout for format-stable byte reads.
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        let i = (y * w + x) * 4
        return (Int(buf[i]), Int(buf[i + 1]), Int(buf[i + 2]))
    }

    /// Colour match with a ±12 tolerance. Tighter than this suite's usual
    /// ±24 on purpose: the three expected colours are only 26 apart on
    /// their closest channel (base r=127 vs blend r=153), so ±24 bands
    /// would nearly touch and a "pass" would stop meaning anything.
    private func matches(_ c: (r: Int, g: Int, b: Int), _ t: (r: Int, g: Int, b: Int)) -> Bool {
        abs(c.r - t.r) <= 12 && abs(c.g - t.g) <= 12 && abs(c.b - t.b) <= 12
    }

    // The three appearances, in capture-report bytes.
    private let base = (r: 127, g: 140, b: 141)      // #7f8c8d
    private let endpoint = (r: 192, g: 57, b: 43)    // #c0392b
    private let blended = (r: 153, g: 107, b: 102)   // spec lerp at t=0.4

    // MARK: - The pin

    /// Four renders, two knobs. Only the both-knobs arm may show the
    /// blend; the other three must show the appearance they showed before
    /// the declared flip existed, which is what keeps the 327 committed
    /// baselines and the settled forced-state captures inert.
    @MainActor
    func testDeclaredFlipPaintsTheBlendAndOnlyWhenBothKnobsAreSet() throws {
        let comp = try bgFade()
        // Bottom-right of the 140×48 box: inside the fill, clear of the
        // top-leading placeholder glyphs and of every box edge.
        let (px, py) = (130, 42)

        // 1. Product path — no knobs. The base colour.
        let plain = try pixel(comp, forced: [], clock: nil, x: px, y: py)
        XCTAssertTrue(matches(plain, base),
                      "unforced, unseized must render the base colour; got \(plain)")

        // 2. Seized but unforced — the whole static corpus under
        //    CAPTURE_ANIMATION_TIME. No forced set ⇒ no declared origin ⇒
        //    no blend, so this arm proves the branch is not firing blind.
        let seized = try pixel(comp, forced: [], clock: 0.4, x: px, y: py)
        XCTAssertTrue(matches(seized, base),
                      "a pinned clock alone must not start a transition; got \(seized)")

        // 3. Forced but unseized — interaction-states.mjs' settled
        //    appearance. Must be the ENDPOINT, unchanged by this lane.
        let settled = try pixel(comp, forced: ["hover"], clock: nil, x: px, y: py)
        XCTAssertTrue(matches(settled, endpoint),
                      "a forced run with no clock must still show the settled forced "
                      + "appearance; got \(settled)")

        // 4. Both knobs — the declared flip. The spec lerp at t=0.4, and
        //    specifically NOT the endpoint, which is what every t rendered
        //    before the flip landed (docs/STATUS.md, 2026-08-28).
        let midFlight = try pixel(comp, forced: ["hover"], clock: 0.4, x: px, y: py)
        XCTAssertTrue(matches(midFlight, blended),
                      "declared flip at t=0.4 must paint (153,107,102); got \(midFlight)")
        XCTAssertFalse(matches(midFlight, endpoint),
                       "painting the endpoint at t=0.4 is the pre-fix behaviour")
        XCTAssertFalse(matches(midFlight, base),
                       "painting the base at t=0.4 means the forced fold never applied")
    }

    /// The blend must reach the PAINTED box, not just the property list:
    /// a second probe well inside the fill, and a probe outside the
    /// declared 140×48 border box that must stay canvas white — the blend
    /// rewrites a colour, never the geometry.
    @MainActor
    func testBlendPaintsTheWholeDeclaredBoxAndNothingBeyondIt() throws {
        let comp = try bgFade()
        let inside = try pixel(comp, forced: ["hover"], clock: 0.4, x: 60, y: 40)
        XCTAssertTrue(matches(inside, blended),
                      "the blend must fill the box, not just its corner; got \(inside)")
        // x = 200 is past the 140 px width — canvas, not component.
        let outside = try pixel(comp, forced: ["hover"], clock: 0.4, x: 200, y: 40)
        XCTAssertTrue(matches(outside, (r: 255, g: 255, b: 255)),
                      "the transition must not resize the box; got \(outside)")
    }
}
