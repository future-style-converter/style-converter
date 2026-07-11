//
//  MotionTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 8 (#35) — animation runtime v1 pins:
//    • AnimationDriver: phase/direction/fill/iteration arithmetic and
//      the cubic-bezier / steps / linear easing evaluators (spec 07 §3)
//    • KeyframeInterpolator: per-tier interpolation (numbers linear,
//      lengths px, colors sRGB, matched transform lists), base-fill
//      endpoints, non-tier step-apply + log (spec 07 §2)
//    • AnimationResolver: list matching, dangling-name defined no-op
//      (spec 07 §1.3), pinned-vs-live paused composition (§5)
//    • TransitionResolver: flip blend, delay hold, settle detection,
//      transition-property coverage (spec 07 §4)
//    • IR v2 wire decode of the `keyframes` envelope key (spec 07 §1.2)
//

import Foundation
import XCTest
@testable import StyleConverterRuntime

final class MotionTests: XCTestCase {

    // Shared literal helpers.
    private func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
    private func prop(_ t: String, _ d: IRValue) -> IRProperty { IRProperty(type: t, data: d) }
    private func opacity(_ a: Double) -> IRValue { obj(["alpha": .double(a)]) }
    private func lengthPx(_ v: Double) -> IRValue { obj(["type": .string("length"), "px": .double(v)]) }
    private func srgb(_ r: Double, _ g: Double, _ b: Double) -> IRValue {
        obj(["srgb": obj(["r": .double(r), "g": .double(g), "b": .double(b)])])
    }
    private func translateX(_ px: Double) -> IRValue {
        obj(["type": .string("functions"),
             "list": .array([obj(["fn": .string("translateX"), "x": obj(["px": .double(px)])])])])
    }

    /// Linear 1s timing — the workhorse tuple.
    private func linear1s(delay: Double = 0, iterations: Double? = 1,
                          direction: AnimationDirectionKind = .normal,
                          fill: AnimationFillModeKind = .none) -> AnimationDriver.Timing {
        var t = AnimationDriver.Timing()
        t.durationMs = 1000; t.delayMs = delay
        t.iterations = iterations; t.direction = direction; t.fillMode = fill
        t.timingFunction = .cubicBezier(x1: 0, y1: 0, x2: 1, y2: 1) // linear
        return t
    }

    // MARK: - Driver: phases + fill (spec 07 §3)

    func testDriverActivePhaseLinear() {
        XCTAssertEqual(AnimationDriver.progress(atSeconds: 0.25, timing: linear1s())!, 0.25, accuracy: 1e-9)
        XCTAssertEqual(AnimationDriver.progress(atSeconds: 0.0, timing: linear1s())!, 0.0, accuracy: 1e-9)
    }

    func testDriverDelayAndBackwardsFill() {
        // During the delay, no fill → nothing applies (base styles).
        XCTAssertNil(AnimationDriver.progress(atSeconds: 0.2, timing: linear1s(delay: 500)))
        // backwards/both fill paints the first frame (MK_FillBoth's pin:
        // t=0 with delay 0.5s + fill both IS the from-state).
        XCTAssertEqual(AnimationDriver.progress(atSeconds: 0.2, timing: linear1s(delay: 500, fill: .both))!, 0.0, accuracy: 1e-9)
        // reverse direction fills the ITERATION START = progress 1.
        XCTAssertEqual(AnimationDriver.progress(atSeconds: 0.0, timing: linear1s(delay: 500, iterations: 1, direction: .reverse, fill: .backwards))!, 1.0, accuracy: 1e-9)
    }

    func testDriverNegativeDelayAdvances() {
        // -300ms delay: at t=0 the animation is already 30% in (§5.5).
        XCTAssertEqual(AnimationDriver.progress(atSeconds: 0.0, timing: linear1s(delay: -300))!, 0.3, accuracy: 1e-9)
    }

    func testDriverAfterPhaseAndForwardsFill() {
        // Ended without forwards fill → nothing applies.
        XCTAssertNil(AnimationDriver.progress(atSeconds: 2.0, timing: linear1s()))
        // forwards/both freeze the end frame.
        XCTAssertEqual(AnimationDriver.progress(atSeconds: 2.0, timing: linear1s(fill: .forwards))!, 1.0, accuracy: 1e-9)
        // Zero-duration animations complete instantly; fill still applies.
        var zero = linear1s(fill: .both); zero.durationMs = 0
        XCTAssertEqual(AnimationDriver.progress(atSeconds: 0.0, timing: zero)!, 1.0, accuracy: 1e-9)
    }

    func testDriverDirectionArithmetic() {
        // alternate, 2 iterations (MK_Alternate): second iteration is
        // reversed — t=1.5s is iteration 1 at raw 0.5 → directed 0.5;
        // t=1.75 → raw 0.75 → directed 0.25.
        XCTAssertEqual(AnimationDriver.progress(atSeconds: 1.75, timing: linear1s(iterations: 2, direction: .alternate))!, 0.25, accuracy: 1e-9)
        // At the end (fill both) alternate×2 lands back at 0.
        XCTAssertEqual(AnimationDriver.progress(atSeconds: 5.0, timing: linear1s(iterations: 2, direction: .alternate, fill: .both))!, 0.0, accuracy: 1e-9)
        // reverse runs backwards throughout.
        XCTAssertEqual(AnimationDriver.progress(atSeconds: 0.25, timing: linear1s(direction: .reverse))!, 0.75, accuracy: 1e-9)
        // alternate-reverse: iteration 0 is the reversed one.
        XCTAssertEqual(AnimationDriver.progress(atSeconds: 0.25, timing: linear1s(direction: .alternateReverse))!, 0.75, accuracy: 1e-9)
    }

    func testDriverFractionalIterations() {
        // 2.5 iterations end mid third iteration (index 2, even → not
        // flipped for alternate) at progress 0.5 (§5.4).
        XCTAssertEqual(AnimationDriver.progress(atSeconds: 10, timing: linear1s(iterations: 2.5, direction: .alternate, fill: .forwards))!, 0.5, accuracy: 1e-9)
        // Infinite iterations never reach the after phase.
        XCTAssertEqual(AnimationDriver.progress(atSeconds: 1234.25, timing: linear1s(iterations: nil))!, 0.25, accuracy: 1e-9)
    }

    // MARK: - Driver: easing evaluators

    func testBezierEvaluation() {
        // Diagonal control points = identity.
        XCTAssertEqual(AnimationDriver.bezier(0.37, x1: 0, y1: 0, x2: 1, y2: 1), 0.37, accuracy: 1e-6)
        // The `ease` keyword curve at midpoint — browser-verified 0.8024.
        XCTAssertEqual(AnimationDriver.bezier(0.5, x1: 0.25, y1: 0.1, x2: 0.25, y2: 1.0), 0.8024, accuracy: 0.001)
        // Endpoints exact.
        XCTAssertEqual(AnimationDriver.bezier(0, x1: 0.42, y1: 0, x2: 1, y2: 1), 0)
        XCTAssertEqual(AnimationDriver.bezier(1, x1: 0.42, y1: 0, x2: 1, y2: 1), 1)
        // ease-in at 0.5 — browser-verified 0.3153.
        XCTAssertEqual(AnimationDriver.bezier(0.5, x1: 0.42, y1: 0, x2: 1, y2: 1), 0.3153, accuracy: 0.001)
    }

    func testStepsEvaluation() {
        // steps(4, end): rises at interval ends.
        XCTAssertEqual(AnimationDriver.step(0.0, count: 4, position: .end), 0.0)
        XCTAssertEqual(AnimationDriver.step(0.5, count: 4, position: .end), 0.5)
        XCTAssertEqual(AnimationDriver.step(0.6, count: 4, position: .end), 0.5)
        XCTAssertEqual(AnimationDriver.step(1.0, count: 4, position: .end), 1.0)
        // steps(4, start): first rise at 0+.
        XCTAssertEqual(AnimationDriver.step(0.1, count: 4, position: .start), 0.25)
        // jump-both: n+1 landing values.
        XCTAssertEqual(AnimationDriver.step(0.0, count: 2, position: .jumpBoth), 1.0 / 3.0, accuracy: 1e-9)
        // jump-none: holds 1 only at the end, n−1 rises.
        XCTAssertEqual(AnimationDriver.step(0.5, count: 2, position: .jumpNone), 1.0, accuracy: 1e-9)
        XCTAssertEqual(AnimationDriver.step(0.4, count: 2, position: .jumpNone), 0.0, accuracy: 1e-9)
    }

    func testLinearStops() {
        // linear(0, 0.5 40%, 1): piecewise through an off-center knee.
        let stops = [LinearStop(value: 0, percent: nil),
                     LinearStop(value: 0.5, percent: 40),
                     LinearStop(value: 1, percent: nil)]
        XCTAssertEqual(AnimationDriver.linear(0.2, stops: stops), 0.25, accuracy: 1e-9)
        XCTAssertEqual(AnimationDriver.linear(0.7, stops: stops), 0.75, accuracy: 1e-9)
    }

    // MARK: - Interpolator: tier lerp (spec 07 §2)

    /// Identity segment easing for value-focused pins (nil would mean
    /// the `ease` keyword default, css-easing-1 §2.3).
    private let linearFn = AnimationTimingFn.cubicBezier(x1: 0, y1: 0, x2: 1, y2: 1)

    func testOpacityLerpAndOverlay() {
        let stops = [IRKeyframeStop(offset: 0, properties: [prop("Opacity", opacity(0))]),
                     IRKeyframeStop(offset: 1, properties: [prop("Opacity", opacity(1))])]
        let base = [prop("Opacity", opacity(0.9)), prop("Width", lengthPx(120))]
        let out = KeyframeInterpolator.apply(stops: stops, progress: 0.5, timing: linearFn, to: base, componentName: "t")
        // Overlay replaced the base Opacity in place (no duplicates).
        XCTAssertEqual(out.filter { $0.type == "Opacity" }.count, 1)
        XCTAssertEqual(out.first { $0.type == "Opacity" }?.data["alpha"]?.doubleValue ?? -1, 0.5, accuracy: 1e-9)
        // Unrelated base declarations survive untouched.
        XCTAssertEqual(out.first { $0.type == "Width" }?.data["px"]?.doubleValue, 120)
    }

    func testMissingEndpointFillsFromBase() {
        // Width declared ONLY at 50% (the golden's slide-shift shape):
        // 0%/100% use the base declaration (css-animations-1 §4.2).
        let stops = [IRKeyframeStop(offset: 0.5, properties: [prop("Width", lengthPx(160))])]
        let base = [prop("Width", lengthPx(40))]
        let quarter = KeyframeInterpolator.apply(stops: stops, progress: 0.25, timing: linearFn, to: base, componentName: "t")
        XCTAssertEqual(quarter.first { $0.type == "Width" }?.data["px"]?.doubleValue ?? -1, 100, accuracy: 1e-9)
        let end = KeyframeInterpolator.apply(stops: stops, progress: 1.0, timing: linearFn, to: base, componentName: "t")
        XCTAssertEqual(end.first { $0.type == "Width" }?.data["px"]?.doubleValue ?? -1, 40, accuracy: 1e-9)
    }

    func testColorLerpInSRGB() {
        let stops = [IRKeyframeStop(offset: 0, properties: [prop("BackgroundColor", srgb(1, 0, 0))]),
                     IRKeyframeStop(offset: 1, properties: [prop("BackgroundColor", srgb(0, 0, 1))])]
        let out = KeyframeInterpolator.apply(stops: stops, progress: 0.5, timing: linearFn, to: [], componentName: "t")
        let c = out.first { $0.type == "BackgroundColor" }?.data["srgb"]
        // Component-wise midpoint on the wire's 0–1 floats (spec 02).
        XCTAssertEqual(c?["r"]?.doubleValue ?? -1, 0.5, accuracy: 1e-9)
        XCTAssertEqual(c?["b"]?.doubleValue ?? -1, 0.5, accuracy: 1e-9)
    }

    func testTransformMatchedListLerp() {
        let stops = [IRKeyframeStop(offset: 0, properties: [prop("Transform", translateX(0))]),
                     IRKeyframeStop(offset: 1, properties: [prop("Transform", translateX(120))])]
        let out = KeyframeInterpolator.apply(stops: stops, progress: 0.25, timing: linearFn, to: [], componentName: "t")
        let fn = out.first { $0.type == "Transform" }?.data["list"]?.arrayValue?.first
        XCTAssertEqual(fn?["fn"]?.stringValue, "translateX")
        XCTAssertEqual(fn?["x"]?["px"]?.doubleValue ?? -1, 30, accuracy: 1e-9)
    }

    /// css-animations-1 §4.4 — the timing function eases each keyframe
    /// SEGMENT, not the whole iteration. Browser-verified via MK_Pulse
    /// (scale 1 → 1.6 → 1, default `ease`): at raw progress 0.5 the
    /// value is EXACTLY the middle stop; whole-iteration easing would
    /// misplace it at ≈1.24 (the divergence the t=0.5 capture caught).
    func testEasingIsPerSegmentNotPerIteration() {
        func scale(_ s: Double) -> IRValue {
            obj(["type": .string("functions"),
                 "list": .array([obj(["fn": .string("scale"), "x": .double(s), "y": .double(s)])])])
        }
        let stops = [IRKeyframeStop(offset: 0, properties: [prop("Transform", scale(1))]),
                     IRKeyframeStop(offset: 0.5, properties: [prop("Transform", scale(1.6))]),
                     IRKeyframeStop(offset: 1, properties: [prop("Transform", scale(1))])]
        // nil timing = the `ease` default — still exact at a stop offset.
        let atStop = KeyframeInterpolator.apply(stops: stops, progress: 0.5, timing: nil, to: [], componentName: "t")
        XCTAssertEqual(atStop.first { $0.type == "Transform" }?.data["list"]?.arrayValue?.first?["x"]?.doubleValue ?? -1,
                       1.6, accuracy: 1e-9)
        // Mid-segment with ease: local u = 0.5 inside [0.5, 1] eases to
        // 0.8024 → 1.6 + (1 − 1.6) × 0.8024 ≈ 1.1186.
        let mid = KeyframeInterpolator.apply(stops: stops, progress: 0.75, timing: nil, to: [], componentName: "t")
        XCTAssertEqual(mid.first { $0.type == "Transform" }?.data["list"]?.arrayValue?.first?["x"]?.doubleValue ?? -1,
                       1.6 - 0.6 * 0.8024, accuracy: 0.002)
    }

    func testMismatchedTransformStepsDiscretely() {
        PropertyTracker._resetForTests()
        let rotate = obj(["type": .string("functions"),
                          "list": .array([obj(["fn": .string("rotate"), "a": obj(["deg": .double(45)])])])])
        let stops = [IRKeyframeStop(offset: 0, properties: [prop("Transform", translateX(0))]),
                     IRKeyframeStop(offset: 1, properties: [prop("Transform", rotate)])]
        // Below the boundary the previous stop holds…
        let mid = KeyframeInterpolator.apply(stops: stops, progress: 0.6, to: [], componentName: "t")
        XCTAssertEqual(mid.first { $0.type == "Transform" }?.data["list"]?.arrayValue?.first?["fn"]?.stringValue, "translateX")
        // …and the discrete fallback left a breadcrumb (spec 07 §2).
        XCTAssertFalse(PropertyTracker.logOnce(key: "keyframe-discrete:Transform", message: "dup"))
        // At the boundary the new value applies.
        let end = KeyframeInterpolator.apply(stops: stops, progress: 1.0, to: [], componentName: "t")
        XCTAssertEqual(end.first { $0.type == "Transform" }?.data["list"]?.arrayValue?.first?["fn"]?.stringValue, "rotate")
    }

    func testNonTierStepAppliesWithLog() {
        PropertyTracker._resetForTests()
        let stops = [IRKeyframeStop(offset: 0, properties: [prop("BorderTopWidth", lengthPx(1))]),
                     IRKeyframeStop(offset: 1, properties: [prop("BorderTopWidth", lengthPx(9))])]
        let out = KeyframeInterpolator.apply(stops: stops, progress: 0.5, to: [], componentName: "t")
        // Carried, not dropped; stepped, not interpolated.
        XCTAssertEqual(out.first { $0.type == "BorderTopWidth" }?.data["px"]?.doubleValue, 1)
        XCTAssertFalse(PropertyTracker.logOnce(key: "keyframe-nontier:BorderTopWidth", message: "dup"))
    }

    // MARK: - Resolver: glue (spec 07 §1.3 / §5)

    /// MK_Fade-shaped component: fade 1s linear, fill both.
    private func fadeProps(name: String = "fade") -> [IRProperty] {
        [prop("Opacity", opacity(0.9)),
         prop("AnimationName", .array([obj(["type": .string("identifier"), "name": .string(name)])])),
         prop("AnimationDuration", obj(["durations": .array([obj(["ms": .double(1000)])])])),
         prop("AnimationTimingFunction", .array([obj(["cb": .array([.double(0), .double(0), .double(1), .double(1)])])])),
         prop("AnimationFillMode", .array([.string("BOTH")]))]
    }
    private var fadeSet: [String: [IRKeyframeStop]] {
        ["fade": [IRKeyframeStop(offset: 0, properties: [prop("Opacity", opacity(0))]),
                  IRKeyframeStop(offset: 1, properties: [prop("Opacity", opacity(1))])]]
    }

    func testResolverAppliesAtPinnedMidpoint() {
        let out = AnimationResolver.resolve(properties: fadeProps(), keyframes: fadeSet,
                                            atSeconds: 0.5, pinnedClock: true, componentName: "MK")
        XCTAssertEqual(out.first { $0.type == "Opacity" }?.data["alpha"]?.doubleValue ?? -1, 0.5, accuracy: 1e-6)
    }

    func testResolverDanglingNameIsDefinedNoOp() {
        PropertyTracker._resetForTests()
        let out = AnimationResolver.resolve(properties: fadeProps(name: "ghost-pulse"),
                                            keyframes: fadeSet,
                                            atSeconds: 0.5, pinnedClock: true, componentName: "GhostBox")
        // Base styles untouched — never an error, never a drop (§1.3).
        XCTAssertEqual(out.first { $0.type == "Opacity" }?.data["alpha"]?.doubleValue, 0.9)
        XCTAssertFalse(PropertyTracker.logOnce(key: "keyframes-dangling:ghost-pulse", message: "dup"))
    }

    func testResolverPausedComposition() {
        var props = fadeProps()
        props.append(prop("AnimationPlayState", .array([.string("PAUSED")])))
        // LIVE paused clock freezes at the initial frame (fill both → 0).
        let live = AnimationResolver.resolve(properties: props, keyframes: fadeSet,
                                             atSeconds: 0.7, pinnedClock: false, componentName: "MK")
        XCTAssertEqual(live.first { $0.type == "Opacity" }?.data["alpha"]?.doubleValue ?? -1, 0.0, accuracy: 1e-9)
        // The PINNED capture clock wins over paused (spec 07 §5).
        let pinned = AnimationResolver.resolve(properties: props, keyframes: fadeSet,
                                               atSeconds: 0.7, pinnedClock: true, componentName: "MK")
        XCTAssertEqual(pinned.first { $0.type == "Opacity" }?.data["alpha"]?.doubleValue ?? -1, 0.7, accuracy: 1e-6)
    }

    func testTimingListMatchingRepeatsShortLists() {
        var cfg = AnimationsConfig()
        cfg.duration = [TimeValue(milliseconds: 1000), TimeValue(milliseconds: 2000)]
        cfg.delay = [TimeValue(milliseconds: 250)] // repeats for index 1 (§5.2)
        cfg.touched = true
        XCTAssertEqual(AnimationResolver.timingSlice(cfg, index: 1).durationMs, 2000)
        XCTAssertEqual(AnimationResolver.timingSlice(cfg, index: 1).delayMs, 250)
        XCTAssertEqual(AnimationResolver.timingSlice(cfg, index: 2).durationMs, 1000)
        // Initials when lists are absent.
        XCTAssertEqual(AnimationResolver.timingSlice(AnimationsConfig(), index: 0).iterations, 1)
    }

    // MARK: - Transitions (spec 07 §4)

    /// hover-flip pair: opacity 0.3 → 1 under transition 1s linear.
    /// Shapes mirror the extractor's documented IR: TransitionDuration/
    /// -Delay are BARE time arrays [{ms}], property entries are
    /// {type:"all"|"property-name", name?}.
    private var transitionNew: [IRProperty] {
        [prop("Opacity", opacity(1)),
         prop("TransitionProperty", .array([obj(["type": .string("all")])])),
         prop("TransitionDuration", .array([obj(["ms": .double(1000)])])),
         prop("TransitionTimingFunction", .array([obj(["cb": .array([.double(0), .double(0), .double(1), .double(1)])])])),
         prop("TransitionDelay", .array([obj(["ms": .double(200)])]))]
    }

    func testTransitionBlendMidpointAndSettle() {
        let old = [prop("Opacity", opacity(0.3))]
        // Inside the delay the OLD value holds (css-transitions-1 §3).
        let held = TransitionResolver.blend(from: old, to: transitionNew, elapsedSeconds: 0.1)
        XCTAssertEqual(held.properties.first { $0.type == "Opacity" }?.data["alpha"]?.doubleValue, 0.3)
        XCTAssertFalse(held.settled)
        // Midpoint: delay 0.2 + 0.5×1s ⇒ elapsed 0.7 → alpha 0.65.
        let mid = TransitionResolver.blend(from: old, to: transitionNew, elapsedSeconds: 0.7)
        XCTAssertEqual(mid.properties.first { $0.type == "Opacity" }?.data["alpha"]?.doubleValue ?? -1, 0.65, accuracy: 1e-6)
        XCTAssertFalse(mid.settled)
        // Past the window: target value, settled.
        let done = TransitionResolver.blend(from: old, to: transitionNew, elapsedSeconds: 1.5)
        XCTAssertEqual(done.properties.first { $0.type == "Opacity" }?.data["alpha"]?.doubleValue, 1.0)
        XCTAssertTrue(done.settled)
    }

    func testTransitionPropertyCoverage() {
        // Named list: only the named property animates; last entry wins.
        XCTAssertEqual(TransitionResolver.coveringEntry(for: "Opacity", entries: [.propertyName("opacity")]), 0)
        XCTAssertNil(TransitionResolver.coveringEntry(for: "Width", entries: [.propertyName("opacity")]))
        XCTAssertEqual(TransitionResolver.coveringEntry(for: "Width", entries: [.all, .propertyName("width")]), 1)
        // hasTransitions gate: positive duration required.
        XCTAssertTrue(TransitionResolver.hasTransitions(transitionNew))
        XCTAssertFalse(TransitionResolver.hasTransitions([prop("Opacity", opacity(1))]))
    }

    // MARK: - Wire decode (spec 07 §1.2)

    func testV2KeyframesDecode() throws {
        let json = """
        {"irVersion":2,"minReaderVersion":2,
         "components":[{"id":"a","name":"A","properties":[]}],
         "keyframes":{"fade":[
            {"offset":0.0,"properties":[{"type":"Opacity","data":{"alpha":0.0}}]},
            {"offset":0.5,"properties":[{"type":"Width","data":{"type":"length","px":140.0}}]},
            {"offset":1.0,"properties":[{"type":"Opacity","data":{"alpha":1.0}}]}]}}
        """
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data(json.utf8))
        let stops = try XCTUnwrap(doc.keyframes?["fade"])
        XCTAssertEqual(stops.map(\.offset), [0.0, 0.5, 1.0])
        XCTAssertEqual(stops[1].properties.first?.type, "Width")
        // Keyframe-free documents stay nil (absent ≠ empty).
        let bare = try JSONDecoder().decode(IRDocument.self, from: Data(
            #"{"irVersion":2,"minReaderVersion":2,"components":[{"id":"a","name":"A","properties":[]}]}"#.utf8))
        XCTAssertNil(bare.keyframes)
    }

    func testV2KeyframesStrictness() {
        // Empty set: schema minItems 1.
        XCTAssertThrowsError(try JSONDecoder().decode(IRDocument.self, from: Data(
            #"{"irVersion":2,"minReaderVersion":2,"components":[],"keyframes":{"x":[]}}"#.utf8)))
        // Offset out of range: schema maximum 1.
        XCTAssertThrowsError(try JSONDecoder().decode(IRDocument.self, from: Data(
            #"{"irVersion":2,"minReaderVersion":2,"components":[],"keyframes":{"x":[{"offset":1.5,"properties":[]}]}}"#.utf8)))
        // Unknown stop key: additionalProperties false.
        XCTAssertThrowsError(try JSONDecoder().decode(IRDocument.self, from: Data(
            #"{"irVersion":2,"minReaderVersion":2,"components":[],"keyframes":{"x":[{"offset":0,"properties":[],"extra":1}]}}"#.utf8)))
    }
}
