//
//  DynamicStylingTests.swift
//  StyleConverterRuntimeTests
//
//  Wave-7 pins (issues #33 + #34 — schema/spec/06-dynamic-styling.md):
//    • MediaQueryEvaluator — the runtime-v1 grammar: min-/max-width
//      against the RENDER-SURFACE width (390/250 truth table, inclusive
//      boundaries), prefers-color-scheme against the platform signal,
//      everything else conservatively unsupported.
//    • StateResolver — the §3 fold: base → active media buckets (array
//      order) → active selector buckets (array order), whole-value
//      replacement, last writer wins, state beats media by step order;
//      the §6 forced-state hook; unsupported conditions inert + logged.
//    • LightDarkResolver — css-color-5 light-dark() arm selection by
//      the same scheme signal, color-scheme property pinning.
//    • The v2/dynamic-styling.json conformance golden resolved end to
//      end — the wire-order dependency §3 rests on.
//

import XCTest
@testable import StyleConverterRuntime

final class DynamicStylingTests: XCTestCase {

    // MARK: - Helpers

    /// Decode an [IRProperty] from inline JSON — the decode path is the
    /// production path (same convention as DynamicValuesTests).
    private func props(_ json: String) -> [IRProperty] {
        try! JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// One typed color property with the converter's static shape.
    private func colorProp(_ type: String, _ hex: String,
                           r: Double, g: Double, b: Double) -> String {
        """
        {"type":"\(type)","data":{"srgb":{"r":\(r),"g":\(g),"b":\(b)},"original":"\(hex)"}}
        """
    }

    /// The `original` string of a property — how these pins identify
    /// which writer won a layering fold.
    private func original(_ p: IRProperty?) -> String? {
        p?.data["original"]?.stringValue
    }

    /// First property of `type` in a resolved list.
    private func first(_ list: [IRProperty], _ type: String) -> IRProperty? {
        list.first { $0.type == type }
    }

    /// The default capture environment: 390 px surface, light scheme.
    private let env390 = MediaQueryEvaluator.Environment(
        surfaceWidthPx: 390, prefersDark: false)
    /// The second media-recipe width (docs/DYNAMIC_CAPTURE.md §2).
    private let env250 = MediaQueryEvaluator.Environment(
        surfaceWidthPx: 250, prefersDark: false)

    // MARK: - MediaQueryEvaluator: the 390/250 truth table

    func testWidthBucketsAt390() {
        // The media-width.json fixture's 390 px answers (spec 06 §4).
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(min-width: 200px)", in: env390), .active)
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(min-width: 300px)", in: env390), .active)
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(max-width: 500px)", in: env390), .active)
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(min-width: 500px)", in: env390), .inactive)
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(max-width: 300px)", in: env390), .inactive)
    }

    func testWidthBucketsFlipAt250() {
        // The 250 px run flips exactly two rows; the rest hold.
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(max-width: 300px)", in: env250), .active)
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(min-width: 300px)", in: env250), .inactive)
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(min-width: 200px)", in: env250), .active)
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(max-width: 500px)", in: env250), .active)
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(min-width: 500px)", in: env250), .inactive)
    }

    func testWidthBoundariesAreInclusive() {
        // mediaqueries-5 §4.2: min-width: 390px MATCHES a 390 surface —
        // and so does max-width: 390px (both bounds are closed).
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(min-width: 390px)", in: env390), .active)
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(max-width: 390px)", in: env390), .active)
    }

    func testConjunctionNeedsEveryTerm() {
        // AND semantics: all terms true → active; one false → inactive.
        XCTAssertEqual(MediaQueryEvaluator.evaluate(
            "(min-width: 200px) and (max-width: 500px)", in: env390), .active)
        XCTAssertEqual(MediaQueryEvaluator.evaluate(
            "(min-width: 300px) and (max-width: 500px)", in: env250), .inactive)
    }

    func testPrefersColorSchemeFollowsPlatformSignal() {
        // §4: the scheme feature maps to the platform dark-mode signal.
        let dark = MediaQueryEvaluator.Environment(surfaceWidthPx: 390, prefersDark: true)
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(prefers-color-scheme: dark)", in: env390), .inactive)
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(prefers-color-scheme: dark)", in: dark), .active)
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(prefers-color-scheme: light)", in: env390), .active)
        XCTAssertEqual(MediaQueryEvaluator.evaluate("(prefers-color-scheme: light)", in: dark), .inactive)
    }

    func testOutsideGrammarIsUnsupportedNeverActive() {
        // §4 "anything else": not/only, comma lists, range syntax, other
        // features, non-px units, boolean terms, malformed — all
        // unsupported (conservatively inactive), NEVER active.
        for q in ["not (min-width: 200px)",
                  "only screen and (min-width: 200px)",
                  "(min-width: 200px), (max-width: 500px)",
                  "(200px <= width)",
                  "(orientation: landscape)",
                  "(min-width: 20em)",
                  "(color)",
                  "min-width: 200px",
                  ""] {
            XCTAssertEqual(MediaQueryEvaluator.evaluate(q, in: env390), .unsupported,
                           "'\(q)' must be unsupported")
        }
        // One unsupported term poisons a whole conjunction.
        XCTAssertEqual(MediaQueryEvaluator.evaluate(
            "(min-width: 200px) and (orientation: portrait)", in: env390), .unsupported)
    }

    // MARK: - StateResolver: selector activation

    /// DS_ActivePress-shaped inputs: blue base, orange :active bucket.
    private var pressBase: [IRProperty] {
        props("[" + colorProp("BackgroundColor", "#3498db", r: 0.204, g: 0.596, b: 0.859) + "]")
    }
    private var pressBucket: [IRSelector] {
        [IRSelector(condition: "active",
                    properties: props("[" + colorProp("BackgroundColor", "#f39c12", r: 0.953, g: 0.612, b: 0.071) + "]"))]
    }

    func testPressedStateOverridesBaseColor() {
        // :active bucket applies while a press is in progress (§2).
        let resolved = StateResolver.resolve(
            base: pressBase, selectors: pressBucket, media: nil,
            state: ComponentState(pressed: true), environment: env390)
        XCTAssertEqual(original(first(resolved, "BackgroundColor")), "#f39c12")
        // Whole-value replacement in place — still exactly one entry.
        XCTAssertEqual(resolved.filter { $0.type == "BackgroundColor" }.count, 1)
    }

    func testUnpressedStateKeepsBase() {
        // No press → the bucket contributes nothing; identity output.
        let resolved = StateResolver.resolve(
            base: pressBase, selectors: pressBucket, media: nil,
            state: ComponentState(), environment: env390)
        XCTAssertEqual(original(first(resolved, "BackgroundColor")), "#3498db")
    }

    func testDisabledBucketAppliesAndAppends() {
        // DS_DisabledDim: the bucket recolors AND introduces Opacity —
        // replace-in-place for the shared type, append for the new one.
        let base = props("[" + colorProp("BackgroundColor", "#2ecc71", r: 0.18, g: 0.8, b: 0.443) + "]")
        let buckets = [IRSelector(condition: "disabled", properties: props(
            "[" + colorProp("BackgroundColor", "#95a5a6", r: 0.584, g: 0.647, b: 0.651)
            + ",{\"type\":\"Opacity\",\"data\":{\"alpha\":0.5}}]"))]
        let resolved = StateResolver.resolve(
            base: base, selectors: buckets, media: nil,
            state: ComponentState(disabled: true), environment: env390)
        XCTAssertEqual(original(first(resolved, "BackgroundColor")), "#95a5a6")
        // The appended Opacity arrives after the base entries (§3).
        XCTAssertEqual(resolved.last?.type, "Opacity")
    }

    func testHoverIsNoOpWithoutPointerDesignation() {
        // §2: on touch surfaces hover never latches — bucket inert.
        let buckets = [IRSelector(condition: "hover",
                                  properties: props("[" + colorProp("BackgroundColor", "#c0392b", r: 0.753, g: 0.224, b: 0.169) + "]"))]
        let base = pressBase
        let idle = StateResolver.resolve(base: base, selectors: buckets, media: nil,
                                         state: ComponentState(), environment: env390)
        XCTAssertEqual(original(first(idle, "BackgroundColor")), "#3498db")
        // Real pointer designation (iPadOS pointer / Catalyst) applies it.
        let hovered = StateResolver.resolve(base: base, selectors: buckets, media: nil,
                                            state: ComponentState(hovered: true), environment: env390)
        XCTAssertEqual(original(first(hovered, "BackgroundColor")), "#c0392b")
    }

    // MARK: - StateResolver: layering order

    func testSelectorBucketsLayerInArrayOrderLastWriterWins() {
        // DS_StateStack: hover + active both write BackgroundColor,
        // focus writes Color. With all three active the LATER
        // BackgroundColor writer (active) wins — css-cascade-5 §6.4.
        let base = props("["
            + colorProp("BackgroundColor", "#ecf0f1", r: 0.925, g: 0.941, b: 0.945) + ","
            + colorProp("Color", "#111111", r: 0.067, g: 0.067, b: 0.067) + "]")
        let buckets = [
            IRSelector(condition: "hover",
                       properties: props("[" + colorProp("BackgroundColor", "#3498db", r: 0.204, g: 0.596, b: 0.859) + "]")),
            IRSelector(condition: "active",
                       properties: props("[" + colorProp("BackgroundColor", "#e74c3c", r: 0.906, g: 0.298, b: 0.235) + "]")),
            IRSelector(condition: "focus",
                       properties: props("[" + colorProp("Color", "#ffffff", r: 1, g: 1, b: 1) + "]")),
        ]
        let resolved = StateResolver.resolve(
            base: base, selectors: buckets, media: nil,
            state: ComponentState(hovered: true, pressed: true, focused: true),
            environment: env390)
        XCTAssertEqual(original(first(resolved, "BackgroundColor")), "#e74c3c")
        XCTAssertEqual(original(first(resolved, "Color")), "#ffffff")
    }

    func testStateBeatsMediaByStepOrder() {
        // §3 fixed order: media buckets fold FIRST, selector buckets
        // fold after — a pressed state must beat a width-bucket recolor.
        let base = pressBase
        let media = [IRMedia(query: "(min-width: 200px)",
                             properties: props("[" + colorProp("BackgroundColor", "#2c3e50", r: 0.173, g: 0.243, b: 0.314) + "]"))]
        let resolved = StateResolver.resolve(
            base: base, selectors: pressBucket, media: media,
            state: ComponentState(pressed: true), environment: env390)
        // Selector writer (state) wins over the ACTIVE media writer.
        XCTAssertEqual(original(first(resolved, "BackgroundColor")), "#f39c12")
        // Without the press the media bucket's recolor shows.
        let unpressed = StateResolver.resolve(
            base: base, selectors: pressBucket, media: media,
            state: ComponentState(), environment: env390)
        XCTAssertEqual(original(first(unpressed, "BackgroundColor")), "#2c3e50")
    }

    func testMediaBucketsLayerInArrayOrder() {
        // MW_Layered: both queries hold at 390 — the later bucket wins.
        let base = props("[" + colorProp("BackgroundColor", "#ecf0f1", r: 0.925, g: 0.941, b: 0.945) + "]")
        let media = [
            IRMedia(query: "(min-width: 200px)",
                    properties: props("[" + colorProp("BackgroundColor", "#2c3e50", r: 0.173, g: 0.243, b: 0.314) + "]")),
            IRMedia(query: "(max-width: 500px)",
                    properties: props("[" + colorProp("BackgroundColor", "#e67e22", r: 0.902, g: 0.494, b: 0.133) + "]")),
        ]
        let resolved = StateResolver.resolve(
            base: base, selectors: nil, media: media,
            state: ComponentState(), environment: env390)
        XCTAssertEqual(original(first(resolved, "BackgroundColor")), "#e67e22")
    }

    func testNoBucketsIsIdentityFastPath() {
        // The whole pre-wave-7 corpus: no buckets → the base array back.
        let base = pressBase
        let resolved = StateResolver.resolve(base: base, selectors: nil, media: nil,
                                             state: ComponentState(), environment: env390)
        XCTAssertEqual(resolved.count, base.count)
        XCTAssertEqual(original(first(resolved, "BackgroundColor")), "#3498db")
    }

    // MARK: - StateResolver: forced states (spec 06 §6)

    func testForcedStateActivatesWithoutRealInput() {
        // Forcing {"active"} resolves EXACTLY what a real press would.
        let forced = StateResolver.resolve(
            base: pressBase, selectors: pressBucket, media: nil,
            state: ComponentState(forced: ["active"]), environment: env390)
        let real = StateResolver.resolve(
            base: pressBase, selectors: pressBucket, media: nil,
            state: ComponentState(pressed: true), environment: env390)
        XCTAssertEqual(original(first(forced, "BackgroundColor")),
                       original(first(real, "BackgroundColor")))
    }

    func testForcedCheckedActivatesTheNoOpCondition() {
        // checked has no real analogue on iOS (defined no-op) — but the
        // §6 hook must still resolve it for deterministic captures.
        let buckets = [IRSelector(condition: "checked",
                                  properties: props("[" + colorProp("BackgroundColor", "#16a085", r: 0.086, g: 0.627, b: 0.522) + "]"))]
        let resolved = StateResolver.resolve(
            base: pressBase, selectors: buckets, media: nil,
            state: ComponentState(forced: ["checked"]), environment: env390)
        XCTAssertEqual(original(first(resolved, "BackgroundColor")), "#16a085")
    }

    func testForcingCannotRescueUnsupportedConditions() {
        // The vocabulary gate wins over forcing: "visited" stays inert
        // even when forced (it is outside runtime v1 entirely).
        let buckets = [IRSelector(condition: "visited",
                                  properties: props("[" + colorProp("BackgroundColor", "#9b59b6", r: 0.608, g: 0.349, b: 0.714) + "]"))]
        let resolved = StateResolver.resolve(
            base: pressBase, selectors: buckets, media: nil,
            state: ComponentState(forced: ["visited"]), environment: env390)
        XCTAssertEqual(original(first(resolved, "BackgroundColor")), "#3498db")
    }

    func testUnsupportedConditionIsInertAndLoggedOnce() {
        // §2 tolerance rule: focus-within is reserved — bucket inert,
        // breadcrumbed once per condition per process.
        PropertyTracker._resetForTests()
        let buckets = [IRSelector(condition: "focus-within",
                                  properties: props("[" + colorProp("BackgroundColor", "#9b59b6", r: 0.608, g: 0.349, b: 0.714) + "]"))]
        let resolved = StateResolver.resolve(
            base: pressBase, selectors: buckets, media: nil,
            state: ComponentState(focused: true), environment: env390)
        XCTAssertEqual(original(first(resolved, "BackgroundColor")), "#3498db")
        // The dedupe pin: first sighting logs, the second stays silent.
        XCTAssertFalse(PropertyTracker.logOnce(key: "selector:focus-within",
                                               message: "dup"))
    }

    func testConditionNormalizationToleratesColonAndCase() {
        // Wire conditions are colon-stripped; normalize() also accepts
        // a stray ':HOVER' defensively (ASCII case-insensitive).
        XCTAssertEqual(StateResolver.normalize(":hover"), "hover")
        XCTAssertEqual(StateResolver.normalize("HOVER"), "hover")
        XCTAssertEqual(StateResolver.normalize(" active "), "active")
    }

    // MARK: - LightDarkResolver (css-color-5 §4.1)

    /// DK_LightDark's background declaration — the converter's dynamic
    /// light-dark shape (no srgb, structured original).
    private var lightDarkProps: [IRProperty] {
        props("""
        [{"type":"BackgroundColor","data":{"original":{
            "type":"light-dark","lightColor":"#ecf0f1","darkColor":"#111827"}}}]
        """)
    }

    func testLightDarkPicksLightArmByDefault() {
        // Light capture environment (the standard run) → light arm.
        let resolved = LightDarkResolver.resolve(lightDarkProps, prefersDark: false)
        let bg = first(resolved, "BackgroundColor")
        XCTAssertEqual(original(bg), "#ecf0f1")
        // The rewrite produced the STATIC shape: srgb of #ecf0f1.
        XCTAssertEqual(bg?.data["srgb"]?["r"]?.doubleValue ?? 0, 0.925, accuracy: 0.001)
    }

    func testLightDarkPicksDarkArmUnderDarkScheme() {
        // Dark platform signal → dark arm (#111827).
        let resolved = LightDarkResolver.resolve(lightDarkProps, prefersDark: true)
        let bg = first(resolved, "BackgroundColor")
        XCTAssertEqual(original(bg), "#111827")
        XCTAssertEqual(bg?.data["srgb"]?["b"]?.doubleValue ?? 0, 0x27 / 255.0, accuracy: 0.001)
    }

    func testColorSchemePropertyPinsTheArm() {
        // css-color-adjust-1 §3: a component declaring ONE scheme pins
        // light-dark() to that arm regardless of the platform signal.
        let darkPinned = lightDarkProps + props("""
        [{"type":"ColorScheme","data":{"schemes":["DARK"]}}]
        """)
        XCTAssertEqual(original(first(
            LightDarkResolver.resolve(darkPinned, prefersDark: false), "BackgroundColor")),
            "#111827")
        let lightPinned = lightDarkProps + props("""
        [{"type":"ColorScheme","data":{"schemes":["LIGHT"]}}]
        """)
        XCTAssertEqual(original(first(
            LightDarkResolver.resolve(lightPinned, prefersDark: true), "BackgroundColor")),
            "#ecf0f1")
        // "light dark" supports both → the platform signal decides.
        let both = lightDarkProps + props("""
        [{"type":"ColorScheme","data":{"schemes":["LIGHT","DARK"]}}]
        """)
        XCTAssertEqual(original(first(
            LightDarkResolver.resolve(both, prefersDark: true), "BackgroundColor")),
            "#111827")
    }

    func testStaticColorsAndUnparseableArmsPassThrough() {
        // Static srgb shapes are untouched (identity fast path)…
        let statics = pressBase
        XCTAssertEqual(original(first(
            LightDarkResolver.resolve(statics, prefersDark: true), "BackgroundColor")),
            "#3498db")
        // …and an arm outside the token grammar stays DYNAMIC (honest
        // drop downstream) instead of guessing an sRGB value.
        let oklch = props("""
        [{"type":"BackgroundColor","data":{"original":{
            "type":"light-dark","lightColor":"oklch(0.7 0.1 200)","darkColor":"#111827"}}}]
        """)
        let resolved = LightDarkResolver.resolve(oklch, prefersDark: false)
        XCTAssertNil(resolved.first?.data["srgb"], "unparseable arm must not fabricate srgb")
    }

    // MARK: - The conformance golden, end to end

    /// repo-root/schema/conformance/fixtures/v2 resolved from #filePath
    /// (same plumbing as ConformanceTests).
    private static var goldenURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("schema/conformance/fixtures/v2/dynamic-styling.json")
    }

    func testGoldenLayeringPinResolvesPerSpec() throws {
        // Decode through the production wire path (strict v2 reader).
        let doc = try JSONDecoder().decode(
            IRDocument.self, from: Data(contentsOf: Self.goldenURL))
        let comp = try XCTUnwrap(doc.components.first { $0.name == "DynamicLayering" })
        // Base run at 390/light: two width buckets hold, the dark one
        // doesn't — the LATER media writer (#e67e22) takes the bg and
        // Color keeps its base value (§3 steps 1–2).
        let base = StateResolver.resolve(
            base: comp.properties, selectors: comp.selectors, media: comp.media,
            state: ComponentState(), environment: env390)
        XCTAssertEqual(original(first(base, "BackgroundColor")), "#e67e22")
        XCTAssertEqual(original(first(base, "Color")), "#111111")
        // All states on: the selector step folds after media — active
        // (last bg writer among hover/active) beats every width bucket,
        // focus takes Color (§3 step 3, the golden's raison d'être).
        let stated = StateResolver.resolve(
            base: comp.properties, selectors: comp.selectors, media: comp.media,
            state: ComponentState(hovered: true, pressed: true, focused: true),
            environment: env390)
        XCTAssertEqual(original(first(stated, "BackgroundColor")), "#e74c3c")
        XCTAssertEqual(original(first(stated, "Color")), "#ffffff")
        // The golden's light-dark component resolves the light arm in
        // the standard capture environment.
        let ld = try XCTUnwrap(doc.components.first { $0.name == "LightDarkValue" })
        XCTAssertEqual(original(first(
            LightDarkResolver.resolve(ld.properties, prefersDark: false), "BackgroundColor")),
            "#ecf0f1")
    }
}
