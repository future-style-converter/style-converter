//
//  TransitionFlipDetectionTests.swift
//  StyleConverterRuntimeTests — the transition flip-DETECTION path.
//
//  WHAT THIS FILE CAN AND CANNOT PIN, up front, so a green run is never
//  read as more than it is.
//
//  The live trigger is one line of ComponentRenderer.body:
//      .onChange(of: componentState) { beginTransition(to: $0) }
//  `beginTransition`, `transitionSnapshot`, `lastInteractionState` and
//  `hasMotion` are all `private` members of a SwiftUI View struct, so
//  `@testable import` cannot reach them; and their @State writes only
//  happen inside a real SwiftUI update pass. The only render this suite
//  has is ImageRenderer — ONE synchronous layout+draw over a freshly
//  built graph — so `.onChange` can no more fire in a test than it can
//  during capture (docs/DYNAMIC_CAPTURE.md §4, iOS row). The WIRING is
//  therefore not testable here; see the lane report for the extraction
//  that would make it so.
//
//  What IS reachable is every piece of DECISION CONTENT the handler
//  composes, each of which is pure:
//    • the trigger PREDICATE — `.onChange` fires iff ComponentState's
//      Equatable says the state changed, so every selector condition the
//      resolver reacts to must be visible in that equality (and the
//      media inputs it also reacts to are not — the canary below);
//    • the FROM-SIDE — `effective(for: oldState)`, the pure StateResolver
//      fold re-run with the pre-flip state (ComponentRenderer :215/:495);
//    • the GATE — hasTransitions of the POST-flip list (:500);
//    • the REVERSAL — a blend output re-fed as the next leg's from-side
//      (:506-513), the one composition unique to the detection path.
//
//  Wire shapes are the live converter's, verbatim from the Android twin
//  (runtimes/compose/src/test/.../TransitionDriverTest.kt) over
//  fixtures/fidelity/motion/transitions.json's MT_BgFade.
//

import Foundation
import XCTest
@testable import StyleConverterRuntime

final class TransitionFlipDetectionTests: XCTestCase {

    // MARK: - Fixture (MT_BgFade, live wire shapes)

    /// Decode declarations from inline JSON — decode is the production
    /// path (same convention as DynamicStylingTests / MotionTests).
    private func props(_ json: String) -> [IRProperty] {
        try! JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// #7f8c8d and #c0392b as BYTES; the wire carries them as 0–1 sRGB
    /// floats (spec 02 §2) but the capture report quotes bytes, so every
    /// expectation below is written in the report's units and divided
    /// only when building JSON.
    private let baseRGB = (r: 127.0, g: 140.0, b: 141.0)
    private let hoverRGB = (r: 192.0, g: 57.0, b: 43.0)

    /// One BackgroundColor declaration in converter shape: normalized
    /// `srgb` floats plus the authored `original` text the blend strips.
    private func bgJSON(_ c: (r: Double, g: Double, b: Double), _ hex: String) -> String {
        // Raw literal so the JSON quotes stay readable (MotionTests style);
        // `original` is carried because the real wire carries it and the
        // blend has to strip it (KeyframeInterpolator.lerp: authored text
        // has no midpoint) — dropping it here would hide that step.
        #"{"type":"BackgroundColor","data":{"srgb":{"r":\#(c.r / 255),"g":\#(c.g / 255),"b":\#(c.b / 255)},"original":"\#(hex)"}}"#
    }

    /// MT_BgFade's base list: 140×48, #7f8c8d, `transition: background-color 1s linear`.
    /// `linear` normalizes to a cubic-bezier on the wire (the converter's
    /// TimingFunctionSerializer emits `cb`), which is why the numbers
    /// below are straight lerps and not `ease` curves.
    private var bgFadeBase: [IRProperty] {
        props("""
        [{"type":"Width","data":{"type":"length","px":140.0}},
         {"type":"Height","data":{"type":"length","px":48.0}},
         \(bgJSON(baseRGB, "#7f8c8d")),
         {"type":"TransitionProperty","data":[{"type":"property-name","name":"background-color"}]},
         {"type":"TransitionDuration","data":[{"ms":1000.0,"original":{"v":1.0,"u":"S"}}]},
         {"type":"TransitionTimingFunction","data":[{"cb":[0.0,0.0,1.0,1.0]}]}]
        """)
    }

    /// The `:hover` bucket — colour only, exactly like the fixture.
    private var hoverBucket: [IRSelector] {
        [IRSelector(condition: "hover", properties: props("[\(bgJSON(hoverRGB, "#c0392b"))]"))]
    }

    /// The capture environment every fold here runs against: the 390 px
    /// default canvas, light scheme (spec 06 §4).
    private let env390 = MediaQueryEvaluator.Environment(surfaceWidthPx: 390, prefersDark: false)

    /// The spec 06 §3 fold — the same call `ComponentRenderer.effective(for:)`
    /// makes, minus the light-dark() pass (no light-dark() values here).
    private func fold(_ state: ComponentState,
                      base: [IRProperty]? = nil,
                      selectors: [IRSelector]? = nil,
                      media: [IRMedia]? = nil) -> [IRProperty] {
        StateResolver.resolve(base: base ?? bgFadeBase,
                              selectors: selectors ?? hoverBucket,
                              media: media,
                              state: state,
                              environment: env390,
                              componentName: "MT_BgFade")
    }

    /// The resolved BackgroundColor as bytes, or nil when absent.
    private func bgBytes(_ list: [IRProperty]) -> (r: Double, g: Double, b: Double)? {
        guard let srgb = list.first(where: { $0.type == "BackgroundColor" })?.data["srgb"],
              let r = srgb["r"]?.doubleValue,
              let g = srgb["g"]?.doubleValue,
              let b = srgb["b"]?.doubleValue else { return nil }
        return (r * 255, g * 255, b * 255)
    }

    // MARK: - The trigger predicate

    /// `.onChange(of: componentState)` fires on ComponentState INEQUALITY,
    /// so a condition the resolver honours but the state cannot express is
    /// a silently undetectable flip. This walks the resolver's own
    /// vocabulary and requires each condition to be (a) activatable by a
    /// stored field and (b) visible to the equality that gates the handler.
    func testEveryResolverConditionIsVisibleToTheOnChangePredicate() {
        // condition → the state a REAL (unforced) flip would produce.
        let byRealFlag: [String: ComponentState] = [
            "hover":    ComponentState(hovered: true),
            "active":   ComponentState(pressed: true),
            "focus":    ComponentState(focused: true),
            "disabled": ComponentState(disabled: true),
            "checked":  ComponentState(checked: true),
        ]
        for condition in StateResolver.runtimeV1Conditions {
            // A new vocabulary entry with no row here is either
            // undetectable by the live path or an omission in this pin —
            // both need a human, so fail loudly rather than skip.
            guard let flipped = byRealFlag[condition] else {
                XCTFail("'\(condition)' joined runtimeV1Conditions with no ComponentState "
                        + "field mapped here — either the live detector cannot see it or "
                        + "this pin needs the new row")
                continue
            }
            // The resolver reacts to it (spec 06 §2)…
            XCTAssertEqual(StateResolver.conditionActive(condition, state: flipped), true,
                           "'\(condition)' must activate from its real input flag")
            // …and the detector's key changes when it does.
            XCTAssertNotEqual(flipped, ComponentState(),
                              "a real '\(condition)' flip must change componentState, "
                              + "or .onChange never fires and no transition begins")
            // The forced-state hook (spec 06 §6) is the capture trigger and
            // must be equally visible — it is the ONLY way a host can start
            // a transition on a touch surface, where hover never latches.
            XCTAssertNotEqual(ComponentState(forced: [condition]), ComponentState(),
                              "forcing '\(condition)' must change componentState")
        }
    }

    /// The other direction, and just as load-bearing: two states with the
    /// same content must compare EQUAL. `forced` is a Set precisely so
    /// insertion order cannot leak into the comparison — were it an Array,
    /// every recomposition could re-fire `.onChange` and restart the
    /// in-flight transition from its own presented value, forever.
    func testIdenticalStatesCompareEqualSoThereAreNoPhantomFlips() {
        let a = ComponentState(hovered: true, focused: true, forced: ["hover", "focus"])
        let b = ComponentState(hovered: true, focused: true, forced: ["focus", "hover"])
        XCTAssertEqual(a, b, "identical states must compare equal — an order-sensitive "
                       + "`forced` would restart the transition on every body pass")
    }

    /// spec 07 §4 says transitions are triggered by "selector/media bucket
    /// flips", but the iOS detector observes only componentState — and
    /// neither the surface width nor the colour scheme is an input to it,
    /// so the media half never starts a transition here. (Android detects
    /// flips by diffing the RESOLVED list instead — TransitionDriver.apply's
    /// `committed != resolved` — so it has no such hole.) The two halves
    /// pinned below are the fold MOVING under media, and the detector's key
    /// being built from interaction inputs only. If the field list ever
    /// grows, this fails and forces the "is it detectable?" question.
    func testMediaBucketFlipMovesTheFoldWhileTheDetectorWatchesStateOnly() {
        // A width bucket that changes the same property the transition names.
        let media = [IRMedia(query: "(max-width: 300px)",
                             properties: props("[\(bgJSON(hoverRGB, "#c0392b"))]"))]
        // 390 px canvas: bucket inactive → the base colour.
        let wideFold = fold(ComponentState(), selectors: [], media: media)
        // 250 px canvas: the same component, same state, different fold.
        let narrowFold = StateResolver.resolve(base: bgFadeBase, selectors: [], media: media,
                                               state: ComponentState(),
                                               environment: MediaQueryEvaluator.Environment(
                                                   surfaceWidthPx: 250, prefersDark: false),
                                               componentName: "MT_BgFade")
        XCTAssertEqual(bgBytes(wideFold)!.r, baseRGB.r, accuracy: 1e-9)
        XCTAssertEqual(bgBytes(narrowFold)!.r, hoverRGB.r, accuracy: 1e-9,
                       "the media bucket must move the fold — otherwise this canary "
                       + "proves nothing about the detector")
        // …while the detector's key is built from interaction inputs ONLY.
        // Reflected rather than asserted in prose so the day someone adds a
        // field — a media input that would CLOSE this gap, or a sixth
        // condition that would widen it — this fails and sends them to the
        // vocabulary pin above and to docs/DYNAMIC_CAPTURE.md §4.
        let fields = Set(Mirror(reflecting: ComponentState()).children.compactMap(\.label))
        XCTAssertEqual(fields, ["hovered", "pressed", "focused", "disabled", "checked", "forced"],
                       "ComponentState's field set changed — re-check what the "
                       + ".onChange(of: componentState) detector can and cannot see")
    }

    // MARK: - The from-side

    /// The declared-flip origin (ComponentRenderer :260-264) is
    /// `componentState` with the forced set cleared. The claim it rests on
    /// is that StateResolver is pure, so that pre-flip list is EXACTLY the
    /// list an unforced render would have produced — pinned entry by entry,
    /// because "close enough" would blend from the wrong from-side.
    func testTheDeclaredOriginIsExactlyTheUnforcedFold() {
        // What the renderer builds under `-forceState hover`, then clears.
        var origin = ComponentState(forced: ["hover"])
        origin.forced = []
        let declared = fold(origin)
        let unforced = fold(ComponentState())
        // Same length and same (type, data) at every index.
        XCTAssertEqual(declared.count, unforced.count)
        for (a, b) in zip(declared, unforced) {
            XCTAssertEqual(a.type, b.type)
            XCTAssertEqual(a.data, b.data, "declared origin diverged on \(a.type)")
        }
        // And it is the BASE appearance, not the forced one.
        XCTAssertEqual(bgBytes(declared)!.r, baseRGB.r, accuracy: 1e-9)
        XCTAssertEqual(bgBytes(fold(ComponentState(forced: ["hover"])))!.r, hoverRGB.r,
                       accuracy: 1e-9, "the forced fold must actually move, or the "
                       + "origin comparison above is vacuous")
    }

    /// The number the 2026-08-28 capture reported, pinned without a device:
    /// MT_BgFade at t=0.4 s is (153, 107, 102) — the spec lerp — and NOT
    /// (192, 57, 43), the endpoint every t rendered before the flip landed.
    func testBgFadeAtPinnedTimeReproducesTheCaptureNumber() {
        let out = TransitionResolver.blend(from: fold(ComponentState()),
                                           to: fold(ComponentState(forced: ["hover"])),
                                           elapsedSeconds: 0.4,
                                           componentName: "MT_BgFade")
        let c = bgBytes(out.properties)!
        // Linear timing, no delay: value = base + 0.4 × (hover − base).
        XCTAssertEqual(c.r, baseRGB.r + 0.4 * (hoverRGB.r - baseRGB.r), accuracy: 1e-9) // 153.0
        XCTAssertEqual(c.g, baseRGB.g + 0.4 * (hoverRGB.g - baseRGB.g), accuracy: 1e-9) // 106.8
        XCTAssertEqual(c.b, baseRGB.b + 0.4 * (hoverRGB.b - baseRGB.b), accuracy: 1e-9) // 101.8
        // Mid-flight, so the driver must keep its clock running.
        XCTAssertFalse(out.settled)
        // The regression this whole lane exists for: never the endpoint.
        XCTAssertLessThan(c.r, hoverRGB.r - 1, "rendering the endpoint at t=0.4 is the "
                          + "pre-fix behaviour — the transition did not run")
    }

    // MARK: - The gate

    /// `beginTransition` gates on the POST-flip list, not the pre-flip one
    /// (css-transitions-1 §3: the after-change style's transition-*
    /// governs). With the transition declared only inside `:hover`, that
    /// makes the flip one-way: base→hover animates, hover→base snaps.
    /// Getting this backwards would animate un-hovers that CSS snaps.
    func testGateReadsThePostFlipListSoBucketOnlyTransitionsAreOneWay() {
        let base = props("[\(bgJSON(baseRGB, "#7f8c8d"))]")
        let bucket = [IRSelector(condition: "hover", properties: props("""
            [\(bgJSON(hoverRGB, "#c0392b")),
             {"type":"TransitionProperty","data":[{"type":"all","unit":{}}]},
             {"type":"TransitionDuration","data":[{"ms":1000.0}]}]
            """))]
        let hovered = fold(ComponentState(forced: ["hover"]), base: base, selectors: bucket)
        let unhovered = fold(ComponentState(), base: base, selectors: bucket)
        // Forward: the after-change style carries the transition → animate.
        XCTAssertTrue(TransitionResolver.hasTransitions(hovered))
        // Reverse: the after-change style (base) declares none → instant.
        XCTAssertFalse(TransitionResolver.hasTransitions(unhovered))
        // The blend agrees with the gate: no timing in `to:` ⇒ target, settled.
        let backwards = TransitionResolver.blend(from: hovered, to: unhovered, elapsedSeconds: 0.4)
        XCTAssertEqual(bgBytes(backwards.properties)!.r, baseRGB.r, accuracy: 1e-9)
        XCTAssertTrue(backwards.settled)
    }

    // MARK: - The reversal composition

    /// The one arithmetic step unique to the DETECTION path: a flip that
    /// lands mid-flight re-feeds the current blend as the next leg's
    /// from-side (ComponentRenderer :506-513, css-transitions-1 §3
    /// reversing). That only works if a blend OUTPUT is a legal blend
    /// INPUT — it has had its `original` metadata stripped, so the pin is
    /// that the second leg still departs from the presented value instead
    /// of snapping. This pins the arithmetic precondition, NOT the
    /// snapshot bookkeeping that invokes it.
    func testMidFlightReversalDepartsFromTheBlendedValue() {
        let base = fold(ComponentState())
        let hover = fold(ComponentState(forced: ["hover"]))
        // Leg 1 at 0.4 s — what is on screen when the pointer leaves.
        let inFlight = TransitionResolver.blend(from: base, to: hover,
                                                elapsedSeconds: 0.4).properties
        let presented = baseRGB.g + 0.4 * (hoverRGB.g - baseRGB.g) // 106.8
        // Leg 2 at elapsed 0: the departure point must BE that value.
        let departure = TransitionResolver.blend(from: inFlight, to: base, elapsedSeconds: 0)
        XCTAssertEqual(bgBytes(departure.properties)!.g, presented, accuracy: 1e-9,
                       "the reverse leg must start from the presented value — anything "
                       + "else is a visible snap at the moment of reversal")
        // Halfway down leg 2: midway between the departure and the base.
        let half = TransitionResolver.blend(from: inFlight, to: base, elapsedSeconds: 0.5)
        XCTAssertEqual(bgBytes(half.properties)!.g, presented + 0.5 * (baseRGB.g - presented),
                       accuracy: 1e-9)
        XCTAssertFalse(half.settled)
    }

    /// LIMIT PIN, not a spec claim. `blend` walks the NEW list and needs an
    /// old value of the same type, so a property the pre-flip list never
    /// declared switches instantly instead of transitioning. Android does
    /// exactly the same (`TransitionDriver.startFlights`:
    /// `prevByType[prop.type] ?: continue`), so all runtimes AGREE — which
    /// is precisely why no cross-platform gate can see it. css-transitions-1
    /// §3 would transition from the before-change COMPUTED value (opacity's
    /// initial 1), not from "absent"; changing that is a three-platform
    /// decision, so this records today's behaviour rather than blessing it.
    func testAPropertyTheOldListNeverDeclaredDoesNotTransition() {
        let base = props("""
            [{"type":"TransitionProperty","data":[{"type":"all","unit":{}}]},
             {"type":"TransitionDuration","data":[{"ms":1000.0}]}]
            """)
        // The bucket INTRODUCES Opacity; the base never mentioned it.
        let bucket = [IRSelector(condition: "hover",
                                 properties: props(#"[{"type":"Opacity","data":{"alpha":0.4}}]"#))]
        let out = TransitionResolver.blend(from: fold(ComponentState(), base: base, selectors: bucket),
                                           to: fold(ComponentState(forced: ["hover"]),
                                                    base: base, selectors: bucket),
                                           elapsedSeconds: 0.4)
        XCTAssertEqual(out.properties.first { $0.type == "Opacity" }?.data["alpha"]?.doubleValue,
                       0.4, "today: an old-list-absent property jumps to its target")
        XCTAssertTrue(out.settled, "nothing is in flight, so the driver may stop its clock")
    }
}
